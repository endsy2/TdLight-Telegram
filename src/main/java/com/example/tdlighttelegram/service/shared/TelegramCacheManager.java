package com.example.tdlighttelegram.service.shared;

import com.example.tdlighttelegram.model.*;
import it.tdlight.jni.TdApi;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Telegram Cache Manager - Optimized for Performance
 *
 * Improvements:
 * - LRU cache eviction for memory management
 * - Read-write locks for better concurrency
 * - Cache size limits to prevent memory leaks
 * - Cache statistics for monitoring
 * - Batch operations for efficiency
 * - TTL (Time To Live) support
 */
@Slf4j
@Component
public class TelegramCacheManager {

    // Cache configuration
    private static final int MAX_MESSAGE_HISTORY_PER_CHAT = 1000;
    private static final int MAX_CACHED_CHATS = 100;
    private static final int MAX_CACHED_USERS = 10000;
    private static final int MAX_VIDEO_MESSAGES_PER_CHAT = 500;
    private static final Duration DEFAULT_TTL = Duration.ofHours(1);

    // Cache entry wrapper with TTL
    private static class CacheEntry<T> {
        private final T value;
        private final Instant expiryTime;

        CacheEntry(T value, Duration ttl) {
            this.value = value;
            this.expiryTime = Instant.now().plus(ttl);
        }

        boolean isExpired() {
            return Instant.now().isAfter(expiryTime);
        }

        T getValue() {
            return value;
        }
    }

    // Group information cache with TTL
    private final ConcurrentHashMap<Long, CacheEntry<GroupInfo>> groupInfoCache = new ConcurrentHashMap<>();

    // User information cache with TTL
    private final ConcurrentHashMap<Long, CacheEntry<UserInfo>> userInfoCache = new ConcurrentHashMap<>();

    // Message history cache by chat ID (with size limit)
    private final ConcurrentHashMap<Long, ConcurrentLinkedDeque<MessageInfo>> messageHistoryCache = new ConcurrentHashMap<>();
    private final ReadWriteLock messageHistoryLock = new ReentrantReadWriteLock();

    // Video messages cache by group ID (with size limit)
    private final ConcurrentHashMap<Long, ConcurrentLinkedDeque<MessageInfo>> groupVideoMessagesCache = new ConcurrentHashMap<>();
    private final ReadWriteLock videoMessagesLock = new ReentrantReadWriteLock();

    // Download tasks cache
    @Getter
    private final ConcurrentHashMap<String, DownloadInfo> downloadCache = new ConcurrentHashMap<>();

    // Group members cache by group ID with TTL
    private final ConcurrentHashMap<Long, CacheEntry<List<GroupMemberInfo>>> groupMembersCache = new ConcurrentHashMap<>();

    // Invite results cache
    @Getter
    private final ConcurrentHashMap<String, InviteResult> inviteResultCache = new ConcurrentHashMap<>();

    // Link download tasks cache
    @Getter
    private final ConcurrentHashMap<String, TelegramLinkDownloadResult> linkDownloadCache = new ConcurrentHashMap<>();

    // TDApi user information cache with TTL
    private final ConcurrentHashMap<Long, CacheEntry<TdApi.User>> tdUserCache = new ConcurrentHashMap<>();

    // TDApi chat information cache with TTL
    private final ConcurrentHashMap<Long, CacheEntry<TdApi.Chat>> tdChatCache = new ConcurrentHashMap<>();

    // LRU tracking for chat history
    private final ConcurrentLinkedDeque<Long> chatAccessOrder = new ConcurrentLinkedDeque<>();

    // Cache statistics
    private volatile long cacheHits = 0;
    private volatile long cacheMisses = 0;


    // ============================================================================
    // MESSAGE HISTORY OPERATIONS (Optimized with size limits and LRU)
    // ============================================================================

    /**
     * Add message to history for specific chat (with size limit)
     */
    public void addMessageToHistory(Long chatId, MessageInfo messageInfo) {
        messageHistoryLock.writeLock().lock();
        try {
            ConcurrentLinkedDeque<MessageInfo> messages = messageHistoryCache.computeIfAbsent(
                chatId,
                k -> new ConcurrentLinkedDeque<>()
            );

            // Add message
            messages.addLast(messageInfo);

            // Enforce size limit (remove oldest messages)
            while (messages.size() > MAX_MESSAGE_HISTORY_PER_CHAT) {
                messages.removeFirst();
                log.debug("Evicted oldest message from chat {} cache (size limit reached)", chatId);
            }

            // Update LRU access order
            updateChatAccessOrder(chatId);

            // Enforce chat limit (evict least recently used chats)
            evictLeastRecentlyUsedChats();

            log.debug("Message added to history: messageId={}, chatId={}, cacheSize={}",
                messageInfo.getId(), chatId, messages.size());
        } finally {
            messageHistoryLock.writeLock().unlock();
        }
    }

    /**
     * Add message to history (legacy method for backward compatibility)
     */
    public void addMessageToHistory(MessageInfo messageInfo) {
        addMessageToHistory(messageInfo.getChatId(), messageInfo);
    }

    /**
     * Add multiple messages in batch (more efficient)
     */
    public void addMessagesToHistory(Long chatId, List<MessageInfo> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        messageHistoryLock.writeLock().lock();
        try {
            ConcurrentLinkedDeque<MessageInfo> chatMessages = messageHistoryCache.computeIfAbsent(
                chatId,
                k -> new ConcurrentLinkedDeque<>()
            );

            // Add all messages
            chatMessages.addAll(messages);

            // Enforce size limit
            while (chatMessages.size() > MAX_MESSAGE_HISTORY_PER_CHAT) {
                chatMessages.removeFirst();
            }

            updateChatAccessOrder(chatId);
            evictLeastRecentlyUsedChats();

            log.debug("Batch added {} messages to chat {}, cacheSize={}",
                messages.size(), chatId, chatMessages.size());
        } finally {
            messageHistoryLock.writeLock().unlock();
        }
    }

    /**
     * Get message history for specific chat (with cache hit tracking)
     */
    public List<MessageInfo> getMessageHistory(Long chatId) {
        messageHistoryLock.readLock().lock();
        try {
            ConcurrentLinkedDeque<MessageInfo> messages = messageHistoryCache.get(chatId);

            if (messages != null && !messages.isEmpty()) {
                cacheHits++;
                updateChatAccessOrder(chatId);
                return new ArrayList<>(messages);
            } else {
                cacheMisses++;
                return new ArrayList<>();
            }
        } finally {
            messageHistoryLock.readLock().unlock();
        }
    }

    /**
     * Get all message history (flattened from all chats) - Use with caution
     */
    public List<MessageInfo> getMessageHistory() {
        messageHistoryLock.readLock().lock();
        try {
            return messageHistoryCache.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        } finally {
            messageHistoryLock.readLock().unlock();
        }
    }

    /**
     * Get recent messages from chat (limit)
     */
    public List<MessageInfo> getRecentMessages(Long chatId, int limit) {
        messageHistoryLock.readLock().lock();
        try {
            ConcurrentLinkedDeque<MessageInfo> messages = messageHistoryCache.get(chatId);

            if (messages == null || messages.isEmpty()) {
                cacheMisses++;
                return new ArrayList<>();
            }

            cacheHits++;
            updateChatAccessOrder(chatId);

            // Get last N messages
            return messages.stream()
                .skip(Math.max(0, messages.size() - limit))
                .collect(Collectors.toList());
        } finally {
            messageHistoryLock.readLock().unlock();
        }
    }

    /**
     * Clear message history for specific chat
     */
    public void clearMessageHistory(Long chatId) {
        messageHistoryLock.writeLock().lock();
        try {
            messageHistoryCache.remove(chatId);
            chatAccessOrder.remove(chatId);
            log.debug("Message history cleared for chat: {}", chatId);
        } finally {
            messageHistoryLock.writeLock().unlock();
        }
    }

    /**
     * Get message history cache size for a chat
     */
    public int getMessageHistorySize(Long chatId) {
        messageHistoryLock.readLock().lock();
        try {
            ConcurrentLinkedDeque<MessageInfo> messages = messageHistoryCache.get(chatId);
            return messages != null ? messages.size() : 0;
        } finally {
            messageHistoryLock.readLock().unlock();
        }
    }



    // ============================================================================
    // GROUP INFO OPERATIONS (With TTL)
    // ============================================================================

    /**
     * Cache group info with TTL
     */
    public void cacheGroupInfo(Long groupId, GroupInfo groupInfo) {
        groupInfoCache.put(groupId, new CacheEntry<>(groupInfo, DEFAULT_TTL));
    }

    /**
     * Get group info from cache
     */
    public GroupInfo getGroupInfo(Long groupId) {
        CacheEntry<GroupInfo> entry = groupInfoCache.get(groupId);

        if (entry == null) {
            cacheMisses++;
            return null;
        }

        if (entry.isExpired()) {
            groupInfoCache.remove(groupId);
            cacheMisses++;
            return null;
        }

        cacheHits++;
        return entry.getValue();
    }

    /**
     * Get group info cache (for backward compatibility)
     */
    public ConcurrentHashMap<Long, GroupInfo> getGroupInfoCache() {
        // Clean expired entries and return unwrapped map
        groupInfoCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        ConcurrentHashMap<Long, GroupInfo> result = new ConcurrentHashMap<>();
        groupInfoCache.forEach((key, entry) -> {
            if (!entry.isExpired()) {
                result.put(key, entry.getValue());
            }
        });
        return result;
    }

    /**
     * Get all group infos
     */
    public List<GroupInfo> getAllGroupInfos() {
        groupInfoCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        return groupInfoCache.values().stream()
            .filter(entry -> !entry.isExpired())
            .map(CacheEntry::getValue)
            .collect(java.util.stream.Collectors.toList());
    }

    // ============================================================================
    // USER INFO OPERATIONS (With TTL)
    // ============================================================================

    /**
     * Cache user info with TTL
     */
    public void cacheUserInfo(Long userId, UserInfo userInfo) {
        userInfoCache.put(userId, new CacheEntry<>(userInfo, DEFAULT_TTL));
    }

    /**
     * Get user info from cache
     */
    public UserInfo getUserInfo(Long userId) {
        CacheEntry<UserInfo> entry = userInfoCache.get(userId);

        if (entry == null) {
            cacheMisses++;
            return null;
        }

        if (entry.isExpired()) {
            userInfoCache.remove(userId);
            cacheMisses++;
            return null;
        }

        cacheHits++;
        return entry.getValue();
    }

    /**
     * Get user info cache (for backward compatibility)
     */
    public ConcurrentHashMap<Long, UserInfo> getUserInfoCache() {
        // Clean expired entries and return unwrapped map
        userInfoCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        ConcurrentHashMap<Long, UserInfo> result = new ConcurrentHashMap<>();
        userInfoCache.forEach((key, entry) -> {
            if (!entry.isExpired()) {
                result.put(key, entry.getValue());
            }
        });
        return result;
    }

    /**
     * Get all user infos
     */
    public List<UserInfo> getAllUserInfos() {
        userInfoCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        return userInfoCache.values().stream()
            .filter(entry -> !entry.isExpired())
            .map(CacheEntry::getValue)
            .collect(java.util.stream.Collectors.toList());
    }

    // ============================================================================
    // VIDEO MESSAGE OPERATIONS (Optimized)
    // ============================================================================

    /**
     * Add video message to cache (with size limit)
     */
    public void addVideoMessage(Long chatId, MessageInfo messageInfo) {
        videoMessagesLock.writeLock().lock();
        try {
            ConcurrentLinkedDeque<MessageInfo> videos = groupVideoMessagesCache.computeIfAbsent(
                chatId,
                k -> new ConcurrentLinkedDeque<>()
            );

            videos.addLast(messageInfo);

            // Enforce size limit
            while (videos.size() > MAX_VIDEO_MESSAGES_PER_CHAT) {
                videos.removeFirst();
            }

            log.debug("Video message cached for group: {}, cacheSize={}", chatId, videos.size());
        } finally {
            videoMessagesLock.writeLock().unlock();
        }
    }

    /**
     * Get group video messages
     */
    public List<MessageInfo> geVideoMessages(Long chatId) {
        videoMessagesLock.readLock().lock();
        try {
            ConcurrentLinkedDeque<MessageInfo> videos = groupVideoMessagesCache.get(chatId);
            return videos != null ? new ArrayList<>(videos) : new ArrayList<>();
        } finally {
            videoMessagesLock.readLock().unlock();
        }
    }

    /**
     * Get group video messages cache (for backward compatibility)
     */
    public ConcurrentHashMap<Long, List<MessageInfo>> getGroupVideoMessagesCache() {
        videoMessagesLock.readLock().lock();
        try {
            ConcurrentHashMap<Long, List<MessageInfo>> result = new ConcurrentHashMap<>();
            groupVideoMessagesCache.forEach((key, deque) -> {
                result.put(key, new ArrayList<>(deque));
            });
            return result;
        } finally {
            videoMessagesLock.readLock().unlock();
        }
    }

    // ============================================================================
    // USER CACHE OPERATIONS (With TTL)
    // ============================================================================

    /**
     * Cache TDApi user with TTL
     */
    public void cacheTdUser(Long userId, TdApi.User user) {
        tdUserCache.put(userId, new CacheEntry<>(user, DEFAULT_TTL));

        // Enforce size limit
        if (tdUserCache.size() > MAX_CACHED_USERS) {
            evictOldestUsers();
        }
    }

    /**
     * Get TDApi user from cache
     */
    public TdApi.User getTdUser(Long userId) {
        CacheEntry<TdApi.User> entry = tdUserCache.get(userId);

        if (entry == null) {
            cacheMisses++;
            return null;
        }

        if (entry.isExpired()) {
            tdUserCache.remove(userId);
            cacheMisses++;
            return null;
        }

        cacheHits++;
        return entry.getValue();
    }

    /**
     * Get TDApi user cache (for backward compatibility)
     */
    public ConcurrentHashMap<Long, TdApi.User> getTdUserCache() {
        // Clean expired entries and return unwrapped map
        cleanExpiredUsers();
        ConcurrentHashMap<Long, TdApi.User> result = new ConcurrentHashMap<>();
        tdUserCache.forEach((key, entry) -> {
            if (!entry.isExpired()) {
                result.put(key, entry.getValue());
            }
        });
        return result;
    }

    // ============================================================================
    // CHAT CACHE OPERATIONS (With TTL)
    // ============================================================================

    /**
     * Cache TDApi chat with TTL
     */
    public void cacheTdChat(Long chatId, TdApi.Chat chat) {
        tdChatCache.put(chatId, new CacheEntry<>(chat, DEFAULT_TTL));
    }

    /**
     * Get TDApi chat from cache
     */
    public TdApi.Chat getTdChat(Long chatId) {
        CacheEntry<TdApi.Chat> entry = tdChatCache.get(chatId);

        if (entry == null) {
            cacheMisses++;
            return null;
        }

        if (entry.isExpired()) {
            tdChatCache.remove(chatId);
            cacheMisses++;
            return null;
        }

        cacheHits++;
        return entry.getValue();
    }

    /**
     * Get TDApi chat cache (for backward compatibility)
     */
    public ConcurrentHashMap<Long, TdApi.Chat> getTdChatCache() {
        // Clean expired entries and return unwrapped map
        cleanExpiredChats();
        ConcurrentHashMap<Long, TdApi.Chat> result = new ConcurrentHashMap<>();
        tdChatCache.forEach((key, entry) -> {
            if (!entry.isExpired()) {
                result.put(key, entry.getValue());
            }
        });
        return result;
    }

    // ============================================================================
    // GROUP MEMBERS OPERATIONS (With TTL)
    // ============================================================================

    /**
     * Get group members from cache
     */
    public List<GroupMemberInfo> getCachedGroupMembers(Long groupId) {
        CacheEntry<List<GroupMemberInfo>> entry = groupMembersCache.get(groupId);

        if (entry == null) {
            cacheMisses++;
            return new ArrayList<>();
        }

        if (entry.isExpired()) {
            groupMembersCache.remove(groupId);
            cacheMisses++;
            return new ArrayList<>();
        }

        cacheHits++;
        return new ArrayList<>(entry.getValue());
    }

    /**
     * Cache group members with TTL
     */
    public void cacheGroupMembers(Long groupId, List<GroupMemberInfo> members) {
        groupMembersCache.put(groupId, new CacheEntry<>(members, DEFAULT_TTL));
        log.debug("Cached {} members for group {}", members.size(), groupId);
    }

    /**
     * Get group members cache (for backward compatibility)
     */
    public ConcurrentHashMap<Long, List<GroupMemberInfo>> getGroupMembersCache() {
        cleanExpiredGroupMembers();
        ConcurrentHashMap<Long, List<GroupMemberInfo>> result = new ConcurrentHashMap<>();
        groupMembersCache.forEach((key, entry) -> {
            if (!entry.isExpired()) {
                result.put(key, entry.getValue());
            }
        });
        return result;
    }


    // ============================================================================
    // CACHE MAINTENANCE & UTILITIES
    // ============================================================================

    /**
     * Update chat access order for LRU tracking
     */
    private void updateChatAccessOrder(Long chatId) {
        chatAccessOrder.remove(chatId);
        chatAccessOrder.addLast(chatId);
    }

    /**
     * Evict least recently used chats when limit is reached
     */
    private void evictLeastRecentlyUsedChats() {
        while (messageHistoryCache.size() > MAX_CACHED_CHATS) {
            Long oldestChatId = chatAccessOrder.pollFirst();
            if (oldestChatId != null) {
                messageHistoryCache.remove(oldestChatId);
                log.debug("Evicted chat {} from cache (LRU)", oldestChatId);
            } else {
                break;
            }
        }
    }

    /**
     * Evict oldest users when limit is reached
     */
    private void evictOldestUsers() {
        int toRemove = tdUserCache.size() - MAX_CACHED_USERS + 100; // Remove 100 at a time

        tdUserCache.entrySet().stream()
            .sorted(Map.Entry.comparingByValue((e1, e2) ->
                e1.expiryTime.compareTo(e2.expiryTime)))
            .limit(toRemove)
            .map(Map.Entry::getKey)
            .forEach(tdUserCache::remove);

        log.debug("Evicted {} users from cache (size limit)", toRemove);
    }

    /**
     * Clean expired user entries
     */
    private void cleanExpiredUsers() {
        tdUserCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * Clean expired chat entries
     */
    private void cleanExpiredChats() {
        tdChatCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * Clean expired group members entries
     */
    private void cleanExpiredGroupMembers() {
        groupMembersCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * Clean all expired entries across all caches
     */
    public void cleanExpiredEntries() {
        int removedCount = 0;

        // Clean each cache and count removals
        if (tdUserCache.entrySet().removeIf(entry -> entry.getValue().isExpired())) {
            removedCount++;
        }
        if (tdChatCache.entrySet().removeIf(entry -> entry.getValue().isExpired())) {
            removedCount++;
        }
        if (groupMembersCache.entrySet().removeIf(entry -> entry.getValue().isExpired())) {
            removedCount++;
        }
        if (groupInfoCache.entrySet().removeIf(entry -> entry.getValue().isExpired())) {
            removedCount++;
        }
        if (userInfoCache.entrySet().removeIf(entry -> entry.getValue().isExpired())) {
            removedCount++;
        }

        if (removedCount > 0) {
            log.info("Cleaned expired cache entries from {} cache types", removedCount);
        }
    }

    /**
     * Get cache statistics
     */
    public Map<String, Object> getCacheStatistics() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("cacheHits", cacheHits);
        stats.put("cacheMisses", cacheMisses);
        stats.put("hitRate", cacheHits + cacheMisses > 0 ?
            (double) cacheHits / (cacheHits + cacheMisses) : 0.0);

        stats.put("messageHistoryCacheSize", messageHistoryCache.size());
        stats.put("tdUserCacheSize", tdUserCache.size());
        stats.put("tdChatCacheSize", tdChatCache.size());
        stats.put("groupMembersCacheSize", groupMembersCache.size());
        stats.put("videoCacheSize", groupVideoMessagesCache.size());
        stats.put("downloadCacheSize", downloadCache.size());

        // Calculate total messages cached
        int totalMessages = messageHistoryCache.values().stream()
            .mapToInt(Collection::size)
            .sum();
        stats.put("totalMessagesCached", totalMessages);

        return stats;
    }

    /**
     * Reset cache statistics
     */
    public void resetStatistics() {
        cacheHits = 0;
        cacheMisses = 0;
        log.info("Cache statistics reset");
    }

    /**
     * Clear all caches (optimized)
     */
    public void clearAllCaches() {
        messageHistoryLock.writeLock().lock();
        videoMessagesLock.writeLock().lock();
        try {
            groupInfoCache.clear();
            userInfoCache.clear();
            messageHistoryCache.clear();
            groupVideoMessagesCache.clear();
            downloadCache.clear();
            groupMembersCache.clear();
            inviteResultCache.clear();
            linkDownloadCache.clear();
            tdUserCache.clear();
            tdChatCache.clear();
            chatAccessOrder.clear();

            resetStatistics();

            log.info("All caches cleared");
        } finally {
            videoMessagesLock.writeLock().unlock();
            messageHistoryLock.writeLock().unlock();
        }
    }

    /**
     * Get memory usage estimate (in MB)
     */
    public double getEstimatedMemoryUsageMB() {
        Runtime runtime = Runtime.getRuntime();
        runtime.gc(); // Suggest garbage collection

        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        return usedMemory / (1024.0 * 1024.0);
    }

    /**
     * Optimize cache (clean expired + evict if needed)
     */
    public void optimizeCache() {
        log.info("Starting cache optimization...");

        cleanExpiredEntries();

        // Check memory usage
        double memoryUsageMB = getEstimatedMemoryUsageMB();
        log.info("Current memory usage: {:.2f} MB", memoryUsageMB);

        // If memory usage is high, be more aggressive with eviction
        if (memoryUsageMB > 500) {
            log.warn("High memory usage detected, performing aggressive cache eviction");
            evictLeastRecentlyUsedChats();
            evictOldestUsers();
        }

        Map<String, Object> stats = getCacheStatistics();
        log.info("Cache optimization complete. Stats: {}", stats);
    }
}

package com.example.tdlighttelegram.service.shared;

import com.example.tdlighttelegram.model.*;
import it.tdlight.jni.TdApi;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Telegram Cache Manager
 * Centralized management of all cache data
 */
@Slf4j
@Component
public class TelegramCacheManager {

    // Group information cache
    @Getter
    private final ConcurrentHashMap<Long, GroupInfo> groupInfoCache = new ConcurrentHashMap<>();

    // User information cache
    @Getter
    private final ConcurrentHashMap<Long, UserInfo> userInfoCache = new ConcurrentHashMap<>();

    // Message history cache
    @Getter
    private final List<MessageInfo> messageHistory = new ArrayList<>();

    // Video messages cache by group ID
    @Getter
    private final ConcurrentHashMap<Long, List<MessageInfo>> groupVideoMessagesCache = new ConcurrentHashMap<>();

    // Download tasks cache
    @Getter
    private final ConcurrentHashMap<String, DownloadInfo> downloadCache = new ConcurrentHashMap<>();

    // Group members cache by group ID
    @Getter
    private final ConcurrentHashMap<Long, List<GroupMemberInfo>> groupMembersCache = new ConcurrentHashMap<>();

    // Invite results cache
    @Getter
    private final ConcurrentHashMap<String, InviteResult> inviteResultCache = new ConcurrentHashMap<>();

    // Link download tasks cache
    @Getter
    private final ConcurrentHashMap<String, TelegramLinkDownloadResult> linkDownloadCache = new ConcurrentHashMap<>();

    // TDApi user information cache
    @Getter
    private final ConcurrentHashMap<Long, TdApi.User> tdUserCache = new ConcurrentHashMap<>();

    // TDApi chat information cache
    @Getter
    private final ConcurrentHashMap<Long, TdApi.Chat> tdChatCache = new ConcurrentHashMap<>();

    /**
     * Add message to history
     */
    public synchronized void addMessageToHistory(MessageInfo messageInfo) {
        messageHistory.add(messageInfo);
        log.debug("Message added to history: messageId={}, chatId={}", 
            messageInfo.getId(), messageInfo.getChatId());
    }

    /**
     * Add video message to cache
     */
    public void addVideoMessage(Long chatId, MessageInfo messageInfo) {
        groupVideoMessagesCache.computeIfAbsent(chatId, k -> new ArrayList<>()).add(messageInfo);
        log.debug("Video message cached for group: {}", chatId);
    }

    /**
     * Get group video messages
     */
    public List<MessageInfo> geVideoMessages(Long chatId) {
        return groupVideoMessagesCache.getOrDefault(chatId, new ArrayList<>());
    }

    /**
     * Get group members from cache
     */
    public List<GroupMemberInfo> getCachedGroupMembers(Long groupId) {
        return groupMembersCache.getOrDefault(groupId, new ArrayList<>());
    }

    /**
     * Cache group members
     */
    public void cacheGroupMembers(Long groupId, List<GroupMemberInfo> members) {
        groupMembersCache.put(groupId, members);
        log.debug("Cached {} members for group {}", members.size(), groupId);
    }

    /**
     * Clear all caches
     */
    public void clearAllCaches() {
        groupInfoCache.clear();
        userInfoCache.clear();
        messageHistory.clear();
        groupVideoMessagesCache.clear();
        downloadCache.clear();
        groupMembersCache.clear();
        inviteResultCache.clear();
        linkDownloadCache.clear();
        tdUserCache.clear();
        tdChatCache.clear();
        log.info("All caches cleared");
    }
}

package com.example.tdlighttelegram.service;

import com.example.tdlighttelegram.mapper.TelegramMapper;
import com.example.tdlighttelegram.model.ChatInfo;
import com.example.tdlighttelegram.service.shared.TelegramCacheManager;
import com.example.tdlighttelegram.service.shared.TelegramClientProvider;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Chat Service
 * Handles chat information retrieval and management
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final TelegramClientProvider clientProvider;
    private final TelegramCacheManager cacheManager;

    /**
     * Get chat information by ID
     */
    public CompletableFuture<ChatInfo> getChatInfo(Long chatId) {
        try {
            // Check cache first
            TdApi.Chat cachedChat = cacheManager.getTdChatCache().get(chatId);
            if (cachedChat != null) {
                return CompletableFuture.completedFuture(TelegramMapper.mapToChatInfo(cachedChat));
            }

            // Get from server
            TdApi.GetChat request = new TdApi.GetChat();
            request.chatId = chatId;

            return clientProvider.getClient().send(request)
                    .thenApply(result -> {
                        if (result instanceof TdApi.Chat) {
                            TdApi.Chat chat = (TdApi.Chat) result;
                            cacheManager.getTdChatCache().put(chatId, chat);
                            return TelegramMapper.mapToChatInfo(chat);
                        }
                        return null;
                    })
                    .exceptionally(throwable -> {
                        log.error("Failed to get chat info for {}", chatId, throwable);
                        return null;
                    });
        } catch (Exception e) {
            log.error("Error getting chat info", e);
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Get multiple chats information
     */
    public CompletableFuture<List<ChatInfo>> getChatsInfo(List<Long> chatIds) {
        List<CompletableFuture<ChatInfo>> futures = new ArrayList<>();
        
        for (Long chatId : chatIds) {
            futures.add(getChatInfo(chatId));
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<ChatInfo> chatInfos = new ArrayList<>();
                    for (CompletableFuture<ChatInfo> future : futures) {
                        ChatInfo chatInfo = future.join();
                        if (chatInfo != null) {
                            chatInfos.add(chatInfo);
                        }
                    }
                    return chatInfos;
                });
    }

    /**
     * Get all chats (recent chats)
     */
    public CompletableFuture<List<ChatInfo>> getAllChats(int limit) {
        try {
            TdApi.GetChats request = new TdApi.GetChats();
            request.limit = limit;

            return clientProvider.getClient().send(request)
                    .thenCompose(result -> {
                        if (result instanceof TdApi.Chats) {
                            TdApi.Chats chats = (TdApi.Chats) result;
                            List<Long> chatIds = new ArrayList<>();
                            for (long chatId : chats.chatIds) {
                                chatIds.add(chatId);
                            }
                            return getChatsInfo(chatIds);
                        }
                        return CompletableFuture.completedFuture(new ArrayList<>());
                    })
                    .exceptionally(throwable -> {
                        log.error("Failed to get all chats", throwable);
                        return new ArrayList<>();
                    });
        } catch (Exception e) {
            log.error("Error getting all chats", e);
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
    }

    /**
     * Search chats by title
     */
    public CompletableFuture<List<ChatInfo>> searchChats(String query, int limit) {
        try {
            TdApi.SearchChats request = new TdApi.SearchChats();
            request.query = query;
            request.limit = limit;

            return clientProvider.getClient().send(request)
                    .thenCompose(result -> {
                        if (result instanceof TdApi.Chats) {
                            TdApi.Chats chats = (TdApi.Chats) result;
                            List<Long> chatIds = new ArrayList<>();
                            for (long chatId : chats.chatIds) {
                                chatIds.add(chatId);
                            }
                            return getChatsInfo(chatIds);
                        }
                        return CompletableFuture.completedFuture(new ArrayList<>());
                    })
                    .exceptionally(throwable -> {
                        log.error("Failed to search chats with query: {}", query, throwable);
                        return new ArrayList<>();
                    });
        } catch (Exception e) {
            log.error("Error searching chats", e);
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
    }

    /**
     * Get supergroup full info
     */
    private CompletableFuture<ChatInfo> getSupergroupFullInfo(Long chatId, ChatInfo chatInfo) {
        try {
            TdApi.Chat chat = cacheManager.getTdChatCache().get(chatId);
            if (chat == null || !(chat.type instanceof TdApi.ChatTypeSupergroup)) {
                return CompletableFuture.completedFuture(chatInfo);
            }

            TdApi.ChatTypeSupergroup supergroup = (TdApi.ChatTypeSupergroup) chat.type;
            TdApi.GetSupergroupFullInfo request = new TdApi.GetSupergroupFullInfo();
            request.supergroupId = supergroup.supergroupId;

            return clientProvider.getClient().send(request)
                    .thenApply(result -> {
                        if (result instanceof TdApi.SupergroupFullInfo) {
                            TdApi.SupergroupFullInfo fullInfo = (TdApi.SupergroupFullInfo) result;
                            chatInfo.setDescription(fullInfo.description);
                            chatInfo.setMemberCount(fullInfo.memberCount);
                        }
                        return chatInfo;
                    })
                    .exceptionally(throwable -> {
                        log.debug("Failed to get supergroup full info for {}", chatId, throwable);
                        return chatInfo;
                    });
        } catch (Exception e) {
            log.error("Error getting supergroup full info", e);
            return CompletableFuture.completedFuture(chatInfo);
        }
    }

    /**
     * Get basic group full info
     */
    private CompletableFuture<ChatInfo> getBasicGroupFullInfo(Long chatId, ChatInfo chatInfo) {
        try {
            TdApi.Chat chat = cacheManager.getTdChatCache().get(chatId);
            if (chat == null || !(chat.type instanceof TdApi.ChatTypeBasicGroup)) {
                return CompletableFuture.completedFuture(chatInfo);
            }

            TdApi.ChatTypeBasicGroup basicGroup = (TdApi.ChatTypeBasicGroup) chat.type;
            TdApi.GetBasicGroupFullInfo request = new TdApi.GetBasicGroupFullInfo();
            request.basicGroupId = basicGroup.basicGroupId;

            return clientProvider.getClient().send(request)
                    .thenApply(result -> {
                        if (result instanceof TdApi.BasicGroupFullInfo) {
                            TdApi.BasicGroupFullInfo fullInfo = (TdApi.BasicGroupFullInfo) result;
                            chatInfo.setDescription(fullInfo.description);
                            chatInfo.setMemberCount(fullInfo.members.length);
                        }
                        return chatInfo;
                    })
                    .exceptionally(throwable -> {
                        log.debug("Failed to get basic group full info for {}", chatId, throwable);
                        return chatInfo;
                    });
        } catch (Exception e) {
            log.error("Error getting basic group full info", e);
            return CompletableFuture.completedFuture(chatInfo);
        }
    }
}

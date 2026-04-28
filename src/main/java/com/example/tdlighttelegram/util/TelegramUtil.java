package com.example.tdlighttelegram.util;

import com.example.tdlighttelegram.model.DownloadInfo;
import com.example.tdlighttelegram.model.MessageInfo;
import com.example.tdlighttelegram.service.TelegramService;
import com.example.tdlighttelegram.service.shared.TelegramCacheManager;
import com.example.tdlighttelegram.service.shared.TelegramClientProvider;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramUtil {
    private final TelegramCacheManager telegramCacheManager;
    private final TelegramClientProvider clientProvider;
    private final UserUtil userUtil;

    private SimpleTelegramClient getClient() {
        return clientProvider.getClient();
    }
    /**
     * Get chat information (with cache)
     */
    public CompletableFuture<TdApi.Chat> getChatInfo(Long chatId) {
        // Check cache first
        TdApi.Chat cachedChat = telegramCacheManager.getTdChatCache().get(chatId);
        if (cachedChat != null) {
            return CompletableFuture.completedFuture(cachedChat);
        }

        // Get from server
        TdApi.GetChat request = new TdApi.GetChat();
        request.chatId = chatId;

        return getClient().send(request)
                .thenApply(result -> {
                    if (result instanceof TdApi.Chat) {
                        TdApi.Chat chat = (TdApi.Chat) result;
                        telegramCacheManager.getTdChatCache().put(chatId, chat);
                        return chat;
                    }
                    return null;
                })
                .exceptionally(throwable -> {
                    log.debug("Failed to get chat info for {}", chatId, throwable);
                    return null;
                });
    }

    /**
     * Get user information from message (with cache)
     */
    public CompletableFuture<TdApi.User> getUserFromMessage(TdApi.Message message) {
        if (!(message.senderId instanceof TdApi.MessageSenderUser)) {
            return CompletableFuture.completedFuture(null);
        }

        Long userId = ((TdApi.MessageSenderUser) message.senderId).userId;

        // Check cache first
        TdApi.User cachedUser = telegramCacheManager.getTdUserCache().get(userId);
        if (cachedUser != null) {
            return CompletableFuture.completedFuture(cachedUser);
        }

        // Get from server
        TdApi.GetUser request = new TdApi.GetUser();
        request.userId = userId;

        return getClient().send(request)
                .thenApply(result -> {
                    if (result instanceof TdApi.User) {
                        TdApi.User user = (TdApi.User) result;
                        telegramCacheManager.getTdUserCache().put(userId, user);
                        return user;
                    }
                    return null;
                })
                .exceptionally(throwable -> {
                    log.debug("Failed to get user info for {}", userId, throwable);
                    return null;
                });
    }
    /**
     * Update local thumbnail path of message
     */
    public void updateMessageThumbnailPath(DownloadInfo download, String localPath) {
        try {
            // Check if this is a thumbnail download (based on file name)
            if (download.getFileName() != null && download.getFileName().startsWith("thumbnail_")) {
                Long messageId = download.getMessageId();
                Long chatId = download.getChatId();

                // Update thumbnail path in message history
                List<MessageInfo> chatMessages = telegramCacheManager.getMessageHistory(chatId);
                chatMessages.stream()
                        .filter(msg -> messageId.equals(msg.getId()))
                        .findFirst()
                        .ifPresent(msg -> {
                            msg.setThumbnailLocalPath(localPath);
                            log.info("Updated thumbnail path for message {}: {}", messageId, localPath);
                        });

                // Update thumbnail path in video message cache
                List<MessageInfo> videoMessages = telegramCacheManager.getGroupVideoMessagesCache().get(chatId);
                if (videoMessages != null) {
                    videoMessages.stream()
                            .filter(msg -> messageId.equals(msg.getId()))
                            .findFirst()
                            .ifPresent(msg -> {
                                msg.setThumbnailLocalPath(localPath);
                                log.debug("Updated thumbnail path in video cache for message {}: {}", messageId, localPath);
                            });
                }
            }
        } catch (Exception e) {
            log.error("Error updating message thumbnail path", e);
        }
    }
    /**
     * Initialize current user information
     */
    public void initializeCurrentUserId() {
        try {
            getMe().thenAccept(user -> {
                if (user != null) {
//                    telegramClientProvider.getCurrentUserId() = user.id;
//                    telegramClientProvider.getCurrentUser() = user;
                    // Share current user with provider
                    clientProvider.setCurrentUser(user);
                    log.info("Current user info set: {} ({})", user.id, userUtil.buildUserDisplayName(user));
                }
            }).exceptionally(throwable -> {
                log.warn("Failed to get current user info", throwable);
                return null;
            });
        } catch (Exception e) {
            log.warn("Error initializing current user info", e);
        }
    }
    /**
     * Get current user information
     */
    public CompletableFuture<TdApi.User> getMe() {
        if (getClient() == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Client not initialized")
            );
        }

        return getClient().getMeAsync();
    }
}

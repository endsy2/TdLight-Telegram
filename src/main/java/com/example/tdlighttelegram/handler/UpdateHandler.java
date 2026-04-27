package com.example.tdlighttelegram.handler;

import com.example.tdlighttelegram.mapping.MessageMapping;
import com.example.tdlighttelegram.model.GroupInfo;
import com.example.tdlighttelegram.model.MessageInfo;
import com.example.tdlighttelegram.service.AuthenticationService;
import com.example.tdlighttelegram.service.TelegramService;
import com.example.tdlighttelegram.service.WebSocketService;
import com.example.tdlighttelegram.service.shared.TelegramCacheManager;
import com.example.tdlighttelegram.service.shared.TelegramClientProvider;
import com.example.tdlighttelegram.service.shared.TelegramFormatHelper;
import com.example.tdlighttelegram.util.TelegramUtil;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateHandler {
    private final AuthenticationService authenticationService;
    private final TelegramUtil telegramUtil;
    private final MessageMapping messageMapping;
    private final WebSocketService webSocketService;
    private final TelegramClientProvider telegramClientProvider;
    private final TelegramCacheManager telegramCacheManager;
    private final TelegramFormatHelper telegramFormatHelper;

    /**
     * Handle authorization state updates
     */
    public void onUpdateAuthorizationState(TdApi.UpdateAuthorizationState update) {
        TdApi.AuthorizationState authorizationState = update.authorizationState;

        if (authorizationState instanceof TdApi.AuthorizationStateReady) {
            log.info("Telegram client is ready and logged in");
            authenticationService.setAuthenticationState("READY");
            // Initialize current user ID
            telegramUtil.initializeCurrentUserId();
        } else if (authorizationState instanceof TdApi.AuthorizationStateClosing) {
            log.info("Telegram client is closing...");
            authenticationService.setAuthenticationState("CLOSING");
        } else if (authorizationState instanceof TdApi.AuthorizationStateClosed) {
            log.info("Telegram client is closed");
            authenticationService.setAuthenticationState("CLOSED");
        } else if (authorizationState instanceof TdApi.AuthorizationStateLoggingOut) {
            log.info("Telegram client is logging out...");
            authenticationService.setAuthenticationState("LOGGING_OUT");
        } else if (authorizationState instanceof TdApi.AuthorizationStateWaitPhoneNumber) {
            log.info("Waiting for phone number...");
            authenticationService.setAuthenticationState("WAITING_FOR_PHONE");
        } else if (authorizationState instanceof TdApi.AuthorizationStateWaitCode) {
            TdApi.AuthorizationStateWaitCode waitCode = (TdApi.AuthorizationStateWaitCode) authorizationState;
            log.info("Waiting for verification code... Code info: {}", waitCode.codeInfo.type.getClass().getSimpleName());
            authenticationService.setAuthenticationState("WAITING_FOR_CODE");
        } else if (authorizationState instanceof TdApi.AuthorizationStateWaitPassword) {
            log.info("Waiting for password...");
            authenticationService.setAuthenticationState("WAITING_FOR_PASSWORD");
        }
    }

    /**
     * Handle new message updates from TDLib
     * Broadcasts to WebSocket for real-time UI updates
     */
    @Async
    public void onUpdateNewMessage(TdApi.UpdateNewMessage update) {
        try {
            TdApi.Message message = update.message;

            // Fetch chat and user info asynchronously
            CompletableFuture<TdApi.Chat> chatFuture = telegramUtil.getChatInfo(message.chatId);
            CompletableFuture<TdApi.User> userFuture = telegramUtil.getUserFromMessage(message);

            CompletableFuture.allOf(chatFuture, userFuture)
                    .thenAccept(v -> {
                        try {
                            TdApi.Chat chat = chatFuture.join();
                            TdApi.User user = userFuture.join();

                            MessageInfo messageInfo = messageMapping.convertToMessageInfo(message, chat, user);
                            telegramCacheManager.addMessageToHistory(messageInfo);

                            // Cache video messages
                            if ("VIDEO".equals(messageInfo.getMessageType())) {
                                Long chatId = messageInfo.getChatId();
                                telegramCacheManager.getGroupVideoMessagesCache().computeIfAbsent(chatId, k -> new ArrayList<>()).add(messageInfo);
                                log.info("Video message cached for group: {}", chatId);
                            }

                            // Format log output
                            String senderInfo = telegramFormatHelper.formatSenderInfo(user);
                            String chatInfo = telegramFormatHelper.formatChatInfo(chat);
                            String formattedContent = telegramFormatHelper.formatMessageContent(messageInfo.getContent());

                            // Check if message is from current user
                            boolean isFromCurrentUser = telegramClientProvider.getCurrentUserId() != null &&
                                    telegramClientProvider.getCurrentUser().equals(messageInfo.getSenderId());

                            if (isFromCurrentUser) {
                                log.info("📤 Message sent to chat: {} (ID: {})", chatInfo, message.chatId);
                                log.info("  Content: {}", formattedContent);
                            } else {
                                log.info("📥 New message from {} in chat: {} (ID: {})", senderInfo, chatInfo, message.chatId);
                                log.info("  Content: {}", formattedContent);
                            }

                            // ===== WEBSOCKET BROADCAST =====
                            // Broadcast new message to specific chat subscribers
                            webSocketService.sendNewMessage(message.chatId, messageInfo);

                            // Broadcast chat update to home screen (chat list)
                            // This updates the last message preview and timestamp
                            webSocketService.sendChatUpdated(chat);

                            log.debug("WebSocket broadcast sent for chat {}", message.chatId);
                            // ===== END WEBSOCKET BROADCAST =====

                        } catch (Exception e) {
                            log.error("Error processing message details", e);
                        }
                    })
                    .exceptionally(throwable -> {
                        log.error("Error getting chat/user info for message", throwable);
                        // Fallback: use basic info
                        try {
                            MessageInfo messageInfo = messageMapping.convertToMessageInfo(message);
                            telegramCacheManager.getMessageHistory().add(messageInfo);
                            String formattedContent = telegramFormatHelper.formatMessageContent(messageInfo.getContent());

                            // Check if message is from current user
                            boolean isFromCurrentUser = telegramClientProvider.getCurrentUser() != null &&
                                    telegramClientProvider.getCurrentUserId().equals(messageInfo.getSenderId());

                            if (isFromCurrentUser) {
                                log.info("📤 Message sent to chat ID: {}", message.chatId);
                                log.info("  Content: {}", formattedContent);
                            } else {
                                log.info("📥 New message (basic info) from chat ID: {}", message.chatId);
                                log.info("  Content: {}", formattedContent);
                            }

                            // Broadcast even with basic info
                            webSocketService.sendNewMessage(message.chatId, messageInfo);

                        } catch (Exception e) {
                            log.error("Error in fallback message processing", e);
                        }
                        return null;
                    });
        } catch (Exception e) {
            log.error("Error processing new message", e);
        }
    }
    /**
     * Handle new chat updates from TDLib
     * Broadcasts to WebSocket for real-time UI updates
     */
    @Async
    public void onUpdateNewChat(TdApi.UpdateNewChat update) {
        try {
            TdApi.Chat chat = update.chat;
            if (chat.type instanceof TdApi.ChatTypeSupergroup || chat.type instanceof TdApi.ChatTypeBasicGroup) {
                GroupInfo groupInfo = messageMapping.convertToGroupInfo(chat);
                telegramCacheManager.getGroupInfoCache().put(chat.id, groupInfo);

                log.info("New group discovered: {} (ID: {})", groupInfo.getTitle(), groupInfo.getId());

                // Broadcast new chat to WebSocket subscribers
                webSocketService.sendChatUpdated(chat);
            }
        } catch (Exception e) {
            log.error("Error processing new chat", e);
        }
    }
    /**
     * Handle chat member updates
     */
    @Async
    public void onUpdateChatMember(TdApi.UpdateChatMember update) {
        try {
            // Handle group member changes
            log.info("Chat member updated in chat: {}, user: {}, status: {}",
                    update.chatId, update.actorUserId, update.newChatMember.status.getClass().getSimpleName());
        } catch (Exception e) {
            log.error("Error processing chat member update", e);
        }
    }

    /**
     * Handle file download updates
     */
    @Async
    public void onUpdateFile(TdApi.UpdateFile update) {
        try {
            TdApi.File file = update.file;

            // Find corresponding download task
            telegramCacheManager.getDownloadCache().values().stream()
                    .filter(download -> download.getFileId().equals(file.id))
                    .findFirst()
                    .ifPresent(download -> {
                        // Update download progress
                        if (file.local.isDownloadingCompleted) {
                            download.setStatus("COMPLETED");
                            download.setProgress(100);
                            download.setDownloadedBytes(file.size);
                            download.setLocalPath(file.local.path);
                            download.setCompletedTime(LocalDateTime.now());
                            download.setUpdatedAt(LocalDateTime.now());

                            log.info("File download completed: {} -> {}", download.getFileName(), file.local.path);

                            // If thumbnail download is completed, update corresponding MessageInfo
                            telegramUtil.updateMessageThumbnailPath(download, file.local.path);

                        } else if (file.local.isDownloadingActive) {
                            download.setStatus("DOWNLOADING");
                            download.setDownloadedBytes((long) file.local.downloadedSize);
                            if (file.size > 0) {
                                download.setProgress((int) ((file.local.downloadedSize * 100L) / file.size));
                            }
                            download.setUpdatedAt(LocalDateTime.now());

                            log.debug("File download progress: {} - {}%", download.getFileName(), download.getProgress());
                        }
                    });
        } catch (Exception e) {
            log.error("Error processing file update", e);
        }
    }
}

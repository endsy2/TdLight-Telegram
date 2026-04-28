package com.example.tdlighttelegram.service;

import com.example.tdlighttelegram.model.MessageInfo;
import com.example.tdlighttelegram.model.WebSocketEvent;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * WebSocket service for sending real-time updates to clients
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Send new message event
     */
    public void sendNewMessage(Long chatId, MessageInfo message) {
        WebSocketEvent event = WebSocketEvent.builder()
                .type(WebSocketEvent.EventType.NEW_MESSAGE)
                .chatId(chatId)
                .messageId(message.getId())
                .data(message)
                .timestamp(LocalDateTime.now())
                .build();

        messagingTemplate.convertAndSend("/topic/chat/" + chatId, event);
        log.debug("Sent new message event for chat {}", chatId);
    }

    /**
     * Send chat updated event
     */
    public void sendChatUpdated(TdApi.Chat chat) {
        WebSocketEvent event = WebSocketEvent.builder()
                .type(WebSocketEvent.EventType.CHAT_UPDATED)
                .chatId(chat.id)
                .data(chat)
                .timestamp(LocalDateTime.now())
                .build();

        messagingTemplate.convertAndSend("/topic/chats", event);
        log.debug("Sent chat updated event for chat {}", chat.id);
    }

    /**
     * Send chats list update
     */
    public void sendChatsUpdate(List<TdApi.Chat> chats) {
        WebSocketEvent event = WebSocketEvent.builder()
                .type(WebSocketEvent.EventType.CHAT_UPDATED)
                .data(chats)
                .timestamp(LocalDateTime.now())
                .build();

        messagingTemplate.convertAndSend("/topic/chats", event);
        log.debug("Sent chats list update with {} chats", chats.size());
    }

    /**
     * Send download progress event
     */
    public void sendDownloadProgress(String downloadId, int progress) {
        WebSocketEvent event = WebSocketEvent.builder()
                .type(WebSocketEvent.EventType.DOWNLOAD_PROGRESS)
                .data(progress)
                .timestamp(LocalDateTime.now())
                .build();

        messagingTemplate.convertAndSend("/topic/downloads/" + downloadId, event);
        log.debug("Sent download progress event: {} - {}%", downloadId, progress);
    }

    /**
     * Send download completed event
     */
    public void sendDownloadCompleted(String downloadId, Object downloadInfo) {
        WebSocketEvent event = WebSocketEvent.builder()
                .type(WebSocketEvent.EventType.DOWNLOAD_COMPLETED)
                .data(downloadInfo)
                .timestamp(LocalDateTime.now())
                .build();

        messagingTemplate.convertAndSend("/topic/downloads/" + downloadId, event);
        log.debug("Sent download completed event: {}", downloadId);
    }

    /**
     * Send typing status event
     */
    public void sendTypingStatus(Long chatId, Long userId, boolean isTyping) {
        WebSocketEvent event = WebSocketEvent.builder()
                .type(WebSocketEvent.EventType.TYPING_STATUS)
                .chatId(chatId)
                .data(new TypingStatus(userId, isTyping))
                .timestamp(LocalDateTime.now())
                .build();

        messagingTemplate.convertAndSend("/topic/chat/" + chatId + "/typing", event);
        log.debug("Sent typing status event for chat {}: user {} - {}", chatId, userId, isTyping);
    }

    /**
     * Send authentication state changed event
     */
    public void sendAuthStateChanged(String state) {
        WebSocketEvent event = WebSocketEvent.builder()
                .type(WebSocketEvent.EventType.AUTH_STATE_CHANGED)
                .data(state)
                .timestamp(LocalDateTime.now())
                .build();

        messagingTemplate.convertAndSend("/topic/auth", event);
        log.debug("Sent auth state changed event: {}", state);
    }

    /**
     * Send error event
     */
    public void sendError(String message) {
        WebSocketEvent event = WebSocketEvent.builder()
                .type(WebSocketEvent.EventType.ERROR)
                .data(message)
                .timestamp(LocalDateTime.now())
                .build();

        messagingTemplate.convertAndSend("/topic/errors", event);
        log.debug("Sent error event: {}", message);
    }

    /**
     * Send notification event
     */
    public void sendNotification(String message) {
        WebSocketEvent event = WebSocketEvent.builder()
                .type(WebSocketEvent.EventType.NOTIFICATION)
                .data(message)
                .timestamp(LocalDateTime.now())
                .build();

        messagingTemplate.convertAndSend("/topic/notifications", event);
        log.debug("Sent notification event: {}", message);
    }

    /**
     * Typing status data class
     */
    public record TypingStatus(Long userId, boolean isTyping) {}
}

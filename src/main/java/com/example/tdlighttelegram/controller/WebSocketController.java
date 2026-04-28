package com.example.tdlighttelegram.controller;

import com.example.tdlighttelegram.service.TelegramService;
import com.example.tdlighttelegram.service.WebSocketService;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * WebSocket controller for handling real-time communication
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final TelegramService telegramService;
    private final WebSocketService webSocketService;

    /**
     * Subscribe to chat updates
     */
    @MessageMapping("/chats/subscribe")
    @SendTo("/topic/chats")
    public CompletableFuture<List<TdApi.Chat>> subscribeToChats() {
        log.info("Client subscribed to chats");
        return telegramService.getHistoryChat();
    }

    /**
     * Subscribe to specific chat messages
     */
    @MessageMapping("/chat/{chatId}/subscribe")
    @SendTo("/topic/chat/{chatId}")
    public void subscribeToChat(@DestinationVariable Long chatId) {
        log.info("Client subscribed to chat: {}", chatId);
    }

    /**
     * Send typing indicator
     */
    @MessageMapping("/chat/{chatId}/typing")
    public void sendTyping(@DestinationVariable Long chatId) {
        log.info("User is typing in chat: {}", chatId);
        // You can broadcast this to other users in the chat
        // webSocketService.sendTypingStatus(chatId, userId, true);
    }
}

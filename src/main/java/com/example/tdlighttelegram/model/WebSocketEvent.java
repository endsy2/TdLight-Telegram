package com.example.tdlighttelegram.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * WebSocket event model for real-time updates
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketEvent {
    
    /**
     * Event type
     */
    private EventType type;
    
    /**
     * Event data (can be any object)
     */
    private Object data;
    
    /**
     * Timestamp
     */
    private LocalDateTime timestamp;
    
    /**
     * Chat ID (if applicable)
     */
    private Long chatId;
    
    /**
     * Message ID (if applicable)
     */
    private Long messageId;
    
    /**
     * Event types
     */
    public enum EventType {
        // Chat events
        NEW_CHAT,
        CHAT_UPDATED,
        CHAT_DELETED,
        
        // Message events
        NEW_MESSAGE,
        MESSAGE_UPDATED,
        MESSAGE_DELETED,
        
        // User events
        USER_STATUS_CHANGED,
        TYPING_STATUS,
        
        // Download events
        DOWNLOAD_PROGRESS,
        DOWNLOAD_COMPLETED,
        DOWNLOAD_FAILED,
        
        // Authentication events
        AUTH_STATE_CHANGED,
        
        // General events
        ERROR,
        NOTIFICATION
    }
}

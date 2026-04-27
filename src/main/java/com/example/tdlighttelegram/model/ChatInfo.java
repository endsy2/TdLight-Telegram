package com.example.tdlighttelegram.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Chat Information Model
 * Represents detailed information about a Telegram chat
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatInfo {
    
    /**
     * Chat ID
     */
    private Long id;
    
    /**
     * Chat title/name
     */
    private String title;
    
    /**
     * Chat type (private, group, supergroup, channel)
     */
    private String type;
    
    /**
     * Chat username (for public chats)
     */
    private String username;
    
    /**
     * Chat description
     */
    private String description;
    
    /**
     * Number of members (for groups)
     */
    private Integer memberCount;
    
    /**
     * Is the chat a channel
     */
    private Boolean isChannel;
    
    /**
     * Is the chat a supergroup
     */
    private Boolean isSupergroup;
    
    /**
     * Is the chat verified
     */
    private Boolean isVerified;
    
    /**
     * Is the chat restricted
     */
    private Boolean isRestricted;
    
    /**
     * Can send messages to this chat
     */
    private Boolean canSendMessages;
    
    /**
     * Chat photo URL (if available)
     */
    private String photoUrl;
    
    /**
     * Last message date
     */
    private LocalDateTime lastMessageDate;
    
    /**
     * Unread message count
     */
    private Integer unreadCount;
    
    /**
     * Chat permissions
     */
    private ChatPermissions permissions;
    
    /**
     * When this info was retrieved
     */
    private LocalDateTime retrievedAt;
    
    /**
     * Chat Permissions nested class
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatPermissions {
        private Boolean canSendMessages;
        private Boolean canSendMediaMessages;
        private Boolean canSendPolls;
        private Boolean canSendOtherMessages;
        private Boolean canAddWebPagePreviews;
        private Boolean canChangeInfo;
        private Boolean canInviteUsers;
        private Boolean canPinMessages;
    }
}

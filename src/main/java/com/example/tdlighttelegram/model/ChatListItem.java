package com.example.tdlighttelegram.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Chat list item - represents a chat in the home screen list
 * Matches Telegram's home screen data structure
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatListItem {
    
    // Basic chat info
    private Long chatId;
    private String title;
    private String type; // "private", "group", "supergroup", "channel"
    private String photoUrl;
    
    // Last message info
    private Long lastMessageId;
    private String lastMessageText;
    private String lastMessageType; // "text", "photo", "video", "voice", "document", etc.
    private LocalDateTime lastMessageDate;
    private Boolean isOutgoing; // true if sent by current user
    
    // Sender info (for groups)
    private Long senderId;
    private String senderName;
    private String senderType; // "user" or "chat"
    
    // Message status (for outgoing messages)
    private String messageStatus; // "sending", "sent", "delivered", "read", "failed"
    private Integer viewCount; // for channels
    
    // Unread info
    private Integer unreadCount;
    private Integer unreadMentionCount;
    private Boolean hasUnreadMention;
    private Boolean isMuted;
    
    // Chat status
    private Boolean isPinned;
    private Boolean isMarkedAsUnread;
    private Long pinnedOrder; // for sorting pinned chats
    
    // User/Group status
    private String userStatus; // "online", "offline", "recently", "lastWeek", "lastMonth"
    private LocalDateTime lastOnline;
    private Boolean isTyping;
    private String typingStatus; // "typing", "recording_video", "uploading_photo", etc.
    
    // Draft message
    private String draftText;
    private LocalDateTime draftDate;
    
    // Permissions and settings
    private Boolean canSendMessages;
    private Boolean isBlocked;
    private Boolean hasScheduledMessages;
    
    // Group/Channel specific
    private Integer memberCount;
    private Integer onlineMemberCount;
    
    // Verification and premium
    private Boolean isVerified;
    private Boolean isPremium;
    private Boolean isScam;
    private Boolean isFake;
    
    // Notification settings
    private Boolean hasCustomNotification;
    private Integer muteFor; // seconds until unmute
    
    // Position in list
    private Long order; // for sorting
    
    // Additional metadata
    private String description;
    private String username;
    private String phoneNumber; // for private chats
}

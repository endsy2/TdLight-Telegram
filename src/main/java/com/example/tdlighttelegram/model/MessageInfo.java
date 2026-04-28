package com.example.tdlighttelegram.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageInfo {
    
    private Long id;
    private Long chatId;
    private String chatTitle;
    private Long senderId;
    private String senderName;
    private String senderUsername;
    private String senderPhotoUrl; // Profile photo URL for message sender
    private String messageType; // TEXT, PHOTO, VIDEO, DOCUMENT, etc.
    private String content;
    private String mediaUrl;
    private String fileName;
    private Long fileSize;
    private Integer duration; // Duration in seconds for video/audio/voice messages
    private Integer fileId; 
    private String localPath; 
    
    
    private Integer thumbnailFileId; 
    private String thumbnailFormat;  
    private Integer thumbnailWidth;  
    private Integer thumbnailHeight; 
    private String thumbnailLocalPath; 
    
    // MinIO URLs
    private String minioUrl; // MinIO permanent URL for media file
    private String minioPresignedUrl; // MinIO temporary URL for media file
    private String thumbnailMinioUrl; // MinIO URL for thumbnail
    private String thumbnailMinioPresignedUrl; // MinIO temporary URL for thumbnail
    
    private Boolean isForwarded;
    private Long forwardedFromChatId;
    private String forwardedFromChatTitle;
    private Boolean isReply;
    private Long replyToMessageId;
    private LocalDateTime messageDate;
    private LocalDateTime receivedAt;
}

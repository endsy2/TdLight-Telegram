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
public class SendMessageResult {
    
    /**
     */
    private Boolean success;
    
    /**
     */
    private Long messageId;
    
    /**
     */
    private Long chatId;
    
    /**
     */
    private String content;
    
    /**
     */
    private LocalDateTime sentAt;
    
    /**
     */
    private String errorMessage;
    
    /**
     */
    private String errorCode;
    
    /**
     * MinIO URL (if file was uploaded to MinIO)
     */
    private String minioUrl;
    
    /**
     * MinIO presigned URL (temporary access)
     */
    private String minioPresignedUrl;
}

package com.example.tdlighttelegram.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageRequest {
    
    /**
     */
    private String content;
    
    /**
     */
    private String messageType;
    
    /**
     */
    private Boolean disableWebPagePreview;
    
    /**
     */
    private Boolean disableNotification;
    
    /**
     */
    private Long replyToMessageId;
    
    /**
     */
    private Boolean protectContent;

    private String filePath;

    private String fileName;
}

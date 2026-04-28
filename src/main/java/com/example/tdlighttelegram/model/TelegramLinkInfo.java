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
public class TelegramLinkInfo {
    
    /**
     */
    private String originalLink;
    
    /**
     */
    private String linkType;
    
    /**
     */
    private Long chatId;
    
    /**
     */
    private Long messageId;
    
    /**
     */
    private String username;
    
    /**
     */
    private Boolean isValid;
    
    /**
     */
    private String errorMessage;
}

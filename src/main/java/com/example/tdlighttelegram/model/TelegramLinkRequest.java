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
public class TelegramLinkRequest {
    
    /**
     * - https:
     * - https:
     * - https:
     */
    private String messageLink;
    
    /**
     */
    private String downloadType;
    
    /**
     */
    private Boolean downloadThumbnail;
    
    /**
     */
    private String fileNamePrefix;
}

package com.example.tdlighttelegram.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelegramLinkDownloadResult {
    
    /**
     */
    private String taskId;
    
    /**
     */
    private String originalLink;
    
    /**
     */
    private TelegramLinkInfo linkInfo;
    
    /**
     */
    private MessageInfo messageInfo;
    
    /**
     */
    private List<DownloadInfo> downloads;
    
    /**
     */
    private String status;
    
    /**
     */
    private Integer successCount;
    
    /**
     */
    private Integer failedCount;
    
    /**
     */
    private Integer totalCount;
    
    /**
     */
    private String errorMessage;
    
    /**
     */
    private LocalDateTime startTime;
    
    /**
     */
    private LocalDateTime completedTime;
    
    /**
     */
    private LocalDateTime createdAt;
    
    /**
     */
    private LocalDateTime updatedAt;
}

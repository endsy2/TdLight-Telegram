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
public class DownloadInfo {
    
    /**
     */
    private String downloadId;
    
    /**
     */
    private Long messageId;
    
    /**
     */
    private Long chatId;
    
    /**
     */
    private Integer fileId;
    
    /**
     */
    private String fileName;
    
    /**
     */
    private Long fileSize;
    
    /**
     */
    private String status;
    
    /**
     */
    private Integer progress;
    
    /**
     */
    private Long downloadedBytes;
    
    /**
     */
    private String localPath;
    
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
    
    /**
     * MinIO URL (if file was uploaded to MinIO)
     */
    private String minioUrl;
    
    /**
     * MinIO presigned URL (temporary access)
     */
    private String minioPresignedUrl;
    
    /**
     * MinIO bucket name
     */
    private String minioBucket;
    
    /**
     * MinIO object name
     */
    private String minioObjectName;
}

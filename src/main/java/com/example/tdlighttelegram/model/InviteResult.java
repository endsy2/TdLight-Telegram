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
public class InviteResult {
    
    /**
     */
    private String inviteId;
    
    /**
     */
    private Long groupId;
    
    /**
     */
    private String groupTitle;
    
    /**
     */
    private List<Long> userIds;
    
    /**
     */
    private String status;
    
    /**
     */
    private List<Long> successUserIds;
    
    /**
     */
    private List<Long> failedUserIds;
    
    /**
     */
    private List<InviteFailureDetail> failureDetails;
    
    /**
     */
    private Integer totalCount;
    
    /**
     */
    private Integer successCount;
    
    /**
     */
    private Integer failedCount;
    
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
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InviteFailureDetail {
        
        /**
         */
        private Long userId;
        
        /**
         */
        private String userDisplayName;
        
        /**
         */
        private String errorCode;
        
        /**
         */
        private String errorMessage;
        
        /**
         */
        private LocalDateTime failureTime;
    }
    
    /**
     */
    public double getSuccessRate() {
        if (totalCount == null || totalCount == 0) {
            return 0.0;
        }
        return (double) (successCount != null ? successCount : 0) / totalCount * 100;
    }
    
    /**
     */
    public boolean isCompleteSuccess() {
        return "SUCCESS".equals(status) && 
               successCount != null && 
               totalCount != null && 
               successCount.equals(totalCount);
    }
    
    /**
     */
    public boolean isPartialSuccess() {
        return "PARTIAL_SUCCESS".equals(status) || 
               (successCount != null && successCount > 0 && 
                failedCount != null && failedCount > 0);
    }
    
    /**
     */
    public boolean isCompleteFailed() {
        return "FAILED".equals(status) || 
               (successCount != null && successCount == 0 && 
                totalCount != null && totalCount > 0);
    }
    
    /**
     */
    public String getStatusDescription() {
        switch (status != null ? status : "UNKNOWN") {
            case "PENDING":
                return "Pending";
            case "SUCCESS":
                return "Success";
            case "PARTIAL_SUCCESS":
                return "Partial Success";
            case "FAILED":
                return "Failed";
            default:
                return "Unknown Status";
        }
    }
}

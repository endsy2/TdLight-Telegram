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
public class SendVoiceRequest {
    
    /**
     */
    private String voiceFilePath;
    
    /**
     */
    private Integer duration;
    
    /**
     */
    private byte[] waveform;
    
    /**
     */
    private String caption;
    
    /**
     */
    private Boolean disableNotification;
    
    /**
     */
    private Long replyToMessageId;
}

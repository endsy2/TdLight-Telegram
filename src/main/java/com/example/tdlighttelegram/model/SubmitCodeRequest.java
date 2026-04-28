package com.example.tdlighttelegram.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Submit Verification Code Request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitCodeRequest {
    
    /**
     * Verification code received from Telegram
     */
    private String code;
}

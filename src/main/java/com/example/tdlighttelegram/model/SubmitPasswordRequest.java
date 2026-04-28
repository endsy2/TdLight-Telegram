package com.example.tdlighttelegram.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Submit Password Request (for 2FA)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitPasswordRequest {
    
    /**
     * Two-factor authentication password
     */
    private String password;
}

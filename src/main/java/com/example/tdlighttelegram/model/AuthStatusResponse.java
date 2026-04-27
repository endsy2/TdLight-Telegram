package com.example.tdlighttelegram.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Authentication Status Response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthStatusResponse {
    
    /**
     * Current authentication state
     * Possible values: NONE, WAITING_FOR_PHONE, WAITING_FOR_CODE, WAITING_FOR_PASSWORD, READY, CLOSING, CLOSED, LOGGING_OUT
     */
    private String authenticationState;
    
    /**
     * Phone number (if provided)
     */
    private String phoneNumber;
    
    /**
     * Is authentication complete and ready to use
     */
    private Boolean isReady;
    
    /**
     * Needs verification code input
     */
    private Boolean needsCode;
    
    /**
     * Needs password input (2FA)
     */
    private Boolean needsPassword;
    
    /**
     * Waiting for phone number input
     */
    private Boolean isWaitingForPhone;
    
    /**
     * Timestamp of the response
     */
    private Long timestamp;
}

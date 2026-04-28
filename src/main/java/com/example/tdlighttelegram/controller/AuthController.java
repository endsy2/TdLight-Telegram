package com.example.tdlighttelegram.controller;

import com.example.tdlighttelegram.model.AuthStatusResponse;
import com.example.tdlighttelegram.model.SubmitCodeRequest;
import com.example.tdlighttelegram.model.SubmitPasswordRequest;
import com.example.tdlighttelegram.service.AuthenticationService;
import com.example.tdlighttelegram.service.TelegramService;
import com.example.tdlighttelegram.util.TelegramUtil;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Authentication Controller
 * Handles Telegram authentication flow for frontend integration
 */
@Slf4j
@RestController
@RequestMapping("/api/telegram/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final TelegramService telegramService;
    private final TelegramUtil telegramUtil;

    /**
     * Submit phone number to initiate authentication
     * 
     * POST /api/telegram/auth/phone
     * Body: { "phoneNumber": "+1234567890" }
     * 
     * @param request Phone number submission request
     * @return Success status
     */
    @PostMapping("/phone")
    public ResponseEntity<Map<String, Object>> submitPhoneNumber(@RequestBody Map<String, String> request) {
        try {
            String phoneNumber = request.get("phoneNumber");
            
            if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("Phone number is required"));
            }
            
            // Validate phone number format (basic validation)
            if (!phoneNumber.matches("^\\+?[1-9]\\d{1,14}$")) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("Invalid phone number format. Use international format (e.g., +1234567890)"));
            }
            
            log.info("Received phone number submission: {}", phoneNumber);
            
            // Submit phone number to TelegramService
            CompletableFuture<Boolean> result = telegramService.setAuthenticationPhoneNumber(phoneNumber);
            
            // Wait for result (with timeout)
            Boolean success = result.get(10, java.util.concurrent.TimeUnit.SECONDS);
            
            if (success) {
                log.info("Phone number submitted successfully");
                return ResponseEntity.ok(createSuccessResponse("Phone number submitted successfully. Check your Telegram app for verification code."));
            } else {
                log.warn("Failed to submit phone number");
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("Failed to submit phone number"));
            }
            
        } catch (java.util.concurrent.TimeoutException e) {
            log.error("Timeout submitting phone number", e);
            return ResponseEntity.status(408)
                    .body(createErrorResponse("Request timeout"));
        } catch (Exception e) {
            log.error("Failed to submit phone number", e);
            return ResponseEntity.internalServerError()
                    .body(createErrorResponse("Failed to submit phone number: " + e.getMessage()));
        }
    }

    /**
     * Get current authentication status
     * 
     * GET /api/telegram/auth/status
     * 
     * @return Authentication status with details
     */
    @GetMapping("/status")
    public ResponseEntity<AuthStatusResponse> getAuthStatus() {
        try {
            String state = authenticationService.getAuthenticationState();
            String phoneNumber = authenticationService.getPhoneNumber();
            
            boolean isReady = "READY".equals(state);
            boolean needsCode = "WAITING_FOR_CODE".equals(state);
            boolean needsPassword = "WAITING_FOR_PASSWORD".equals(state);
            boolean isWaitingForPhone = "WAITING_FOR_PHONE".equals(state) || "NONE".equals(state);
            
            AuthStatusResponse response = AuthStatusResponse.builder()
                    .authenticationState(state)
                    .phoneNumber(phoneNumber)
                    .isReady(isReady)
                    .needsCode(needsCode)
                    .needsPassword(needsPassword)
                    .isWaitingForPhone(isWaitingForPhone)
                    .timestamp(Instant.now().toEpochMilli())
                    .build();
            
            log.info("Auth status requested: {}", state);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to get auth status", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Submit verification code
     * 
     * POST /api/telegram/auth/code
     * Body: { "code": "12345" }
     * 
     * @param request Code submission request
     * @return Success status
     */
    @PostMapping("/code")
    public ResponseEntity<Map<String, Object>> submitCode(@RequestBody SubmitCodeRequest request) {
        try {
            log.info("Received verification code submission");
            
            if (request.getCode() == null || request.getCode().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("Code is required"));
            }
            
            // Submit code to TelegramService
            CompletableFuture<Boolean> result = telegramService.submitVerificationCode(request.getCode());
            
            // Wait for result (with timeout)
            Boolean success = result.get(10, java.util.concurrent.TimeUnit.SECONDS);
            
            if (success) {
                log.info("Verification code submitted successfully");
                return ResponseEntity.ok(createSuccessResponse("Code submitted successfully"));
            } else {
                log.warn("Failed to submit verification code");
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("Invalid verification code"));
            }
            
        } catch (java.util.concurrent.TimeoutException e) {
            log.error("Timeout submitting verification code", e);
            return ResponseEntity.status(408)
                    .body(createErrorResponse("Request timeout"));
        } catch (Exception e) {
            log.error("Failed to submit verification code", e);
            return ResponseEntity.internalServerError()
                    .body(createErrorResponse("Failed to submit code: " + e.getMessage()));
        }
    }

    /**
     * Submit password (for 2FA)
     * 
     * POST /api/telegram/auth/password
     * Body: { "password": "mypassword" }
     * 
     * @param request Password submission request
     * @return Success status
     */
    @PostMapping("/password")
    public ResponseEntity<Map<String, Object>> submitPassword(@RequestBody SubmitPasswordRequest request) {
        try {
            log.info("Received password submission");
            
            if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("Password is required"));
            }
            
            // Submit password to TelegramService
            CompletableFuture<Boolean> result = telegramService.submitPassword(request.getPassword());
            
            // Wait for result (with timeout)
            Boolean success = result.get(10, java.util.concurrent.TimeUnit.SECONDS);
            
            if (success) {
                log.info("Password submitted successfully");
                return ResponseEntity.ok(createSuccessResponse("Password submitted successfully"));
            } else {
                log.warn("Failed to submit password");
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("Invalid password"));
            }
            
        } catch (java.util.concurrent.TimeoutException e) {
            log.error("Timeout submitting password", e);
            return ResponseEntity.status(408)
                    .body(createErrorResponse("Request timeout"));
        } catch (Exception e) {
            log.error("Failed to submit password", e);
            return ResponseEntity.internalServerError()
                    .body(createErrorResponse("Failed to submit password: " + e.getMessage()));
        }
    }

    /**
     * Get current user information (to verify authentication)
     * 
     * GET /api/telegram/auth/me
     * 
     * @return Current user information
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser() {
        try {
            String state = authenticationService.getAuthenticationState();
            
            if (!"READY".equals(state)) {
                return ResponseEntity.status(401)
                        .body(createErrorResponse("Not authenticated"));
            }
            
            CompletableFuture<TdApi.User> userFuture = telegramUtil.getMe();
            TdApi.User user = userFuture.get(5, java.util.concurrent.TimeUnit.SECONDS);
            
            if (user == null) {
                return ResponseEntity.status(401)
                        .body(createErrorResponse("Failed to get user information"));
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("user", convertUserToMap(user));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to get current user", e);
            return ResponseEntity.internalServerError()
                    .body(createErrorResponse("Failed to get user: " + e.getMessage()));
        }
    }

    /**
     * Logout (reset authentication)
     * 
     * POST /api/telegram/auth/logout
     * 
     * @return Success status
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout() {
        try {
            log.info("Logout requested");
            authenticationService.reset();
            
            return ResponseEntity.ok(createSuccessResponse("Logged out successfully"));
            
        } catch (Exception e) {
            log.error("Failed to logout", e);
            return ResponseEntity.internalServerError()
                    .body(createErrorResponse("Failed to logout: " + e.getMessage()));
        }
    }

    /**
     * Health check endpoint
     * 
     * GET /api/telegram/auth/health
     * 
     * @return Health status
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "Authentication");
        response.put("timestamp", Instant.now().toEpochMilli());
        return ResponseEntity.ok(response);
    }

    // Helper methods

    private Map<String, Object> createSuccessResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("timestamp", Instant.now().toEpochMilli());
        return response;
    }

    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        response.put("timestamp", Instant.now().toEpochMilli());
        return response;
    }

    private Map<String, Object> convertUserToMap(TdApi.User user) {
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", user.id);
        userMap.put("firstName", user.firstName);
        userMap.put("lastName", user.lastName);
        userMap.put("phoneNumber", user.phoneNumber);
        userMap.put("isVerified", user.isVerified);
        userMap.put("isPremium", user.isPremium);
        
        if (user.usernames != null && user.usernames.activeUsernames.length > 0) {
            userMap.put("username", user.usernames.activeUsernames[0]);
        }
        
        userMap.put("isBot", user.type instanceof TdApi.UserTypeBot);
        
        return userMap;
    }
}

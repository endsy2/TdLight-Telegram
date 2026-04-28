# Backend Authentication Changes

This document outlines the changes needed in your backend to support Telegram-style login where users enter their phone number in the frontend.

## Overview

The authentication flow should work as follows:
1. User enters phone number in frontend
2. Backend receives phone number and initiates Telegram authentication
3. Telegram sends verification code to user's Telegram app
4. User enters code in frontend
5. If 2FA is enabled, user enters password
6. User is authenticated

## Required Backend Changes

### 1. Update AuthController

Add a new endpoint to receive phone numbers from the frontend:

```java
@RestController
@RequestMapping("/api/telegram/auth")
public class AuthController {
    
    @Autowired
    private TelegramAuthService authService;
    
    /**
     * Submit phone number to initiate authentication
     */
    @PostMapping("/phone")
    public ResponseEntity<Map<String, Object>> submitPhoneNumber(
            @RequestBody Map<String, String> request) {
        
        String phoneNumber = request.get("phoneNumber");
        
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                .body(Map.of(
                    "success", false,
                    "error", "Phone number is required"
                ));
        }
        
        try {
            // Send phone number to TDLib
            authService.setAuthenticationPhoneNumber(phoneNumber);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Phone number submitted successfully"
            ));
            
        } catch (Exception e) {
            return ResponseEntity.status(500)
                .body(Map.of(
                    "success", false,
                    "error", e.getMessage()
                ));
        }
    }
    
    /**
     * Get current authentication status
     */
    @GetMapping("/status")
    public ResponseEntity<AuthStatus> getAuthStatus() {
        AuthStatus status = authService.getAuthStatus();
        return ResponseEntity.ok(status);
    }
    
    /**
     * Submit verification code
     */
    @PostMapping("/code")
    public ResponseEntity<Map<String, Object>> submitCode(
            @RequestBody Map<String, String> request) {
        
        String code = request.get("code");
        
        if (code == null || code.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                .body(Map.of(
                    "success", false,
                    "error", "Verification code is required"
                ));
        }
        
        try {
            authService.checkAuthenticationCode(code);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Code submitted successfully"
            ));
            
        } catch (Exception e) {
            return ResponseEntity.status(500)
                .body(Map.of(
                    "success", false,
                    "error", e.getMessage()
                ));
        }
    }
    
    /**
     * Submit 2FA password
     */
    @PostMapping("/password")
    public ResponseEntity<Map<String, Object>> submitPassword(
            @RequestBody Map<String, String> request) {
        
        String password = request.get("password");
        
        if (password == null || password.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                .body(Map.of(
                    "success", false,
                    "error", "Password is required"
                ));
        }
        
        try {
            authService.checkAuthenticationPassword(password);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Password submitted successfully"
            ));
            
        } catch (Exception e) {
            return ResponseEntity.status(500)
                .body(Map.of(
                    "success", false,
                    "error", e.getMessage()
                ));
        }
    }
}
```

### 2. Update AuthStatus DTO

```java
public class AuthStatus {
    private String authenticationState;
    private boolean isReady;
    private boolean needsCode;
    private boolean needsPassword;
    private boolean isWaitingForPhone;
    private String phoneNumber;
    private long timestamp;
    
    // Constructors, getters, setters
}
```

### 3. Update TelegramAuthService

```java
@Service
public class TelegramAuthService {
    
    private Client client;
    private String currentAuthState = "UNKNOWN";
    private String currentPhoneNumber = null;
    
    /**
     * Set phone number for authentication
     */
    public void setAuthenticationPhoneNumber(String phoneNumber) {
        this.currentPhoneNumber = phoneNumber;
        
        // Send to TDLib
        TdApi.SetAuthenticationPhoneNumber setPhone = 
            new TdApi.SetAuthenticationPhoneNumber();
        setPhone.phoneNumber = phoneNumber;
        
        client.send(setPhone, new Client.ResultHandler() {
            @Override
            public void onResult(TdApi.Object object) {
                if (object instanceof TdApi.Error) {
                    TdApi.Error error = (TdApi.Error) object;
                    log.error("Failed to set phone number: {}", error.message);
                } else {
                    log.info("Phone number set successfully");
                }
            }
        });
    }
    
    /**
     * Submit verification code
     */
    public void checkAuthenticationCode(String code) {
        TdApi.CheckAuthenticationCode checkCode = 
            new TdApi.CheckAuthenticationCode();
        checkCode.code = code;
        
        client.send(checkCode, new Client.ResultHandler() {
            @Override
            public void onResult(TdApi.Object object) {
                if (object instanceof TdApi.Error) {
                    TdApi.Error error = (TdApi.Error) object;
                    log.error("Failed to check code: {}", error.message);
                } else {
                    log.info("Code verified successfully");
                }
            }
        });
    }
    
    /**
     * Submit 2FA password
     */
    public void checkAuthenticationPassword(String password) {
        TdApi.CheckAuthenticationPassword checkPassword = 
            new TdApi.CheckAuthenticationPassword();
        checkPassword.password = password;
        
        client.send(checkPassword, new Client.ResultHandler() {
            @Override
            public void onResult(TdApi.Object object) {
                if (object instanceof TdApi.Error) {
                    TdApi.Error error = (TdApi.Error) object;
                    log.error("Failed to check password: {}", error.message);
                } else {
                    log.info("Password verified successfully");
                }
            }
        });
    }
    
    /**
     * Get current authentication status
     */
    public AuthStatus getAuthStatus() {
        AuthStatus status = new AuthStatus();
        status.setAuthenticationState(currentAuthState);
        status.setPhoneNumber(currentPhoneNumber);
        status.setTimestamp(System.currentTimeMillis());
        
        // Determine what's needed based on state
        switch (currentAuthState) {
            case "WAIT_PHONE_NUMBER":
                status.setWaitingForPhone(true);
                status.setReady(false);
                status.setNeedsCode(false);
                status.setNeedsPassword(false);
                break;
                
            case "WAIT_CODE":
                status.setWaitingForPhone(false);
                status.setReady(false);
                status.setNeedsCode(true);
                status.setNeedsPassword(false);
                break;
                
            case "WAIT_PASSWORD":
                status.setWaitingForPhone(false);
                status.setReady(false);
                status.setNeedsCode(false);
                status.setNeedsPassword(true);
                break;
                
            case "READY":
                status.setWaitingForPhone(false);
                status.setReady(true);
                status.setNeedsCode(false);
                status.setNeedsPassword(false);
                break;
                
            default:
                status.setWaitingForPhone(false);
                status.setReady(false);
                status.setNeedsCode(false);
                status.setNeedsPassword(false);
        }
        
        return status;
    }
    
    /**
     * Handle TDLib authorization state updates
     */
    private void onAuthorizationStateUpdated(TdApi.AuthorizationState authState) {
        if (authState instanceof TdApi.AuthorizationStateWaitPhoneNumber) {
            currentAuthState = "WAIT_PHONE_NUMBER";
            log.info("Waiting for phone number");
            
        } else if (authState instanceof TdApi.AuthorizationStateWaitCode) {
            currentAuthState = "WAIT_CODE";
            log.info("Waiting for verification code");
            
        } else if (authState instanceof TdApi.AuthorizationStateWaitPassword) {
            currentAuthState = "WAIT_PASSWORD";
            log.info("Waiting for 2FA password");
            
        } else if (authState instanceof TdApi.AuthorizationStateReady) {
            currentAuthState = "READY";
            log.info("Authorization complete");
            
        } else if (authState instanceof TdApi.AuthorizationStateLoggingOut) {
            currentAuthState = "LOGGING_OUT";
            log.info("Logging out");
            
        } else if (authState instanceof TdApi.AuthorizationStateClosing) {
            currentAuthState = "CLOSING";
            log.info("Closing");
            
        } else if (authState instanceof TdApi.AuthorizationStateClosed) {
            currentAuthState = "CLOSED";
            log.info("Closed");
        }
    }
}
```

### 4. Remove Phone Number from application.yml

Since users will now enter their phone number in the frontend, you can remove the hardcoded phone number from your configuration:

```yaml
# application.yml - REMOVE OR COMMENT OUT
telegram:
  api-id: ${TELEGRAM_API_ID}
  api-hash: ${TELEGRAM_API_HASH}
  # phone-number: "+1234567890"  # Remove this line
```

### 5. Update TDLib Client Initialization

Make sure your TDLib client doesn't automatically send a phone number on startup:

```java
@PostConstruct
public void init() {
    // Initialize TDLib client
    client = Client.create(
        new UpdateHandler(),
        null,
        null
    );
    
    // Set TDLib parameters
    TdApi.SetTdlibParameters parameters = new TdApi.SetTdlibParameters();
    parameters.apiId = apiId;
    parameters.apiHash = apiHash;
    parameters.systemLanguageCode = "en";
    parameters.deviceModel = "Server";
    parameters.applicationVersion = "1.0";
    parameters.databaseDirectory = "tdlib";
    parameters.useMessageDatabase = true;
    parameters.useSecretChats = false;
    
    client.send(parameters, new Client.ResultHandler() {
        @Override
        public void onResult(TdApi.Object object) {
            if (object instanceof TdApi.Error) {
                log.error("Failed to set parameters: {}", 
                    ((TdApi.Error) object).message);
            } else {
                log.info("TDLib parameters set successfully");
                // Don't automatically send phone number here
                // Wait for user to submit via frontend
            }
        }
    });
}
```

## Testing the Changes

1. Start your backend server
2. Open the frontend at http://localhost:3000
3. You should see a phone number input form
4. Enter your phone number with country code (e.g., +1234567890)
5. Click "Continue"
6. Check your Telegram app for the verification code
7. Enter the code in the frontend
8. If you have 2FA enabled, enter your password
9. You should be logged in and redirected to /chats

## API Endpoints Summary

- `POST /api/telegram/auth/phone` - Submit phone number
- `GET /api/telegram/auth/status` - Get authentication status
- `POST /api/telegram/auth/code` - Submit verification code
- `POST /api/telegram/auth/password` - Submit 2FA password

## Frontend Changes Already Made

✅ Added phone number input form in AuthPage
✅ Added `sendPhoneNumber` method to authApi
✅ Updated AuthStatus type to include `isWaitingForPhone` and `phoneNumber`
✅ Updated authentication flow to start with phone input
✅ Added proper validation and error handling

## Next Steps

1. Implement the backend changes above
2. Test the authentication flow
3. Remove any hardcoded phone numbers from your configuration
4. Deploy and enjoy Telegram-style login!

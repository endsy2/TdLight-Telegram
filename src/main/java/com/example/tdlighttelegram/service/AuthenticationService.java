package com.example.tdlighttelegram.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Authentication service - handles Telegram authentication flow
 */
@Slf4j
@Service
public class AuthenticationService {

    private final AtomicReference<CompletableFuture<String>> codePromise = new AtomicReference<>();
    private final AtomicReference<CompletableFuture<String>> passwordPromise = new AtomicReference<>();
    private volatile String authenticationState = "NONE";
    private volatile String phoneNumber = "";

    /**
     * Wait for verification code input
     */
    public CompletableFuture<String> waitForCode(String phone) {
        log.info("Waiting for verification code for phone: {}", phone);
        this.phoneNumber = phone;
        this.authenticationState = "WAITING_FOR_CODE";

        CompletableFuture<String> promise = new CompletableFuture<>();
        codePromise.set(promise);
        return promise;
    }

    /**
     * Wait for password input (two-factor authentication)
     */
    public CompletableFuture<String> waitForPassword() {
        log.info("Waiting for password (2FA)");
        this.authenticationState = "WAITING_FOR_PASSWORD";

        CompletableFuture<String> promise = new CompletableFuture<>();
        passwordPromise.set(promise);
        return promise;
    }

    /**
     * Submit verification code
     */
    public boolean submitCode(String code) {
        log.info("Submitting verification code: {}", code);
        CompletableFuture<String> promise = codePromise.get();
        if (promise != null && !promise.isDone()) {
            promise.complete(code);
            this.authenticationState = "CODE_SUBMITTED";
            return true;
        }
        return false;
    }

    /**
     * Submit password
     */
    public boolean submitPassword(String password) {
        log.info("Submitting password");
        CompletableFuture<String> promise = passwordPromise.get();
        if (promise != null && !promise.isDone()) {
            promise.complete(password);
            this.authenticationState = "PASSWORD_SUBMITTED";
            return true;
        }
        return false;
    }

    /**
     * Get current authentication state
     */
    public String getAuthenticationState() {
        return authenticationState;
    }

    /**
     * Get phone number
     */
    public String getPhoneNumber() {
        return phoneNumber;
    }

    /**
     * Set authentication state
     */
    public void setAuthenticationState(String state) {
        this.authenticationState = state;
        log.info("Authentication state changed to: {}", state);
    }

    /**
     * Reset authentication state
     */
    public void reset() {
        CompletableFuture<String> codePromiseRef = codePromise.get();
        if (codePromiseRef != null && !codePromiseRef.isDone()) {
            codePromiseRef.cancel(true);
        }

        CompletableFuture<String> passwordPromiseRef = passwordPromise.get();
        if (passwordPromiseRef != null && !passwordPromiseRef.isDone()) {
            passwordPromiseRef.cancel(true);
        }

        codePromise.set(null);
        passwordPromise.set(null);
        authenticationState = "NONE";
        phoneNumber = "";
        log.info("Authentication service reset");
    }
}
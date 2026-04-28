package com.example.tdlighttelegram.service.shared;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Telegram Client Provider
 * Provides access to SimpleTelegramClient and ensures single client instance
 */
@Slf4j
@Component
public class TelegramClientProvider {

    @Getter
    private SimpleTelegramClient client;

    @Getter
    private Long currentUserId;

    @Getter
    private TdApi.User currentUser;

    /**
     * Set client instance
     */
    public void setClient(SimpleTelegramClient client) {
        this.client = client;
        log.debug("Telegram client instance set");
    }

    /**
     * Set current user information
     */
    public void setCurrentUser(TdApi.User user) {
        this.currentUser = user;
        this.currentUserId = user != null ? user.id : null;
        log.debug("Current user set: {}", currentUserId);
    }

    /**
     * Check if client is initialized
     */
    public boolean isInitialized() {
        return client != null;
    }
}

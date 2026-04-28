package com.example.tdlighttelegram.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Telegram configuration properties
 */
@Data
@Component
@ConfigurationProperties(prefix = "telegram")
public class TelegramProperties {

    /**
     * API ID
     */
    private int apiId;

    /**
     * API Hash
     */
    private String apiHash;

    /**
     * Phone number
     */
    private String phoneNumber;

    /**
     * Whether to use test datacenter
     */
    private boolean useTestDatacenter = false;

    /**
     * Session directory path
     */
    private String sessionPath = "tdlight-session";

    /**
     * Database directory path
     */
    private String databasePath = "tdlight-data";

    /**
     * Download files directory path
     */
    private String downloadsPath = "tdlight-downloads";

    /**
     * System language code
     */
    private String systemLanguageCode = "en";

    /**
     * Device model
     */
    private String deviceModel = "Desktop";

    /**
     * System version
     */
    private String systemVersion = "Unknown";

    /**
     * Application version
     */
    private String applicationVersion = "1.0";

    /**
     * Enable storage optimizer
     */
    private boolean enableStorageOptimizer = true;

    /**
     * Ignore file names
     */
    private boolean ignoreFileNames = false;
}
package com.example.tdlighttelegram.config;

import it.tdlight.Init;
import it.tdlight.Log;
import it.tdlight.Slf4JLogMessageHandler;
import it.tdlight.client.APIToken;
import it.tdlight.client.SimpleTelegramClientFactory;
import it.tdlight.client.TDLibSettings;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Telegram Config
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class TelegramConfig {
    
    private final TelegramProperties telegramProperties;
    private SimpleTelegramClientFactory clientFactory;
    
    @PostConstruct
    public void init() {
        try {

            Init.init();
            

            Log.setLogMessageHandler(1, new Slf4JLogMessageHandler());
            
            log.info("TDLight initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize TDLight", e);
            throw new RuntimeException("Failed to initialize TDLight", e);
        }
    }
    
    @Bean
    public SimpleTelegramClientFactory telegramClientFactory() {
        if (clientFactory == null) {
            clientFactory = new SimpleTelegramClientFactory();
        }
        return clientFactory;
    }
    
    @Bean
    public APIToken apiToken() {
        return new APIToken(telegramProperties.getApiId(), telegramProperties.getApiHash());
    }
    
    @Bean
    public TDLibSettings tdLibSettings(APIToken apiToken) {
        TDLibSettings settings = TDLibSettings.create(apiToken);
        

        Path sessionPath = Paths.get(telegramProperties.getSessionPath());
        settings.setDatabaseDirectoryPath(sessionPath.resolve("data"));
        settings.setDownloadedFilesDirectoryPath(sessionPath.resolve("downloads"));
        

        settings.setUseTestDatacenter(telegramProperties.isUseTestDatacenter());
        settings.setSystemLanguageCode(telegramProperties.getSystemLanguageCode());
        settings.setDeviceModel(telegramProperties.getDeviceModel());
        settings.setSystemVersion(telegramProperties.getSystemVersion());
        settings.setApplicationVersion(telegramProperties.getApplicationVersion());
        settings.setEnableStorageOptimizer(telegramProperties.isEnableStorageOptimizer());
        settings.setIgnoreFileNames(telegramProperties.isIgnoreFileNames());
        
        return settings;
    }
    
    @PreDestroy
    public void cleanup() {
        if (clientFactory != null) {
            try {
                clientFactory.close();
                log.info("TelegramClientFactory closed successfully");
            } catch (Exception e) {
                log.error("Error closing TelegramClientFactory", e);
            }
        }
    }
}

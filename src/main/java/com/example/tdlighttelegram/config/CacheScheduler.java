package com.example.tdlighttelegram.config;

import com.example.tdlighttelegram.service.shared.TelegramCacheManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cache Scheduler
 * Automatically maintains cache health by cleaning expired entries
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheScheduler {

    private final TelegramCacheManager cacheManager;

    /**
     * Clean expired cache entries every 5 minutes
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void cleanExpiredEntries() {
        try {
            log.debug("Running scheduled cache cleanup...");
            cacheManager.cleanExpiredEntries();
        } catch (Exception e) {
            log.error("Error during scheduled cache cleanup", e);
        }
    }

    /**
     * Optimize cache every 30 minutes
     */
    @Scheduled(fixedRate = 1800000) // 30 minutes
    public void optimizeCache() {
        try {
            log.debug("Running scheduled cache optimization...");
            cacheManager.optimizeCache();
        } catch (Exception e) {
            log.error("Error during scheduled cache optimization", e);
        }
    }

    /**
     * Log cache statistics every hour
     */
    @Scheduled(fixedRate = 3600000) // 1 hour
    public void logCacheStatistics() {
        try {
            var stats = cacheManager.getCacheStatistics();
            log.info("Cache Statistics: {}", stats);
        } catch (Exception e) {
            log.error("Error logging cache statistics", e);
        }
    }
}

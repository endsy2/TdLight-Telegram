package com.example.tdlighttelegram.controller;

import com.example.tdlighttelegram.service.shared.TelegramCacheManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Cache Controller
 * Provides endpoints for cache monitoring and management
 */
@Slf4j
@RestController
@RequestMapping("/api/telegram/cache")
@RequiredArgsConstructor
public class CacheController {

    private final TelegramCacheManager cacheManager;

    /**
     * Get cache statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getCacheStatistics() {
        try {
            Map<String, Object> stats = cacheManager.getCacheStatistics();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error getting cache statistics", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to get cache statistics"));
        }
    }

    /**
     * Clear all caches
     */
    @PostMapping("/clear")
    public ResponseEntity<Map<String, Object>> clearAllCaches() {
        try {
            cacheManager.clearAllCaches();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "All caches cleared successfully"
            ));
        } catch (Exception e) {
            log.error("Error clearing caches", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to clear caches"));
        }
    }

    /**
     * Clear message history for specific chat
     */
    @DeleteMapping("/messages/{chatId}")
    public ResponseEntity<Map<String, Object>> clearChatHistory(@PathVariable Long chatId) {
        try {
            cacheManager.clearMessageHistory(chatId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Chat history cleared for chat " + chatId
            ));
        } catch (Exception e) {
            log.error("Error clearing chat history for chat {}", chatId, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to clear chat history"));
        }
    }

    /**
     * Clean expired cache entries
     */
    @PostMapping("/clean")
    public ResponseEntity<Map<String, Object>> cleanExpiredEntries() {
        try {
            cacheManager.cleanExpiredEntries();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Expired cache entries cleaned"
            ));
        } catch (Exception e) {
            log.error("Error cleaning expired entries", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to clean expired entries"));
        }
    }

    /**
     * Optimize cache
     */
    @PostMapping("/optimize")
    public ResponseEntity<Map<String, Object>> optimizeCache() {
        try {
            cacheManager.optimizeCache();
            Map<String, Object> stats = cacheManager.getCacheStatistics();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Cache optimized successfully",
                    "stats", stats
            ));
        } catch (Exception e) {
            log.error("Error optimizing cache", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to optimize cache"));
        }
    }

    /**
     * Reset cache statistics
     */
    @PostMapping("/stats/reset")
    public ResponseEntity<Map<String, Object>> resetStatistics() {
        try {
            cacheManager.resetStatistics();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Cache statistics reset"
            ));
        } catch (Exception e) {
            log.error("Error resetting statistics", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to reset statistics"));
        }
    }

    /**
     * Get memory usage
     */
    @GetMapping("/memory")
    public ResponseEntity<Map<String, Object>> getMemoryUsage() {
        try {
            double memoryUsageMB = cacheManager.getEstimatedMemoryUsageMB();
            Runtime runtime = Runtime.getRuntime();
            
            return ResponseEntity.ok(Map.of(
                    "usedMemoryMB", memoryUsageMB,
                    "totalMemoryMB", runtime.totalMemory() / (1024.0 * 1024.0),
                    "freeMemoryMB", runtime.freeMemory() / (1024.0 * 1024.0),
                    "maxMemoryMB", runtime.maxMemory() / (1024.0 * 1024.0)
            ));
        } catch (Exception e) {
            log.error("Error getting memory usage", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to get memory usage"));
        }
    }
}

package com.example.tdlighttelegram.controller;

import com.example.tdlighttelegram.model.ChatInfo;
import com.example.tdlighttelegram.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Chat Controller
 * REST API endpoints for chat operations
 */
@Slf4j
@RestController
@RequestMapping("/api/telegram/chats")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * Get chat information by ID
     * 
     * @param chatId Chat ID
     * @return Chat information
     */
    @GetMapping("/{chatId}")
    public ResponseEntity<ChatInfo> getChatInfo(@PathVariable Long chatId) {
        try {
            log.info("Getting chat info for chat ID: {}", chatId);
            
            ChatInfo chatInfo = chatService.getChatInfo(chatId)
                    .get(10, TimeUnit.SECONDS);
            
            if (chatInfo != null) {
                return ResponseEntity.ok(chatInfo);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Failed to get chat info for chat ID: {}", chatId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get chat full information (with description, member count, etc.)
     * 
     * @param chatId Chat ID
     * @return Full chat information
     */
//    @GetMapping("/{chatId}/full")
//    public ResponseEntity<ChatInfo> getChatFullInfo(@PathVariable Long chatId) {
//        try {
//            log.info("Getting full chat info for chat ID: {}", chatId);
//
//            ChatInfo chatInfo = chatService.getChatFullInfo(chatId)
//                    .get(15, TimeUnit.SECONDS);
//
//            if (chatInfo != null) {
//                return ResponseEntity.ok(chatInfo);
//            } else {
//                return ResponseEntity.notFound().build();
//            }
//        } catch (Exception e) {
//            log.error("Failed to get full chat info for chat ID: {}", chatId, e);
//            return ResponseEntity.internalServerError().build();
//        }
//    }

    /**
     * Get all recent chats
     * 
     * @param limit Maximum number of chats to return (default: 50)
     * @return List of chat information
     */
    @GetMapping
    public ResponseEntity<List<ChatInfo>> getAllChats(
            @RequestParam(defaultValue = "50") int limit) {
        try {
            log.info("Getting all chats with limit: {}", limit);
            
            List<ChatInfo> chats = chatService.getAllChats(limit)
                    .get(15, TimeUnit.SECONDS);
            
            return ResponseEntity.ok(chats);
        } catch (Exception e) {
            log.error("Failed to get all chats", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Search chats by title
     * 
     * @param query Search query
     * @param limit Maximum number of results (default: 20)
     * @return List of matching chats
     */
    @GetMapping("/search")
    public ResponseEntity<List<ChatInfo>> searchChats(
            @RequestParam String query,
            @RequestParam(defaultValue = "20") int limit) {
        try {
            log.info("Searching chats with query: {}, limit: {}", query, limit);
            
            if (query == null || query.trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            
            List<ChatInfo> chats = chatService.searchChats(query, limit)
                    .get(15, TimeUnit.SECONDS);
            
            return ResponseEntity.ok(chats);
        } catch (Exception e) {
            log.error("Failed to search chats with query: {}", query, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get multiple chats information
     * 
     * @param chatIds List of chat IDs
     * @return List of chat information
     */
    @PostMapping("/batch")
    public ResponseEntity<List<ChatInfo>> getChatsInfo(@RequestBody List<Long> chatIds) {
        try {
            log.info("Getting info for {} chats", chatIds.size());
            
            if (chatIds == null || chatIds.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            
            List<ChatInfo> chats = chatService.getChatsInfo(chatIds)
                    .get(20, TimeUnit.SECONDS);
            
            return ResponseEntity.ok(chats);
        } catch (Exception e) {
            log.error("Failed to get chats info", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}

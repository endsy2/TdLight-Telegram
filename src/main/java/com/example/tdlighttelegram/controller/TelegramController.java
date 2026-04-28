package com.example.tdlighttelegram.controller;

import com.example.tdlighttelegram.mapping.MessageMapping;
import com.example.tdlighttelegram.model.*;
import com.example.tdlighttelegram.service.CallService;
import com.example.tdlighttelegram.service.ChatService;
import com.example.tdlighttelegram.service.TelegramService;
import com.example.tdlighttelegram.service.shared.TelegramCacheManager;
import com.example.tdlighttelegram.util.TelegramUtil;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Telegram Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/telegram")
@RequiredArgsConstructor
public class TelegramController {
    
    private final TelegramService telegramService;
    private final CallService callService;
    private final ChatService chatService;
    private final TelegramUtil telegramUtil;
    private final TelegramCacheManager telegramCacheManager;
    private final MessageMapping messageMapping;

    /**
     * Get current user information
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getMe() {
        try {
            TdApi.User user = telegramUtil.getMe().get(30, TimeUnit.SECONDS);
            
            Map<String, Object> result = new HashMap<>();
            result.put("id", user.id);
            result.put("firstName", user.firstName);
            result.put("lastName", user.lastName);
            result.put("username", user.usernames != null && user.usernames.activeUsernames.length > 0 ?
                    user.usernames.activeUsernames[0] : "");
            result.put("phoneNumber", user.phoneNumber);
            result.put("isBot", user.type instanceof TdApi.UserTypeBot);
            result.put("isVerified", user.isVerified);
            result.put("isPremium", user.isPremium);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error getting user info", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to get user info: " + e.getMessage()));
        }
    }
    
    /**
     * Join group
     */
    @PostMapping("/join-group")
    public ResponseEntity<Map<String, Object>> joinGroup(@RequestBody Map<String, String> request) {
        try {
            String inviteLink = request.get("inviteLink");
            if (inviteLink == null || inviteLink.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Invite link is required"));
            }
            
            Boolean success = telegramService.joinGroup(inviteLink).get(30, TimeUnit.SECONDS);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            result.put("message", success ? "Successfully joined group" : "Failed to join group");
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error joining group", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to join group: " + e.getMessage()));
        }
    }
    
    /**
     * Get group list
     */
    @GetMapping("/groups")
    public ResponseEntity<List<GroupInfo>> getGroups() {
        try {
            List<GroupInfo> groups = telegramService.getGroupInfos();
            return ResponseEntity.ok(groups);
        } catch (Exception e) {
            log.error("Error getting groups", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get message history for a specific chat (with pagination and caching)
     * Cache is only used for initial load (fromMessageId == null)
     */
    @GetMapping("/message/{chatId}/messages/history")
    public ResponseEntity<Map<String, Object>> getMessageHistoryByChatId(
            @PathVariable Long chatId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(required = false) Long fromMessageId,
            @RequestParam(defaultValue = "false") boolean forceRefresh) {
        try {
            log.debug("Fetching message history for chat {}: limit={}, offset={}, fromMessageId={}, forceRefresh={}",
                    chatId, limit, offset, fromMessageId, forceRefresh);

            // Validate parameters
            if (limit <= 0 || limit > 100) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Limit must be between 1 and 100"));
            }

            // Fetch chat profile information
            ChatInfo chatProfile = chatService.getChatInfo(chatId)
                    .get(10, TimeUnit.SECONDS);

            // Use cache only for initial load without pagination
            if (!forceRefresh && fromMessageId == null && offset == 0) {
                List<MessageInfo> cachedMessages = telegramCacheManager.getMessageHistory(chatId);
                if (!cachedMessages.isEmpty()) {
                    log.debug("Returning {} cached messages for chat {}", cachedMessages.size(), chatId);
                    return ResponseEntity.ok(Map.of(
                            "messages", cachedMessages.stream().limit(limit).toList(),
                            "chatId", chatId,
                            "profile", chatProfile != null ? chatProfile : Map.of(),
                            "fromCache", true,
                            "total", cachedMessages.size()
                    ));
                }
            }

            // Fetch from Telegram
            List<MessageInfo> messages = telegramService.getMessagesByChatId(chatId, limit, offset, fromMessageId)
                    .get(30, TimeUnit.SECONDS);

            // Cache messages only for initial load
            if (fromMessageId == null && offset == 0) {
                telegramCacheManager.clearMessageHistory(chatId);
                messages.forEach(msg -> telegramCacheManager.addMessageToHistory(chatId, msg));
                log.debug("Cached {} messages for chat {}", messages.size(), chatId);
            }

            return ResponseEntity.ok(Map.of(
                    "messages", messages,
                    "chatId", chatId,
                    "profile", chatProfile != null ? chatProfile : Map.of(),
                    "fromCache", false,
                    "total", messages.size()
            ));

        } catch (ExecutionException e) {
            log.error("Error getting message history for chat {}: {}", chatId, e.getCause().getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "error", "Failed to fetch messages: " + e.getCause().getMessage(),
                            "chatId", chatId
                    ));
        } catch (Exception e) {
            log.error("Unexpected error getting message history for chat {}", chatId, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "error", "Failed to fetch messages: " + e.getMessage(),
                            "chatId", chatId
                    ));
        }
    }
    /**
     * Open a chat to mark messages as read
     * Call this when user opens/views a chat
     */
    @PostMapping("/chats/{chatId}/open")
    public ResponseEntity<Map<String, Object>> openChat(@PathVariable Long chatId) {
        try {
            boolean success = telegramService.openChat(chatId)
                    .get(10, TimeUnit.SECONDS);

            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("chatId", chatId);
            response.put("message", success ? "Chat opened successfully" : "Failed to open chat");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error opening chat {}", chatId, e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("chatId", chatId);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    @GetMapping("/user-profile/{userId}")
    public CompletableFuture<ResponseEntity<ProfileResponse>> getUserProfile(@PathVariable int userId) {
        return telegramService.getUserProfile(userId)
                .thenApply(ResponseEntity::ok);
    }

    /**
     * Close a chat to stop marking messages as read
     * Call this when user leaves/closes a chat
     */
    @PostMapping("/chats/{chatId}/close")
    public ResponseEntity<Map<String, Object>> closeChat(@PathVariable Long chatId) {
        try {
            boolean success = telegramService.closeChat(chatId)
                    .get(10, TimeUnit.SECONDS);

            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("chatId", chatId);
            response.put("message", success ? "Chat closed successfully" : "Failed to close chat");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error closing chat {}", chatId, e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("chatId", chatId);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get information for a specific group
     */
    @GetMapping("/groups/{groupId}")
    public ResponseEntity<GroupInfo> getGroupInfo(@PathVariable Long groupId) {
        try {
            GroupInfo groupInfo = telegramService.getGroupInfo(groupId).get(30, TimeUnit.SECONDS);
            if (groupInfo != null) {
                return ResponseEntity.ok(groupInfo);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error getting group info for group {}", groupId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get video message list for a specific group (from cache)
     */
    @GetMapping("/groups/{groupId}/videos")
    public ResponseEntity<List<MessageInfo>> getGroupVideoMessages(@PathVariable Long groupId) {
        try {
            List<MessageInfo> videoMessages = telegramService.getGroupVideoMessages(groupId);
            return ResponseEntity.ok(videoMessages);
        } catch (Exception e) {
            log.error("Error getting group video messages for group {}", groupId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get video message history for a specific group (from Telegram server)
     */
    @GetMapping("/groups/{groupId}/videos/history")
    public ResponseEntity<List<MessageInfo>> getGroupVideoMessageHistory(
            @PathVariable Long groupId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) Long fromMessageId) {
        try {
            List<MessageInfo> videoMessages = telegramService.getGroupVideoMessages(groupId, limit, fromMessageId)
                    .get(30, TimeUnit.SECONDS);
            return ResponseEntity.ok(videoMessages);
        } catch (Exception e) {
            log.error("Error getting group video message history for group {}", groupId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Download video file
     */
    @PostMapping("/videos/download")
    public ResponseEntity<Map<String, Object>> downloadVideo(@RequestBody Map<String, Object> request) {
        try {
            Long messageId = Long.valueOf(request.get("messageId").toString());
            Long chatId = Long.valueOf(request.get("chatId").toString());

            DownloadInfo downloadInfo = telegramService.downloadVideo(messageId, chatId)
                    .get(30, TimeUnit.SECONDS);

            Map<String, Object> result = new HashMap<>();
            result.put("downloadId", downloadInfo.getDownloadId());
            result.put("status", downloadInfo.getStatus());
            result.put("fileName", downloadInfo.getFileName());
            result.put("fileSize", downloadInfo.getFileSize());
            result.put("progress", downloadInfo.getProgress());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error starting video download", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to start download: " + e.getMessage()));
        }
    }

    /**
     * Get download status
     */
    @GetMapping("/downloads/{downloadId}/status")
    public ResponseEntity<DownloadInfo> getDownloadStatus(@PathVariable String downloadId) {
        try {
            DownloadInfo downloadInfo = telegramService.getDownloadStatus(downloadId);
            if (downloadInfo != null) {
                return ResponseEntity.ok(downloadInfo);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error getting download status for {}", downloadId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Cancel download
     */
    @DeleteMapping("/downloads/{downloadId}")
    public ResponseEntity<Map<String, Object>> cancelDownload(@PathVariable String downloadId) {
        try {
            Boolean success = telegramService.cancelDownload(downloadId).get(30, TimeUnit.SECONDS);

            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            result.put("message", success ? "Download cancelled successfully" : "Failed to cancel download");

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error cancelling download {}", downloadId, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to cancel download: " + e.getMessage()));
        }
    }

    /**
     * Get all download tasks
     */
    @GetMapping("/downloads")
    public ResponseEntity<List<DownloadInfo>> getAllDownloads() {
        try {
            List<DownloadInfo> downloads = telegramService.getAllDownloads();
            return ResponseEntity.ok(downloads);
        } catch (Exception e) {
            log.error("Error getting all downloads", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Download video thumbnail
     */
    @PostMapping("/videos/thumbnail/download")
    public ResponseEntity<Map<String, Object>> downloadVideoThumbnail(@RequestBody Map<String, Object> request) {
        try {
            Long messageId = Long.valueOf(request.get("messageId").toString());
            Long chatId = Long.valueOf(request.get("chatId").toString());

            DownloadInfo downloadInfo = telegramService.downloadVideoThumbnail(messageId, chatId)
                    .get(30, TimeUnit.SECONDS);

            Map<String, Object> result = new HashMap<>();
            result.put("downloadId", downloadInfo.getDownloadId());
            result.put("status", downloadInfo.getStatus());
            result.put("fileName", downloadInfo.getFileName());
            result.put("fileSize", downloadInfo.getFileSize());
            result.put("progress", downloadInfo.getProgress());
            result.put("messageId", messageId);
            result.put("chatId", chatId);
            result.put("type", "thumbnail");

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error starting thumbnail download", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to start thumbnail download: " + e.getMessage()));
        }
    }

    /**
     * Get group member list
     */
    @GetMapping("/groups/{groupId}/members")
    public ResponseEntity<List<GroupMemberInfo>> getGroupMembers(
            @PathVariable Long groupId,
            @RequestParam(defaultValue = "true") boolean excludeAdmins,
            @RequestParam(defaultValue = "true") boolean onlyActiveUsers,
            @RequestParam(defaultValue = "true") boolean excludeBots) {
        try {
            log.info("Getting member list for group {}, exclude admins: {}, active users only: {}, exclude bots: {}", groupId, excludeAdmins, onlyActiveUsers, excludeBots);

            List<GroupMemberInfo> members = telegramService.getGroupMembers(groupId, excludeAdmins, onlyActiveUsers, excludeBots)
                    .get(60, TimeUnit.SECONDS); // Increase timeout because fetching members may take longer

            log.info("Successfully retrieved {} members from group {}", members.size(), groupId);
            return ResponseEntity.ok(members);
        } catch (Exception e) {
            log.error("Failed to get members for group {}", groupId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get group member list (from cache)
     */
    @GetMapping("/groups/{groupId}/members/cached")
    public ResponseEntity<List<GroupMemberInfo>> getCachedGroupMembers(@PathVariable Long groupId) {
        try {
            List<GroupMemberInfo> members = telegramService.getCachedGroupMembers(groupId);
            log.info("Retrieved {} members for group {} from cache", members.size(), groupId);
            return ResponseEntity.ok(members);
        } catch (Exception e) {
            log.error("Failed to get cached members for group {}", groupId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Invite users to a group
     */
    @PostMapping("/groups/{groupId}/invite")
    public ResponseEntity<Map<String, Object>> inviteUsersToGroup(
            @PathVariable Long groupId,
            @RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<Long> userIds = (List<Long>) request.get("userIds");

            if (userIds == null || userIds.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "User ID list cannot be empty"));
            }

            log.info("Starting invitation of {} users to group {}", userIds.size(), groupId);

            InviteResult result = telegramService.inviteUsersToGroup(groupId, userIds)
                    .get(120, TimeUnit.SECONDS); // Invitations may take longer

            Map<String, Object> response = new HashMap<>();
            response.put("inviteId", result.getInviteId());
            response.put("status", result.getStatus());
            response.put("totalCount", result.getTotalCount());
            response.put("successCount", result.getSuccessCount());
            response.put("failedCount", result.getFailedCount());
            response.put("successRate", result.getSuccessRate());
            response.put("groupTitle", result.getGroupTitle());
            response.put("startTime", result.getStartTime());
            response.put("completedTime", result.getCompletedTime());

            if (result.getFailureDetails() != null && !result.getFailureDetails().isEmpty()) {
                response.put("failureDetails", result.getFailureDetails());
            }

                log.info("Invitation completed - invite ID: {}, success: {}/{}, success rate: {:.1f}%",
                    result.getInviteId(), result.getSuccessCount(), result.getTotalCount(), result.getSuccessRate());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
                log.error("Failed to invite users to group {}", groupId, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Invitation failed: " + e.getMessage()));
        }
    }

    /**
     * Get invitation result
     */
    @GetMapping("/invites/{inviteId}")
    public ResponseEntity<InviteResult> getInviteResult(@PathVariable String inviteId) {
        try {
            InviteResult result = telegramService.getInviteResult(inviteId);
            if (result != null) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Failed to get invitation result {}", inviteId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Batch invite users to multiple groups
     */
    @PostMapping("/groups/batch-invite")
    public ResponseEntity<Map<String, Object>> batchInviteUsers(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<Long> groupIds = (List<Long>) request.get("groupIds");
            @SuppressWarnings("unchecked")
            List<Long> userIds = (List<Long>) request.get("userIds");

            if (groupIds == null || groupIds.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Group ID list cannot be empty"));
            }

            if (userIds == null || userIds.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "User ID list cannot be empty"));
            }

            log.info("Starting batch invitation of {} users to {} groups", userIds.size(), groupIds.size());

            Map<String, Object> response = new HashMap<>();
            Map<Long, InviteResult> results = new HashMap<>();

            // Invite group by group
            for (Long groupId : groupIds) {
                try {
                    InviteResult result = telegramService.inviteUsersToGroup(groupId, userIds)
                            .get(120, TimeUnit.SECONDS);
                    results.put(groupId, result);

                    log.info("Group {} invitation completed - success: {}/{}", groupId, result.getSuccessCount(), result.getTotalCount());
                } catch (Exception e) {
                    log.error("Failed to invite users to group {}", groupId, e);
                    // Create failure result
                    InviteResult failedResult = InviteResult.builder()
                            .groupId(groupId)
                            .status("FAILED")
                            .errorMessage(e.getMessage())
                            .totalCount(userIds.size())
                            .successCount(0)
                            .failedCount(userIds.size())
                            .build();
                    results.put(groupId, failedResult);
                }
            }

            // Aggregate overall result
            int totalInvites = groupIds.size() * userIds.size();
            int totalSuccess = results.values().stream()
                    .mapToInt(r -> r.getSuccessCount() != null ? r.getSuccessCount() : 0)
                    .sum();
            int totalFailed = totalInvites - totalSuccess;

            response.put("totalGroups", groupIds.size());
            response.put("totalUsers", userIds.size());
            response.put("totalInvites", totalInvites);
            response.put("totalSuccess", totalSuccess);
            response.put("totalFailed", totalFailed);
            response.put("overallSuccessRate", totalInvites > 0 ? (double) totalSuccess / totalInvites * 100 : 0);
            response.put("results", results);

                log.info("Batch invitation completed - total: {}, success: {}, failed: {}, success rate: {:.1f}%",
                    totalInvites, totalSuccess, totalFailed,
                    totalInvites > 0 ? (double) totalSuccess / totalInvites * 100 : 0);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
                log.error("Batch invitation failed", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Batch invitation failed: " + e.getMessage()));
        }
    }

    /**
     * Send message to user
     */
    @PostMapping("/users/{userId}/messages")
    public ResponseEntity<SendMessageResult> sendMessageToUser(
            @PathVariable Long userId,
            @RequestBody SendMessageRequest request) {
        try {
            log.info("Received request to send message to user: userId={}, content={}", userId, request.getContent());

            // Validate request parameters
//            if (request.getContent() == null || request.getContent().trim().isEmpty()) {
//                SendMessageResult errorResult = SendMessageResult.builder()
//                        .success(false)
//                        .chatId(userId)
//                        .errorMessage("Message content cannot be empty")
//                        .errorCode("INVALID_CONTENT")
//                        .sentAt(LocalDateTime.now())
//                        .build();
//                return ResponseEntity.badRequest().body(errorResult);
//            }

            // Set default values
            if (request.getMessageType() == null) {
                request.setMessageType("TEXT");
            }

            SendMessageResult result = telegramService.sendMessageToUser(userId, request)
                    .get(30, TimeUnit.SECONDS);

            if (result.getSuccess()) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.internalServerError().body(result);
            }
        } catch (Exception e) {
                log.error("Failed to send message to user: userId={}", userId, e);
            SendMessageResult errorResult = SendMessageResult.builder()
                    .success(false)
                    .chatId(userId)
                    .content(request != null ? request.getContent() : null)
                    .errorMessage("Message sending failed: " + e.getMessage())
                    .errorCode("SEND_FAILED")
                    .sentAt(LocalDateTime.now())
                    .build();
            return ResponseEntity.internalServerError().body(errorResult);
        }
    }

    /**
     * Send message to group
     */
    @PostMapping("/groups/{groupId}/messages")
    public ResponseEntity<SendMessageResult> sendMessageToGroup(
            @PathVariable Long groupId,
            @RequestBody SendMessageRequest request) {
        try {
            log.info("Received request to send message to group: groupId={}, content={}", groupId, request.getContent());

            // Validate request parameters
            if (request.getContent() == null || request.getContent().trim().isEmpty()) {
                SendMessageResult errorResult = SendMessageResult.builder()
                        .success(false)
                        .chatId(groupId)
                        .errorMessage("Message content cannot be empty")
                        .errorCode("INVALID_CONTENT")
                        .sentAt(LocalDateTime.now())
                        .build();
                return ResponseEntity.badRequest().body(errorResult);
            }

            // Set default values
            if (request.getMessageType() == null) {
                request.setMessageType("TEXT");
            }

            SendMessageResult result = telegramService.sendMessageToGroup(groupId, request)
                    .get(30, TimeUnit.SECONDS);

            if (result.getSuccess()) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.internalServerError().body(result);
            }
        } catch (Exception e) {
                log.error("Failed to send message to group: groupId={}", groupId, e);
            SendMessageResult errorResult = SendMessageResult.builder()
                    .success(false)
                    .chatId(groupId)
                    .content(request != null ? request.getContent() : null)
                    .errorMessage("Message sending failed: " + e.getMessage())
                    .errorCode("SEND_FAILED")
                    .sentAt(LocalDateTime.now())
                    .build();
            return ResponseEntity.internalServerError().body(errorResult);
        }
    }
    /**
     * Send file message to group (handles file upload and sending)
     */
    @PostMapping("/groups/{groupId}/files")
    public ResponseEntity<SendMessageResult> sendFileToGroup(
            @PathVariable Long groupId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "caption", required = false) String caption) {
        try {
            log.info("Receiving file upload for group {}: {}", groupId, file.getOriginalFilename());

            // Validate file
            if (file.isEmpty()) {
                SendMessageResult errorResult = SendMessageResult.builder()
                        .success(false)
                        .chatId(groupId)
                        .errorMessage("File is empty")
                        .errorCode("INVALID_FILE")
                        .sentAt(LocalDateTime.now())
                        .build();
                return ResponseEntity.badRequest().body(errorResult);
            }

            // Save file to temporary location
            String tempDir = System.getProperty("java.io.tmpdir");
            String originalFilename = file.getOriginalFilename();
            String filename = UUID.randomUUID() + "_" + originalFilename;
            java.io.File tempFile = new java.io.File(tempDir, filename);
            
            file.transferTo(tempFile);
            log.info("File saved to temporary location: {}", tempFile.getAbsolutePath());

            // Determine message type based on file
            String messageType = "FILE";
            String contentType = file.getContentType();
            log.info("File content type: {}", contentType);
            
            if (contentType != null) {
                if (contentType.startsWith("video/")) {
                    messageType = "VIDEO";
                } else if (contentType.startsWith("image/")) {
                    messageType = "PHOTO";
                } else if (contentType.startsWith("audio/")) {
                    messageType = "AUDIO";
                }
            }
            
            log.info("Determined message type: {}", messageType);

            // Create send message request
            SendMessageRequest request = SendMessageRequest.builder()
                    .content(caption != null ? caption : originalFilename)
                    .messageType(messageType)
                    .filePath(tempFile.getAbsolutePath())
                    .fileName(originalFilename)
                    .build();
            
            log.info("SendMessageRequest - Type: {}, FilePath: {}, FileName: {}", 
                request.getMessageType(), request.getFilePath(), request.getFileName());

            // Send message
            SendMessageResult result = telegramService.sendMessageToGroup(groupId, request)
                    .get(60, TimeUnit.SECONDS);

            // Clean up temp file after sending
            try {
                if (tempFile.exists()) {
                    tempFile.delete();
                    log.info("Temporary file deleted: {}", tempFile.getAbsolutePath());
                }
            } catch (Exception e) {
                log.warn("Failed to delete temporary file: {}", tempFile.getAbsolutePath(), e);
            }

            if (result.getSuccess()) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.internalServerError().body(result);
            }
        } catch (Exception e) {
            log.error("Failed to send file to group {}", groupId, e);
            SendMessageResult errorResult = SendMessageResult.builder()
                    .success(false)
                    .chatId(groupId)
                    .errorMessage("Failed to send file: " + e.getMessage())
                    .errorCode("SEND_FAILED")
                    .sentAt(LocalDateTime.now())
                    .build();
            return ResponseEntity.internalServerError().body(errorResult);
        }
    }
    /**
     * Get latest chat history (Telegram home screen format)
     * Returns enriched chat list with all metadata like Telegram's home screen:
     * - Last message preview
     * - Unread counts and mentions
     * - User online status
     * - Pinned chats
     * - Draft messages
     * - Verification badges
     * - And more...
     * 
     * Pagination: Use page parameter for cumulative loading
     * - Page 1: limit=100 (first 100 chats)
     * - Page 2: limit=200 (first 200 chats)
     * - Page 3: limit=300 (first 300 chats)
     * 
     * @param page Page number (default: 1). Each page loads 100 more chats cumulatively
     * @param limit Custom limit (optional). If provided, overrides page-based calculation
     * @return List of enriched chat items ordered by most recent activity
     */
    @GetMapping("/chat/latest-history")
    public ResponseEntity<List<ChatListItem>> getLatestHistory(
            @RequestParam(value = "page", defaultValue = "1", required = false) int page,
            @RequestParam(value = "limit", required = false) Integer limit) {

        // Calculate limit based on page if not explicitly provided
        // Page 1 = 100, Page 2 = 200, Page 3 = 300, etc.
        int effectiveLimit = (limit != null) ? limit : (page * 100);
        
        log.info("Getting enriched chat history - page: {}, effectiveLimit: {}", page, effectiveLimit);

        try {
            CompletableFuture<List<ChatListItem>> chats = telegramService.getEnrichedChatList(effectiveLimit);
            return ResponseEntity.ok().body(chats.get());
        } catch (Exception e) {
            log.error("Failed to get enriched chat history", e);
            return ResponseEntity.internalServerError().body(null);
        }
    }


    /**
     * Send voice message to user
     */
    @PostMapping("/users/{userId}/voice")
    public ResponseEntity<SendMessageResult> sendVoiceToUser(
            @PathVariable Long userId,
            @RequestBody SendVoiceRequest request) {
        try {
                log.info("Received request to send voice message to user: userId={}, file={}", userId, request.getVoiceFilePath());

                // Validate request parameters
            if (request.getVoiceFilePath() == null || request.getVoiceFilePath().trim().isEmpty()) {
                SendMessageResult errorResult = SendMessageResult.builder()
                        .success(false)
                        .chatId(userId)
                    .errorMessage("Voice file path cannot be empty")
                        .errorCode("INVALID_FILE_PATH")
                        .sentAt(LocalDateTime.now())
                        .build();
                return ResponseEntity.badRequest().body(errorResult);
            }

            SendMessageResult result = telegramService.sendVoiceToUser(userId, request)
                    .get(60, TimeUnit.SECONDS); // Voice files can be large, increase timeout

            if (result.getSuccess()) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.internalServerError().body(result);
            }
        } catch (Exception e) {
                log.error("Failed to send voice message to user: userId={}", userId, e);
            SendMessageResult errorResult = SendMessageResult.builder()
                    .success(false)
                    .chatId(userId)
                    .content("Voice message: " + (request != null ? request.getVoiceFilePath() : ""))
                    .errorMessage("Voice message sending failed: " + e.getMessage())
                    .errorCode("SEND_FAILED")
                    .sentAt(LocalDateTime.now())
                    .build();
            return ResponseEntity.internalServerError().body(errorResult);
        }
    }

    /**
     * Send voice message to group
     */
    @PostMapping("/groups/{groupId}/voice")
    public ResponseEntity<SendMessageResult> sendVoiceToGroup(
            @PathVariable Long groupId,
            @RequestBody SendVoiceRequest request) {
        try {
                log.info("Received request to send voice message to group: groupId={}, file={}", groupId, request.getVoiceFilePath());

                // Validate request parameters
            if (request.getVoiceFilePath() == null || request.getVoiceFilePath().trim().isEmpty()) {
                SendMessageResult errorResult = SendMessageResult.builder()
                        .success(false)
                        .chatId(groupId)
                    .errorMessage("Voice file path cannot be empty")
                        .errorCode("INVALID_FILE_PATH")
                        .sentAt(LocalDateTime.now())
                        .build();
                return ResponseEntity.badRequest().body(errorResult);
            }

            SendMessageResult result = telegramService.sendVoiceToGroup(groupId, request)
                    .get(60, TimeUnit.SECONDS); // Voice files can be large, increase timeout

            if (result.getSuccess()) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.internalServerError().body(result);
            }
        } catch (Exception e) {
                log.error("Failed to send voice message to group: groupId={}", groupId, e);
            SendMessageResult errorResult = SendMessageResult.builder()
                    .success(false)
                    .chatId(groupId)
                    .content("Voice message: " + (request != null ? request.getVoiceFilePath() : ""))
                    .errorMessage("Voice message sending failed: " + e.getMessage())
                    .errorCode("SEND_FAILED")
                    .sentAt(LocalDateTime.now())
                    .build();
            return ResponseEntity.internalServerError().body(errorResult);
        }
    }


    /**
     * Parse Telegram message link
     */
    @PostMapping("/links/parse")
    public ResponseEntity<TelegramLinkInfo> parseTelegramLink(@RequestBody Map<String, String> request) {
        try {
            String messageLink = request.get("messageLink");
            if (messageLink == null || messageLink.trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            TelegramLinkInfo linkInfo = telegramService.parseTelegramLink(messageLink.trim());
            return ResponseEntity.ok(linkInfo);
        } catch (Exception e) {
            log.error("Error parsing Telegram link", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Download media files from Telegram link
     */
    @PostMapping("/links/download")
    public ResponseEntity<Map<String, Object>> downloadFromTelegramLink(@RequestBody TelegramLinkRequest request) {
        try {
            log.info("Received link download request: {}", request.getMessageLink());

            // Validate request parameters
            if (request.getMessageLink() == null || request.getMessageLink().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Message link cannot be empty"));
            }

            // Set default values
            if (request.getDownloadType() == null) {
                request.setDownloadType("AUTO");
            }
            if (request.getDownloadThumbnail() == null) {
                request.setDownloadThumbnail(false);
            }

            TelegramLinkDownloadResult result = telegramService.downloadFromTelegramLink(request)
                    .get(60, TimeUnit.SECONDS);

            Map<String, Object> response = new HashMap<>();
            response.put("taskId", result.getTaskId());
            response.put("status", result.getStatus());
            response.put("originalLink", result.getOriginalLink());
            response.put("linkInfo", result.getLinkInfo());

            if (result.getMessageInfo() != null) {
                response.put("messageInfo", result.getMessageInfo());
            }

            if (result.getDownloads() != null && !result.getDownloads().isEmpty()) {
                response.put("downloads", result.getDownloads());
            }

            response.put("totalCount", result.getTotalCount());
            response.put("successCount", result.getSuccessCount());
            response.put("failedCount", result.getFailedCount());

            if (result.getErrorMessage() != null) {
                response.put("errorMessage", result.getErrorMessage());
            }

            log.info("Link download task created: {} - status: {}", result.getTaskId(), result.getStatus());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error downloading from Telegram link", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Download failed: " + e.getMessage()));
        }
    }

    /**
     * Get link download task status
     */
    @GetMapping("/links/downloads/{taskId}")
    public ResponseEntity<TelegramLinkDownloadResult> getLinkDownloadStatus(@PathVariable String taskId) {
        try {
            TelegramLinkDownloadResult result = telegramService.getLinkDownloadStatus(taskId);
            if (result != null) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error getting link download status: {}", taskId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get all link download tasks
     */
    @GetMapping("/links/downloads")
    public ResponseEntity<List<TelegramLinkDownloadResult>> getAllLinkDownloads() {
        try {
            List<TelegramLinkDownloadResult> downloads = telegramService.getAllLinkDownloads();
            return ResponseEntity.ok(downloads);
        } catch (Exception e) {
            log.error("Error getting all link downloads", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Check whether user can access a specific group
     */
    @GetMapping("/chats/{chatId}/access")
    public ResponseEntity<Map<String, Object>> checkChatAccess(@PathVariable Long chatId) {
        try {
            Boolean hasAccess = telegramService.isUserInChat(chatId).get(10, TimeUnit.SECONDS);

            Map<String, Object> result = new HashMap<>();
            result.put("chatId", chatId);
            result.put("hasAccess", hasAccess);
            result.put("message", hasAccess ? "This group is accessible" : "Cannot access this group, or you are not a member");

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error checking chat access: {}", chatId, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to check group access permission: " + e.getMessage()));
        }
    }

    /**
     * Verify group ID conversion
     */
    @GetMapping("/links/debug/{originalId}")
    public ResponseEntity<Map<String, Object>> debugChatIdConversion(@PathVariable Long originalId) {
        try {
            // Calculate converted group ID
            Long convertedChatId = -1000000000000L - originalId;

            Map<String, Object> result = new HashMap<>();
            result.put("originalId", originalId);
            result.put("convertedChatId", convertedChatId);
            result.put("linkFormat", "https://t.me/c/" + originalId + "/MESSAGE_ID");

            // Check whether converted group is accessible
            Boolean hasAccess = telegramService.isUserInChat(convertedChatId).get(10, TimeUnit.SECONDS);
            result.put("hasAccess", hasAccess);
            result.put("accessMessage", hasAccess ? "Converted group is accessible" : "Converted group is not accessible");

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error debugging chat ID conversion: {}", originalId, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to debug group ID conversion: " + e.getMessage()));
        }
    }

    /**
     * Get latest messages in a group (used to find valid message IDs)
     */
    @GetMapping("/chats/{chatId}/messages/latest")
    public ResponseEntity<Map<String, Object>> getLatestMessages(@PathVariable Long chatId) {
        try {
            log.info("Getting latest messages in group: chatId={}", chatId);

            List<MessageInfo> messages = telegramService.getLatestMessages(chatId, 10).get(20, TimeUnit.SECONDS);

            Map<String, Object> result = new HashMap<>();
            result.put("chatId", chatId);
            result.put("messageCount", messages.size());
            result.put("messages", messages);

            if (!messages.isEmpty()) {
                MessageInfo latestMessage = messages.get(0);
                result.put("latestMessageId", latestMessage.getId());
                result.put("latestMessageType", latestMessage.getMessageType());
                result.put("latestMessageDate", latestMessage.getMessageDate());

                // Generate test links
                Long originalChatId = Math.abs(chatId + 1000000000000L);
                List<String> testLinks = new ArrayList<>();
                for (MessageInfo msg : messages.subList(0, Math.min(5, messages.size()))) {
                    testLinks.add("https://t.me/c/" + originalChatId + "/" + msg.getId());
                }
                result.put("testLinks", testLinks);
                result.put("message", "Found " + messages.size() + " latest messages");
            } else {
                result.put("message", "No messages found");
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error getting latest messages: {}", chatId, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to get latest messages: " + e.getMessage()));
        }
    }

    /**
     * Get message share link
     */
    @GetMapping("/chats/{chatId}/messages/{messageId}/link")
    public ResponseEntity<Map<String, Object>> getMessageLink(
            @PathVariable Long chatId, @PathVariable Long messageId) {
        try {
            log.info("Getting message link: chatId={}, messageId={}", chatId, messageId);

            String messageLink = telegramService.getMessageLink(chatId, messageId).get(15, TimeUnit.SECONDS);

            Map<String, Object> result = new HashMap<>();
            result.put("chatId", chatId);
            result.put("messageId", messageId);
            result.put("messageLink", messageLink);
            result.put("success", messageLink != null);

            if (messageLink != null) {
                result.put("message", "Message link retrieved successfully");
            } else {
                result.put("message", "Unable to retrieve message link");
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error getting message link: chatId={}, messageId={}", chatId, messageId, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to get message link: " + e.getMessage()));
        }
    }

    /**
     * Check whether a specific message exists
     */
    @GetMapping("/chats/{chatId}/messages/{messageId}/check")
    public ResponseEntity<Map<String, Object>> checkMessage(
            @PathVariable Long chatId, @PathVariable Long messageId) {
        try {
            log.info("Checking message: chatId={}, messageId={}", chatId, messageId);

            // Try to get message
            MessageInfo message = telegramService.getMessageInfo(chatId, messageId).get(15, TimeUnit.SECONDS);

            Map<String, Object> result = new HashMap<>();
            result.put("chatId", chatId);
            result.put("messageId", messageId);
            result.put("exists", message != null);

            if (message != null) {
                result.put("messageType", message.getMessageType());
                result.put("content", message.getContent());
                result.put("senderName", message.getSenderName());
                result.put("messageDate", message.getMessageDate());
                result.put("hasVideo", "VIDEO".equals(message.getMessageType()));
                result.put("hasPhoto", "PHOTO".equals(message.getMessageType()));
                result.put("hasDocument", "DOCUMENT".equals(message.getMessageType()));
                result.put("message", "Message exists and is accessible");
            } else {
                result.put("message", "Message does not exist or is not accessible");
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error checking message: chatId={}, messageId={}", chatId, messageId, e);

            Map<String, Object> result = new HashMap<>();
            result.put("chatId", chatId);
            result.put("messageId", messageId);
            result.put("exists", false);
            result.put("error", e.getMessage());

            if (e.getMessage().contains("404") || e.getMessage().contains("Not Found")) {
                result.put("message", "Message does not exist or has been deleted");
            } else if (e.getMessage().contains("403") || e.getMessage().contains("Forbidden")) {
                result.put("message", "No permission to access this message");
            } else {
                result.put("message", "Error while checking message: " + e.getMessage());
            }

            return ResponseEntity.ok(result);
        }
    }

    /**
     * Health check
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "OK",
                "service", "Telegram Service",
                "timestamp", String.valueOf(System.currentTimeMillis())
        ));
    }

    /**
     * Get WebSocket connection info
     */
    @GetMapping("/ws/info")
    public ResponseEntity<Map<String, Object>> getWebSocketInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("endpoint", "/ws");
        info.put("topics", Map.of(
                "chats", "/topic/chats",
                "chat", "/topic/chat/{chatId}",
                "downloads", "/topic/downloads/{downloadId}",
                "auth", "/topic/auth",
                "notifications", "/topic/notifications",
                "errors", "/topic/errors"
        ));
        info.put("appDestinationPrefix", "/app");
        info.put("protocols", List.of("websocket", "sockjs"));
        
        return ResponseEntity.ok(info);
    }


    // ==================== MESSAGE MANAGEMENT ENDPOINTS ====================
    
    /**
     * Pin a message in a chat
     * POST /api/telegram/chats/{chatId}/messages/{messageId}/pin
     */
    @PostMapping("/chats/{chatId}/messages/{messageId}/pin")
    public ResponseEntity<?> pinMessage(
            @PathVariable Long chatId,
            @PathVariable Long messageId,
            @RequestParam(defaultValue = "false") boolean disableNotification,
            @RequestParam(defaultValue = "false") boolean onlyForSelf) {
        try {
            telegramService.pinMessage(chatId, messageId, disableNotification, onlyForSelf).join();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Message pinned successfully",
                "chatId", chatId,
                "messageId", messageId
            ));
        } catch (Exception e) {
            log.error("Failed to pin message: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Unpin a message in a chat
     * DELETE /api/telegram/chats/{chatId}/messages/{messageId}/pin
     */
    @DeleteMapping("/chats/{chatId}/messages/{messageId}/pin")
    public ResponseEntity<?> unpinMessage(
            @PathVariable Long chatId,
            @PathVariable Long messageId) {
        try {
            telegramService.unpinMessage(chatId, messageId).join();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Message unpinned successfully",
                "chatId", chatId,
                "messageId", messageId
            ));
        } catch (Exception e) {
            log.error("Failed to unpin message: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Get pinned messages in a chat
     * GET /api/telegram/chats/{chatId}/pinned-messages
     */
    @GetMapping("/chats/{chatId}/pinned-messages")
    public ResponseEntity<?> getPinnedMessages(@PathVariable Long chatId) {
        try {
            List<MessageInfo> pinnedMessages = telegramService.getPinnedMessages(chatId).join();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "chatId", chatId,
                "pinnedMessages", pinnedMessages,
                "count", pinnedMessages.size()
            ));
        } catch (Exception e) {
            log.error("Failed to get pinned messages: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Get a specific message by ID
     * GET /api/telegram/chats/{chatId}/messages/{messageId}
     */
    @GetMapping("/chats/{chatId}/messages/{messageId}")
    public ResponseEntity<?> getMessageById(
            @PathVariable Long chatId,
            @PathVariable Long messageId) {
        try {
            MessageInfo message = telegramService.getMessageById(chatId, messageId).join();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", message
            ));
        } catch (Exception e) {
            log.error("Failed to get message: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    // ==================== CHAT MANAGEMENT ENDPOINTS ====================
    
    /**
     * Mark chat as unread
     * POST /api/telegram/chats/{chatId}/unread
     */
    @PostMapping("/chats/{chatId}/unread")
    public ResponseEntity<?> markChatAsUnread(@PathVariable Long chatId) {
        try {
            telegramService.markChatAsUnread(chatId).join();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Chat marked as unread",
                "chatId", chatId
            ));
        } catch (Exception e) {
            log.error("Failed to mark chat as unread: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Pin a chat
     * POST /api/telegram/chats/{chatId}/pin
     */
    @PostMapping("/chats/{chatId}/pin")
    public ResponseEntity<?> pinChat(@PathVariable Long chatId) {
        try {
            telegramService.pinChat(chatId).join();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Chat pinned successfully",
                "chatId", chatId
            ));
        } catch (Exception e) {
            log.error("Failed to pin chat: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Unpin a chat
     * DELETE /api/telegram/chats/{chatId}/pin
     */
    @DeleteMapping("/chats/{chatId}/pin")
    public ResponseEntity<?> unpinChat(@PathVariable Long chatId) {
        try {
            telegramService.unpinChat(chatId).join();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Chat unpinned successfully",
                "chatId", chatId
            ));
        } catch (Exception e) {
            log.error("Failed to unpin chat: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Archive a chat
     * POST /api/telegram/chats/{chatId}/archive
     */
    @PostMapping("/chats/{chatId}/archive")
    public ResponseEntity<?> archiveChat(@PathVariable Long chatId) {
        try {
            telegramService.archiveChat(chatId).join();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Chat archived successfully",
                "chatId", chatId
            ));
        } catch (Exception e) {
            log.error("Failed to archive chat: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Unarchive a chat
     * DELETE /api/telegram/chats/{chatId}/archive
     */
    @DeleteMapping("/chats/{chatId}/archive")
    public ResponseEntity<?> unarchiveChat(@PathVariable Long chatId) {
        try {
            telegramService.unarchiveChat(chatId).join();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Chat unarchived successfully",
                "chatId", chatId
            ));
        } catch (Exception e) {
            log.error("Failed to unarchive chat: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Get archived chats
     * GET /api/telegram/chats/archived
     */
    @GetMapping("/chats/archived")
    public ResponseEntity<?> getArchivedChats(
            @RequestParam(defaultValue = "100") int limit) {
        try {
            List<ChatListItem> archivedChats = telegramService.getArchivedChats(limit).join();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "chats", archivedChats,
                "count", archivedChats.size()
            ));
        } catch (Exception e) {
            log.error("Failed to get archived chats: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Delete chat history
     * DELETE /api/telegram/chats/{chatId}/history
     */
    @DeleteMapping("/chats/{chatId}/history")
    public ResponseEntity<?> deleteChatHistory(
            @PathVariable Long chatId,
            @RequestParam(defaultValue = "false") boolean deleteForEveryone,
            @RequestParam(defaultValue = "false") boolean revoke) {
        try {
            telegramService.deleteChatHistory(chatId, deleteForEveryone, revoke).join();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Chat history deleted successfully",
                "chatId", chatId
            ));
        } catch (Exception e) {
            log.error("Failed to delete chat history: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Leave a chat
     * POST /api/telegram/chats/{chatId}/leave
     */
    @PostMapping("/chats/{chatId}/leave")
    public ResponseEntity<?> leaveChat(@PathVariable Long chatId) {
        try {
            telegramService.leaveChat(chatId).join();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Left chat successfully",
                "chatId", chatId
            ));
        } catch (Exception e) {
            log.error("Failed to leave chat: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Block a user
     * POST /api/telegram/users/{userId}/block
     */
    @PostMapping("/users/{userId}/block")
    public ResponseEntity<?> blockUser(@PathVariable Long userId) {
        try {
            telegramService.blockUser(userId).join();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "User blocked successfully",
                "userId", userId
            ));
        } catch (Exception e) {
            log.error("Failed to block user: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Unblock a user
     * DELETE /api/telegram/users/{userId}/block
     */
    @DeleteMapping("/users/{userId}/block")
    public ResponseEntity<?> unblockUser(@PathVariable Long userId) {
        try {
            telegramService.unblockUser(userId).join();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "User unblocked successfully",
                "userId", userId
            ));
        } catch (Exception e) {
            log.error("Failed to unblock user: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Get blocked users
     * GET /api/telegram/users/blocked
     */
    @GetMapping("/users/blocked")
    public ResponseEntity<?> getBlockedUsers() {
        try {
            List<Long> blockedUsers = telegramService.getBlockedUsers().join();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "blockedUsers", blockedUsers,
                "count", blockedUsers.size()
            ));
        } catch (Exception e) {
            log.error("Failed to get blocked users: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    // ==================== GROUP MANAGEMENT ENDPOINTS ====================
    
    /**
     * Update group information
     * PUT /api/telegram/groups/{groupId}
     */
    @PutMapping("/groups/{groupId}")
    public ResponseEntity<?> updateGroupInfo(
            @PathVariable Long groupId,
            @RequestBody Map<String, String> request) {
        try {
            String title = request.get("title");
            String description = request.get("description");
            
            telegramService.updateGroupInfo(groupId, title, description).join();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Group information updated successfully",
                "groupId", groupId
            ));
        } catch (Exception e) {
            log.error("Failed to update group info: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Set group photo
     * POST /api/telegram/groups/{groupId}/photo
     */
    @PostMapping("/groups/{groupId}/photo")
    public ResponseEntity<?> setGroupPhoto(
            @PathVariable Long groupId,
            @RequestParam("photoPath") String photoPath) {
        try {
            telegramService.setGroupPhoto(groupId, photoPath).join();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Group photo updated successfully",
                "groupId", groupId
            ));
        } catch (Exception e) {
            log.error("Failed to set group photo: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Get group administrators
     * GET /api/telegram/groups/{groupId}/admins
     */
    @GetMapping("/groups/{groupId}/admins")
    public ResponseEntity<?> getGroupAdmins(@PathVariable Long groupId) {
        try {
            List<GroupMemberInfo> admins = telegramService.getGroupAdmins(groupId).join();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "groupId", groupId,
                "admins", admins,
                "count", admins.size()
            ));
        } catch (Exception e) {
            log.error("Failed to get group admins: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Promote member to admin
     * POST /api/telegram/groups/{groupId}/members/{userId}/promote
     */
    @PostMapping("/groups/{groupId}/members/{userId}/promote")
    public ResponseEntity<?> promoteMemberToAdmin(
            @PathVariable Long groupId,
            @PathVariable Long userId,
            @RequestBody(required = false) Map<String, Boolean> permissions) {
        try {
            if (permissions == null) {
                permissions = new HashMap<>();
            }
            
            telegramService.promoteMemberToAdmin(groupId, userId, permissions).join();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Member promoted to admin successfully",
                "groupId", groupId,
                "userId", userId
            ));
        } catch (Exception e) {
            log.error("Failed to promote member: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Demote admin to regular member
     * POST /api/telegram/groups/{groupId}/members/{userId}/demote
     */
    @PostMapping("/groups/{groupId}/members/{userId}/demote")
    public ResponseEntity<?> demoteAdmin(
            @PathVariable Long groupId,
            @PathVariable Long userId) {
        try {
            telegramService.demoteAdmin(groupId, userId).join();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Admin demoted successfully",
                "groupId", groupId,
                "userId", userId
            ));
        } catch (Exception e) {
            log.error("Failed to demote admin: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Kick member from group
     * DELETE /api/telegram/groups/{groupId}/members/{userId}
     */
    @DeleteMapping("/groups/{groupId}/members/{userId}")
    public ResponseEntity<?> kickMember(
            @PathVariable Long groupId,
            @PathVariable Long userId,
            @RequestParam(defaultValue = "false") boolean banUser) {
        try {
            telegramService.kickMember(groupId, userId, banUser).join();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", banUser ? "Member banned successfully" : "Member kicked successfully",
                "groupId", groupId,
                "userId", userId
            ));
        } catch (Exception e) {
            log.error("Failed to kick member: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Ban member from group
     * POST /api/telegram/groups/{groupId}/members/{userId}/ban
     */
    @PostMapping("/groups/{groupId}/members/{userId}/ban")
    public ResponseEntity<?> banMember(
            @PathVariable Long groupId,
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int untilDate) {
        try {
            telegramService.banMember(groupId, userId, untilDate).join();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Member banned successfully",
                "groupId", groupId,
                "userId", userId,
                "untilDate", untilDate
            ));
        } catch (Exception e) {
            log.error("Failed to ban member: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    @GetMapping("/groups/{groupId}/stream")
    public ResponseEntity<TdApi.GroupCallStreams> groupCallStream(@PathVariable("groupId") int groupId) throws ExecutionException, InterruptedException {
        CompletableFuture<TdApi.GroupCallStreams>result=callService.getGroupStreams(groupId);
        return ResponseEntity.ok().body(result.get());
    }
    
    /**
     * Unban member from group
     * DELETE /api/telegram/groups/{groupId}/members/{userId}/ban
     */
    @DeleteMapping("/groups/{groupId}/members/{userId}/ban")
    public ResponseEntity<?> unbanMember(
            @PathVariable Long groupId,
            @PathVariable Long userId) {
        try {
            telegramService.unbanMember(groupId, userId).join();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Member unbanned successfully",
                "groupId", groupId,
                "userId", userId
            ));
        } catch (Exception e) {
            log.error("Failed to unban member: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Get banned members
     * GET /api/telegram/groups/{groupId}/banned
     */
    @GetMapping("/groups/{groupId}/banned")
    public ResponseEntity<?> getBannedMembers(@PathVariable Long groupId) {
        try {
            List<GroupMemberInfo> bannedMembers = telegramService.getBannedMembers(groupId).join();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "groupId", groupId,
                "bannedMembers", bannedMembers,
                "count", bannedMembers.size()
            ));
        } catch (Exception e) {
            log.error("Failed to get banned members: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Restrict member permissions
     * POST /api/telegram/groups/{groupId}/members/{userId}/restrict
     */
    @PostMapping("/groups/{groupId}/members/{userId}/restrict")
    public ResponseEntity<?> restrictMember(
            @PathVariable Long groupId,
            @PathVariable Long userId,
            @RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Boolean> permissions = (Map<String, Boolean>) request.get("permissions");
            int untilDate = (int) request.getOrDefault("untilDate", 0);
            
            if (permissions == null) {
                permissions = new HashMap<>();
            }
            
            telegramService.restrictMember(groupId, userId, permissions, untilDate).join();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Member restricted successfully",
                "groupId", groupId,
                "userId", userId
            ));
        } catch (Exception e) {
            log.error("Failed to restrict member: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Generate invite link for group
     * POST /api/telegram/groups/{groupId}/invite-links
     */
    @PostMapping("/groups/{groupId}/invite-links")
    public ResponseEntity<?> generateInviteLink(
            @PathVariable Long groupId,
            @RequestBody(required = false) Map<String, Object> request) {
        try {
            String name = request != null ? (String) request.get("name") : null;
            int expireDate = request != null ? (int) request.getOrDefault("expireDate", 0) : 0;
            int memberLimit = request != null ? (int) request.getOrDefault("memberLimit", 0) : 0;
            boolean createsJoinRequest = request != null ? (boolean) request.getOrDefault("createsJoinRequest", false) : false;
            
            String inviteLink = telegramService.generateInviteLink(groupId, name, expireDate, memberLimit, createsJoinRequest).join();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Invite link generated successfully",
                "groupId", groupId,
                "inviteLink", inviteLink
            ));
        } catch (Exception e) {
            log.error("Failed to generate invite link: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Revoke invite link
     * DELETE /api/telegram/groups/{groupId}/invite-links
     */
    @DeleteMapping("/groups/{groupId}/invite-links")
    public ResponseEntity<?> revokeInviteLink(
            @PathVariable Long groupId,
            @RequestParam String inviteLink) {
        try {
            telegramService.revokeInviteLink(groupId, inviteLink).join();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Invite link revoked successfully",
                "groupId", groupId
            ));
        } catch (Exception e) {
            log.error("Failed to revoke invite link: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Get all invite links for a group
     * GET /api/telegram/groups/{groupId}/invite-links
     */
    @GetMapping("/groups/{groupId}/invite-links")
    public ResponseEntity<?> getInviteLinks(@PathVariable Long groupId) {
        try {
            List<String> inviteLinks = telegramService.getInviteLinks(groupId).join();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "groupId", groupId,
                "inviteLinks", inviteLinks,
                "count", inviteLinks.size()
            ));
        } catch (Exception e) {
            log.error("Failed to get invite links: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
}

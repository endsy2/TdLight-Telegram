package com.example.tdlighttelegram.service.shared;

import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Telegram Format Helper
 * Provides formatting methods for messages, users, and chat information
 */
@Slf4j
@Component
public class TelegramFormatHelper {

    /**
     * Format sender information
     */
    public String formatSenderInfo(TdApi.User user) {
        if (user == null) {
            return "Unknown User";
        }

        StringBuilder info = new StringBuilder();

        // Add username
        if (user.usernames != null && user.usernames.activeUsernames.length > 0) {
            info.append("@").append(user.usernames.activeUsernames[0]);
        } else {
            // Use name
            if (user.firstName != null && !user.firstName.isEmpty()) {
                info.append(user.firstName);
            }
            if (user.lastName != null && !user.lastName.isEmpty()) {
                if (info.length() > 0) {
                    info.append(" ");
                }
                info.append(user.lastName);
            }
        }

        // Add user ID
        info.append(" (ID: ").append(user.id).append(")");

        return info.toString();
    }

    /**
     * Format chat information
     */
    public String formatChatInfo(TdApi.Chat chat) {
        if (chat == null) {
            return "Unknown Chat";
        }

        return chat.title != null ? chat.title : "Chat " + chat.id;
    }

    /**
     * Format message content, handle line breaks and length limits
     */
    public String formatMessageContent(String content) {
        if (content == null || content.isEmpty()) {
            return "[Empty message]";
        }

        // Remove or replace special characters to avoid log format issues
        String cleaned = content
                .replace("\r\n", "\\n")  // Windows line break
                .replace("\n", "\\n")    // Unix line break
                .replace("\r", "\\n")    // Mac line break
                .replace("\t", "\\t")    // Tab character
                .trim();

        // Limit length to avoid overly long logs
        int maxLength = 200;
        if (cleaned.length() > maxLength) {
            return cleaned.substring(0, maxLength) + "... [truncated, total length: " + content.length() + "]";
        }

        return cleaned;
    }

    /**
     * Build user display name
     */
    public String buildUserDisplayName(TdApi.User user) {
        if (user == null) {
            return "Unknown User";
        }

        StringBuilder name = new StringBuilder();
        if (user.firstName != null && !user.firstName.isEmpty()) {
            name.append(user.firstName);
        }
        if (user.lastName != null && !user.lastName.isEmpty()) {
            if (name.length() > 0) {
                name.append(" ");
            }
            name.append(user.lastName);
        }
        return name.length() > 0 ? name.toString() : "User " + user.id;
    }

    /**
     * Get file extension based on thumbnail format
     */
    public String getThumbnailExtension(String thumbnailFormat) {
        if (thumbnailFormat == null) {
            return ".jpg";
        }

        switch (thumbnailFormat.toLowerCase()) {
            case "thumbnailformatjpeg":
                return ".jpg";
            case "thumbnailformatpng":
                return ".png";
            case "thumbnailformatwebp":
                return ".webp";
            case "thumbnailformatgif":
                return ".gif";
            case "thumbnailformatmpeg4":
                return ".mp4";
            default:
                return ".jpg"; // Default to JPEG
        }
    }
}

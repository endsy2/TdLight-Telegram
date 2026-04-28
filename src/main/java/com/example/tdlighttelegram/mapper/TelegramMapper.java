package com.example.tdlighttelegram.mapper;

import com.example.tdlighttelegram.model.ChatInfo;
import com.example.tdlighttelegram.model.*;
import com.example.tdlighttelegram.service.shared.TelegramCacheManager;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Telegram Mapper
 * Maps TdApi objects from Telegram server to DTO response objects
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramMapper {

    private static SimpleTelegramClient client;
    private final TelegramCacheManager telegramCacheManager;
    /**
     * Map TdApi.Chat to ChatInfo
     */
    public static ChatInfo mapToChatInfo(TdApi.Chat chat) {
        if (chat == null) {
            return null;
        }

        String chatType = getChatType(chat.type);
        boolean isChannel = false;
        boolean isSupergroup = false;
        String username = null;

        if (chat.type instanceof TdApi.ChatTypeSupergroup) {
            TdApi.ChatTypeSupergroup supergroup = (TdApi.ChatTypeSupergroup) chat.type;
            isChannel = supergroup.isChannel;
            isSupergroup = true;
        } else if (chat.type instanceof TdApi.ChatTypeBasicGroup) {
            isSupergroup = false;
        }

        // Extract permissions
        ChatInfo.ChatPermissions permissions = null;
        if (chat.permissions != null) {
            permissions = mapToChatPermissions(chat.permissions);
        }

        // Get last message date
        LocalDateTime lastMessageDate = null;
        if (chat.lastMessage != null) {
            lastMessageDate = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(chat.lastMessage.date),
                    ZoneId.systemDefault()
            );
        }
        log.info("profile url:{}",chat.photo.small);

        client.send(
                new TdApi.DownloadFile(
                        chat.photo.big.id,
                        1,
                        0,
                        0,
                        true
                ),
                result -> {
                    if (result.isError()) {
                        System.out.println("Download failed: " + result.getError());
                        return;
                    }

                    TdApi.File file = result.get();

                    String path = file.local.path;
                    System.out.println("Downloaded to: " + path);
                }
        );

        return ChatInfo.builder()
                .id(chat.id)
                .title(chat.title)
                .type(chatType)
                .username(username)
                .description(null) // Need to get from full info
                .memberCount(null)
                .isChannel(isChannel)
                .isSupergroup(isSupergroup)
                .isVerified(false)
                .isRestricted(false)
                .canSendMessages(chat.permissions != null && chat.permissions.canSendBasicMessages)
                .photoUrl(null)
                .lastMessageDate(lastMessageDate)
                .unreadCount(chat.unreadCount)
                .permissions(permissions)
                .retrievedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Map TdApi.ChatPermissions to ChatInfo.ChatPermissions
     */
    public static ChatInfo.ChatPermissions mapToChatPermissions(TdApi.ChatPermissions permissions) {
        if (permissions == null) {
            return null;
        }

        return ChatInfo.ChatPermissions.builder()
                .canSendMessages(permissions.canSendBasicMessages)
                .canSendMediaMessages(permissions.canSendPhotos || permissions.canSendVideos)
                .canSendPolls(permissions.canSendPolls)
                .canSendOtherMessages(permissions.canSendOtherMessages)
                .canAddWebPagePreviews(permissions.canAddWebPagePreviews)
                .canChangeInfo(permissions.canChangeInfo)
                .canInviteUsers(permissions.canInviteUsers)
                .canPinMessages(permissions.canPinMessages)
                .build();
    }

    /**
     * Map TdApi.Message to MessageInfo
     */
    public static MessageInfo mapToMessageInfo(TdApi.Message message) {
        if (message == null) {
            return null;
        }

        String content = "";
        String messageType = "UNKNOWN";
        String mediaUrl = null;
        String fileName = null;
        Long fileSize = null;
        Integer fileId = null;
        Integer thumbnailFileId = null;
        String thumbnailFormat = null;
        Integer thumbnailWidth = null;
        Integer thumbnailHeight = null;

        // Extract content based on message type
        if (message.content instanceof TdApi.MessageText) {
            TdApi.MessageText textMessage = (TdApi.MessageText) message.content;
            content = textMessage.text.text;
            messageType = "TEXT";
        } else if (message.content instanceof TdApi.MessagePhoto) {
            TdApi.MessagePhoto photoMessage = (TdApi.MessagePhoto) message.content;
            messageType = "PHOTO";
            content = photoMessage.caption != null ? photoMessage.caption.text : "Photo message";
            if (photoMessage.photo.sizes.length > 0) {
                TdApi.PhotoSize largestPhoto = photoMessage.photo.sizes[photoMessage.photo.sizes.length - 1];
                fileSize = (long) largestPhoto.photo.size;
                fileId = largestPhoto.photo.id;
            }
        } else if (message.content instanceof TdApi.MessageVideo) {
            TdApi.MessageVideo videoMessage = (TdApi.MessageVideo) message.content;
            messageType = "VIDEO";
            content = videoMessage.caption != null ? videoMessage.caption.text : "Video message";
            fileName = videoMessage.video.fileName;
            fileSize = (long) videoMessage.video.video.size;
            fileId = videoMessage.video.video.id;

            // Get video thumbnail info
            if (videoMessage.video.thumbnail != null) {
                TdApi.Thumbnail thumbnail = videoMessage.video.thumbnail;
                thumbnailFileId = thumbnail.file.id;
                thumbnailFormat = thumbnail.format.getClass().getSimpleName();
                thumbnailWidth = thumbnail.width;
                thumbnailHeight = thumbnail.height;
            }
        } else if (message.content instanceof TdApi.MessageDocument) {
            TdApi.MessageDocument documentMessage = (TdApi.MessageDocument) message.content;
            messageType = "DOCUMENT";
            content = documentMessage.caption != null ? documentMessage.caption.text : "Document message";
            fileName = documentMessage.document.fileName;
            fileSize = (long) documentMessage.document.document.size;
            fileId = documentMessage.document.document.id;
        } else if (message.content instanceof TdApi.MessageAudio) {
            TdApi.MessageAudio audioMessage = (TdApi.MessageAudio) message.content;
            messageType = "AUDIO";
            content = audioMessage.caption != null ? audioMessage.caption.text : "Audio message";
            fileName = audioMessage.audio.fileName;
            fileSize = (long) audioMessage.audio.audio.size;
            fileId = audioMessage.audio.audio.id;
        } else if (message.content instanceof TdApi.MessageVoiceNote) {
            TdApi.MessageVoiceNote voiceMessage = (TdApi.MessageVoiceNote) message.content;
            messageType = "VOICE";
            content = voiceMessage.caption != null ? voiceMessage.caption.text : "Voice message";
            fileId = voiceMessage.voiceNote.voice.id;
        } else if (message.content instanceof TdApi.MessageSticker) {
            messageType = "STICKER";
            content = "Sticker message";
        } else if (message.content instanceof TdApi.MessageAnimation) {
            TdApi.MessageAnimation animationMessage = (TdApi.MessageAnimation) message.content;
            messageType = "ANIMATION";
            content = animationMessage.caption != null ? animationMessage.caption.text : "Animation message";
            fileName = animationMessage.animation.fileName;
            fileSize = (long) animationMessage.animation.animation.size;
            fileId = animationMessage.animation.animation.id;
        }

        // Check if forwarded message
        boolean isForwarded = message.forwardInfo != null;
        Long forwardedFromChatId = null;
        String forwardedFromChatTitle = null;

//        if (isForwarded && message.forwardInfo.origin instanceof TdApi.) {
//            TdApi.MessageForwardOriginChannel origin = (TdApi.MessageForwardOriginChannel) message.forwardInfo.origin;
//            forwardedFromChatId = origin.chatId;
//            forwardedFromChatTitle = "Forwarded from channel";
//        } else if (isForwarded) {
//            forwardedFromChatTitle = "Forwarded message";
//        }

        // Check if reply message
        boolean isReply = message.replyTo != null;
        Long replyToMessageId = null;
        if (isReply && message.replyTo instanceof TdApi.MessageReplyToMessage) {
            TdApi.MessageReplyToMessage replyTo = (TdApi.MessageReplyToMessage) message.replyTo;
            replyToMessageId = replyTo.messageId;
        }

        // Extract sender ID
        Long senderId = null;
        if (message.senderId instanceof TdApi.MessageSenderUser) {
            senderId = ((TdApi.MessageSenderUser) message.senderId).userId;
        }

        return MessageInfo.builder()
                .id(message.id)
                .chatId(message.chatId)
                .senderId(senderId)
                .messageType(messageType)
                .content(content)
                .mediaUrl(mediaUrl)
                .fileName(fileName)
                .fileSize(fileSize)
                .fileId(fileId)
                .thumbnailFileId(thumbnailFileId)
                .thumbnailFormat(thumbnailFormat)
                .thumbnailWidth(thumbnailWidth)
                .thumbnailHeight(thumbnailHeight)
                .isForwarded(isForwarded)
                .forwardedFromChatId(forwardedFromChatId)
                .forwardedFromChatTitle(forwardedFromChatTitle)
                .isReply(isReply)
                .replyToMessageId(replyToMessageId)
                .messageDate(LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(message.date), ZoneId.systemDefault()))
                .receivedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Map TdApi.User to UserInfo
     */
    public static UserInfo mapToUserInfo(TdApi.User user) {
        if (user == null) {
            return null;
        }

        String username = null;
        if (user.usernames != null && user.usernames.activeUsernames.length > 0) {
            username = user.usernames.activeUsernames[0];
        }

        boolean isBot = user.type instanceof TdApi.UserTypeBot;

        return UserInfo.builder()
                .id(user.id)
                .firstName(user.firstName)
                .lastName(user.lastName)
                .username(username)
                .phoneNumber(user.phoneNumber)
                .isBot(isBot)
                .isVerified(user.isVerified)
                .isPremium(user.isPremium)
                .languageCode(user.languageCode)
                .build();
    }

    /**
     * Map TdApi.Chat to GroupInfo
     */
    public static GroupInfo mapToGroupInfo(TdApi.Chat chat) {
        if (chat == null) {
            return null;
        }

        boolean isChannel = false;
        boolean isSupergroup = false;

        if (chat.type instanceof TdApi.ChatTypeSupergroup) {
            TdApi.ChatTypeSupergroup supergroup = (TdApi.ChatTypeSupergroup) chat.type;
            isChannel = supergroup.isChannel;
            isSupergroup = true;
        } else if (chat.type instanceof TdApi.ChatTypeBasicGroup) {
            isSupergroup = false;
        }

        return GroupInfo.builder()
                .id(chat.id)
                .title(chat.title)
                .isChannel(isChannel)
                .isSupergroup(isSupergroup)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Map TdApi.User and TdApi.ChatMember to GroupMemberInfo
     */
    public static GroupMemberInfo mapToGroupMemberInfo(TdApi.User user, TdApi.ChatMember member, Long groupId) {
        if (user == null || member == null) {
            return null;
        }

        String username = null;
        if (user.usernames != null && user.usernames.activeUsernames.length > 0) {
            username = user.usernames.activeUsernames[0];
        }

        boolean isBot = user.type instanceof TdApi.UserTypeBot;
        boolean isDeleted = user.type instanceof TdApi.UserTypeDeleted;

        // Parse member status
        String memberStatus = getMemberStatus(member.status);
        
        // Parse permissions
        boolean canSendMessages = true;
        boolean canSendMedia = true;
        boolean canInviteUsers = false;
        boolean canChangeInfo = false;
        boolean canPinMessages = false;
        boolean canDeleteMessages = false;
        boolean canBanUsers = false;
        boolean canRestrictMembers = false;
        boolean canPromoteMembers = false;
        String customTitle = null;

        if (member.status instanceof TdApi.ChatMemberStatusCreator) {
            TdApi.ChatMemberStatusCreator creator = (TdApi.ChatMemberStatusCreator) member.status;
            canInviteUsers = true;
            canChangeInfo = true;
            canPinMessages = true;
            canDeleteMessages = true;
            canBanUsers = true;
            canRestrictMembers = true;
            canPromoteMembers = true;
            customTitle = creator.customTitle;
        } else if (member.status instanceof TdApi.ChatMemberStatusAdministrator) {
            TdApi.ChatMemberStatusAdministrator admin = (TdApi.ChatMemberStatusAdministrator) member.status;
            canInviteUsers = admin.rights.canInviteUsers;
            canChangeInfo = admin.rights.canChangeInfo;
            canPinMessages = admin.rights.canPinMessages;
            canDeleteMessages = admin.rights.canDeleteMessages;
            canBanUsers = admin.rights.canRestrictMembers;
            canRestrictMembers = admin.rights.canRestrictMembers;
            canPromoteMembers = admin.rights.canPromoteMembers;
            customTitle = admin.customTitle;
        } else if (member.status instanceof TdApi.ChatMemberStatusRestricted) {
            TdApi.ChatMemberStatusRestricted restricted = (TdApi.ChatMemberStatusRestricted) member.status;
            canSendMessages = restricted.permissions.canSendBasicMessages;
            canSendMedia = restricted.permissions.canSendPhotos || restricted.permissions.canSendVideos;
        }

        return GroupMemberInfo.builder()
                .userId(user.id)
                .groupId(groupId)
                .firstName(user.firstName)
                .lastName(user.lastName)
                .username(username)
                .phoneNumber(user.phoneNumber)
                .isBot(isBot)
                .isVerified(user.isVerified)
                .isPremium(user.isPremium)
                .isDeleted(isDeleted)
                .memberStatus(memberStatus)
                .onlineStatus("OFFLINE") // Will be updated separately
                .canSendMessages(canSendMessages)
                .canSendMedia(canSendMedia)
                .canInviteUsers(canInviteUsers)
                .canChangeInfo(canChangeInfo)
                .canPinMessages(canPinMessages)
                .canDeleteMessages(canDeleteMessages)
                .canBanUsers(canBanUsers)
                .canRestrictMembers(canRestrictMembers)
                .canPromoteMembers(canPromoteMembers)
                .customTitle(customTitle)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Map TdApi.File to DownloadInfo
     */
    public static DownloadInfo mapToDownloadInfo(TdApi.File file, Long messageId, Long chatId, String fileName) {
        if (file == null) {
            return null;
        }

        String status = "PENDING";
        int progress = 0;
        String localPath = null;

        if (file.local.isDownloadingCompleted) {
            status = "COMPLETED";
            progress = 100;
            localPath = file.local.path;
        } else if (file.local.isDownloadingActive) {
            status = "DOWNLOADING";
            if (file.size > 0) {
                progress = (int) ((file.local.downloadedSize * 100L) / file.size);
            }
        }

        return DownloadInfo.builder()
                .downloadId(null) // Will be set by service
                .messageId(messageId)
                .chatId(chatId)
                .fileId(file.id)
                .fileName(fileName)
                .fileSize((long) file.size)
                .status(status)
                .progress(progress)
                .downloadedBytes((long) file.local.downloadedSize)
                .localPath(localPath)
                .startTime(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Get chat type as string
     */
    private static String getChatType(TdApi.ChatType type) {
        if (type instanceof TdApi.ChatTypePrivate) {
            return "PRIVATE";
        } else if (type instanceof TdApi.ChatTypeBasicGroup) {
            return "BASIC_GROUP";
        } else if (type instanceof TdApi.ChatTypeSupergroup) {
            TdApi.ChatTypeSupergroup supergroup = (TdApi.ChatTypeSupergroup) type;
            return supergroup.isChannel ? "CHANNEL" : "SUPERGROUP";
        } else if (type instanceof TdApi.ChatTypeSecret) {
            return "SECRET";
        }
        return "UNKNOWN";
    }

    /**
     * Get member status as string
     */
    private static String getMemberStatus(TdApi.ChatMemberStatus status) {
        if (status instanceof TdApi.ChatMemberStatusCreator) {
            return "CREATOR";
        } else if (status instanceof TdApi.ChatMemberStatusAdministrator) {
            return "ADMINISTRATOR";
        } else if (status instanceof TdApi.ChatMemberStatusMember) {
            return "MEMBER";
        } else if (status instanceof TdApi.ChatMemberStatusRestricted) {
            return "RESTRICTED";
        } else if (status instanceof TdApi.ChatMemberStatusLeft) {
            return "LEFT";
        } else if (status instanceof TdApi.ChatMemberStatusBanned) {
            return "BANNED";
        }
        return "UNKNOWN";
    }

    /**
     * Get online status as string
     */
    public static String getOnlineStatus(TdApi.UserStatus status) {
        if (status instanceof TdApi.UserStatusOnline) {
            return "ONLINE";
        } else if (status instanceof TdApi.UserStatusRecently) {
            return "RECENTLY";
        } else if (status instanceof TdApi.UserStatusLastWeek) {
            return "LAST_WEEK";
        } else if (status instanceof TdApi.UserStatusLastMonth) {
            return "LAST_MONTH";
        } else if (status instanceof TdApi.UserStatusOffline) {
            TdApi.UserStatusOffline offline = (TdApi.UserStatusOffline) status;
            if (offline.wasOnline > 0) {
                LocalDateTime lastOnline = LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(offline.wasOnline),
                        ZoneId.systemDefault()
                );
                LocalDateTime now = LocalDateTime.now();
                long daysSinceOnline = java.time.Duration.between(lastOnline, now).toDays();

                if (daysSinceOnline <= 7) {
                    return "RECENTLY";
                } else if (daysSinceOnline <= 30) {
                    return "LAST_MONTH";
                } else {
                    return "LONG_TIME_AGO";
                }
            }
            return "LONG_TIME_AGO";
        }
        return "OFFLINE";
    }

    /**
     * Get last online time
     */
    public static LocalDateTime getLastOnlineTime(TdApi.UserStatus status) {
        if (status instanceof TdApi.UserStatusOffline) {
            TdApi.UserStatusOffline offline = (TdApi.UserStatusOffline) status;
            if (offline.wasOnline > 0) {
                return LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(offline.wasOnline),
                        ZoneId.systemDefault()
                );
            }
        }
        return null;
    }
}

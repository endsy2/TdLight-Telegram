package com.example.tdlighttelegram.mapping;

import com.example.tdlighttelegram.model.GroupInfo;
import com.example.tdlighttelegram.model.MessageInfo;
import com.example.tdlighttelegram.util.UserUtil;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
public class MessageMapping {
    private final UserUtil userUtil;
    public GroupInfo convertToGroupInfo(TdApi.Chat chat) {
        return GroupInfo.builder()
                .id(chat.id)
                .title(chat.title)
                .isChannel(chat.type instanceof TdApi.ChatTypeSupergroup &&
                        ((TdApi.ChatTypeSupergroup) chat.type).isChannel)
                .isSupergroup(chat.type instanceof TdApi.ChatTypeSupergroup)
                .updatedAt(LocalDateTime.now())
                .build();
    }
    /**
     */
    public MessageInfo convertToMessageInfo(TdApi.Message message) {
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
            }
        } else if (message.content instanceof TdApi.MessageVideo) {
            TdApi.MessageVideo videoMessage = (TdApi.MessageVideo) message.content;
            messageType = "VIDEO";
            content = videoMessage.caption != null ? videoMessage.caption.text : "Video message";
            fileName = videoMessage.video.fileName;
            fileSize = (long) videoMessage.video.video.size;
            fileId = videoMessage.video.video.id; 

            
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
        } else if (message.content instanceof TdApi.MessageAudio) {
            TdApi.MessageAudio audioMessage = (TdApi.MessageAudio) message.content;
            messageType = "AUDIO";
            content = audioMessage.caption != null ? audioMessage.caption.text : "Audio message";
            fileName = audioMessage.audio.fileName;
            fileSize = (long) audioMessage.audio.audio.size;
        } else if (message.content instanceof TdApi.MessageVoiceNote) {
            messageType = "VOICE";
            content = "Voice message";
        } else if (message.content instanceof TdApi.MessageSticker) {
            messageType = "STICKER";
            content = "Sticker message";
        }

        
        boolean isForwarded = message.forwardInfo != null;
        Long forwardedFromChatId = null;
        String forwardedFromChatTitle = null;

        if (isForwarded) {
            
            forwardedFromChatTitle = "Forwarded message";
        }

        
        boolean isReply = message.replyTo != null;
        Long replyToMessageId = null;
        if (isReply && message.replyTo instanceof TdApi.MessageReplyToMessage) {
            TdApi.MessageReplyToMessage replyTo = (TdApi.MessageReplyToMessage) message.replyTo;
            replyToMessageId = replyTo.messageId;
        }

        return MessageInfo.builder()
                .id(message.id)
                .chatId(message.chatId)
                .senderId(message.senderId instanceof TdApi.MessageSenderUser ?
                        ((TdApi.MessageSenderUser) message.senderId).userId : null)
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
     */
    public MessageInfo convertToMessageInfo(TdApi.Message message, TdApi.Chat chat, TdApi.User user) {
        MessageInfo messageInfo = convertToMessageInfo(message);

        
        if (chat != null) {
            messageInfo.setChatTitle(chat.title);
        }

        
        if (user != null) {
            messageInfo.setSenderName(userUtil.buildUserDisplayName(user));
            if (user.usernames != null && user.usernames.activeUsernames.length > 0) {
                messageInfo.setSenderUsername(user.usernames.activeUsernames[0]);
            }
            
            // Get user profile photo URL
            if (user.profilePhoto != null && user.profilePhoto.small != null) {
                // Create URL to profile photo endpoint
                String photoUrl = "/api/telegram/media/profile-photos/" + user.id + ".jpg";
                messageInfo.setSenderPhotoUrl(photoUrl);
            }
        }

        return messageInfo;
    }
}

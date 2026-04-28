package com.example.tdlighttelegram.mapping;

import com.example.tdlighttelegram.model.ChatListItem;
import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Mapper for converting TdApi.Chat to ChatListItem
 */
@Slf4j
@Component
public class ChatListMapper {

    /**
     * Convert TdApi.Chat to ChatListItem (Telegram home screen format)
     */
    public ChatListItem toChatListItem(TdApi.Chat chat, Long currentUserId) {
        if (chat == null) {
            return null;
        }

        ChatListItem.ChatListItemBuilder builder = ChatListItem.builder()
                .chatId(chat.id)
                .title(chat.title)
                .unreadCount(chat.unreadCount)
                .unreadMentionCount(chat.unreadMentionCount)
                .hasUnreadMention(chat.unreadMentionCount > 0)
                .isPinned(chat.positions != null && chat.positions.length > 0 && chat.positions[0].isPinned)
                .isMarkedAsUnread(chat.isMarkedAsUnread)
                .order(chat.positions != null && chat.positions.length > 0 ? chat.positions[0].order : 0L);

        // Chat type
        builder.type(getChatType(chat.type));

        // Photo URL
        if (chat.photo != null && chat.photo.small != null) {
            builder.photoUrl(chat.photo.small.local.path);
        }

        // Last message
        if (chat.lastMessage != null) {
            TdApi.Message lastMsg = chat.lastMessage;
            builder.lastMessageId(lastMsg.id)
                    .lastMessageDate(convertToLocalDateTime(lastMsg.date))
                    .isOutgoing(lastMsg.isOutgoing);

            // Message content
            String[] contentInfo = extractMessageContent(lastMsg.content);
            builder.lastMessageText(contentInfo[0])
                    .lastMessageType(contentInfo[1]);

            // Sender info
            if (lastMsg.senderId != null) {
                if (lastMsg.senderId instanceof TdApi.MessageSenderUser senderUser) {
                    builder.senderId(senderUser.userId)
                            .senderType("user");
                } else if (lastMsg.senderId instanceof TdApi.MessageSenderChat senderChat) {
                    builder.senderId(senderChat.chatId)
                            .senderType("chat");
                }
            }

            // Message status (for outgoing messages)
            if (lastMsg.isOutgoing) {
                builder.messageStatus(getMessageStatus(lastMsg.sendingState));
            }

            // View count (for channels)
            if (lastMsg.interactionInfo != null) {
                builder.viewCount(lastMsg.interactionInfo.viewCount);
            }
        }

        // Draft message
        if (chat.draftMessage != null && chat.draftMessage.inputMessageText != null) {
            if (chat.draftMessage.inputMessageText instanceof TdApi.InputMessageText draftText) {
                builder.draftText(draftText.text.text)
                        .draftDate(convertToLocalDateTime(chat.draftMessage.date));
            }
        }

        // Notification settings
        if (chat.notificationSettings != null) {
            builder.isMuted(chat.notificationSettings.muteFor > 0)
                    .muteFor(chat.notificationSettings.muteFor)
                    .hasCustomNotification(chat.notificationSettings.useDefaultMuteFor == false);
        }

        // Permissions
        if (chat.permissions != null) {
            builder.canSendMessages(chat.permissions.canSendBasicMessages);
        }

        // Chat-specific info
        if (chat.type instanceof TdApi.ChatTypePrivate privateChat) {
            builder.phoneNumber(null); // Would need to fetch user info separately
        } else if (chat.type instanceof TdApi.ChatTypeSupergroup supergroupChat) {
            // Member count would need to be fetched separately
        } else if (chat.type instanceof TdApi.ChatTypeBasicGroup basicGroupChat) {
            // Member count would need to be fetched separately
        }

        // Pinned order
        if (chat.positions != null && chat.positions.length > 0) {
            for (TdApi.ChatPosition position : chat.positions) {
                if (position.isPinned) {
                    builder.pinnedOrder(position.order);
                    break;
                }
            }
        }

        // Has scheduled messages
        builder.hasScheduledMessages(chat.hasScheduledMessages);

        return builder.build();
    }

    /**
     * Get chat type as string
     */
    private String getChatType(TdApi.ChatType type) {
        if (type instanceof TdApi.ChatTypePrivate) {
            return "private";
        } else if (type instanceof TdApi.ChatTypeBasicGroup) {
            return "group";
        } else if (type instanceof TdApi.ChatTypeSupergroup supergroup) {
            return supergroup.isChannel ? "channel" : "supergroup";
        } else if (type instanceof TdApi.ChatTypeSecret) {
            return "secret";
        }
        return "unknown";
    }

    /**
     * Extract message content text and type
     * Returns [text, type]
     */
    private String[] extractMessageContent(TdApi.MessageContent content) {
        String text = "";
        String type = "unknown";

        if (content instanceof TdApi.MessageText messageText) {
            text = messageText.text.text;
            type = "text";
        } else if (content instanceof TdApi.MessagePhoto messagePhoto) {
            text = messagePhoto.caption != null ? messagePhoto.caption.text : "Photo";
            type = "photo";
        } else if (content instanceof TdApi.MessageVideo messageVideo) {
            text = messageVideo.caption != null ? messageVideo.caption.text : "Video";
            type = "video";
        } else if (content instanceof TdApi.MessageVoiceNote) {
            text = "Voice message";
            type = "voice";
        } else if (content instanceof TdApi.MessageVideoNote) {
            text = "Video message";
            type = "video_note";
        } else if (content instanceof TdApi.MessageDocument messageDocument) {
            text = messageDocument.caption != null ? messageDocument.caption.text : "Document";
            type = "document";
        } else if (content instanceof TdApi.MessageAudio messageAudio) {
            text = messageAudio.caption != null ? messageAudio.caption.text : "Audio";
            type = "audio";
        } else if (content instanceof TdApi.MessageSticker messageSticker) {
            text = messageSticker.sticker.emoji + " Sticker";
            type = "sticker";
        } else if (content instanceof TdApi.MessageAnimation messageAnimation) {
            text = messageAnimation.caption != null ? messageAnimation.caption.text : "GIF";
            type = "animation";
        } else if (content instanceof TdApi.MessageLocation) {
            text = "Location";
            type = "location";
        } else if (content instanceof TdApi.MessageContact) {
            text = "Contact";
            type = "contact";
        } else if (content instanceof TdApi.MessagePoll messagePoll) {
            text = "Poll: " + messagePoll.poll.question;
            type = "poll";
        } else if (content instanceof TdApi.MessageCall messageCall) {
            text = getCallDescription(messageCall);
            type = "call";
        } else if (content instanceof TdApi.MessageChatAddMembers) {
            text = "Members added";
            type = "service";
        } else if (content instanceof TdApi.MessageChatDeleteMember) {
            text = "Member removed";
            type = "service";
        } else if (content instanceof TdApi.MessageChatChangeTitle messageTitle) {
            text = "Title changed to: " + messageTitle.title;
            type = "service";
        } else if (content instanceof TdApi.MessageChatChangePhoto) {
            text = "Photo changed";
            type = "service";
        } else if (content instanceof TdApi.MessagePinMessage) {
            text = "Message pinned";
            type = "service";
        }

        return new String[]{text, type};
    }

    /**
     * Get call description
     */
    private String getCallDescription(TdApi.MessageCall call) {
        String callType = call.isVideo ? "Video call" : "Call";
        if (call.discardReason != null) {
            if (call.discardReason instanceof TdApi.CallDiscardReasonMissed) {
                return callType + " (missed)";
            } else if (call.discardReason instanceof TdApi.CallDiscardReasonDeclined) {
                return callType + " (declined)";
            }
        }
        return callType + " (" + call.duration + "s)";
    }

    /**
     * Get message sending status
     */
    private String getMessageStatus(TdApi.MessageSendingState sendingState) {
        if (sendingState == null) {
            return "sent";
        } else if (sendingState instanceof TdApi.MessageSendingStatePending) {
            return "sending";
        } else if (sendingState instanceof TdApi.MessageSendingStateFailed) {
            return "failed";
        }
        return "sent";
    }

    /**
     * Convert Unix timestamp to LocalDateTime
     */
    private LocalDateTime convertToLocalDateTime(int timestamp) {
        return LocalDateTime.ofInstant(
                Instant.ofEpochSecond(timestamp),
                ZoneId.systemDefault()
        );
    }

    /**
     * Enrich chat list item with user status (for private chats)
     */
    public void enrichWithUserStatus(ChatListItem item, TdApi.User user) {
        if (user == null || item == null) {
            return;
        }

        // User status
        if (user.status != null) {
            if (user.status instanceof TdApi.UserStatusOnline) {
                item.setUserStatus("online");
            } else if (user.status instanceof TdApi.UserStatusOffline offline) {
                item.setUserStatus("offline");
                item.setLastOnline(convertToLocalDateTime(offline.wasOnline));
            } else if (user.status instanceof TdApi.UserStatusRecently) {
                item.setUserStatus("recently");
            } else if (user.status instanceof TdApi.UserStatusLastWeek) {
                item.setUserStatus("lastWeek");
            } else if (user.status instanceof TdApi.UserStatusLastMonth) {
                item.setUserStatus("lastMonth");
            }
        }

        // Verification and premium
        item.setIsVerified(user.isVerified);
        item.setIsPremium(user.isPremium);
        item.setIsScam(user.isScam);
        item.setIsFake(user.isFake);

        // Username and phone
        if (user.usernames != null && user.usernames.activeUsernames.length > 0) {
            item.setUsername(user.usernames.activeUsernames[0]);
        }
        item.setPhoneNumber(user.phoneNumber);
    }

    /**
     * Enrich chat list item with supergroup info
     */
    public void enrichWithSupergroupInfo(ChatListItem item, TdApi.Supergroup supergroup, TdApi.SupergroupFullInfo fullInfo) {
        if (supergroup == null || item == null) {
            return;
        }

        item.setMemberCount(supergroup.memberCount);
        item.setIsVerified(supergroup.isVerified);
        item.setIsScam(supergroup.isScam);
        item.setIsFake(supergroup.isFake);

        if (supergroup.usernames != null && supergroup.usernames.activeUsernames.length > 0) {
            item.setUsername(supergroup.usernames.activeUsernames[0]);
        }

        if (fullInfo != null) {
            item.setDescription(fullInfo.description);
            // Online member count would be in fullInfo if available
        }
    }

    /**
     * Enrich chat list item with basic group info
     */
    public void enrichWithBasicGroupInfo(ChatListItem item, TdApi.BasicGroup basicGroup, TdApi.BasicGroupFullInfo fullInfo) {
        if (basicGroup == null || item == null) {
            return;
        }

        item.setMemberCount(basicGroup.memberCount);

        if (fullInfo != null) {
            item.setDescription(fullInfo.description);
        }
    }
}

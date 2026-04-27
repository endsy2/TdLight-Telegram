package com.example.tdlighttelegram.service;

import com.example.tdlighttelegram.config.TelegramProperties;
import com.example.tdlighttelegram.handler.UpdateHandler;
import com.example.tdlighttelegram.mapping.ChatListMapper;
import com.example.tdlighttelegram.mapping.MessageMapping;
import com.example.tdlighttelegram.model.*;
import com.example.tdlighttelegram.service.shared.TelegramCacheManager;
import com.example.tdlighttelegram.service.shared.TelegramClientProvider;
import com.example.tdlighttelegram.service.shared.TelegramFormatHelper;
import com.example.tdlighttelegram.util.AutoUtil;
import com.example.tdlighttelegram.util.TelegramUtil;
import com.example.tdlighttelegram.util.UserUtil;
import it.tdlight.client.*;
import it.tdlight.jni.TdApi;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Telegram Service Class
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramService {

    private final SimpleTelegramClientFactory clientFactory;
    private final TDLibSettings tdLibSettings;
    private final TelegramProperties telegramProperties;
    private final AuthenticationService authenticationService;
    private final WebSocketService webSocketService;
    private final TelegramClientProvider clientProvider;
    private final TelegramUtil telegramUtil;
    private final TelegramFormatHelper telegramFormatHelper;
    private final UpdateHandler updateHandler;
    private final TelegramCacheManager telegramCacheManager;

    private SimpleTelegramClient client;



    private final MessageMapping messageMapping;
    private final ChatListMapper chatListMapper;
    
    // Local storage path for media files
    private static final String MEDIA_STORAGE_PATH = "media-files";

    @PostConstruct
    public void initialize() {
        try {
            log.info("Initializing Telegram client...");

            // Create client builder
            SimpleTelegramClientBuilder clientBuilder = clientFactory.builder(tdLibSettings);

            // Add update handlers
            clientBuilder.addUpdateHandler(TdApi.UpdateAuthorizationState.class, updateHandler::onUpdateAuthorizationState);
            clientBuilder.addUpdateHandler(TdApi.UpdateNewMessage.class, updateHandler::onUpdateNewMessage);
            clientBuilder.addUpdateHandler(TdApi.UpdateNewChat.class, updateHandler::onUpdateNewChat);
            clientBuilder.addUpdateHandler(TdApi.UpdateChatMember.class, updateHandler::onUpdateChatMember);
            clientBuilder.addUpdateHandler(TdApi.UpdateFile.class, updateHandler::onUpdateFile);

            // Use user authentication supplier - initially no phone number, wait for API call
            // The phone number will be set dynamically via setAuthenticationPhoneNumber API
            SimpleAuthenticationSupplier<?> authenticationData = AuthenticationSupplier.user("");

            // Build client
            client = clientBuilder.build(authenticationData);

            // Share client with provider for other services
            clientProvider.setClient(client);

            log.info("Telegram client initialized successfully - waiting for phone number via API");
            log.info("Please use POST /api/telegram/auth/phone to submit phone number");
        } catch (Exception e) {
            log.error("Failed to initialize Telegram client", e);
            throw new RuntimeException("Failed to initialize Telegram client", e);
        }
    }


    /**
     * Get chat history with default limit (for WebSocket)
     *
     * @return CompletableFuture with list of chats
     */
    public CompletableFuture<List<TdApi.Chat>> getHistoryChat() {
        return getHistoryChat(100);
    }

    /**
     * Get enriched chat list (Telegram home screen format)
     * Returns chat list with all metadata like Telegram's home screen
     *
     * @param limit Maximum number of chats to return
     * @return CompletableFuture with list of enriched chat items
     */
    public CompletableFuture<List<ChatListItem>> getEnrichedChatList(int limit) {
        return getHistoryChat(limit).thenCompose(chats -> {
            if (chats == null || chats.isEmpty()) {
                return CompletableFuture.completedFuture(new ArrayList<>());
            }

            List<ChatListItem> enrichedChats = Collections.synchronizedList(new ArrayList<>());
            AtomicInteger counter = new AtomicInteger(chats.size());
            CompletableFuture<List<ChatListItem>> future = new CompletableFuture<>();

            for (TdApi.Chat chat : chats) {
                // Convert to ChatListItem
                ChatListItem item = chatListMapper.toChatListItem(chat, clientProvider.getCurrentUserId());

                // Enrich with additional data based on chat type
                if (chat.type instanceof TdApi.ChatTypePrivate privateChat) {
                    // Fetch user info for private chats
                    client.send(new TdApi.GetUser(privateChat.userId)).thenAccept(result -> {
                        if (result instanceof TdApi.User user) {
                            chatListMapper.enrichWithUserStatus(item, user);
                        }
                        enrichedChats.add(item);
                        if (counter.decrementAndGet() == 0) {
                            // Sort by order (pinned first, then by last message date)
                            enrichedChats.sort((a, b) -> {
                                // Pinned chats first
                                if (a.getIsPinned() && !b.getIsPinned()) return -1;
                                if (!a.getIsPinned() && b.getIsPinned()) return 1;
                                // Then by order
                                return Long.compare(b.getOrder(), a.getOrder());
                            });
                            future.complete(enrichedChats);
                        }
                    }).exceptionally(err -> {
                        log.warn("Failed to fetch user info for chat {}", chat.id);
                        enrichedChats.add(item);
                        if (counter.decrementAndGet() == 0) {
                            enrichedChats.sort((a, b) -> {
                                if (a.getIsPinned() && !b.getIsPinned()) return -1;
                                if (!a.getIsPinned() && b.getIsPinned()) return 1;
                                return Long.compare(b.getOrder(), a.getOrder());
                            });
                            future.complete(enrichedChats);
                        }
                        return null;
                    });
                } else if (chat.type instanceof TdApi.ChatTypeSupergroup supergroupChat) {
                    // Fetch supergroup info
                    client.send(new TdApi.GetSupergroup(supergroupChat.supergroupId)).thenAccept(result -> {
                        if (result instanceof TdApi.Supergroup supergroup) {
                            // Optionally fetch full info
                            client.send(new TdApi.GetSupergroupFullInfo(supergroupChat.supergroupId)).thenAccept(fullInfoResult -> {
                                if (fullInfoResult instanceof TdApi.SupergroupFullInfo fullInfo) {
                                    chatListMapper.enrichWithSupergroupInfo(item, supergroup, fullInfo);
                                } else {
                                    chatListMapper.enrichWithSupergroupInfo(item, supergroup, null);
                                }
                                enrichedChats.add(item);
                                if (counter.decrementAndGet() == 0) {
                                    enrichedChats.sort((a, b) -> {
                                        if (a.getIsPinned() && !b.getIsPinned()) return -1;
                                        if (!a.getIsPinned() && b.getIsPinned()) return 1;
                                        return Long.compare(b.getOrder(), a.getOrder());
                                    });
                                    future.complete(enrichedChats);
                                }
                            }).exceptionally(err -> {
                                chatListMapper.enrichWithSupergroupInfo(item, supergroup, null);
                                enrichedChats.add(item);
                                if (counter.decrementAndGet() == 0) {
                                    enrichedChats.sort((a, b) -> {
                                        if (a.getIsPinned() && !b.getIsPinned()) return -1;
                                        if (!a.getIsPinned() && b.getIsPinned()) return 1;
                                        return Long.compare(b.getOrder(), a.getOrder());
                                    });
                                    future.complete(enrichedChats);
                                }
                                return null;
                            });
                        } else {
                            enrichedChats.add(item);
                            if (counter.decrementAndGet() == 0) {
                                enrichedChats.sort((a, b) -> {
                                    if (a.getIsPinned() && !b.getIsPinned()) return -1;
                                    if (!a.getIsPinned() && b.getIsPinned()) return 1;
                                    return Long.compare(b.getOrder(), a.getOrder());
                                });
                                future.complete(enrichedChats);
                            }
                        }
                    }).exceptionally(err -> {
                        log.warn("Failed to fetch supergroup info for chat {}", chat.id);
                        enrichedChats.add(item);
                        if (counter.decrementAndGet() == 0) {
                            enrichedChats.sort((a, b) -> {
                                if (a.getIsPinned() && !b.getIsPinned()) return -1;
                                if (!a.getIsPinned() && b.getIsPinned()) return 1;
                                return Long.compare(b.getOrder(), a.getOrder());
                            });
                            future.complete(enrichedChats);
                        }
                        return null;
                    });
                } else if (chat.type instanceof TdApi.ChatTypeBasicGroup basicGroupChat) {
                    // Fetch basic group info
                    client.send(new TdApi.GetBasicGroup(basicGroupChat.basicGroupId)).thenAccept(result -> {
                        if (result instanceof TdApi.BasicGroup basicGroup) {
                            client.send(new TdApi.GetBasicGroupFullInfo(basicGroupChat.basicGroupId)).thenAccept(fullInfoResult -> {
                                if (fullInfoResult instanceof TdApi.BasicGroupFullInfo fullInfo) {
                                    chatListMapper.enrichWithBasicGroupInfo(item, basicGroup, fullInfo);
                                } else {
                                    chatListMapper.enrichWithBasicGroupInfo(item, basicGroup, null);
                                }
                                enrichedChats.add(item);
                                if (counter.decrementAndGet() == 0) {
                                    enrichedChats.sort((a, b) -> {
                                        if (a.getIsPinned() && !b.getIsPinned()) return -1;
                                        if (!a.getIsPinned() && b.getIsPinned()) return 1;
                                        return Long.compare(b.getOrder(), a.getOrder());
                                    });
                                    future.complete(enrichedChats);
                                }
                            }).exceptionally(err -> {
                                chatListMapper.enrichWithBasicGroupInfo(item, basicGroup, null);
                                enrichedChats.add(item);
                                if (counter.decrementAndGet() == 0) {
                                    enrichedChats.sort((a, b) -> {
                                        if (a.getIsPinned() && !b.getIsPinned()) return -1;
                                        if (!a.getIsPinned() && b.getIsPinned()) return 1;
                                        return Long.compare(b.getOrder(), a.getOrder());
                                    });
                                    future.complete(enrichedChats);
                                }
                                return null;
                            });
                        } else {
                            enrichedChats.add(item);
                            if (counter.decrementAndGet() == 0) {
                                enrichedChats.sort((a, b) -> {
                                    if (a.getIsPinned() && !b.getIsPinned()) return -1;
                                    if (!a.getIsPinned() && b.getIsPinned()) return 1;
                                    return Long.compare(b.getOrder(), a.getOrder());
                                });
                                future.complete(enrichedChats);
                            }
                        }
                    }).exceptionally(err -> {
                        log.warn("Failed to fetch basic group info for chat {}", chat.id);
                        enrichedChats.add(item);
                        if (counter.decrementAndGet() == 0) {
                            enrichedChats.sort((a, b) -> {
                                if (a.getIsPinned() && !b.getIsPinned()) return -1;
                                if (!a.getIsPinned() && b.getIsPinned()) return 1;
                                return Long.compare(b.getOrder(), a.getOrder());
                            });
                            future.complete(enrichedChats);
                        }
                        return null;
                    });
                } else {
                    // Other chat types (secret, etc.)
                    enrichedChats.add(item);
                    if (counter.decrementAndGet() == 0) {
                        enrichedChats.sort((a, b) -> {
                            if (a.getIsPinned() && !b.getIsPinned()) return -1;
                            if (!a.getIsPinned() && b.getIsPinned()) return 1;
                            return Long.compare(b.getOrder(), a.getOrder());
                        });
                        future.complete(enrichedChats);
                    }
                }
            }

            return future;
        });
    }

    /**
     * Get chat history with custom limit
     * <p>
     * Note: TDLib's GetChats API returns chats in order by their position in the chat list.
     * For pagination, you would need to use LoadChats first to load more chats into the list,
     * then call GetChats again. The current implementation returns the most recent chats.
     *
     * @param limit Maximum number of chats to return
     * @return CompletableFuture with list of chats
     */
    public CompletableFuture<List<TdApi.Chat>> getHistoryChat(int limit) {
        TdApi.GetChats request = new TdApi.GetChats();
        request.limit = limit;

        CompletableFuture<List<TdApi.Chat>> future = new CompletableFuture<>();

        client.send(request).thenAccept(result -> {
            if (!(result instanceof TdApi.Chats chats)) {
                future.completeExceptionally(new RuntimeException("Invalid response"));
                return;
            }

            long[] chatIds = chats.chatIds;

            // Handle empty chat list
            if (chatIds == null || chatIds.length == 0) {
                log.info("No chats found");
                future.complete(new ArrayList<>());
                return;
            }

            List<TdApi.Chat> chatList = Collections.synchronizedList(new ArrayList<>());
            AtomicInteger counter = new AtomicInteger(chatIds.length);

            log.info("Fetching {} chats...", chatIds.length);

            for (long chatId : chatIds) {
                TdApi.GetChat requestChat = new TdApi.GetChat(chatId);

                client.send(requestChat).thenAccept(chatResult -> {
                    if (chatResult instanceof TdApi.Chat chat) {
                        chatList.add(chat);

                        if (chat.lastMessage != null) {
                            log.debug("Chat: {} -> last message id: {}", chat.title, chat.lastMessage.id);
                        }
                    } else {
                        log.warn("Failed to get chat info for chatId: {}", chatId);
                    }

                    // Check if all requests finished
                    if (counter.decrementAndGet() == 0) {
                        log.info("Successfully fetched {} chats", chatList.size());
                        future.complete(chatList);
                    }
                }).exceptionally(err -> {
                    log.error("Error fetching chat {}: {}", chatId, err.getMessage());

                    // Still decrement counter even on error
                    if (counter.decrementAndGet() == 0) {
                        log.info("Completed fetching chats with some errors. Total: {}", chatList.size());
                        future.complete(chatList);
                    }
                    return null;
                });
            }
        }).exceptionally(err -> {
            log.error("Error fetching chat list", err);
            future.completeExceptionally(err);
            return null;
        });

        return future;
    }


    public CompletableFuture< ProfileResponse> getUserProfile(long userId) {
        TdApi.GetUserProfilePhotos request = new TdApi.GetUserProfilePhotos();
        request.userId = userId;
        request.limit=1;
        request.offset=0;
        return client.send(request).thenApply(result->{
            if (result instanceof TdApi.ChatPhotos photos) {
                log.info("photo:{}", photos.photos[0]);
                log.info("total:{}", photos.totalCount);
            }
            return ProfileResponse.builder()
                    .photo(result.photos[0])
                    .totalCount(result.totalCount)
                    .build();
        });
//        return null;
    }




    /**
     * Get authentication state
     */
    public String getAuthenticationState() {
        return authenticationService.getAuthenticationState();
    }

    /**
     * Set authentication phone number for login
     */
    public CompletableFuture<Boolean> setAuthenticationPhoneNumber(String phoneNumber) {
        try {
            log.info("Setting authentication phone number: {}", phoneNumber);

            // Check current authentication state
            String currentState = authenticationService.getAuthenticationState();
            log.info("Current authentication state: {}", currentState);

            // If not in WAITING_FOR_PHONE state, handle differently
            if (!"WAITING_FOR_PHONE".equals(currentState) && !"NONE".equals(currentState)) {
                log.warn("Cannot set phone number in current state: {}. Must be in WAITING_FOR_PHONE state.", currentState);

                // If already authenticated, return success
                if ("READY".equals(currentState)) {
                    log.info("Already authenticated");
                    return CompletableFuture.completedFuture(true);
                }

                // If waiting for code or password, phone is already set
                if ("WAITING_FOR_CODE".equals(currentState) || "WAITING_FOR_PASSWORD".equals(currentState)) {
                    log.info("Phone number already set, currently in {} state", currentState);
                    return CompletableFuture.completedFuture(true);
                }

                return CompletableFuture.completedFuture(false);
            }

            TdApi.SetAuthenticationPhoneNumber request = new TdApi.SetAuthenticationPhoneNumber();
            request.phoneNumber = phoneNumber;
            request.settings = new TdApi.PhoneNumberAuthenticationSettings();
            request.settings.allowFlashCall = false;
            request.settings.allowMissedCall = false;
            request.settings.isCurrentPhoneNumber = false;
            request.settings.allowSmsRetrieverApi = false;
            request.settings.authenticationTokens = new String[0];

            return client.send(request)
                    .thenApply(result -> {
                        log.info("Phone number set successfully, waiting for verification code");
                        authenticationService.setAuthenticationState("WAITING_FOR_CODE");
                        return true;
                    })
                    .exceptionally(throwable -> {
                        log.error("Failed to set phone number", throwable);
                        Throwable cause = throwable.getCause();
                        if (cause != null) {
                            log.error("Error details: {}", cause.getMessage());
                        }
                        return false;
                    });

        } catch (Exception e) {
            log.error("Error setting phone number", e);
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Submit verification code
     */
    public CompletableFuture<Boolean> submitVerificationCode(String code) {
        try {
            TdApi.CheckAuthenticationCode request = new TdApi.CheckAuthenticationCode();
            request.code = code;

            return client.send(request)
                    .thenApply(result -> {
                        log.info("Verification code submitted successfully");
                        return true;
                    })
                    .exceptionally(throwable -> {
                        log.error("Failed to submit verification code", throwable);
                        return false;
                    });

        } catch (Exception e) {
            log.error("Error submitting verification code", e);
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Submit password (two-factor authentication)
     */
    public CompletableFuture<Boolean> submitPassword(String password) {
        try {
            TdApi.CheckAuthenticationPassword request = new TdApi.CheckAuthenticationPassword();
            request.password = password;

            return client.send(request)
                    .thenApply(result -> {
                        log.info("Password submitted successfully");
                        return true;
                    })
                    .exceptionally(throwable -> {
                        log.error("Failed to submit password", throwable);
                        return false;
                    });

        } catch (Exception e) {
            log.error("Error submitting password", e);
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Join a group
     */
    public CompletableFuture<Boolean> joinGroup(String inviteLink) {
        try {
            TdApi.JoinChatByInviteLink request = new TdApi.JoinChatByInviteLink();
            request.inviteLink = inviteLink;

            return client.send(request)
                    .thenApply(result -> {
                        log.info("Successfully joined group via invite link: {}", inviteLink);
                        return true;
                    })
                    .exceptionally(throwable -> {
                        log.error("Failed to join group via invite link: {}", inviteLink, throwable);
                        return false;
                    });

        } catch (Exception e) {
            log.error("Error joining group", e);
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Get group information list
     */
    public List<GroupInfo> getGroupInfos() {
        return new ArrayList<>(telegramCacheManager.getGroupInfoCache().values());
    }

    /**
     * Get message history
     */
    public List<MessageInfo> getMessageHistory() {
        return new ArrayList<>(telegramCacheManager.getMessageHistory());
    }

    /**
     * Get message history for specified group
     */
//    public List<MessageInfo> getGroupMessages(Long groupId) {
//        return telegramCacheManager.getMessageHistory().stream()
//                .filter(message -> groupId.equals(message.getChatId()))
//                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
//    }

    /**
     * Get message history for a specific chat (with pagination)
     */
    public CompletableFuture<List<MessageInfo>> getMessagesByChatId(Long chatId, int limit, int offset, Long fromMessageId) {
        try {
//            // First, ensure the chat is loaded
            TdApi.GetChat getChatRequest = new TdApi.GetChat();
            getChatRequest.chatId = chatId;

//            TdApi.GetChatHistory getChatRequest = new TdApi.GetChatHistory();
//            getChatRequest.chatId = chatId;

            return client.send(getChatRequest)
                    .thenCompose(chatResult -> {
                        if (!(chatResult instanceof TdApi.Chat)) {
                            log.warn("Failed to get chat {} before fetching messages", chatId);
                            return CompletableFuture.completedFuture(new ArrayList<>());
                        }

                        log.debug("Chat {} loaded successfully, fetching messages...", chatId);

                        // For first request, use longer delay and retry mechanism
                        if (fromMessageId == null || fromMessageId == 0) {
                            return fetchMessagesWithRetry(chatId, limit, offset, 0, 3);
                        } else {
                            // For subsequent requests, fetch directly
                            return fetchMessages(chatId, limit, offset, fromMessageId);
                        }
                    })
                    .exceptionally(throwable -> {
                        log.error("Failed to get chat {} before fetching messages", chatId, throwable);
                        return new ArrayList<>();
                    });
        } catch (Exception e) {
            log.error("Error getting messages for chat {}", chatId, e);
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
    }
//    public CompletableFuture<List<MessageInfo>> getMessagesByChatId(
//            Long chatId, int limit, int offset, Long fromMessageId) {
//
//        TdApi.GetChatHistory request = new TdApi.GetChatHistory();
//        request.chatId = chatId;
//        request.limit = limit;
//        request.offset = offset;
//        request.fromMessageId = (fromMessageId == null) ? 0 : fromMessageId;
//
//        return client.send(request)
//                .thenApply(result -> {
//                    if (!(result instanceof TdApi.Messages messages)) {
//                        log.warn("Invalid response for chat {}", chatId);
//                        return List.of();
//                    }
//
//                    log.info("Received {} messages for chat {}", messages.messages.length, chatId);
//
//                    // Convert TdApi.Message to MessageInfo
//                    return Arrays.stream(messages.messages)
//                            .map(message -> {
//                                try {
//                                    return messageMapping.convertToMessageInfo(message);
//                                } catch (Exception e) {
//                                    log.error("Error converting message {}", message.id, e);
//                                    return null;
//                                }
//                            })
//                            .filter(Objects::nonNull)
//                            .toList();
//                })
//                .exceptionally(e -> {
//                    log.error("Error fetching messages for chat {}", chatId, e);
//                    return List.of();
//                });
//    }
    /**
     * Open a chat to mark messages as read
     * This tells Telegram that the user is viewing this chat, which automatically marks messages as read
     */
    public CompletableFuture<Boolean> openChat(Long chatId) {
        try {
            log.info("Opening chat {} to mark messages as read", chatId);

            TdApi.OpenChat request = new TdApi.OpenChat();
            request.chatId = chatId;

            return client.send(request)
                    .thenCompose(result -> {
                        log.info("Successfully opened chat {}", chatId);
                        
                        // Fetch updated chat info to get new unread count
                        TdApi.GetChat getChatRequest = new TdApi.GetChat();
                        getChatRequest.chatId = chatId;
                        
                        return client.send(getChatRequest)
                                .thenApply(chatResult -> {
                                    if (chatResult instanceof TdApi.Chat) {
                                        TdApi.Chat chat = (TdApi.Chat) chatResult;
                                        // Broadcast chat update via WebSocket
                                        webSocketService.sendChatUpdated(chat);
                                        log.info("Broadcasted chat update for chat {} - unread count: {}", chatId, chat.unreadCount);
                                    }
                                    return true;
                                })
                                .exceptionally(throwable -> {
                                    log.error("Error fetching chat info after opening: {}", throwable.getMessage());
                                    return true; // Still return true as openChat succeeded
                                });
                    })
                    .exceptionally(throwable -> {
                        log.error("Error opening chat {}: {}", chatId, throwable.getMessage());
                        return false;
                    });
        } catch (Exception e) {
            log.error("Exception opening chat {}", chatId, e);
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Close a chat to stop marking messages as read
     * This tells Telegram that the user is no longer viewing this chat
     */
    public CompletableFuture<Boolean> closeChat(Long chatId) {
        try {
            log.info("Closing chat {}", chatId);

            TdApi.CloseChat request = new TdApi.CloseChat();
            request.chatId = chatId;

            return client.send(request)
                    .thenCompose(result -> {
                        log.info("Successfully closed chat {}", chatId);
                        
                        // Fetch updated chat info
                        TdApi.GetChat getChatRequest = new TdApi.GetChat();
                        getChatRequest.chatId = chatId;
                        
                        return client.send(getChatRequest)
                                .thenApply(chatResult -> {
                                    if (chatResult instanceof TdApi.Chat) {
                                        TdApi.Chat chat = (TdApi.Chat) chatResult;
                                        // Broadcast chat update via WebSocket
                                        webSocketService.sendChatUpdated(chat);
                                        log.info("Broadcasted chat update for chat {} after closing", chatId);
                                    }
                                    return true;
                                })
                                .exceptionally(throwable -> {
                                    log.error("Error fetching chat info after closing: {}", throwable.getMessage());
                                    return true; // Still return true as closeChat succeeded
                                });
                    })
                    .exceptionally(throwable -> {
                        log.error("Error closing chat {}: {}", chatId, throwable.getMessage());
                        return false;
                    });
        } catch (Exception e) {
            log.error("Exception closing chat {}", chatId, e);
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Fetch messages with retry mechanism for TDLib sync issues
     */
    private CompletableFuture<List<MessageInfo>> fetchMessagesWithRetry(Long chatId, int limit, int offset, long fromMessageId, int maxRetries) {
        return fetchMessages(chatId, limit, offset, fromMessageId)
                .thenCompose(messages -> {
                    // Only retry if:
                    // 1. We got exactly 1 message
                    // 2. We requested more than 1
                    // 3. We have retries left
                    // 4. This is the first fetch (fromMessageId == 0)
                    // 
                    // The key insight: if it's a first fetch and TDLib returns only 1 message
                    // when we asked for more, it's likely a sync issue, not that the chat
                    // only has 1 message. After retries, we accept whatever we get.
                    if (messages.size() == 1 && limit > 1 && maxRetries > 0 && fromMessageId == 0) {
                        log.info("Only 1 message returned for chat {} on first fetch, retrying... ({} retries left)", chatId, maxRetries);

                        // Wait 300ms before retry to let TDLib sync
                        return CompletableFuture.runAsync(() -> {
                            try {
                                Thread.sleep(300);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }).thenCompose(v -> fetchMessagesWithRetry(chatId, limit, offset, fromMessageId, maxRetries - 1));
                    }
                    return CompletableFuture.completedFuture(messages);
                });
    }

    /**
     * Fetch messages from TDLib
     */
    private CompletableFuture<List<MessageInfo>> fetchMessages(Long chatId, int limit, int offset, long fromMessageId) {
        TdApi.GetChatHistory request = new TdApi.GetChatHistory();
        request.chatId = chatId;
        request.limit = limit;
        request.fromMessageId = fromMessageId;
        request.offset = offset;
        request.onlyLocal = false;

        if (fromMessageId == 0) {
            log.info("Fetching latest {} messages from chat {} with offset {}", limit, chatId, offset);
        } else {
            log.info("Fetching {} messages from chat {} starting from messageId {} with offset {}", limit, chatId, fromMessageId, offset);
        }

        CompletableFuture<List<MessageInfo>> future = new CompletableFuture<>();

        client.send(request)
                .thenAccept(result -> {
                    try {
                        if (result instanceof TdApi.Messages) {
                            TdApi.Messages messages = (TdApi.Messages) result;
                            List<MessageInfo> messageInfos = new ArrayList<>();

                            log.info("TDLib returned {} messages for chat {}", messages.messages.length, chatId);

                            if (messages.messages.length == 0) {
                                log.warn("No messages returned from TDLib for chat {}", chatId);
                            } else if (messages.messages.length == 1 && limit > 1) {
                                log.warn("Only 1 message returned when {} were requested for chat {}. Chat may need more sync time.", limit, chatId);
                            }

                            for (TdApi.Message message : messages.messages) {
                                try {
                                    MessageInfo messageInfo = messageMapping.convertToMessageInfo(message);
                                    messageInfos.add(messageInfo);
                                } catch (Exception e) {
                                    log.error("Error converting message {} from chat {}", message.id, chatId, e);
                                }
                            }

                            log.info("Successfully converted {} messages from chat {}", messageInfos.size(), chatId);
                            
                            // Process media files - download from Telegram and upload to MinIO
                            processMediaFilesForMessages(messageInfos)
                                    .thenAccept(v -> {
                                        log.info("Media processing completed for {} messages", messageInfos.size());
                                        future.complete(messageInfos);
                                    })
                                    .exceptionally(e -> {
                                        log.error("Error processing media files, returning messages without URLs", e);
                                        future.complete(messageInfos);
                                        return null;
                                    });
                        } else {
                            log.warn("Unexpected result type from TDLib: {}", result.getClass().getSimpleName());
                            future.complete(new ArrayList<>());
                        }
                    } catch (Exception e) {
                        log.error("Error processing messages from chat {}", chatId, e);
                        future.complete(new ArrayList<>());
                    }
                })
                .exceptionally(throwable -> {
                    log.error("Failed to get messages for chat {}", chatId, throwable);
                    future.complete(new ArrayList<>());
                    return null;
                });

        return future;
    }


    /**
     * Get group information
     */
    public CompletableFuture<GroupInfo> getGroupInfo(Long groupId) {
        try {
            TdApi.GetChat request = new TdApi.GetChat();
            request.chatId = groupId;

            return client.send(request)
                    .thenApply(result -> {
                        if (result instanceof TdApi.Chat) {
                            TdApi.Chat chat = (TdApi.Chat) result;
                            GroupInfo groupInfo = messageMapping.convertToGroupInfo(chat);
                            telegramCacheManager.getGroupInfoCache().put(chat.id, groupInfo);
                            return groupInfo;
                        }
                        return null;
                    })
                    .exceptionally(throwable -> {
                        log.error("Failed to get group info for {}", groupId, throwable);
                        return null;
                    });
        } catch (Exception e) {
            log.error("Error getting group info", e);
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Get video messages for specified group (from cache)
     */
    public List<MessageInfo> getGroupVideoMessages(Long groupId) {
        List<MessageInfo> videoMessages = telegramCacheManager.getGroupVideoMessagesCache().get(groupId);
        if (videoMessages == null) {
            return new ArrayList<>();
        }
        // Return a copy, sorted in reverse chronological order (newest first)
        return videoMessages.stream()
                .sorted((m1, m2) -> m2.getMessageDate().compareTo(m1.getMessageDate()))
                .collect(Collectors.toList());
    }

    /**
     * Get video message history for specified group (from Telegram server)
     */
    public CompletableFuture<List<MessageInfo>> getGroupVideoMessages(Long groupId, int limit, Long fromMessageId) {
        try {
            CompletableFuture<List<MessageInfo>> future = new CompletableFuture<>();

            TdApi.GetChatHistory request = new TdApi.GetChatHistory();
            request.chatId = groupId;
            request.limit = Math.max(limit * 5, 100); // Fetch more messages to ensure enough video messages
            request.fromMessageId = fromMessageId != null ? fromMessageId : 0;
            request.offset = 0;
            request.onlyLocal = false;

            client.send(request)
                    .thenAccept(result -> {
                        try {
                            if (result instanceof TdApi.Messages) {
                                TdApi.Messages messages = (TdApi.Messages) result;
                                List<MessageInfo> videoMessages = new ArrayList<>();

                                for (TdApi.Message message : messages.messages) {
                                    MessageInfo messageInfo = messageMapping.convertToMessageInfo(message);
                                    // Keep only video messages
                                    if ("VIDEO".equals(messageInfo.getMessageType())) {
                                        videoMessages.add(messageInfo);

                                        // Update cache at the same time
                                        telegramCacheManager.getGroupVideoMessagesCache().computeIfAbsent(groupId, k -> new ArrayList<>())
                                                .add(messageInfo);
                                    }

                                    // Stop processing if we have enough video messages
                                    if (videoMessages.size() >= limit) {
                                        break;
                                    }
                                }

                                log.info("Retrieved {} video messages from group {}", videoMessages.size(), groupId);
                                future.complete(videoMessages);
                            } else {
                                future.complete(new ArrayList<>());
                            }
                        } catch (Exception e) {
                            log.error("Error processing video messages", e);
                            future.complete(new ArrayList<>());
                        }
                    })
                    .exceptionally(throwable -> {
                        log.error("Failed to get video messages for group {}", groupId, throwable);
                        future.complete(new ArrayList<>());
                        return null;
                    });

            return future;
        } catch (Exception e) {
            log.error("Error getting group video messages", e);
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
    }

    /**
     * Download video file
     */
    public CompletableFuture<DownloadInfo> downloadVideo(Long messageId, Long chatId) {
        try {
            log.info("Starting video download: messageId={}, chatId={}", messageId, chatId);

            // Find video message from cache
            MessageInfo videoMessage = findVideoMessage(messageId, chatId);
            if (videoMessage == null) {
                log.error("Video message not found: messageId={}, chatId={}", messageId, chatId);
                log.info("Current video cache status: chatId={}, cached message count={}",
                        chatId,
                        telegramCacheManager.getGroupVideoMessagesCache().getOrDefault(chatId, new ArrayList<>()).size());
                return CompletableFuture.failedFuture(
                        new IllegalArgumentException("Video message not found: " + messageId));
            }

            if (videoMessage.getFileId() == null) {
                return CompletableFuture.failedFuture(
                        new IllegalArgumentException("File ID not available for message: " + messageId));
            }

            // Generate download ID
            String downloadId = UUID.randomUUID().toString();

            // Create download info
            DownloadInfo downloadInfo = DownloadInfo.builder()
                    .downloadId(downloadId)
                    .messageId(messageId)
                    .chatId(chatId)
                    .fileId(videoMessage.getFileId())
                    .fileName(videoMessage.getFileName())
                    .fileSize(videoMessage.getFileSize())
                    .status("PENDING")
                    .progress(0)
                    .downloadedBytes(0L)
                    .startTime(LocalDateTime.now())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            // Cache download info
            telegramCacheManager.getDownloadCache().put(downloadId, downloadInfo);

            // Build download path
            Path downloadDir = Paths.get(telegramProperties.getDownloadsPath());
            String fileName = videoMessage.getFileName() != null ?
                    videoMessage.getFileName() : "video_" + messageId + ".mp4";

            // Start download
            TdApi.DownloadFile request = new TdApi.DownloadFile();
            request.fileId = videoMessage.getFileId();
            request.priority = 1;
            request.offset = 0;
            request.limit = 0; // Download entire file
            request.synchronous = false;

            return client.send(request)
                    .thenApply(result -> {
                        if (result instanceof TdApi.File) {
                            TdApi.File file = (TdApi.File) result;
                            downloadInfo.setStatus("DOWNLOADING");
                            downloadInfo.setUpdatedAt(LocalDateTime.now());

                            log.info("Started downloading video: {} (File ID: {})", fileName, videoMessage.getFileId());
                            return downloadInfo;
                        } else {
                            downloadInfo.setStatus("FAILED");
                            downloadInfo.setErrorMessage("Failed to start download");
                            downloadInfo.setUpdatedAt(LocalDateTime.now());
                            return downloadInfo;
                        }
                    })
                    .exceptionally(throwable -> {
                        log.error("Failed to start video download", throwable);
                        downloadInfo.setStatus("FAILED");
                        downloadInfo.setErrorMessage(throwable.getMessage());
                        downloadInfo.setUpdatedAt(LocalDateTime.now());
                        return downloadInfo;
                    });

        } catch (Exception e) {
            log.error("Error downloading video", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Get download status
     */
    public DownloadInfo getDownloadStatus(String downloadId) {
        return telegramCacheManager.getDownloadCache().get(downloadId);
    }

    /**
     * Cancel download
     */
    public CompletableFuture<Boolean> cancelDownload(String downloadId) {
        try {
            DownloadInfo downloadInfo = telegramCacheManager.getDownloadCache().get(downloadId);
            if (downloadInfo == null) {
                return CompletableFuture.completedFuture(false);
            }

            TdApi.CancelDownloadFile request = new TdApi.CancelDownloadFile();
            request.fileId = downloadInfo.getFileId();
            request.onlyIfPending = false;

            return client.send(request)
                    .thenApply(result -> {
                        downloadInfo.setStatus("CANCELLED");
                        downloadInfo.setUpdatedAt(LocalDateTime.now());
                        log.info("Download cancelled: {}", downloadId);
                        return true;
                    })
                    .exceptionally(throwable -> {
                        log.error("Failed to cancel download: {}", downloadId, throwable);
                        return false;
                    });
        } catch (Exception e) {
            log.error("Error cancelling download", e);
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Get all download tasks
     */
    public List<DownloadInfo> getAllDownloads() {
        return new ArrayList<>(telegramCacheManager.getDownloadCache().values());
    }

    /**
     * Download video thumbnail
     */
    public CompletableFuture<DownloadInfo> downloadVideoThumbnail(Long messageId, Long chatId) {
        try {
            // Find video message from cache
            MessageInfo videoMessage = findVideoMessage(messageId, chatId);
            if (videoMessage == null) {
                return CompletableFuture.failedFuture(
                        new IllegalArgumentException("Video message not found: " + messageId));
            }

            if (videoMessage.getThumbnailFileId() == null) {
                return CompletableFuture.failedFuture(
                        new IllegalArgumentException("Thumbnail not available for message: " + messageId));
            }

            // Generate download ID
            String downloadId = UUID.randomUUID().toString();

            // Generate thumbnail filename
            final String thumbnailFileName = "thumbnail_" + messageId + "_" + chatId +
                    (videoMessage.getThumbnailFormat() != null ?
                            getThumbnailExtension(videoMessage.getThumbnailFormat()) : ".jpg");

            // Get thumbnail file ID (for lambda expression)
            final Integer thumbnailFileId = videoMessage.getThumbnailFileId();

            // Create download info
            DownloadInfo downloadInfo = DownloadInfo.builder()
                    .downloadId(downloadId)
                    .messageId(messageId)
                    .chatId(chatId)
                    .fileId(thumbnailFileId)
                    .fileName(thumbnailFileName)
                    .fileSize(0L) // Thumbnail size is usually small, may not be available in advance
                    .status("PENDING")
                    .progress(0)
                    .downloadedBytes(0L)
                    .startTime(LocalDateTime.now())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            // Cache download info
            telegramCacheManager.getDownloadCache().put(downloadId, downloadInfo);

            // Start downloading thumbnail
            TdApi.DownloadFile request = new TdApi.DownloadFile();
            request.fileId = thumbnailFileId;
            request.priority = 2; // Thumbnail has slightly lower priority
            request.offset = 0;
            request.limit = 0; // Download entire file
            request.synchronous = false;

            return client.send(request)
                    .thenApply(result -> {
                        if (result instanceof TdApi.File) {
                            TdApi.File file = (TdApi.File) result;
                            downloadInfo.setStatus("DOWNLOADING");
                            downloadInfo.setUpdatedAt(LocalDateTime.now());

                            log.info("Started downloading video thumbnail: {} (File ID: {})",
                                    thumbnailFileName, thumbnailFileId);
                            return downloadInfo;
                        } else {
                            downloadInfo.setStatus("FAILED");
                            downloadInfo.setErrorMessage("Failed to start thumbnail download");
                            downloadInfo.setUpdatedAt(LocalDateTime.now());
                            return downloadInfo;
                        }
                    })
                    .exceptionally(throwable -> {
                        log.error("Failed to start thumbnail download", throwable);
                        downloadInfo.setStatus("FAILED");
                        downloadInfo.setErrorMessage(throwable.getMessage());
                        downloadInfo.setUpdatedAt(LocalDateTime.now());
                        return downloadInfo;
                    });

        } catch (Exception e) {
            log.error("Error downloading video thumbnail", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Get file extension based on thumbnail format
     */
    private String getThumbnailExtension(String thumbnailFormat) {
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

    /**
     * Add message to corresponding cache
     */
    private void addMessageToCache(MessageInfo messageInfo) {
        try {
            // Add to message history
            telegramCacheManager.getMessageHistory().add(messageInfo);

            // If it's a video message, add to group video message cache
            if ("VIDEO".equals(messageInfo.getMessageType())) {
                Long chatId = messageInfo.getChatId();
                telegramCacheManager.getGroupVideoMessagesCache().computeIfAbsent(chatId, k -> new ArrayList<>()).add(messageInfo);
                log.info("Video message cached for group: {} (messageId: {})", chatId, messageInfo.getId());
            }

            log.debug("Message added to cache: type={}, messageId={}, chatId={}",
                    messageInfo.getMessageType(), messageInfo.getId(), messageInfo.getChatId());
        } catch (Exception e) {
            log.error("Error adding message to cache", e);
        }
    }

    /**
     * Find video message
     */
    private MessageInfo findVideoMessage(Long messageId, Long chatId) {
        // First search in video message cache
        List<MessageInfo> videoMessages = telegramCacheManager.getGroupVideoMessagesCache().get(chatId);
        if (videoMessages != null) {
            for (MessageInfo message : videoMessages) {
                if (messageId.equals(message.getId())) {
                    log.debug("Found video message in cache: messageId={}, chatId={}", messageId, chatId);
                    return message;
                }
            }
        }

        // Search in all message history
        for (MessageInfo message : telegramCacheManager.getMessageHistory()) {
            if (messageId.equals(message.getId()) && chatId.equals(message.getChatId())
                    && "VIDEO".equals(message.getMessageType())) {
                log.debug("Found video message in history: messageId={}, chatId={}", messageId, chatId);
                return message;
            }
        }

        log.warn("Video message not found in cache or history: messageId={}, chatId={}", messageId, chatId);
        return null;
    }

    /**
     * Convert to GroupInfo
     */






    /**
     * Get group member list (backward compatible method, excludes bots by default)
     */
    public CompletableFuture<List<GroupMemberInfo>> getGroupMembers(Long groupId, boolean excludeAdmins, boolean onlyActiveUsers) {
        return getGroupMembers(groupId, excludeAdmins, onlyActiveUsers, true); // Exclude bots by default
    }

    /**
     * Get group member list (non-admin, active users, exclude bots)
     */
    public CompletableFuture<List<GroupMemberInfo>> getGroupMembers(Long groupId, boolean excludeAdmins, boolean onlyActiveUsers, boolean excludeBots) {
        try {
            log.info("Starting to get member list for group {}, exclude admins: {}, only active users: {}, exclude bots: {}", groupId, excludeAdmins, onlyActiveUsers, excludeBots);

            CompletableFuture<List<GroupMemberInfo>> future = new CompletableFuture<>();

            // First get group information to determine group type
            TdApi.GetChat getChatRequest = new TdApi.GetChat();
            getChatRequest.chatId = groupId;

            client.send(getChatRequest)
                    .thenCompose(chatResult -> {
                        if (!(chatResult instanceof TdApi.Chat)) {
                            throw new RuntimeException("Unable to get group information");
                        }

                        TdApi.Chat chat = (TdApi.Chat) chatResult;
                        log.info("Group information: {} (type: {})", chat.title, chat.type.getClass().getSimpleName());

                        // Get members based on group type
                        if (chat.type instanceof TdApi.ChatTypeSupergroup) {
                            TdApi.ChatTypeSupergroup supergroup = (TdApi.ChatTypeSupergroup) chat.type;
                            return getSupergroupMembers(supergroup.supergroupId, groupId, excludeAdmins, onlyActiveUsers, excludeBots);
                        } else if (chat.type instanceof TdApi.ChatTypeBasicGroup) {
                            TdApi.ChatTypeBasicGroup basicGroup = (TdApi.ChatTypeBasicGroup) chat.type;
                            return getBasicGroupMembers(basicGroup.basicGroupId, groupId, excludeAdmins, onlyActiveUsers, excludeBots);
                        } else {
                            throw new RuntimeException("Unsupported group type: " + chat.type.getClass().getSimpleName());
                        }
                    })
                    .thenAccept(members -> {
                        // Cache results
                        telegramCacheManager.getGroupMembersCache().put(groupId, members);
                        log.info("Successfully retrieved {} members for group {}", members.size(), groupId);
                        future.complete(members);
                    })
                    .exceptionally(throwable -> {
                        log.error("Failed to get members for group {}", groupId, throwable);
                        future.complete(new ArrayList<>());
                        return null;
                    });

            return future;
        } catch (Exception e) {
            log.error("Error occurred while getting group members", e);
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
    }

    /**
     * Get supergroup members
     */
    private CompletableFuture<List<GroupMemberInfo>> getSupergroupMembers(long supergroupId, Long groupId,
                                                                          boolean excludeAdmins, boolean onlyActiveUsers, boolean excludeBots) {
        CompletableFuture<List<GroupMemberInfo>> future = new CompletableFuture<>();
        List<GroupMemberInfo> allMembers = new ArrayList<>();

        // Get member list
        getSupergroupMembersRecursive(supergroupId, groupId, null, allMembers, excludeAdmins, onlyActiveUsers, excludeBots, future);

        return future;
    }

    /**
     * Recursively get supergroup members
     */
    private void getSupergroupMembersRecursive(long supergroupId, Long groupId, String offset,
                                               List<GroupMemberInfo> allMembers, boolean excludeAdmins,
                                               boolean onlyActiveUsers, boolean excludeBots, CompletableFuture<List<GroupMemberInfo>> future) {
        try {
            TdApi.GetSupergroupMembers request = new TdApi.GetSupergroupMembers();
            request.supergroupId = supergroupId;
            request.filter = new TdApi.SupergroupMembersFilterRecent();
            request.offset = offset != null ? Integer.parseInt(offset) : 0;
            request.limit = 200; // Get 200 members at a time

            client.send(request)
                    .thenCompose(result -> {
                        if (!(result instanceof TdApi.ChatMembers)) {
                            return CompletableFuture.completedFuture(new ArrayList<GroupMemberInfo>());
                        }

                        TdApi.ChatMembers chatMembers = (TdApi.ChatMembers) result;
                        log.info("Retrieved {} group members", chatMembers.members.length);

                        // Process current batch of members
                        List<CompletableFuture<GroupMemberInfo>> memberFutures = new ArrayList<>();

                        for (TdApi.ChatMember member : chatMembers.members) {
                            CompletableFuture<GroupMemberInfo> memberFuture = processGroupMember(member, groupId, excludeAdmins, onlyActiveUsers, excludeBots);
                            memberFutures.add(memberFuture);
                        }

                        // Wait for all member information processing to complete
                        return CompletableFuture.allOf(memberFutures.toArray(new CompletableFuture[0]))
                                .thenApply(v -> memberFutures.stream()
                                        .map(CompletableFuture::join)
                                        .filter(memberInfo -> memberInfo != null)
                                        .collect(Collectors.toList()));
                    })
                    .thenAccept(batchMembers -> {
                        allMembers.addAll(batchMembers);
                        log.info("Current batch processing completed, total members: {}", allMembers.size());

                        // If the number of members in this batch equals the limit, there may be more members
                        if (batchMembers.size() == 200) {
                            // Continue getting next batch
                            getSupergroupMembersRecursive(supergroupId, groupId,
                                    String.valueOf(request.offset + 200),
                                    allMembers, excludeAdmins, onlyActiveUsers, excludeBots, future);
                        } else {
                            // All members retrieved
                            future.complete(allMembers);
                        }
                    })
                    .exceptionally(throwable -> {
                        log.error("Failed to get supergroup members", throwable);
                        future.complete(allMembers); // Return already retrieved members
                        return null;
                    });
        } catch (Exception e) {
            log.error("Error occurred while getting supergroup members", e);
            future.complete(allMembers);
        }
    }

    /**
     * Get basic group members
     */
    private CompletableFuture<List<GroupMemberInfo>> getBasicGroupMembers(long basicGroupId, Long groupId,
                                                                          boolean excludeAdmins, boolean onlyActiveUsers, boolean excludeBots) {
        try {
            TdApi.GetBasicGroupFullInfo request = new TdApi.GetBasicGroupFullInfo();
            request.basicGroupId = basicGroupId;

            return client.send(request)
                    .thenCompose(result -> {
                        if (!(result instanceof TdApi.BasicGroupFullInfo)) {
                            return CompletableFuture.completedFuture(new ArrayList<>());
                        }

                        TdApi.BasicGroupFullInfo groupInfo = (TdApi.BasicGroupFullInfo) result;
                        log.info("Basic group member count: {}", groupInfo.members.length);

                        List<CompletableFuture<GroupMemberInfo>> memberFutures = new ArrayList<>();

                        for (TdApi.ChatMember member : groupInfo.members) {
                            CompletableFuture<GroupMemberInfo> memberFuture = processGroupMember(member, groupId, excludeAdmins, onlyActiveUsers, excludeBots);
                            memberFutures.add(memberFuture);
                        }

                        return CompletableFuture.allOf(memberFutures.toArray(new CompletableFuture[0]))
                                .thenApply(v -> memberFutures.stream()
                                        .map(CompletableFuture::join)
                                        .filter(memberInfo -> memberInfo != null)
                                        .collect(Collectors.toList()));
                    });
        } catch (Exception e) {
            log.error("Error occurred while getting basic group members", e);
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
    }

    /**
     * Process group member information
     */
    private CompletableFuture<GroupMemberInfo> processGroupMember(TdApi.ChatMember member, Long groupId,
                                                                  boolean excludeAdmins, boolean onlyActiveUsers, boolean excludeBots) {
        try {
            // Get user ID
            final Long userId;
            if (member.memberId instanceof TdApi.MessageSenderUser) {
                userId = ((TdApi.MessageSenderUser) member.memberId).userId;
            } else {
                log.debug("Skip non-user member: {}", member.memberId.getClass().getSimpleName());
                return CompletableFuture.completedFuture(null);
            }

            // Get user detailed information
            TdApi.GetUser getUserRequest = new TdApi.GetUser();
            getUserRequest.userId = userId;

            return client.send(getUserRequest)
                    .thenCompose(userResult -> {
                        if (!(userResult instanceof TdApi.User)) {
                            return CompletableFuture.completedFuture(null);
                        }

                        TdApi.User user = (TdApi.User) userResult;

                        // Check if it's a bot (decide whether to exclude based on excludeBots parameter)
                        if (excludeBots && user.type instanceof TdApi.UserTypeBot) {
                            log.debug("Exclude bot: {} ({})", user.firstName, userId);
                            return CompletableFuture.completedFuture(null);
                        }

                        // Check if it's a deleted user (always exclude)
                        if (user.type instanceof TdApi.UserTypeDeleted) {
                            log.debug("Skip deleted user: {} ({})", user.firstName, userId);
                            return CompletableFuture.completedFuture(null);
                        }

                        // Get user status
                        return getUserStatus(userId).thenApply(status -> {
                            GroupMemberInfo memberInfo = convertToGroupMemberInfo(user, member, groupId, status);

                            // Apply filter conditions
                            if (excludeAdmins && memberInfo.isAdmin()) {
                                log.debug("Exclude admin: {}", memberInfo.getDisplayName());
                                return null;
                            }

                            if (onlyActiveUsers && !memberInfo.isActiveUser()) {
                                log.debug("Exclude inactive user: {} (status: {})", memberInfo.getDisplayName(), memberInfo.getOnlineStatus());
                                return null;
                            }

                            if (!memberInfo.isValidUser()) {
                                log.debug("Exclude invalid user: {} (deleted: {}, bot: {}, status: {})",
                                        memberInfo.getDisplayName(), memberInfo.getIsDeleted(),
                                        memberInfo.getIsBot(), memberInfo.getOnlineStatus());
                                return null;
                            }

                            log.debug("Valid member: {} (status: {}, role: {})",
                                    memberInfo.getDisplayName(), memberInfo.getOnlineStatus(), memberInfo.getMemberStatus());
                            return memberInfo;
                        });
                    });
        } catch (Exception e) {
            log.error("Error occurred while processing group member", e);
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Get user status
     */
    private CompletableFuture<TdApi.UserStatus> getUserStatus(Long userId) {
        try {
            TdApi.GetUser request = new TdApi.GetUser();
            request.userId = userId;

            return client.send(request)
                    .thenApply(result -> {
                        if (result instanceof TdApi.User) {
                            return ((TdApi.User) result).status;
                        }
                        return new TdApi.UserStatusOffline();
                    })
                    .exceptionally(throwable -> {
                        log.debug("Failed to get user status: {}", userId);
                        return new TdApi.UserStatusOffline();
                    });
        } catch (Exception e) {
            log.debug("Error occurred while getting user status: {}", userId);
            return CompletableFuture.completedFuture(new TdApi.UserStatusOffline());
        }
    }

    /**
     * Convert to GroupMemberInfo
     */
    private GroupMemberInfo convertToGroupMemberInfo(TdApi.User user, TdApi.ChatMember member,
                                                     Long groupId, TdApi.UserStatus userStatus) {
        // Parse member status
        String memberStatus = "MEMBER";
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
        LocalDateTime joinedTime = null;

        if (member.status instanceof TdApi.ChatMemberStatusCreator) {
            memberStatus = "CREATOR";
            TdApi.ChatMemberStatusCreator creator = (TdApi.ChatMemberStatusCreator) member.status;
            canSendMessages = true;
            canSendMedia = true;
            canInviteUsers = true;
            canChangeInfo = true;
            canPinMessages = true;
            canDeleteMessages = true;
            canBanUsers = true;
            canRestrictMembers = true;
            canPromoteMembers = true;
            customTitle = creator.customTitle;
        } else if (member.status instanceof TdApi.ChatMemberStatusAdministrator) {
            memberStatus = "ADMINISTRATOR";
            TdApi.ChatMemberStatusAdministrator admin = (TdApi.ChatMemberStatusAdministrator) member.status;
            canSendMessages = true;
            canSendMedia = true;
            canInviteUsers = admin.rights.canInviteUsers;
            canChangeInfo = admin.rights.canChangeInfo;
            canPinMessages = admin.rights.canPinMessages;
            canDeleteMessages = admin.rights.canDeleteMessages;
            canBanUsers = admin.rights.canRestrictMembers;
            canRestrictMembers = admin.rights.canRestrictMembers;
            canPromoteMembers = admin.rights.canPromoteMembers;
            customTitle = admin.customTitle;
        } else if (member.status instanceof TdApi.ChatMemberStatusMember) {
            memberStatus = "MEMBER";
        } else if (member.status instanceof TdApi.ChatMemberStatusRestricted) {
            memberStatus = "RESTRICTED";
            TdApi.ChatMemberStatusRestricted restricted = (TdApi.ChatMemberStatusRestricted) member.status;
            canSendMessages = restricted.permissions.canSendBasicMessages;
            canSendMedia = restricted.permissions.canSendPhotos || restricted.permissions.canSendVideos;
        } else if (member.status instanceof TdApi.ChatMemberStatusLeft) {
            memberStatus = "LEFT";
        } else if (member.status instanceof TdApi.ChatMemberStatusBanned) {
            memberStatus = "BANNED";
        }

        // Parse online status
        String onlineStatus = "OFFLINE";
        LocalDateTime lastOnlineTime = null;

        if (userStatus instanceof TdApi.UserStatusOnline) {
            onlineStatus = "ONLINE";
        } else if (userStatus instanceof TdApi.UserStatusRecently) {
            onlineStatus = "RECENTLY";
        } else if (userStatus instanceof TdApi.UserStatusLastWeek) {
            onlineStatus = "LAST_WEEK";
        } else if (userStatus instanceof TdApi.UserStatusLastMonth) {
            onlineStatus = "LAST_MONTH";
        } else if (userStatus instanceof TdApi.UserStatusOffline) {
            TdApi.UserStatusOffline offline = (TdApi.UserStatusOffline) userStatus;
            if (offline.wasOnline > 0) {
                lastOnlineTime = LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(offline.wasOnline), ZoneId.systemDefault());

                // Determine offline duration
                LocalDateTime now = LocalDateTime.now();
                long daysSinceOnline = java.time.Duration.between(lastOnlineTime, now).toDays();

                if (daysSinceOnline <= 7) {
                    onlineStatus = "RECENTLY";
                } else if (daysSinceOnline <= 30) {
                    onlineStatus = "LAST_MONTH";
                } else {
                    onlineStatus = "LONG_TIME_AGO";
                }
            } else {
                onlineStatus = "LONG_TIME_AGO";
            }
        }

        return GroupMemberInfo.builder()
                .userId(user.id)
                .groupId(groupId)
                .firstName(user.firstName)
                .lastName(user.lastName)
                .username(user.usernames != null && user.usernames.activeUsernames.length > 0 ?
                        user.usernames.activeUsernames[0] : null)
                .phoneNumber(user.phoneNumber)
                .isBot(user.type instanceof TdApi.UserTypeBot)
                .isVerified(user.isVerified)
                .isPremium(user.isPremium)
                .isDeleted(user.type instanceof TdApi.UserTypeDeleted)
                .memberStatus(memberStatus)
                .onlineStatus(onlineStatus)
                .lastOnlineTime(lastOnlineTime)
                .joinedGroupTime(joinedTime)
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
     * Invite users to group
     */
    public CompletableFuture<InviteResult> inviteUsersToGroup(Long groupId, List<Long> userIds) {
        try {
            String inviteId = UUID.randomUUID().toString();
            log.info("Starting to invite {} users to group {}, invite ID: {}", userIds.size(), groupId, inviteId);

            InviteResult result = InviteResult.builder()
                    .inviteId(inviteId)
                    .groupId(groupId)
                    .userIds(new ArrayList<>(userIds))
                    .status("PENDING")
                    .totalCount(userIds.size())
                    .successCount(0)
                    .failedCount(0)
                    .successUserIds(new ArrayList<>())
                    .failedUserIds(new ArrayList<>())
                    .failureDetails(new ArrayList<>())
                    .startTime(LocalDateTime.now())
                    .build();

            // Cache invite result
            telegramCacheManager.getInviteResultCache().put(inviteId, result);

            CompletableFuture<InviteResult> future = new CompletableFuture<>();

            // First get group information
            getGroupInfo(groupId)
                    .thenCompose(groupInfo -> {
                        if (groupInfo == null) {
                            throw new RuntimeException("Unable to get group information");
                        }

                        result.setGroupTitle(groupInfo.getTitle());
                        log.info("Target group: {} ({})", groupInfo.getTitle(), groupId);

                        // Batch invite users
                        return inviteUsersBatch(groupId, userIds, result);
                    })
                    .thenAccept(finalResult -> {
                        finalResult.setCompletedTime(LocalDateTime.now());

                        // Update final status
                        if (finalResult.getSuccessCount() == finalResult.getTotalCount()) {
                            finalResult.setStatus("SUCCESS");
                        } else if (finalResult.getSuccessCount() > 0) {
                            finalResult.setStatus("PARTIAL_SUCCESS");
                        } else {
                            finalResult.setStatus("FAILED");
                        }

                        log.info("Invitation completed - total: {}, success: {}, failed: {}, success rate: {:.1f}%",
                                finalResult.getTotalCount(), finalResult.getSuccessCount(),
                                finalResult.getFailedCount(), finalResult.getSuccessRate());

                        future.complete(finalResult);
                    })
                    .exceptionally(throwable -> {
                        log.error("Failed to invite users to group", throwable);
                        result.setStatus("FAILED");
                        result.setErrorMessage(throwable.getMessage());
                        result.setCompletedTime(LocalDateTime.now());
                        future.complete(result);
                        return null;
                    });

            return future;
        } catch (Exception e) {
            log.error("Error occurred while inviting users to group", e);
            return CompletableFuture.completedFuture(
                    InviteResult.builder()
                            .status("FAILED")
                            .errorMessage(e.getMessage())
                            .build()
            );
        }
    }

    /**
     * Batch invite users
     */
    private CompletableFuture<InviteResult> inviteUsersBatch(Long groupId, List<Long> userIds, InviteResult result) {
        CompletableFuture<InviteResult> future = new CompletableFuture<>();

        // Create invite task list
        List<CompletableFuture<Boolean>> inviteFutures = new ArrayList<>();

        for (Long userId : userIds) {
            CompletableFuture<Boolean> inviteFuture = inviteSingleUser(groupId, userId, result);
            inviteFutures.add(inviteFuture);
        }

        // Wait for all invites to complete
        CompletableFuture.allOf(inviteFutures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    log.info("All invite tasks completed");
                    future.complete(result);
                })
                .exceptionally(throwable -> {
                    log.error("Error occurred during batch invite", throwable);
                    future.complete(result);
                    return null;
                });

        return future;
    }

    /**
     * Invite single user
     */
    private CompletableFuture<Boolean> inviteSingleUser(Long groupId, Long userId, InviteResult result) {
        try {
            TdApi.AddChatMember request = new TdApi.AddChatMember();
            request.chatId = groupId;
            request.userId = userId;
            request.forwardLimit = 0; // Don't forward message history

            return client.send(request)
                    .thenCompose(addResult -> {
                        // Get user info for logging
                        return getUserInfo(userId).thenApply(userInfo -> {
                            String displayName = userInfo != null ? userInfo.getDisplayName() : "User " + userId;

                            if (addResult instanceof TdApi.Ok) {
                                // Invite successful
                                synchronized (result) {
                                    result.getSuccessUserIds().add(userId);
                                    result.setSuccessCount(result.getSuccessCount() + 1);
                                }
                                log.info("Successfully invited user: {} ({})", displayName, userId);
                                return true;
                            } else {
                                // Invite failed
                                String errorMsg = "Invite failed: " + addResult.getClass().getSimpleName();
                                handleInviteFailure(userId, displayName, "INVITE_FAILED", errorMsg, result);
                                return false;
                            }
                        });
                    })
                    .exceptionally(throwable -> {
                        // Get user info for error logging
                        getUserInfo(userId).thenAccept(userInfo -> {
                            String displayName = userInfo != null ? userInfo.getDisplayName() : "User " + userId;
                            String errorMsg = throwable.getMessage();
                            String errorCode = "EXCEPTION";

                            // Parse specific error type
                            if (errorMsg != null) {
                                if (errorMsg.contains("USER_ALREADY_PARTICIPANT")) {
                                    errorCode = "ALREADY_MEMBER";
                                    errorMsg = "User is already a group member";
                                } else if (errorMsg.contains("USER_PRIVACY_RESTRICTED")) {
                                    errorCode = "PRIVACY_RESTRICTED";
                                    errorMsg = "User privacy settings restricted";
                                } else if (errorMsg.contains("USER_NOT_FOUND")) {
                                    errorCode = "USER_NOT_FOUND";
                                    errorMsg = "User does not exist";
                                } else if (errorMsg.contains("CHAT_ADMIN_REQUIRED")) {
                                    errorCode = "ADMIN_REQUIRED";
                                    errorMsg = "Admin permission required";
                                } else if (errorMsg.contains("TOO_MANY_REQUESTS")) {
                                    errorCode = "RATE_LIMITED";
                                    errorMsg = "Too many requests";
                                }
                            }

                            handleInviteFailure(userId, displayName, errorCode, errorMsg, result);
                        });

                        return false;
                    });
        } catch (Exception e) {
            log.error("Exception occurred while inviting user {}", userId, e);
            handleInviteFailure(userId, "User " + userId, "EXCEPTION", e.getMessage(), result);
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Handle invite failure
     */
    private void handleInviteFailure(Long userId, String displayName, String errorCode, String errorMsg, InviteResult result) {
        synchronized (result) {
            result.getFailedUserIds().add(userId);
            result.setFailedCount(result.getFailedCount() + 1);

            InviteResult.InviteFailureDetail failureDetail = InviteResult.InviteFailureDetail.builder()
                    .userId(userId)
                    .userDisplayName(displayName)
                    .errorCode(errorCode)
                    .errorMessage(errorMsg)
                    .failureTime(LocalDateTime.now())
                    .build();

            result.getFailureDetails().add(failureDetail);
        }

        log.warn("Failed to invite user: {} ({}) - {} ({})", displayName, userId, errorMsg, errorCode);
    }

    /**
     * Get user information (for display)
     */
    private CompletableFuture<GroupMemberInfo> getUserInfo(Long userId) {
        try {
            TdApi.GetUser request = new TdApi.GetUser();
            request.userId = userId;

            return client.send(request)
                    .thenApply(result -> {
                        if (result instanceof TdApi.User) {
                            TdApi.User user = (TdApi.User) result;
                            return GroupMemberInfo.builder()
                                    .userId(user.id)
                                    .firstName(user.firstName)
                                    .lastName(user.lastName)
                                    .username(user.usernames != null && user.usernames.activeUsernames.length > 0 ?
                                            user.usernames.activeUsernames[0] : null)
                                    .build();
                        }
                        return null;
                    })
                    .exceptionally(throwable -> (GroupMemberInfo) null);
        } catch (Exception e) {
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Get invite result
     */
    public InviteResult getInviteResult(String inviteId) {
        return telegramCacheManager.getInviteResultCache().get(inviteId);
    }

    /**
     * Get group members (from cache)
     */
    public List<GroupMemberInfo> getCachedGroupMembers(Long groupId) {
        return telegramCacheManager.getGroupMembersCache().getOrDefault(groupId, new ArrayList<>());
    }

    /**
     * Send message to user
     */
    public CompletableFuture<SendMessageResult> sendMessageToUser(Long userId, SendMessageRequest request) {
        log.info("Sending message to user: {}, content: {}, type: {}", userId, request.getContent(), request.getMessageType());
        if (request.getMessageType() != null) {
            if (request.getMessageType().equals("VIDEO")) {
                return sendVideoMessage(userId, request);
            } else if (request.getMessageType().equals("FILE")) {
                return sendFileMessage(userId, request);
            } else if (request.getMessageType().equals("PHOTO")) {
                return sendPhotoMessage(userId, request);
            } else if (request.getMessageType().equals("AUDIO")) {
                return sendAudioMessage(userId, request);
            }
        }
        return sendTextMessage(userId, request);
    }

    /**
     * Send message to group
     */
    public CompletableFuture<SendMessageResult> sendMessageToGroup(Long groupId, SendMessageRequest request) {
        log.info("Sending message to group: {}, content: {}, type: {}", groupId, request.getContent(), request.getMessageType());
        if (request.getMessageType() != null) {
            if (request.getMessageType().equals("VIDEO")) {
                return sendVideoMessage(groupId, request);
            } else if (request.getMessageType().equals("FILE")) {
                return sendFileMessage(groupId, request);
            } else if (request.getMessageType().equals("PHOTO")) {
                return sendPhotoMessage(groupId, request);
            } else if (request.getMessageType().equals("AUDIO")) {
                return sendAudioMessage(groupId, request);
            }
        }
        return sendTextMessage(groupId, request);
    }

    /**
     * Send voice message to user
     */
    public CompletableFuture<SendMessageResult> sendVoiceToUser(Long userId, SendVoiceRequest request) {
        log.info("Sending voice message to user: {}, file: {}", userId, request.getVoiceFilePath());
        return sendVoiceMessage(userId, request);
    }

    /**
     * Send voice message to group
     */
    public CompletableFuture<SendMessageResult> sendVoiceToGroup(Long groupId, SendVoiceRequest request) {
        log.info("Sending voice message to group: {}, file: {}", groupId, request.getVoiceFilePath());
        return sendVoiceMessage(groupId, request);
    }

    /**
     * Common method for sending voice messages
     */
    private CompletableFuture<SendMessageResult> sendVoiceMessage(Long chatId, SendVoiceRequest request) {
        try {
            // Validate file path
            if (request.getVoiceFilePath() == null || request.getVoiceFilePath().trim().isEmpty()) {
                throw new IllegalArgumentException("Voice file path cannot be empty");
            }

            // Validate file extension
            String filePath = request.getVoiceFilePath().toLowerCase();
            String[] supportedExtensions = {".ogg", ".oga", ".opus", ".mp3", ".m4a", ".aac", ".wav", ".flac"};
            boolean validExtension = false;
            for (String ext : supportedExtensions) {
                if (filePath.endsWith(ext)) {
                    validExtension = true;
                    break;
                }
            }

            if (!validExtension) {
                throw new IllegalArgumentException(
                        "Unsupported voice file format. Supported formats: OGG, OPUS, MP3, M4A, AAC, WAV, FLAC"
                );
            }

            // Validate file exists and convert to absolute path
            java.io.File file = new java.io.File(request.getVoiceFilePath());
            if (!file.exists()) {
                throw new IllegalArgumentException("Voice file does not exist: " + request.getVoiceFilePath());
            }

            if (!file.canRead()) {
                throw new IllegalArgumentException("Cannot read voice file: " + request.getVoiceFilePath());
            }

            // Get absolute path (TDLight requires absolute path)
            String absolutePath = file.getAbsolutePath();
            log.info("Using absolute path: {}", absolutePath);

            // Validate file size (Telegram limit: 20MB for voice notes)
            long fileSizeInMB = file.length() / (1024 * 1024);
            if (fileSizeInMB > 20) {
                throw new IllegalArgumentException(
                        "Voice file too large: " + fileSizeInMB + "MB. Maximum supported: 20MB"
                );
            }

            log.info("Voice file validation passed: file={}, size={}MB, format={}",
                    request.getVoiceFilePath(),
                    String.format("%.2f", file.length() / (1024.0 * 1024.0)),
                    filePath.substring(filePath.lastIndexOf('.'))
            );

            // Create input file (using absolute path)
            TdApi.InputFileLocal inputFile = new TdApi.InputFileLocal();
            inputFile.path = absolutePath;

            // Create voice message content
            TdApi.InputMessageVoiceNote inputMessageVoice = new TdApi.InputMessageVoiceNote();
            inputMessageVoice.voiceNote = inputFile;
            inputMessageVoice.duration = request.getDuration() != null ? AutoUtil.getDurationSeconds(absolutePath) : 0;
            inputMessageVoice.waveform = request.getWaveform() != null ? AutoUtil.getWaveform(absolutePath) : new byte[0];

            // If there's a caption, add it
            if (request.getCaption() != null && !request.getCaption().trim().isEmpty()) {
                TdApi.FormattedText caption = new TdApi.FormattedText();
                caption.text = request.getCaption();
                caption.entities = new TdApi.TextEntity[0];
                inputMessageVoice.caption = caption;
            }

            // Create message send options
            TdApi.MessageSendOptions sendOptions = new TdApi.MessageSendOptions();
            sendOptions.disableNotification = request.getDisableNotification() != null ?
                    request.getDisableNotification() : false;
            sendOptions.fromBackground = false;
            sendOptions.schedulingState = null;

            // Create send message request
            TdApi.SendMessage sendMessageRequest = new TdApi.SendMessage();
            sendMessageRequest.chatId = chatId;
            sendMessageRequest.messageThreadId = 0;
            sendMessageRequest.options = sendOptions;
            sendMessageRequest.replyMarkup = null;
            sendMessageRequest.inputMessageContent = inputMessageVoice;

            // If there's a reply message ID, set reply
            if (request.getReplyToMessageId() != null) {
                sendMessageRequest.replyTo = new TdApi.InputMessageReplyToMessage();
                ((TdApi.InputMessageReplyToMessage) sendMessageRequest.replyTo).messageId = request.getReplyToMessageId();
            }

            // Send message
            return client.send(sendMessageRequest)
                    .thenCompose(result -> {
                        if (result instanceof TdApi.Message) {
                            TdApi.Message sentMessage = (TdApi.Message) result;

                            // Get chat info for logging
                            return telegramUtil.getChatInfo(chatId).thenApply(chat -> {
                                String chatInfo = telegramFormatHelper.formatChatInfo(chat);
                                String senderInfo = clientProvider.getCurrentUser() != null ? telegramFormatHelper.formatSenderInfo(clientProvider.getCurrentUser()) : "Current user";

                                log.info("🎤 Voice message sent from {} to chat: {} (ID: {})", senderInfo, chatInfo, chatId);
                                log.info("  File: {}, duration: {}s", request.getVoiceFilePath(), request.getDuration());

                                return SendMessageResult.builder()
                                        .success(true)
                                        .messageId(sentMessage.id)
                                        .chatId(chatId)
                                        .content("Voice message: " + request.getVoiceFilePath())
                                        .sentAt(LocalDateTime.now())
                                        .build();
                            }).exceptionally(throwable -> {
                                // If getting chat info fails, use basic logging
                                String senderInfo = clientProvider.getCurrentUser() != null ? telegramFormatHelper.formatSenderInfo(clientProvider.getCurrentUser()) : "Current user";
                                log.info("🎤 Voice message sent from {} to chat ID: {}", senderInfo, chatId);
                                log.info("  File: {}, duration: {}s", request.getVoiceFilePath(), request.getDuration());

                                return SendMessageResult.builder()
                                        .success(true)
                                        .messageId(sentMessage.id)
                                        .chatId(chatId)
                                        .content("Voice message: " + request.getVoiceFilePath())
                                        .sentAt(LocalDateTime.now())
                                        .build();
                            });
                        } else {
                            log.error("❌ Failed to send voice message: unknown response type {} to chat ID: {}", result.getClass().getSimpleName(), chatId);
                            return CompletableFuture.completedFuture(SendMessageResult.builder()
                                    .success(false)
                                    .chatId(chatId)
                                    .content("Voice message: " + request.getVoiceFilePath())
                                    .sentAt(LocalDateTime.now())
                                    .errorMessage("Unknown response type: " + result.getClass().getSimpleName())
                                    .errorCode("UNKNOWN_RESPONSE")
                                    .build());
                        }
                    })
                    .exceptionally(throwable -> {
                        log.error("❌ Failed to send voice message: chat ID={}, error: {}", chatId, throwable.getMessage(), throwable);
                        return SendMessageResult.builder()
                                .success(false)
                                .chatId(chatId)
                                .content("Voice message: " + request.getVoiceFilePath())
                                .sentAt(LocalDateTime.now())
                                .errorMessage(throwable.getMessage())
                                .errorCode("SEND_FAILED")
                                .build();
                    });
        } catch (Exception e) {
            log.error("❌ Failed to create send voice message request: chat ID={}", chatId, e);
            return CompletableFuture.completedFuture(
                    SendMessageResult.builder()
                            .success(false)
                            .chatId(chatId)
                            .content("Voice message: " + request.getVoiceFilePath())
                            .sentAt(LocalDateTime.now())
                            .errorMessage(e.getMessage())
                            .errorCode("REQUEST_CREATION_FAILED")
                            .build()
            );
        }
    }


    /**
     * Common method for sending text messages
     */
    private CompletableFuture<SendMessageResult> sendTextMessage(Long chatId, SendMessageRequest request) {
        try {
            // Create formatted text
            TdApi.FormattedText formattedText = new TdApi.FormattedText();
            formattedText.text = request.getContent();
            formattedText.entities = new TdApi.TextEntity[0]; // Simplified handling, no format parsing

            // Create text message content
            TdApi.InputMessageText inputMessageText = new TdApi.InputMessageText();
            inputMessageText.text = formattedText;
            inputMessageText.clearDraft = true;

            // Create message send options
            TdApi.MessageSendOptions sendOptions = new TdApi.MessageSendOptions();
            sendOptions.disableNotification = request.getDisableNotification() != null ?
                    request.getDisableNotification() : false;
            sendOptions.fromBackground = false;
            sendOptions.schedulingState = null;

            // Create send message request
            TdApi.SendMessage sendMessageRequest = new TdApi.SendMessage();
            sendMessageRequest.chatId = chatId;
            sendMessageRequest.messageThreadId = 0; // Do not use message thread
            sendMessageRequest.options = sendOptions;
            sendMessageRequest.replyMarkup = null; // Do not use reply markup
            sendMessageRequest.inputMessageContent = inputMessageText;

            // Send message
            return client.send(sendMessageRequest)
                    .thenCompose(result -> {
                        if (result instanceof TdApi.Message) {
                            TdApi.Message sentMessage = (TdApi.Message) result;

                            // Get chat info for logging
                            return telegramUtil.getChatInfo(chatId).thenApply(chat -> {
                                String chatInfo = telegramFormatHelper.formatChatInfo(chat);
                                String formattedContent = telegramFormatHelper.formatMessageContent(request.getContent());
                                String senderInfo = clientProvider.getCurrentUser() != null ? telegramFormatHelper.formatSenderInfo(clientProvider.getCurrentUser()) : "Current User";

                                log.info("📤 Message sent from {} to chat: {} (ID: {})", senderInfo, chatInfo, chatId);
                                log.info("  Content: {}", formattedContent);

                                return SendMessageResult.builder()
                                        .success(true)
                                        .messageId(sentMessage.id)
                                        .chatId(chatId)
                                        .content(request.getContent())
                                        .sentAt(LocalDateTime.now())
                                        .build();
                            }).exceptionally(throwable -> {
                                // If getting chat info fails, use basic logs
                                String formattedContent = telegramFormatHelper.formatMessageContent(request.getContent());
                                String senderInfo = clientProvider.getCurrentUser() != null ? telegramFormatHelper.formatSenderInfo(clientProvider.getCurrentUser()) : "Current User";
                                log.info("📤 Message sent from {} to chat ID: {}", senderInfo, chatId);
                                log.info("  Content: {}", formattedContent);

                                return SendMessageResult.builder()
                                        .success(true)
                                        .messageId(sentMessage.id)
                                        .chatId(chatId)
                                        .content(request.getContent())
                                        .sentAt(LocalDateTime.now())
                                        .build();
                            });
                        } else {
                            log.error("❌ Message send failed: unknown response type {} to chat ID: {}", result.getClass().getSimpleName(), chatId);
                            return CompletableFuture.completedFuture(SendMessageResult.builder()
                                    .success(false)
                                    .chatId(chatId)
                                    .content(request.getContent())
                                    .sentAt(LocalDateTime.now())
                                    .errorMessage("Unknown response type: " + result.getClass().getSimpleName())
                                    .errorCode("UNKNOWN_RESPONSE")
                                    .build());
                        }
                    })
                    .exceptionally(throwable -> {
                        log.error("❌ Message send failed: chat ID={}, error: {}", chatId, throwable.getMessage(), throwable);
                        return SendMessageResult.builder()
                                .success(false)
                                .chatId(chatId)
                                .content(request.getContent())
                                .sentAt(LocalDateTime.now())
                                .errorMessage(throwable.getMessage())
                                .errorCode("SEND_FAILED")
                                .build();
                    });
        } catch (Exception e) {
                    log.error("❌ Failed to create send message request: chat ID={}", chatId, e);
            return CompletableFuture.completedFuture(
                    SendMessageResult.builder()
                            .success(false)
                            .chatId(chatId)
                            .content(request.getContent())
                            .sentAt(LocalDateTime.now())
                            .errorMessage(e.getMessage())
                            .errorCode("REQUEST_CREATION_FAILED")
                            .build()
            );
        }
    }

    private CompletableFuture<SendMessageResult> sendVideoMessage(
            Long chatId,
            SendMessageRequest sendMessageRequest) {

        try {
//            java.io.File file=new java.io.File(sendMessageRequest.getFilePath());
//
//            String absolutePath=file.getAbsolutePath();

            TdApi.InputFileLocal inputFile = new TdApi.InputFileLocal();
            inputFile.path = sendMessageRequest.getFilePath();

            // ✅ VIDEO (not document)
            TdApi.InputMessageVideo content = new TdApi.InputMessageVideo();
            content.video = inputFile;
            content.caption = new TdApi.FormattedText(sendMessageRequest.getFileName(), null);
            content.supportsStreaming = true;

            TdApi.SendMessage request = new TdApi.SendMessage();
            request.chatId = chatId;
            request.inputMessageContent = content;

            return client.send(request)
                    .thenApply(result -> {

                        if (result instanceof TdApi.Message message) {
                            log.info("Video sent: {}", message.id);

                            return SendMessageResult.builder()
                                    .success(true)
                                    .messageId(message.id)
                                    .chatId(chatId)
                                    .content(sendMessageRequest.getFileName())
                                    .sentAt(LocalDateTime.now())
                                    .build();
                        }

                        return SendMessageResult.builder()
                                .success(false)
                                .chatId(chatId)
                                .errorMessage("Unexpected response type")
                                .sentAt(LocalDateTime.now())
                                .build();
                    });

        } catch (Exception e) {
            log.error("fail to send video: {}", e.getMessage());

            return CompletableFuture.completedFuture(
                    SendMessageResult.builder()
                            .success(false)
                            .chatId(chatId)
                            .content(sendMessageRequest.getFilePath())
                            .sentAt(LocalDateTime.now())
                            .errorMessage(e.getMessage())
                            .errorCode("REQUEST_CREATION_FAILED")
                            .build()
            );
        }
    }

    private CompletableFuture<SendMessageResult> sendFileMessage(Long chatId, SendMessageRequest messageRequest) {
        try {
            log.info("📄 Sending FILE message - ChatId: {}, FilePath: {}, FileName: {}",
                    chatId, messageRequest.getFilePath(), messageRequest.getFileName());

            TdApi.InputFileLocal inputFile = new TdApi.InputFileLocal();
            inputFile.path = messageRequest.getFilePath();

            TdApi.InputMessageDocument document = new TdApi.InputMessageDocument();
            document.document = inputFile;
            document.caption = new TdApi.FormattedText(messageRequest.getContent() != null ? messageRequest.getContent() : "", null);

            TdApi.SendMessage request = new TdApi.SendMessage();
            request.chatId = chatId;
            request.inputMessageContent = document;

            log.info("📤 Sending InputMessageDocument to TDLib...");

            return client.send(request)
                    .thenApply(result -> {
                        if (result instanceof TdApi.Message message) {
                            log.info("✅ FILE sent successfully - MessageId: {}, ContentType: {}",
                                    message.id, message.content.getClass().getSimpleName());

                            return SendMessageResult.builder()
                                    .success(true)
                                    .messageId(message.id)
                                    .chatId(chatId)
                                    .content(messageRequest.getFileName())
                                    .sentAt(LocalDateTime.now())
                                    .build();
                        }

                        log.error("❌ Unexpected response type: {}", result.getClass().getSimpleName());
                        return SendMessageResult.builder()
                                .success(false)
                                .chatId(chatId)
                                .errorMessage("Unexpected response type")
                                .sentAt(LocalDateTime.now())
                                .build();
                    });


        } catch (Exception e) {
            log.error("❌ Failed to send file message: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(
                    SendMessageResult.builder()
                            .success(false)
                            .chatId(chatId)
                            .content(messageRequest.getContent())
                            .sentAt(LocalDateTime.now())
                            .errorMessage(e.getMessage())
                            .errorCode("REQUEST_CREATION_FAILED")
                            .build()
            );

        }
    }

    private CompletableFuture<SendMessageResult> sendPhotoMessage(Long chatId, SendMessageRequest messageRequest) {
        try {
            TdApi.InputFileLocal inputFile = new TdApi.InputFileLocal();
            inputFile.path = messageRequest.getFilePath();


            TdApi.InputMessagePhoto photo = new TdApi.InputMessagePhoto();
            photo.photo = inputFile;
            photo.caption = new TdApi.FormattedText(messageRequest.getFileName(), null);

            TdApi.SendMessage request = new TdApi.SendMessage();
            request.chatId = chatId;
            request.inputMessageContent = photo;

            return client.send(request)
                    .thenApply(result -> {
                        if (result instanceof TdApi.Message message) {
                            log.info("Photo sent: {}", message.id);
                            return SendMessageResult.builder()
                                    .success(true)
                                    .messageId(message.id)
                                    .chatId(chatId)
                                    .content(messageRequest.getFileName())
                                    .sentAt(LocalDateTime.now())
                                    .build();
                        }

                        return SendMessageResult.builder()
                                .success(false)
                                .chatId(chatId)
                                .errorMessage("Unexpected response type")
                                .sentAt(LocalDateTime.now())
                                .build();
                    });

        } catch (Exception e) {
            log.error("Failed to send photo: {}", e.getMessage());
            return CompletableFuture.completedFuture(
                    SendMessageResult.builder()
                            .success(false)
                            .chatId(chatId)
                            .content(messageRequest.getContent())
                            .sentAt(LocalDateTime.now())
                            .errorMessage(e.getMessage())
                            .errorCode("REQUEST_CREATION_FAILED")
                            .build()
            );
        }
    }

    private CompletableFuture<SendMessageResult> sendAudioMessage(Long chatId, SendMessageRequest messageRequest) {
        try {
            TdApi.InputFileLocal inputFile = new TdApi.InputFileLocal();
            inputFile.path = messageRequest.getFilePath();

            TdApi.InputMessageAudio audio = new TdApi.InputMessageAudio();
            audio.audio = inputFile;
            audio.caption = new TdApi.FormattedText(messageRequest.getFileName(), null);

            TdApi.SendMessage request = new TdApi.SendMessage();
            request.chatId = chatId;
            request.inputMessageContent = audio;

            return client.send(request)
                    .thenApply(result -> {
                        if (result instanceof TdApi.Message message) {
                            log.info("Audio sent: {}", message.id);
                            return SendMessageResult.builder()
                                    .success(true)
                                    .messageId(message.id)
                                    .chatId(chatId)
                                    .content(messageRequest.getFileName())
                                    .sentAt(LocalDateTime.now())
                                    .build();
                        }

                        return SendMessageResult.builder()
                                .success(false)
                                .chatId(chatId)
                                .errorMessage("Unexpected response type")
                                .sentAt(LocalDateTime.now())
                                .build();
                    });

        } catch (Exception e) {
            log.error("Failed to send audio: {}", e.getMessage());
            return CompletableFuture.completedFuture(
                    SendMessageResult.builder()
                            .success(false)
                            .chatId(chatId)
                            .content(messageRequest.getContent())
                            .sentAt(LocalDateTime.now())
                            .errorMessage(e.getMessage())
                            .errorCode("REQUEST_CREATION_FAILED")
                            .build()
            );
        }
    }

    /**
     * Parse Telegram message link
     */
    public TelegramLinkInfo parseTelegramLink(String messageLink) {
        try {
            if (messageLink == null || messageLink.trim().isEmpty()) {
                return TelegramLinkInfo.builder()
                        .originalLink(messageLink)
                        .isValid(false)
                        .errorMessage("Message link cannot be empty")
                        .build();
            }

            // Remove potential spaces and line breaks
            messageLink = messageLink.trim();

            // Check whether it is a valid Telegram link
            if (!messageLink.startsWith("https://t.me/")) {
                return TelegramLinkInfo.builder()
                        .originalLink(messageLink)
                        .isValid(false)
                        .errorMessage("Invalid Telegram link format")
                        .build();
            }

            // Parse the link
            String path = messageLink.substring("https://t.me/".length());
            String[] parts = path.split("/");

            if (parts.length < 2) {
                return TelegramLinkInfo.builder()
                        .originalLink(messageLink)
                        .isValid(false)
                        .errorMessage("Incomplete link format")
                        .build();
            }

            TelegramLinkInfo.TelegramLinkInfoBuilder builder = TelegramLinkInfo.builder()
                    .originalLink(messageLink)
                    .isValid(true);

            if (parts[0].equals("c")) {
                // Private group/channel format: https://t.me/c/2415289392/5664
                if (parts.length < 3) {
                    return builder.isValid(false).errorMessage("Incomplete private group link format").build();
                }

                try {
                    Long chatIdNumber = Long.parseLong(parts[1]);
                    Long messageId = Long.parseLong(parts[2]);

                    // Private group chatId needs to be converted to negative format: -100 + original ID
                    Long chatId = -1000000000000L - chatIdNumber;

                    return builder
                            .linkType("PRIVATE_CHAT")
                            .chatId(chatId)
                            .messageId(messageId)
                            .build();
                } catch (NumberFormatException e) {
                    return builder.isValid(false).errorMessage("Invalid group ID or message ID").build();
                }
            } else {
                // Public channel format: https://t.me/jzshipin/16651
                try {
                    String username = parts[0];
                    Long messageId = Long.parseLong(parts[1]);

                    return builder
                            .linkType("PUBLIC_CHANNEL")
                            .username(username)
                            .messageId(messageId)
                            .chatId(null) // Needs to be resolved from username
                            .build();
                } catch (NumberFormatException e) {
                    return builder.isValid(false).errorMessage("Invalid message ID").build();
                }
            }
        } catch (Exception e) {
            log.error("Error parsing Telegram link: {}", messageLink, e);
            return TelegramLinkInfo.builder()
                    .originalLink(messageLink)
                    .isValid(false)
                    .errorMessage("Error occurred while parsing link: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Resolve public channel chatId by username
     */
    private CompletableFuture<Long> resolveChatIdByUsername(String username) {
        try {
            TdApi.SearchPublicChat request = new TdApi.SearchPublicChat();
            request.username = username;

            return client.send(request)
                    .thenApply(result -> {
                        if (result instanceof TdApi.Chat) {
                            TdApi.Chat chat = (TdApi.Chat) result;
                            log.info("Resolved username {} to chatId: {}", username, chat.id);
                            return chat.id;
                        } else {
                            log.warn("Failed to resolve username: {}", username);
                            return null;
                        }
                    })
                    .exceptionally(throwable -> {
                        log.error("Error resolving username: {}", username, throwable);
                        return null;
                    });
        } catch (Exception e) {
            log.error("Error resolving chat ID by username", e);
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Download media files from Telegram link
     */
    public CompletableFuture<TelegramLinkDownloadResult> downloadFromTelegramLink(TelegramLinkRequest request) {
        try {
            String taskId = UUID.randomUUID().toString();

            // Parse link
            TelegramLinkInfo linkInfo = parseTelegramLink(request.getMessageLink());

            if (!linkInfo.getIsValid()) {
                TelegramLinkDownloadResult result = TelegramLinkDownloadResult.builder()
                        .taskId(taskId)
                        .originalLink(request.getMessageLink())
                        .linkInfo(linkInfo)
                        .status("FAILED")
                        .errorMessage(linkInfo.getErrorMessage())
                        .totalCount(0)
                        .successCount(0)
                        .failedCount(0)
                        .startTime(LocalDateTime.now())
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();

                telegramCacheManager.getLinkDownloadCache().put(taskId, result);
                return CompletableFuture.completedFuture(result);
            }

            // Create initial result
            TelegramLinkDownloadResult result = TelegramLinkDownloadResult.builder()
                    .taskId(taskId)
                    .originalLink(request.getMessageLink())
                    .linkInfo(linkInfo)
                    .status("PARSING")
                    .totalCount(0)
                    .successCount(0)
                    .failedCount(0)
                    .startTime(LocalDateTime.now())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .downloads(new ArrayList<>())
                    .build();

            telegramCacheManager.getLinkDownloadCache().put(taskId, result);

            // Handle different link types
            if ("PUBLIC_CHANNEL".equals(linkInfo.getLinkType())) {
                return handlePublicChannelDownload(result, request);
            } else {
                return handlePrivateChatDownload(result, request);
            }

        } catch (Exception e) {
            log.error("Error downloading from Telegram link", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Handle public channel download
     */
    private CompletableFuture<TelegramLinkDownloadResult> handlePublicChannelDownload(
            TelegramLinkDownloadResult result, TelegramLinkRequest request) {

        TelegramLinkInfo linkInfo = result.getLinkInfo();

        // First resolve username to get chatId
        return resolveChatIdByUsername(linkInfo.getUsername())
                .thenCompose(chatId -> {
                    if (chatId == null) {
                        result.setStatus("FAILED");
                        result.setErrorMessage("Failed to resolve channel username: " + linkInfo.getUsername());
                        result.setUpdatedAt(LocalDateTime.now());
                        return CompletableFuture.completedFuture(result);
                    }

                    // Update chatId in linkInfo
                    linkInfo.setChatId(chatId);
                    result.setStatus("FETCHING");
                    result.setUpdatedAt(LocalDateTime.now());

                    // Fetch message and download
                    return fetchMessageAndDownload(result, request, chatId, linkInfo.getMessageId());
                });
    }

    /**
     * Handle private chat download
     */
    private CompletableFuture<TelegramLinkDownloadResult> handlePrivateChatDownload(
            TelegramLinkDownloadResult result, TelegramLinkRequest request) {

        TelegramLinkInfo linkInfo = result.getLinkInfo();
        result.setStatus("FETCHING");
        result.setUpdatedAt(LocalDateTime.now());

          // Fetch message directly and download
        return fetchMessageAndDownload(result, request, linkInfo.getChatId(), linkInfo.getMessageId());
    }

    /**
      * Fetch message and start download
     */
    private CompletableFuture<TelegramLinkDownloadResult> fetchMessageAndDownload(
            TelegramLinkDownloadResult result, TelegramLinkRequest request, Long chatId, Long messageId) {

        try {
            log.info("Trying to fetch message: chatId={}, messageId={}", chatId, messageId);

            // Try direct message fetch
            TdApi.GetMessage getMessageRequest = new TdApi.GetMessage();
            getMessageRequest.chatId = chatId;
            getMessageRequest.messageId = messageId;

            return client.send(getMessageRequest)
                    .handle((messageResult, throwable) -> {
                        if (throwable != null) {
                            log.warn("Direct message fetch failed: chatId={}, messageId={}, error={}",
                                    chatId, messageId, throwable.getMessage());
                            log.info("Trying to find message via message ID mapping...");

                            // Try to find the corresponding internal message ID from chat history
                            return findMessageByLinkId(chatId, messageId)
                                    .thenCompose(foundMessage -> {
                                        if (foundMessage != null) {
                                    log.info("Found message via ID mapping: link ID={}, internal ID={}",
                                                    messageId, foundMessage.id);

                                    // Fetch chat and user info
                                            return CompletableFuture.allOf(
                                                    telegramUtil.getChatInfo(chatId),
                                                    telegramUtil.getUserFromMessage(foundMessage)
                                            ).thenCompose(v -> {
                                                try {
                                                    TdApi.Chat chatInfo = telegramUtil.getChatInfo(chatId).join();
                                                    TdApi.User user =telegramUtil.getUserFromMessage(foundMessage).join();

                                                    MessageInfo messageInfo = messageMapping.convertToMessageInfo(foundMessage, chatInfo, user);

                                                    // Add converted message to related cache
                                                    addMessageToCache(messageInfo);

                                                    result.setMessageInfo(messageInfo);
                                                    result.setStatus("DOWNLOADING");
                                                    result.setUpdatedAt(LocalDateTime.now());

                                                    // Start media download
                                                    return startMediaDownloads(result, request, messageInfo);

                                                } catch (Exception e) {
                                                    log.error("Error processing found message info", e);
                                                    result.setStatus("FAILED");
                                                    result.setErrorMessage("Error while processing found message info: " + e.getMessage());
                                                    result.setUpdatedAt(LocalDateTime.now());
                                                    return CompletableFuture.completedFuture(result);
                                                }
                                            });
                                        } else {
                                            log.error("Message ID mapping also failed: chatId={}, messageId={}", chatId, messageId);
                                            result.setStatus("FAILED");
                                            result.setErrorMessage("Unable to fetch message: " + messageId + " (message does not exist or has been deleted)");
                                            result.setUpdatedAt(LocalDateTime.now());
                                            return CompletableFuture.completedFuture(result);
                                        }
                                    });
                        }

                        if (!(messageResult instanceof TdApi.Message)) {
                            log.warn("Direct message fetch failed: chatId={}, messageId={}, result={}",
                                    chatId, messageId, messageResult.getClass().getSimpleName());
                            log.info("Trying to find message via message ID mapping...");

                            // Try to find the corresponding internal message ID from chat history
                            return findMessageByLinkId(chatId, messageId)
                                    .thenCompose(foundMessage -> {
                                        if (foundMessage != null) {
                                            log.info("Found message via ID mapping: link ID={}, internal ID={}",
                                                    messageId, foundMessage.id);

                                            // Fetch chat and user info
                                            return CompletableFuture.allOf(
                                                    telegramUtil.getChatInfo(chatId),
                                                    telegramUtil.getUserFromMessage(foundMessage)
                                            ).thenCompose(v -> {
                                                try {
                                                    TdApi.Chat chatInfo = telegramUtil.getChatInfo(chatId).join();
                                                    TdApi.User user = telegramUtil.getUserFromMessage(foundMessage).join();

                                                    MessageInfo messageInfo = messageMapping.convertToMessageInfo(foundMessage, chatInfo, user);

                                                    // Add converted message to related cache
                                                    addMessageToCache(messageInfo);

                                                    result.setMessageInfo(messageInfo);
                                                    result.setStatus("DOWNLOADING");
                                                    result.setUpdatedAt(LocalDateTime.now());

                                                    // Start media download
                                                    return startMediaDownloads(result, request, messageInfo);

                                                } catch (Exception e) {
                                                    log.error("Error processing found message info", e);
                                                    result.setStatus("FAILED");
                                                    result.setErrorMessage("Error while processing found message info: " + e.getMessage());
                                                    result.setUpdatedAt(LocalDateTime.now());
                                                    return CompletableFuture.completedFuture(result);
                                                }
                                            });
                                        } else {
                                            log.error("Message ID mapping also failed: chatId={}, messageId={}", chatId, messageId);
                                            result.setStatus("FAILED");
                                            result.setErrorMessage("Unable to fetch message: " + messageId + " (message does not exist or has been deleted)");
                                            result.setUpdatedAt(LocalDateTime.now());
                                            return CompletableFuture.completedFuture(result);
                                        }
                                    });
                        }

                        TdApi.Message message = (TdApi.Message) messageResult;

                        // Fetch chat and user info
                        return CompletableFuture.allOf(
                                telegramUtil.getChatInfo(chatId),
                                telegramUtil.getUserFromMessage(message)
                        ).thenCompose(v -> {
                            try {
                                TdApi.Chat chatInfo = telegramUtil.getChatInfo(chatId).join();
                                TdApi.User user = telegramUtil.getUserFromMessage(message).join();

                                MessageInfo messageInfo = messageMapping.convertToMessageInfo(message, chatInfo, user);

                                // Add message to related cache
                                addMessageToCache(messageInfo);

                                result.setMessageInfo(messageInfo);
                                result.setStatus("DOWNLOADING");
                                result.setUpdatedAt(LocalDateTime.now());

                                // Start media download
                                return startMediaDownloads(result, request, messageInfo);

                            } catch (Exception e) {
                                log.error("Error processing message info", e);
                                result.setStatus("FAILED");
                                result.setErrorMessage("Error while processing message info: " + e.getMessage());
                                result.setUpdatedAt(LocalDateTime.now());
                                return CompletableFuture.completedFuture(result);
                            }
                        });
                    })
                    .thenCompose(future -> future);

        } catch (Exception e) {
            log.error("Error in fetchMessageAndDownload", e);
            result.setStatus("FAILED");
            result.setErrorMessage("Error while fetching message: " + e.getMessage());
            result.setUpdatedAt(LocalDateTime.now());
            return CompletableFuture.completedFuture(result);
        }
    }

    /**
     * Start media file downloads
     */
    private CompletableFuture<TelegramLinkDownloadResult> startMediaDownloads(
            TelegramLinkDownloadResult result, TelegramLinkRequest request, MessageInfo messageInfo) {

        try {
            List<CompletableFuture<DownloadInfo>> downloadFutures = new ArrayList<>();
            String downloadType = request.getDownloadType() != null ? request.getDownloadType().toUpperCase() : "AUTO";

            // Decide what to download based on message type and download type
            boolean shouldDownloadMedia = shouldDownloadMedia(messageInfo.getMessageType(), downloadType);
            boolean shouldDownloadThumbnail = request.getDownloadThumbnail() != null ?
                    request.getDownloadThumbnail() : false;

            if (shouldDownloadMedia) {
                // Download main media file
                if ("VIDEO".equals(messageInfo.getMessageType())) {
                    downloadFutures.add(downloadVideo(messageInfo.getId(), messageInfo.getChatId()));
                } else if ("PHOTO".equals(messageInfo.getMessageType())) {
                    downloadFutures.add(downloadPhoto(messageInfo.getId(), messageInfo.getChatId()));
                } else if ("DOCUMENT".equals(messageInfo.getMessageType())) {
                    downloadFutures.add(downloadDocument(messageInfo.getId(), messageInfo.getChatId()));
                }
            }

            // Download thumbnail (video only)
            if (shouldDownloadThumbnail && "VIDEO".equals(messageInfo.getMessageType()) &&
                    messageInfo.getThumbnailFileId() != null) {
                downloadFutures.add(downloadVideoThumbnail(messageInfo.getId(), messageInfo.getChatId()));
            }

            if (downloadFutures.isEmpty()) {
                result.setStatus("COMPLETED");
                result.setTotalCount(0);
                result.setSuccessCount(0);
                result.setFailedCount(0);
                result.setErrorMessage("No downloadable media files found");
                result.setCompletedTime(LocalDateTime.now());
                result.setUpdatedAt(LocalDateTime.now());
                return CompletableFuture.completedFuture(result);
            }

            result.setTotalCount(downloadFutures.size());
            result.setUpdatedAt(LocalDateTime.now());

            // Wait for all downloads to complete
            return CompletableFuture.allOf(downloadFutures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> {
                        List<DownloadInfo> downloads = new ArrayList<>();
                        int successCount = 0;
                        int failedCount = 0;

                        for (CompletableFuture<DownloadInfo> future : downloadFutures) {
                            try {
                                DownloadInfo download = future.join();
                                downloads.add(download);
                                if ("COMPLETED".equals(download.getStatus()) || "DOWNLOADING".equals(download.getStatus())) {
                                    successCount++;
                                } else {
                                    failedCount++;
                                }
                            } catch (Exception e) {
                                failedCount++;
                                log.error("Download failed", e);
                            }
                        }

                        result.setDownloads(downloads);
                        result.setSuccessCount(successCount);
                        result.setFailedCount(failedCount);
                        result.setStatus(failedCount == 0 ? "COMPLETED" : "PARTIAL_SUCCESS");
                        result.setCompletedTime(LocalDateTime.now());
                        result.setUpdatedAt(LocalDateTime.now());

                        log.info("Link download completed: {} successful, {} failed", successCount, failedCount);
                        return result;
                    })
                    .exceptionally(throwable -> {
                        log.error("Error in media downloads", throwable);
                        result.setStatus("FAILED");
                        result.setErrorMessage("Error occurred while downloading media files: " + throwable.getMessage());
                        result.setUpdatedAt(LocalDateTime.now());
                        return result;
                    });

        } catch (Exception e) {
            log.error("Error starting media downloads", e);
            result.setStatus("FAILED");
            result.setErrorMessage("Error occurred while starting download: " + e.getMessage());
            result.setUpdatedAt(LocalDateTime.now());
            return CompletableFuture.completedFuture(result);
        }
    }

    /**
     * Determine whether media should be downloaded
     */
    private boolean shouldDownloadMedia(String messageType, String downloadType) {
        if ("ALL".equals(downloadType) || "AUTO".equals(downloadType)) {
            return "VIDEO".equals(messageType) || "PHOTO".equals(messageType) || "DOCUMENT".equals(messageType);
        }

        switch (downloadType) {
            case "VIDEO":
                return "VIDEO".equals(messageType);
            case "PHOTO":
                return "PHOTO".equals(messageType);
            case "DOCUMENT":
                return "DOCUMENT".equals(messageType);
            default:
                return false;
        }
    }

    /**
     * Download photo (to be implemented)
     */
    private CompletableFuture<DownloadInfo> downloadPhoto(Long messageId, Long chatId) {
        // TODO: Implement photo download logic
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Photo download is not implemented yet"));
    }

    /**
     * Download document (to be implemented)
     */
    private CompletableFuture<DownloadInfo> downloadDocument(Long messageId, Long chatId) {
        // TODO: Implement document download logic
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Document download is not implemented yet"));
    }

    /**
     * Get link download task status
     */
    public TelegramLinkDownloadResult getLinkDownloadStatus(String taskId) {
        return telegramCacheManager.getLinkDownloadCache().get(taskId);
    }

    /**
     * Get all link download tasks
     */
    public List<TelegramLinkDownloadResult> getAllLinkDownloads() {
        return new ArrayList<>(telegramCacheManager.getLinkDownloadCache().values());
    }

    /**
     * Check whether user is in the specified chat
     */
    public CompletableFuture<Boolean> isUserInChat(Long chatId) {
        try {
            return telegramUtil.getChatInfo(chatId)
                    .thenApply(chat -> {
                        if (chat == null) {
                            log.warn("Unable to access chat: chatId={}", chatId);
                            return false;
                        }
                        log.info("Chat is accessible: {} (ID: {})", chat.title, chatId);
                        return true;
                    })
                    .exceptionally(throwable -> {
                        log.error("Error while checking chat access: chatId={}", chatId, throwable);
                        return false;
                    });
        } catch (Exception e) {
            log.error("Error checking user in chat", e);
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Get latest messages in a chat
     */
    public CompletableFuture<List<MessageInfo>> getLatestMessages(Long chatId, int limit) {
        try {
            log.info("Getting latest messages in chat: chatId={}, limit={}", chatId, limit);

            TdApi.GetChatHistory getChatHistoryRequest = new TdApi.GetChatHistory();
            getChatHistoryRequest.chatId = chatId;
            getChatHistoryRequest.fromMessageId = 0; // Start from latest message
            getChatHistoryRequest.offset = 0;
            getChatHistoryRequest.limit = limit;
            getChatHistoryRequest.onlyLocal = false;

            return client.send(getChatHistoryRequest)
                    .thenCompose(result -> {
                        if (!(result instanceof TdApi.Messages)) {
                            log.error("Failed to get chat history: chatId={}, result={}", chatId, result.getClass().getSimpleName());
                            return CompletableFuture.completedFuture(new ArrayList<MessageInfo>());
                        }

                        TdApi.Messages messages = (TdApi.Messages) result;
                        log.info("Retrieved {} messages", messages.messages.length);

                        if (messages.messages.length == 0) {
                            return CompletableFuture.completedFuture(new ArrayList<MessageInfo>());
                        }

                        // Fetch chat info
                        return telegramUtil.getChatInfo(chatId)
                                .thenCompose(chat -> {
                                    if (chat == null) {
                                        return CompletableFuture.completedFuture(new ArrayList<MessageInfo>());
                                    }

                                    // Convert messages
                                    List<CompletableFuture<MessageInfo>> messageFutures = new ArrayList<CompletableFuture<MessageInfo>>();
                                    for (TdApi.Message message : messages.messages) {
                                        CompletableFuture<MessageInfo> messageFuture = telegramUtil.getUserFromMessage(message)
                                                .thenApply(user -> messageMapping.convertToMessageInfo(message, chat, user));
                                        messageFutures.add(messageFuture);
                                    }

                                    return CompletableFuture.allOf(messageFutures.toArray(new CompletableFuture[0]))
                                            .thenApply(v -> {
                                                List<MessageInfo> messageInfos = new ArrayList<MessageInfo>();
                                                for (CompletableFuture<MessageInfo> future : messageFutures) {
                                                    try {
                                                        MessageInfo messageInfo = future.join();
                                                        if (messageInfo != null) {
                                                            messageInfos.add(messageInfo);
                                                        }
                                                    } catch (Exception e) {
                                                        log.error("Error converting message", e);
                                                    }
                                                }
                                                log.info("Successfully converted {} messages", messageInfos.size());
                                                return messageInfos;
                                            });
                                });
                    })
                    .exceptionally(throwable -> {
                        log.error("Error while getting latest messages: chatId={}", chatId, throwable);
                        return new ArrayList<MessageInfo>();
                    });
        } catch (Exception e) {
            log.error("Error getting latest messages", e);
            return CompletableFuture.completedFuture(new ArrayList<MessageInfo>());
        }
    }

    /**
     * Find the corresponding internal message by link message ID
     *
     * @param chatId        chat ID
     * @param linkMessageId message ID from link
     * @return found message, or null if not found
     */
    private CompletableFuture<TdApi.Message> findMessageByLinkId(Long chatId, Long linkMessageId) {
        try {
            log.info("Start searching internal message by link message ID: chatId={}, linkMessageId={}", chatId, linkMessageId);

            // Get chat history and search from latest messages
            return getMessagesForLinkIdSearch(chatId, linkMessageId, 0, 100)
                    .thenCompose(foundMessage -> {
                        if (foundMessage != null) {
                            return CompletableFuture.completedFuture(foundMessage);
                        }

                        // If not found in the latest 100 messages, continue searching deeper history
                        log.info("Not found in latest 100 messages, continuing to search history...");
                        return searchInHistoryMessages(chatId, linkMessageId, 500);
                    });
        } catch (Exception e) {
            log.error("Error finding message by link ID", e);
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Fetch messages and check link ID match
     */
    private CompletableFuture<TdApi.Message> getMessagesForLinkIdSearch(Long chatId, Long linkMessageId, long fromMessageId, int limit) {
        try {
            TdApi.GetChatHistory getChatHistoryRequest = new TdApi.GetChatHistory();
            getChatHistoryRequest.chatId = chatId;
            getChatHistoryRequest.fromMessageId = fromMessageId;
            getChatHistoryRequest.offset = 0;
            getChatHistoryRequest.limit = limit;
            getChatHistoryRequest.onlyLocal = false;

            return client.send(getChatHistoryRequest)
                    .thenCompose(result -> {
                        if (!(result instanceof TdApi.Messages)) {
                            return CompletableFuture.completedFuture(null);
                        }

                        TdApi.Messages messages = (TdApi.Messages) result;
                        log.info("Searching messages: retrieved {} messages for link ID matching", messages.messages.length);

                        // Get link for each message and check whether it matches
                        List<CompletableFuture<TdApi.Message>> linkCheckFutures = new ArrayList<>();

                        for (TdApi.Message message : messages.messages) {
                            CompletableFuture<TdApi.Message> linkCheckFuture = getMessageLink(chatId, message.id)
                                    .thenApply(messageLink -> {
                                        if (messageLink != null && extractLinkMessageId(messageLink).equals(linkMessageId)) {
                                            log.info("Found matching message: internal ID={}, link ID={}, link={}",
                                                    message.id, linkMessageId, messageLink);
                                            return message;
                                        }
                                        return null;
                                    });
                            linkCheckFutures.add(linkCheckFuture);
                        }

                        // Wait for all link checks to complete
                        return CompletableFuture.allOf(linkCheckFutures.toArray(new CompletableFuture[0]))
                                .thenApply(v -> {
                                    for (CompletableFuture<TdApi.Message> future : linkCheckFutures) {
                                        try {
                                            TdApi.Message foundMessage = future.join();
                                            if (foundMessage != null) {
                                                return foundMessage;
                                            }
                                        } catch (Exception e) {
                                            log.debug("Error checking message link", e);
                                        }
                                    }
                                    return null;
                                });
                    });
        } catch (Exception e) {
            log.error("Error getting messages for link ID search", e);
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Search in historical messages
     */
    private CompletableFuture<TdApi.Message> searchInHistoryMessages(Long chatId, Long linkMessageId, int totalLimit) {
        // Simplified version: only search recent messages to avoid excessive complexity
        return getMessagesForLinkIdSearch(chatId, linkMessageId, 0, totalLimit);
    }

    /**
     * Extract link message ID from message link
     */
    private Long extractLinkMessageId(String messageLink) {
        try {
            if (messageLink == null || !messageLink.contains("t.me/c/")) {
                return null;
            }

            // Parse link format: https://t.me/c/2415289392/5664
            String[] parts = messageLink.split("/");
            if (parts.length >= 2) {
                String lastPart = parts[parts.length - 1];
                return Long.parseLong(lastPart);
            }
        } catch (Exception e) {
            log.debug("Error extracting link message ID from: {}", messageLink, e);
        }
        return null;
    }

    /**
     * Get message share link
     */
    public CompletableFuture<String> getMessageLink(Long chatId, Long messageId) {
        try {
            log.info("Getting message link: chatId={}, messageId={}", chatId, messageId);

            TdApi.GetMessageLink getMessageLinkRequest = new TdApi.GetMessageLink();
            getMessageLinkRequest.chatId = chatId;
            getMessageLinkRequest.messageId = messageId;
            getMessageLinkRequest.mediaTimestamp = 0;
            getMessageLinkRequest.forAlbum = false;

            return client.send(getMessageLinkRequest)
                    .thenApply(result -> {
                        if (result instanceof TdApi.MessageLink) {
                            TdApi.MessageLink messageLink = (TdApi.MessageLink) result;
                            log.info("Successfully retrieved message link: chatId={}, messageId={}, link={}",
                                    chatId, messageId, messageLink.link);
                            return messageLink.link;
                        } else {
                            log.error("Failed to get message link: chatId={}, messageId={}, result={}",
                                    chatId, messageId, result.getClass().getSimpleName());
                            return null;
                        }
                    })
                    .exceptionally(throwable -> {
                        log.error("Error while getting message link: chatId={}, messageId={}", chatId, messageId, throwable);
                        return null;
                    });
        } catch (Exception e) {
            log.error("Error getting message link", e);
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Get info for a specific message
     */
    public CompletableFuture<MessageInfo> getMessageInfo(Long chatId, Long messageId) {
        try {
            log.info("Getting message info: chatId={}, messageId={}", chatId, messageId);

            TdApi.GetMessage getMessageRequest = new TdApi.GetMessage();
            getMessageRequest.chatId = chatId;
            getMessageRequest.messageId = messageId;

            return client.send(getMessageRequest)
                    .thenCompose(messageResult -> {
                        if (!(messageResult instanceof TdApi.Message)) {
                            log.error("Failed to get message: chatId={}, messageId={}, result={}",
                                    chatId, messageId, messageResult.getClass().getSimpleName());
                            return CompletableFuture.completedFuture(null);
                        }

                        TdApi.Message message = (TdApi.Message) messageResult;
                        log.info("Successfully retrieved message: chatId={}, messageId={}, type={}",
                                chatId, messageId, message.content.getClass().getSimpleName());

                        // Fetch chat and user info
                        return CompletableFuture.allOf(
                                telegramUtil.getChatInfo(chatId),
                                telegramUtil.getUserFromMessage(message)
                        ).thenApply(v -> {
                            try {
                                TdApi.Chat chatInfo = telegramUtil.getChatInfo(chatId).join();
                                TdApi.User user = telegramUtil.getUserFromMessage(message).join();

                                MessageInfo messageInfo = messageMapping.convertToMessageInfo(message, chatInfo, user);
                                log.info("Message conversion completed: type={}, content={}",
                                        messageInfo.getMessageType(),
                                        messageInfo.getContent() != null ? messageInfo.getContent().substring(0, Math.min(50, messageInfo.getContent().length())) : "null");
                                return messageInfo;
                            } catch (Exception e) {
                                log.error("Error converting message info", e);
                                return null;
                            }
                        });
                    })
                    .exceptionally(throwable -> {
                        log.error("Error while getting message info: chatId={}, messageId={}", chatId, messageId, throwable);
                        return null;
                    });
        } catch (Exception e) {
            log.error("Error getting message info", e);
            return CompletableFuture.completedFuture(null);
        }
    }

    @PreDestroy
    public void cleanup() {
        if (client != null) {
            try {
                client.close();
                log.info("Telegram client closed successfully");
            } catch (Exception e) {
                log.error("Error closing Telegram client", e);
            }
        }
    }


    // ==================== MESSAGE MANAGEMENT APIs ====================

    /**
     * Pin a message in a chat
     */
    public CompletableFuture<Boolean> pinMessage(Long chatId, Long messageId, boolean disableNotification, boolean onlyForSelf) {
        try {
            TdApi.PinChatMessage request = new TdApi.PinChatMessage(
                    chatId,
                    messageId,
                    disableNotification,
                    onlyForSelf
            );

            return client.send(request)
                    .thenApply(result -> {
                        log.info("Message {} pinned in chat {}", messageId, chatId);
                        return true;
                    })
                    .exceptionally(throwable -> {
                        log.error("Error pinning message: {}", throwable.getMessage());
                        return false;
                    });
        } catch (Exception e) {
            log.error("Error pinning message: {}", e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Unpin a message in a chat
     */
    public CompletableFuture<Boolean> unpinMessage(Long chatId, Long messageId) {
        try {
            TdApi.UnpinChatMessage request = new TdApi.UnpinChatMessage(chatId, messageId);

            return client.send(request)
                    .thenApply(result -> {
                        log.info("Message {} unpinned in chat {}", messageId, chatId);
                        return true;
                    })
                    .exceptionally(throwable -> {
                        log.error("Error unpinning message: {}", throwable.getMessage());
                        return false;
                    });
        } catch (Exception e) {
            log.error("Error unpinning message: {}", e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Get pinned messages in a chat
     */
    public CompletableFuture<List<MessageInfo>> getPinnedMessages(Long chatId) {
        try {
            TdApi.GetChatPinnedMessage request = new TdApi.GetChatPinnedMessage(chatId);

            return client.send(request)
                    .thenApply(message -> {
                        MessageInfo messageInfo = convertToMessageInfo(message);
                        return List.of(messageInfo);
                    })
                    .exceptionally(throwable -> {
                        log.error("Error getting pinned messages: {}", throwable.getMessage());
                        return List.of();
                    });
        } catch (Exception e) {
            log.error("Error getting pinned messages: {}", e.getMessage());
            return CompletableFuture.completedFuture(List.of());
        }
    }

    /**
     * Get a specific message by ID
     */
    public CompletableFuture<MessageInfo> getMessageById(Long chatId, Long messageId) {
        try {
            TdApi.GetMessage request = new TdApi.GetMessage(chatId, messageId);

            return client.send(request)
                    .thenApply(message -> convertToMessageInfo(message))
                    .exceptionally(throwable -> {
                        log.error("Error getting message: {}", throwable.getMessage());
                        throw new RuntimeException(throwable);
                    });
        } catch (Exception e) {
            log.error("Error getting message: {}", e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }

    // ==================== CHAT MANAGEMENT APIs ====================

    /**
     * Mark chat as unread
     */
    public CompletableFuture<Boolean> markChatAsUnread(Long chatId) {
        try {
            TdApi.ToggleChatIsMarkedAsUnread request = new TdApi.ToggleChatIsMarkedAsUnread(chatId, true);

            return client.send(request)
                    .thenApply(result -> {
                        log.info("Chat {} marked as unread", chatId);
                        return true;
                    })
                    .exceptionally(throwable -> {
                        log.error("Error marking chat as unread: {}", throwable.getMessage());
                        return false;
                    });
        } catch (Exception e) {
            log.error("Error marking chat as unread: {}", e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Pin a chat
     */
    public CompletableFuture<Boolean> pinChat(Long chatId) {
        try {
            TdApi.ToggleChatIsPinned request = new TdApi.ToggleChatIsPinned(
                    new TdApi.ChatListMain(),
                    chatId,
                    true
            );

            return client.send(request)
                    .thenApply(result -> {
                        log.info("Chat {} pinned", chatId);
                        return true;
                    })
                    .exceptionally(throwable -> {
                        log.error("Error pinning chat: {}", throwable.getMessage());
                        return false;
                    });
        } catch (Exception e) {
            log.error("Error pinning chat: {}", e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Unpin a chat
     */
    public CompletableFuture<Boolean> unpinChat(Long chatId) {
        try {
            TdApi.ToggleChatIsPinned request = new TdApi.ToggleChatIsPinned(
                    new TdApi.ChatListMain(),
                    chatId,
                    false
            );

            return client.send(request)
                    .thenApply(result -> {
                        log.info("Chat {} unpinned", chatId);
                        return true;
                    })
                    .exceptionally(throwable -> {
                        log.error("Error unpinning chat: {}", throwable.getMessage());
                        return false;
                    });
        } catch (Exception e) {
            log.error("Error unpinning chat: {}", e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Archive a chat
     */
    public CompletableFuture<Boolean> archiveChat(Long chatId) {
        try {
            TdApi.AddChatToList request = new TdApi.AddChatToList(
                    chatId,
                    new TdApi.ChatListArchive()
            );

            return client.send(request)
                    .thenApply(result -> {
                        log.info("Chat {} archived", chatId);
                        return true;
                    })
                    .exceptionally(throwable -> {
                        log.error("Error archiving chat: {}", throwable.getMessage());
                        return false;
                    });
        } catch (Exception e) {
            log.error("Error archiving chat: {}", e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Unarchive a chat
     */
    public CompletableFuture<Boolean> unarchiveChat(Long chatId) {
        try {
            TdApi.AddChatToList request = new TdApi.AddChatToList(
                    chatId,
                    new TdApi.ChatListMain()
            );

            return client.send(request)
                    .thenApply(result -> {
                        log.info("Chat {} unarchived", chatId);
                        return true;
                    })
                    .exceptionally(throwable -> {
                        log.error("Error unarchiving chat: {}", throwable.getMessage());
                        return false;
                    });
        } catch (Exception e) {
            log.error("Error unarchiving chat: {}", e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Get archived chats
     */
    public CompletableFuture<List<ChatListItem>> getArchivedChats(int limit) {
        try {
            TdApi.GetChats request = new TdApi.GetChats(new TdApi.ChatListArchive(), limit);

            return client.send(request)
                    .thenCompose(chats -> {
                        List<CompletableFuture<ChatListItem>> futures = new ArrayList<>();

                        for (long chatId : chats.chatIds) {
                            CompletableFuture<ChatListItem> future = telegramUtil.getChatInfo(chatId).thenApply(chat -> {
                                ChatListItem item = new ChatListItem();
                                item.setChatId(chatId);
                                item.setTitle(chat.title);
                                return item;
                            });
                            futures.add(future);
                        }

                        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                                .thenApply(v -> futures.stream()
                                        .map(CompletableFuture::join)
                                        .collect(Collectors.toList()));
                    })
                    .exceptionally(throwable -> {
                        log.error("Error getting archived chats: {}", throwable.getMessage());
                        return List.of();
                    });
        } catch (Exception e) {
            log.error("Error getting archived chats: {}", e.getMessage());
            return CompletableFuture.completedFuture(List.of());
        }
    }

    /**
     * Delete chat history
     */
    public CompletableFuture<Boolean> deleteChatHistory(Long chatId, boolean deleteForEveryone, boolean revoke) {
        try {
            TdApi.DeleteChatHistory request = new TdApi.DeleteChatHistory(chatId, deleteForEveryone, revoke);

            return client.send(request)
                    .thenApply(result -> {
                        log.info("Chat history deleted for chat {}", chatId);
                        return true;
                    })
                    .exceptionally(throwable -> {
                        log.error("Error deleting chat history: {}", throwable.getMessage());
                        return false;
                    });
        } catch (Exception e) {
            log.error("Error deleting chat history: {}", e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Leave a chat
     */
    public CompletableFuture<Boolean> leaveChat(Long chatId) {
        try {
            TdApi.LeaveChat request = new TdApi.LeaveChat(chatId);

            return client.send(request)
                    .thenApply(result -> {
                        log.info("Left chat {}", chatId);
                        return true;
                    })
                    .exceptionally(throwable -> {
                        log.error("Error leaving chat: {}", throwable.getMessage());
                        return false;
                    });
        } catch (Exception e) {
            log.error("Error leaving chat: {}", e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Block a user
     */
    public CompletableFuture<Boolean> blockUser(Long userId) {
        try {
            TdApi.SetMessageSenderBlockList request = new TdApi.SetMessageSenderBlockList(
                    new TdApi.MessageSenderUser(userId),
                    new TdApi.BlockListMain()
            );

            return client.send(request)
                    .thenApply(result -> {
                        log.info("User {} blocked", userId);
                        return true;
                    })
                    .exceptionally(throwable -> {
                        log.error("Error blocking user: {}", throwable.getMessage());
                        return false;
                    });
        } catch (Exception e) {
            log.error("Error blocking user: {}", e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Unblock a user
     */
    public CompletableFuture<Boolean> unblockUser(Long userId) {
        try {
            TdApi.SetMessageSenderBlockList request = new TdApi.SetMessageSenderBlockList(
                    new TdApi.MessageSenderUser(userId),
                    null  // null means unblock
            );

            return client.send(request)
                    .thenApply(result -> {
                        log.info("User {} unblocked", userId);
                        return true;
                    })
                    .exceptionally(throwable -> {
                        log.error("Error unblocking user: {}", throwable.getMessage());
                        return false;
                    });
        } catch (Exception e) {
            log.error("Error unblocking user: {}", e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Get blocked users
     */
    public CompletableFuture<List<Long>> getBlockedUsers() {
        try {
            TdApi.GetBlockedMessageSenders request = new TdApi.GetBlockedMessageSenders(
                    new TdApi.BlockListMain(),
                    0,
                    100
            );

            return client.send(request)
                    .thenApply(senders -> {
                        List<Long> blockedUserIds = new ArrayList<>();

                        for (TdApi.MessageSender sender : senders.senders) {
                            if (sender instanceof TdApi.MessageSenderUser) {
                                TdApi.MessageSenderUser userSender = (TdApi.MessageSenderUser) sender;
                                blockedUserIds.add(userSender.userId);
                            }
                        }

                        log.info("Retrieved {} blocked users", blockedUserIds.size());
                        return blockedUserIds;
                    })
                    .exceptionally(throwable -> {
                        log.error("Error getting blocked users: {}", throwable.getMessage());
                        return List.of();
                    });
        } catch (Exception e) {
            log.error("Error getting blocked users: {}", e.getMessage());
            return CompletableFuture.completedFuture(List.of());
        }
    }

    // ==================== GROUP MANAGEMENT APIs ====================

    /**
     * Update group information
     */
    public CompletableFuture<Boolean> updateGroupInfo(Long groupId, String title, String description) {
        return telegramUtil.getChatInfo(groupId).thenCompose(chat -> {
            List<CompletableFuture<Void>> updates = new ArrayList<>();

            // Update title if provided
            if (title != null && !title.isEmpty()) {
                TdApi.SetChatTitle titleRequest = new TdApi.SetChatTitle(groupId, title);
                CompletableFuture<Void> titleFuture = client.send(titleRequest)
                        .thenApply(result -> {
                            log.info("Group {} title updated", groupId);
                            return null;
                        });
                updates.add(titleFuture);
            }

            // Update description if provided
            if (description != null && !description.isEmpty()) {
                TdApi.SetChatDescription descRequest = new TdApi.SetChatDescription(groupId, description);
                CompletableFuture<Void> descFuture = client.send(descRequest)
                        .thenApply(result -> {
                            log.info("Group {} description updated", groupId);
                            return null;
                        });
                updates.add(descFuture);
            }

            return CompletableFuture.allOf(updates.toArray(new CompletableFuture[0]))
                    .thenApply(v -> true);
        }).exceptionally(throwable -> {
            log.error("Error updating group info: {}", throwable.getMessage());
            return false;
        });
    }

    /**
     * Set group photo
     */
    public CompletableFuture<Boolean> setGroupPhoto(Long groupId, String photoPath) {
        try {
            TdApi.SetChatPhoto request = new TdApi.SetChatPhoto(
                    groupId,
                    new TdApi.InputChatPhotoStatic(new TdApi.InputFileLocal(photoPath))
            );

            return client.send(request)
                    .thenApply(result -> {
                        log.info("Group {} photo updated", groupId);
                        return true;
                    })
                    .exceptionally(throwable -> {
                        log.error("Error setting group photo: {}", throwable.getMessage());
                        return false;
                    });
        } catch (Exception e) {
            log.error("Error setting group photo: {}", e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Get group administrators
     */
    public CompletableFuture<List<GroupMemberInfo>> getGroupAdmins(Long groupId) {
        return telegramUtil.getChatInfo(groupId).thenCompose(chat -> {
            if (chat.type instanceof TdApi.ChatTypeSupergroup) {
                TdApi.ChatTypeSupergroup supergroup = (TdApi.ChatTypeSupergroup) chat.type;

                TdApi.GetSupergroupMembers request = new TdApi.GetSupergroupMembers(
                        supergroup.supergroupId,
                        new TdApi.SupergroupMembersFilterAdministrators(),
                        0,
                        100
                );

                return client.send(request)
                        .thenCompose(members -> {
                            List<CompletableFuture<GroupMemberInfo>> futures = new ArrayList<>();

                            for (TdApi.ChatMember member : members.members) {
                                if (member.memberId instanceof TdApi.MessageSenderUser) {
                                    TdApi.MessageSenderUser userSender = (TdApi.MessageSenderUser) member.memberId;
                                    futures.add(getUserInfo(userSender.userId));
                                }
                            }

                            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                                    .thenApply(v -> {
                                        List<GroupMemberInfo> result = new ArrayList<>();
                                        for (CompletableFuture<GroupMemberInfo> f : futures) {
                                            result.add(f.join());
                                        }
                                        return result;
                                    });
                        });
            } else {
                return CompletableFuture.completedFuture(new ArrayList<GroupMemberInfo>());
            }
        }).exceptionally(throwable -> {
            log.error("Error getting group admins: {}", throwable.getMessage());
            return new ArrayList<GroupMemberInfo>();
        });
    }

    /**
     * Promote member to admin
     */
    public CompletableFuture<Boolean> promoteMemberToAdmin(Long groupId, Long userId, Map<String, Boolean> permissions) {
        return telegramUtil.getChatInfo(groupId).thenCompose(chat -> {
            if (chat.type instanceof TdApi.ChatTypeSupergroup) {
                // Create admin rights
                TdApi.ChatAdministratorRights rights = new TdApi.ChatAdministratorRights(
                        permissions.getOrDefault("canManageChat", true),
                        permissions.getOrDefault("canChangeInfo", true),
                        permissions.getOrDefault("canPostMessages", false),
                        permissions.getOrDefault("canEditMessages", false),
                        permissions.getOrDefault("canDeleteMessages", true),
                        permissions.getOrDefault("canInviteUsers", true),
                        permissions.getOrDefault("canRestrictMembers", true),
                        permissions.getOrDefault("canPinMessages", true),
                        permissions.getOrDefault("canManageTopics", false),
                        permissions.getOrDefault("canPromoteMembers", false),
                        permissions.getOrDefault("canManageVideoChats", true),
                        permissions.getOrDefault("canPostStories", false),
                        permissions.getOrDefault("canEditStories", false),
                        permissions.getOrDefault("canDeleteStories", false),
                        permissions.getOrDefault("isAnonymous", false)
                );

                TdApi.SetChatMemberStatus request = new TdApi.SetChatMemberStatus(
                        groupId,
                        new TdApi.MessageSenderUser(userId),
                        new TdApi.ChatMemberStatusAdministrator("", true, rights)
                );

                return client.send(request)
                        .thenApply(result -> {
                            log.info("User {} promoted to admin in group {}", userId, groupId);
                            return true;
                        });
            } else {
                return CompletableFuture.failedFuture(new RuntimeException("Not a supergroup"));
            }
        }).exceptionally(throwable -> {
            log.error("Error promoting member: {}", throwable.getMessage());
            return false;
        });
    }

    /**
     * Demote admin to regular member
     */
    public CompletableFuture<Boolean> demoteAdmin(Long groupId, Long userId) {
        try {
            TdApi.SetChatMemberStatus request = new TdApi.SetChatMemberStatus(
                    groupId,
                    new TdApi.MessageSenderUser(userId),
                    new TdApi.ChatMemberStatusMember()
            );

            return client.send(request)
                    .thenApply(result -> {
                        log.info("User {} demoted in group {}", userId, groupId);
                        return true;
                    })
                    .exceptionally(throwable -> {
                        log.error("Error demoting admin: {}", throwable.getMessage());
                        return false;
                    });
        } catch (Exception e) {
            log.error("Error demoting admin: {}", e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Kick member from group
     */
    public CompletableFuture<Boolean> kickMember(Long groupId, Long userId, boolean banUser) {
        try {
            TdApi.ChatMemberStatus status = banUser
                    ? new TdApi.ChatMemberStatusBanned(0)
                    : new TdApi.ChatMemberStatusLeft();

            TdApi.SetChatMemberStatus request = new TdApi.SetChatMemberStatus(
                    groupId,
                    new TdApi.MessageSenderUser(userId),
                    status
            );

            return client.send(request)
                    .thenApply(result -> {
                        log.info("User {} kicked from group {}", userId, groupId);
                        return true;
                    })
                    .exceptionally(throwable -> {
                        log.error("Error kicking member: {}", throwable.getMessage());
                        return false;
                    });
        } catch (Exception e) {
            log.error("Error kicking member: {}", e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Ban member from group
     */
    public CompletableFuture<Boolean> banMember(Long groupId, Long userId, int untilDate) {
        try {
            TdApi.SetChatMemberStatus request = new TdApi.SetChatMemberStatus(
                    groupId,
                    new TdApi.MessageSenderUser(userId),
                    new TdApi.ChatMemberStatusBanned(untilDate)
            );

            return client.send(request)
                    .thenApply(result -> {
                        log.info("User {} banned from group {}", userId, groupId);
                        return true;
                    })
                    .exceptionally(throwable -> {
                        log.error("Error banning member: {}", throwable.getMessage());
                        return false;
                    });
        } catch (Exception e) {
            log.error("Error banning member: {}", e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Unban member from group
     */
    public CompletableFuture<Boolean> unbanMember(Long groupId, Long userId) {
        try {
            TdApi.SetChatMemberStatus request = new TdApi.SetChatMemberStatus(
                    groupId,
                    new TdApi.MessageSenderUser(userId),
                    new TdApi.ChatMemberStatusMember()
            );

            return client.send(request)
                    .thenApply(result -> {
                        log.info("User {} unbanned from group {}", userId, groupId);
                        return true;
                    })
                    .exceptionally(throwable -> {
                        log.error("Error unbanning member: {}", throwable.getMessage());
                        return false;
                    });
        } catch (Exception e) {
            log.error("Error unbanning member: {}", e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Get banned members
     */
    public CompletableFuture<List<GroupMemberInfo>> getBannedMembers(Long groupId) {
        return telegramUtil.getChatInfo(groupId).thenCompose(chat -> {
            if (chat.type instanceof TdApi.ChatTypeSupergroup) {
                TdApi.ChatTypeSupergroup supergroup = (TdApi.ChatTypeSupergroup) chat.type;

                TdApi.GetSupergroupMembers request = new TdApi.GetSupergroupMembers(
                        supergroup.supergroupId,
                        new TdApi.SupergroupMembersFilterBanned(""),
                        0,
                        100
                );

                return client.send(request)
                        .thenCompose(members -> {
                            List<CompletableFuture<GroupMemberInfo>> futures = new ArrayList<>();

                            for (TdApi.ChatMember member : members.members) {
                                if (member.memberId instanceof TdApi.MessageSenderUser) {
                                    TdApi.MessageSenderUser userSender = (TdApi.MessageSenderUser) member.memberId;
                                    futures.add(getUserInfo(userSender.userId));
                                }
                            }

                            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                                    .thenApply(v -> {
                                        List<GroupMemberInfo> result = new ArrayList<>();
                                        for (CompletableFuture<GroupMemberInfo> f : futures) {
                                            result.add(f.join());
                                        }
                                        return result;
                                    });
                        });
            } else {
                return CompletableFuture.completedFuture(new ArrayList<GroupMemberInfo>());
            }
        }).exceptionally(throwable -> {
            log.error("Error getting banned members: {}", throwable.getMessage());
            return new ArrayList<GroupMemberInfo>();
        });
    }

    /**
     * Restrict member permissions
     */
    public CompletableFuture<Boolean> restrictMember(Long groupId, Long userId, Map<String, Boolean> permissions, int untilDate) {
        try {
            TdApi.ChatPermissions chatPermissions = new TdApi.ChatPermissions(
                    permissions.getOrDefault("canSendMessages", false),
                    permissions.getOrDefault("canSendAudios", false),
                    permissions.getOrDefault("canSendDocuments", false),
                    permissions.getOrDefault("canSendPhotos", false),
                    permissions.getOrDefault("canSendVideos", false),
                    permissions.getOrDefault("canSendVideoNotes", false),
                    permissions.getOrDefault("canSendVoiceNotes", false),
                    permissions.getOrDefault("canSendPolls", false),
                    permissions.getOrDefault("canSendOtherMessages", false),
                    permissions.getOrDefault("canAddWebPagePreviews", false),
                    permissions.getOrDefault("canChangeInfo", false),
                    permissions.getOrDefault("canInviteUsers", false),
                    permissions.getOrDefault("canPinMessages", false),
                    permissions.getOrDefault("canManageTopics", false)
            );

            TdApi.SetChatMemberStatus request = new TdApi.SetChatMemberStatus(
                    groupId,
                    new TdApi.MessageSenderUser(userId),
                    new TdApi.ChatMemberStatusRestricted(
                            true,
                            untilDate,
                            chatPermissions
                    )
            );

            return client.send(request)
                    .thenApply(result -> {
                        log.info("User {} restricted in group {}", userId, groupId);
                        return true;
                    })
                    .exceptionally(throwable -> {
                        log.error("Error restricting member: {}", throwable.getMessage());
                        return false;
                    });
        } catch (Exception e) {
            log.error("Error restricting member: {}", e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Generate invite link for group
     */
    public CompletableFuture<String> generateInviteLink(Long groupId, String name, int expireDate, int memberLimit, boolean createsJoinRequest) {
        try {
            TdApi.CreateChatInviteLink request = new TdApi.CreateChatInviteLink(
                    groupId,
                    name != null ? name : "",
                    expireDate,
                    memberLimit,
                    createsJoinRequest
            );

            return client.send(request)
                    .thenApply(inviteLink -> {
                        log.info("Invite link generated for group {}: {}", groupId, inviteLink.inviteLink);
                        return inviteLink.inviteLink;
                    })
                    .exceptionally(throwable -> {
                        log.error("Error generating invite link: {}", throwable.getMessage());
                        throw new RuntimeException(throwable);
                    });
        } catch (Exception e) {
            log.error("Error generating invite link: {}", e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Revoke invite link
     */
    public CompletableFuture<Boolean> revokeInviteLink(Long groupId, String inviteLink) {
        try {
            TdApi.RevokeChatInviteLink request = new TdApi.RevokeChatInviteLink(groupId, inviteLink);

            return client.send(request)
                    .thenApply(result -> {
                        log.info("Invite link revoked for group {}", groupId);
                        return true;
                    })
                    .exceptionally(throwable -> {
                        log.error("Error revoking invite link: {}", throwable.getMessage());
                        return false;
                    });
        } catch (Exception e) {
            log.error("Error revoking invite link: {}", e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Get all invite links for a group
     */
    public CompletableFuture<List<String>> getInviteLinks(Long groupId) {
        try {
            TdApi.GetChatInviteLinks request = new TdApi.GetChatInviteLinks(
                    groupId,
                    clientProvider.getCurrentUserId(),
                    false,
                    0,
                    "",
                    100
            );

            return client.send(request)
                    .thenApply(links -> {
                        List<String> inviteLinks = new ArrayList<>();

                        for (TdApi.ChatInviteLink link : links.inviteLinks) {
                            inviteLinks.add(link.inviteLink);
                        }

                        log.info("Retrieved {} invite links for group {}", inviteLinks.size(), groupId);
                        return inviteLinks;
                    })
                    .exceptionally(throwable -> {
                        log.error("Error getting invite links: {}", throwable.getMessage());
                        return List.of();
                    });
        } catch (Exception e) {
            log.error("Error getting invite links: {}", e.getMessage());
            return CompletableFuture.completedFuture(List.of());
        }
    }

    /**
     * Helper method to convert TdApi.Message to MessageInfo
     */
    private MessageInfo convertToMessageInfo(TdApi.Message message) {
        MessageInfo info = new MessageInfo();
        info.setId(message.id);
        info.setChatId(message.chatId);
        info.setMessageDate(LocalDateTime.ofInstant(
                Instant.ofEpochSecond(message.date),
                ZoneId.systemDefault()
        ));

        // Set content based on message type
        if (message.content.getConstructor() == TdApi.MessageText.CONSTRUCTOR) {
            TdApi.MessageText textContent = (TdApi.MessageText) message.content;
            info.setContent(textContent.text.text);
            info.setMessageType("TEXT");
        }

        return info;
    }

    /**
     * Process media files for messages - download from Telegram and upload to MinIO
     */
    private CompletableFuture<Void> processMediaFilesForMessages(List<MessageInfo> messages) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (MessageInfo message : messages) {
            // Skip text messages
            if ("TEXT".equals(message.getMessageType())) {
                continue;
            }
            
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    processMessageMedia(message);
                } catch (Exception e) {
                    log.error("Failed to process media for message {}: {}", message.getId(), e.getMessage());
                }
            });
            
            futures.add(future);
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    /**
     * Process media for a single message
     */
    private void processMessageMedia(MessageInfo message) {
        String messageType = message.getMessageType();
        
        try {
            switch (messageType) {
                case "PHOTO":
                    processPhotoMessage(message);
                    break;
                case "VIDEO":
                    processVideoMessage(message);
                    break;
                case "VOICE":
                    processVoiceMessage(message);
                    break;
                case "AUDIO":
                    processAudioMessage(message);
                    break;
                case "DOCUMENT":
                    processDocumentMessage(message);
                    break;
            }
        } catch (Exception e) {
            log.error("Failed to process {} for message {}", messageType, message.getId(), e);
        }
    }

    /**
     * Process photo message
     */
    private void processPhotoMessage(MessageInfo message) {
        try {
            TdApi.Message tdMessage = getMessageFromTelegram(message.getChatId(), message.getId());
            if (!(tdMessage.content instanceof TdApi.MessagePhoto)) {
                return;
            }
            
            TdApi.MessagePhoto photoMessage = (TdApi.MessagePhoto) tdMessage.content;
            TdApi.PhotoSize largestPhoto = photoMessage.photo.sizes[photoMessage.photo.sizes.length - 1];
            
            // Download from Telegram
            TdApi.File file = downloadFileSync(largestPhoto.photo.id);
            if (file == null || !file.local.isDownloadingCompleted) {
                log.warn("Failed to download photo for message {}", message.getId());
                return;
            }
            
            // Copy to local storage
            String fileName = message.getChatId() + "_" + message.getId() + ".jpg";
            String localPath = copyToLocalStorage(file.local.path, "photos", fileName);
            
            if (localPath != null) {
                // Generate URL for local file
                String fileUrl = "/api/telegram/media/photos/" + fileName;
                message.setMinioPresignedUrl(fileUrl);
                log.info("Photo processed for message {}: {}", message.getId(), fileUrl);
            }
        } catch (Exception e) {
            log.error("Error processing photo for message {}", message.getId(), e);
        }
    }

    /**
     * Process video message
     */
    private void processVideoMessage(MessageInfo message) {
        try {
            TdApi.Message tdMessage = getMessageFromTelegram(message.getChatId(), message.getId());
            if (!(tdMessage.content instanceof TdApi.MessageVideo)) {
                return;
            }
            
            TdApi.MessageVideo videoMessage = (TdApi.MessageVideo) tdMessage.content;
            
            // Download video
            TdApi.File videoFile = downloadFileSync(videoMessage.video.video.id);
            if (videoFile != null && videoFile.local.isDownloadingCompleted) {
                String fileName = message.getChatId() + "_" + message.getId() + ".mp4";
                String localPath = copyToLocalStorage(videoFile.local.path, "videos", fileName);
                
                if (localPath != null) {
                    String fileUrl = "/api/telegram/media/videos/" + fileName;
                    message.setMinioPresignedUrl(fileUrl);
                }
            }
            
            // Download thumbnail
            if (videoMessage.video.thumbnail != null) {
                TdApi.File thumbFile = downloadFileSync(videoMessage.video.thumbnail.file.id);
                if (thumbFile != null && thumbFile.local.isDownloadingCompleted) {
                    String thumbName = message.getChatId() + "_" + message.getId() + "_thumb.jpg";
                    String thumbPath = copyToLocalStorage(thumbFile.local.path, "thumbnails", thumbName);
                    
                    if (thumbPath != null) {
                        String thumbUrl = "/api/telegram/media/thumbnails/" + thumbName;
                        message.setThumbnailMinioPresignedUrl(thumbUrl);
                    }
                }
            }
            
            log.info("Video processed for message {}: {}", message.getId(), message.getMinioPresignedUrl());
        } catch (Exception e) {
            log.error("Error processing video for message {}", message.getId(), e);
        }
    }

    /**
     * Process voice message
     */
    private void processVoiceMessage(MessageInfo message) {
        try {
            TdApi.Message tdMessage = getMessageFromTelegram(message.getChatId(), message.getId());
            if (!(tdMessage.content instanceof TdApi.MessageVoiceNote)) {
                return;
            }
            
            TdApi.MessageVoiceNote voiceMessage = (TdApi.MessageVoiceNote) tdMessage.content;
            
            // Download voice
            TdApi.File voiceFile = downloadFileSync(voiceMessage.voiceNote.voice.id);
            if (voiceFile == null || !voiceFile.local.isDownloadingCompleted) {
                log.warn("Failed to download voice for message {}", message.getId());
                return;
            }
            
            // Copy to local storage
            String fileName = message.getChatId() + "_" + message.getId() + ".ogg";
            String localPath = copyToLocalStorage(voiceFile.local.path, "voices", fileName);
            
            if (localPath != null) {
                String fileUrl = "/api/telegram/media/voices/" + fileName;
                message.setMinioPresignedUrl(fileUrl);
                log.info("Voice processed for message {}: {}", message.getId(), fileUrl);
            }
        } catch (Exception e) {
            log.error("Error processing voice for message {}", message.getId(), e);
        }
    }

    /**
     * Process audio message
     */
    private void processAudioMessage(MessageInfo message) {
        try {
            TdApi.Message tdMessage = getMessageFromTelegram(message.getChatId(), message.getId());
            if (!(tdMessage.content instanceof TdApi.MessageAudio)) {
                return;
            }
            
            TdApi.MessageAudio audioMessage = (TdApi.MessageAudio) tdMessage.content;
            
            // Download audio
            TdApi.File audioFile = downloadFileSync(audioMessage.audio.audio.id);
            if (audioFile == null || !audioFile.local.isDownloadingCompleted) {
                return;
            }
            
            // Copy to local storage
            String fileName = message.getChatId() + "_" + message.getId() + "_" + audioMessage.audio.fileName;
            String localPath = copyToLocalStorage(audioFile.local.path, "audios", fileName);
            
            if (localPath != null) {
                String fileUrl = "/api/telegram/media/audios/" + fileName;
                message.setMinioPresignedUrl(fileUrl);
                log.info("Audio processed for message {}: {}", message.getId(), fileUrl);
            }
        } catch (Exception e) {
            log.error("Error processing audio for message {}", message.getId(), e);
        }
    }

    /**
     * Process document message
     */
    private void processDocumentMessage(MessageInfo message) {
        try {
            TdApi.Message tdMessage = getMessageFromTelegram(message.getChatId(), message.getId());
            if (!(tdMessage.content instanceof TdApi.MessageDocument)) {
                return;
            }
            
            TdApi.MessageDocument docMessage = (TdApi.MessageDocument) tdMessage.content;
            
            // Download document
            TdApi.File docFile = downloadFileSync(docMessage.document.document.id);
            if (docFile == null || !docFile.local.isDownloadingCompleted) {
                return;
            }
            
            // Copy to local storage
            String fileName = message.getChatId() + "_" + message.getId() + "_" + docMessage.document.fileName;
            String localPath = copyToLocalStorage(docFile.local.path, "documents", fileName);
            
            if (localPath != null) {
                String fileUrl = "/api/telegram/media/documents/" + fileName;
                message.setMinioPresignedUrl(fileUrl);
                log.info("Document processed for message {}: {}", message.getId(), fileUrl);
            }
        } catch (Exception e) {
            log.error("Error processing document for message {}", message.getId(), e);
        }
    }

    /**
     * Copy file to local storage
     */
    private String copyToLocalStorage(String sourcePath, String category, String fileName) {
        try {
            // Create directory structure: media-files/{category}/
            Path categoryDir = Paths.get(MEDIA_STORAGE_PATH, category);
            if (!Files.exists(categoryDir)) {
                Files.createDirectories(categoryDir);
            }
            
            // Destination path
            Path destPath = categoryDir.resolve(fileName);
            
            // Check if file already exists
            if (Files.exists(destPath)) {
                log.debug("File already exists in local storage: {}", destPath);
                return destPath.toString();
            }
            
            // Copy file
            Path source = Paths.get(sourcePath);
            Files.copy(source, destPath);
            
            log.debug("File copied to local storage: {}", destPath);
            return destPath.toString();
        } catch (Exception e) {
            log.error("Failed to copy file to local storage: {} -> {}/{}", sourcePath, category, fileName, e);
            return null;
        }
    }

    /**
     * Download file from Telegram synchronously
     */
    private TdApi.File downloadFileSync(int fileId) {
        try {
            TdApi.DownloadFile request = new TdApi.DownloadFile();
            request.fileId = fileId;
            request.priority = 32;
            request.synchronous = true;
            
            Object result = client.send(request).get(30, java.util.concurrent.TimeUnit.SECONDS);
            if (result instanceof TdApi.File) {
                return (TdApi.File) result;
            }
            return null;
        } catch (Exception e) {
            log.error("Failed to download file {}", fileId, e);
            return null;
        }
    }

    /**
     * Get message from Telegram
     */
    private TdApi.Message getMessageFromTelegram(Long chatId, Long messageId) throws Exception {
        TdApi.GetMessage request = new TdApi.GetMessage();
        request.chatId = chatId;
        request.messageId = messageId;
        
        Object result = client.send(request).get(10, java.util.concurrent.TimeUnit.SECONDS);
        if (result instanceof TdApi.Message) {
            return (TdApi.Message) result;
        }
        throw new Exception("Failed to get message from Telegram");
    }

}
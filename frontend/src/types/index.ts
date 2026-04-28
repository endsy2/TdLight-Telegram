// API Response Types
export interface User {
  id: number
  firstName: string
  lastName: string
  username: string
  phoneNumber: string
  isBot: boolean
  isVerified: boolean
  isPremium: boolean
}

export interface Chat {
  id: number
  title: string
  type: 'PRIVATE' | 'BASIC_GROUP' | 'SUPERGROUP' | 'CHANNEL' | 'SECRET'
  username?: string
  description?: string
  memberCount?: number
  isChannel: boolean
  isSupergroup: boolean
  isVerified: boolean
  isRestricted: boolean
  canSendMessages: boolean
  photoUrl?: string
  lastMessageDate?: string
  unreadCount: number
  permissions?: ChatPermissions
  retrievedAt: string
}

export interface ChatPermissions {
  canSendMessages: boolean
  canSendMediaMessages: boolean
  canSendPolls: boolean
  canSendOtherMessages: boolean
  canAddWebPagePreviews: boolean
  canChangeInfo: boolean
  canInviteUsers: boolean
  canPinMessages: boolean
}

export interface Message {
  id: number
  chatId: number
  senderId?: number
  senderName?: string
  senderPhotoUrl?: string
  messageType: 'TEXT' | 'PHOTO' | 'VIDEO' | 'DOCUMENT' | 'AUDIO' | 'VOICE' | 'STICKER' | 'ANIMATION' | 'UNKNOWN'
  content: string
  mediaUrl?: string
  fileName?: string
  fileSize?: number
  duration?: number // Duration in seconds for video/audio/voice messages
  fileId?: number
  thumbnailFileId?: number
  thumbnailFormat?: string
  thumbnailWidth?: number
  thumbnailHeight?: number
  thumbnailLocalPath?: string
  minioUrl?: string
  minioPresignedUrl?: string
  thumbnailMinioUrl?: string
  thumbnailMinioPresignedUrl?: string
  isForwarded: boolean
  forwardedFromChatId?: number
  forwardedFromChatTitle?: string
  isReply: boolean
  replyToMessageId?: number
  messageDate: string
  receivedAt: string
}

export interface GroupInfo {
  id: number
  title: string
  isChannel: boolean
  isSupergroup: boolean
  updatedAt: string
}

export interface GroupMember {
  userId: number
  groupId: number
  firstName: string
  lastName: string
  username?: string
  phoneNumber?: string
  isBot: boolean
  isVerified: boolean
  isPremium: boolean
  isDeleted: boolean
  memberStatus: 'CREATOR' | 'ADMINISTRATOR' | 'MEMBER' | 'RESTRICTED' | 'LEFT' | 'BANNED'
  onlineStatus: string
  canSendMessages: boolean
  canSendMedia: boolean
  canInviteUsers: boolean
  canChangeInfo: boolean
  canPinMessages: boolean
  canDeleteMessages: boolean
  canBanUsers: boolean
  canRestrictMembers: boolean
  canPromoteMembers: boolean
  customTitle?: string
  updatedAt: string
}

export interface SendMessageRequest {
  content: string
  messageType?: string
  disableNotification?: boolean
  replyToMessageId?: number
}

export interface SendMessageResult {
  success: boolean
  messageId?: number
  chatId: number
  content?: string
  sentAt: string
  minioUrl?: string
  minioPresignedUrl?: string
  errorMessage?: string
  errorCode?: string
}

export interface SendVoiceRequest {
  voiceFilePath: string
  duration?: number
  waveform?: number[]
  caption?: string
  disableNotification?: boolean
  replyToMessageId?: number
}

export interface DownloadInfo {
  downloadId: string
  messageId: number
  chatId: number
  fileId: number
  fileName: string
  fileSize: number
  status: 'PENDING' | 'DOWNLOADING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
  progress: number
  downloadedBytes: number
  localPath?: string
  minioUrl?: string
  minioPresignedUrl?: string
  minioBucket?: string
  minioObjectName?: string
  startTime: string
  completedTime?: string
  createdAt: string
  updatedAt: string
  errorMessage?: string
}

export interface InviteResult {
  inviteId: string
  groupId: number
  groupTitle?: string
  status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED'
  totalCount: number
  successCount: number
  failedCount: number
  successRate: number
  startTime: string
  completedTime?: string
  failureDetails?: Record<number, string>
  errorMessage?: string
}

export interface AuthStatus {
  authenticationState: string
  isReady: boolean
  needsCode: boolean
  needsPassword: boolean
  isWaitingForPhone: boolean
  phoneNumber?: string
  timestamp: number
}

// Telegram Link Types
export interface TelegramLinkInfo {
  originalLink: string
  linkType: 'PUBLIC_CHANNEL' | 'PRIVATE_CHANNEL' | 'PUBLIC_GROUP' | 'PRIVATE_GROUP' | 'USER' | 'UNKNOWN'
  username?: string
  chatId?: number
  messageId?: number
  isValid: boolean
  errorMessage?: string
}

export interface TelegramLinkRequest {
  messageLink: string
  downloadType?: 'AUTO' | 'VIDEO' | 'PHOTO' | 'DOCUMENT' | 'ALL'
  downloadThumbnail?: boolean
}

export interface TelegramLinkDownloadResult {
  taskId: string
  status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED'
  originalLink: string
  linkInfo?: TelegramLinkInfo
  messageInfo?: Message
  downloads?: DownloadInfo[]
  totalCount: number
  successCount: number
  failedCount: number
  errorMessage?: string
  createdAt: string
  completedAt?: string
}

// Chat Access Types
export interface ChatAccessInfo {
  chatId: number
  hasAccess: boolean
  message: string
}

export interface ChatIdDebugInfo {
  originalId: number
  convertedChatId: number
  linkFormat: string
  hasAccess: boolean
  accessMessage: string
}

// Telegram Home Screen Chat List Item (matches backend ChatListItem)
export interface ChatListItem {
  // Basic chat info
  chatId: number
  title: string
  type: 'private' | 'group' | 'supergroup' | 'channel' | 'secret'
  photoUrl?: string
  
  // Last message info
  lastMessageId?: number
  lastMessageText?: string
  lastMessageType?: string
  lastMessageDate?: string
  isOutgoing?: boolean
  
  // Sender info (for groups)
  senderId?: number
  senderName?: string
  senderType?: 'user' | 'chat'
  
  // Message status (for outgoing messages)
  messageStatus?: 'sending' | 'sent' | 'delivered' | 'read' | 'failed'
  viewCount?: number
  
  // Unread info
  unreadCount: number
  unreadMentionCount?: number
  hasUnreadMention?: boolean
  isMuted?: boolean
  
  // Chat status
  isPinned?: boolean
  isMarkedAsUnread?: boolean
  pinnedOrder?: number
  
  // User/Group status
  userStatus?: 'online' | 'offline' | 'recently' | 'lastWeek' | 'lastMonth'
  lastOnline?: string
  isTyping?: boolean
  typingStatus?: string
  
  // Draft message
  draftText?: string
  draftDate?: string
  
  // Permissions and settings
  canSendMessages?: boolean
  isBlocked?: boolean
  hasScheduledMessages?: boolean
  
  // Group/Channel specific
  memberCount?: number
  onlineMemberCount?: number
  
  // Verification and premium
  isVerified?: boolean
  isPremium?: boolean
  isScam?: boolean
  isFake?: boolean
  
  // Notification settings
  hasCustomNotification?: boolean
  muteFor?: number
  
  // Position in list
  order?: number
  
  // Additional metadata
  description?: string
  username?: string
  phoneNumber?: string
}

export interface MessageGroup {
  date: string
  messages: Message[]
}

// Filter and Sort Types
export type ChatFilter = 'all' | 'groups' | 'channels' | 'private'
export type MessageFilter = 'all' | 'media' | 'files' | 'links' | 'voice'
export type SortOrder = 'newest' | 'oldest' | 'unread'

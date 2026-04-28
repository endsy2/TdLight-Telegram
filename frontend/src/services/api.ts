import axios from 'axios'
import type {
  User,
  Chat,
  ChatListItem,
  Message,
  GroupInfo,
  GroupMember,
  SendMessageRequest,
  SendMessageResult,
  SendVoiceRequest,
  DownloadInfo,
  InviteResult,
  AuthStatus,
  TelegramLinkInfo,
  TelegramLinkRequest,
  TelegramLinkDownloadResult,
  ChatAccessInfo,
  ChatIdDebugInfo,
} from '@/types'

const api = axios.create({
  baseURL: '/api/telegram',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// Request interceptor
api.interceptors.request.use(
  (config) => {
    // Add any auth tokens here if needed
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// Response interceptor
api.interceptors.response.use(
  (response) => response,
  (error) => {
    console.error('API Error:', error)
    return Promise.reject(error)
  }
)

// Auth APIs
export const authApi = {
  getStatus: () => api.get<AuthStatus>('/auth/status'),
  sendPhoneNumber: (phoneNumber: string) => api.post('/auth/phone', { phoneNumber }),
  submitCode: (code: string) => api.post('/auth/code', { code }),
  submitPassword: (password: string) => api.post('/auth/password', { password }),
  getCurrentUser: () => api.get('/auth/me'),
  logout: () => api.post('/auth/logout'),
  health: () => api.get('/auth/health'),
}

// User APIs
export const userApi = {
  getMe: () => api.get<User>('/me'),
}

// Chat APIs
export const chatApi = {
  getAllChats: (limit = 50) => api.get<Chat[]>('/chats', { params: { limit } }),
  getChatInfo: (chatId: number) => api.get<Chat>(`/chats/${chatId}`),
  searchChats: (query: string, limit = 20) => 
    api.get<Chat[]>('/chats/search', { params: { query, limit } }),
  getChatsInfo: (chatIds: number[]) => api.post<Chat[]>('/chats/batch', chatIds),
  openChat: (chatId: number) => api.post(`/chats/${chatId}/open`),
  closeChat: (chatId: number) => api.post(`/chats/${chatId}/close`),
}

// Group APIs
export const groupApi = {
  getGroups: () => api.get<GroupInfo[]>('/groups'),
  getGroupInfo: (groupId: number) => api.get<GroupInfo>(`/groups/${groupId}`),
  joinGroup: (inviteLink: string) => api.post('/join-group', { inviteLink }),
  getGroupMembers: (
    groupId: number,
    excludeAdmins = true,
    onlyActiveUsers = true,
    excludeBots = true
  ) =>
    api.get<GroupMember[]>(`/groups/${groupId}/members`, {
      params: { excludeAdmins, onlyActiveUsers, excludeBots },
    }),
  getCachedGroupMembers: (groupId: number) =>
    api.get<GroupMember[]>(`/groups/${groupId}/members/cached`),
  inviteUsers: (groupId: number, userIds: number[]) =>
    api.post<InviteResult>(`/groups/${groupId}/invite`, { userIds }),
  batchInviteUsers: (groupIds: number[], userIds: number[]) =>
    api.post('/groups/batch-invite', { groupIds, userIds }),
}

// Message APIs
export const messageApi = {
  getMessages: () => api.get<Message[]>('/messages'),
  getGroupMessages: (groupId: number) =>
    api.get<Message[]>(`/groups/${groupId}/messages`),
  getMessageHistoryByChatId: (chatId: number, limit = 20, fromMessageId?: number, offset = 0) =>
    api.get<{ messages: Message[], chatId: number, profile: Chat, fromCache: boolean, total: number }>(`/message/${chatId}/messages/history`, {
      params: { limit, fromMessageId, offset },
    }),
  getLatestMessages: (chatId: number, limit = 10) =>
    api.get(`/chats/${chatId}/messages/latest`, { params: { limit } }),
  getMessageLink: (chatId: number, messageId: number) =>
    api.get(`/chats/${chatId}/messages/${messageId}/link`),
  checkMessage: (chatId: number, messageId: number) =>
    api.get(`/chats/${chatId}/messages/${messageId}/check`),
  sendMessageToUser: (userId: number, request: SendMessageRequest) =>
    api.post<SendMessageResult>(`/users/${userId}/messages`, request),
  sendMessageToGroup: (groupId: number, request: SendMessageRequest) =>
    api.post<SendMessageResult>(`/groups/${groupId}/messages`, request),
  sendVoiceToUser: (userId: number, request: SendVoiceRequest) =>
    api.post<SendMessageResult>(`/users/${userId}/voice`, request),
  sendVoiceToGroup: (groupId: number, request: SendVoiceRequest) =>
    api.post<SendMessageResult>(`/groups/${groupId}/voice`, request),
  // Send file message to group
  sendFileToGroup: async (groupId: number, file: File, caption?: string) => {
    console.log('[API] sendFileToGroup called:', {
      groupId,
      fileName: file.name,
      fileType: file.type,
      fileSize: file.size,
      caption
    })
    
    const formData = new FormData()
    formData.append('file', file)
    if (caption) {
      formData.append('caption', caption)
    }
    
    console.log('[API] Sending to:', `/groups/${groupId}/files`)
    
    return api.post<SendMessageResult>(`/groups/${groupId}/files`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 120000, // 2 minutes for large files
    })
  },
  // Get enriched chat list (Telegram home screen format)
  // Pagination: page 1 = 100 chats, page 2 = 200 chats, page 3 = 300 chats, etc.
  getLatestHistory: (page = 1, limit?: number) => 
    api.get<ChatListItem[]>('/chat/latest-history', { 
      params: limit ? { limit } : { page } 
    }),
}

// Video APIs
export const videoApi = {
  getGroupVideos: (groupId: number) =>
    api.get<Message[]>(`/groups/${groupId}/videos`),
  getGroupVideoHistory: (groupId: number, limit = 20, fromMessageId?: number) =>
    api.get<Message[]>(`/groups/${groupId}/videos/history`, {
      params: { limit, fromMessageId },
    }),
  downloadVideo: (messageId: number, chatId: number) =>
    api.post<{ downloadId: string; status: string; fileName: string; fileSize: number; progress: number }>(
      '/videos/download',
      { messageId, chatId }
    ),
  downloadThumbnail: (messageId: number, chatId: number) =>
    api.post('/videos/thumbnail/download', { messageId, chatId }),
}

// Download APIs
export const downloadApi = {
  getDownloadStatus: (downloadId: string) =>
    api.get<DownloadInfo>(`/downloads/${downloadId}/status`),
  getAllDownloads: () => api.get<DownloadInfo[]>('/downloads'),
  cancelDownload: (downloadId: string) => api.delete(`/downloads/${downloadId}`),
}

// Invite APIs
export const inviteApi = {
  getInviteResult: (inviteId: string) => api.get<InviteResult>(`/invites/${inviteId}`),
}

// Telegram Link APIs
export const linkApi = {
  parseLink: (messageLink: string) =>
    api.post<TelegramLinkInfo>('/links/parse', { messageLink }),
  downloadFromLink: (request: TelegramLinkRequest) =>
    api.post<TelegramLinkDownloadResult>('/links/download', request),
  getLinkDownloadStatus: (taskId: string) =>
    api.get<TelegramLinkDownloadResult>(`/links/downloads/${taskId}`),
  getAllLinkDownloads: () =>
    api.get<TelegramLinkDownloadResult[]>('/links/downloads'),
}

// Chat Access APIs
export const chatAccessApi = {
  checkChatAccess: (chatId: number) =>
    api.get<ChatAccessInfo>(`/chats/${chatId}/access`),
  debugChatIdConversion: (originalId: number) =>
    api.get<ChatIdDebugInfo>(`/links/debug/${originalId}`),
}

// Health Check API
export const healthApi = {
  check: () => api.get<{ status: string; service: string; timestamp: string }>('/health'),
}

// MinIO APIs
const minioApi = axios.create({
  baseURL: '/api/minio',
  timeout: 60000,
})

export const fileApi = {
  uploadFile: (file: File, bucket = 'telegram-files') => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('bucket', bucket)
    return minioApi.post('/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
  downloadFile: (bucket: string, filename: string) =>
    minioApi.get(`/download/${bucket}/${filename}`, { responseType: 'blob' }),
  getPresignedUrl: (bucket: string, filename: string) =>
    minioApi.get(`/url/${bucket}/${filename}`),
  deleteFile: (bucket: string, filename: string) =>
    minioApi.delete(`/${bucket}/${filename}`),
  checkFileExists: (bucket: string, filename: string) =>
    minioApi.get(`/exists/${bucket}/${filename}`),
}

export default api

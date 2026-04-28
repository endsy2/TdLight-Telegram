/**
 * Chat-related constants
 */

export const CHAT_CONSTANTS = {
  MESSAGES_PER_PAGE: 25,
  CHATS_PER_PAGE: 100,
  SCROLL_THRESHOLD_TOP: 200,
  SCROLL_THRESHOLD_BOTTOM: 100,
  MAX_FILE_SIZE_MB: 2000,
  CACHE_TIME_MS: 30000,
} as const

export const MESSAGE_STATUS_ICONS = {
  sending: 'loader',
  sent: 'check',
  delivered: 'check-check',
  read: 'check-check',
  failed: 'error',
} as const

export const MEDIA_TYPES = {
  TEXT: 'TEXT',
  PHOTO: 'PHOTO',
  VIDEO: 'VIDEO',
  DOCUMENT: 'DOCUMENT',
  AUDIO: 'AUDIO',
  VOICE: 'VOICE',
  STICKER: 'STICKER',
  ANIMATION: 'ANIMATION',
  UNKNOWN: 'UNKNOWN',
} as const

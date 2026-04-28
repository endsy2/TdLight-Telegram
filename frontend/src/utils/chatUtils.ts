/**
 * Chat-related utility functions
 */

import type { ChatListItem } from '@/types'
import { Edit3 } from 'lucide-react'

export const getMessagePreview = (chat: ChatListItem) => {
  if (chat.draftText) {
    return (
      <span className="text-red-500">
        <Edit3 className="w-3 h-3 inline mr-1" />
        Draft: {chat.draftText}
      </span>
    )
  }

  if (!chat.lastMessageText) return 'No messages yet'

  const prefix = chat.isOutgoing ? 'You: ' : (chat.senderName ? `${chat.senderName}: ` : '')
  
  // Add emoji for different message types
  const typeEmoji: Record<string, string> = {
    photo: '📷',
    video: '🎥',
    voice: '🎤',
    video_note: '📹',
    document: '📄',
    audio: '🎵',
    sticker: '🎨',
    animation: '🎬',
    location: '📍',
    contact: '👤',
    poll: '📊',
    call: '📞',
  }

  const emoji = chat.lastMessageType && typeEmoji[chat.lastMessageType] ? typeEmoji[chat.lastMessageType] + ' ' : ''
  
  return (
    <>
      {prefix}{emoji}{chat.lastMessageText}
    </>
  )
}

export const getUserStatusColor = (status?: string): string => {
  return status === 'online' ? 'bg-green-500' : 'bg-gray-300'
}

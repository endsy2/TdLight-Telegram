import { X } from 'lucide-react'
import type { Message } from '@/types'

interface MessageReplyProps {
  message: Message
  onClose: () => void
}

export default function MessageReply({ message, onClose }: MessageReplyProps) {
  const getPreviewText = () => {
    if (message.messageType === 'TEXT') {
      return message.content
    }
    const typeEmojis: Record<string, string> = {
      PHOTO: '📷 Photo',
      VIDEO: '🎥 Video',
      VOICE: '🎤 Voice message',
      AUDIO: '🎵 Audio',
      DOCUMENT: '📄 Document',
      STICKER: '🎨 Sticker',
      ANIMATION: '🎬 GIF'
    }
    return typeEmojis[message.messageType] || message.content
  }

  return (
    <div className="bg-gray-100 dark:bg-gray-800 border-l-4 border-blue-500 p-3 rounded-lg flex items-start gap-3">
      <div className="flex-1 min-w-0">
        <p className="text-sm font-semibold text-blue-600 dark:text-blue-400">
          {message.senderName || 'User'}
        </p>
        <p className="text-sm text-gray-600 dark:text-gray-300 truncate">
          {getPreviewText()}
        </p>
      </div>
      <button
        onClick={onClose}
        className="p-1 hover:bg-gray-200 dark:hover:bg-gray-700 rounded-full transition-colors"
        aria-label="Cancel reply"
      >
        <X className="w-4 h-4 text-gray-500" />
      </button>
    </div>
  )
}

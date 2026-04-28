import { CheckCheck } from 'lucide-react'
import { formatTime } from '@/utils/dateUtils'
import UserAvatar from './UserAvatar'
import type { Message } from '@/types'

interface MessageBubbleProps {
  message: Message
  isOwnMessage: boolean
  onContextMenu: (e: React.MouseEvent, message: Message) => void
  children: React.ReactNode
}

export default function MessageBubble({ 
  message, 
  isOwnMessage, 
  onContextMenu,
  children 
}: MessageBubbleProps) {
  return (
    <div className={`flex gap-2 ${isOwnMessage ? 'justify-end' : 'justify-start'}`}>
      {/* User avatar for incoming messages */}
      {!isOwnMessage && (
        <UserAvatar
          photoUrl={message.senderPhotoUrl}
          name={message.senderName || 'User'}
          size="sm"
        />
      )}
      
      <div
        onContextMenu={(e) => onContextMenu(e, message)}
        className={`max-w-md px-4 py-2 rounded-2xl ${
          isOwnMessage
            ? 'bg-blue-500 text-white'
            : 'bg-white text-gray-900 shadow-sm'
        }`}
      >
        {!isOwnMessage && message.messageType !== 'STICKER' && (
          <p className="text-xs font-semibold mb-1 text-blue-600">
            {message.senderName || `User ${message.senderId || 'Unknown'}`}
          </p>
        )}
        
        {/* Reply indicator */}
        {message.isReply && message.replyToMessageId && (
          <div className="mb-2 pl-2 border-l-2 border-blue-500 text-xs opacity-75">
            <p>Replying to message</p>
          </div>
        )}
        
        {/* Message content */}
        {children}
        
        {/* Timestamp and status */}
        <div className="flex items-center justify-end gap-1 mt-1">
          <p className={`text-xs ${isOwnMessage ? 'text-blue-100' : 'text-gray-400'}`}>
            {formatTime(message.messageDate)}
          </p>
          {isOwnMessage && (
            <CheckCheck className="w-3 h-3 text-blue-100" />
          )}
        </div>
      </div>
    </div>
  )
}

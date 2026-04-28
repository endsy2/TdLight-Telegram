import { Pin, VolumeX, Check } from 'lucide-react'
import { formatTime } from '@/utils/dateUtils'
import { getMessagePreview, getUserStatusColor } from '@/utils/chatUtils'
import MessageStatusIcon from './MessageStatusIcon'
import type { ChatListItem as ChatListItemType } from '@/types'

interface ChatListItemProps {
  chat: ChatListItemType
  isSelected: boolean
  onClick: () => void
}

export default function ChatListItem({ chat, isSelected, onClick }: ChatListItemProps) {
  const hasUnread = (chat.unreadCount || 0) > 0

  return (
    <div
      onClick={onClick}
      className={`p-4 border-b cursor-pointer hover:bg-gray-50 transition-all duration-200 relative ${
        isSelected ? 'bg-blue-50' : ''
      } ${hasUnread ? 'bg-blue-50/30' : ''}`}
    >
      {/* Pinned indicator */}
      {chat.isPinned && (
        <div className="absolute top-2 right-2">
          <Pin className="w-3 h-3 text-gray-400" />
        </div>
      )}

      <div className="flex items-start gap-3">
        {/* Avatar with online status */}
        <div className="relative flex-shrink-0">
          <div className="w-12 h-12 rounded-full bg-gradient-to-br from-blue-400 to-blue-600 flex items-center justify-center text-white font-semibold">
            {chat.title.charAt(0).toUpperCase()}
          </div>
          {/* Online status indicator */}
          {chat.type === 'private' && chat.userStatus === 'online' && (
            <div className={`absolute bottom-0 right-0 w-3 h-3 ${getUserStatusColor(chat.userStatus)} border-2 border-white rounded-full`} />
          )}
          {/* Verification badge */}
          {chat.isVerified && (
            <div className="absolute -bottom-1 -right-1 w-4 h-4 bg-blue-500 rounded-full flex items-center justify-center">
              <Check className="w-2.5 h-2.5 text-white" />
            </div>
          )}
        </div>

        <div className="flex-1 min-w-0">
          {/* Title and time */}
          <div className="flex items-baseline justify-between mb-1">
            <div className="flex items-center gap-1 flex-1 min-w-0">
              <h3 className={`font-semibold text-gray-900 truncate ${hasUnread ? 'font-bold' : ''}`}>
                {chat.title}
              </h3>
              {/* Premium badge */}
              {chat.isPremium && (
                <span className="text-xs text-purple-500">⭐</span>
              )}
              {/* Scam/Fake warning */}
              {(chat.isScam || chat.isFake) && (
                <span className="text-xs text-red-500 font-semibold">!</span>
              )}
              {/* Mute icon */}
              {chat.isMuted && (
                <VolumeX className="w-3 h-3 text-gray-400" />
              )}
            </div>
            {chat.lastMessageDate && (
              <span className={`text-xs ml-2 flex-shrink-0 ${hasUnread ? 'text-blue-600 font-semibold' : 'text-gray-500'}`}>
                {formatTime(chat.lastMessageDate)}
              </span>
            )}
          </div>

          {/* Message preview and status */}
          <div className="flex items-center justify-between gap-2">
            <div className="flex items-center gap-1 flex-1 min-w-0">
              {/* Message status for outgoing messages */}
              {chat.isOutgoing && chat.messageStatus && (
                <span className="text-blue-500 flex-shrink-0">
                  <MessageStatusIcon status={chat.messageStatus} />
                </span>
              )}
              {/* Typing indicator */}
              {chat.isTyping ? (
                <span className="text-sm text-blue-500 italic">typing...</span>
              ) : (
                <p className={`text-sm truncate ${hasUnread ? 'text-gray-900 font-medium' : 'text-gray-500'}`}>
                  {getMessagePreview(chat)}
                </p>
              )}
            </div>

            {/* Unread badge and mentions */}
            <div className="flex items-center gap-1 flex-shrink-0">
              {chat.hasUnreadMention && (
                <span className="bg-green-500 text-white text-xs px-1.5 py-0.5 rounded-full">
                  @
                </span>
              )}
              {hasUnread && !chat.isMuted && (
                <span className="bg-blue-500 text-white text-xs px-2 py-0.5 rounded-full min-w-[20px] text-center">
                  {chat.unreadCount}
                </span>
              )}
              {hasUnread && chat.isMuted && (
                <span className="bg-gray-400 text-white text-xs px-2 py-0.5 rounded-full min-w-[20px] text-center">
                  {chat.unreadCount}
                </span>
              )}
              {/* View count for channels */}
              {chat.viewCount && chat.type === 'channel' && (
                <span className="text-xs text-gray-400">
                  {chat.viewCount > 1000 ? `${(chat.viewCount / 1000).toFixed(1)}K` : chat.viewCount} views
                </span>
              )}
            </div>
          </div>

          {/* Additional info for groups */}
          {(chat.type === 'group' || chat.type === 'supergroup') && chat.memberCount && (
            <p className="text-xs text-gray-400 mt-0.5">
              {chat.memberCount} members
              {chat.onlineMemberCount ? `, ${chat.onlineMemberCount} online` : ''}
            </p>
          )}
        </div>
      </div>
    </div>
  )
}

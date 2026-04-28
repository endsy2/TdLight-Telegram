import { useNavigate } from 'react-router-dom'
import { Search, Phone, MoreVertical, ArrowLeft } from 'lucide-react'
import UserAvatar from './UserAvatar'
import type { Chat } from '@/types'

interface ChatHeaderProps {
  chat: Chat
  onSearchClick?: () => void
  onCallClick?: () => void
  onMenuClick?: () => void
}

export default function ChatHeader({ 
  chat, 
  onSearchClick, 
  onCallClick, 
  onMenuClick 
}: ChatHeaderProps) {
  const navigate = useNavigate()

  return (
    <div className="bg-white border-b px-6 py-3 flex items-center justify-between">
      <div className="flex items-center gap-3">
        <button
          onClick={() => navigate('/chats')}
          className="lg:hidden p-2 hover:bg-gray-100 rounded-full"
        >
          <ArrowLeft className="w-5 h-5" />
        </button>
        
        <UserAvatar
          photoUrl={chat.photoUrl}
          name={chat.title}
          size="md"
          showOnlineStatus={chat.type === 'PRIVATE'}
          isOnline={true}
        />
        
        <div>
          <h2 className="font-semibold text-gray-900">{chat.title}</h2>
          <p className="text-sm text-gray-500">
            {chat.memberCount ? `${chat.memberCount} members` : 'Private chat'}
          </p>
        </div>
      </div>
      
      <div className="flex items-center gap-2">
        <button 
          onClick={onSearchClick}
          className="p-2 hover:bg-gray-100 rounded-full"
        >
          <Search className="w-5 h-5 text-gray-600" />
        </button>
        <button 
          onClick={onCallClick}
          className="p-2 hover:bg-gray-100 rounded-full"
        >
          <Phone className="w-5 h-5 text-gray-600" />
        </button>
        <button 
          onClick={onMenuClick}
          className="p-2 hover:bg-gray-100 rounded-full"
        >
          <MoreVertical className="w-5 h-5 text-gray-600" />
        </button>
      </div>
    </div>
  )
}

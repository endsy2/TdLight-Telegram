import type { Message } from '@/types'

interface PhotoMessageProps {
  message: Message
  isOwnMessage: boolean
  onImageClick: (message: Message) => void
}

export default function PhotoMessage({ message, isOwnMessage, onImageClick }: PhotoMessageProps) {
  return (
    <div className={`${isOwnMessage ? 'bg-blue-500' : 'bg-white'} rounded-2xl overflow-hidden`}>
      <div className="relative">
        <img 
          src={message.minioPresignedUrl || message.thumbnailMinioPresignedUrl} 
          alt="Photo"
          className="w-full cursor-pointer hover:opacity-90 transition-opacity"
          style={{ maxWidth: '400px', maxHeight: '400px', objectFit: 'cover' }}
          onClick={() => onImageClick(message)}
        />
        {message.content && (
          <div className={`px-3 py-2 ${isOwnMessage ? 'text-white' : 'text-gray-900'}`}>
            <p className="text-sm whitespace-pre-wrap">{message.content}</p>
          </div>
        )}
      </div>
    </div>
  )
}

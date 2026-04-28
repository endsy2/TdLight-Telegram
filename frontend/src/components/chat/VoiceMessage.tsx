import { formatDuration } from '@/utils/dateUtils'
import type { Message } from '@/types'

interface VoiceMessageProps {
  message: Message
  isOwnMessage: boolean
  isPlaying: boolean
  onPlayPause: (messageId: number, audioUrl: string) => void
  audioRef: (el: HTMLAudioElement | null) => void
}

export default function VoiceMessage({ 
  message, 
  isOwnMessage, 
  isPlaying, 
  onPlayPause,
  audioRef 
}: VoiceMessageProps) {
  if (!message.minioPresignedUrl) {
    return (
      <div className={`flex items-center gap-3 rounded-2xl p-3 ${
        isOwnMessage ? 'bg-white/10' : 'bg-gray-50'
      }`}>
        <div className={`w-10 h-10 rounded-full flex items-center justify-center ${
          isOwnMessage ? 'bg-white/20' : 'bg-blue-100'
        }`}>
          <span className="text-xl">🎤</span>
        </div>
        <div className="flex-1">
          <p className={`text-sm font-medium ${isOwnMessage ? 'text-white' : 'text-gray-900'}`}>
            Voice Message
          </p>
          <p className={`text-xs ${isOwnMessage ? 'text-white/70' : 'text-gray-500'}`}>
            {message.duration ? formatDuration(message.duration) : 'Loading...'}
          </p>
        </div>
      </div>
    )
  }

  return (
    <div className="min-w-[250px]">
      <div className={`flex items-center gap-2 rounded-2xl p-2 ${
        isOwnMessage ? 'bg-white/10' : 'bg-gray-50'
      }`}>
        <button
          onClick={() => onPlayPause(message.id, message.minioPresignedUrl!)}
          className={`w-10 h-10 rounded-full flex items-center justify-center flex-shrink-0 transition-colors ${
            isOwnMessage 
              ? 'bg-white/20 hover:bg-white/30' 
              : 'bg-blue-500 hover:bg-blue-600'
          }`}
        >
          {isPlaying ? (
            <svg className={`w-5 h-5 ${isOwnMessage ? 'text-white' : 'text-white'}`} fill="currentColor" viewBox="0 0 24 24">
              <path d="M6 4h4v16H6V4zm8 0h4v16h-4V4z"/>
            </svg>
          ) : (
            <svg className={`w-5 h-5 ${isOwnMessage ? 'text-white' : 'text-white'}`} fill="currentColor" viewBox="0 0 24 24">
              <path d="M8 5v14l11-7z"/>
            </svg>
          )}
        </button>
        
        <div className="flex-1 flex items-center gap-0.5 h-8">
          {[...Array(30)].map((_, i) => {
            const height = Math.random() * 60 + 40
            return (
              <div
                key={i}
                className={`flex-1 rounded-full transition-all ${
                  isOwnMessage ? 'bg-white/40' : 'bg-blue-300'
                }`}
                style={{ 
                  height: `${height}%`,
                  minWidth: '2px',
                  maxWidth: '3px'
                }}
              />
            )
          })}
        </div>
        
        <span className={`text-xs flex-shrink-0 ${
          isOwnMessage ? 'text-white/80' : 'text-gray-500'
        }`}>
          {message.duration ? formatDuration(message.duration) : '0:00'}
        </span>
        
        <audio
          ref={audioRef}
          preload="metadata"
          onEnded={() => {}}
          className="hidden"
        />
      </div>
    </div>
  )
}

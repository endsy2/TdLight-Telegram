interface UserAvatarProps {
  photoUrl?: string
  name: string
  size?: 'sm' | 'md' | 'lg'
  showOnlineStatus?: boolean
  isOnline?: boolean
}

const sizeClasses = {
  sm: 'w-8 h-8 text-xs',
  md: 'w-10 h-10 text-sm',
  lg: 'w-12 h-12 text-base',
}

export default function UserAvatar({ 
  photoUrl, 
  name, 
  size = 'md',
  showOnlineStatus = false,
  isOnline = false 
}: UserAvatarProps) {
  const sizeClass = sizeClasses[size]
  
  return (
    <div className="relative flex-shrink-0">
      {photoUrl ? (
        <img 
          src={photoUrl} 
          alt={name}
          className={`${sizeClass} rounded-full object-cover`}
        />
      ) : (
        <div className={`${sizeClass} rounded-full bg-gradient-to-br from-purple-400 to-purple-600 flex items-center justify-center text-white font-semibold`}>
          {name.charAt(0).toUpperCase()}
        </div>
      )}
      {showOnlineStatus && isOnline && (
        <div className="absolute bottom-0 right-0 w-3 h-3 bg-green-500 border-2 border-white rounded-full" />
      )}
    </div>
  )
}

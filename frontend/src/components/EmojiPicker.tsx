import { useState, useRef, useEffect } from 'react'
import { Smile } from 'lucide-react'

interface EmojiPickerProps {
  onEmojiSelect: (emoji: string) => void
}

const EMOJI_CATEGORIES = {
  'Smileys': ['😀', '😃', '😄', '😁', '😅', '😂', '🤣', '😊', '😇', '🙂', '🙃', '😉', '😌', '😍', '🥰', '😘', '😗', '😙', '😚', '😋', '😛', '😝', '😜', '🤪', '🤨', '🧐', '🤓', '😎', '🥸', '🤩', '🥳'],
  'Gestures': ['👍', '👎', '👌', '✌️', '🤞', '🤟', '🤘', '🤙', '👈', '👉', '👆', '👇', '☝️', '👏', '🙌', '👐', '🤲', '🤝', '🙏'],
  'Hearts': ['❤️', '🧡', '💛', '💚', '💙', '💜', '🖤', '🤍', '🤎', '💔', '❤️‍🔥', '❤️‍🩹', '💕', '💞', '💓', '💗', '💖', '💘', '💝'],
  'Objects': ['🎉', '🎊', '🎈', '🎁', '🏆', '🥇', '🥈', '🥉', '⚽', '🏀', '🏈', '⚾', '🎾', '🏐', '🏉', '🎱', '🎮', '🎯', '🎲', '🎰'],
  'Symbols': ['✅', '❌', '⭐', '🌟', '💫', '✨', '🔥', '💯', '💢', '💥', '💨', '💦', '💤', '🕐', '⏰', '⏱️', '⏲️', '🔔', '🔕']
}

const RECENT_EMOJIS_KEY = 'recent_emojis'

export default function EmojiPicker({ onEmojiSelect }: EmojiPickerProps) {
  const [isOpen, setIsOpen] = useState(false)
  const [activeCategory, setActiveCategory] = useState('Smileys')
  const [recentEmojis, setRecentEmojis] = useState<string[]>([])
  const pickerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    // Load recent emojis from localStorage
    const stored = localStorage.getItem(RECENT_EMOJIS_KEY)
    if (stored) {
      setRecentEmojis(JSON.parse(stored))
    }
  }, [])

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (pickerRef.current && !pickerRef.current.contains(event.target as Node)) {
        setIsOpen(false)
      }
    }

    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside)
    }

    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
    }
  }, [isOpen])

  const handleEmojiClick = (emoji: string) => {
    onEmojiSelect(emoji)
    
    // Update recent emojis
    const updated = [emoji, ...recentEmojis.filter(e => e !== emoji)].slice(0, 20)
    setRecentEmojis(updated)
    localStorage.setItem(RECENT_EMOJIS_KEY, JSON.stringify(updated))
    
    setIsOpen(false)
  }

  return (
    <div className="relative" ref={pickerRef}>
      <button
        type="button"
        onClick={() => setIsOpen(!isOpen)}
        className="p-2 text-gray-500 hover:text-gray-700 hover:bg-gray-100 rounded-full transition-colors"
        aria-label="Emoji picker"
      >
        <Smile className="w-5 h-5" />
      </button>

      {isOpen && (
        <div className="absolute bottom-full right-0 mb-2 bg-white dark:bg-gray-800 rounded-lg shadow-xl border border-gray-200 dark:border-gray-700 w-80 max-h-96 overflow-hidden z-50">
          {/* Category tabs */}
          <div className="flex gap-1 p-2 border-b border-gray-200 dark:border-gray-700 overflow-x-auto">
            {recentEmojis.length > 0 && (
              <button
                onClick={() => setActiveCategory('Recent')}
                className={`px-3 py-1 text-xs rounded-full whitespace-nowrap ${
                  activeCategory === 'Recent'
                    ? 'bg-blue-500 text-white'
                    : 'bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300'
                }`}
              >
                Recent
              </button>
            )}
            {Object.keys(EMOJI_CATEGORIES).map(category => (
              <button
                key={category}
                onClick={() => setActiveCategory(category)}
                className={`px-3 py-1 text-xs rounded-full whitespace-nowrap ${
                  activeCategory === category
                    ? 'bg-blue-500 text-white'
                    : 'bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300'
                }`}
              >
                {category}
              </button>
            ))}
          </div>

          {/* Emoji grid */}
          <div className="p-2 overflow-y-auto max-h-64">
            <div className="grid grid-cols-8 gap-1">
              {(activeCategory === 'Recent' ? recentEmojis : EMOJI_CATEGORIES[activeCategory as keyof typeof EMOJI_CATEGORIES] || []).map((emoji, index) => (
                <button
                  key={`${emoji}-${index}`}
                  onClick={() => handleEmojiClick(emoji)}
                  className="text-2xl p-2 hover:bg-gray-100 dark:hover:bg-gray-700 rounded transition-colors"
                  title={emoji}
                >
                  {emoji}
                </button>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

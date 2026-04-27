import { useState, useEffect, useRef, useCallback } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { 
  Send, 
  Paperclip, 
  MoreVertical, 
  Search, 
  Phone, 
  ArrowLeft, 
  Loader,
  Check,
  CheckCheck,
  Pin,
  VolumeX,
  Edit3,
  Reply,
  Copy,
  Forward,
  Trash2
} from 'lucide-react'
import { chatApi, messageApi } from '@/services/api'
import { useStore } from '@/store/useStore'
import { useChatsSubscription, useChatSubscription, useTypingIndicator } from '@/hooks/useWebSocket'
import toast from 'react-hot-toast'
import type { Message, ChatListItem } from '@/types'

// Import new components
import MessageReply from '@/components/MessageReply'
import EmojiPicker from '@/components/EmojiPicker'
import VoiceRecorder from '@/components/VoiceRecorder'
import ImageGallery from '@/components/ImageGallery'

export default function ChatPage() {
  const { chatId } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const currentUser = useStore((state: any) => state.currentUser)
  
  const [messageText, setMessageText] = useState('')
  const [searchQuery, setSearchQuery] = useState('')
  const [currentPage, setCurrentPage] = useState(1)
  const [allChats, setAllChats] = useState<ChatListItem[]>([])
  const [isLoadingMore, setIsLoadingMore] = useState(false)
  const [hasMoreChats, setHasMoreChats] = useState(true)
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [isUploading, setIsUploading] = useState(false)
  
  // Message pagination state (Telegram-style with fromMessageId cursor)
  const [allMessages, setAllMessages] = useState<Message[]>([])
  const [isLoadingOlderMessages, setIsLoadingOlderMessages] = useState(false)
  const [isLoadingNewerMessages, setIsLoadingNewerMessages] = useState(false)
  const [hasMoreOlderMessages, setHasMoreOlderMessages] = useState(true)
  const [hasMoreNewerMessages, setHasMoreNewerMessages] = useState(false)
  const [oldestMessageId, setOldestMessageId] = useState<number | null>(null)
  const [newestMessageId, setNewestMessageId] = useState<number | null>(null)
  const [showScrollToBottom, setShowScrollToBottom] = useState(false)
  const [isAtBottom, setIsAtBottom] = useState(true)
  const [initialLoadComplete, setInitialLoadComplete] = useState(false)
  
  // Enhanced features state
  const [replyingTo, setReplyingTo] = useState<Message | null>(null)
  const [isDragging, setIsDragging] = useState(false)
  const [galleryOpen, setGalleryOpen] = useState(false)
  const [galleryImages, setGalleryImages] = useState<Message[]>([])
  const [galleryIndex, setGalleryIndex] = useState(0)
  const [contextMenu, setContextMenu] = useState<{
    message: Message
    x: number
    y: number
  } | null>(null)
  
  // Voice message state
  const [playingVoiceId, setPlayingVoiceId] = useState<number | null>(null)
  const audioRefs = useRef<Map<number, HTMLAudioElement>>(new Map())
  
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const messagesContainerRef = useRef<HTMLDivElement>(null)
  const chatListRef = useRef<HTMLDivElement>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const previousScrollHeightRef = useRef<number>(0)

  // Helper function to download media files
  const handleDownload = async (url: string, fileName: string) => {
    try {
      const response = await fetch(url)
      const blob = await response.blob()
      const downloadUrl = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = downloadUrl
      link.download = fileName || 'download'
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      window.URL.revokeObjectURL(downloadUrl)
      toast.success('Download started!')
    } catch (error) {
      console.error('Download failed:', error)
      toast.error('Failed to download file')
    }
  }

  // Handle voice message play/pause
  const handleVoicePlayPause = (messageId: number, audioUrl: string) => {
    const audio = audioRefs.current.get(messageId)
    
    if (playingVoiceId === messageId && audio && !audio.paused) {
      // Pause current
      audio.pause()
      setPlayingVoiceId(null)
    } else {
      // Stop any currently playing audio
      if (playingVoiceId !== null) {
        const currentAudio = audioRefs.current.get(playingVoiceId)
        if (currentAudio) {
          currentAudio.pause()
          currentAudio.currentTime = 0
        }
      }
      
      // Play new audio
      if (!audio) {
        const newAudio = new Audio(audioUrl)
        audioRefs.current.set(messageId, newAudio)
        newAudio.addEventListener('ended', () => {
          setPlayingVoiceId(null)
        })
        newAudio.play()
        setPlayingVoiceId(messageId)
      } else {
        audio.play()
        setPlayingVoiceId(messageId)
      }
    }
  }

  // Format duration for voice messages
  const formatDuration = (seconds: number) => {
    const mins = Math.floor(seconds / 60)
    const secs = Math.floor(seconds % 60)
    return `${mins}:${secs.toString().padStart(2, '0')}`
  }

  // Fetch enriched chats list (Telegram home screen format)
  // Page 1 = 100 chats, Page 2 = 200 chats, Page 3 = 300 chats
  const { data: chats, isLoading: chatsLoading, isFetching } = useQuery({
    queryKey: ['chats', currentPage],
    queryFn: async () => {
      const response = await messageApi.getLatestHistory(currentPage)
      return response.data
    },
    staleTime: 30000, // Keep data fresh for 30 seconds
  })

  // Update allChats when new data arrives and preserve scroll position
  useEffect(() => {
    if (chats && chats.length > 0) {
      // Check if we got a full page (100 new chats)
      const expectedChats = currentPage * 100
      const gotFullPage = chats.length >= expectedChats
      
      // If we didn't get a full page, there are no more chats to load
      if (!gotFullPage && currentPage > 1) {
        setHasMoreChats(false)
      }
      
      // Only update if we have more chats than before
      if (chats.length > allChats.length) {
        // Store current scroll position before update
        const scrollElement = chatListRef.current
        if (scrollElement && currentPage > 1) {
          const scrollTop = scrollElement.scrollTop
          const scrollHeight = scrollElement.scrollHeight

          setAllChats(chats)

          // Restore scroll position after DOM update
          requestAnimationFrame(() => {
            if (scrollElement) {
              const newScrollHeight = scrollElement.scrollHeight
              const heightDiff = newScrollHeight - scrollHeight
              scrollElement.scrollTop = scrollTop + heightDiff
            }
          })
        } else {
          // First page, just set the chats
          setAllChats(chats)
        }
      } else if (currentPage === 1) {
        // Reset for first page
        setAllChats(chats)
        setHasMoreChats(true)
      }
      
      setIsLoadingMore(false)
    }
  }, [chats, currentPage, allChats.length])

  // Subscribe to chats updates via WebSocket
  useChatsSubscription(
    useCallback((event) => {
      console.log('[WebSocket] Chats event:', event)
      if (event.type === 'CHAT_UPDATED') {
        // Invalidate all chat pages to get fresh data (including unread counts)
        queryClient.invalidateQueries({ queryKey: ['chats'] })
      }
    }, [queryClient])
  )

  // Subscribe to specific chat messages via WebSocket
  useChatSubscription(
    chatId ? Number(chatId) : null,
    useCallback((event) => {
      console.log('[WebSocket] Chat event:', event)
      if (event.type === 'NEW_MESSAGE') {
        // Add new message to the END of the list (bottom)
        setAllMessages(prev => {
          // Check if message already exists to avoid duplicates
          const exists = prev.some((msg: Message) => msg.id === event.data.id)
          if (exists) return prev
          // Add new message at the end
          return [...prev, event.data]
        })
        // Update newest message ID
        setNewestMessageId(event.data.id)
        // Note: We don't invalidate queries here because:
        // 1. We already added the message to the state above
        // 2. Invalidating would cause unnecessary refetch of 50 messages
        // 3. The chat list will update via the chats WebSocket subscription
      }
    }, [chatId])
  )

  // Typing indicator
  const sendTyping = useTypingIndicator(chatId ? Number(chatId) : null)

  // Fetch selected chat info
  const { data: selectedChat } = useQuery({
    queryKey: ['chat', chatId],
    queryFn: async () => {
      if (!chatId) return null
      const response = await chatApi.getChatInfo(Number(chatId))
      return response.data
    },
    enabled: !!chatId,
  })

  // Fetch messages for selected chat (initial load only)
  const { data: rawMessages, isLoading: messagesLoading } = useQuery({
    queryKey: ['messages', chatId, 'initial'],
    queryFn: async () => {
      if (!chatId) return []
      // Load initial 25 messages (no fromMessageId = get latest)
      const response = await messageApi.getMessageHistoryByChatId(Number(chatId), 25)
      return response.data
    },
    enabled: !!chatId && !initialLoadComplete,
  })

  // Initialize messages when first loaded
  useEffect(() => {
    console.log('[INIT] Raw messages received:', {
      hasRawMessages: !!rawMessages,
      rawMessagesLength: rawMessages?.length || 0,
      initialLoadComplete
    })
    
    if (rawMessages && rawMessages.length > 0 && !initialLoadComplete) {
      // Sort messages in chronological order (oldest first, newest last)
      const sorted = [...rawMessages].sort((a, b) => {
        const dateA = new Date(a.messageDate).getTime()
        const dateB = new Date(b.messageDate).getTime()
        return dateA - dateB
      })
      
      // ALWAYS set hasMoreOlderMessages to true initially
      // Let the first loadOlderMessages call determine if there are actually more
      // This ensures pagination works even if initial load returns any number of messages
      const mightHaveMore = true
      
      console.log('[INIT] Setting initial state:', {
        sortedLength: sorted.length,
        oldestMessageId: sorted[0].id,
        newestMessageId: sorted[sorted.length - 1].id,
        hasMoreOlderMessages: mightHaveMore,
        hasMoreNewerMessages: false,
        reasoning: 'Always TRUE initially - let API tell us if no more'
      })
      
      setAllMessages(sorted)
      setOldestMessageId(sorted[0].id)
      setNewestMessageId(sorted[sorted.length - 1].id)
      setInitialLoadComplete(true)
      setHasMoreOlderMessages(mightHaveMore)
      setHasMoreNewerMessages(false) // Initially at latest, so no newer messages
      
      console.log('[INIT] ✅ Initialization complete! hasMoreOlderMessages =', mightHaveMore)
    }
  }, [rawMessages, initialLoadComplete])

  // Use allMessages for rendering
  const messages = allMessages

  // Reset message pagination when chat changes
  useEffect(() => {
    if (chatId) {
      console.log('[RESET] Chat changed to:', chatId)
      console.log('[RESET] Resetting all pagination state...')
      
      setAllMessages([])
      setHasMoreOlderMessages(true)
      setHasMoreNewerMessages(false)
      setOldestMessageId(null)
      setNewestMessageId(null)
      setIsLoadingOlderMessages(false)
      setIsLoadingNewerMessages(false)
      setShowScrollToBottom(false)
      setIsAtBottom(true)
      setInitialLoadComplete(false)
      
      console.log('[RESET] ✅ Reset complete! hasMoreOlderMessages reset to TRUE')
      
      // Open the chat to mark messages as read
      chatApi.openChat(Number(chatId))
        .then(() => {
          console.log(`[OPEN_CHAT] ✅ Chat ${chatId} opened - messages will be marked as read`)
        })
        .catch(error => {
          console.error(`[OPEN_CHAT] ❌ Failed to open chat ${chatId}:`, error)
        })
      
      // Close the previous chat when component unmounts or chat changes
      return () => {
        console.log(`[CLOSE_CHAT] Closing chat ${chatId}`)
        chatApi.closeChat(Number(chatId))
          .then(() => {
            console.log(`[CLOSE_CHAT] ✅ Chat ${chatId} closed`)
          })
          .catch(error => {
            console.error(`[CLOSE_CHAT] ❌ Failed to close chat ${chatId}:`, error)
          })
      }
    }
  }, [chatId])

  // Load older messages function (using fromMessageId for pagination)
  const loadOlderMessages = async () => {
    console.log('[loadOlderMessages] Called with:', {
      chatId,
      oldestMessageId,
      isLoadingOlderMessages,
      hasMoreOlderMessages,
      currentMessagesCount: allMessages.length
    })
    
    if (!chatId || !oldestMessageId || isLoadingOlderMessages) {
      console.log('[loadOlderMessages] Skipping - missing requirements')
      return
    }
    
    setIsLoadingOlderMessages(true)
    try {
      console.log('[loadOlderMessages] Fetching from messageId:', oldestMessageId)
      
      // Use fromMessageId for pagination (no offset needed)
      const response = await messageApi.getMessageHistoryByChatId(
        Number(chatId),
        25,
        oldestMessageId // This is the pagination cursor
      )
      
      console.log('[loadOlderMessages] Response:', {
        messageCount: response.data?.length || 0,
        firstMessageId: response.data?.[0]?.id,
        lastMessageId: response.data?.[response.data.length - 1]?.id
      })
      
      if (response.data && response.data.length > 0) {
        // Sort new messages
        const sorted = [...response.data].sort((a, b) => {
          const dateA = new Date(a.messageDate).getTime()
          const dateB = new Date(b.messageDate).getTime()
          return dateA - dateB
        })
        
        console.log('[loadOlderMessages] Sorted messages:', {
          count: sorted.length,
          oldestId: sorted[0].id,
          newestId: sorted[sorted.length - 1].id
        })
        
        // Prepend older messages, filtering out duplicates
        setAllMessages(prev => {
          const existingIds = new Set(prev.map(m => m.id))
          const newMessages = sorted.filter(m => !existingIds.has(m.id))
          
          console.log('[loadOlderMessages] Duplicate check:', {
            receivedCount: sorted.length,
            duplicatesFound: sorted.length - newMessages.length,
            newMessagesCount: newMessages.length
          })
          
          return [...newMessages, ...prev]
        })
        
        setOldestMessageId(sorted[0].id) // Update to new oldest
        
        // IMPORTANT: Don't set hasMoreOlderMessages to false just because we got < 100
        // Only set it to false when we get 0 messages
        // There might be more messages even if this batch was small
        console.log('[loadOlderMessages] ✅ Loaded', sorted.length, 'messages. Keeping hasMoreOlderMessages = true')
        
        // If we loaded older messages, there might be newer ones now
        if (!hasMoreNewerMessages && sorted.length > 0) {
          setHasMoreNewerMessages(true)
        }
      } else {
        // Only set to false when we get ZERO messages
        setHasMoreOlderMessages(false)
        console.log('[loadOlderMessages] ❌ No messages returned - no more older messages')
      }
    } catch (error) {
      console.error('[loadOlderMessages] Failed:', error)
    } finally {
      setIsLoadingOlderMessages(false)
    }
  }

  // Load newer messages function (get latest messages)
  const loadNewerMessages = async () => {
    if (!chatId || !newestMessageId || isLoadingNewerMessages) return
    
    setIsLoadingNewerMessages(true)
    try {
      // Fetch latest messages (no fromMessageId = get latest)
      const response = await messageApi.getMessageHistoryByChatId(
        Number(chatId),
        25
      )
      
      if (response.data && response.data.length > 0) {
        // Sort messages in chronological order
        const sorted = [...response.data].sort((a, b) => {
          const dateA = new Date(a.messageDate).getTime()
          const dateB = new Date(b.messageDate).getTime()
          return dateA - dateB
        })
        
        // Filter to only get messages newer than what we have
        const newerMessages = sorted.filter(msg => msg.id > newestMessageId)
        
        if (newerMessages.length > 0) {
          // Append newer messages to the end, filtering out duplicates
          setAllMessages(prev => {
            const existingIds = new Set(prev.map(m => m.id))
            const newMessages = newerMessages.filter(m => !existingIds.has(m.id))
            
            console.log('[loadNewerMessages] Duplicate check:', {
              receivedCount: newerMessages.length,
              duplicatesFound: newerMessages.length - newMessages.length,
              newMessagesCount: newMessages.length
            })
            
            return [...prev, ...newMessages]
          })
          
          setNewestMessageId(newerMessages[newerMessages.length - 1].id)
        } else {
          // No newer messages found, we're at the latest
          setHasMoreNewerMessages(false)
        }
        
        // If we got fewer new messages than expected, we're probably at the end
        if (newerMessages.length < 10) {
          setHasMoreNewerMessages(false)
        }
      } else {
        setHasMoreNewerMessages(false)
      }
    } catch (error) {
      console.error('Failed to load newer messages:', error)
      setHasMoreNewerMessages(false)
    } finally {
      setIsLoadingNewerMessages(false)
    }
  }

  // Send message mutation
  const sendMessageMutation = useMutation({
    mutationFn: async (content: string) => {
      if (!chatId) throw new Error('No chat selected')
      const response = await messageApi.sendMessageToGroup(Number(chatId), {
        content,
        messageType: 'TEXT',
        replyToMessageId: replyingTo?.id
      })
      return response.data
    },
    onSuccess: (data) => {
      setMessageText('')
      setReplyingTo(null)
      console.log('Message sent successfully:', data)
      
      // Note: We don't invalidate queries here because:
      // 1. The new message will arrive via WebSocket (onUpdateNewMessage)
      // 2. WebSocket will add the message to state automatically
      // 3. WebSocket will also update the chat list
      // 4. No need to refetch 50 messages
      
      // Ensure we scroll to bottom after sending
      setTimeout(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
      }, 100)
    },
    onError: (error: any) => {
      console.error('Failed to send message:', error)
      toast.error(error.response?.data?.error || 'Failed to send message')
    },
  })

  const handleSendMessage = (e: React.FormEvent) => {
    e.preventDefault()
    
    // If there's a file selected, send file with optional caption
    if (selectedFile) {
      handleFileSend()
      return
    }
    
    // Otherwise send text message
    if (messageText.trim() && !sendMessageMutation.isPending) {
      sendMessageMutation.mutate(messageText.trim())
    }
  }

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) {
      setSelectedFile(file)
      // Don't auto-send, let user add caption if they want
    }
  }

  const handleFileSend = async () => {
    if (!chatId || !selectedFile) return
    
    setIsUploading(true)
    const toastId = 'file-upload'
    
    try {
      console.log('[File Upload] Starting upload:', {
        chatId,
        fileName: selectedFile.name,
        fileType: selectedFile.type,
        fileSize: selectedFile.size,
        caption: messageText.trim() || undefined
      })
      
      toast.loading('Uploading file...', { id: toastId })
      
      // Upload file using the API
      const response = await messageApi.sendFileToGroup(
        Number(chatId),
        selectedFile,
        messageText.trim() || undefined
      )
      
      console.log('[File Upload] Success:', response.data)
      toast.success('File sent successfully!', { id: toastId })
      
      // Clear state
      setSelectedFile(null)
      setMessageText('')
      
      // Reset file input
      if (fileInputRef.current) {
        fileInputRef.current.value = ''
      }
      
      // Note: We don't invalidate queries here because:
      // 1. The new message will arrive via WebSocket
      // 2. No need to refetch 50 messages
      
      // Scroll to bottom after file is sent
      setTimeout(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
      }, 100)
    } catch (error: any) {
      console.error('[File Upload] Failed:', error)
      toast.error(error.response?.data?.error || 'Failed to upload file', { id: toastId })
    } finally {
      setIsUploading(false)
    }
  }

  // Handle voice message send
  const handleVoiceSend = async (audioBlob: Blob) => {
    if (!chatId) return
    
    try {
      const file = new File([audioBlob], `voice_${Date.now()}.ogg`, { type: 'audio/ogg' })
      
      toast.loading('Sending voice message...')
      await messageApi.sendFileToGroup(Number(chatId), file, 'Voice message')
      
      toast.success('Voice message sent!')
      
      // Note: We don't invalidate queries here because:
      // 1. The new message will arrive via WebSocket
      // 2. No need to refetch 50 messages
      
      // Scroll to bottom after voice message is sent
      setTimeout(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
      }, 100)
    } catch (error) {
      console.error('Failed to send voice message:', error)
      toast.error('Failed to send voice message')
    }
  }

  // Handle emoji select
  const handleEmojiSelect = (emoji: string) => {
    setMessageText(prev => prev + emoji)
    textareaRef.current?.focus()
  }

  // Handle image click (open gallery)
  const handleImageClick = (message: Message) => {
    const images = messages?.filter(m => m.messageType === 'PHOTO') || []
    const index = images.findIndex(m => m.id === message.id)
    setGalleryImages(images)
    setGalleryIndex(index)
    setGalleryOpen(true)
  }

  // Handle message context menu
  const handleContextMenu = (e: React.MouseEvent, message: Message) => {
    e.preventDefault()
    setContextMenu({
      message,
      x: e.clientX,
      y: e.clientY
    })
  }

  // Handle reply
  const handleReply = (message: Message) => {
    setReplyingTo(message)
    textareaRef.current?.focus()
    setContextMenu(null)
  }

  // Handle copy
  const handleCopy = (message: Message) => {
    navigator.clipboard.writeText(message.content)
    toast.success('Message copied!')
    setContextMenu(null)
  }

  // Drag and drop handlers
  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault()
    setIsDragging(true)
  }

  const handleDragLeave = () => {
    setIsDragging(false)
  }

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault()
    setIsDragging(false)
    
    const files = Array.from(e.dataTransfer.files)
    if (files.length > 0) {
      setSelectedFile(files[0])
    }
  }

  // Close context menu on click outside
  useEffect(() => {
    const handleClick = () => setContextMenu(null)
    if (contextMenu) {
      document.addEventListener('click', handleClick)
      return () => document.removeEventListener('click', handleClick)
    }
  }, [contextMenu])

  // Auto-scroll to bottom
  useEffect(() => {
    // Only auto-scroll if user is at bottom (for new messages)
    if (isAtBottom && messagesEndRef.current) {
      messagesEndRef.current.scrollIntoView({ behavior: 'smooth' })
    }
  }, [messages, isAtBottom])

  // Handle message scroll (Telegram-style: bidirectional pagination)
  const handleMessageScroll = useCallback((e: React.UIEvent<HTMLDivElement>) => {
    const element = e.currentTarget
    const scrollTop = element.scrollTop
    const scrollHeight = element.scrollHeight
    const clientHeight = element.clientHeight
    
    // Check if user is at bottom (within 100px)
    const distanceFromBottom = scrollHeight - scrollTop - clientHeight
    const atBottom = distanceFromBottom < 100
    setIsAtBottom(atBottom)
    setShowScrollToBottom(!atBottom)
    
    // Check conditions
    const condition1 = scrollTop < 200
    const condition2 = !isLoadingOlderMessages
    const condition3 = !isLoadingNewerMessages
    const condition4 = hasMoreOlderMessages
    const condition5 = !!messages
    const condition6 = messages && messages.length > 0
    const allConditionsMet = condition1 && condition2 && condition3 && condition4 && condition5 && condition6
    
    // LOG EVERY SCROLL EVENT - SIMPLIFIED
    if (scrollTop < 300) { // Only log when near top
      console.log('📍 [SCROLL] scrollTop:', scrollTop, '| ALL CONDITIONS MET:', allConditionsMet)
      console.log('   Conditions:', {
        '1. scrollTop < 200': condition1,
        '2. !isLoadingOlderMessages': condition2,
        '3. !isLoadingNewerMessages': condition3,
        '4. hasMoreOlderMessages': condition4,
        '5. messages exists': condition5,
        '6. messages.length > 0': condition6
      })
      console.log('   State:', {
        oldestMessageId,
        messagesCount: messages?.length || 0,
        hasMoreOlderMessages
      })
      
      // Special warning if hasMoreOlderMessages is false
      if (!condition4) {
        console.warn('⚠️ [PAGINATION BLOCKED] hasMoreOlderMessages is FALSE')
        console.warn('   This might be from a previous buggy load attempt.')
        console.warn('   Solution: Switch to a different chat and back, or refresh the page.')
      }
    }
    
    // Load older messages when scrolling near top (within 200px)
    if (allConditionsMet) {
      console.log('🔥🔥🔥 [TRIGGER] Loading older messages NOW!')
      
      // Store current scroll position
      previousScrollHeightRef.current = scrollHeight
      
      // Load older messages
      loadOlderMessages()
    } else if (scrollTop < 200) {
      console.log('❌ [NO TRIGGER] Conditions not met - see above for details')
    }
    
    // Load newer messages when scrolling near bottom (within 200px from bottom)
    if (
      distanceFromBottom < 200 && 
      !isLoadingOlderMessages && 
      !isLoadingNewerMessages &&
      hasMoreNewerMessages &&
      messages &&
      messages.length > 0
    ) {
      console.log('🔥 [TRIGGER] Loading newer messages!')
      
      // Store current scroll position
      previousScrollHeightRef.current = scrollHeight
      
      // Load newer messages
      loadNewerMessages()
    }
  }, [isLoadingOlderMessages, isLoadingNewerMessages, hasMoreOlderMessages, hasMoreNewerMessages, messages, loadOlderMessages, loadNewerMessages, oldestMessageId, newestMessageId])

  // Restore scroll position after loading older messages
  useEffect(() => {
    if (messagesContainerRef.current && messages && messages.length > 0) {
      const container = messagesContainerRef.current
      const previousScrollHeight = previousScrollHeightRef.current
      const currentScrollHeight = container.scrollHeight
      
      // When loading older messages, adjust scroll to maintain visual position
      if (previousScrollHeight > 0 && currentScrollHeight > previousScrollHeight) {
        const heightDifference = currentScrollHeight - previousScrollHeight
        container.scrollTop = container.scrollTop + heightDifference
        previousScrollHeightRef.current = 0 // Reset
      }
    }
  }, [messages])

  // Scroll to bottom function
  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
    setIsAtBottom(true)
    setShowScrollToBottom(false)
  }

  // Handle scroll for loading more chats (Telegram-style)
  const handleChatListScroll = useCallback((e: React.UIEvent<HTMLDivElement>) => {
    const element = e.currentTarget
    const scrollTop = element.scrollTop
    const scrollHeight = element.scrollHeight
    const clientHeight = element.clientHeight
    
    // Calculate how far from bottom (in pixels)
    const distanceFromBottom = scrollHeight - scrollTop - clientHeight
    
    // Load more when within 300px of bottom (Telegram loads early)
    if (
      distanceFromBottom < 300 && 
      !isLoadingMore && 
      !isFetching &&
      hasMoreChats
    ) {
      console.log(`Loading page ${currentPage + 1} - current chats: ${allChats.length}`)
      setIsLoadingMore(true)
      setCurrentPage(prev => prev + 1)
    }
  }, [isLoadingMore, isFetching, currentPage, allChats.length, hasMoreChats])

  const filteredChats = allChats?.filter((chat: ChatListItem) => {
    const title = chat.title || ''
    return title.toLowerCase().includes(searchQuery.toLowerCase())
  })

  const formatTime = (dateString: string | number) => {
    const date = typeof dateString === 'number' ? new Date(dateString * 1000) : new Date(dateString)
    const now = new Date()
    const diffMs = now.getTime() - date.getTime()
    const diffMins = Math.floor(diffMs / 60000)
    const diffHours = Math.floor(diffMs / 3600000)
    const diffDays = Math.floor(diffMs / 86400000)

    if (diffMins < 1) return 'now'
    if (diffMins < 60) return `${diffMins}m`
    if (diffHours < 24) return date.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' })
    if (diffDays < 7) return date.toLocaleDateString('en-US', { weekday: 'short' })
    return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
  }

  const formatDate = (dateString: string | number) => {
    const date = typeof dateString === 'number' ? new Date(dateString * 1000) : new Date(dateString)
    const today = new Date()
    const yesterday = new Date(today)
    yesterday.setDate(yesterday.getDate() - 1)

    if (date.toDateString() === today.toDateString()) {
      return 'Today'
    } else if (date.toDateString() === yesterday.toDateString()) {
      return 'Yesterday'
    } else {
      return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
    }
  }

  const getMessagePreview = (chat: ChatListItem) => {
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

  const getMessageStatusIcon = (status?: string) => {
    switch (status) {
      case 'sending':
        return <Loader className="w-3 h-3 animate-spin" />
      case 'sent':
        return <Check className="w-3 h-3" />
      case 'delivered':
      case 'read':
        return <CheckCheck className="w-3 h-3" />
      case 'failed':
        return <span className="text-red-500">!</span>
      default:
        return null
    }
  }

  const getUserStatusColor = (status?: string) => {
    return status === 'online' ? 'bg-green-500' : 'bg-gray-300'
  }

  return (
    <div className="flex h-screen bg-gray-50">
      {/* Chats Sidebar */}
      <div className="w-96 bg-white border-r flex flex-col">
        {/* Sidebar Header */}
        <div className="p-4 border-b">
          <div className="flex items-center justify-between mb-3">
            <h2 className="text-xl font-semibold">Chats</h2>
            <button className="p-2 hover:bg-gray-100 rounded-full">
              <MoreVertical className="w-5 h-5 text-gray-600" />
            </button>
          </div>
          <div className="relative">
            <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 w-4 h-4 text-gray-400" />
            <input
              type="text"
              placeholder="Search chats..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-10 pr-4 py-2 bg-gray-100 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
        </div>

        {/* Chats List */}
        <div 
          ref={chatListRef}
          onScroll={handleChatListScroll}
          className="flex-1 overflow-y-auto scroll-smooth"
          style={{ 
            scrollBehavior: 'auto', // Disable smooth scroll for manual scrolling
            overscrollBehavior: 'contain' // Prevent scroll chaining
          }}
        >
          {chatsLoading && currentPage === 1 && allChats.length === 0 ? (
            <div className="flex items-center justify-center h-32">
              <div className="w-6 h-6 border-2 border-blue-500 border-t-transparent rounded-full animate-spin"></div>
            </div>
          ) : filteredChats && filteredChats.length > 0 ? (
            <>
              {filteredChats.map((chat: ChatListItem) => {
              const isSelected = chatId === String(chat.chatId)
              const hasUnread = (chat.unreadCount || 0) > 0
              const isNewlyLoaded = currentPage > 1 && chats && chat.chatId === chats[chats.length - 1]?.chatId
              
              return (
                <div
                  key={chat.chatId}
                  onClick={() => navigate(`/chats/${chat.chatId}`)}
                  className={`p-4 border-b cursor-pointer hover:bg-gray-50 transition-all duration-200 relative ${
                    isSelected ? 'bg-blue-50' : ''
                  } ${hasUnread ? 'bg-blue-50/30' : ''} ${isNewlyLoaded ? 'animate-fadeIn' : ''}`}
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
                              {getMessageStatusIcon(chat.messageStatus)}
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
            })}
            
            {/* Subtle loading indicator at bottom (like Telegram) */}
            {isFetching && currentPage > 1 && (
              <div className="flex items-center justify-center py-2">
                <div className="w-4 h-4 border-2 border-blue-500 border-t-transparent rounded-full animate-spin"></div>
              </div>
            )}
          </>
          ) : (
            <div className="text-center py-12 text-gray-500">
              <p>No chats found</p>
            </div>
          )}
        </div>
      </div>

      {/* Chat Area */}
      {chatId && selectedChat ? (
        <div 
          className="flex-1 flex flex-col"
          onDragOver={handleDragOver}
          onDragLeave={handleDragLeave}
          onDrop={handleDrop}
        >
          {/* Drag overlay */}
          {isDragging && (
            <div className="absolute inset-0 bg-blue-500/10 backdrop-blur-sm z-40 flex items-center justify-center">
              <div className="bg-white rounded-lg p-8 shadow-xl">
                <Paperclip className="w-16 h-16 text-blue-500 mx-auto mb-4" />
                <p className="text-lg font-medium text-gray-900">Drop file to send</p>
              </div>
            </div>
          )}

          {/* Chat Header */}
          <div className="bg-white border-b px-6 py-3 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <button
                onClick={() => navigate('/chats')}
                className="lg:hidden p-2 hover:bg-gray-100 rounded-full"
              >
                <ArrowLeft className="w-5 h-5" />
              </button>
              <div className="relative">
                <div className="w-10 h-10 rounded-full bg-gradient-to-br from-blue-400 to-blue-600 flex items-center justify-center text-white font-semibold">
                  {selectedChat.title.charAt(0).toUpperCase()}
                </div>
                {/* Online status for private chats */}
                {selectedChat.type === 'PRIVATE' && (
                  <div className="absolute bottom-0 right-0 w-3 h-3 bg-green-500 border-2 border-white rounded-full" />
                )}
              </div>
              <div>
                <h2 className="font-semibold text-gray-900">{selectedChat.title}</h2>
                <p className="text-sm text-gray-500">
                  {selectedChat.memberCount ? `${selectedChat.memberCount} members` : 'Private chat'}
                </p>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <button className="p-2 hover:bg-gray-100 rounded-full">
                <Search className="w-5 h-5 text-gray-600" />
              </button>
              <button className="p-2 hover:bg-gray-100 rounded-full">
                <Phone className="w-5 h-5 text-gray-600" />
              </button>
              <button className="p-2 hover:bg-gray-100 rounded-full">
                <MoreVertical className="w-5 h-5 text-gray-600" />
              </button>
            </div>
          </div>

          {/* Messages Area */}
          <div 
            ref={messagesContainerRef}
            onScroll={handleMessageScroll}
            className="flex-1 overflow-y-auto p-6 space-y-4 bg-gray-50 relative"
            style={{ 
              scrollBehavior: 'auto', // Disable smooth scroll for manual scrolling
              overscrollBehavior: 'contain' // Prevent scroll chaining
            }}
          >
            {/* Loading indicator for older messages */}
            {isLoadingOlderMessages && (
              <div className="flex items-center justify-center py-2">
                <div className="w-4 h-4 border-2 border-blue-500 border-t-transparent rounded-full animate-spin"></div>
                <span className="ml-2 text-sm text-gray-500">Loading older messages...</span>
              </div>
            )}
            
            {messagesLoading && !initialLoadComplete ? (
              <div className="flex items-center justify-center h-full">
                <Loader className="w-8 h-8 animate-spin text-blue-500" />
              </div>
            ) : messages && messages.length > 0 ? (
              <>
                {messages.map((message: Message, index: number) => {
                  const isOwnMessage = message.senderId === currentUser?.id
                  const showDate = index === 0 || 
                    formatDate(messages[index - 1].messageDate) !== formatDate(message.messageDate)
                  
                  // Debug: Log message details for media types
                  if (['VOICE', 'VIDEO', 'PHOTO', 'DOCUMENT'].includes(message.messageType)) {
                    if (!message.minioPresignedUrl) {
                      console.warn(`[MESSAGE] ${message.messageType} missing URL:`, {
                        id: message.id,
                        type: message.messageType,
                        fileName: message.fileName,
                        fileSize: message.fileSize,
                        hasUrl: !!message.minioPresignedUrl,
                        hasFileId: !!message.fileId,
                        chatId: message.chatId
                      })
                    } else {
                      console.log(`[MESSAGE] ${message.messageType} with URL:`, {
                        id: message.id,
                        type: message.messageType,
                        url: message.minioPresignedUrl.substring(0, 50) + '...',
                        hasUrl: true
                      })
                    }
                  }

                  return (
                    <div key={message.id}>
                      {showDate && (
                        <div className="flex justify-center my-4">
                          <span className="bg-white px-3 py-1 rounded-full text-xs text-gray-500 shadow-sm">
                            {formatDate(message.messageDate)}
                          </span>
                        </div>
                      )}
                      <div className={`flex ${isOwnMessage ? 'justify-end' : 'justify-start'}`}>
                        <div
                          onContextMenu={(e) => handleContextMenu(e, message)}
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
                          
                          {/* Render different content based on message type */}
                          {message.messageType === 'TEXT' && (
                            <p className="break-words">{message.content}</p>
                          )}
                          
                          {message.messageType === 'VIDEO' && (
                            <div className={`${isOwnMessage ? 'bg-blue-500' : 'bg-white'} rounded-2xl overflow-hidden`}>
                              {message.minioPresignedUrl ? (
                                <div className="relative group">
                                  <video 
                                    controls 
                                    className="w-full max-w-md"
                                    poster={message.thumbnailMinioPresignedUrl}
                                    preload="metadata"
                                    style={{ maxHeight: '400px' }}
                                    controlsList="nodownload"
                                  >
                                    <source src={message.minioPresignedUrl} type="video/mp4" />
                                    <source src={message.minioPresignedUrl} type="video/webm" />
                                    Your browser does not support the video tag.
                                  </video>
                                  {/* Download button overlay */}
                                  <button
                                    onClick={() => handleDownload(message.minioPresignedUrl!, message.fileName || 'video.mp4')}
                                    className="absolute top-2 right-2 bg-black/50 hover:bg-black/70 backdrop-blur-sm text-white p-2 rounded-full opacity-0 group-hover:opacity-100 transition-opacity"
                                    title="Download video"
                                  >
                                    <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
                                    </svg>
                                  </button>
                                  {message.content && message.content !== message.fileName && (
                                    <div className={`px-3 py-2 ${isOwnMessage ? 'text-white' : 'text-gray-900'}`}>
                                      <p className="text-sm whitespace-pre-wrap">{message.content}</p>
                                    </div>
                                  )}
                                </div>
                              ) : (
                                <div className="px-4 py-3">
                                  {message.thumbnailMinioPresignedUrl && (
                                    <img 
                                      src={message.thumbnailMinioPresignedUrl} 
                                      alt="Video thumbnail"
                                      className="rounded-lg w-full mb-2"
                                      style={{ maxHeight: '300px', objectFit: 'cover' }}
                                    />
                                  )}
                                  <div className="flex items-center gap-3">
                                    <div className={`w-12 h-12 rounded-lg flex items-center justify-center ${
                                      isOwnMessage ? 'bg-white/20' : 'bg-blue-100'
                                    }`}>
                                      <span className="text-2xl">🎥</span>
                                    </div>
                                    <div className="flex-1">
                                      <p className="text-sm font-medium">{message.fileName || 'Video'}</p>
                                      {message.fileSize && (
                                        <p className="text-xs opacity-75">
                                          {(message.fileSize / (1024 * 1024)).toFixed(2)} MB
                                        </p>
                                      )}
                                    </div>
                                  </div>
                                  {message.content && message.content !== message.fileName && (
                                    <p className="text-sm mt-2 whitespace-pre-wrap">{message.content}</p>
                                  )}
                                </div>
                              )}
                            </div>
                          )}
                          
                          {message.messageType === 'PHOTO' && (
                            <div className={`${isOwnMessage ? 'bg-blue-500' : 'bg-white'} rounded-2xl overflow-hidden`}>
                              {message.minioPresignedUrl ? (
                                <div className="relative group">
                                  <img 
                                    src={message.minioPresignedUrl} 
                                    alt="Photo"
                                    className="w-full cursor-pointer hover:opacity-90 transition-opacity"
                                    style={{ maxWidth: '400px', maxHeight: '400px', objectFit: 'cover' }}
                                    onClick={() => handleImageClick(message)}
                                  />
                                  {/* Download button overlay */}
                                  <button
                                    onClick={(e) => {
                                      e.stopPropagation()
                                      handleDownload(message.minioPresignedUrl!, message.fileName || 'photo.jpg')
                                    }}
                                    className="absolute top-2 right-2 bg-black/50 hover:bg-black/70 backdrop-blur-sm text-white p-2 rounded-full opacity-0 group-hover:opacity-100 transition-opacity"
                                    title="Download photo"
                                  >
                                    <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
                                    </svg>
                                  </button>
                                  {message.content && (
                                    <div className={`px-3 py-2 ${isOwnMessage ? 'text-white' : 'text-gray-900'}`}>
                                      <p className="text-sm whitespace-pre-wrap">{message.content}</p>
                                    </div>
                                  )}
                                </div>
                              ) : (
                                <div className="px-4 py-3">
                                  <div className="flex items-center gap-3">
                                    <div className={`w-12 h-12 rounded-lg flex items-center justify-center ${
                                      isOwnMessage ? 'bg-white/20' : 'bg-blue-100'
                                    }`}>
                                      <span className="text-2xl">📷</span>
                                    </div>
                                    <div className="flex-1">
                                      <p className="text-sm font-medium">Photo</p>
                                      {message.fileSize && (
                                        <p className="text-xs opacity-75">
                                          {(message.fileSize / 1024).toFixed(0)} KB
                                        </p>
                                      )}
                                    </div>
                                  </div>
                                  {message.content && (
                                    <p className="text-sm mt-2 whitespace-pre-wrap">{message.content}</p>
                                  )}
                                </div>
                              )}
                            </div>
                          )}
                          
                          {message.messageType === 'DOCUMENT' && (
                            <div className="space-y-2">
                              <div className="flex items-center gap-3 bg-white/10 rounded-lg p-3">
                                <span className="text-3xl">📄</span>
                                <div className="flex-1 min-w-0">
                                  <p className="text-sm font-medium truncate">{message.fileName || 'Document'}</p>
                                  {message.fileSize && (
                                    <p className="text-xs opacity-75">
                                      {(message.fileSize / (1024 * 1024)).toFixed(2)} MB
                                    </p>
                                  )}
                                </div>
                                {message.minioPresignedUrl && (
                                  <a 
                                    href={message.minioPresignedUrl} 
                                    download={message.fileName}
                                    className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${
                                      isOwnMessage 
                                        ? 'bg-white/20 hover:bg-white/30 text-white' 
                                        : 'bg-blue-500 hover:bg-blue-600 text-white'
                                    }`}
                                    onClick={(e) => e.stopPropagation()}
                                  >
                                    Download
                                  </a>
                                )}
                              </div>
                              {message.content && message.content !== message.fileName && (
                                <p className="text-sm mt-2">{message.content}</p>
                              )}
                            </div>
                          )}
                          
                          {message.messageType === 'VOICE' && (
                            <div className="min-w-[250px]">
                              {message.minioPresignedUrl ? (
                                <div className={`flex items-center gap-2 rounded-2xl p-2 ${
                                  isOwnMessage ? 'bg-white/10' : 'bg-gray-50'
                                }`}>
                                  {/* Play/Pause Button */}
                                  <button
                                    onClick={() => handleVoicePlayPause(message.id, message.minioPresignedUrl!)}
                                    className={`w-10 h-10 rounded-full flex items-center justify-center flex-shrink-0 transition-colors ${
                                      isOwnMessage 
                                        ? 'bg-white/20 hover:bg-white/30' 
                                        : 'bg-blue-500 hover:bg-blue-600'
                                    }`}
                                  >
                                    {playingVoiceId === message.id ? (
                                      <svg className={`w-5 h-5 ${isOwnMessage ? 'text-white' : 'text-white'}`} fill="currentColor" viewBox="0 0 24 24">
                                        <path d="M6 4h4v16H6V4zm8 0h4v16h-4V4z"/>
                                      </svg>
                                    ) : (
                                      <svg className={`w-5 h-5 ${isOwnMessage ? 'text-white' : 'text-white'}`} fill="currentColor" viewBox="0 0 24 24">
                                        <path d="M8 5v14l11-7z"/>
                                      </svg>
                                    )}
                                  </button>
                                  
                                  {/* Waveform visualization (simplified bars) */}
                                  <div className="flex-1 flex items-center gap-0.5 h-8">
                                    {[...Array(30)].map((_, i) => {
                                      const height = Math.random() * 60 + 40 // Random height between 40-100%
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
                                  
                                  {/* Duration */}
                                  <span className={`text-xs flex-shrink-0 ${
                                    isOwnMessage ? 'text-white/80' : 'text-gray-500'
                                  }`}>
                                    {message.fileSize ? formatDuration(message.fileSize / 1024) : '0:00'}
                                  </span>
                                  
                                  {/* Hidden audio element for streaming playback */}
                                  <audio
                                    ref={(el) => {
                                      if (el) audioRefs.current.set(message.id, el)
                                    }}
                                    src={message.minioPresignedUrl}
                                    preload="metadata"
                                    onEnded={() => setPlayingVoiceId(null)}
                                    className="hidden"
                                  />
                                </div>
                              ) : (
                                <div className={`flex items-center gap-3 rounded-2xl p-3 ${
                                  isOwnMessage ? 'bg-white/10' : 'bg-gray-50'
                                }`}>
                                  <div className={`w-10 h-10 rounded-full flex items-center justify-center ${
                                    isOwnMessage ? 'bg-white/20' : 'bg-blue-100'
                                  }`}>
                                    <span className="text-xl">🎤</span>
                                  </div>
                                  <div className="flex-1">
                                    <p className={`text-sm font-medium ${
                                      isOwnMessage ? 'text-white' : 'text-gray-900'
                                    }`}>
                                      Voice Message
                                    </p>
                                    <p className={`text-xs ${
                                      isOwnMessage ? 'text-white/70' : 'text-gray-500'
                                    }`}>
                                      {message.fileSize 
                                        ? `${(message.fileSize / 1024).toFixed(0)} KB - File not available` 
                                        : 'File not available'}
                                    </p>
                                  </div>
                                </div>
                              )}
                            </div>
                          )}
                          
                          {message.messageType === 'AUDIO' && (
                            <div className="min-w-[300px]">
                              {message.minioPresignedUrl ? (
                                <div>
                                  <div className={`flex items-center gap-3 p-3 rounded-lg ${
                                    isOwnMessage ? 'bg-white/10' : 'bg-gray-50'
                                  }`}>
                                    <div className={`w-12 h-12 rounded-lg flex items-center justify-center flex-shrink-0 ${
                                      isOwnMessage ? 'bg-white/20' : 'bg-blue-100'
                                    }`}>
                                      <span className="text-2xl">🎵</span>
                                    </div>
                                    <div className="flex-1 min-w-0">
                                      <p className={`text-sm font-medium truncate ${
                                        isOwnMessage ? 'text-white' : 'text-gray-900'
                                      }`}>
                                        {message.fileName || 'Audio'}
                                      </p>
                                      {message.fileSize && (
                                        <p className={`text-xs ${
                                          isOwnMessage ? 'text-blue-100' : 'text-gray-500'
                                        }`}>
                                          {(message.fileSize / (1024 * 1024)).toFixed(2)} MB
                                        </p>
                                      )}
                                    </div>
                                    <button
                                      onClick={() => handleDownload(message.minioPresignedUrl!, message.fileName || 'audio.mp3')}
                                      className={`p-2 rounded-lg transition-colors flex-shrink-0 ${
                                        isOwnMessage 
                                          ? 'hover:bg-white/20 text-white' 
                                          : 'hover:bg-gray-200 text-gray-700'
                                      }`}
                                      title="Download audio"
                                    >
                                      <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
                                      </svg>
                                    </button>
                                  </div>
                                  <audio controls className="w-full mt-2" style={{ height: '40px' }} controlsList="nodownload">
                                    <source src={message.minioPresignedUrl} type="audio/mpeg" />
                                    <source src={message.minioPresignedUrl} type="audio/mp4" />
                                    <source src={message.minioPresignedUrl} type="audio/ogg" />
                                    Your browser does not support the audio element.
                                  </audio>
                                </div>
                              ) : (
                                <div className={`flex items-center gap-3 p-3 rounded-lg ${
                                  isOwnMessage ? 'bg-white/10' : 'bg-gray-50'
                                }`}>
                                  <div className={`w-12 h-12 rounded-lg flex items-center justify-center ${
                                    isOwnMessage ? 'bg-white/20' : 'bg-blue-100'
                                  }`}>
                                    <span className="text-2xl">🎵</span>
                                  </div>
                                  <div className="flex-1">
                                    <p className="text-sm font-medium">{message.fileName || 'Audio'}</p>
                                    {message.fileSize && (
                                      <p className="text-xs opacity-75">
                                        {(message.fileSize / (1024 * 1024)).toFixed(2)} MB
                                      </p>
                                    )}
                                  </div>
                                </div>
                              )}
                            </div>
                          )}
                          
                          {message.messageType === 'STICKER' && (
                            <div className="space-y-2">
                              {message.minioPresignedUrl ? (
                                <img 
                                  src={message.minioPresignedUrl} 
                                  alt="Sticker"
                                  className="w-32 h-32 object-contain"
                                />
                              ) : (
                                <div className="flex items-center gap-2">
                                  <span className="text-4xl">🎨</span>
                                  <p className="text-sm">Sticker</p>
                                </div>
                              )}
                            </div>
                          )}
                          
                          {message.messageType === 'ANIMATION' && (
                            <div className="space-y-2">
                              {message.minioPresignedUrl ? (
                                <img 
                                  src={message.minioPresignedUrl} 
                                  alt="Animation"
                                  className="rounded-lg max-w-full h-auto"
                                />
                              ) : (
                                <div className="flex items-center gap-2">
                                  <span className="text-2xl">🎬</span>
                                  <p className="text-sm">Animation</p>
                                </div>
                              )}
                            </div>
                          )}
                          
                          {message.messageType === 'UNKNOWN' && (
                            <div className="flex items-center gap-2 opacity-75">
                              <span className="text-xl">❓</span>
                              <p className="text-sm italic">Unsupported message type</p>
                            </div>
                          )}
                          
                          <div className="flex items-center justify-end gap-1 mt-1">
                            <p
                              className={`text-xs ${
                                isOwnMessage ? 'text-blue-100' : 'text-gray-400'
                              }`}
                            >
                              {formatTime(message.messageDate)}
                            </p>
                            {isOwnMessage && (
                              <CheckCheck className="w-3 h-3 text-blue-100" />
                            )}
                          </div>
                        </div>
                      </div>
                    </div>
                  )
                })}
                
                {/* Loading indicator for newer messages at bottom */}
                {isLoadingNewerMessages && (
                  <div className="flex items-center justify-center py-2">
                    <div className="w-4 h-4 border-2 border-blue-500 border-t-transparent rounded-full animate-spin"></div>
                    <span className="ml-2 text-sm text-gray-500">Loading newer messages...</span>
                  </div>
                )}
                
                <div ref={messagesEndRef} />
              </>
            ) : (
              <div className="flex items-center justify-center h-full text-gray-500">
                <p>No messages yet. Start the conversation!</p>
              </div>
            )}
            
            {/* Scroll to bottom button (Telegram-style) */}
            {showScrollToBottom && (
              <button
                onClick={scrollToBottom}
                className="fixed bottom-24 right-8 bg-white hover:bg-gray-50 text-gray-700 rounded-full p-3 shadow-lg border border-gray-200 transition-all duration-200 z-10 flex items-center gap-2"
                title="Scroll to bottom"
              >
                <svg 
                  className="w-5 h-5" 
                  fill="none" 
                  stroke="currentColor" 
                  viewBox="0 0 24 24"
                >
                  <path 
                    strokeLinecap="round" 
                    strokeLinejoin="round" 
                    strokeWidth={2} 
                    d="M19 14l-7 7m0 0l-7-7m7 7V3" 
                  />
                </svg>
              </button>
            )}
          </div>

          {/* Message Input */}
          <div className="bg-white border-t px-4 py-3">
            {/* Reply preview */}
            {replyingTo && (
              <div className="mb-2">
                <MessageReply 
                  message={replyingTo} 
                  onClose={() => setReplyingTo(null)} 
                />
              </div>
            )}
            
            {/* File preview if selected */}
            {selectedFile && (
              <div className="mb-2 p-2 bg-gray-100 rounded-lg flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Paperclip className="w-4 h-4 text-gray-500" />
                  <span className="text-sm text-gray-700">{selectedFile.name}</span>
                  <span className="text-xs text-gray-500">
                    ({(selectedFile.size / (1024 * 1024)).toFixed(2)} MB)
                  </span>
                </div>
                <button
                  onClick={() => setSelectedFile(null)}
                  className="text-red-500 hover:text-red-700"
                >
                  ✕
                </button>
              </div>
            )}
            
            <form onSubmit={handleSendMessage} className="flex items-end gap-2">
              {/* Hidden file input */}
              <input
                ref={fileInputRef}
                type="file"
                className="hidden"
                onChange={handleFileSelect}
                accept="*/*"
              />
              
              {/* Attachment button */}
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                disabled={isUploading}
                className="p-2 text-gray-500 hover:text-gray-700 hover:bg-gray-100 rounded-full transition-colors flex-shrink-0 disabled:opacity-50"
                title="Attach file"
              >
                <Paperclip className="w-5 h-5" />
              </button>
              
              {/* Message input */}
              <div className="flex-1 relative">
                <textarea
                  ref={textareaRef}
                  value={messageText}
                  onChange={(e) => {
                    setMessageText(e.target.value)
                    // Auto-resize textarea
                    e.target.style.height = 'auto'
                    e.target.style.height = Math.min(e.target.scrollHeight, 120) + 'px'
                    // Send typing indicator
                    if (e.target.value.length > 0) {
                      sendTyping()
                    }
                  }}
                  onKeyDown={(e) => {
                    // Enter to send, Shift+Enter for new line (Telegram behavior)
                    if (e.key === 'Enter' && !e.shiftKey) {
                      e.preventDefault()
                      if (messageText.trim() || selectedFile) {
                        handleSendMessage(e)
                        // Reset textarea height
                        e.currentTarget.style.height = 'auto'
                      }
                    }
                  }}
                  placeholder="Type a message..."
                  disabled={isUploading}
                  className="w-full bg-gray-100 rounded-3xl px-4 py-2.5 resize-none focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all disabled:opacity-50"
                  rows={1}
                  style={{ minHeight: '40px', maxHeight: '120px' }}
                />
              </div>
              
              {/* Emoji picker */}
              <EmojiPicker onEmojiSelect={handleEmojiSelect} />
              
              {/* Send button - only show when there's text or file */}
              {(messageText.trim() || selectedFile) ? (
                <button
                  type="submit"
                  disabled={sendMessageMutation.isPending || isUploading}
                  className="p-2.5 bg-blue-500 text-white rounded-full hover:bg-blue-600 disabled:opacity-50 disabled:cursor-not-allowed transition-all flex-shrink-0 shadow-md hover:shadow-lg"
                  title="Send"
                >
                  {sendMessageMutation.isPending || isUploading ? (
                    <Loader className="w-5 h-5 animate-spin" />
                  ) : (
                    <Send className="w-5 h-5" />
                  )}
                </button>
              ) : (
                <VoiceRecorder 
                  onSend={handleVoiceSend}
                  disabled={isUploading}
                />
              )}
            </form>
          </div>
        </div>
      ) : (
        <div className="flex-1 flex items-center justify-center bg-gray-50">
          <div className="text-center">
            <div className="w-32 h-32 bg-blue-100 rounded-full flex items-center justify-center mx-auto mb-4">
              <Send className="w-16 h-16 text-blue-500" />
            </div>
            <h2 className="text-2xl font-semibold text-gray-900 mb-2">Select a chat</h2>
            <p className="text-gray-500">Choose a chat from the sidebar to start messaging</p>
          </div>
        </div>
      )}
      
      {/* Image Gallery */}
      <ImageGallery
        images={galleryImages}
        initialIndex={galleryIndex}
        isOpen={galleryOpen}
        onClose={() => setGalleryOpen(false)}
      />

      {/* Context Menu */}
      {contextMenu && (
        <div
          className="fixed bg-white dark:bg-gray-800 rounded-lg shadow-xl border border-gray-200 dark:border-gray-700 py-2 z-50"
          style={{ left: contextMenu.x, top: contextMenu.y }}
        >
          <button
            onClick={() => handleReply(contextMenu.message)}
            className="w-full px-4 py-2 text-left hover:bg-gray-100 dark:hover:bg-gray-700 flex items-center gap-2 text-gray-900 dark:text-gray-100"
          >
            <Reply className="w-4 h-4" />
            Reply
          </button>
          <button
            onClick={() => handleCopy(contextMenu.message)}
            className="w-full px-4 py-2 text-left hover:bg-gray-100 dark:hover:bg-gray-700 flex items-center gap-2 text-gray-900 dark:text-gray-100"
          >
            <Copy className="w-4 h-4" />
            Copy
          </button>
          <button
            onClick={() => {
              toast.success('Forward feature coming soon!')
              setContextMenu(null)
            }}
            className="w-full px-4 py-2 text-left hover:bg-gray-100 dark:hover:bg-gray-700 flex items-center gap-2 text-gray-900 dark:text-gray-100"
          >
            <Forward className="w-4 h-4" />
            Forward
          </button>
          <button
            onClick={() => {
              toast.success('Delete feature coming soon!')
              setContextMenu(null)
            }}
            className="w-full px-4 py-2 text-left hover:bg-gray-100 dark:hover:bg-gray-700 flex items-center gap-2 text-red-600 dark:text-red-400"
          >
            <Trash2 className="w-4 h-4" />
            Delete
          </button>
        </div>
      )}
    </div>
  )
}

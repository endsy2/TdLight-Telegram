/**
 * ChatPage - Refactored for better organization and performance
 * 
 * This component has been split into:
 * - Custom hooks for business logic
 * - Smaller components for UI
 * - Utility functions for data transformation
 * - Constants for configuration
 */

import { useState, useEffect, useRef, useCallback } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Send, Paperclip, Search, Loader } from 'lucide-react'
import { chatApi, messageApi } from '@/services/api'
import { useStore } from '@/store/useStore'
import { useChatsSubscription, useChatSubscription, useTypingIndicator } from '@/hooks/useWebSocket'
import { useChatPagination } from '@/hooks/useChatPagination'
import { useScrollManagement } from '@/hooks/useScrollManagement'
import toast from 'react-hot-toast'
import type { Message, ChatListItem, Chat } from '@/types'

// Components
import ChatHeader from '@/components/chat/ChatHeader'
import ChatListItem from '@/components/chat/ChatListItem'
import MessageBubble from '@/components/chat/MessageBubble'
import TextMessage from '@/components/chat/TextMessage'
import PhotoMessage from '@/components/chat/PhotoMessage'
import VoiceMessage from '@/components/chat/VoiceMessage'
import MessageReply from '@/components/MessageReply'
import EmojiPicker from '@/components/EmojiPicker'
import VoiceRecorder from '@/components/VoiceRecorder'
import ImageGallery from '@/components/ImageGallery'

// Utils
import { formatDate } from '@/utils/dateUtils'
import { CHAT_CONSTANTS } from '@/constants/chat'

export default function ChatPage() {
  const { chatId } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const currentUser = useStore((state: any) => state.currentUser)
  
  // ============================================================================
  // STATE MANAGEMENT
  // ============================================================================
  
  // Message input state
  const [messageText, setMessageText] = useState('')
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [isUploading, setIsUploading] = useState(false)
  const [replyingTo, setReplyingTo] = useState<Message | null>(null)
  
  // Chat list state
  const [searchQuery, setSearchQuery] = useState('')
  const [currentPage, setCurrentPage] = useState(1)
  const [allChats, setAllChats] = useState<ChatListItem[]>([])
  const [isLoadingMore, setIsLoadingMore] = useState(false)
  const [hasMoreChats, setHasMoreChats] = useState(true)
  
  // Chat profile state
  const [chatProfile, setChatProfile] = useState<Chat | null>(null)
  const [initialLoadComplete, setInitialLoadComplete] = useState(false)
  
  // UI state
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
  
  // Refs
  const chatListRef = useRef<HTMLDivElement>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  
  // ============================================================================
  // CUSTOM HOOKS
  // ============================================================================
  
  // Message pagination hook
  const pagination = useChatPagination({
    chatId,
    onProfileUpdate: setChatProfile,
  })
  
  // Scroll management hook
  const scroll = useScrollManagement({
    hasMoreOlderMessages: pagination.hasMoreOlderMessages,
    hasMoreNewerMessages: pagination.hasMoreNewerMessages,
    isLoadingOlderMessages: pagination.isLoadingOlderMessages,
    isLoadingNewerMessages: pagination.isLoadingNewerMessages,
    onLoadOlder: pagination.loadOlderMessages,
    onLoadNewer: pagination.loadNewerMessages,
    messages: pagination.allMessages,
  })
  
  // WebSocket subscriptions
  useChatsSubscription(
    useCallback((event) => {
      console.log('[WebSocket] Chats event:', event)
      if (event.type === 'CHAT_UPDATED') {
        queryClient.invalidateQueries({ queryKey: ['chats'] })
      }
    }, [queryClient])
  )
  
  useChatSubscription(
    chatId ? Number(chatId) : null,
    useCallback((event) => {
      console.log('[WebSocket] Chat event:', event)
      if (event.type === 'NEW_MESSAGE') {
        pagination.setAllMessages(prev => {
          const exists = prev.some((msg: Message) => msg.id === event.data.id)
          if (exists) return prev
          return [...prev, event.data]
        })
      }
    }, [chatId, pagination])
  )
  
  const sendTyping = useTypingIndicator(chatId ? Number(chatId) : null)
  
  // ============================================================================
  // DATA FETCHING
  // ============================================================================
  
  // Fetch chats list
  const { data: chats, isLoading: chatsLoading, isFetching } = useQuery({
    queryKey: ['chats', currentPage],
    queryFn: async () => {
      const response = await messageApi.getLatestHistory(currentPage)
      return response.data
    },
    staleTime: CHAT_CONSTANTS.CACHE_TIME_MS,
  })
  
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
  
  // Fetch initial messages
  const { data: rawMessages, isLoading: messagesLoading } = useQuery({
    queryKey: ['messages', chatId, 'initial'],
    queryFn: async () => {
      if (!chatId) return { messages: [], profile: null }
      const response = await messageApi.getMessageHistoryByChatId(
        Number(chatId), 
        CHAT_CONSTANTS.MESSAGES_PER_PAGE
      )
      
      console.log('[API Response] Message history:', {
        messagesCount: response.data.messages?.length || 0,
        hasProfile: !!response.data.profile,
        chatId: response.data.chatId
      })
      
      if (response.data.profile) {
        setChatProfile(response.data.profile)
      }
      
      return { messages: response.data.messages, profile: response.data.profile }
    },
    enabled: !!chatId && !initialLoadComplete,
  })
  
  // ============================================================================
  // EFFECTS
  // ============================================================================
  
  // Update chats list
  useEffect(() => {
    if (chats && chats.length > 0) {
      const expectedChats = currentPage * CHAT_CONSTANTS.CHATS_PER_PAGE
      const gotFullPage = chats.length >= expectedChats
      
      if (!gotFullPage && currentPage > 1) {
        setHasMoreChats(false)
      }
      
      if (chats.length > allChats.length) {
        const scrollElement = chatListRef.current
        if (scrollElement && currentPage > 1) {
          const scrollTop = scrollElement.scrollTop
          const scrollHeight = scrollElement.scrollHeight

          setAllChats(chats)

          requestAnimationFrame(() => {
            if (scrollElement) {
              const newScrollHeight = scrollElement.scrollHeight
              const heightDiff = newScrollHeight - scrollHeight
              scrollElement.scrollTop = scrollTop + heightDiff
            }
          })
        } else {
          setAllChats(chats)
        }
      } else if (currentPage === 1) {
        setAllChats(chats)
        setHasMoreChats(true)
      }
      
      setIsLoadingMore(false)
    }
  }, [chats, currentPage, allChats.length])
  
  // Initialize messages
  useEffect(() => {
    if (rawMessages && rawMessages.messages && rawMessages.messages.length > 0 && !initialLoadComplete) {
      pagination.initializeMessages(rawMessages.messages)
      setInitialLoadComplete(true)
    }
  }, [rawMessages, initialLoadComplete, pagination])
  
  // Reset on chat change
  useEffect(() => {
    if (chatId) {
      console.log('[RESET] Chat changed to:', chatId)
      pagination.resetPagination()
      setChatProfile(null)
      setInitialLoadComplete(false)
      
      chatApi.openChat(Number(chatId))
        .then(() => console.log(`[OPEN_CHAT] ✅ Chat ${chatId} opened`))
        .catch(error => console.error(`[OPEN_CHAT] ❌ Failed:`, error))
      
      return () => {
        chatApi.closeChat(Number(chatId))
          .then(() => console.log(`[CLOSE_CHAT] ✅ Chat ${chatId} closed`))
          .catch(error => console.error(`[CLOSE_CHAT] ❌ Failed:`, error))
      }
    }
  }, [chatId, pagination])
  
  // Close context menu on click outside
  useEffect(() => {
    const handleClick = () => setContextMenu(null)
    if (contextMenu) {
      document.addEventListener('click', handleClick)
      return () => document.removeEventListener('click', handleClick)
    }
  }, [contextMenu])
  
  // ============================================================================
  // EVENT HANDLERS
  // ============================================================================
  
  // Send message
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
    onSuccess: () => {
      setMessageText('')
      setReplyingTo(null)
      setTimeout(() => scroll.messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' }), 100)
    },
    onError: (error: any) => {
      console.error('Failed to send message:', error)
      toast.error(error.response?.data?.error || 'Failed to send message')
    },
  })
  
  const handleSendMessage = (e: React.FormEvent) => {
    e.preventDefault()
    
    if (selectedFile) {
      handleFileSend()
      return
    }
    
    if (messageText.trim() && !sendMessageMutation.isPending) {
      sendMessageMutation.mutate(messageText.trim())
    }
  }
  
  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) {
      setSelectedFile(file)
    }
  }
  
  const handleFileSend = async () => {
    if (!chatId || !selectedFile) return
    
    setIsUploading(true)
    const toastId = 'file-upload'
    
    try {
      toast.loading('Uploading file...', { id: toastId })
      
      const response = await messageApi.sendFileToGroup(
        Number(chatId),
        selectedFile,
        messageText.trim() || undefined
      )
      
      toast.success('File sent successfully!', { id: toastId })
      
      setSelectedFile(null)
      setMessageText('')
      
      if (fileInputRef.current) {
        fileInputRef.current.value = ''
      }
      
      setTimeout(() => scroll.messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' }), 100)
    } catch (error: any) {
      console.error('[File Upload] Failed:', error)
      toast.error(error.response?.data?.error || 'Failed to upload file', { id: toastId })
    } finally {
      setIsUploading(false)
    }
  }
  
  const handleVoiceSend = async (audioBlob: Blob) => {
    if (!chatId) return
    
    try {
      const file = new File([audioBlob], `voice_${Date.now()}.ogg`, { type: 'audio/ogg' })
      
      toast.loading('Sending voice message...')
      await messageApi.sendFileToGroup(Number(chatId), file, 'Voice message')
      
      toast.success('Voice message sent!')
      
      setTimeout(() => scroll.messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' }), 100)
    } catch (error) {
      console.error('Failed to send voice message:', error)
      toast.error('Failed to send voice message')
    }
  }
  
  const handleEmojiSelect = (emoji: string) => {
    setMessageText(prev => prev + emoji)
    textareaRef.current?.focus()
  }
  
  const handleImageClick = (message: Message) => {
    const images = pagination.allMessages?.filter(m => m.messageType === 'PHOTO') || []
    const index = images.findIndex(m => m.id === message.id)
    setGalleryImages(images)
    setGalleryIndex(index)
    setGalleryOpen(true)
  }
  
  const handleContextMenu = (e: React.MouseEvent, message: Message) => {
    e.preventDefault()
    setContextMenu({
      message,
      x: e.clientX,
      y: e.clientY
    })
  }
  
  const handleReply = (message: Message) => {
    setReplyingTo(message)
    textareaRef.current?.focus()
    setContextMenu(null)
  }
  
  const handleCopy = (message: Message) => {
    navigator.clipboard.writeText(message.content)
    toast.success('Message copied!')
    setContextMenu(null)
  }
  
  const handleVoicePlayPause = (messageId: number, audioUrl: string) => {
    const audio = audioRefs.current.get(messageId)
    
    if (playingVoiceId === messageId && audio && !audio.paused) {
      audio.pause()
      setPlayingVoiceId(null)
    } else {
      if (playingVoiceId !== null) {
        const currentAudio = audioRefs.current.get(playingVoiceId)
        if (currentAudio) {
          currentAudio.pause()
          currentAudio.currentTime = 0
        }
      }
      
      if (!audio) {
        const newAudio = new Audio(audioUrl)
        audioRefs.current.set(messageId, newAudio)
        newAudio.addEventListener('ended', () => setPlayingVoiceId(null))
        newAudio.play()
        setPlayingVoiceId(messageId)
      } else {
        audio.play()
        setPlayingVoiceId(messageId)
      }
    }
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
  
  // Chat list scroll handler
  const handleChatListScroll = useCallback((e: React.UIEvent<HTMLDivElement>) => {
    const element = e.currentTarget
    const scrollTop = element.scrollTop
    const scrollHeight = element.scrollHeight
    const clientHeight = element.clientHeight
    
    const distanceFromBottom = scrollHeight - scrollTop - clientHeight
    
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
  
  // Filter chats
  const filteredChats = allChats?.filter((chat: ChatListItem) => {
    const title = chat.title || ''
    return title.toLowerCase().includes(searchQuery.toLowerCase())
  })
  
  // ============================================================================
  // RENDER
  // ============================================================================
  
  return (
    <div className="flex h-screen bg-gray-50">
      {/* Chats Sidebar */}
      <div className="w-96 bg-white border-r flex flex-col">
        {/* Sidebar Header */}
        <div className="p-4 border-b">
          <div className="flex items-center justify-between mb-3">
            <h2 className="text-xl font-semibold">Chats</h2>
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
            scrollBehavior: 'auto',
            overscrollBehavior: 'contain'
          }}
        >
          {chatsLoading && currentPage === 1 && allChats.length === 0 ? (
            <div className="flex items-center justify-center h-32">
              <div className="w-6 h-6 border-2 border-blue-500 border-t-transparent rounded-full animate-spin"></div>
            </div>
          ) : filteredChats && filteredChats.length > 0 ? (
            <>
              {filteredChats.map((chat: ChatListItem) => (
                <ChatListItem
                  key={chat.chatId}
                  chat={chat}
                  isSelected={chatId === String(chat.chatId)}
                  onClick={() => navigate(`/chats/${chat.chatId}`)}
                />
              ))}
              
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
      {chatId && (selectedChat || chatProfile) ? (
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
          {(chatProfile || selectedChat) && (
            <ChatHeader
              chat={chatProfile || selectedChat!}
              onSearchClick={() => console.log('Search clicked')}
              onCallClick={() => console.log('Call clicked')}
              onMenuClick={() => console.log('Menu clicked')}
            />
          )}

          {/* Messages Area */}
          <div 
            ref={scroll.messagesContainerRef}
            onScroll={scroll.handleMessageScroll}
            className="flex-1 overflow-y-auto p-6 space-y-4 bg-gray-50 relative"
            style={{ 
              scrollBehavior: 'auto',
              overscrollBehavior: 'contain'
            }}
          >
            {/* Loading indicator for older messages */}
            {pagination.isLoadingOlderMessages && (
              <div className="flex items-center justify-center py-2">
                <div className="w-4 h-4 border-2 border-blue-500 border-t-transparent rounded-full animate-spin"></div>
                <span className="ml-2 text-sm text-gray-500">Loading older messages...</span>
              </div>
            )}
            
            {messagesLoading && !initialLoadComplete ? (
              <div className="flex items-center justify-center h-full">
                <Loader className="w-8 h-8 animate-spin text-blue-500" />
              </div>
            ) : pagination.allMessages && pagination.allMessages.length > 0 ? (
              <>
                {pagination.allMessages.map((message: Message, index: number) => {
                  const isOwnMessage = message.senderId === currentUser?.id
                  const showDate = index === 0 || 
                    formatDate(pagination.allMessages[index - 1].messageDate) !== formatDate(message.messageDate)

                  return (
                    <div key={message.id}>
                      {showDate && (
                        <div className="flex justify-center my-4">
                          <span className="bg-white px-3 py-1 rounded-full text-xs text-gray-500 shadow-sm">
                            {formatDate(message.messageDate)}
                          </span>
                        </div>
                      )}
                      
                      <MessageBubble
                        message={message}
                        isOwnMessage={isOwnMessage}
                        onContextMenu={handleContextMenu}
                      >
                        {message.messageType === 'TEXT' && (
                          <TextMessage content={message.content} />
                        )}
                        
                        {message.messageType === 'PHOTO' && (
                          <PhotoMessage
                            message={message}
                            isOwnMessage={isOwnMessage}
                            onImageClick={handleImageClick}
                          />
                        )}
                        
                        {message.messageType === 'VOICE' && (
                          <VoiceMessage
                            message={message}
                            isOwnMessage={isOwnMessage}
                            isPlaying={playingVoiceId === message.id}
                            onPlayPause={handleVoicePlayPause}
                            audioRef={(el) => {
                              if (el) audioRefs.current.set(message.id, el)
                            }}
                          />
                        )}
                        
                        {message.messageType === 'UNKNOWN' && (
                          <div className="flex items-center gap-2 opacity-75">
                            <span className="text-xl">❓</span>
                            <p className="text-sm italic">Unsupported message type</p>
                          </div>
                        )}
                      </MessageBubble>
                    </div>
                  )
                })}
                
                {pagination.isLoadingNewerMessages && (
                  <div className="flex items-center justify-center py-2">
                    <div className="w-4 h-4 border-2 border-blue-500 border-t-transparent rounded-full animate-spin"></div>
                    <span className="ml-2 text-sm text-gray-500">Loading newer messages...</span>
                  </div>
                )}
                
                <div ref={scroll.messagesEndRef} />
              </>
            ) : (
              <div className="flex items-center justify-center h-full text-gray-500">
                <p>No messages yet. Start the conversation!</p>
              </div>
            )}
            
            {/* Scroll to bottom button */}
            {scroll.showScrollToBottom && (
              <button
                onClick={scroll.scrollToBottom}
                className="fixed bottom-24 right-8 bg-white hover:bg-gray-50 text-gray-700 rounded-full p-3 shadow-lg border border-gray-200 transition-all duration-200 z-10"
                title="Scroll to bottom"
              >
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 14l-7 7m0 0l-7-7m7 7V3" />
                </svg>
              </button>
            )}
          </div>

import { useState, useCallback, useRef, useEffect } from 'react'
import { CHAT_CONSTANTS } from '@/constants/chat'

interface UseScrollManagementProps {
  hasMoreOlderMessages: boolean
  hasMoreNewerMessages: boolean
  isLoadingOlderMessages: boolean
  isLoadingNewerMessages: boolean
  onLoadOlder: () => void
  onLoadNewer: () => void
  messages: any[]
}

export function useScrollManagement({
  hasMoreOlderMessages,
  hasMoreNewerMessages,
  isLoadingOlderMessages,
  isLoadingNewerMessages,
  onLoadOlder,
  onLoadNewer,
  messages,
}: UseScrollManagementProps) {
  const [showScrollToBottom, setShowScrollToBottom] = useState(false)
  const [isAtBottom, setIsAtBottom] = useState(true)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const messagesContainerRef = useRef<HTMLDivElement>(null)
  const previousScrollHeightRef = useRef<number>(0)

  const handleMessageScroll = useCallback((e: React.UIEvent<HTMLDivElement>) => {
    const element = e.currentTarget
    const scrollTop = element.scrollTop
    const scrollHeight = element.scrollHeight
    const clientHeight = element.clientHeight
    
    // Check if user is at bottom
    const distanceFromBottom = scrollHeight - scrollTop - clientHeight
    const atBottom = distanceFromBottom < CHAT_CONSTANTS.SCROLL_THRESHOLD_BOTTOM
    setIsAtBottom(atBottom)
    setShowScrollToBottom(!atBottom)
    
    // Load older messages when scrolling near top
    if (
      scrollTop < CHAT_CONSTANTS.SCROLL_THRESHOLD_TOP &&
      !isLoadingOlderMessages &&
      !isLoadingNewerMessages &&
      hasMoreOlderMessages &&
      messages &&
      messages.length > 0
    ) {
      previousScrollHeightRef.current = scrollHeight
      onLoadOlder()
    }
    
    // Load newer messages when scrolling near bottom
    if (
      distanceFromBottom < CHAT_CONSTANTS.SCROLL_THRESHOLD_TOP &&
      !isLoadingOlderMessages &&
      !isLoadingNewerMessages &&
      hasMoreNewerMessages &&
      messages &&
      messages.length > 0
    ) {
      previousScrollHeightRef.current = scrollHeight
      onLoadNewer()
    }
  }, [
    isLoadingOlderMessages,
    isLoadingNewerMessages,
    hasMoreOlderMessages,
    hasMoreNewerMessages,
    messages,
    onLoadOlder,
    onLoadNewer,
  ])

  // Restore scroll position after loading older messages
  useEffect(() => {
    if (messagesContainerRef.current && messages && messages.length > 0) {
      const container = messagesContainerRef.current
      const previousScrollHeight = previousScrollHeightRef.current
      const currentScrollHeight = container.scrollHeight
      
      if (previousScrollHeight > 0 && currentScrollHeight > previousScrollHeight) {
        const heightDifference = currentScrollHeight - previousScrollHeight
        container.scrollTop = container.scrollTop + heightDifference
        previousScrollHeightRef.current = 0
      }
    }
  }, [messages])

  // Auto-scroll to bottom for new messages
  useEffect(() => {
    if (isAtBottom && messagesEndRef.current) {
      messagesEndRef.current.scrollIntoView({ behavior: 'smooth' })
    }
  }, [messages, isAtBottom])

  const scrollToBottom = useCallback(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
    setIsAtBottom(true)
    setShowScrollToBottom(false)
  }, [])

  return {
    showScrollToBottom,
    isAtBottom,
    messagesEndRef,
    messagesContainerRef,
    handleMessageScroll,
    scrollToBottom,
  }
}

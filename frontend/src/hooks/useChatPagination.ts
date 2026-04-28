import { useState, useCallback } from 'react'
import { messageApi } from '@/services/api'
import type { Message } from '@/types'
import { CHAT_CONSTANTS } from '@/constants/chat'

interface UseChatPaginationProps {
  chatId: string | undefined
  onProfileUpdate?: (profile: any) => void
}

export function useChatPagination({ chatId, onProfileUpdate }: UseChatPaginationProps) {
  const [allMessages, setAllMessages] = useState<Message[]>([])
  const [isLoadingOlderMessages, setIsLoadingOlderMessages] = useState(false)
  const [isLoadingNewerMessages, setIsLoadingNewerMessages] = useState(false)
  const [hasMoreOlderMessages, setHasMoreOlderMessages] = useState(true)
  const [hasMoreNewerMessages, setHasMoreNewerMessages] = useState(false)
  const [oldestMessageId, setOldestMessageId] = useState<number | null>(null)
  const [newestMessageId, setNewestMessageId] = useState<number | null>(null)

  const loadOlderMessages = useCallback(async () => {
    if (!chatId || !oldestMessageId || isLoadingOlderMessages) {
      return
    }
    
    setIsLoadingOlderMessages(true)
    try {
      const response = await messageApi.getMessageHistoryByChatId(
        Number(chatId),
        CHAT_CONSTANTS.MESSAGES_PER_PAGE,
        oldestMessageId
      )
      
      // Update profile if provided
      if (response.data.profile && onProfileUpdate) {
        onProfileUpdate(response.data.profile)
      }
      
      if (response.data.messages && response.data.messages.length > 0) {
        const sorted = [...response.data.messages].sort((a, b) => {
          const dateA = new Date(a.messageDate).getTime()
          const dateB = new Date(b.messageDate).getTime()
          return dateA - dateB
        })
        
        setAllMessages(prev => {
          const existingIds = new Set(prev.map(m => m.id))
          const newMessages = sorted.filter(m => !existingIds.has(m.id))
          return [...newMessages, ...prev]
        })
        
        setOldestMessageId(sorted[0].id)
        setHasMoreOlderMessages(sorted.length >= CHAT_CONSTANTS.MESSAGES_PER_PAGE)
        
        if (!hasMoreNewerMessages && sorted.length > 0) {
          setHasMoreNewerMessages(true)
        }
      } else {
        setHasMoreOlderMessages(false)
      }
    } catch (error) {
      console.error('[loadOlderMessages] Failed:', error)
    } finally {
      setIsLoadingOlderMessages(false)
    }
  }, [chatId, oldestMessageId, isLoadingOlderMessages, hasMoreNewerMessages, onProfileUpdate])

  const loadNewerMessages = useCallback(async () => {
    if (!chatId || !newestMessageId || isLoadingNewerMessages) return
    
    setIsLoadingNewerMessages(true)
    try {
      const response = await messageApi.getMessageHistoryByChatId(
        Number(chatId),
        CHAT_CONSTANTS.MESSAGES_PER_PAGE
      )
      
      if (response.data.messages && response.data.messages.length > 0) {
        const sorted = [...response.data.messages].sort((a, b) => {
          const dateA = new Date(a.messageDate).getTime()
          const dateB = new Date(b.messageDate).getTime()
          return dateA - dateB
        })
        
        const newerMessages = sorted.filter(msg => msg.id > newestMessageId)
        
        if (newerMessages.length > 0) {
          setAllMessages(prev => {
            const existingIds = new Set(prev.map(m => m.id))
            const newMessages = newerMessages.filter(m => !existingIds.has(m.id))
            return [...prev, ...newMessages]
          })
          
          setNewestMessageId(newerMessages[newerMessages.length - 1].id)
        } else {
          setHasMoreNewerMessages(false)
        }
        
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
  }, [chatId, newestMessageId, isLoadingNewerMessages])

  const resetPagination = useCallback(() => {
    setAllMessages([])
    setHasMoreOlderMessages(true)
    setHasMoreNewerMessages(false)
    setOldestMessageId(null)
    setNewestMessageId(null)
    setIsLoadingOlderMessages(false)
    setIsLoadingNewerMessages(false)
  }, [])

  const initializeMessages = useCallback((messages: Message[]) => {
    if (messages.length === 0) return

    const sorted = [...messages].sort((a, b) => {
      const dateA = new Date(a.messageDate).getTime()
      const dateB = new Date(b.messageDate).getTime()
      return dateA - dateB
    })
    
    setAllMessages(sorted)
    setOldestMessageId(sorted[0].id)
    setNewestMessageId(sorted[sorted.length - 1].id)
    setHasMoreOlderMessages(sorted.length >= CHAT_CONSTANTS.MESSAGES_PER_PAGE)
    setHasMoreNewerMessages(false)
  }, [])

  return {
    allMessages,
    setAllMessages,
    isLoadingOlderMessages,
    isLoadingNewerMessages,
    hasMoreOlderMessages,
    hasMoreNewerMessages,
    oldestMessageId,
    newestMessageId,
    loadOlderMessages,
    loadNewerMessages,
    resetPagination,
    initializeMessages,
  }
}

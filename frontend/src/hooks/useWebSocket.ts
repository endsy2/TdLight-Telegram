import { useEffect, useState, useCallback } from 'react'
import { websocketService, WebSocketCallback } from '@/services/websocket'

/**
 * Hook for WebSocket connection management
 */
export function useWebSocket() {
  const [connected, setConnected] = useState(false)
  const [connecting, setConnecting] = useState(false)

  useEffect(() => {
    const connect = async () => {
      if (websocketService.isConnected()) {
        setConnected(true)
        return
      }

      setConnecting(true)
      try {
        await websocketService.connect()
        setConnected(true)
      } catch (error) {
        console.error('[useWebSocket] Connection failed:', error)
        setConnected(false)
      } finally {
        setConnecting(false)
      }
    }

    connect()

    return () => {
      websocketService.disconnect()
      setConnected(false)
    }
  }, [])

  return { connected, connecting }
}

/**
 * Hook for subscribing to chats updates
 */
export function useChatsSubscription(callback: WebSocketCallback) {
  const { connected } = useWebSocket()

  useEffect(() => {
    if (!connected) return

    let unsubscribe: (() => void) | undefined

    // Subscribe asynchronously
    websocketService.subscribeToChats(callback).then((unsub) => {
      unsubscribe = unsub
    }).catch((error) => {
      console.error('[useChatsSubscription] Subscription failed:', error)
    })

    return () => {
      if (unsubscribe) {
        unsubscribe()
      }
    }
  }, [connected, callback])
}

/**
 * Hook for subscribing to specific chat
 */
export function useChatSubscription(chatId: number | null, callback: WebSocketCallback) {
  const { connected } = useWebSocket()

  useEffect(() => {
    if (!connected || !chatId) return

    let unsubscribe: (() => void) | undefined

    // Subscribe asynchronously
    websocketService.subscribeToChat(chatId, callback).then((unsub) => {
      unsubscribe = unsub
    }).catch((error) => {
      console.error('[useChatSubscription] Subscription failed:', error)
    })

    return () => {
      if (unsubscribe) {
        unsubscribe()
      }
    }
  }, [connected, chatId, callback])
}

/**
 * Hook for subscribing to download progress
 */
export function useDownloadSubscription(downloadId: string | null, callback: WebSocketCallback) {
  const { connected } = useWebSocket()

  useEffect(() => {
    if (!connected || !downloadId) return

    const unsubscribe = websocketService.subscribeToDownload(downloadId, callback)
    return unsubscribe
  }, [connected, downloadId, callback])
}

/**
 * Hook for subscribing to authentication events
 */
export function useAuthSubscription(callback: WebSocketCallback) {
  const { connected } = useWebSocket()

  useEffect(() => {
    if (!connected) return

    const unsubscribe = websocketService.subscribeToAuth(callback)
    return unsubscribe
  }, [connected, callback])
}

/**
 * Hook for subscribing to notifications
 */
export function useNotificationsSubscription(callback: WebSocketCallback) {
  const { connected } = useWebSocket()

  useEffect(() => {
    if (!connected) return

    const unsubscribe = websocketService.subscribeToNotifications(callback)
    return unsubscribe
  }, [connected, callback])
}

/**
 * Hook for sending typing indicator
 */
export function useTypingIndicator(chatId: number | null) {
  const { connected } = useWebSocket()

  const sendTyping = useCallback(() => {
    if (connected && chatId) {
      websocketService.sendTyping(chatId)
    }
  }, [connected, chatId])

  return sendTyping
}

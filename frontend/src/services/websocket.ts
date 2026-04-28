import { Client, IMessage } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

export interface WebSocketEvent {
  type: string
  data: any
  timestamp: string
  chatId?: number
  messageId?: number
}

export type WebSocketCallback = (event: WebSocketEvent) => void

class WebSocketService {
  private client: Client | null = null
  private connected = false
  private connecting = false
  private subscriptions: Map<string, any> = new Map()
  private connectionPromise: Promise<void> | null = null

  /**
   * Connect to WebSocket server
   */
  connect(): Promise<void> {
    // Return existing connection promise if already connecting
    if (this.connectionPromise) {
      return this.connectionPromise
    }

    // Return resolved promise if already connected
    if (this.connected && this.client?.connected) {
      return Promise.resolve()
    }

    this.connectionPromise = new Promise((resolve, reject) => {
      if (this.connecting) {
        return
      }

      this.connecting = true
      console.log('[WebSocket] Attempting to connect...')

      this.client = new Client({
        webSocketFactory: () => new SockJS('/ws'),
        debug: (str) => {
          // Only log important messages to reduce noise
          if (str.includes('ERROR') || str.includes('CONNECTED') || str.includes('DISCONNECTED')) {
            console.log('[WebSocket Debug]', str)
          }
        },
        reconnectDelay: 3000,
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,
        onConnect: () => {
          console.log('[WebSocket] ✓ Connected successfully')
          this.connected = true
          this.connecting = false
          this.connectionPromise = null
          resolve()
        },
        onDisconnect: () => {
          console.log('[WebSocket] Disconnected')
          this.connected = false
          this.connecting = false
          this.connectionPromise = null
          this.subscriptions.clear()
        },
        onStompError: (frame) => {
          console.error('[WebSocket] STOMP Error:', frame.headers['message'])
          console.error('[WebSocket] Details:', frame.body)
          this.connecting = false
          this.connectionPromise = null
          reject(new Error(frame.headers['message']))
        },
        onWebSocketError: (event) => {
          console.error('[WebSocket] Connection Error:', event)
          this.connecting = false
          this.connectionPromise = null
          reject(new Error('WebSocket connection failed'))
        },
      })

      try {
        this.client.activate()
      } catch (error) {
        console.error('[WebSocket] Activation failed:', error)
        this.connecting = false
        this.connectionPromise = null
        reject(error)
      }
    })

    return this.connectionPromise
  }

  /**
   * Disconnect from WebSocket server
   */
  disconnect(): void {
    if (this.client) {
      this.client.deactivate()
      this.client = null
      this.connected = false
      this.subscriptions.clear()
    }
  }

  /**
   * Check if connected
   */
  isConnected(): boolean {
    return this.connected
  }

  /**
   * Subscribe to chats updates
   */
  async subscribeToChats(callback: WebSocketCallback): Promise<() => void> {
    const destination = '/topic/chats'
    
    // Ensure we're connected first
    if (!this.connected) {
      try {
        await this.connect()
      } catch (error) {
        console.error('[WebSocket] Failed to connect for chats subscription:', error)
        return () => {}
      }
    }

    if (!this.client) {
      console.warn('[WebSocket] Client not initialized')
      return () => {}
    }

    const subscription = this.client.subscribe(destination, (message: IMessage) => {
      try {
        const event: WebSocketEvent = JSON.parse(message.body)
        callback(event)
      } catch (error) {
        console.error('[WebSocket] Error parsing chats message:', error)
      }
    })

    this.subscriptions.set(destination, subscription)
    console.log('[WebSocket] ✓ Subscribed to chats')

    // Request initial data
    this.client.publish({
      destination: '/app/chats/subscribe',
      body: JSON.stringify({}),
    })

    return () => {
      subscription.unsubscribe()
      this.subscriptions.delete(destination)
      console.log('[WebSocket] Unsubscribed from chats')
    }
  }

  /**
   * Subscribe to specific chat messages
   */
  async subscribeToChat(chatId: number, callback: WebSocketCallback): Promise<() => void> {
    const destination = `/topic/chat/${chatId}`
    
    // Ensure we're connected first
    if (!this.connected) {
      try {
        await this.connect()
      } catch (error) {
        console.error('[WebSocket] Failed to connect for chat subscription:', error)
        return () => {}
      }
    }

    if (!this.client) {
      console.warn('[WebSocket] Client not initialized')
      return () => {}
    }

    const subscription = this.client.subscribe(destination, (message: IMessage) => {
      try {
        const event: WebSocketEvent = JSON.parse(message.body)
        callback(event)
      } catch (error) {
        console.error('[WebSocket] Error parsing chat message:', error)
      }
    })

    this.subscriptions.set(destination, subscription)
    console.log(`[WebSocket] ✓ Subscribed to chat ${chatId}`)

    // Notify server of subscription
    this.client.publish({
      destination: `/app/chat/${chatId}/subscribe`,
      body: JSON.stringify({}),
    })

    return () => {
      subscription.unsubscribe()
      this.subscriptions.delete(destination)
      console.log(`[WebSocket] Unsubscribed from chat ${chatId}`)
    }
  }

  /**
   * Subscribe to download progress
   */
  subscribeToDownload(downloadId: string, callback: WebSocketCallback): () => void {
    const destination = `/topic/downloads/${downloadId}`
    
    if (!this.client || !this.connected) {
      console.warn('[WebSocket] Not connected, cannot subscribe to download', downloadId)
      return () => {}
    }

    const subscription = this.client.subscribe(destination, (message: IMessage) => {
      try {
        const event: WebSocketEvent = JSON.parse(message.body)
        callback(event)
      } catch (error) {
        console.error('[WebSocket] Error parsing download message:', error)
      }
    })

    this.subscriptions.set(destination, subscription)

    return () => {
      subscription.unsubscribe()
      this.subscriptions.delete(destination)
    }
  }

  /**
   * Subscribe to authentication events
   */
  subscribeToAuth(callback: WebSocketCallback): () => void {
    const destination = '/topic/auth'
    
    if (!this.client || !this.connected) {
      console.warn('[WebSocket] Not connected, cannot subscribe to auth')
      return () => {}
    }

    const subscription = this.client.subscribe(destination, (message: IMessage) => {
      try {
        const event: WebSocketEvent = JSON.parse(message.body)
        callback(event)
      } catch (error) {
        console.error('[WebSocket] Error parsing auth message:', error)
      }
    })

    this.subscriptions.set(destination, subscription)

    return () => {
      subscription.unsubscribe()
      this.subscriptions.delete(destination)
    }
  }

  /**
   * Subscribe to notifications
   */
  subscribeToNotifications(callback: WebSocketCallback): () => void {
    const destination = '/topic/notifications'
    
    if (!this.client || !this.connected) {
      console.warn('[WebSocket] Not connected, cannot subscribe to notifications')
      return () => {}
    }

    const subscription = this.client.subscribe(destination, (message: IMessage) => {
      try {
        const event: WebSocketEvent = JSON.parse(message.body)
        callback(event)
      } catch (error) {
        console.error('[WebSocket] Error parsing notification message:', error)
      }
    })

    this.subscriptions.set(destination, subscription)

    return () => {
      subscription.unsubscribe()
      this.subscriptions.delete(destination)
    }
  }

  /**
   * Subscribe to errors
   */
  subscribeToErrors(callback: WebSocketCallback): () => void {
    const destination = '/topic/errors'
    
    if (!this.client || !this.connected) {
      console.warn('[WebSocket] Not connected, cannot subscribe to errors')
      return () => {}
    }

    const subscription = this.client.subscribe(destination, (message: IMessage) => {
      try {
        const event: WebSocketEvent = JSON.parse(message.body)
        callback(event)
      } catch (error) {
        console.error('[WebSocket] Error parsing error message:', error)
      }
    })

    this.subscriptions.set(destination, subscription)

    return () => {
      subscription.unsubscribe()
      this.subscriptions.delete(destination)
    }
  }

  /**
   * Send typing indicator
   */
  sendTyping(chatId: number): void {
    if (!this.client || !this.connected) {
      console.warn('[WebSocket] Not connected, cannot send typing indicator')
      return
    }

    this.client.publish({
      destination: `/app/chat/${chatId}/typing`,
      body: JSON.stringify({}),
    })
  }
}

// Export singleton instance
export const websocketService = new WebSocketService()

import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { User, Chat, Message, ChatFilter } from '@/types'

interface AppState {
  // User state
  currentUser: User | null
  setCurrentUser: (user: User | null) => void

  // Chat state
  selectedChat: Chat | null
  setSelectedChat: (chat: Chat | null) => void
  chats: Chat[]
  setChats: (chats: Chat[]) => void
  addChat: (chat: Chat) => void
  updateChat: (chatId: number, updates: Partial<Chat>) => void

  // Message state
  messages: Record<number, Message[]> // chatId -> messages
  setMessages: (chatId: number, messages: Message[]) => void
  addMessage: (chatId: number, message: Message) => void
  prependMessages: (chatId: number, messages: Message[]) => void

  // UI state
  isSidebarOpen: boolean
  toggleSidebar: () => void
  setSidebarOpen: (open: boolean) => void

  chatFilter: ChatFilter
  setChatFilter: (filter: ChatFilter) => void

  searchQuery: string
  setSearchQuery: (query: string) => void

  // Theme
  theme: 'light' | 'dark' | 'system'
  setTheme: (theme: 'light' | 'dark' | 'system') => void

  // Loading states
  isLoadingChats: boolean
  setLoadingChats: (loading: boolean) => void

  isLoadingMessages: boolean
  setLoadingMessages: (loading: boolean) => void

  // Auth state
  isAuthenticated: boolean
  setAuthenticated: (authenticated: boolean) => void

  authState: string
  setAuthState: (state: string) => void

  // Reset
  reset: () => void
}

const initialState = {
  currentUser: null,
  selectedChat: null,
  chats: [],
  messages: {},
  isSidebarOpen: true,
  chatFilter: 'all' as ChatFilter,
  searchQuery: '',
  theme: 'system' as const,
  isLoadingChats: false,
  isLoadingMessages: false,
  isAuthenticated: false,
  authState: 'UNKNOWN',
}

export const useStore = create<AppState>()(
  persist(
    (set) => ({
      ...initialState,

      // User actions
      setCurrentUser: (user) => set({ currentUser: user }),

      // Chat actions
      setSelectedChat: (chat) => set({ selectedChat: chat }),
      
      setChats: (chats) => set({ chats }),
      
      addChat: (chat) => set((state) => ({
        chats: [chat, ...state.chats.filter((c) => c.id !== chat.id)],
      })),
      
      updateChat: (chatId, updates) => set((state) => ({
        chats: state.chats.map((chat) =>
          chat.id === chatId ? { ...chat, ...updates } : chat
        ),
        selectedChat:
          state.selectedChat?.id === chatId
            ? { ...state.selectedChat, ...updates }
            : state.selectedChat,
      })),

      // Message actions
      setMessages: (chatId, messages) => set((state) => ({
        messages: { ...state.messages, [chatId]: messages },
      })),
      
      addMessage: (chatId, message) => set((state) => {
        const existingMessages = state.messages[chatId] || []
        const messageExists = existingMessages.some((m) => m.id === message.id)
        
        if (messageExists) {
          return state
        }
        
        return {
          messages: {
            ...state.messages,
            [chatId]: [...existingMessages, message],
          },
        }
      }),
      
      prependMessages: (chatId, messages) => set((state) => {
        const existingMessages = state.messages[chatId] || []
        const newMessages = messages.filter(
          (msg) => !existingMessages.some((m) => m.id === msg.id)
        )
        
        return {
          messages: {
            ...state.messages,
            [chatId]: [...newMessages, ...existingMessages],
          },
        }
      }),

      // UI actions
      toggleSidebar: () => set((state) => ({ isSidebarOpen: !state.isSidebarOpen })),
      setSidebarOpen: (open) => set({ isSidebarOpen: open }),
      
      setChatFilter: (filter) => set({ chatFilter: filter }),
      setSearchQuery: (query) => set({ searchQuery: query }),
      setTheme: (theme) => set({ theme }),

      // Loading actions
      setLoadingChats: (loading) => set({ isLoadingChats: loading }),
      setLoadingMessages: (loading) => set({ isLoadingMessages: loading }),

      // Auth actions
      setAuthenticated: (authenticated) => set({ isAuthenticated: authenticated }),
      setAuthState: (authState) => set({ authState }),

      // Reset
      reset: () => set(initialState),
    }),
    {
      name: 'tdlight-storage', // localStorage key
      partialize: (state) => ({
        // Only persist these fields
        isAuthenticated: state.isAuthenticated,
        authState: state.authState,
        currentUser: state.currentUser,
        theme: state.theme,
      }),
    }
  )
)

// Selectors
export const useCurrentUser = () => useStore((state) => state.currentUser)
export const useSelectedChat = () => useStore((state) => state.selectedChat)
export const useChats = () => useStore((state) => state.chats)
export const useMessages = (chatId: number) =>
  useStore((state) => state.messages[chatId] || [])
export const useIsSidebarOpen = () => useStore((state) => state.isSidebarOpen)
export const useChatFilter = () => useStore((state) => state.chatFilter)
export const useSearchQuery = () => useStore((state) => state.searchQuery)
export const useTheme = () => useStore((state) => state.theme)
export const useIsAuthenticated = () => useStore((state) => state.isAuthenticated)
export const useAuthState = () => useStore((state) => state.authState)

# Frontend Refactoring Guide

## Overview
This document explains the refactored frontend structure for better maintainability, debugging, and code organization.

## New Structure

```
frontend/src/
├── components/
│   ├── chat/              # Chat-specific components
│   │   ├── ChatHeader.tsx
│   │   ├── UserAvatar.tsx
│   │   ├── MessageStatusIcon.tsx
│   │   └── ... (more to be added)
│   ├── EmojiPicker.tsx
│   ├── ImageGallery.tsx
│   └── ...
├── hooks/                 # Custom React hooks
│   ├── useChatPagination.ts    # Message pagination logic
│   ├── useScrollManagement.ts  # Scroll behavior management
│   ├── useWebSocket.ts
│   └── ...
├── utils/                 # Utility functions
│   ├── dateUtils.ts       # Date/time formatting
│   ├── chatUtils.ts       # Chat-related utilities
│   └── ...
├── constants/             # Application constants
│   ├── chat.ts            # Chat-related constants
│   └── ...
├── services/              # API services
│   ├── api.ts
│   └── websocket.ts
├── store/                 # State management
│   └── useStore.ts
├── types/                 # TypeScript types
│   └── index.ts
└── pages/                 # Page components
    ├── ChatPage.tsx
    └── ...
```

## Key Improvements

### 1. **Separation of Concerns**
- **Components**: Reusable UI components
- **Hooks**: Business logic and state management
- **Utils**: Pure functions for data transformation
- **Constants**: Configuration and magic numbers

### 2. **Custom Hooks**

#### `useChatPagination`
Manages message pagination logic:
- Loading older/newer messages
- Tracking pagination state
- Handling message deduplication

```typescript
const {
  allMessages,
  loadOlderMessages,
  loadNewerMessages,
  hasMoreOlderMessages,
  resetPagination,
} = useChatPagination({ chatId, onProfileUpdate })
```

#### `useScrollManagement`
Handles scroll behavior:
- Detecting scroll position
- Triggering pagination
- Auto-scrolling to bottom
- Maintaining scroll position

```typescript
const {
  messagesContainerRef,
  handleMessageScroll,
  scrollToBottom,
  showScrollToBottom,
} = useScrollManagement({
  hasMoreOlderMessages,
  onLoadOlder: loadOlderMessages,
  messages,
})
```

### 3. **Reusable Components**

#### `UserAvatar`
Displays user profile pictures with fallback:
```typescript
<UserAvatar
  photoUrl={user.photoUrl}
  name={user.name}
  size="md"
  showOnlineStatus
  isOnline={user.isOnline}
/>
```

#### `ChatHeader`
Standardized chat header with actions:
```typescript
<ChatHeader
  chat={selectedChat}
  onSearchClick={handleSearch}
  onCallClick={handleCall}
/>
```

#### `MessageStatusIcon`
Shows message delivery status:
```typescript
<MessageStatusIcon status={message.status} />
```

### 4. **Utility Functions**

#### Date Utilities (`dateUtils.ts`)
- `formatTime()` - Smart time formatting (now, 5m, 2h, Mon, Jan 15)
- `formatDate()` - Date formatting (Today, Yesterday, Jan 15, 2024)
- `formatDuration()` - Duration formatting (2:35)

#### Chat Utilities (`chatUtils.ts`)
- `getMessagePreview()` - Generate message preview with emojis
- `getUserStatusColor()` - Get status indicator color

### 5. **Constants**

All magic numbers and configuration in one place:
```typescript
export const CHAT_CONSTANTS = {
  MESSAGES_PER_PAGE: 25,
  CHATS_PER_PAGE: 100,
  SCROLL_THRESHOLD_TOP: 200,
  SCROLL_THRESHOLD_BOTTOM: 100,
  MAX_FILE_SIZE_MB: 2000,
  CACHE_TIME_MS: 30000,
}
```

## Benefits

### 1. **Easier Debugging**
- Isolated logic in hooks makes testing easier
- Console logs can be added to specific hooks
- Clear separation between UI and logic

### 2. **Better Maintainability**
- Small, focused files (< 200 lines each)
- Single responsibility principle
- Easy to locate and fix bugs

### 3. **Improved Reusability**
- Components can be used across different pages
- Hooks can be shared between features
- Utils are pure functions (easy to test)

### 4. **Type Safety**
- All utilities and hooks are fully typed
- Better IDE autocomplete
- Catch errors at compile time

### 5. **Performance**
- `useCallback` and `useMemo` used appropriately
- Prevents unnecessary re-renders
- Optimized scroll handling

## Migration Guide

### Before (ChatPage.tsx - 1827 lines)
```typescript
// Everything in one file
const ChatPage = () => {
  // 100+ lines of state
  // 500+ lines of functions
  // 1000+ lines of JSX
}
```

### After (ChatPage.tsx - ~400 lines)
```typescript
const ChatPage = () => {
  // Use custom hooks
  const pagination = useChatPagination({ chatId })
  const scroll = useScrollManagement({ ...pagination })
  
  // Render with extracted components
  return (
    <>
      <ChatHeader chat={selectedChat} />
      <MessageList messages={pagination.allMessages} />
      <MessageInput onSend={handleSend} />
    </>
  )
}
```

## Next Steps

### Components to Extract
1. `MessageList` - Message rendering logic
2. `MessageItem` - Individual message display
3. `MessageInput` - Message composition area
4. `ChatListItem` - Chat list item component
5. `MediaMessage` - Photo/Video/Document rendering
6. `VoiceMessage` - Voice message player
7. `FileUploadArea` - Drag & drop file upload

### Hooks to Create
1. `useMessageSending` - Message sending logic
2. `useFileUpload` - File upload handling
3. `useVoiceRecording` - Voice recording logic
4. `useMessageActions` - Reply, forward, delete actions
5. `useChatList` - Chat list pagination

### Utils to Add
1. `fileUtils.ts` - File size formatting, validation
2. `mediaUtils.ts` - Media URL handling
3. `errorUtils.ts` - Error message formatting
4. `validationUtils.ts` - Input validation

## Best Practices

### 1. Component Size
- Keep components under 200 lines
- Extract complex logic to hooks
- Use composition over inheritance

### 2. Hook Design
- One responsibility per hook
- Return object with clear names
- Document parameters and return values

### 3. Naming Conventions
- Components: PascalCase (`UserAvatar.tsx`)
- Hooks: camelCase with `use` prefix (`useChatPagination.ts`)
- Utils: camelCase (`formatTime`)
- Constants: UPPER_SNAKE_CASE (`MESSAGES_PER_PAGE`)

### 4. File Organization
- Group related files in folders
- Keep folder depth shallow (max 3 levels)
- Use index files for clean imports

### 5. Error Handling
- Always wrap async operations in try-catch
- Log errors with context
- Show user-friendly error messages
- Don't let errors crash the app

## Debugging Tips

### 1. Hook Debugging
```typescript
// Add console.group for better log organization
useEffect(() => {
  console.group('[useChatPagination] State Update')
  console.log('Messages:', allMessages.length)
  console.log('Has more:', hasMoreOlderMessages)
  console.groupEnd()
}, [allMessages, hasMoreOlderMessages])
```

### 2. Component Debugging
```typescript
// Use React DevTools Profiler
// Add data attributes for testing
<div data-testid="message-item" data-message-id={message.id}>
```

### 3. Performance Debugging
```typescript
// Use React.memo for expensive components
export default React.memo(MessageItem, (prev, next) => {
  return prev.message.id === next.message.id
})
```

## Testing Strategy

### 1. Unit Tests
- Test utility functions (pure functions)
- Test custom hooks with `@testing-library/react-hooks`
- Mock API calls

### 2. Integration Tests
- Test component interactions
- Test hook combinations
- Test user flows

### 3. E2E Tests
- Test critical user journeys
- Test with real backend
- Test error scenarios

## Performance Optimization

### 1. Virtualization
- Use `react-virtuoso` for long message lists
- Render only visible messages
- Reduce DOM nodes

### 2. Memoization
- Use `React.memo` for list items
- Use `useMemo` for expensive calculations
- Use `useCallback` for event handlers

### 3. Code Splitting
- Lazy load pages
- Lazy load heavy components
- Use dynamic imports

## Conclusion

This refactoring makes the codebase:
- ✅ Easier to understand
- ✅ Easier to debug
- ✅ Easier to test
- ✅ Easier to maintain
- ✅ More performant
- ✅ More scalable

The investment in refactoring pays off through:
- Faster development
- Fewer bugs
- Better developer experience
- Easier onboarding for new developers

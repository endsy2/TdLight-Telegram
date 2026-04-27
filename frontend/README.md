# TDLight Pro Frontend

Modern React + TypeScript frontend for TDLight Pro with complete backend integration.

## Quick Start

```bash
npm install
npm run dev
```

Access at: http://localhost:3000

## Features

- ✅ Authentication (phone, code, password)
- ✅ Chat management
- ✅ Group operations
- ✅ Message sending (text, voice)
- ✅ Video downloads with progress
- ✅ MinIO file storage
- ✅ Real-time updates

## Pages

1. **Chats** - `/chats` - View and manage chats
2. **Groups** - `/groups` - Group management
3. **Downloads** - `/downloads` - Track downloads
4. **Files** - `/files` - MinIO file storage
5. **Settings** - `/settings` - User settings

## API Integration

All backend endpoints are integrated. See `src/services/api.ts` for details.

## Development

```bash
# Start backend
./gradlew bootRun

# Start frontend
cd frontend
npm run dev
```

## Build

```bash
npm run build
npm run preview
```

See parent README for more details.

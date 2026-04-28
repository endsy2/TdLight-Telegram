import { Routes, Route, Navigate } from 'react-router-dom'
import { useEffect } from 'react'
import MainLayout from '@/layouts/MainLayout'
import AuthPage from '@/pages/AuthPage'
import ChatPage from '@/pages/ChatPage'
import GroupsPage from '@/pages/GroupsPage'
import DownloadsPage from '@/pages/DownloadsPage'
import FilesPage from '@/pages/FilesPage'
import SettingsPage from '@/pages/SettingsPage'
import NotFoundPage from '@/pages/NotFoundPage'
import { useIsAuthenticated, useStore } from '@/store/useStore'
import { authApi, userApi } from '@/services/api'

function PrivateRoute({ children }: { children: React.ReactNode }) {
  const isAuthenticated = useIsAuthenticated()
  return isAuthenticated ? <>{children}</> : <Navigate to="/auth" replace />
}

function App() {
  const isAuthenticated = useIsAuthenticated()
  const setAuthenticated = useStore((state) => state.setAuthenticated)
  const setCurrentUser = useStore((state) => state.setCurrentUser)
  const setAuthState = useStore((state) => state.setAuthState)

  // Check authentication status on app load
  useEffect(() => {
    const checkAuth = async () => {
      try {
        const response = await authApi.getStatus()
        const status = response.data

        setAuthState(status.authenticationState)

        if (status.isReady && !isAuthenticated) {
          // Backend is authenticated, sync with frontend
          try {
            const userResponse = await userApi.getMe()
            setCurrentUser(userResponse.data)
            setAuthenticated(true)
          } catch (err) {
            console.error('Failed to fetch user:', err)
          }
        } else if (!status.isReady && isAuthenticated) {
          // Backend lost authentication, clear frontend state
          setAuthenticated(false)
          setCurrentUser(null)
        }
      } catch (err) {
        console.error('Failed to check auth status:', err)
        // Don't clear auth state on error, might just be temporary network issue
      }
    }

    checkAuth()
  }, [])

  return (
    <Routes>
      <Route path="/auth" element={<AuthPage />} />
      <Route
        path="/"
        element={
          <PrivateRoute>
            <MainLayout />
          </PrivateRoute>
        }
      >
        <Route index element={<Navigate to="/chats" replace />} />
        <Route path="chats" element={<ChatPage />} />
        <Route path="chats/:chatId" element={<ChatPage />} />
        <Route path="groups" element={<GroupsPage />} />
        <Route path="downloads" element={<DownloadsPage />} />
        <Route path="files" element={<FilesPage />} />
        <Route path="settings" element={<SettingsPage />} />
      </Route>
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}

export default App

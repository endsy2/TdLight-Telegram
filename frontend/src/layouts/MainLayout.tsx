import { Outlet, NavLink, useNavigate } from 'react-router-dom'
import {
  MessageSquare,
  Users,
  Download,
  FolderOpen,
  Settings,
  LogOut,
  Menu,
  X,
} from 'lucide-react'
import { useStore, useCurrentUser } from '@/store/useStore'
import { useState } from 'react'

const navigation = [
  { name: 'Chats', href: '/chats', icon: MessageSquare },
  { name: 'Groups', href: '/groups', icon: Users },
  { name: 'Downloads', href: '/downloads', icon: Download },
  { name: 'Files', href: '/files', icon: FolderOpen },
  { name: 'Settings', href: '/settings', icon: Settings },
]

export default function MainLayout() {
  const navigate = useNavigate()
  const currentUser = useCurrentUser()
  const reset = useStore((state: any) => state.reset)
  const [sidebarOpen, setSidebarOpen] = useState(true)

  const handleLogout = () => {
    reset()
    navigate('/auth')
  }

  return (
    <div className="h-screen flex bg-gray-100">
      {/* Sidebar */}
      <div
        className={`${
          sidebarOpen ? 'w-64' : 'w-20'
        } bg-white border-r flex flex-col transition-all duration-300`}
      >
        {/* Header */}
        <div className="h-16 border-b flex items-center justify-between px-4">
          {sidebarOpen && (
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 bg-blue-500 rounded-full flex items-center justify-center text-white font-bold">
                T
              </div>
              <div>
                <h1 className="font-bold text-gray-900">TDLight Pro</h1>
                <p className="text-xs text-gray-500">Telegram Client</p>
              </div>
            </div>
          )}
          <button
            onClick={() => setSidebarOpen(!sidebarOpen)}
            className="p-2 hover:bg-gray-100 rounded-lg transition-colors"
          >
            {sidebarOpen ? (
              <X className="w-5 h-5 text-gray-600" />
            ) : (
              <Menu className="w-5 h-5 text-gray-600" />
            )}
          </button>
        </div>

        {/* Navigation */}
        <nav className="flex-1 p-4 space-y-1">
          {navigation.map((item) => {
            const Icon = item.icon
            return (
              <NavLink
                key={item.name}
                to={item.href}
                className={({ isActive }) =>
                  `flex items-center gap-3 px-3 py-2 rounded-lg transition-colors ${
                    isActive
                      ? 'bg-blue-50 text-blue-600'
                      : 'text-gray-700 hover:bg-gray-100'
                  }`
                }
              >
                <Icon className="w-5 h-5 flex-shrink-0" />
                {sidebarOpen && <span className="font-medium">{item.name}</span>}
              </NavLink>
            )
          })}
        </nav>

        {/* User Section */}
        <div className="border-t p-4">
          {currentUser && sidebarOpen && (
            <div className="mb-3 px-3 py-2 bg-gray-50 rounded-lg">
              <p className="font-medium text-gray-900 text-sm truncate">
                {currentUser.firstName} {currentUser.lastName}
              </p>
              <p className="text-xs text-gray-500 truncate">
                {currentUser.username ? `@${currentUser.username}` : currentUser.phoneNumber}
              </p>
            </div>
          )}
          <button
            onClick={handleLogout}
            className="w-full flex items-center gap-3 px-3 py-2 text-red-600 hover:bg-red-50 rounded-lg transition-colors"
          >
            <LogOut className="w-5 h-5 flex-shrink-0" />
            {sidebarOpen && <span className="font-medium">Logout</span>}
          </button>
        </div>
      </div>

      {/* Main Content */}
      <div className="flex-1 overflow-hidden">
        <Outlet />
      </div>
    </div>
  )
}

import { useState } from 'react'
import { useQuery, useMutation } from '@tanstack/react-query'
import { User, Bell, Lock, Palette, LogOut, Loader } from 'lucide-react'
import { authApi, userApi } from '@/services/api'
import { useStore } from '@/store/useStore'
import { useNavigate } from 'react-router-dom'
import toast from 'react-hot-toast'

export default function SettingsPage() {
  const navigate = useNavigate()
  const currentUser = useStore((state) => state.currentUser)
  const setAuthenticated = useStore((state) => state.setAuthenticated)
  const setCurrentUser = useStore((state) => state.setCurrentUser)
  const theme = useStore((state) => state.theme)
  const setTheme = useStore((state) => state.setTheme)

  const [activeTab, setActiveTab] = useState<'profile' | 'notifications' | 'privacy' | 'appearance'>('profile')

  // Fetch user data
  const { data: user, isLoading } = useQuery({
    queryKey: ['currentUser'],
    queryFn: async () => {
      const response = await userApi.getMe()
      return response.data
    },
    initialData: currentUser || undefined,
  })

  // Logout mutation
  const logoutMutation = useMutation({
    mutationFn: () => authApi.logout(),
    onSuccess: () => {
      setAuthenticated(false)
      setCurrentUser(null)
      toast.success('Logged out successfully')
      navigate('/auth')
    },
    onError: () => {
      toast.error('Failed to logout')
    },
  })

  const handleLogout = () => {
    if (confirm('Are you sure you want to logout?')) {
      logoutMutation.mutate()
    }
  }

  const tabs = [
    { id: 'profile', label: 'Profile', icon: User },
    { id: 'notifications', label: 'Notifications', icon: Bell },
    { id: 'privacy', label: 'Privacy', icon: Lock },
    { id: 'appearance', label: 'Appearance', icon: Palette },
  ]

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-screen">
        <Loader className="w-8 h-8 animate-spin text-blue-500" />
      </div>
    )
  }

  return (
    <div className="flex h-screen bg-gray-50">
      {/* Sidebar */}
      <div className="w-64 bg-white border-r">
        <div className="p-4 border-b">
          <h2 className="text-xl font-semibold">Settings</h2>
        </div>
        <nav className="p-2">
          {tabs.map((tab) => {
            const Icon = tab.icon
            return (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id as any)}
                className={`w-full flex items-center gap-3 px-4 py-3 rounded-lg transition-colors ${
                  activeTab === tab.id
                    ? 'bg-blue-50 text-blue-600'
                    : 'text-gray-700 hover:bg-gray-50'
                }`}
              >
                <Icon className="w-5 h-5" />
                <span className="font-medium">{tab.label}</span>
              </button>
            )
          })}
          <button
            onClick={handleLogout}
            disabled={logoutMutation.isPending}
            className="w-full flex items-center gap-3 px-4 py-3 rounded-lg text-red-600 hover:bg-red-50 transition-colors mt-4"
          >
            {logoutMutation.isPending ? (
              <Loader className="w-5 h-5 animate-spin" />
            ) : (
              <LogOut className="w-5 h-5" />
            )}
            <span className="font-medium">Logout</span>
          </button>
        </nav>
      </div>

      {/* Content */}
      <div className="flex-1 overflow-y-auto">
        <div className="max-w-4xl mx-auto p-8">
          {activeTab === 'profile' && (
            <div className="space-y-6">
              <div>
                <h3 className="text-2xl font-bold text-gray-900 mb-2">Profile</h3>
                <p className="text-gray-500">Manage your account information</p>
              </div>

              <div className="bg-white rounded-lg shadow-sm border p-6">
                <div className="flex items-center gap-6 mb-6">
                  <div className="w-24 h-24 rounded-full bg-gradient-to-br from-blue-400 to-blue-600 flex items-center justify-center text-white text-3xl font-bold">
                    {user?.firstName.charAt(0).toUpperCase()}
                  </div>
                  <div>
                    <h4 className="text-xl font-semibold text-gray-900">
                      {user?.firstName} {user?.lastName}
                    </h4>
                    {user?.username && (
                      <p className="text-gray-500 mt-1">@{user.username}</p>
                    )}
                    {user?.phoneNumber && (
                      <p className="text-gray-500 mt-1">{user.phoneNumber}</p>
                    )}
                  </div>
                </div>

                <div className="space-y-4">
                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-2">
                        First Name
                      </label>
                      <input
                        type="text"
                        value={user?.firstName || ''}
                        readOnly
                        className="w-full px-4 py-2 border border-gray-300 rounded-lg bg-gray-50"
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-2">
                        Last Name
                      </label>
                      <input
                        type="text"
                        value={user?.lastName || ''}
                        readOnly
                        className="w-full px-4 py-2 border border-gray-300 rounded-lg bg-gray-50"
                      />
                    </div>
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">
                      Username
                    </label>
                    <input
                      type="text"
                      value={user?.username || 'Not set'}
                      readOnly
                      className="w-full px-4 py-2 border border-gray-300 rounded-lg bg-gray-50"
                    />
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">
                      Phone Number
                    </label>
                    <input
                      type="text"
                      value={user?.phoneNumber || ''}
                      readOnly
                      className="w-full px-4 py-2 border border-gray-300 rounded-lg bg-gray-50"
                    />
                  </div>

                  <div className="flex items-center gap-4 pt-4">
                    {user?.isVerified && (
                      <span className="flex items-center gap-2 text-blue-600">
                        <span className="text-xl">✓</span>
                        <span className="font-medium">Verified Account</span>
                      </span>
                    )}
                    {user?.isPremium && (
                      <span className="bg-gradient-to-r from-purple-500 to-pink-500 text-white px-3 py-1 rounded-full text-sm font-medium">
                        Premium
                      </span>
                    )}
                  </div>
                </div>
              </div>
            </div>
          )}

          {activeTab === 'notifications' && (
            <div className="space-y-6">
              <div>
                <h3 className="text-2xl font-bold text-gray-900 mb-2">Notifications</h3>
                <p className="text-gray-500">Manage your notification preferences</p>
              </div>

              <div className="bg-white rounded-lg shadow-sm border divide-y">
                <div className="p-6 flex items-center justify-between">
                  <div>
                    <h4 className="font-medium text-gray-900">Message Notifications</h4>
                    <p className="text-sm text-gray-500 mt-1">Receive notifications for new messages</p>
                  </div>
                  <label className="relative inline-flex items-center cursor-pointer">
                    <input type="checkbox" className="sr-only peer" defaultChecked />
                    <div className="w-11 h-6 bg-gray-200 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-blue-300 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-blue-600"></div>
                  </label>
                </div>

                <div className="p-6 flex items-center justify-between">
                  <div>
                    <h4 className="font-medium text-gray-900">Group Notifications</h4>
                    <p className="text-sm text-gray-500 mt-1">Receive notifications from groups</p>
                  </div>
                  <label className="relative inline-flex items-center cursor-pointer">
                    <input type="checkbox" className="sr-only peer" defaultChecked />
                    <div className="w-11 h-6 bg-gray-200 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-blue-300 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-blue-600"></div>
                  </label>
                </div>

                <div className="p-6 flex items-center justify-between">
                  <div>
                    <h4 className="font-medium text-gray-900">Sound</h4>
                    <p className="text-sm text-gray-500 mt-1">Play sound for notifications</p>
                  </div>
                  <label className="relative inline-flex items-center cursor-pointer">
                    <input type="checkbox" className="sr-only peer" defaultChecked />
                    <div className="w-11 h-6 bg-gray-200 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-blue-300 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-blue-600"></div>
                  </label>
                </div>
              </div>
            </div>
          )}

          {activeTab === 'privacy' && (
            <div className="space-y-6">
              <div>
                <h3 className="text-2xl font-bold text-gray-900 mb-2">Privacy & Security</h3>
                <p className="text-gray-500">Control your privacy settings</p>
              </div>

              <div className="bg-white rounded-lg shadow-sm border divide-y">
                <div className="p-6">
                  <h4 className="font-medium text-gray-900 mb-4">Who can see my phone number?</h4>
                  <select className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500">
                    <option>Everyone</option>
                    <option>My Contacts</option>
                    <option>Nobody</option>
                  </select>
                </div>

                <div className="p-6">
                  <h4 className="font-medium text-gray-900 mb-4">Who can add me to groups?</h4>
                  <select className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500">
                    <option>Everyone</option>
                    <option>My Contacts</option>
                    <option>Nobody</option>
                  </select>
                </div>

                <div className="p-6">
                  <h4 className="font-medium text-gray-900 mb-2">Two-Step Verification</h4>
                  <p className="text-sm text-gray-500 mb-4">
                    Add an extra layer of security to your account
                  </p>
                  <button className="px-4 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600">
                    Enable
                  </button>
                </div>
              </div>
            </div>
          )}

          {activeTab === 'appearance' && (
            <div className="space-y-6">
              <div>
                <h3 className="text-2xl font-bold text-gray-900 mb-2">Appearance</h3>
                <p className="text-gray-500">Customize how the app looks</p>
              </div>

              <div className="bg-white rounded-lg shadow-sm border p-6">
                <h4 className="font-medium text-gray-900 mb-4">Theme</h4>
                <div className="grid grid-cols-3 gap-4">
                  <button
                    onClick={() => setTheme('light')}
                    className={`p-4 border-2 rounded-lg transition-colors ${
                      theme === 'light'
                        ? 'border-blue-500 bg-blue-50'
                        : 'border-gray-200 hover:border-gray-300'
                    }`}
                  >
                    <div className="w-full h-20 bg-white rounded mb-2 border"></div>
                    <p className="text-sm font-medium text-center">Light</p>
                  </button>
                  <button
                    onClick={() => setTheme('dark')}
                    className={`p-4 border-2 rounded-lg transition-colors ${
                      theme === 'dark'
                        ? 'border-blue-500 bg-blue-50'
                        : 'border-gray-200 hover:border-gray-300'
                    }`}
                  >
                    <div className="w-full h-20 bg-gray-900 rounded mb-2"></div>
                    <p className="text-sm font-medium text-center">Dark</p>
                  </button>
                  <button
                    onClick={() => setTheme('system')}
                    className={`p-4 border-2 rounded-lg transition-colors ${
                      theme === 'system'
                        ? 'border-blue-500 bg-blue-50'
                        : 'border-gray-200 hover:border-gray-300'
                    }`}
                  >
                    <div className="w-full h-20 bg-gradient-to-r from-white to-gray-900 rounded mb-2"></div>
                    <p className="text-sm font-medium text-center">System</p>
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

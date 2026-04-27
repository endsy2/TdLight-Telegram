import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { Loader, Phone, Lock, Key, CheckCircle, AlertCircle } from 'lucide-react'
import { authApi, userApi } from '@/services/api'
import { useStore } from '@/store/useStore'
import toast from 'react-hot-toast'

type AuthStep = 'phone' | 'checking' | 'code' | 'password' | 'ready' | 'error'

export default function AuthPage() {
  const navigate = useNavigate()
  const setAuthenticated = useStore((state) => state.setAuthenticated)
  const setAuthState = useStore((state) => state.setAuthState)
  const setCurrentUser = useStore((state) => state.setCurrentUser)
  const isAuthenticated = useStore((state) => state.isAuthenticated)

  const [step, setStep] = useState<AuthStep>('checking')
  const [code, setCode] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [phoneNumber, setPhoneNumber] = useState('')

  // Redirect if already authenticated
  useEffect(() => {
    if (isAuthenticated) {
      navigate('/chats', { replace: true })
    }
  }, [isAuthenticated, navigate])

  // Check auth status on mount and periodically
  useEffect(() => {
    // Check immediately on mount
    checkAuthStatus()
    
    // Only poll if not authenticated yet
    if (!step || step === 'checking' || step === 'code' || step === 'password') {
      const interval = setInterval(checkAuthStatus, 2000)
      return () => clearInterval(interval)
    }
  }, [step])

  const checkAuthStatus = async () => {
    try {
      const response = await authApi.getStatus()
      const status = response.data

      setAuthState(status.authenticationState)
      setPhoneNumber(status.phoneNumber || '')

      if (status.isReady) {
        // Already authenticated, get user info and redirect
        await fetchUserAndRedirect()
      } else if (status.needsCode) {
        setStep('code')
      } else if (status.needsPassword) {
        setStep('password')
      } else if (status.isWaitingForPhone) {
        // Only show phone input if not already authenticated
        setStep('phone')
      }
    } catch (err) {
      console.error('Failed to check auth status:', err)
      // Don't show error immediately, might just be starting up
      if (step !== 'phone') {
        setStep('phone')
      }
    }
  }

  const fetchUserAndRedirect = async () => {
    try {
      const userResponse = await userApi.getMe()
      const user = userResponse.data
      
      setCurrentUser(user)
      setAuthenticated(true)
      setStep('ready')
      
      toast.success('Authentication successful!')
      setTimeout(() => navigate('/chats'), 1000)
    } catch (err) {
      console.error('Failed to fetch user:', err)
      toast.error('Failed to fetch user information')
    }
  }

  const handleSubmitPhone = async (e: React.FormEvent) => {
    e.preventDefault()
    
    if (!phoneNumber.trim()) {
      setError('Please enter your phone number')
      return
    }

    // Basic phone validation
    if (!phoneNumber.match(/^\+?[1-9]\d{1,14}$/)) {
      setError('Please enter a valid phone number with country code')
      return
    }

    setLoading(true)
    setError('')

    try {
      const response = await authApi.sendPhoneNumber(phoneNumber)
      
      if (response.data.success) {
        toast.success('Phone number sent! Check Telegram for verification code')
        setStep('checking')
        // Start checking auth status
        setTimeout(checkAuthStatus, 1000)
      } else {
        setError(response.data.error || 'Failed to send phone number')
        toast.error('Failed to send phone number')
      }
    } catch (err: any) {
      console.error('Failed to submit phone:', err)
      const errorMsg = err.response?.data?.error || 'Failed to submit phone number'
      setError(errorMsg)
      toast.error(errorMsg)
    } finally {
      setLoading(false)
    }
  }

  const handleSubmitCode = async (e: React.FormEvent) => {
    e.preventDefault()
    
    if (!code.trim()) {
      setError('Please enter the verification code')
      return
    }

    setLoading(true)
    setError('')

    try {
      const response = await authApi.submitCode(code)
      
      if (response.data.success) {
        toast.success('Code submitted successfully')
        setCode('')
        // Wait a bit for backend to process
        setTimeout(checkAuthStatus, 1000)
      } else {
        setError(response.data.error || 'Invalid verification code')
        toast.error('Invalid verification code')
      }
    } catch (err: any) {
      console.error('Failed to submit code:', err)
      const errorMsg = err.response?.data?.error || 'Failed to submit code'
      setError(errorMsg)
      toast.error(errorMsg)
    } finally {
      setLoading(false)
    }
  }

  const handleSubmitPassword = async (e: React.FormEvent) => {
    e.preventDefault()
    
    if (!password.trim()) {
      setError('Please enter your password')
      return
    }

    setLoading(true)
    setError('')

    try {
      const response = await authApi.submitPassword(password)
      
      if (response.data.success) {
        toast.success('Password submitted successfully')
        setPassword('')
        // Wait a bit for backend to process
        setTimeout(checkAuthStatus, 1000)
      } else {
        setError(response.data.error || 'Invalid password')
        toast.error('Invalid password')
      }
    } catch (err: any) {
      console.error('Failed to submit password:', err)
      const errorMsg = err.response?.data?.error || 'Failed to submit password'
      setError(errorMsg)
      toast.error(errorMsg)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md p-8">
        {/* Header */}
        <div className="text-center mb-8">
          <div className="w-20 h-20 bg-blue-500 rounded-full flex items-center justify-center mx-auto mb-4">
            <Phone className="w-10 h-10 text-white" />
          </div>
          <h1 className="text-3xl font-bold text-gray-900 mb-2">TDLight Pro</h1>
          <p className="text-gray-600">Telegram Authentication</p>
        </div>

        {/* Phone Number Input */}
        {step === 'phone' && (
          <form onSubmit={handleSubmitPhone} className="space-y-6">
            <div>
              <label htmlFor="phone" className="block text-sm font-medium text-gray-700 mb-2">
                Phone Number
              </label>
              <input
                id="phone"
                type="tel"
                value={phoneNumber}
                onChange={(e) => setPhoneNumber(e.target.value)}
                placeholder="+1 234 567 8900"
                className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                autoFocus
                disabled={loading}
              />
              <p className="mt-2 text-xs text-gray-500">
                Include country code (e.g., +1 for US, +44 for UK)
              </p>
            </div>

            {error && (
              <div className="flex items-center gap-2 p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
                <AlertCircle className="w-4 h-4 flex-shrink-0" />
                <span>{error}</span>
              </div>
            )}

            <button
              type="submit"
              disabled={loading || !phoneNumber.trim()}
              className="w-full bg-blue-500 text-white py-3 rounded-lg font-medium hover:bg-blue-600 disabled:opacity-50 disabled:cursor-not-allowed transition-colors flex items-center justify-center gap-2"
            >
              {loading ? (
                <>
                  <Loader className="w-5 h-5 animate-spin" />
                  Submitting...
                </>
              ) : (
                'Continue'
              )}
            </button>

            <p className="text-xs text-gray-500 text-center">
              You'll receive a verification code on Telegram
            </p>
          </form>
        )}

        {/* Checking Status */}
        {step === 'checking' && (
          <div className="text-center py-8">
            <Loader className="w-12 h-12 text-blue-500 animate-spin mx-auto mb-4" />
            <p className="text-gray-600">Checking authentication status...</p>
          </div>
        )}

        {/* Verification Code Input */}
        {step === 'code' && (
          <form onSubmit={handleSubmitCode} className="space-y-6">
            <div>
              <div className="flex items-center gap-2 mb-4 text-sm text-gray-600">
                <Key className="w-4 h-4" />
                <span>Enter the verification code sent to:</span>
              </div>
              <div className="bg-blue-50 border border-blue-200 rounded-lg p-3 mb-4">
                <p className="text-blue-900 font-medium text-center">{phoneNumber}</p>
              </div>
            </div>

            <div>
              <label htmlFor="code" className="block text-sm font-medium text-gray-700 mb-2">
                Verification Code
              </label>
              <input
                id="code"
                type="text"
                value={code}
                onChange={(e) => setCode(e.target.value)}
                placeholder="12345"
                className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent text-center text-2xl tracking-widest"
                maxLength={6}
                autoFocus
                disabled={loading}
              />
            </div>

            {error && (
              <div className="flex items-center gap-2 p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
                <AlertCircle className="w-4 h-4 flex-shrink-0" />
                <span>{error}</span>
              </div>
            )}

            <button
              type="submit"
              disabled={loading || !code.trim()}
              className="w-full bg-blue-500 text-white py-3 rounded-lg font-medium hover:bg-blue-600 disabled:opacity-50 disabled:cursor-not-allowed transition-colors flex items-center justify-center gap-2"
            >
              {loading ? (
                <>
                  <Loader className="w-5 h-5 animate-spin" />
                  Submitting...
                </>
              ) : (
                'Submit Code'
              )}
            </button>

            <p className="text-xs text-gray-500 text-center">
              Check your Telegram app for the verification code
            </p>
          </form>
        )}

        {/* Password Input (2FA) */}
        {step === 'password' && (
          <form onSubmit={handleSubmitPassword} className="space-y-6">
            <div>
              <div className="flex items-center gap-2 mb-4 text-sm text-gray-600">
                <Lock className="w-4 h-4" />
                <span>Two-factor authentication is enabled</span>
              </div>
            </div>

            <div>
              <label htmlFor="password" className="block text-sm font-medium text-gray-700 mb-2">
                Password
              </label>
              <input
                id="password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="Enter your password"
                className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                autoFocus
                disabled={loading}
              />
            </div>

            {error && (
              <div className="flex items-center gap-2 p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
                <AlertCircle className="w-4 h-4 flex-shrink-0" />
                <span>{error}</span>
              </div>
            )}

            <button
              type="submit"
              disabled={loading || !password.trim()}
              className="w-full bg-blue-500 text-white py-3 rounded-lg font-medium hover:bg-blue-600 disabled:opacity-50 disabled:cursor-not-allowed transition-colors flex items-center justify-center gap-2"
            >
              {loading ? (
                <>
                  <Loader className="w-5 h-5 animate-spin" />
                  Submitting...
                </>
              ) : (
                'Submit Password'
              )}
            </button>

            <p className="text-xs text-gray-500 text-center">
              Enter your two-factor authentication password
            </p>
          </form>
        )}

        {/* Ready State */}
        {step === 'ready' && (
          <div className="text-center py-8">
            <CheckCircle className="w-16 h-16 text-green-500 mx-auto mb-4" />
            <h2 className="text-2xl font-bold text-gray-900 mb-2">Authentication Successful!</h2>
            <p className="text-gray-600 mb-4">Redirecting to chats...</p>
            <Loader className="w-6 h-6 text-blue-500 animate-spin mx-auto" />
          </div>
        )}

        {/* Error State */}
        {step === 'error' && (
          <div className="text-center py-8">
            <AlertCircle className="w-16 h-16 text-red-500 mx-auto mb-4" />
            <h2 className="text-xl font-bold text-gray-900 mb-2">Connection Error</h2>
            <p className="text-gray-600 mb-6">{error}</p>
            <button
              onClick={checkAuthStatus}
              className="px-6 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600 transition-colors"
            >
              Retry
            </button>
          </div>
        )}

        {/* Footer */}
        <div className="mt-8 pt-6 border-t border-gray-200 text-center">
          <p className="text-xs text-gray-500">
            Powered by TDLight • Secure Telegram Authentication
          </p>
        </div>
      </div>
    </div>
  )
}

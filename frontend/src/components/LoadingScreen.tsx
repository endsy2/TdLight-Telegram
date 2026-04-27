import { Loader2 } from 'lucide-react'

export default function LoadingScreen() {
  return (
    <div className="flex h-screen items-center justify-center bg-gradient-to-br from-blue-50 to-indigo-100 dark:from-gray-900 dark:to-gray-800">
      <div className="text-center">
        <Loader2 className="mx-auto h-12 w-12 animate-spin text-blue-600" />
        <p className="mt-4 text-lg font-medium text-gray-700 dark:text-gray-300">
          Loading Telegram...
        </p>
      </div>
    </div>
  )
}

import { Loader, Check, CheckCheck } from 'lucide-react'

interface MessageStatusIconProps {
  status?: string
}

export default function MessageStatusIcon({ status }: MessageStatusIconProps) {
  switch (status) {
    case 'sending':
      return <Loader className="w-3 h-3 animate-spin" />
    case 'sent':
      return <Check className="w-3 h-3" />
    case 'delivered':
    case 'read':
      return <CheckCheck className="w-3 h-3" />
    case 'failed':
      return <span className="text-red-500">!</span>
    default:
      return null
  }
}

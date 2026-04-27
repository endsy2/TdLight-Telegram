import { useState, useRef, useEffect } from 'react'
import { Mic, X, Send, Loader } from 'lucide-react'

interface VoiceRecorderProps {
  onSend: (audioBlob: Blob, duration: number) => Promise<void>
  disabled?: boolean
}

export default function VoiceRecorder({ onSend, disabled }: VoiceRecorderProps) {
  const [isRecording, setIsRecording] = useState(false)
  const [duration, setDuration] = useState(0)
  const [isSending, setIsSending] = useState(false)
  
  const mediaRecorderRef = useRef<MediaRecorder | null>(null)
  const chunksRef = useRef<Blob[]>([])
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const streamRef = useRef<MediaStream | null>(null)

  useEffect(() => {
    return () => {
      // Cleanup on unmount
      if (timerRef.current) {
        clearInterval(timerRef.current)
      }
      if (streamRef.current) {
        streamRef.current.getTracks().forEach(track => track.stop())
      }
    }
  }, [])

  const startRecording = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ 
        audio: {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true
        } 
      })
      
      streamRef.current = stream
      
      // Use appropriate MIME type
      const mimeType = MediaRecorder.isTypeSupported('audio/webm;codecs=opus')
        ? 'audio/webm;codecs=opus'
        : 'audio/webm'
      
      const mediaRecorder = new MediaRecorder(stream, { mimeType })
      
      mediaRecorder.ondataavailable = (e) => {
        if (e.data.size > 0) {
          chunksRef.current.push(e.data)
        }
      }
      
      mediaRecorder.onstop = async () => {
        const blob = new Blob(chunksRef.current, { type: mimeType })
        chunksRef.current = []
        
        if (blob.size > 0) {
          setIsSending(true)
          try {
            await onSend(blob, duration)
          } catch (error) {
            console.error('Failed to send voice message:', error)
          } finally {
            setIsSending(false)
          }
        }
        
        // Stop all tracks
        if (streamRef.current) {
          streamRef.current.getTracks().forEach(track => track.stop())
          streamRef.current = null
        }
      }
      
      mediaRecorder.start()
      mediaRecorderRef.current = mediaRecorder
      setIsRecording(true)
      setDuration(0)
      
      // Start timer
      timerRef.current = setInterval(() => {
        setDuration(prev => {
          const next = prev + 1
          // Auto-stop at 5 minutes
          if (next >= 300) {
            stopRecording()
          }
          return next
        })
      }, 1000)
      
    } catch (error) {
      console.error('Failed to start recording:', error)
      alert('Could not access microphone. Please check permissions.')
    }
  }

  const stopRecording = () => {
    if (mediaRecorderRef.current && isRecording) {
      mediaRecorderRef.current.stop()
      setIsRecording(false)
      
      if (timerRef.current) {
        clearInterval(timerRef.current)
        timerRef.current = null
      }
    }
  }

  const cancelRecording = () => {
    if (mediaRecorderRef.current && isRecording) {
      mediaRecorderRef.current.stop()
      chunksRef.current = [] // Clear chunks to prevent sending
      setIsRecording(false)
      setDuration(0)
      
      if (timerRef.current) {
        clearInterval(timerRef.current)
        timerRef.current = null
      }
      
      if (streamRef.current) {
        streamRef.current.getTracks().forEach(track => track.stop())
        streamRef.current = null
      }
    }
  }

  const formatDuration = (seconds: number) => {
    const mins = Math.floor(seconds / 60)
    const secs = seconds % 60
    return `${mins}:${secs.toString().padStart(2, '0')}`
  }

  if (isSending) {
    return (
      <div className="flex items-center gap-2 px-4 py-2 bg-blue-50 dark:bg-blue-900/20 rounded-full">
        <Loader className="w-5 h-5 text-blue-500 animate-spin" />
        <span className="text-sm text-blue-600 dark:text-blue-400">Sending...</span>
      </div>
    )
  }

  if (!isRecording) {
    return (
      <button
        type="button"
        onClick={startRecording}
        disabled={disabled}
        className="p-2 text-gray-500 hover:text-gray-700 hover:bg-gray-100 rounded-full transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
        aria-label="Record voice message"
        title="Hold to record voice message"
      >
        <Mic className="w-5 h-5" />
      </button>
    )
  }

  return (
    <div className="flex items-center gap-3 px-4 py-2 bg-red-50 dark:bg-red-900/20 rounded-full animate-pulse-slow">
      <div className="flex items-center gap-2">
        <div className="w-3 h-3 bg-red-500 rounded-full animate-pulse" />
        <span className="text-sm font-medium text-red-600 dark:text-red-400">
          {formatDuration(duration)}
        </span>
      </div>
      
      <div className="flex items-center gap-2 ml-auto">
        <button
          onClick={cancelRecording}
          className="p-2 hover:bg-red-100 dark:hover:bg-red-900/40 rounded-full transition-colors"
          aria-label="Cancel recording"
          title="Cancel"
        >
          <X className="w-5 h-5 text-red-600 dark:text-red-400" />
        </button>
        
        <button
          onClick={stopRecording}
          className="p-2 bg-blue-500 hover:bg-blue-600 text-white rounded-full transition-colors"
          aria-label="Send voice message"
          title="Send"
        >
          <Send className="w-5 h-5" />
        </button>
      </div>
    </div>
  )
}

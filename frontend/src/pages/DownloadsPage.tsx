import { useEffect, useState } from 'react'
import { Download, X, RefreshCw, CheckCircle, AlertCircle, Loader } from 'lucide-react'
import { downloadApi } from '@/services/api'
import type { DownloadInfo } from '@/types'
import toast from 'react-hot-toast'

export default function DownloadsPage() {
  const [downloads, setDownloads] = useState<DownloadInfo[]>([])
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)

  const fetchDownloads = async () => {
    try {
      const response = await downloadApi.getAllDownloads()
      setDownloads(response.data)
    } catch (error) {
      console.error('Failed to fetch downloads:', error)
      toast.error('Failed to load downloads')
    } finally {
      setLoading(false)
      setRefreshing(false)
    }
  }

  useEffect(() => {
    fetchDownloads()
    // Poll for updates every 2 seconds
    const interval = setInterval(fetchDownloads, 2000)
    return () => clearInterval(interval)
  }, [])

  const handleRefresh = () => {
    setRefreshing(true)
    fetchDownloads()
  }

  const handleCancel = async (downloadId: string) => {
    try {
      await downloadApi.cancelDownload(downloadId)
      toast.success('Download cancelled')
      fetchDownloads()
    } catch (error) {
      console.error('Failed to cancel download:', error)
      toast.error('Failed to cancel download')
    }
  }

  const formatBytes = (bytes: number) => {
    if (bytes === 0) return '0 B'
    const k = 1024
    const sizes = ['B', 'KB', 'MB', 'GB']
    const i = Math.floor(Math.log(bytes) / Math.log(k))
    return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i]
  }

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'COMPLETED':
        return <CheckCircle className="w-5 h-5 text-green-500" />
      case 'DOWNLOADING':
        return <Loader className="w-5 h-5 text-blue-500 animate-spin" />
      case 'FAILED':
      case 'CANCELLED':
        return <AlertCircle className="w-5 h-5 text-red-500" />
      default:
        return <Download className="w-5 h-5 text-gray-400" />
    }
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center h-full">
        <Loader className="w-8 h-8 animate-spin text-blue-500" />
      </div>
    )
  }

  return (
    <div className="h-full flex flex-col bg-gray-50">
      {/* Header */}
      <div className="bg-white border-b px-6 py-4">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">Downloads</h1>
            <p className="text-sm text-gray-500 mt-1">
              {downloads.length} {downloads.length === 1 ? 'download' : 'downloads'}
            </p>
          </div>
          <button
            onClick={handleRefresh}
            disabled={refreshing}
            className="flex items-center gap-2 px-4 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600 disabled:opacity-50"
          >
            <RefreshCw className={`w-4 h-4 ${refreshing ? 'animate-spin' : ''}`} />
            Refresh
          </button>
        </div>
      </div>

      {/* Downloads List */}
      <div className="flex-1 overflow-y-auto p-6">
        {downloads.length === 0 ? (
          <div className="text-center py-12">
            <Download className="w-16 h-16 text-gray-300 mx-auto mb-4" />
            <p className="text-gray-500">No downloads yet</p>
          </div>
        ) : (
          <div className="space-y-4">
            {downloads.map((download) => (
              <div
                key={download.downloadId}
                className="bg-white rounded-lg shadow-sm border p-4 hover:shadow-md transition-shadow"
              >
                <div className="flex items-start gap-4">
                  {/* Status Icon */}
                  <div className="flex-shrink-0 mt-1">
                    {getStatusIcon(download.status)}
                  </div>

                  {/* Content */}
                  <div className="flex-1 min-w-0">
                    <div className="flex items-start justify-between gap-4">
                      <div className="flex-1 min-w-0">
                        <h3 className="font-medium text-gray-900 truncate">
                          {download.fileName}
                        </h3>
                        <p className="text-sm text-gray-500 mt-1">
                          {formatBytes(download.fileSize)} • {download.status}
                        </p>
                      </div>

                      {/* Actions */}
                      {download.status === 'DOWNLOADING' && (
                        <button
                          onClick={() => handleCancel(download.downloadId)}
                          className="flex-shrink-0 p-2 text-gray-400 hover:text-red-500 hover:bg-red-50 rounded-lg transition-colors"
                          title="Cancel download"
                        >
                          <X className="w-5 h-5" />
                        </button>
                      )}
                    </div>

                    {/* Progress Bar */}
                    {download.status === 'DOWNLOADING' && (
                      <div className="mt-3">
                        <div className="flex items-center justify-between text-sm text-gray-600 mb-1">
                          <span>{download.progress}%</span>
                          <span>
                            {formatBytes(download.downloadedBytes)} / {formatBytes(download.fileSize)}
                          </span>
                        </div>
                        <div className="w-full bg-gray-200 rounded-full h-2">
                          <div
                            className="bg-blue-500 h-2 rounded-full transition-all duration-300"
                            style={{ width: `${download.progress}%` }}
                          />
                        </div>
                      </div>
                    )}

                    {/* MinIO URLs */}
                    {download.minioUrl && (
                      <div className="mt-3 space-y-2">
                        <div className="flex items-center gap-2">
                          <span className="text-xs font-medium text-gray-500">MinIO URL:</span>
                          <a
                            href={download.minioUrl}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="text-xs text-blue-500 hover:underline truncate"
                          >
                            {download.minioUrl}
                          </a>
                        </div>
                        {download.minioPresignedUrl && (
                          <div className="flex items-center gap-2">
                            <span className="text-xs font-medium text-gray-500">Temp URL:</span>
                            <a
                              href={download.minioPresignedUrl}
                              target="_blank"
                              rel="noopener noreferrer"
                              className="text-xs text-blue-500 hover:underline truncate"
                            >
                              Download (expires in 1 hour)
                            </a>
                          </div>
                        )}
                      </div>
                    )}

                    {/* Error Message */}
                    {download.errorMessage && (
                      <div className="mt-3 p-2 bg-red-50 border border-red-200 rounded text-sm text-red-600">
                        {download.errorMessage}
                      </div>
                    )}

                    {/* Metadata */}
                    <div className="mt-3 flex items-center gap-4 text-xs text-gray-400">
                      <span>ID: {download.downloadId.slice(0, 8)}...</span>
                      <span>Message: {download.messageId}</span>
                      <span>Chat: {download.chatId}</span>
                    </div>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

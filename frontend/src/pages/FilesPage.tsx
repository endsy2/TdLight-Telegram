import { useState } from 'react'
import { 
  FolderOpen, Upload, Download, Trash2, Search, File, 
  Image, Video, Music, FileText, Archive, Loader, ExternalLink 
} from 'lucide-react'
import { fileApi } from '@/services/api'
import toast from 'react-hot-toast'

interface FileItem {
  name: string
  size: number
  type: string
  url: string
  uploadedAt: string
}

export default function FilesPage() {
  const [searchQuery, setSearchQuery] = useState('')
  const [selectedBucket, setSelectedBucket] = useState('telegram-files')
  const [uploadingFile, setUploadingFile] = useState(false)

  const buckets = [
    { id: 'telegram-files', name: 'Telegram Files', icon: FolderOpen },
    { id: 'downloads', name: 'Downloads', icon: Download },
    { id: 'uploads', name: 'Uploads', icon: Upload },
  ]

  // Mock files data - replace with actual API call
  const files: FileItem[] = [
    {
      name: 'video_2024_01_15.mp4',
      size: 15728640,
      type: 'video/mp4',
      url: 'https://example.com/file1',
      uploadedAt: '2024-01-15T10:30:00Z',
    },
    {
      name: 'document.pdf',
      size: 2097152,
      type: 'application/pdf',
      url: 'https://example.com/file2',
      uploadedAt: '2024-01-14T15:20:00Z',
    },
    {
      name: 'image.jpg',
      size: 524288,
      type: 'image/jpeg',
      url: 'https://example.com/file3',
      uploadedAt: '2024-01-13T09:15:00Z',
    },
  ]

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return

    setUploadingFile(true)
    try {
      await fileApi.uploadFile(file, selectedBucket)
      toast.success('File uploaded successfully')
      // Refresh files list if you have a query
    } catch (error) {
      console.error('Upload failed:', error)
      toast.error('Failed to upload file')
    } finally {
      setUploadingFile(false)
    }
  }

  const formatBytes = (bytes: number) => {
    if (bytes === 0) return '0 B'
    const k = 1024
    const sizes = ['B', 'KB', 'MB', 'GB']
    const i = Math.floor(Math.log(bytes) / Math.log(k))
    return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i]
  }

  const getFileIcon = (type: string) => {
    if (type.startsWith('image/')) return <Image className="w-8 h-8 text-blue-500" />
    if (type.startsWith('video/')) return <Video className="w-8 h-8 text-purple-500" />
    if (type.startsWith('audio/')) return <Music className="w-8 h-8 text-green-500" />
    if (type.includes('pdf')) return <FileText className="w-8 h-8 text-red-500" />
    if (type.includes('zip') || type.includes('rar')) return <Archive className="w-8 h-8 text-yellow-500" />
    return <File className="w-8 h-8 text-gray-500" />
  }

  const filteredFiles = files.filter(file =>
    file.name.toLowerCase().includes(searchQuery.toLowerCase())
  )

  return (
    <div className="flex h-screen bg-gray-50">
      {/* Buckets Sidebar */}
      <div className="w-64 bg-white border-r flex flex-col">
        <div className="p-4 border-b">
          <h2 className="text-xl font-semibold mb-4">Storage</h2>
          <label className="w-full flex items-center justify-center gap-2 px-4 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600 cursor-pointer">
            <Upload className="w-4 h-4" />
            <span>Upload File</span>
            <input
              type="file"
              onChange={handleFileUpload}
              className="hidden"
              disabled={uploadingFile}
            />
          </label>
        </div>

        <nav className="flex-1 p-2">
          {buckets.map((bucket) => {
            const Icon = bucket.icon
            return (
              <button
                key={bucket.id}
                onClick={() => setSelectedBucket(bucket.id)}
                className={`w-full flex items-center gap-3 px-4 py-3 rounded-lg transition-colors ${
                  selectedBucket === bucket.id
                    ? 'bg-blue-50 text-blue-600'
                    : 'text-gray-700 hover:bg-gray-50'
                }`}
              >
                <Icon className="w-5 h-5" />
                <span className="font-medium">{bucket.name}</span>
              </button>
            )
          })}
        </nav>

        <div className="p-4 border-t">
          <div className="bg-gray-50 rounded-lg p-3">
            <p className="text-sm text-gray-600 mb-1">Storage Used</p>
            <p className="text-lg font-semibold text-gray-900">2.4 GB / 10 GB</p>
            <div className="w-full bg-gray-200 rounded-full h-2 mt-2">
              <div className="bg-blue-500 h-2 rounded-full" style={{ width: '24%' }}></div>
            </div>
          </div>
        </div>
      </div>

      {/* Files Area */}
      <div className="flex-1 flex flex-col">
        {/* Header */}
        <div className="bg-white border-b px-6 py-4">
          <div className="flex items-center justify-between mb-4">
            <h1 className="text-2xl font-bold text-gray-900">
              {buckets.find(b => b.id === selectedBucket)?.name}
            </h1>
            <div className="flex items-center gap-2">
              <button className="px-4 py-2 text-gray-600 hover:bg-gray-100 rounded-lg">
                Grid View
              </button>
              <button className="px-4 py-2 text-gray-600 hover:bg-gray-100 rounded-lg">
                List View
              </button>
            </div>
          </div>
          <div className="relative">
            <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 w-4 h-4 text-gray-400" />
            <input
              type="text"
              placeholder="Search files..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-10 pr-4 py-2 bg-gray-100 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
        </div>

        {/* Files Grid */}
        <div className="flex-1 overflow-y-auto p-6">
          {uploadingFile && (
            <div className="mb-4 p-4 bg-blue-50 border border-blue-200 rounded-lg flex items-center gap-3">
              <Loader className="w-5 h-5 animate-spin text-blue-500" />
              <span className="text-blue-700">Uploading file...</span>
            </div>
          )}

          {filteredFiles.length > 0 ? (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
              {filteredFiles.map((file, index) => (
                <div
                  key={index}
                  className="bg-white rounded-lg shadow-sm border p-4 hover:shadow-md transition-shadow"
                >
                  <div className="flex items-start justify-between mb-3">
                    {getFileIcon(file.type)}
                    <button className="p-1 text-gray-400 hover:text-red-500 hover:bg-red-50 rounded">
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                  <h3 className="font-medium text-gray-900 truncate mb-1" title={file.name}>
                    {file.name}
                  </h3>
                  <p className="text-sm text-gray-500 mb-3">{formatBytes(file.size)}</p>
                  <div className="flex items-center gap-2">
                    <a
                      href={file.url}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="flex-1 flex items-center justify-center gap-2 px-3 py-2 bg-blue-500 text-white text-sm rounded-lg hover:bg-blue-600"
                    >
                      <Download className="w-4 h-4" />
                      Download
                    </a>
                    <a
                      href={file.url}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="p-2 text-gray-600 hover:bg-gray-100 rounded-lg"
                    >
                      <ExternalLink className="w-4 h-4" />
                    </a>
                  </div>
                  <p className="text-xs text-gray-400 mt-3">
                    {new Date(file.uploadedAt).toLocaleDateString()}
                  </p>
                </div>
              ))}
            </div>
          ) : (
            <div className="flex items-center justify-center h-full">
              <div className="text-center">
                <FolderOpen className="w-16 h-16 text-gray-300 mx-auto mb-4" />
                <p className="text-gray-500">No files found</p>
                <label className="mt-4 inline-flex items-center gap-2 px-4 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600 cursor-pointer">
                  <Upload className="w-4 h-4" />
                  <span>Upload your first file</span>
                  <input
                    type="file"
                    onChange={handleFileUpload}
                    className="hidden"
                    disabled={uploadingFile}
                  />
                </label>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Users, Search, Plus, Settings, UserPlus, Loader } from 'lucide-react'
import { groupApi } from '@/services/api'
import toast from 'react-hot-toast'

export default function GroupsPage() {
  const queryClient = useQueryClient()
  const [searchQuery, setSearchQuery] = useState('')
  const [selectedGroup, setSelectedGroup] = useState<number | null>(null)
  const [inviteLink, setInviteLink] = useState('')
  const [showJoinModal, setShowJoinModal] = useState(false)

  // Fetch groups
  const { data: groups, isLoading: groupsLoading } = useQuery({
    queryKey: ['groups'],
    queryFn: async () => {
      const response = await groupApi.getGroups()
      return response.data
    },
  })

  // Fetch group members
  const { data: members, isLoading: membersLoading } = useQuery({
    queryKey: ['groupMembers', selectedGroup],
    queryFn: async () => {
      if (!selectedGroup) return []
      const response = await groupApi.getGroupMembers(selectedGroup, false, false, false)
      return response.data
    },
    enabled: !!selectedGroup,
  })

  // Join group mutation
  const joinGroupMutation = useMutation({
    mutationFn: (link: string) => groupApi.joinGroup(link),
    onSuccess: () => {
      toast.success('Successfully joined group!')
      setInviteLink('')
      setShowJoinModal(false)
      queryClient.invalidateQueries({ queryKey: ['groups'] })
    },
    onError: (error: any) => {
      toast.error(error.response?.data?.error || 'Failed to join group')
    },
  })

  const handleJoinGroup = (e: React.FormEvent) => {
    e.preventDefault()
    if (inviteLink.trim()) {
      joinGroupMutation.mutate(inviteLink.trim())
    }
  }

  const filteredGroups = groups?.filter(group =>
    group.title.toLowerCase().includes(searchQuery.toLowerCase())
  )

  const selectedGroupInfo = groups?.find(g => g.id === selectedGroup)

  return (
    <div className="flex h-screen bg-gray-50">
      {/* Groups Sidebar */}
      <div className="w-96 bg-white border-r flex flex-col">
        {/* Header */}
        <div className="p-4 border-b">
          <div className="flex items-center justify-between mb-3">
            <h2 className="text-xl font-semibold">Groups</h2>
            <button
              onClick={() => setShowJoinModal(true)}
              className="p-2 bg-blue-500 text-white rounded-full hover:bg-blue-600"
              title="Join group"
            >
              <Plus className="w-5 h-5" />
            </button>
          </div>
          <div className="relative">
            <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 w-4 h-4 text-gray-400" />
            <input
              type="text"
              placeholder="Search groups..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-10 pr-4 py-2 bg-gray-100 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
        </div>

        {/* Groups List */}
        <div className="flex-1 overflow-y-auto">
          {groupsLoading ? (
            <div className="flex items-center justify-center h-32">
              <Loader className="w-6 h-6 animate-spin text-blue-500" />
            </div>
          ) : filteredGroups && filteredGroups.length > 0 ? (
            filteredGroups.map((group) => (
              <div
                key={group.id}
                onClick={() => setSelectedGroup(group.id)}
                className={`p-4 border-b cursor-pointer hover:bg-gray-50 transition-colors ${
                  selectedGroup === group.id ? 'bg-blue-50' : ''
                }`}
              >
                <div className="flex items-start gap-3">
                  <div className="w-12 h-12 rounded-full bg-gradient-to-br from-purple-400 to-purple-600 flex items-center justify-center text-white flex-shrink-0">
                    <Users className="w-6 h-6" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <h3 className="font-semibold text-gray-900 truncate">{group.title}</h3>
                    <div className="flex items-center gap-2 mt-1">
                      {group.isSupergroup && (
                        <span className="text-xs bg-blue-100 text-blue-600 px-2 py-0.5 rounded">
                          Supergroup
                        </span>
                      )}
                      {group.isChannel && (
                        <span className="text-xs bg-green-100 text-green-600 px-2 py-0.5 rounded">
                          Channel
                        </span>
                      )}
                    </div>
                  </div>
                </div>
              </div>
            ))
          ) : (
            <div className="text-center py-12 text-gray-500">
              <Users className="w-16 h-16 text-gray-300 mx-auto mb-4" />
              <p>No groups found</p>
              <button
                onClick={() => setShowJoinModal(true)}
                className="mt-4 text-blue-500 hover:underline"
              >
                Join a group
              </button>
            </div>
          )}
        </div>
      </div>

      {/* Group Details */}
      {selectedGroup && selectedGroupInfo ? (
        <div className="flex-1 flex flex-col">
          {/* Header */}
          <div className="bg-white border-b px-6 py-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-4">
                <div className="w-16 h-16 rounded-full bg-gradient-to-br from-purple-400 to-purple-600 flex items-center justify-center text-white">
                  <Users className="w-8 h-8" />
                </div>
                <div>
                  <h2 className="text-2xl font-bold text-gray-900">{selectedGroupInfo.title}</h2>
                  <p className="text-sm text-gray-500 mt-1">
                    Group ID: {selectedGroupInfo.id}
                  </p>
                </div>
              </div>
              <div className="flex items-center gap-2">
                <button className="p-2 hover:bg-gray-100 rounded-full" title="Group settings">
                  <Settings className="w-5 h-5 text-gray-600" />
                </button>
              </div>
            </div>
          </div>

          {/* Members List */}
          <div className="flex-1 overflow-y-auto p-6">
            <div className="bg-white rounded-lg shadow-sm border">
              <div className="p-4 border-b">
                <h3 className="font-semibold text-gray-900">
                  Members {members && `(${members.length})`}
                </h3>
              </div>
              <div className="divide-y">
                {membersLoading ? (
                  <div className="flex items-center justify-center py-12">
                    <Loader className="w-6 h-6 animate-spin text-blue-500" />
                  </div>
                ) : members && members.length > 0 ? (
                  members.map((member) => (
                    <div key={member.userId} className="p-4 hover:bg-gray-50">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-3">
                          <div className="w-10 h-10 rounded-full bg-gradient-to-br from-blue-400 to-blue-600 flex items-center justify-center text-white font-semibold">
                            {member.firstName.charAt(0).toUpperCase()}
                          </div>
                          <div>
                            <div className="flex items-center gap-2">
                              <h4 className="font-medium text-gray-900">
                                {member.firstName} {member.lastName}
                              </h4>
                              {member.isVerified && (
                                <span className="text-blue-500" title="Verified">✓</span>
                              )}
                              {member.isPremium && (
                                <span className="text-xs bg-gradient-to-r from-purple-500 to-pink-500 text-white px-2 py-0.5 rounded">
                                  Premium
                                </span>
                              )}
                            </div>
                            <div className="flex items-center gap-2 mt-1">
                              {member.username && (
                                <p className="text-sm text-gray-500">@{member.username}</p>
                              )}
                              <span className="text-xs bg-gray-100 text-gray-600 px-2 py-0.5 rounded">
                                {member.memberStatus}
                              </span>
                            </div>
                          </div>
                        </div>
                        {member.isBot && (
                          <span className="text-xs bg-blue-100 text-blue-600 px-2 py-1 rounded">
                            BOT
                          </span>
                        )}
                      </div>
                    </div>
                  ))
                ) : (
                  <div className="text-center py-12 text-gray-500">
                    <p>No members found</p>
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      ) : (
        <div className="flex-1 flex items-center justify-center bg-gray-50">
          <div className="text-center">
            <div className="w-32 h-32 bg-purple-100 rounded-full flex items-center justify-center mx-auto mb-4">
              <Users className="w-16 h-16 text-purple-500" />
            </div>
            <h2 className="text-2xl font-semibold text-gray-900 mb-2">Select a group</h2>
            <p className="text-gray-500">Choose a group to view members and details</p>
          </div>
        </div>
      )}

      {/* Join Group Modal */}
      {showJoinModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg shadow-xl w-full max-w-md p-6">
            <h3 className="text-xl font-bold text-gray-900 mb-4">Join Group</h3>
            <form onSubmit={handleJoinGroup} className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Invite Link
                </label>
                <input
                  type="text"
                  value={inviteLink}
                  onChange={(e) => setInviteLink(e.target.value)}
                  placeholder="https://t.me/joinchat/..."
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  autoFocus
                />
              </div>
              <div className="flex gap-3">
                <button
                  type="button"
                  onClick={() => {
                    setShowJoinModal(false)
                    setInviteLink('')
                  }}
                  className="flex-1 px-4 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={!inviteLink.trim() || joinGroupMutation.isPending}
                  className="flex-1 px-4 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
                >
                  {joinGroupMutation.isPending ? (
                    <>
                      <Loader className="w-4 h-4 animate-spin" />
                      Joining...
                    </>
                  ) : (
                    <>
                      <UserPlus className="w-4 h-4" />
                      Join
                    </>
                  )}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}

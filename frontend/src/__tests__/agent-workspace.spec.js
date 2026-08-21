import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { nextTick } from 'vue'
import { mount } from '@vue/test-utils'

// ─── Mock API modules ───
vi.mock('../api/chat-api', () => ({
  listSessions: vi.fn(() => Promise.resolve({ data: { records: [] } })),
  listAgentViews: vi.fn(() =>
    Promise.resolve({
      data: [
        { code: 'MY_ACTIVE', label: '处理中', count: 3 },
        { code: 'MY_UNREAD', label: '未读', count: 1 },
        { code: 'MY_HIGH_PRIORITY', label: '高优先级', count: 0 },
        { code: 'MY_UNARCHIVED', label: '未归档', count: 2 },
        { code: 'MY_RECENT_CLOSED', label: '最近关闭', count: 5 },
      ],
    })
  ),
  listAgentViewSessions: vi.fn(() =>
    Promise.resolve({
      data: {
        records: [
          {
            sessionId: 's1',
            title: '测试会话1',
            priority: 'HIGH',
            category: 'TECHNICAL',
            status: 'ACTIVE',
            unreadCount: 2,
            tags: ['vip', 'urgent'],
          },
          {
            sessionId: 's2',
            title: '测试会话2',
            priority: 'NORMAL',
            status: 'ACTIVE',
            unreadCount: 0,
          },
        ],
      },
    })
  ),
  getSessionMetadata: vi.fn(() =>
    Promise.resolve({
      data: {
        sessionId: 's1',
        title: '测试会话1',
        priority: 'HIGH',
        category: 'TECHNICAL',
        tags: ['vip', 'urgent'],
      },
    })
  ),
  updateSessionMetadata: vi.fn(() =>
    Promise.resolve({
      data: {
        sessionId: 's1',
        title: '更新后的标题',
        priority: 'URGENT',
        category: 'ACCOUNT',
        tags: ['new'],
      },
    })
  ),
  setArchiveStatus: vi.fn(() => Promise.resolve({ data: null })),
  getUserProfile: vi.fn(() =>
    Promise.resolve({
      data: {
        userId: 'u1',
        username: '测试用户',
        vipLevel: 3,
        totalSessionCount: 15,
        lastSessionTime: '2025-01-01T10:00:00',
      },
    })
  ),
  findAgentDashboard: vi.fn(() => Promise.resolve({ data: { queueSize: 2, activeSessions: [], todayClosedSessions: 4 } })),
  findAgentRatingSummary: vi.fn(() => Promise.resolve({ data: { averageRating: 4.5, ratingCount: 8 } })),
  findTransferLogs: vi.fn(() => Promise.resolve({ data: [] })),
  listQuickReplies: vi.fn(() =>
    Promise.resolve({
      data: [
        { id: 'qr1', title: '问候语', content: '您好，有什么可以帮助您？', sortOrder: 0 },
        { id: 'qr2', title: '结束语', content: '感谢您的咨询，祝您生活愉快！', sortOrder: 1 },
      ],
    })
  ),
  createQuickReply: vi.fn(() =>
    Promise.resolve({ data: { id: 'qr3', title: '新回复', content: '新内容', sortOrder: 2 } })
  ),
  updateQuickReply: vi.fn(() =>
    Promise.resolve({ data: { id: 'qr1', title: '已更新', content: '已更新内容', sortOrder: 0 } })
  ),
  deleteQuickReply: vi.fn(() => Promise.resolve(null)),
  getQueueStatus: vi.fn(() => Promise.resolve({ data: null })),
  getSessionRating: vi.fn(() => Promise.resolve({ data: null })),
  submitRating: vi.fn(() => Promise.resolve({ data: null })),
  uploadAttachment: vi.fn(() => Promise.resolve({ data: null })),
  agentOnline: vi.fn(() => Promise.resolve(null)),
  agentOffline: vi.fn(() => Promise.resolve(null)),
  fetchAttachmentBlob: vi.fn(() => Promise.resolve('blob:test')),
}))

vi.mock('../services/stomp-client', () => ({
  createStompClient: vi.fn(() => ({
    connect: vi.fn(),
    disconnect: vi.fn(),
    subscribe: vi.fn(),
    publish: vi.fn(),
  })),
}))

// ─── Import after mocks ───
import { useChatStore } from '../stores/chat'
import { useAuthStore } from '../stores/auth'
import {
  listAgentViews,
  listAgentViewSessions,
  getSessionMetadata,
  updateSessionMetadata,
  setArchiveStatus,
  getUserProfile,
  listQuickReplies,
  createQuickReply,
  deleteQuickReply,
  agentOnline,
  findAgentDashboard,
  findAgentRatingSummary,
  findTransferLogs,
} from '../api/chat-api'

// ─── Store tests ───
describe('Chat Store - Agent Workspace', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    const auth = useAuthStore()
    auth.login({ token: 'test-token', userId: 'agent-1', role: 'AGENT' })
    vi.clearAllMocks()
  })

  it('loads agent view counts', async () => {
    const chat = useChatStore()
    await chat.loadAgentViewCounts()
    expect(chat.agentViewCounts).toHaveLength(5)
    expect(chat.agentViewCounts[0].code).toBe('MY_ACTIVE')
    expect(chat.agentViewCounts[0].count).toBe(3)
  })

  it('loads sessions for a specific agent view', async () => {
    const chat = useChatStore()
    await chat.loadAgentViewSessions('MY_ACTIVE')
    expect(listAgentViewSessions).toHaveBeenCalledWith('MY_ACTIVE', {})
    expect(chat.sessions).toHaveLength(2)
    expect(chat.sessions[0].sessionId).toBe('s1')
  })

  it('switches active agent view and refreshes sessions', async () => {
    const chat = useChatStore()
    await chat.switchAgentView('MY_UNREAD')
    expect(chat.activeAgentView).toBe('MY_UNREAD')
    expect(listAgentViewSessions).toHaveBeenCalledWith('MY_UNREAD', {})
  })

  it('loads agent dashboard and rating summary data', async () => {
    const chat = useChatStore()
    await chat.loadAgentDashboard()

    expect(findAgentDashboard).toHaveBeenCalled()
    expect(findAgentRatingSummary).toHaveBeenCalled()
    expect(chat.agentDashboard.queueSize).toBe(2)
    expect(chat.agentRatingSummary.averageRating).toBe(4.5)
  })

  it('loads transfer logs for the selected session', async () => {
    const chat = useChatStore()
    await chat.loadTransferLogs('s1')
    expect(findTransferLogs).toHaveBeenCalledWith('s1')
    expect(chat.transferLogs).toEqual([])
  })

  it('records readable WebSocket errors and connection activity', () => {
    const chat = useChatStore()
    chat.handleConnectionError('连接被服务器关闭')

    expect(chat.connectionState).toBe('error')
    expect(chat.connectionError).toBe('连接被服务器关闭')
    expect(chat.connectionLogs.at(-1).message).toBe('连接被服务器关闭')
  })

  it('refreshes the fixed view immediately when the server creates a session', () => {
    const chat = useChatStore()
    chat.activeAgentView = 'MY_ACTIVE'
    const refreshCounts = vi.spyOn(chat, 'loadAgentViewCounts')
    const refreshSessions = vi.spyOn(chat, 'loadAgentViewSessions')

    chat._handleChatEvent({ event: 'SESSION_CREATED', session: { sessionId: 's1' } })

    expect(refreshCounts).toHaveBeenCalled()
    expect(refreshSessions).toHaveBeenCalledWith('MY_ACTIVE')
  })

  it('refreshes the fixed view immediately when the server transfers a session', () => {
    const chat = useChatStore()
    chat.activeAgentView = 'MY_ACTIVE'
    const refreshCounts = vi.spyOn(chat, 'loadAgentViewCounts')
    const refreshSessions = vi.spyOn(chat, 'loadAgentViewSessions')

    chat._handleChatEvent({ event: 'SESSION_TRANSFERRED', sessionId: 's1' })

    expect(refreshCounts).toHaveBeenCalled()
    expect(refreshSessions).toHaveBeenCalledWith('MY_ACTIVE')
  })

  it('loads session metadata', async () => {
    const chat = useChatStore()
    await chat.loadSessionMetadata('s1')
    expect(getSessionMetadata).toHaveBeenCalledWith('s1')
    expect(chat.activeMetadata).toBeDefined()
    expect(chat.activeMetadata.title).toBe('测试会话1')
    expect(chat.activeMetadata.priority).toBe('HIGH')
  })

  it('updates session metadata and refreshes local state', async () => {
    const chat = useChatStore()
    chat.sessions = [{ sessionId: 's1', title: '旧标题', priority: 'NORMAL' }]
    chat.activeSessionId = 's1'
    const result = await chat.updateSessionMetadata('s1', {
      title: '更新后的标题',
      priority: 'URGENT',
    })
    expect(updateSessionMetadata).toHaveBeenCalledWith('s1', {
      title: '更新后的标题',
      priority: 'URGENT',
    })
    expect(result.title).toBe('更新后的标题')
    // Check local session was updated
    expect(chat.sessions[0].title).toBe('更新后的标题')
  })

  it('updates archive status and refreshes sessions', async () => {
    const chat = useChatStore()
    chat.activeAgentView = 'MY_ACTIVE'
    await chat.updateArchiveStatus('s1', { archiveStatus: 'COMPLETED' })
    expect(setArchiveStatus).toHaveBeenCalledWith('s1', { archiveStatus: 'COMPLETED' })
    // Should reload sessions after archive
    expect(listAgentViewSessions).toHaveBeenCalledWith('MY_ACTIVE', {})
  })

  it('loads user profile', async () => {
    const chat = useChatStore()
    await chat.loadUserProfile('s1')
    expect(getUserProfile).toHaveBeenCalledWith('s1')
    expect(chat.activeUserProfile).toBeDefined()
    expect(chat.activeUserProfile.username).toBe('测试用户')
    expect(chat.activeUserProfile.vipLevel).toBe(3)
  })

  it('selectAgentSession loads messages, metadata, and user profile', async () => {
    const chat = useChatStore()
    // Pre-populate sessions so selectSession can find the session
    chat.sessions = [{ sessionId: 's1' }]
    await chat.selectAgentSession('s1')
    expect(chat.activeSessionId).toBe('s1')
    expect(getSessionMetadata).toHaveBeenCalledWith('s1')
    expect(getUserProfile).toHaveBeenCalledWith('s1')
  })

  it('marks the newest counterpart message as read even when the newest history item was sent by the agent', () => {
    const chat = useChatStore()
    chat.activeSessionId = 's1'
    chat.markRead = vi.fn()

    chat._handleChatEvent({
      event: 'CHAT_HISTORY',
      sessionId: 's1',
      messages: [
        { id: 'm-user', sessionId: 's1', senderId: 'user-1' },
        { id: 'm-agent', sessionId: 's1', senderId: 'agent-1' },
      ],
    })

    expect(chat.markRead).toHaveBeenCalledWith('s1', 'm-user')
  })

  it('uses a readable attachment label instead of exposing an attachment address in the session preview', async () => {
    const chat = useChatStore()
    chat.sessions = [{
      sessionId: 's1',
      title: '附件会话',
      lastMessageContent: '/chat/attachments/3efd884bbaf7/content',
    }]
    const AgentSessionList = (await import('../components/session/AgentSessionList.vue')).default
    const wrapper = mount(AgentSessionList)

    expect(wrapper.text()).toContain('附件消息')
    expect(wrapper.text()).not.toContain('/chat/attachments/')
  })
})

// ─── Quick Reply API tests ───
describe('Quick Reply APIs', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    const auth = useAuthStore()
    auth.login({ token: 'test-token', userId: 'agent-1', role: 'AGENT' })
    vi.clearAllMocks()
  })

  it('lists quick replies', async () => {
    const result = await listQuickReplies()
    expect(result.data).toHaveLength(2)
    expect(result.data[0].title).toBe('问候语')
  })

  it('creates a quick reply', async () => {
    const result = await createQuickReply({ title: '新回复', content: '新内容', sortOrder: 2 })
    expect(result.data.id).toBe('qr3')
  })

  it('deletes a quick reply', async () => {
    const result = await deleteQuickReply('qr1')
    expect(deleteQuickReply).toHaveBeenCalledWith('qr1')
  })
})

// ─── Agent Online/Offline tests ───
describe('Agent Online/Offline', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    const auth = useAuthStore()
    auth.login({ token: 'test-token', userId: 'agent-1', role: 'AGENT' })
    vi.clearAllMocks()
  })

  it('calls agentOnline API', async () => {
    await agentOnline()
    expect(agentOnline).toHaveBeenCalled()
  })
})

// ─── Metadata validation tests ───
describe('Metadata Update Validation', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    const auth = useAuthStore()
    auth.login({ token: 'test-token', userId: 'agent-1', role: 'AGENT' })
    vi.clearAllMocks()
  })

  it('passes correct data shape to updateSessionMetadata', async () => {
    const chat = useChatStore()
    const data = {
      title: '新标题',
      priority: 'HIGH',
      category: 'ACCOUNT',
      tags: ['tag1', 'tag2'],
    }
    await chat.updateSessionMetadata('s1', data)
    expect(updateSessionMetadata).toHaveBeenCalledWith('s1', data)
  })

  it('handles empty tags gracefully', async () => {
    const chat = useChatStore()
    const data = { title: '标题', priority: 'NORMAL', tags: [] }
    await chat.updateSessionMetadata('s1', data)
    expect(updateSessionMetadata).toHaveBeenCalledWith('s1', data)
  })
})

// ─── Archive State Transition tests ───
describe('Archive State Transitions', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    const auth = useAuthStore()
    auth.login({ token: 'test-token', userId: 'agent-1', role: 'AGENT' })
    vi.clearAllMocks()
  })

  it('sends correct archive status and remark', async () => {
    const chat = useChatStore()
    chat.activeAgentView = 'MY_ACTIVE'
    await chat.updateArchiveStatus('s1', { archiveStatus: 'ON_HOLD', remark: '等待用户回复' })
    expect(setArchiveStatus).toHaveBeenCalledWith('s1', {
      archiveStatus: 'ON_HOLD',
      remark: '等待用户回复',
    })
  })
})

// ─── View Selection tests ───
describe('View Selection', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    const auth = useAuthStore()
    auth.login({ token: 'test-token', userId: 'agent-1', role: 'AGENT' })
    vi.clearAllMocks()
  })

  it('starts with MY_ACTIVE as default view', () => {
    const chat = useChatStore()
    expect(chat.activeAgentView).toBe('MY_ACTIVE')
  })

  it('switches view and updates activeAgentView', async () => {
    const chat = useChatStore()
    await chat.switchAgentView('MY_HIGH_PRIORITY')
    expect(chat.activeAgentView).toBe('MY_HIGH_PRIORITY')
  })
})

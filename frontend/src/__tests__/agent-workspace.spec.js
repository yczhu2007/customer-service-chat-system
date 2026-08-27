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
        { code: 'MY_ARCHIVED_COMPLETED', label: '已解决', count: 1 },
        { code: 'MY_ARCHIVED_PENDING', label: '待处理', count: 1 },
        { code: 'MY_ARCHIVED_ON_HOLD', label: '暂停', count: 1 },
        { code: 'MY_ARCHIVED_OTHER', label: '其他', count: 1 },
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
  saveArchiveRemark: vi.fn(() => Promise.resolve({ data: null })),
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
  findAgentDashboard: vi.fn(() => Promise.resolve({ data: { queueSize: 2, activeSessions: [], todayClosedSessions: 4, openTicketCount: 2, inProgressTicketCount: 3, waitingUserTicketCount: 1 } })),
  findAgentRatingSummary: vi.fn(() => Promise.resolve({ data: { averageRating: 4.5, ratingCount: 8 } })),
  findTransferLogs: vi.fn(() => Promise.resolve({ data: [] })),
  getSupportTicket: vi.fn(() => Promise.resolve({ data: null })),
  createSupportTicket: vi.fn(() => Promise.resolve({ data: null })),
  updateSupportTicket: vi.fn(() => Promise.resolve({ data: null })),
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
  searchAgentMessages: vi.fn(() => Promise.resolve({ data: { records: [], total: 0 } })),
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
  saveArchiveRemark,
  getUserProfile,
  listQuickReplies,
  createQuickReply,
  deleteQuickReply,
  agentOnline,
  agentOffline,
  findAgentDashboard,
  findAgentRatingSummary,
  findTransferLogs,
  searchAgentMessages,
} from '../api/chat-api'
import AgentWorkspaceView from '../views/AgentWorkspaceView.vue'
import AgentSessionList from '../components/session/AgentSessionList.vue'
import SessionArchiveActions from '../components/session/SessionArchiveActions.vue'
import AgentViewNav from '../components/session/AgentViewNav.vue'
import AgentMessageSearchPanel from '../components/agent/AgentMessageSearchPanel.vue'
import MessageList from '../components/chat/MessageList.vue'
import AgentOverviewPanel from '../components/agent/AgentOverviewPanel.vue'

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
    expect(chat.agentViewCounts).toHaveLength(9)
    expect(chat.agentViewCounts[0].code).toBe('MY_ACTIVE')
    expect(chat.agentViewCounts[0].count).toBe(3)
  })

  it('loads sessions for a specific agent view', async () => {
    const chat = useChatStore()
    await chat.loadAgentViewSessions('MY_ACTIVE')
    expect(listAgentViewSessions).toHaveBeenCalledWith('MY_ACTIVE', { pageNo: 1, pageSize: 20 })
    expect(chat.sessions).toHaveLength(2)
    expect(chat.sessions[0].sessionId).toBe('s1')
  })

  it('filters the ticket view by ticket status', async () => {
    const chat = useChatStore()

    await chat.filterAgentTickets('OPEN')

    expect(chat.activeAgentView).toBe('MY_TICKETS')
    expect(listAgentViewSessions).toHaveBeenCalledWith('MY_TICKETS', expect.objectContaining({ ticketStatus: 'OPEN' }))
  })

  it('does not apply the ticket filter to ordinary session views', async () => {
    const chat = useChatStore()
    chat.ticketStatusFilter = 'OPEN'

    await chat.loadAgentViewSessions('MY_ACTIVE')

    expect(listAgentViewSessions).toHaveBeenCalledWith('MY_ACTIVE', expect.objectContaining({ ticketStatus: undefined }))
  })

  it('opens a searched message in its session and records the message to focus', async () => {
    const chat = useChatStore()

    await chat.openAgentSearchResult({
      messageId: 'message-7',
      sessionId: 'closed-session',
      sessionTitle: '已结束的支付咨询',
      sessionStatus: 'CLOSED',
      userId: 'user-7',
      agentId: 'agent-1',
    })

    expect(chat.activeSessionId).toBe('closed-session')
    expect(chat.focusedMessageId).toBe('message-7')
    expect(chat.sessions[0].sessionId).toBe('closed-session')
  })

  it('keeps the searched session selected when the current view refresh excludes it', async () => {
    const chat = useChatStore()
    await chat.openAgentSearchResult({
      messageId: 'message-7',
      sessionId: 'closed-session',
      sessionTitle: '已结束的支付咨询',
      sessionStatus: 'CLOSED',
      userId: 'user-7',
      agentId: 'agent-1',
    })
    listAgentViewSessions.mockResolvedValueOnce({
      data: { current: 1, size: 20, total: 1, pages: 1, records: [{ sessionId: 'active-session', status: 'ACTIVE' }] },
    })

    await chat.loadAgentViewSessions('MY_ACTIVE')

    expect(chat.activeSessionId).toBe('closed-session')
    expect(chat.sessions.map((session) => session.sessionId)).toContain('closed-session')
  })

  it('shows all four archive states in the fixed agent view navigation', async () => {
    const wrapper = mount(AgentViewNav)

    expect(wrapper.text()).toContain('已解决')
    expect(wrapper.text()).toContain('待处理')
    expect(wrapper.text()).toContain('暂停')
    expect(wrapper.text()).toContain('其他')
  })

  it('opens a clicked search result through the store navigation action', async () => {
    const chat = useChatStore()
    chat.openAgentSearchResult = vi.fn(() => Promise.resolve())
    searchAgentMessages.mockResolvedValueOnce({
      data: { records: [{ messageId: 'message-7', sessionId: 'closed-session', content: '支付失败' }], total: 1 },
    })
    const wrapper = mount(AgentMessageSearchPanel)

    await wrapper.get('input').setValue('支付失败')
    await wrapper.find('.el-button--primary').trigger('click')
    await nextTick()
    await wrapper.get('.search-result').trigger('click')

    expect(chat.openAgentSearchResult).toHaveBeenCalledWith(expect.objectContaining({
      messageId: 'message-7', sessionId: 'closed-session',
    }))
  })

  it('scrolls to and highlights the searched message after its session loads', async () => {
    const chat = useChatStore()
    const scrollIntoView = vi.fn()
    HTMLElement.prototype.scrollIntoView = scrollIntoView
    chat.activeSessionId = 's1'
    chat.focusedMessageId = 'message-7'
    chat.messages = [{ id: 'message-7', sessionId: 's1', senderId: 'user-7', senderRole: 'USER', type: 'TEXT', content: '支付失败' }]

    const wrapper = mount(MessageList, { attachTo: document.body, props: { sessionId: 's1' } })
    await nextTick()
    await nextTick()

    expect(scrollIntoView).toHaveBeenCalled()
    expect(wrapper.get('#message-message-7').classes()).toContain('is-focused')
    wrapper.unmount()
  })

  it('loads every remaining page for the active agent view and removes duplicate sessions', async () => {
    listAgentViewSessions
      .mockResolvedValueOnce({
        data: { current: 1, size: 20, total: 45, pages: 3, records: [{ sessionId: 's1' }, { sessionId: 's2' }] },
      })
      .mockResolvedValueOnce({
        data: { current: 2, size: 20, total: 45, pages: 3, records: [{ sessionId: 's2' }, { sessionId: 's3' }] },
      })
      .mockResolvedValueOnce({
        data: { current: 3, size: 20, total: 45, pages: 3, records: [{ sessionId: 's4' }] },
      })
    const chat = useChatStore()
    await chat.loadAgentViewSessions('MY_ACTIVE')

    await chat.loadAllRemainingAgentSessions()

    expect(listAgentViewSessions).toHaveBeenNthCalledWith(2, 'MY_ACTIVE', { pageNo: 2, pageSize: 20 })
    expect(listAgentViewSessions).toHaveBeenNthCalledWith(3, 'MY_ACTIVE', { pageNo: 3, pageSize: 20 })
    expect(chat.sessions.map((session) => session.sessionId)).toEqual(['s1', 's2', 's3', 's4'])
    expect(chat.sessionsHasMore).toBe(false)
  })

  it('keeps a newly selected agent view isolated from a delayed load-all response', async () => {
    let releaseOldPage
    listAgentViewSessions
      .mockResolvedValueOnce({
        data: { current: 1, size: 20, total: 21, pages: 2, records: [{ sessionId: 'active-1' }] },
      })
      .mockImplementationOnce(() => new Promise((resolve) => { releaseOldPage = resolve }))
      .mockResolvedValueOnce({
        data: { current: 1, size: 20, total: 1, pages: 1, records: [{ sessionId: 'unread-1' }] },
      })
    const chat = useChatStore()
    await chat.loadAgentViewSessions('MY_ACTIVE')
    const oldLoad = chat.loadAllRemainingAgentSessions()
    await chat.switchAgentView('MY_UNREAD')
    releaseOldPage({ data: { current: 2, size: 20, total: 21, pages: 2, records: [{ sessionId: 'active-2' }] } })
    await oldLoad

    expect(chat.activeAgentView).toBe('MY_UNREAD')
    expect(chat.sessions.map((session) => session.sessionId)).toEqual(['unread-1'])
  })

  it('shows a load-more button that loads all remaining sessions without hiding the current list', async () => {
    const chat = useChatStore()
    chat.sessions = [{ sessionId: 's1', title: '当前会话' }]
    chat.sessionsHasMore = true
    chat.sessionsLoadingMore = false
    chat.loadAllRemainingAgentSessions = vi.fn(() => Promise.resolve())
    const wrapper = mount(AgentSessionList)

    await wrapper.get('.load-more-btn').trigger('click')

    expect(wrapper.text()).toContain('当前会话')
    expect(chat.loadAllRemainingAgentSessions).toHaveBeenCalledOnce()
  })

  it('refreshes both the current view sessions and all view counts from the sidebar button', async () => {
    const chat = useChatStore()
    chat.refreshAgentViews = vi.fn()
    const wrapper = mount(AgentWorkspaceView, { global: { stubs: {
      AgentViewNav: true, AgentSessionList: true, AgentChatWindow: true, SessionMetadataEditor: true,
      SessionArchiveActions: true, UserProfileSidebar: true, QuickReplyPanel: true, ConnectionStatus: true,
      AgentOverviewPanel: true, TransferLogPanel: true,
    } } })

    await wrapper.get('.refresh-btn').trigger('click')

    expect(chat.refreshAgentViews).toHaveBeenCalledOnce()
  })

  it('switches active agent view and refreshes sessions', async () => {
    const chat = useChatStore()
    await chat.switchAgentView('MY_UNREAD')
    expect(chat.activeAgentView).toBe('MY_UNREAD')
    expect(listAgentViewSessions).toHaveBeenCalledWith('MY_UNREAD', { pageNo: 1, pageSize: 20 })
  })

  it('loads agent dashboard and rating summary data', async () => {
    const chat = useChatStore()
    await chat.loadAgentDashboard()

    expect(findAgentDashboard).toHaveBeenCalled()
    expect(findAgentRatingSummary).toHaveBeenCalled()
    expect(chat.agentDashboard.queueSize).toBe(2)
    expect(chat.agentRatingSummary.averageRating).toBe(4.5)
  })

  it('shows ticket counts on the agent dashboard', async () => {
    const wrapper = mount(AgentOverviewPanel)
    await new Promise((resolve) => setTimeout(resolve, 0))
    await nextTick()

    expect(wrapper.text()).toContain('待处理工单')
    expect(wrapper.text()).toContain('处理中工单')
    expect(wrapper.text()).toContain('等待用户')
  })

  it('loads transfer logs for the selected session', async () => {
    const chat = useChatStore()
    await chat.loadTransferLogs('s1')
    expect(findTransferLogs).toHaveBeenCalledWith('s1')
    expect(chat.transferLogs).toEqual([])
  })

  it('records transport errors without changing a still-connected state', () => {
    const chat = useChatStore()
    chat.connected = true
    chat.connectionState = 'connected'
    chat.handleConnectionError('连接被服务器关闭')

    expect(chat.connectionState).toBe('connected')
    expect(chat.connectionError).toBe('连接被服务器关闭')
    expect(chat.connectionLogs).toHaveLength(0)
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

  it('updates read state and clears the unread badge from a read event', () => {
    const auth = useAuthStore()
    auth.login({ token: 't', userId: 'u2', role: 'AGENT' })
    const chat = useChatStore()
    chat.activeSessionId = 's1'
    chat.unreadCounts = { s1: 2 }
    chat.messages = [
      { id: 'm1', sessionId: 's1', senderId: 'u1' },
      { id: 'm2', sessionId: 's1', senderId: 'u1' },
      { id: 'm3', sessionId: 's1', senderId: 'u2' },
    ]

    chat._handleMessageEvent({
      event: 'MESSAGES_READ',
      sessionId: 's1',
      readerId: 'u2',
      lastReadMessageId: 'm2',
    })

    expect(chat.unreadCounts.s1).toBe(0)
    expect(chat.messages[0].read).toBe(true)
    expect(chat.messages[1].read).toBe(true)
    expect(chat.messages[2].read).not.toBe(true)
  })

  it('does not count an agent persisted message as unread when another session is active', () => {
    const chat = useChatStore()
    chat.activeSessionId = 's2'

    chat._handleChatEvent({ id: 'm1', sessionId: 's1', senderId: 'agent-1', type: 'TEXT', content: '回复' })

    expect(chat.unreadCounts.s1).toBeUndefined()
  })

  it('rejects transfer and end actions while disconnected', () => {
    const chat = useChatStore()
    chat.activeSessionId = 's1'

    expect(chat.transferSession('s1', 'agent-2')).toBe(false)
    expect(chat.endSession('s1')).toBe(false)
    expect(chat.error).toBe('未连接到服务器')
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
    expect(listAgentViewSessions).toHaveBeenCalledWith('MY_ACTIVE', { pageNo: 1, pageSize: 20 })
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

  it('does not call the full online endpoint again as a periodic heartbeat', async () => {
    vi.useFakeTimers()
    const wrapper = mount(AgentWorkspaceView, { global: { stubs: { AgentViewNav: true, AgentSessionList: true, AgentChatWindow: true, SessionMetadataEditor: true, SessionArchiveActions: true, UserProfileSidebar: true, QuickReplyPanel: true, ConnectionStatus: true, AgentOverviewPanel: true, TransferLogPanel: true } } })
    await vi.runAllTicks()

    expect(agentOnline).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(30000)
    expect(agentOnline).toHaveBeenCalledTimes(1)

    wrapper.unmount()
    vi.useRealTimers()
  })

  it('takes the agent offline when the agent workspace is left', async () => {
    const wrapper = mount(AgentWorkspaceView, { global: { stubs: { AgentViewNav: true, AgentSessionList: true, AgentChatWindow: true, SessionMetadataEditor: true, SessionArchiveActions: true, UserProfileSidebar: true, QuickReplyPanel: true, ConnectionStatus: true, AgentOverviewPanel: true, TransferLogPanel: true } } })
    await nextTick()

    wrapper.unmount()

    expect(agentOffline).toHaveBeenCalled()
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

  it('shows the one saved archive remark again when reopening its session', async () => {
    const chat = useChatStore()
    chat.sessions = [
      { sessionId: 's1', status: 'CLOSED', archiveRemark: '已保存的备注' },
      { sessionId: 's2', status: 'CLOSED' },
    ]
    chat.activeSessionId = 's1'
    const wrapper = mount(SessionArchiveActions)
    const textarea = wrapper.get('textarea')
    expect(textarea.element.value).toBe('已保存的备注')

    await textarea.setValue('尚未保存的新内容')

    chat.activeSessionId = 's2'
    await nextTick()

    expect(wrapper.get('textarea').element.value).toBe('')

    chat.activeSessionId = 's1'
    await nextTick()

    expect(wrapper.get('textarea').element.value).toBe('已保存的备注')
  })

  it('saves the archive remark independently without changing archive status', async () => {
    const chat = useChatStore()
    chat.sessions = [
      { sessionId: 's1', status: 'CLOSED', archiveStatus: 'PENDING', archiveRemark: '旧备注' },
    ]
    chat.activeSessionId = 's1'
    const wrapper = mount(SessionArchiveActions)
    await wrapper.get('textarea').setValue('新的唯一备注')

    const saveButton = wrapper.findAll('button').find((button) => button.text() === '保存备注')
    expect(saveButton).toBeTruthy()
    await saveButton.trigger('click')
    await nextTick()

    expect(saveArchiveRemark).toHaveBeenCalledWith('s1', { remark: '新的唯一备注' })
    expect(chat.activeSession.archiveRemark).toBe('新的唯一备注')
    expect(chat.activeSession.archiveStatus).toBe('PENDING')
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

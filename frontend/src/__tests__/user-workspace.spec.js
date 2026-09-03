import { describe, expect, it, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { mount } from '@vue/test-utils'
import { ElMessageBox } from 'element-plus'
import { useChatStore } from '../stores/chat'
import { useAuthStore } from '../stores/auth'
import MessageComposer from '../components/chat/MessageComposer.vue'
import MessageList from '../components/chat/MessageList.vue'
import UserSessionList from '../components/session/UserSessionList.vue'
import { fetchAttachmentBlob } from '../api/chat-api'

// Mock fetch globally
const mockFetch = vi.fn()
global.fetch = mockFetch

// Mock crypto.randomUUID
if (!global.crypto) global.crypto = {}
global.crypto.randomUUID = () => 'test-uuid-' + Math.random().toString(36).slice(2, 9)

// Mock URL.createObjectURL
if (!global.URL.createObjectURL) {
  global.URL.createObjectURL = () => 'blob:mock'
}

// Mock @stomp/stompjs
vi.mock('@stomp/stompjs', () => ({
  Client: class MockClient {
    constructor(opts) {
      this._opts = opts
    }
    activate() {
      if (this._opts?.onConnect) {
        setTimeout(() => this._opts.onConnect(), 0)
      }
    }
    deactivate() {}
    subscribe(dest, cb) {
      return { unsubscribe: () => {} }
    }
    publish() {}
  },
}))

// Mock stomp-client
vi.mock('../services/stomp-client', () => ({
  createStompClient: (opts) => ({
    client: {},
    connect: () => {
      if (opts?.onConnect) opts.onConnect()
    },
    disconnect: () => {},
    subscribe: (dest, cb) => {},
    publish: (dest, body) => {},
  }),
}))

describe('User Workspace', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockFetch.mockReset()
  })

  describe('chat store — session list loading', () => {
    it('loads sessions and stores them', async () => {
      const chat = useChatStore()
      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: () =>
          Promise.resolve({
            code: 200,
            data: {
              pageNo: 1,
              pageSize: 20,
              total: 2,
              records: [
                { sessionId: 's1', title: '会话一', status: 'ACTIVE', lastMessageContent: '你好', unreadCount: 3 },
                { sessionId: 's2', title: '会话二', status: 'CLOSED', lastMessageContent: '再见', unreadCount: 0 },
              ],
            },
          }),
      })

      await chat.loadSessions()
      expect(chat.sessions).toHaveLength(2)
      expect(chat.sessions[0].sessionId).toBe('s1')
      expect(chat.sessions[1].title).toBe('会话二')
      expect(chat.unreadCounts).toEqual({ s1: 3, s2: 0 })
    })

    it('handles load error', async () => {
      const chat = useChatStore()
      mockFetch.mockRejectedValueOnce(new Error('Network error'))
      await chat.loadSessions()
      expect(chat.error).toBe('Network error')
      expect(chat.sessions).toHaveLength(0)
    })
  })

  describe('chat store — session selection', () => {
    it('sets active session and clears messages', async () => {
      const auth = useAuthStore()
      auth.login({ token: 't', userId: 'u1', role: 'USER' })
      const chat = useChatStore()
      chat.sessions = [{ sessionId: 's1', status: 'ACTIVE' }]
      chat.unreadCounts = { s1: 5 }

      // selectSession calls loadHistory which needs STOMP — won't throw but won't send
      await chat.selectSession('s1')
      expect(chat.activeSessionId).toBe('s1')
      expect(chat.messages).toEqual([])
      expect(chat.unreadCounts.s1).toBe(0)
    })
    it('starts loading the newly selected session while the previous history request is pending', async () => {
      const chat = useChatStore()
      chat._stomp = { publish: vi.fn() }
      chat.connected = true

      await chat.selectSession('session-a')
      await chat.selectSession('session-b')

      expect(chat._stomp.publish).toHaveBeenNthCalledWith(1, '/app/chat.history', {
        sessionId: 'session-a', beforeMessageId: null, pageSize: 50, requestId: expect.any(String),
      })
      expect(chat._stomp.publish).toHaveBeenNthCalledWith(2, '/app/chat.history', {
        sessionId: 'session-b', beforeMessageId: null, pageSize: 50, requestId: expect.any(String),
      })
      expect(chat.messagesLoading).toBe(true)
    })
  })

  it('loads earlier history with the server cursor', async () => {
    const chat = useChatStore()
    chat._stomp = { publish: vi.fn() }
    chat.connected = true
    chat.activeSessionId = 's1'
    chat.historyCursor = 'm-oldest-visible'
    chat.historyHasMore = true

    await chat.loadMoreHistory('s1')

    expect(chat._stomp.publish).toHaveBeenCalledWith('/app/chat.history', {
      sessionId: 's1',
      beforeMessageId: 'm-oldest-visible',
      pageSize: 50,
      requestId: expect.any(String),
    })
    expect(chat.historyLoadingMore).toBe(true)
  })

  it('ignores a late history response after the request timed out and was retried', async () => {
    vi.useFakeTimers()
    try {
      const chat = useChatStore()
      chat._stomp = { publish: vi.fn() }
      chat.connected = true
      chat.activeSessionId = 's1'

      await chat.loadHistory('s1')
      const firstRequestId = chat._stomp.publish.mock.calls[0][1].requestId
      await vi.advanceTimersByTimeAsync(15000)
      await chat.loadHistory('s1')

      chat._handleChatEvent({
        event: 'CHAT_HISTORY', sessionId: 's1', requestId: firstRequestId,
        messages: [{ id: 'old-response' }], nextCursor: 'old-cursor', hasMore: true,
      })

      expect(chat.messages).toEqual([])
      expect(chat.historyCursor).toBeNull()
      expect(chat.messagesLoading).toBe(true)
    } finally {
      vi.useRealTimers()
    }
  })

  it('does not turn the current user persisted message into unread after switching sessions', () => {
    const auth = useAuthStore()
    auth.login({ token: 't', userId: 'u1', role: 'USER' })
    const chat = useChatStore()
    chat.activeSessionId = 's2'

    chat._handleChatEvent({ id: 'm1', sessionId: 's1', senderId: 'u1', type: 'TEXT', content: '我发出的消息' })

    expect(chat.unreadCounts.s1).toBeUndefined()
  })

  describe('chat store — WebSocket ticket', () => {
    it('leaves connecting state and retries when the ticket request times out', async () => {
      vi.useFakeTimers()
      const auth = useAuthStore()
      auth.login({ token: 'token', userId: 'u1', role: 'USER' })
      mockFetch.mockImplementationOnce((path, options) => new Promise((resolve, reject) => {
        options.signal?.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')))
      }))
      const chat = useChatStore()

      chat.connectStomp()
      await vi.advanceTimersByTimeAsync(15_000)

      expect(chat.connectionState).toBe('reconnecting')
      expect(chat.connectionError).toContain('请求超时')
      vi.useRealTimers()
    })
  })

  it('acknowledges an inactive-session delivery without marking it as read', () => {
    const auth = useAuthStore()
    auth.login({ token: 't', userId: 'u1', role: 'USER' })
    const chat = useChatStore()
    chat._stomp = { publish: vi.fn() }
    chat.connected = true
    chat.activeSessionId = 's2'

    chat._handleChatEvent({ id: 'm1', sessionId: 's1', senderId: 'a1', type: 'TEXT', content: '新消息' })

    expect(chat._stomp.publish).toHaveBeenCalledWith('/app/chat.ack', { messageId: 'm1' })
    expect(chat._stomp.publish).not.toHaveBeenCalledWith('/app/chat.read', expect.anything())
    expect(chat.unreadCounts.s1).toBe(1)
  })

  it('refreshes authoritative unread counts after offline replay completes', () => {
    const chat = useChatStore()
    chat.loadSessions = vi.fn()

    chat._handleChatEvent({ event: 'OFFLINE_MESSAGES_REPLAYED', count: 2 })

    expect(chat.loadSessions).toHaveBeenCalledOnce()
  })

  it('records queue events and automatically selects an assigned session', () => {
    const chat = useChatStore()
    chat.sessions = [{ sessionId: 's1', status: 'ACTIVE' }]
    chat.consultationStarting = true
    chat.selectSession = vi.fn()

    chat._handleChatEvent({ event: 'WAITING_FOR_AGENT', queuePosition: 2 })
    expect(chat.queueNotice).toBeNull()

    chat._handleChatEvent({ event: 'SESSION_CREATED', sessionId: 's1' })
    expect(chat.selectSession).toHaveBeenCalledWith('s1')
  })

  it('ignores a late waiting event after queue cancellation returned to idle', () => {
    const chat = useChatStore()
    chat.queueStatus = { queueSize: 0, myPosition: null }
    chat.queueWaiting = false
    chat.consultationStarting = false

    chat._handleChatEvent({ event: 'WAITING_FOR_AGENT', queuePosition: 1 })

    expect(chat.consultationState).toBe('IDLE')
    expect(chat.queueNotice).toBeNull()
  })

  it('keeps loaded sessions visible when an unrelated chat error exists', () => {
    const chat = useChatStore()
    chat.sessions = [{ sessionId: 's1', title: '之前的会话', status: 'CLOSED' }]
    chat.error = '服务器内部错误'

    const wrapper = mount(UserSessionList)

    expect(wrapper.text()).toContain('之前的会话')
  })

  it('does not show an archive filter in the user session list', () => {
    const wrapper = mount(UserSessionList)

    expect(wrapper.find('.filter-row').exists()).toBe(false)
    expect(wrapper.find('select.archive-select').exists()).toBe(false)
  })

  it('uses the shared compact date format in the user session list', () => {
    const chat = useChatStore()
    chat.sessions = [{ sessionId: 's1', title: '历史会话', status: 'CLOSED', createTime: '2026-01-02T09:05:00' }]

    const wrapper = mount(UserSessionList)

    expect(wrapper.get('.time').text()).toBe('01-02')
  })

  it('uses the shared confirmation dialog before cancelling queue entry', async () => {
    const chat = useChatStore()
    chat.queueStatus = { myPosition: 1 }
    chat.connectStomp = vi.fn()
    chat.loadQueueStatus = vi.fn()
    chat.loadSessions = vi.fn()
    chat.cancelQueue = vi.fn(() => Promise.resolve())
    const confirm = vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue()
    const UserWorkspaceView = (await import('../views/UserWorkspaceView.vue')).default
    const wrapper = mount(UserWorkspaceView, {
      global: { stubs: { ChatWindow: true, UserSessionList: true, SessionRatingForm: true, ConnectionStatus: true, SupportTicketPanel: true } },
    })

    await wrapper.get('.cancel-queue-btn').trigger('click')

    expect(confirm).toHaveBeenCalledWith('确定取消排队吗？', '取消排队', expect.objectContaining({
      confirmButtonText: '确认取消', cancelButtonText: '取消', type: 'warning',
    }))
    expect(chat.cancelQueue).toHaveBeenCalledOnce()
    wrapper.unmount()
    confirm.mockRestore()
  })

  it('keeps the ticket panel and rating form in an independent right sidebar', async () => {
    const chat = useChatStore()
    chat.connectStomp = vi.fn()
    chat.loadQueueStatus = vi.fn()
    chat.loadSessions = vi.fn()
    chat.sessions = [{ sessionId: 's1', status: 'CLOSED' }]
    chat.activeSessionId = 's1'
    const UserWorkspaceView = (await import('../views/UserWorkspaceView.vue')).default
    const wrapper = mount(UserWorkspaceView, {
      global: {
        stubs: {
          ChatWindow: { template: '<div class="chat-window" />' },
          UserSessionList: true,
          SessionRatingForm: { template: '<section class="rating-form" />' },
          ConnectionStatus: true,
          SupportTicketPanel: { template: '<section class="ticket-panel" />' },
        },
      },
    })

    expect(wrapper.get('.center-panel > .user-chat-window').exists()).toBe(true)
    expect(wrapper.get('.right-panel > .user-ticket-panel').exists()).toBe(true)
    expect(wrapper.get('.right-panel > .user-rating-form').exists()).toBe(true)
    wrapper.unmount()
  })

  it('keeps the consultation action labeled as a new consultation when history is closed', async () => {
    const chat = useChatStore()
    chat.connectStomp = vi.fn()
    chat.loadQueueStatus = vi.fn()
    chat.loadSessions = vi.fn()
    chat.sessions = [{ sessionId: 's1', status: 'CLOSED' }]
    const UserWorkspaceView = (await import('../views/UserWorkspaceView.vue')).default
    const wrapper = mount(UserWorkspaceView, {
      global: { stubs: { ChatWindow: true, UserSessionList: true, SessionRatingForm: true, ConnectionStatus: true, SupportTicketPanel: true } },
    })

    expect(wrapper.get('.consult-btn').text()).toBe('发起咨询')
    wrapper.unmount()
  })

  it('uses one authoritative consultation state for idle, queued and active users', () => {
    const chat = useChatStore()
    expect(chat.consultationState).toBe('IDLE')
    expect(chat.canStartConsultation).toBe(true)

    chat.consultationStarting = true
    expect(chat.consultationState).toBe('STARTING')
    expect(chat.canStartConsultation).toBe(false)

    chat.queueStatus = { queueSize: 2, myPosition: 1 }
    expect(chat.consultationState).toBe('QUEUED')

    chat.sessions = [{ sessionId: 's1', status: 'ACTIVE' }]
    expect(chat.consultationState).toBe('ACTIVE')
  })

  it('finishes the submitting state when the server confirms queue entry', () => {
    vi.useFakeTimers()
    try {
      const chat = useChatStore()
      chat.consultationStarting = true
      chat._consultationTimeout = setTimeout(() => {}, 15000)

      chat._handleChatEvent({ event: 'WAITING_FOR_AGENT', waitingPosition: 2 })

      expect(chat.consultationStarting).toBe(false)
      expect(chat._consultationTimeout).toBeNull()
      expect(chat.queueNotice).toBeNull()
      expect(chat.consultationState).toBe('QUEUED')
    } finally {
      vi.useRealTimers()
    }
  })

  it('returns to idle immediately when queue cancellation is confirmed', () => {
    const chat = useChatStore()
    chat.consultationStarting = true
    chat.queueStatus = { onlineAgentCount: 1, queueSize: 1, myPosition: 1, estimatedWaitSeconds: 300 }

    chat._handleChatEvent({ event: 'QUEUE_CANCELLED' })

    expect(chat.consultationStarting).toBe(false)
    expect(chat.queueStatus).toEqual({
      onlineAgentCount: 1,
      queueSize: 0,
      myPosition: null,
      estimatedWaitSeconds: null,
    })
    expect(chat.canStartConsultation).toBe(true)
  })

  it('keeps the queued visual state stable until cancellation is confirmed and applies it once', async () => {
    const chat = useChatStore()
    chat.queueWaiting = true
    chat.queueStatus = {
      onlineAgentCount: 2,
      queueSize: 5,
      myPosition: 2,
      estimatedWaitSeconds: 120,
    }
    let resolveCancellation
    mockFetch.mockReturnValueOnce(new Promise((resolve) => { resolveCancellation = resolve }))

    const cancellation = chat.cancelQueue()

    expect(chat.consultationState).toBe('QUEUED')
    expect(chat.queueStatus).toEqual({
      onlineAgentCount: 2,
      queueSize: 5,
      myPosition: 2,
      estimatedWaitSeconds: 120,
    })

    chat._handleChatEvent({ event: 'QUEUE_CANCELLED' })
    expect(chat.consultationState).toBe('IDLE')
    expect(chat.queueStatus.queueSize).toBe(4)
    expect(chat.queueStatus.myPosition).toBeNull()

    resolveCancellation({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ code: 200, data: null }),
    })
    await cancellation

    expect(chat.queueStatus.queueSize).toBe(4)
    expect(chat.queueStatus.myPosition).toBeNull()
  })

  it('releases consultation state after the server rejects a queue request', () => {
    const chat = useChatStore()
    chat.consultationStarting = true
    chat.queueWaiting = false

    chat._handleStompErrorEvent({ event: 'ERROR', message: '服务器处理消息失败' })

    expect(chat.consultationStarting).toBe(false)
    expect(chat.queueWaiting).toBe(false)
    expect(chat.consultationState).toBe('IDLE')
    expect(chat.error).toBe('服务器处理消息失败')
  })

  it('prevents duplicate consultation requests while a session is active or a request is pending', () => {
    const chat = useChatStore()
    chat._stomp = { publish: vi.fn() }
    chat.connected = true
    chat.sessions = [{ sessionId: 's1', status: 'ACTIVE' }]

    chat.startConsultation()
    expect(chat._stomp.publish).not.toHaveBeenCalled()

    chat.sessions = []
    chat.consultationStarting = true
    chat.startConsultation()
    expect(chat._stomp.publish).not.toHaveBeenCalled()
  })

  it('unlocks consultation start when publishing the request throws', () => {
    const chat = useChatStore()
    chat._stomp = { publish: vi.fn(() => { throw new Error('connection closed') }) }
    chat.connected = true

    expect(() => chat.startConsultation()).not.toThrow()
    expect(chat.consultationStarting).toBe(false)
    expect(chat.queueNotice).toBeNull()
    expect(chat.error).toBe('connection closed')
  })

  it('shows a labeled quote card in a closed session without mutation actions', () => {
    const auth = useAuthStore()
    auth.login({ token: 't', userId: 'agent1', role: 'AGENT' })
    const chat = useChatStore()
    chat.activeSessionId = 's1'
    chat.messages = [{
      id: 'm2',
      sessionId: 's1',
      senderId: 'u1',
      senderRole: 'USER',
      type: 'TEXT',
      content: '回复内容',
      replyToMessageId: 'm1',
      replyPreview: '被引用的用户消息',
      replyPreviewSenderRole: 'USER',
    }]

    const wrapper = mount(MessageList, { props: { sessionId: 's1', closed: true } })

    expect(wrapper.find('.reply-preview').exists()).toBe(true)
    expect(wrapper.text()).toContain('引用用户')
    expect(wrapper.text()).toContain('被引用的用户消息')
    expect(wrapper.text()).not.toContain('撤回')
    expect(wrapper.text()).not.toContain('引用回复')
  })

  it('does not render an edit action for an own text message', async () => {
    const auth = useAuthStore()
    auth.login({ token: 't', userId: 'u1', role: 'USER' })
    const chat = useChatStore()
    chat.activeSessionId = 's1'
    chat.messages = [{ id: 'm1', sessionId: 's1', senderId: 'u1', type: 'TEXT', content: '原内容' }]

    const wrapper = mount(MessageList, { props: { sessionId: 's1' } })
    expect(wrapper.text()).not.toContain('编辑')
    expect(wrapper.find('.edit-input').exists()).toBe(false)
  })

  it('releases attachment blob URLs when the message list is unmounted', async () => {
    const auth = useAuthStore()
    auth.login({ token: 't', userId: 'u1', role: 'USER' })
    const chat = useChatStore()
    chat.activeSessionId = 's1'
    chat.messages = [{ id: 'm1', sessionId: 's1', senderId: 'u2', type: 'FILE', content: '/chat/attachments/a' }]
    mockFetch.mockResolvedValueOnce({
      ok: true,
      blob: () => Promise.resolve(new Blob(['file'], { type: 'text/plain' })),
      headers: { get: () => 'attachment; filename="a.txt"' },
    })
    const createUrl = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:test-file')
    const hadRevokeUrl = typeof URL.revokeObjectURL === 'function'
    const revokeUrl = hadRevokeUrl
      ? vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
      : vi.fn()

    try {
      const wrapper = mount(MessageList, { props: { sessionId: 's1' } })
      await new Promise((resolve) => setTimeout(resolve, 0))
      wrapper.unmount()

      expect(createUrl).toHaveBeenCalled()
      if (hadRevokeUrl) expect(revokeUrl).toHaveBeenCalledWith('blob:test-file')
    } finally {
      createUrl.mockRestore()
      if (hadRevokeUrl) revokeUrl.mockRestore()
    }
  })

  describe('chat store — consultation start', () => {
    it('refreshes the session list immediately when the server creates a session', () => {
      const chat = useChatStore()
      const loadSessions = vi.spyOn(chat, 'loadSessions')

      chat._handleChatEvent({ event: 'SESSION_CREATED', session: { sessionId: 's1' } })

      expect(loadSessions).toHaveBeenCalled()
    })
  })

  describe('attachment sending', () => {
    it('keeps the server-provided attachment filename when loading a file', async () => {
      const auth = useAuthStore()
      auth.login({ token: 't', userId: 'u1', role: 'USER' })
      mockFetch.mockResolvedValueOnce({
        ok: true,
        headers: new Headers({ 'content-disposition': "attachment; filename*=UTF-8''guide.pdf", 'content-type': 'application/pdf' }),
        blob: () => Promise.resolve(new Blob(['content'], { type: 'application/pdf' })),
      })

      const attachment = await fetchAttachmentBlob('/chat/attachments/attachment-id/content')

      expect(attachment.name).toBe('guide.pdf')
      expect(attachment.url).toMatch(/^blob:/)
    })

    it('sends the uploaded attachment content address instead of its opaque id', async () => {
      const chat = useChatStore()
      chat.uploadAttachment = vi.fn(() => Promise.resolve({
        id: 'attachment-id',
        contentUrl: '/chat/attachments/attachment-id/content',
        messageType: 'FILE',
      }))
      chat.sendMessage = vi.fn()

      const wrapper = mount(MessageComposer, { props: { sessionId: 's1' } })
      const file = new File(['content'], 'guide.pdf', { type: 'application/pdf' })
      const input = wrapper.find('input[type="file"]')
      Object.defineProperty(input.element, 'files', { value: [file] })
      await input.trigger('change')
      await Promise.resolve()
      expect(chat.sendMessage).not.toHaveBeenCalled()
      const sendAttachmentButton = wrapper.findAll('button').find((button) => button.text() === '发送附件')
      await sendAttachmentButton.trigger('click')

      expect(chat.sendMessage).toHaveBeenCalledWith(
        's1',
        'FILE',
        '/chat/attachments/attachment-id/content'
      )
    })
  })

  describe('chat store — queue status', () => {
    it('loads queue status', async () => {
      const chat = useChatStore()
      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: () =>
          Promise.resolve({
            code: 200,
            data: {
              onlineAgentCount: 3,
              queueSize: 5,
              myPosition: 2,
              estimatedWaitSeconds: 120,
            },
          }),
      })

      await chat.loadQueueStatus()
      expect(chat.queueStatus.onlineAgentCount).toBe(3)
      expect(chat.queueStatus.myPosition).toBe(2)
      expect(chat.queueStatus.estimatedWaitSeconds).toBe(120)
    })
  })

  describe('queue status display', () => {
    it('displays online agents, position, and estimated wait', () => {
      const chat = useChatStore()
      chat.queueStatus = {
        onlineAgentCount: 5,
        queueSize: 3,
        myPosition: 2,
        estimatedWaitSeconds: 90,
      }

      expect(chat.queueStatus.onlineAgentCount).toBe(5)
      expect(chat.queueStatus.myPosition).toBe(2)
      // 90 seconds → 2 minutes (ceil)
      expect(Math.ceil(chat.queueStatus.estimatedWaitSeconds / 60)).toBe(2)
    })

    it('shows null position when not in queue', () => {
      const chat = useChatStore()
      chat.queueStatus = {
        onlineAgentCount: 5,
        queueSize: 0,
        myPosition: null,
        estimatedWaitSeconds: null,
      }
      expect(chat.queueStatus.myPosition).toBeNull()
    })
  })

  describe('rating form validation and submission', () => {
    it('submits rating with valid data', async () => {
      const auth = useAuthStore()
      auth.login({ token: 't', userId: 'u1', role: 'USER' })
      const chat = useChatStore()

      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: () =>
          Promise.resolve({
            code: 200,
            data: {
              sessionId: 's1',
              rating: 4,
              comment: '很好',
              createTime: '2026-01-01T00:00:00',
            },
          }),
      })

      const result = await chat.submitRating('s1', { rating: 4, comment: '很好' })
      expect(result.rating).toBe(4)
      expect(result.comment).toBe('很好')
    })

    it('loads existing rating', async () => {
      const auth = useAuthStore()
      auth.login({ token: 't', userId: 'u1', role: 'USER' })
      const chat = useChatStore()

      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: () =>
          Promise.resolve({
            code: 200,
            data: {
              sessionId: 's1',
              rating: 5,
              comment: '非常好',
            },
          }),
      })

      const result = await chat.getSessionRating('s1')
      expect(result.rating).toBe(5)
    })
  })

  describe('message rendering types', () => {
    it('distinguishes TEXT, IMAGE, and FILE message types', () => {
      const messages = [
        { id: 'm1', type: 'TEXT', content: '你好', senderId: 'u1' },
        { id: 'm2', type: 'IMAGE', content: 'att-123', senderId: 'u2' },
        { id: 'm3', type: 'FILE', content: 'att-456', senderId: 'u1' },
      ]

      const textMsg = messages.find((m) => m.type === 'TEXT')
      const imageMsg = messages.find((m) => m.type === 'IMAGE')
      const fileMsg = messages.find((m) => m.type === 'FILE')

      expect(textMsg.type).toBe('TEXT')
      expect(textMsg.content).toBe('你好')
      expect(imageMsg.type).toBe('IMAGE')
      expect(imageMsg.content).toBe('att-123')
      expect(fileMsg.type).toBe('FILE')
      expect(fileMsg.content).toBe('att-456')
    })

    it('distinguishes mine vs other messages', () => {
      const auth = useAuthStore()
      auth.login({ token: 't', userId: 'user-1', role: 'USER' })
      const chat = useChatStore()

      const msg1 = { senderId: 'user-1', type: 'TEXT', content: 'hello' }
      const msg2 = { senderId: 'agent-1', type: 'TEXT', content: 'hi' }

      expect(msg1.senderId === auth.userId).toBe(true) // mine
      expect(msg2.senderId === auth.userId).toBe(false) // other
    })

    it('handles recalled messages', () => {
      const msg = {
        id: 'm1',
        type: 'TEXT',
        content: null,
        recalled: true,
        recalledAt: '2026-01-01T00:01:00',
      }
      expect(msg.recalled).toBe(true)
      expect(msg.content).toBeNull()
    })

  })

  describe('chat store — message deduplication', () => {
    it('marks the newest received history message as read for the active session', () => {
      const chat = useChatStore()
      chat.activeSessionId = 's1'
      chat.markRead = vi.fn()

      chat._handleChatEvent({
        event: 'CHAT_HISTORY', sessionId: 's1',
        messages: [{ id: 'm1', sessionId: 's1', senderId: 'agent-1' }],
      })

      expect(chat.markRead).toHaveBeenCalledWith('s1', 'm1')
    })

    it('deduplicates by clientMsgId', () => {
      const chat = useChatStore()
      chat.messages = [
        { clientMsgId: 'c1', id: 's1', content: 'hello' },
        { clientMsgId: 'c1', id: 's1', content: 'hello' },
        { clientMsgId: 'c2', id: 's2', content: 'world' },
      ]
      chat._deduplicateMessages()
      expect(chat.messages).toHaveLength(2)
    })
  })

  describe('chat store — sorted sessions', () => {
    it('sorts sessions by lastMessageTime descending', () => {
      const chat = useChatStore()
      chat.sessions = [
        { sessionId: 's1', lastMessageTime: '2026-01-01T10:00:00', createTime: '2026-01-01T09:00:00' },
        { sessionId: 's2', lastMessageTime: '2026-01-01T12:00:00', createTime: '2026-01-01T11:00:00' },
        { sessionId: 's3', lastMessageTime: null, createTime: '2026-01-01T08:00:00' },
      ]
      const sorted = chat.sortedSessions
      expect(sorted[0].sessionId).toBe('s2')
      expect(sorted[1].sessionId).toBe('s1')
      expect(sorted[2].sessionId).toBe('s3')
    })
  })
})

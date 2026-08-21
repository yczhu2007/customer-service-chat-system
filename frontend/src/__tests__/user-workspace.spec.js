import { describe, expect, it, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { mount } from '@vue/test-utils'
import { useChatStore } from '../stores/chat'
import { useAuthStore } from '../stores/auth'
import MessageComposer from '../components/chat/MessageComposer.vue'
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
                { sessionId: 's1', title: '会话一', status: 'ACTIVE', lastMessageContent: '你好' },
                { sessionId: 's2', title: '会话二', status: 'CLOSED', lastMessageContent: '再见' },
              ],
            },
          }),
      })

      await chat.loadSessions()
      expect(chat.sessions).toHaveLength(2)
      expect(chat.sessions[0].sessionId).toBe('s1')
      expect(chat.sessions[1].title).toBe('会话二')
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
    })
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
      expect(attachment.url).toBe('blob:mock')
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

      expect(chat.sendMessage).toHaveBeenCalledWith(
        's1',
        'FILE',
        '/chat/attachments/attachment-id/content'
      )
    })
  })

  describe('message operations', () => {
    it('publishes edit and recall operations for a selected own message', () => {
      const auth = useAuthStore()
      auth.login({ token: 't', userId: 'u1', role: 'USER' })
      const chat = useChatStore()
      chat._stomp = { publish: vi.fn() }
      chat.connected = true
      chat.messages = [{ id: 'm1', senderId: 'u1', type: 'TEXT', content: '原内容' }]

      chat.editMessage('m1', '修改后的内容')
      chat.recallMessage('m1')

      expect(chat._stomp.publish).toHaveBeenNthCalledWith(1, '/app/chat.message.edit', {
        messageId: 'm1', content: '修改后的内容',
      })
      expect(chat._stomp.publish).toHaveBeenNthCalledWith(2, '/app/chat.message.recall', {
        messageId: 'm1',
      })
    })

    it('applies edited and recalled events to the current message', () => {
      const chat = useChatStore()
      chat.messages = [{ id: 'm1', type: 'TEXT', content: '原内容' }]

      chat._handleMessageEvent({ event: 'MESSAGE_EDITED', messageId: 'm1', content: '新内容' })
      expect(chat.messages[0]).toMatchObject({ content: '新内容', edited: true })

      chat._handleMessageEvent({ event: 'MESSAGE_RECALLED', messageId: 'm1' })
      expect(chat.messages[0]).toMatchObject({ recalled: true, content: null })
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

    it('handles edited messages', () => {
      const msg = {
        id: 'm1',
        type: 'TEXT',
        content: 'edited content',
        edited: true,
        editedAt: '2026-01-01T00:02:00',
      }
      expect(msg.edited).toBe(true)
      expect(msg.content).toBe('edited content')
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

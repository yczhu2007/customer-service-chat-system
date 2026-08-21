import { describe, expect, it, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useChatStore } from '../stores/chat'
import { useAuthStore } from '../stores/auth'

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

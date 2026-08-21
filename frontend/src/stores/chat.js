import { defineStore } from 'pinia'
import { useAuthStore } from './auth'
import {
  listSessions,
  listAgentViews,
  listAgentViewSessions,
  getQueueStatus,
  getSessionRating,
  submitRating,
  uploadAttachment,
  getSessionMetadata,
  updateSessionMetadata,
  setArchiveStatus,
  getUserProfile,
} from '../api/chat-api'
import { createStompClient } from '../services/stomp-client'

export const useChatStore = defineStore('chat', {
  state: () => ({
    /** All loaded sessions */
    sessions: [],
    /** Currently selected session ID */
    activeSessionId: null,
    /** Messages for the active session */
    messages: [],
    /** Queue status object: { onlineAgentCount, queueSize, myPosition, estimatedWaitSeconds } */
    queueStatus: null,
    /** Map of sessionId -> unread count */
    unreadCounts: {},
    /** Whether STOMP is connected */
    connected: false,
    /** STOMP client instance (internal) */
    _stomp: null,
    /** Dedup set for client message IDs already rendered */
    _sentClientMsgIds: new Set(),
    /** Loading state for sessions */
    sessionsLoading: false,
    /** Loading state for messages */
    messagesLoading: false,
    /** Error message */
    error: null,
    // ─── Agent workspace state ───
    /** Agent view counts: [{ code, label, count }] */
    agentViewCounts: [],
    /** Active agent view code */
    activeAgentView: 'MY_ACTIVE',
    /** Metadata for the active session */
    activeMetadata: null,
    /** User profile for the active session */
    activeUserProfile: null,
    /** Agent online status */
    agentOnline: false,
  }),

  getters: {
    activeSession(state) {
      return state.sessions.find((s) => s.sessionId === state.activeSessionId) || null
    },
    sortedSessions(state) {
      return [...state.sessions].sort((a, b) => {
        const ta = a.lastMessageTime || a.createTime || ''
        const tb = b.lastMessageTime || b.createTime || ''
        return tb.localeCompare(ta)
      })
    },
  },

  actions: {
    /**
     * Load sessions from REST.
     */
    async loadSessions(params = {}) {
      this.sessionsLoading = true
      this.error = null
      try {
        const result = await listSessions(params)
        // result is Result<PageResult<ChatSessionListItemVO>>
        const page = result?.data || result
        this.sessions = page?.records || []
      } catch (e) {
        this.error = e.message
      } finally {
        this.sessionsLoading = false
      }
    },

    /**
     * Select a session and load its history.
     */
    async selectSession(sessionId) {
      this.activeSessionId = sessionId
      this.messages = []
      this._sentClientMsgIds.clear()
      // Reset unread count
      if (this.unreadCounts[sessionId]) {
        this.unreadCounts[sessionId] = 0
      }
      await this.loadHistory(sessionId)
    },

    /**
     * Load history via STOMP /app/chat.history.
     * Falls back if STOMP not connected.
     */
    async loadHistory(sessionId, beforeMessageId = null, pageSize = 50) {
      if (!this._stomp || !this.connected) {
        this.error = '未连接到服务器'
        return
      }
      this.messagesLoading = true
      this.error = null
      try {
        this._stomp.publish('/app/chat.history', {
          sessionId,
          beforeMessageId,
          pageSize,
        })
      } catch (e) {
        this.error = e.message
      } finally {
        this.messagesLoading = false
      }
    },

    /**
     * Send a chat message via STOMP.
     */
    sendMessage(sessionId, type, content) {
      if (!this._stomp || !this.connected) {
        this.error = '未连接到服务器'
        return
      }
      const clientMsgId = crypto.randomUUID()
      this._sentClientMsgIds.add(clientMsgId)
      // Optimistic local render
      const auth = useAuthStore()
      this.messages.push({
        clientMsgId,
        sessionId,
        senderId: auth.userId,
        senderRole: auth.role,
        type,
        content,
        createTime: new Date().toISOString(),
      })
      this._stomp.publish('/app/chat.send', {
        sessionId,
        type,
        content,
        clientMsgId,
      })
    },

    /**
     * Start a consultation (USER only).
     */
    startConsultation() {
      if (!this._stomp || !this.connected) {
        this.error = '未连接到服务器'
        return
      }
      this._stomp.publish('/app/chat.start', {})
    },

    /**
     * Send ACK for a message.
     */
    sendAck(messageId) {
      if (!this._stomp || !this.connected) return
      this._stomp.publish('/app/chat.ack', { messageId })
    },

    /**
     * Mark messages as read up to a given message ID.
     */
    markRead(sessionId, lastReadMessageId) {
      if (!this._stomp || !this.connected) return
      this._stomp.publish('/app/chat.read', { sessionId, lastReadMessageId })
      if (this.unreadCounts[sessionId]) {
        this.unreadCounts[sessionId] = 0
      }
    },

    /**
     * Pull offline messages.
     */
    pullOfflineMessages() {
      if (!this._stomp || !this.connected) return
      this._stomp.publish('/app/chat.offline.pull', {})
    },

    /**
     * Submit a rating for a closed session.
     */
    async submitRating(sessionId, data) {
      const result = await submitRating(sessionId, data)
      return result?.data || result
    },

    /**
     * Get existing rating for a session.
     */
    async getSessionRating(sessionId) {
      const result = await getSessionRating(sessionId)
      return result?.data || result
    },

    /**
     * Upload an attachment.
     */
    async uploadAttachment(sessionId, file) {
      const result = await uploadAttachment(sessionId, file)
      return result?.data || result
    },

    /**
     * Fetch queue status via REST.
     */
    async loadQueueStatus() {
      try {
        const result = await getQueueStatus()
        this.queueStatus = result?.data || result
      } catch (e) {
        // Non-critical, don't set error
      }
    },

    /**
     * Connect STOMP and subscribe to user queues.
     */
    connectStomp() {
      const auth = useAuthStore()
      if (!auth.token) return

      // Acquire ws ticket, then connect
      fetch('/chat/ws-ticket', {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${auth.token}`,
          'Content-Type': 'application/json',
        },
      })
        .then((res) => {
          if (!res.ok) throw new Error(`Ticket HTTP ${res.status}`)
          return res.json()
        })
        .then((ticketResult) => {
          const ticket = ticketResult?.data?.ticket || ticketResult?.ticket
          if (!ticket) throw new Error('未获取到WebSocket票据')

          const wsUrl =
            (location.protocol === 'https:' ? 'wss://' : 'ws://') +
            location.host +
            `/ws/chat?ticket=${encodeURIComponent(ticket)}`

          const stomp = createStompClient({
            brokerURL: wsUrl,
            connectHeaders: { Authorization: `Bearer ${auth.token}` },
            onConnect: () => {
              this.connected = true
              // Subscribe to chat events
              stomp.subscribe('/user/queue/chat', (frame) => {
                const body = JSON.parse(frame.body)
                this._handleChatEvent(body)
              })
              // Subscribe to message events (read, edit, recall, ack)
              stomp.subscribe('/user/queue/messages', (frame) => {
                const body = JSON.parse(frame.body)
                this._handleMessageEvent(body)
              })
              // Pull offline messages on connect
              this.pullOfflineMessages()
            },
            onMessage: (frame) => {
              // fallback handler
            },
          })

          this._stomp = stomp
          stomp.connect()

          // Store deactivate for cleanup
          this._deactivateStomp = () => {
            stomp.disconnect()
            this.connected = false
            this._stomp = null
          }
        })
        .catch((e) => {
          this.error = 'WebSocket连接失败: ' + e.message
        })
    },

    /**
     * Disconnect STOMP.
     */
    disconnectStomp() {
      if (this._deactivateStomp) {
        this._deactivateStomp()
        this._deactivateStomp = null
      }
    },

    // ─── Agent workspace actions ──────────────────────────────

    /**
     * Load agent view counts from REST.
     */
    async loadAgentViewCounts() {
      try {
        const result = await listAgentViews()
        const data = result?.data || result
        this.agentViewCounts = Array.isArray(data) ? data : []
      } catch (e) {
        this.error = e.message
      }
    },

    /**
     * Load sessions for a specific agent view.
     */
    async loadAgentViewSessions(viewCode, params = {}) {
      this.sessionsLoading = true
      this.error = null
      try {
        const result = await listAgentViewSessions(viewCode, params)
        const page = result?.data || result
        this.sessions = page?.records || []
      } catch (e) {
        this.error = e.message
      } finally {
        this.sessionsLoading = false
      }
    },

    /**
     * Switch active agent view and refresh sessions.
     */
    async switchAgentView(viewCode) {
      this.activeAgentView = viewCode
      await this.loadAgentViewSessions(viewCode)
    },

    /**
     * Load session metadata (title, priority, category, tags).
     */
    async loadSessionMetadata(sessionId) {
      try {
        const result = await getSessionMetadata(sessionId)
        this.activeMetadata = result?.data || result
      } catch (e) {
        this.activeMetadata = null
        this.error = e.message
      }
    },

    /**
     * Update session metadata.
     */
    async updateSessionMetadata(sessionId, data) {
      try {
        const result = await updateSessionMetadata(sessionId, data)
        this.activeMetadata = result?.data || result
        // Refresh the session list entry if present
        const idx = this.sessions.findIndex((s) => s.sessionId === sessionId)
        if (idx !== -1) {
          this.sessions[idx] = { ...this.sessions[idx], ...data }
        }
        return this.activeMetadata
      } catch (e) {
        this.error = e.message
        throw e
      }
    },

    /**
     * Update archive status for a session.
     */
    async updateArchiveStatus(sessionId, data) {
      try {
        await setArchiveStatus(sessionId, data)
        // Refresh sessions for current view
        await this.loadAgentViewSessions(this.activeAgentView)
      } catch (e) {
        this.error = e.message
        throw e
      }
    },

    /**
     * Load user profile for a session (agent sidebar).
     */
    async loadUserProfile(sessionId) {
      try {
        const result = await getUserProfile(sessionId)
        this.activeUserProfile = result?.data || result
      } catch (e) {
        this.activeUserProfile = null
        this.error = e.message
      }
    },

    /**
     * Select session for agent workspace: loads messages, metadata, and user profile.
     */
    async selectAgentSession(sessionId) {
      await this.selectSession(sessionId)
      await Promise.all([
        this.loadSessionMetadata(sessionId),
        this.loadUserProfile(sessionId),
      ])
    },

    /**
     * Internal: handle events from /user/queue/chat
     */
    _handleChatEvent(body) {
      const event = body.event

      if (event === 'CHAT_HISTORY') {
        // History response
        const incoming = body.messages || []
        // Prepend older messages or replace
        if (body.sessionId === this.activeSessionId) {
          this.messages = [...incoming, ...this.messages]
          // Deduplicate by clientMsgId
          this._deduplicateMessages()
        }
        return
      }

      if (event === 'NEW_MESSAGE' || !event) {
        // A new chat message
        const msg = body
        if (msg.sessionId === this.activeSessionId) {
          // Deduplicate by clientMsgId
          if (msg.clientMsgId && this._sentClientMsgIds.has(msg.clientMsgId)) {
            // Replace optimistic message with server version
            const idx = this.messages.findIndex(
              (m) => m.clientMsgId === msg.clientMsgId
            )
            if (idx !== -1) {
              this.messages[idx] = { ...this.messages[idx], ...msg }
            } else {
              this.messages.push(msg)
            }
          } else {
            this.messages.push(msg)
          }
          // Send ACK
          if (msg.id) this.sendAck(msg.id)
          // Mark read if this session is active
          if (msg.id) this.markRead(msg.sessionId, msg.id)
        } else {
          // Increment unread count for other session
          const sid = msg.sessionId
          this.unreadCounts[sid] = (this.unreadCounts[sid] || 0) + 1
        }
        // Update session last message
        this._updateSessionLastMessage(msg)
        return
      }

      // SESSION_ASSIGNED, SESSION_ENDED, etc.
      if (event === 'SESSION_ASSIGNED' || event === 'SESSION_ENDED') {
        // Refresh sessions
        this.loadSessions()
      }
    },

    /**
     * Internal: handle events from /user/queue/messages
     */
    _handleMessageEvent(body) {
      const event = body.event

      if (event === 'MESSAGES_READ') {
        // Update read status in current messages
        // Could update UI indicators here
        return
      }

      if (event === 'MESSAGE_EDITED') {
        const idx = this.messages.findIndex((m) => m.id === body.messageId)
        if (idx !== -1) {
          this.messages[idx] = {
            ...this.messages[idx],
            content: body.content,
            edited: true,
            editedAt: body.editedAt,
          }
        }
        return
      }

      if (event === 'MESSAGE_RECALLED') {
        const idx = this.messages.findIndex((m) => m.id === body.messageId)
        if (idx !== -1) {
          this.messages[idx] = {
            ...this.messages[idx],
            content: null,
            recalled: true,
            recalledAt: body.recalledAt,
          }
        }
        return
      }

      if (event === 'ACK_STORED') {
        // Update ack status
        const idx = this.messages.findIndex((m) => m.id === body.messageId)
        if (idx !== -1) {
          this.messages[idx] = { ...this.messages[idx], ackStatus: 'STORED' }
        }
      }
    },

    /**
     * Deduplicate messages array by clientMsgId.
     */
    _deduplicateMessages() {
      const seen = new Set()
      this.messages = this.messages.filter((m) => {
        const key = m.clientMsgId || m.id
        if (seen.has(key)) return false
        seen.add(key)
        return true
      })
    },

    /**
     * Update the session list entry's last message fields.
     */
    _updateSessionLastMessage(msg) {
      const session = this.sessions.find((s) => s.sessionId === msg.sessionId)
      if (session) {
        session.lastMessageContent = msg.content
        session.lastMessageSenderId = msg.senderId
        session.lastMessageTime = msg.createTime
      }
    },
  },
})

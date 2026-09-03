import { defineStore } from 'pinia'
import { useAuthStore } from './auth'
import { connectionActions } from './chat-connection'
import { sessionActions } from './chat-session-actions'
import { agentActions } from './chat-agent-actions'
import {
  listSessions,
  listAgentViews,
  listAgentViewSessions,
  getQueueStatus,
  cancelQueue,
  getSessionRating,
  submitRating,
  uploadAttachment,
  getSessionMetadata,
  updateSessionMetadata,
  setArchiveStatus,
  saveArchiveRemark as saveArchiveRemarkRequest,
  getUserProfile,
  findAgentDashboard,
  findAgentRatingSummary,
  findTransferLogs,
  getSupportTicket,
  getSupportTicketHistory,
  getSupportTicketHistoryPage,
  createSupportTicket,
  updateSupportTicket,
  submitSupportTicketUserFeedback,
} from '../api/chat-api'

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
    queueWaiting: false,
    queueCancelling: false,
    queueNotice: null,
    consultationStarting: false,
    /** Map of sessionId -> unread count */
    unreadCounts: {},
    /** Currently selected message being quoted by the composer. */
    replyingTo: null,
    /** Map of session ID to the peer currently typing. */
    typingBySession: {},
    /** Whether STOMP is connected */
    connected: false,
    connectionState: 'disconnected',
    connectionError: null,
    connectionLogs: [],
    reconnectAttempts: 0,
    lastActivityAt: null,
    manualDisconnect: false,
    /** STOMP client instance (internal) */
    _stomp: null,
    _connectAttempt: 0,
    _ticketAbortController: null,
    /** Business heartbeat timer (keeps Redis presence alive) */
    _heartbeatTimer: null,
    /** Dedup set for client message IDs already rendered */
    _sentClientMsgIds: new Set(),
    /** Map of client message IDs to optimistic-send timeout handles. */
    _sendTimeouts: {},
    /** Loading state for sessions */
    sessionsLoading: false,
    sessionsPageNo: 1,
    sessionsPageSize: 20,
    sessionsTotal: 0,
    sessionsTotalPages: 0,
    sessionsHasMore: false,
    sessionsLoadingMore: false,
    _sessionsRequestSequence: 0,
    sessionsError: null,
    _queueStatusRequestSequence: 0,
    _queueLifecycleVersion: 0,
    _consultationTimeout: null,
    /** Loading state for messages */
    messagesLoading: false,
    /** Cursor state for loading earlier messages */
    historyCursor: null,
    historyHasMore: false,
    historyLoadingMore: false,
    _historyRequestSequence: 0,
    _historyPendingSessionId: null,
    _historyPendingRequestId: null,
    _historyTimeout: null,
    /** Error message */
    error: null,
    // ─── Agent workspace state ───
    /** Agent view counts: [{ code, label, count }] */
    agentViewCounts: [],
    /** Active agent view code */
    activeAgentView: 'MY_ACTIVE',
    ticketStatusFilter: '',
    ticketKeyword: '',
    /** Message ID to locate after a result is opened from agent message search. */
    focusedMessageId: null,
    /** Session kept visible while an agent locates a search result outside the current view. */
    pinnedAgentSearchSession: null,
    /** Metadata for the active session */
    activeMetadata: null,
    activeSupportTicket: null,
    activeSupportTicketHistory: [],
    supportTicketHistoryPageNo: 0,
    supportTicketHistoryHasMore: false,
    supportTicketLoading: false,
    supportTicketError: null,
    _supportTicketRequestSequence: 0,
    _supportTicketHistoryRequestSequence: 0,
    supportTicketFormDirty: false,
    supportTicketStale: false,
    /** User profile for the active session */
    activeUserProfile: null,
    /** Agent online status */
    agentOnline: false,
    agentDashboard: null,
    agentRatingSummary: null,
    transferLogs: [],
    _transferLogRequestSequence: 0,
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
    consultationState(state) {
      if (state.sessions.some((session) => session.status === 'ACTIVE')) return 'ACTIVE'
      if (state.queueWaiting || state.queueStatus?.myPosition != null) return 'QUEUED'
      if (state.queueCancelling) return 'CANCELLING'
      if (state.consultationStarting) return 'STARTING'
      return 'IDLE'
    },
    canStartConsultation() {
      return this.consultationState === 'IDLE'
    },
  },

    actions: {
    ...connectionActions,
    ...sessionActions,
    ...agentActions,
    logConnection(message) {
      this.connectionLogs = [...this.connectionLogs.slice(-19), { message, time: new Date().toISOString() }]
      this.lastActivityAt = new Date().toISOString()
    },

    handleConnectionError(message) {
      // A STOMP error can be reported while the WebSocket is still usable.
      // Keep the transport marked connected until the close callback confirms it.
      if (this.connected) {
        this.connectionError = message || 'WebSocket 连接失败'
        return
      }
      this.connectionState = 'error'
      this.connectionError = message || 'WebSocket 连接失败'
    },

    reconnectStomp() {
      if (this.reconnectAttempts >= 5) {
        this.connectionError = '自动重连次数已达上限，请手动重连'
        return
      }
      // This reconnect path schedules its own timer; suppress the close callback
      // from scheduling a second concurrent reconnect attempt.
      this.disconnectStomp({ manual: true })
      this.reconnectAttempts += 1
      const delay = Math.min(1000 * 2 ** (this.reconnectAttempts - 1), 10000)
      this.connectionState = 'reconnecting'
      this.logConnection(`正在进行第 ${this.reconnectAttempts} 次重连`)
      this._reconnectTimer = setTimeout(() => this.connectStomp(), delay)
    },

    /**
     * Load sessions from REST.
     */
    /** Clear data that belongs to the authenticated account after a token expires. */
    clearAuthenticatedChat() {
      this.disconnectStomp({ manual: true })
      this._clearHistoryTimeout()
      this._historyRequestSequence += 1
      this._historyPendingSessionId = null
      this._historyPendingRequestId = null
      this.sessions = []
      this.activeSessionId = null
      this.messages = []
      this.queueStatus = null
      this.queueWaiting = false
      this.queueCancelling = false
      this.queueNotice = null
      this.consultationStarting = false
      this.unreadCounts = {}
      this._sentClientMsgIds.clear()
      this.sessionsLoading = false
      this.sessionsLoadingMore = false
      this.messagesLoading = false
      this.historyCursor = null
      this.historyHasMore = false
      this.historyLoadingMore = false
      this.agentViewCounts = []
      this.activeMetadata = null
      this.activeSupportTicket = null
      this.activeSupportTicketHistory = []
      this.supportTicketHistoryPageNo = 0
      this.supportTicketHistoryHasMore = false
      this.supportTicketLoading = false
      this.supportTicketError = null
      this.supportTicketFormDirty = false
      this.supportTicketStale = false
      this.activeUserProfile = null
      this.agentOnline = false
      this.agentDashboard = null
      this.agentRatingSummary = null
      this.transferLogs = []
      this._transferLogRequestSequence += 1
      this.error = null
    },

    // ─── Agent workspace actions ──────────────────────────────

    _handleChatEvent(body) {
      const event = body.event

      if (event === 'OFFLINE_MESSAGES_REPLAYED') {
        const auth = useAuthStore()
        if (auth.role === 'AGENT') this.refreshAgentViews()
        else this.loadSessions()
        return
      }

      if (event === 'TYPING') {
        if (body.sessionId && body.senderId) {
          const previous = this.typingBySession[body.sessionId]
          if (previous?.timeout) clearTimeout(previous.timeout)
          if (body.typing) {
            this.typingBySession[body.sessionId] = {
              senderId: body.senderId,
              timeout: setTimeout(() => { delete this.typingBySession[body.sessionId] }, 5000),
            }
          } else {
            delete this.typingBySession[body.sessionId]
          }
        }
        return
      }

      if (event === 'TICKET_CREATED' || event === 'TICKET_UPDATED') {
        if (body.sessionId === this.activeSessionId) {
          this.loadSupportTicketHistory(body.sessionId)
          if (this.supportTicketFormDirty) {
            this.supportTicketStale = true
          } else {
            this.loadSupportTicket(body.sessionId)
          }
        }
        return
      }

      if (event === 'CHAT_HISTORY') {
        // History response
        if (this._historyPendingRequestId && body.requestId !== this._historyPendingRequestId) return
        const incoming = body.messages || []
        // Prepend older messages or replace
        if (body.sessionId === this.activeSessionId) {
          this.messages = [...incoming, ...this.messages]
          // Deduplicate by clientMsgId
          this._deduplicateMessages()
          this.markActiveSessionRead()
          this.historyCursor = body.nextCursor || null
          this.historyHasMore = Boolean(body.hasMore && body.nextCursor)
          this._finishHistoryRequest(this._historyRequestSequence, body.sessionId, body.requestId)
        }
        return
      }

      if (event === 'NEW_MESSAGE' || !event) {
        // A new chat message
        const msg = body
        const auth = useAuthStore()
        if (msg.id && msg.senderId !== auth.userId) {
          // Transport delivery confirmation is independent from whether the user has read it.
          this.sendAck(msg.id)
        }
        if (msg.sessionId === this.activeSessionId) {
          // Deduplicate by clientMsgId
          if (msg.clientMsgId && this._sentClientMsgIds.has(msg.clientMsgId)) {
            // Replace optimistic message with server version
            const idx = this.messages.findIndex(
              (m) => m.clientMsgId === msg.clientMsgId
            )
            if (idx !== -1) {
              this.messages[idx] = { ...this.messages[idx], ...msg, sendState: 'SENT', failureReason: null }
              this._markMessageSent(msg.clientMsgId)
            } else {
              this.messages.push(msg)
            }
          } else {
            this.messages.push(msg)
          }
          // Mark read only while the conversation is visible.
          if (msg.id && msg.senderId !== auth.userId && document.visibilityState === 'visible') {
            this.markRead(msg.sessionId, msg.id)
          } else if (msg.id && msg.senderId !== auth.userId) {
            const sid = msg.sessionId
            this.unreadCounts[sid] = (this.unreadCounts[sid] || 0) + 1
          }
        } else if (msg.senderId !== auth.userId) {
          // Increment unread count for other session
          const sid = msg.sessionId
          this.unreadCounts[sid] = (this.unreadCounts[sid] || 0) + 1
        }
        // Update session last message
        this._updateSessionLastMessage(msg)
        if (useAuthStore().role === 'AGENT') this.refreshAgentViews()
        return
      }

      if (event === 'QUEUE_CANCELLED') {
        this._applyQueueCancellation()
        return
      }

      if (event === 'WAITING_FOR_AGENT' || event === 'VIP_CALLBACK_REQUIRED' || event === 'ASSIGNMENT_PROCESSING') {
        if (this.queueCancelling || (!this.consultationStarting && !this.queueWaiting)) return
        if (event !== 'ASSIGNMENT_PROCESSING') {
          this._finishConsultationStart()
          this.queueWaiting = true
        }
        this.queueNotice = event === 'VIP_CALLBACK_REQUIRED'
          ? '暂时没有可接待的 VIP 客服，已登记回呼。'
          : event === 'ASSIGNMENT_PROCESSING'
            ? '正在分配客服…'
            : null
        this.loadQueueStatus()
        return
      }

      if (event === 'ERROR') {
        if (body.clientMsgId) {
          this._markMessageFailed(body.clientMsgId, body.message || body.error || '服务端拒绝了消息')
          return
        }
        this._finishConsultationStart()
        this.queueWaiting = false
        this.queueNotice = null
        this.error = body.message || body.error || '咨询请求失败'
        return
      }

      if (event === 'SESSION_CREATED' || event === 'SESSION_RECONNECTED' || event === 'SESSION_TRANSFERRED' || event === 'SESSION_ENDED' || event === 'SESSION_CLOSED') {
        const auth = useAuthStore()
        if (auth.role === 'AGENT') {
          this.refreshAgentViews()
        } else {
          const sessionId = body.sessionId || body.session?.sessionId
          this._finishConsultationStart()
          this.queueWaiting = false
          this.queueCancelling = false
          this.queueNotice = null
          const selectAssignedSession = () => {
            if (sessionId && this.sessions.some((session) => session.sessionId === sessionId)) {
              this.selectSession(sessionId)
              return true
            }
            return false
          }
          const selectedImmediately = selectAssignedSession()
          const refreshSessions = this.loadSessions()
          if (!selectedImmediately) refreshSessions.then(selectAssignedSession)
          this.loadQueueStatus()
        }
      }
    },

    _handleStompErrorEvent(body) {
      const reason = body?.message || body?.error || '服务端拒绝了消息'
      if (this.consultationStarting) {
        this._finishConsultationStart()
        this.queueWaiting = false
        this.queueNotice = null
      }
      this.error = reason
      this._markLatestPendingMessageFailed(reason)
    },

    /**
     * Internal: handle events from /user/queue/messages
     */
    _handleMessageEvent(body) {
      const event = body.event

      if (event === 'MESSAGES_READ') {
        const auth = useAuthStore()
        const sessionId = body.sessionId
        const anchorIndex = this.messages.findIndex(
          (message) => message.id === body.lastReadMessageId
        )

        if (sessionId === this.activeSessionId && anchorIndex >= 0) {
          this.messages = this.messages.map((message, index) => {
            if (
              index > anchorIndex ||
              message.sessionId !== sessionId ||
              !message.id
            ) return message

            if (body.readerId === auth.userId && message.senderId !== auth.userId) {
              return { ...message, read: true }
            }
            if (body.readerId !== auth.userId && message.senderId === auth.userId) {
              return { ...message, readByPeer: true }
            }
            return message
          })
        }

        if (body.readerId === auth.userId) {
          this.unreadCounts[sessionId] = 0
        }
        if (auth.role === 'AGENT') this.refreshAgentViews()
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

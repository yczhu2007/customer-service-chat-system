import { defineStore } from 'pinia'
import { useAuthStore } from './auth'
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
  createSupportTicket,
  updateSupportTicket,
} from '../api/chat-api'
import { createStompClient } from '../services/stomp-client'
import { handleUnauthorized } from '../services/http-client'

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
    /** Message ID to locate after a result is opened from agent message search. */
    focusedMessageId: null,
    /** Session kept visible while an agent locates a search result outside the current view. */
    pinnedAgentSearchSession: null,
    /** Metadata for the active session */
    activeMetadata: null,
    activeSupportTicket: null,
    supportTicketLoading: false,
    supportTicketError: null,
    _supportTicketRequestSequence: 0,
    /** User profile for the active session */
    activeUserProfile: null,
    /** Agent online status */
    agentOnline: false,
    agentDashboard: null,
    agentRatingSummary: null,
    transferLogs: [],
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
    hasClosedConsultation(state) {
      return state.sessions.some((session) => session.status === 'CLOSED')
    },
  },

    actions: {
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
    async loadSessions(params = {}) {
      const pageNo = Number(params.pageNo || 1)
      const pageSize = Number(params.pageSize || this.sessionsPageSize)
      const requestSequence = ++this._sessionsRequestSequence
      this.sessionsLoading = true
      this.sessionsError = null
      try {
        const result = await listSessions({ ...params, pageNo, pageSize })
        if (requestSequence !== this._sessionsRequestSequence) return
        const page = result?.data || result
        const records = page?.records || []
        const pageUnreadCounts = Object.fromEntries(records
          .filter((session) => session.sessionId)
          .map((session) => [session.sessionId, Math.max(0, Number(session.unreadCount) || 0)]))
        if (pageNo > 1) {
          const bySessionId = new Map(this.sessions.map((session) => [session.sessionId, session]))
          records.forEach((session) => bySessionId.set(session.sessionId, session))
          this.sessions = [...bySessionId.values()]
          this.unreadCounts = { ...this.unreadCounts, ...pageUnreadCounts }
        } else {
          this.sessions = records
          this.unreadCounts = pageUnreadCounts
        }
        this.sessionsPageNo = page?.current || pageNo
        this.sessionsPageSize = page?.size || pageSize
        this.sessionsTotal = page?.total || 0
        this.sessionsTotalPages = page?.pages || (this.sessionsPageSize ? Math.ceil(this.sessionsTotal / this.sessionsPageSize) : 0)
        this.sessionsHasMore = this.sessionsPageNo < this.sessionsTotalPages
        return true
      } catch (e) {
        if (requestSequence === this._sessionsRequestSequence) {
          this.sessionsError = e.message
          this.error = e.message
        }
        return false
      } finally {
        if (requestSequence === this._sessionsRequestSequence) this.sessionsLoading = false
      }
    },

    async loadNextSessionsPage(params = {}) {
      if (this.sessionsLoading || !this.sessionsHasMore) return
      return this.loadSessions({ ...params, pageNo: this.sessionsPageNo + 1, pageSize: this.sessionsPageSize })
    },

    /**
     * Select a session and load its history.
     */
    async selectSession(sessionId) {
      this._clearHistoryTimeout()
      this._historyRequestSequence += 1
      this._historyPendingSessionId = null
      this._historyPendingRequestId = null
      this.messagesLoading = false
      this.activeSessionId = sessionId
      this.messages = []
      this.activeSupportTicket = null
      this.supportTicketError = null
      const ticketRequestSequence = ++this._supportTicketRequestSequence
      this.loadSupportTicket(sessionId, ticketRequestSequence)
      this._sentClientMsgIds.clear()
      this.historyCursor = null
      this.historyHasMore = false
      this.historyLoadingMore = false
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
      if (this.messagesLoading) return
      this.messagesLoading = true
      this.error = null
      const historyRequest = this._beginHistoryRequest(sessionId, false)
      try {
        this._stomp.publish('/app/chat.history', {
          sessionId,
          beforeMessageId,
          pageSize,
          requestId: historyRequest.requestId,
        })
      } catch (e) {
        this.error = e.message
        this._finishHistoryRequest(historyRequest.requestSequence, sessionId, historyRequest.requestId)
      }
    },

    /** Load the next page of older messages for the active session. */
    async loadMoreHistory(sessionId, pageSize = 50) {
      if (sessionId !== this.activeSessionId || !this.historyHasMore || this.messagesLoading || this.historyLoadingMore) return
      if (!this.historyCursor) return
      this.historyLoadingMore = true
      this.error = null
      const historyRequest = this._beginHistoryRequest(sessionId, true)
      try {
        this._stomp.publish('/app/chat.history', {
          sessionId,
          beforeMessageId: this.historyCursor,
          pageSize,
          requestId: historyRequest.requestId,
        })
      } catch (e) {
        this.error = e.message
        this._finishHistoryRequest(historyRequest.requestSequence, sessionId, historyRequest.requestId)
      }
    },

    /**
     * Send a chat message with a local SENDING → SENT / FAILED lifecycle.
     */
    setReplyTarget(message) {
      if (message?.id && !message.recalled) this.replyingTo = message
    },

    clearReplyTarget() {
      this.replyingTo = null
    },

    sendMessage(sessionId, type, content, replyToMessageId = this.replyingTo?.id || null) {
      const clientMsgId = crypto.randomUUID()
      const auth = useAuthStore()
      const localMessage = {
        clientMsgId,
        sessionId,
        senderId: auth.userId,
        senderRole: auth.role,
        type,
        content,
        replyToMessageId,
        createTime: new Date().toISOString(),
        sendState: 'SENDING',
        failureReason: null,
      }
      this.messages.push(localMessage)
      this._publishPendingMessage(localMessage)
      return clientMsgId
    },

    retryMessage(clientMsgId) {
      const message = this.messages.find((item) => item.clientMsgId === clientMsgId)
      if (!message || message.sendState !== 'FAILED') return
      const nextClientMsgId = crypto.randomUUID()
      this._sentClientMsgIds.delete(message.clientMsgId)
      this.messages = this.messages.map((item) => item.clientMsgId === clientMsgId
        ? { ...item, clientMsgId: nextClientMsgId, id: null, sendState: 'SENDING', failureReason: null, createTime: new Date().toISOString() }
        : item)
      this._publishPendingMessage(this.messages.find((item) => item.clientMsgId === nextClientMsgId))
    },

    _publishPendingMessage(message) {
      if (!message) return
      if (!this._stomp || !this.connected) {
        this._markMessageFailed(message.clientMsgId, 'WebSocket 未连接，请重连后重试')
        return
      }
      this._sentClientMsgIds.add(message.clientMsgId)
      try {
        this._stomp.publish('/app/chat.send', {
          sessionId: message.sessionId,
          type: message.type,
          content: message.content,
          replyToMessageId: message.replyToMessageId,
          clientMsgId: message.clientMsgId,
        })
        this._sendTimeouts[message.clientMsgId] = setTimeout(() => {
          this._markMessageFailed(message.clientMsgId, '消息发送超时，请重试')
        }, 15000)
      } catch (exception) {
        this._markMessageFailed(message.clientMsgId, exception.message || '消息发送失败，请重试')
      }
    },

    _markMessageFailed(clientMsgId, reason) {
      if (this._sendTimeouts[clientMsgId]) {
        clearTimeout(this._sendTimeouts[clientMsgId])
        delete this._sendTimeouts[clientMsgId]
      }
      const index = this.messages.findIndex((item) => item.clientMsgId === clientMsgId)
      if (index !== -1 && this.messages[index].sendState === 'SENDING') {
        this.messages[index] = { ...this.messages[index], sendState: 'FAILED', failureReason: reason }
      }
    },

    _markLatestPendingMessageFailed(reason) {
      const pending = [...this.messages].reverse().find((item) => item.sendState === 'SENDING')
      if (pending) this._markMessageFailed(pending.clientMsgId, reason)
    },

    _markMessageSent(clientMsgId) {
      if (this._sendTimeouts[clientMsgId]) {
        clearTimeout(this._sendTimeouts[clientMsgId])
        delete this._sendTimeouts[clientMsgId]
      }
      const index = this.messages.findIndex((item) => item.clientMsgId === clientMsgId)
      if (index !== -1) {
        this.messages[index] = { ...this.messages[index], sendState: 'SENT', failureReason: null }
      }
    },

    sendTyping(sessionId, typing) {
      if (!sessionId || !this._stomp || !this.connected) return
      try {
        this._stomp.publish('/app/chat.typing', { sessionId, typing })
      } catch {
        // Typing is ephemeral and must never interrupt message entry.
      }
    },

    recallMessage(messageId) {
      const auth = useAuthStore()
      const message = this.messages.find((item) => item.id === messageId)
      if (!message || message.senderId !== auth.userId || message.recalled) return
      if (!this._stomp || !this.connected) { this.error = '未连接到服务器'; return }
      this._stomp.publish('/app/chat.message.recall', { messageId })
    },

    /**
     * Start a consultation (USER only).
     */
    startConsultation() {
      if (!this._stomp || !this.connected) {
        this.error = '未连接到服务器'
        return
      }
      if (!this.canStartConsultation) {
        this.error = this.consultationState === 'QUEUED' ? '当前已在排队中' : '当前已有进行中的咨询'
        return
      }
      this.consultationStarting = true
      this.queueNotice = '正在为您分配客服…'
      try {
        this._stomp.publish('/app/chat.start', {})
      } catch (e) {
        this.consultationStarting = false
        if (this._consultationTimeout) { clearTimeout(this._consultationTimeout); this._consultationTimeout = null }
        this.queueNotice = null
        this.error = e.message || '咨询请求发送失败'
        return
      }
      this._consultationTimeout = setTimeout(() => {
        if (!this.consultationStarting) return
        this.consultationStarting = false
        if (this._consultationTimeout) { clearTimeout(this._consultationTimeout); this._consultationTimeout = null }
        this.queueNotice = null
        this.error = '咨询请求超时，请重试'
      }, 15000)
      setTimeout(() => this.loadSessions(), 500)
    },

    transferSession(sessionId, targetAgentId) {
      if (!this._stomp || !this.connected) {
        this.error = '未连接到服务器'
        return false
      }
      this._stomp.publish('/app/chat.transfer', { sessionId, targetAgentId })
      return true
    },

    endSession(sessionId) {
      if (!this._stomp || !this.connected) {
        this.error = '未连接到服务器'
        return false
      }
      try {
        this._stomp.publish('/app/chat.end', { sessionId })
        return true
      } catch (e) {
        this.error = e.message || '结束会话失败'
        return false
      }
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
      try {
        const result = await uploadAttachment(sessionId, file)
        return result?.data || result
      } catch (e) {
        this.error = e.message || '附件上传失败'
        throw e
      }
    },

    _beginHistoryRequest(sessionId, loadingMore) {
      this._clearHistoryTimeout()
      const requestSequence = ++this._historyRequestSequence
      const requestId = `${sessionId}:${requestSequence}`
      this._historyPendingSessionId = sessionId
      this._historyPendingRequestId = requestId
      this._historyTimeout = setTimeout(() => {
        if (requestSequence !== this._historyRequestSequence) return
        this.error = '加载历史消息超时，请重试'
        this.messagesLoading = false
        this.historyLoadingMore = false
        this._historyPendingRequestId = null
      }, 15000)
      return { requestSequence, requestId }
    },

    _finishHistoryRequest(requestSequence = this._historyRequestSequence, sessionId = this._historyPendingSessionId, requestId = this._historyPendingRequestId) {
      if (requestSequence !== this._historyRequestSequence) return
      if (sessionId !== this._historyPendingSessionId) return
      if (requestId !== this._historyPendingRequestId) return
      this._clearHistoryTimeout()
      this.messagesLoading = false
      this.historyLoadingMore = false
      this._historyPendingSessionId = null
      this._historyPendingRequestId = null
    },

    _clearHistoryTimeout() {
      if (this._historyTimeout) {
        clearTimeout(this._historyTimeout)
        this._historyTimeout = null
      }
    },

    /**
     * Fetch queue status via REST.
     */
    async loadQueueStatus() {
      const requestSequence = ++this._queueStatusRequestSequence
      const lifecycleVersion = this._queueLifecycleVersion
      try {
        const result = await getQueueStatus()
        if (requestSequence !== this._queueStatusRequestSequence || lifecycleVersion !== this._queueLifecycleVersion) return
        this.queueStatus = result?.data || result
        this.queueWaiting = this.queueStatus?.myPosition != null
        if (this.queueWaiting) this._finishConsultationStart()
      } catch (e) {
        // Non-critical, don't set error
      }
    },

    async cancelQueue() {
      if (this.queueCancelling) return false
      this.queueCancelling = true
      this._queueLifecycleVersion += 1
      this.queueNotice = null
      try {
        await cancelQueue()
        this._applyQueueCancellation()
        return true
      } catch (exception) {
        if (!this.queueWaiting && this.queueStatus?.myPosition == null) return true
        this.queueCancelling = false
        this.error = exception.message || '取消排队失败'
        return false
      }
    },

    _applyQueueCancellation() {
      const wasWaiting = this.queueWaiting || this.queueStatus?.myPosition != null
      this._queueLifecycleVersion += 1
      this._finishConsultationStart()
      this.queueWaiting = false
      this.queueCancelling = false
      this.queueStatus = {
        ...(this.queueStatus || {}),
        queueSize: wasWaiting
          ? Math.max(0, Number(this.queueStatus?.queueSize || 0) - 1)
          : Number(this.queueStatus?.queueSize || 0),
        myPosition: null,
        estimatedWaitSeconds: null,
      }
      this.queueNotice = null
    },

    _finishConsultationStart() {
      this.consultationStarting = false
      if (this._consultationTimeout) {
        clearTimeout(this._consultationTimeout)
        this._consultationTimeout = null
      }
    },

    async loadAgentDashboard() {
      try {
        const [dashboardResult, ratingResult] = await Promise.all([
          findAgentDashboard(),
          findAgentRatingSummary(),
        ])
        this.agentDashboard = dashboardResult?.data || dashboardResult
        this.agentRatingSummary = ratingResult?.data || ratingResult
      } catch (e) {
        this.error = e.message
      }
    },

    /** Mark the newest persisted message from the other participant as read. */
    markActiveSessionRead() {
      if (!this.activeSessionId) return
      const auth = useAuthStore()
      const latestReceived = [...this.messages]
        .reverse()
        .find((message) => message?.id && message.senderId !== auth.userId)
      if (latestReceived) this.markRead(this.activeSessionId, latestReceived.id)
    },

    async loadTransferLogs(sessionId) {
      if (!sessionId) { this.transferLogs = []; return }
      try {
        const result = await findTransferLogs(sessionId)
        this.transferLogs = result?.data || result || []
      } catch (e) {
        this.transferLogs = []
        this.error = e.message
      }
    },

    async loadSupportTicket(sessionId, requestSequence = ++this._supportTicketRequestSequence) {
      if (!sessionId) {
        this.activeSupportTicket = null
        return null
      }
      this.supportTicketLoading = true
      try {
        const result = await getSupportTicket(sessionId)
        const ticket = result && Object.prototype.hasOwnProperty.call(result, 'data') ? result.data : result
        if (this.activeSessionId === sessionId && requestSequence === this._supportTicketRequestSequence) {
          this.activeSupportTicket = ticket
          this.supportTicketError = null
        }
        return ticket
      } catch (e) {
        if (this.activeSessionId === sessionId && requestSequence === this._supportTicketRequestSequence) {
          this.supportTicketError = e.message
          this.error = e.message
        }
        return null
      } finally {
        if (this.activeSessionId === sessionId && requestSequence === this._supportTicketRequestSequence) {
          this.supportTicketLoading = false
        }
      }
    },

    async createSupportTicket(sessionId, data) {
      try {
        const result = await createSupportTicket(sessionId, data)
        const ticket = result && Object.prototype.hasOwnProperty.call(result, 'data') ? result.data : result
        if (this.activeSessionId === sessionId) this.activeSupportTicket = ticket
        return ticket
      } catch (e) {
        this.error = e.message
        throw e
      }
    },

    async updateSupportTicket(ticketNo, data) {
      try {
        const result = await updateSupportTicket(ticketNo, data)
        const ticket = result && Object.prototype.hasOwnProperty.call(result, 'data') ? result.data : result
        if (this.activeSessionId === ticket?.sessionId) this.activeSupportTicket = ticket
        return ticket
      } catch (e) {
        this.error = e.message
        throw e
      }
    },

    /**
     * Connect STOMP and subscribe to user queues.
     */
    connectStomp() {
      const auth = useAuthStore()
      if (!auth.token) return
      if (this.connected || this.connectionState === 'connecting') return
      this.connectionState = 'connecting'
      this.connectionError = null
      this.manualDisconnect = false
      this.logConnection('正在连接 WebSocket')
      const attempt = ++this._connectAttempt
      const ticketAbortController = new AbortController()
      this._ticketAbortController = ticketAbortController

      // Acquire ws ticket, then connect
      fetch('/chat/ws-ticket', {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${auth.token}`,
          'Content-Type': 'application/json',
        },
        signal: ticketAbortController.signal,
      })
        .then(async (res) => {
          if (res.status === 401) await handleUnauthorized()
          if (!res.ok) throw new Error(`Ticket HTTP ${res.status}`)
          return res.json()
        })
        .then((ticketResult) => {
          if (attempt !== this._connectAttempt || this.manualDisconnect) return
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
              if (attempt !== this._connectAttempt || this.manualDisconnect) {
                stomp.disconnect()
                return
              }
              this.connected = true
              this.connectionState = 'connected'
              this.reconnectAttempts = 0
              this.logConnection('WebSocket 已连接')
              this.startHeartbeat()
              // Subscribe to chat events
              stomp.subscribe('/user/queue/chat', (frame) => {
                const body = JSON.parse(frame.body)
                this._handleChatEvent(body)
              })
              // Subscribe to message events (read, recall, ack)
              stomp.subscribe('/user/queue/messages', (frame) => {
                const body = JSON.parse(frame.body)
                this._handleMessageEvent(body)
              })
              stomp.subscribe('/user/queue/errors', (frame) => {
                try {
                  const body = JSON.parse(frame.body)
                  this._handleStompErrorEvent(body)
                } catch {
                  this._handleStompErrorEvent({ message: frame.body || '操作失败' })
                }
              })
              // Pull offline messages on connect
              this.pullOfflineMessages()
            },
            onMessage: (frame) => {
              // fallback handler
            },
            onError: (message) => this.handleConnectionError(message),
            onDisconnect: () => {
              const wasManual = this.manualDisconnect
              this.manualDisconnect = false
              this.stopHeartbeat()
              this.connected = false
              this.connectionState = 'disconnected'
              this.logConnection('WebSocket 已断开')
              if (!wasManual) this.reconnectStomp()
            },
          })

          this._stomp = stomp
          stomp.connect()

          // Store deactivate for cleanup
          this._deactivateStomp = () => {
            stomp.disconnect()
            this.connected = false
            this.connectionState = 'disconnected'
            this._stomp = null
          }
        })
        .catch((e) => {
          if (e.name === 'AbortError' || attempt !== this._connectAttempt) return
          this.error = 'WebSocket连接失败: ' + e.message
          this.handleConnectionError(this.error)
          if (!this.manualDisconnect) this.reconnectStomp()
        })
    },

    /**
     * Disconnect STOMP.
     */
    /** Disconnect transport. Manual disconnects suppress automatic reconnect. */
    disconnectStomp({ manual = true } = {}) {
      this._connectAttempt += 1
      if (this._ticketAbortController) {
        this._ticketAbortController.abort()
        this._ticketAbortController = null
      }
      this.manualDisconnect = manual
      this.stopHeartbeat()
      if (this._reconnectTimer) {
        clearTimeout(this._reconnectTimer)
        this._reconnectTimer = null
      }
      if (this._deactivateStomp) {
        this._deactivateStomp()
        this._deactivateStomp = null
      }
      this.connectionState = 'disconnected'
      this.connected = false
      if (manual) this.logConnection('已手动断开 WebSocket')
    },

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
      this.supportTicketLoading = false
      this.supportTicketError = null
      this.activeUserProfile = null
      this.agentOnline = false
      this.agentDashboard = null
      this.agentRatingSummary = null
      this.transferLogs = []
      this.error = null
    },

    // ─── Agent workspace actions ──────────────────────────────

    /** Refresh agent view counts and the currently selected view. */
    refreshAgentViews() {
      const auth = useAuthStore()
      if (auth.role !== 'AGENT') return
      this.loadAgentViewCounts()
      this.loadAgentViewSessions(this.activeAgentView)
    },

    async openAgentSearchResult(result) {
      if (!result?.sessionId) return false
      if (!this.sessions.some((session) => session.sessionId === result.sessionId)) {
        this.sessions.unshift({
          sessionId: result.sessionId,
          title: result.sessionTitle || '新咨询',
          status: result.sessionStatus || 'CLOSED',
          userId: result.userId,
          agentId: result.agentId,
        })
      }
      this.pinnedAgentSearchSession = this.sessions.find((session) => session.sessionId === result.sessionId) || null
      this.focusedMessageId = result.messageId || null
      await this.selectAgentSession(result.sessionId)
      return true
    },

    clearFocusedMessage(messageId) {
      if (!messageId || this.focusedMessageId === messageId) this.focusedMessageId = null
    },

    /** Send the business heartbeat expected by the backend presence tracker. */
    startHeartbeat() {
      this.stopHeartbeat()
      if (!this._stomp || !this.connected) return
      const send = () => {
        if (this._stomp && this.connected) this._stomp.publish('/app/chat.heartbeat', {})
      }
      send()
      this._heartbeatTimer = setInterval(send, 30000)
    },

    stopHeartbeat() {
      if (this._heartbeatTimer) {
        clearInterval(this._heartbeatTimer)
        this._heartbeatTimer = null
      }
    },

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
      const pageNo = Number(params.pageNo || 1)
      const pageSize = Number(params.pageSize || this.sessionsPageSize)
      const requestSequence = ++this._sessionsRequestSequence
      this.sessionsLoading = true
      this.error = null
      try {
        const requestParams = {
          ...params,
          pageNo,
          pageSize,
        }
        if (viewCode === 'MY_TICKETS' && this.ticketStatusFilter) {
          requestParams.ticketStatus = this.ticketStatusFilter
        }
        const result = await listAgentViewSessions(viewCode, requestParams)
        if (requestSequence !== this._sessionsRequestSequence) return
        const page = result?.data || result
        const records = page?.records || []
        if (pageNo > 1) {
          const bySessionId = new Map(this.sessions.map((session) => [session.sessionId, session]))
          records.forEach((session) => bySessionId.set(session.sessionId, session))
          this.sessions = [...bySessionId.values()]
        } else {
          this.sessions = records
        }
        const pinned = this.pinnedAgentSearchSession
        if (pinned && this.activeSessionId === pinned.sessionId
          && !this.sessions.some((session) => session.sessionId === pinned.sessionId)) {
          this.sessions = [pinned, ...this.sessions]
        }
        this.sessionsPageNo = page?.current || pageNo
        this.sessionsPageSize = page?.size || pageSize
        this.sessionsTotal = page?.total || 0
        this.sessionsTotalPages = page?.pages || (this.sessionsPageSize ? Math.ceil(this.sessionsTotal / this.sessionsPageSize) : 0)
        this.sessionsHasMore = this.sessionsPageNo < this.sessionsTotalPages
        return true
      } catch (e) {
        if (requestSequence === this._sessionsRequestSequence) this.error = e.message
        return false
      } finally {
        if (requestSequence === this._sessionsRequestSequence) this.sessionsLoading = false
      }
    },

    async loadNextAgentSessionsPage() {
      if (this.sessionsLoading || !this.sessionsHasMore) return
      return this.loadAgentViewSessions(this.activeAgentView, { pageNo: this.sessionsPageNo + 1, pageSize: this.sessionsPageSize })
    },

    async loadAllRemainingAgentSessions() {
      if (this.sessionsLoading || this.sessionsLoadingMore || !this.sessionsHasMore) return
      const viewCode = this.activeAgentView
      this.sessionsLoadingMore = true
      try {
        while (viewCode === this.activeAgentView && this.sessionsHasMore) {
          const loaded = await this.loadAgentViewSessions(viewCode, {
            pageNo: this.sessionsPageNo + 1,
            pageSize: this.sessionsPageSize,
          })
          if (!loaded || viewCode !== this.activeAgentView) break
        }
      } finally {
        this.sessionsLoadingMore = false
      }
    },

    /**
     * Switch active agent view and refresh sessions.
     */
    async switchAgentView(viewCode) {
      this.pinnedAgentSearchSession = null
      this.activeAgentView = viewCode
      await this.loadAgentViewSessions(viewCode)
    },

    async filterAgentTickets(ticketStatus) {
      this.ticketStatusFilter = ticketStatus || ''
      this.activeAgentView = 'MY_TICKETS'
      await this.loadAgentViewSessions('MY_TICKETS')
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

    async saveArchiveRemark(sessionId, remark) {
      try {
        await saveArchiveRemarkRequest(sessionId, { remark })
        const index = this.sessions.findIndex((session) => session.sessionId === sessionId)
        if (index !== -1) {
          this.sessions[index] = { ...this.sessions[index], archiveRemark: remark || null }
        }
        return true
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
      if (this.pinnedAgentSearchSession && this.pinnedAgentSearchSession.sessionId !== sessionId) {
        this.pinnedAgentSearchSession = null
      }
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
        if (body.sessionId === this.activeSessionId) this.loadSupportTicket(body.sessionId)
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

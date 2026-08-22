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
  findAgentDashboard,
  findAgentRatingSummary,
  findTransferLogs,
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
    queueNotice: null,
    consultationStarting: false,
    /** Map of sessionId -> unread count */
    unreadCounts: {},
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
    /** Loading state for sessions */
    sessionsLoading: false,
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
    /** Metadata for the active session */
    activeMetadata: null,
    /** User profile for the active session */
    activeUserProfile: null,
    /** Agent online status */
    agentOnline: false,
    agentDashboard: null,
    agentRatingSummary: null,
    transferLogs: [],
    /** Message currently being edited */
    editingMessageId: null,
    editingContent: '',
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
      this._clearHistoryTimeout()
      this._historyRequestSequence += 1
      this._historyPendingSessionId = null
      this._historyPendingRequestId = null
      this.messagesLoading = false
      this.activeSessionId = sessionId
      this.messages = []
      this._sentClientMsgIds.clear()
      this.editingMessageId = null
      this.editingContent = ''
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

    startEditing(message) {
      const auth = useAuthStore()
      if (!message || message.senderId !== auth.userId || message.type !== 'TEXT' || message.recalled) return
      this.editingMessageId = message.id
      this.editingContent = message.content || ''
    },

    cancelEditing() {
      this.editingMessageId = null
      this.editingContent = ''
    },

    editMessage(messageId, content = this.editingContent) {
      const auth = useAuthStore()
      const message = this.messages.find((item) => item.id === messageId)
      const value = content.trim()
      if (!message || message.senderId !== auth.userId || message.type !== 'TEXT' || !value) return
      if (!this._stomp || !this.connected) { this.error = '未连接到服务器'; return }
      this._stomp.publish('/app/chat.message.edit', { messageId, content: value })
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
      if (this.consultationStarting || this.sessions.some((session) => session.status === 'ACTIVE' || session.status === 'QUEUED')) {
        this.error = '当前已有进行中的咨询'
        return
      }
      this.consultationStarting = true
      this.queueNotice = '正在为您分配客服…'
      try {
        this._stomp.publish('/app/chat.start', {})
      } catch (e) {
        this.consultationStarting = false
        this.queueNotice = null
        this.error = e.message || '咨询请求发送失败'
        return
      }
      // The session-assigned event is asynchronous; refresh once as a fallback.
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
      this._stomp.publish('/app/chat.end', { sessionId })
      return true
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
      try {
        const result = await getQueueStatus()
        this.queueStatus = result?.data || result
      } catch (e) {
        // Non-critical, don't set error
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
              // Subscribe to message events (read, edit, recall, ack)
              stomp.subscribe('/user/queue/messages', (frame) => {
                const body = JSON.parse(frame.body)
                this._handleMessageEvent(body)
              })
              stomp.subscribe('/user/queue/errors', (frame) => {
                try {
                  const body = JSON.parse(frame.body)
                  this.error = body.message || body.error || '操作失败'
                } catch {
                  this.error = frame.body || '操作失败'
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
      this.queueNotice = null
      this.consultationStarting = false
      this.unreadCounts = {}
      this._sentClientMsgIds.clear()
      this.sessionsLoading = false
      this.messagesLoading = false
      this.historyCursor = null
      this.historyHasMore = false
      this.historyLoadingMore = false
      this.agentViewCounts = []
      this.activeMetadata = null
      this.activeUserProfile = null
      this.agentOnline = false
      this.agentDashboard = null
      this.agentRatingSummary = null
      this.transferLogs = []
      this.editingMessageId = null
      this.editingContent = ''
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
          if (msg.id && msg.senderId !== auth.userId) this.sendAck(msg.id)
          // Mark read if this session is active
          if (msg.id && msg.senderId !== auth.userId) this.markRead(msg.sessionId, msg.id)
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

      if (event === 'WAITING_FOR_AGENT' || event === 'VIP_CALLBACK_REQUIRED' || event === 'ASSIGNMENT_PROCESSING') {
        this.queueNotice = event === 'VIP_CALLBACK_REQUIRED'
          ? '暂时没有可接待的 VIP 客服，已登记回呼。'
          : event === 'ASSIGNMENT_PROCESSING'
            ? '正在分配客服…'
            : '已进入排队，正在等待客服接入。'
        this.loadQueueStatus()
        return
      }

      if (event === 'ERROR') {
        this.consultationStarting = false
        this.error = body.message || body.error || '咨询请求失败'
        return
      }

      if (event === 'SESSION_CREATED' || event === 'SESSION_RECONNECTED' || event === 'SESSION_TRANSFERRED' || event === 'SESSION_ENDED' || event === 'SESSION_CLOSED') {
        const auth = useAuthStore()
        if (auth.role === 'AGENT') {
          this.refreshAgentViews()
        } else {
          const sessionId = body.sessionId || body.session?.sessionId
          this.consultationStarting = false
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
        if (this.editingMessageId === body.messageId) this.cancelEditing()
        this.refreshAgentViews()
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
        if (this.editingMessageId === body.messageId) this.cancelEditing()
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

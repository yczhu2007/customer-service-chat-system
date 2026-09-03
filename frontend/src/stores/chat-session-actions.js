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
  getSupportTicketHistory,
  getSupportTicketHistoryPage,
  createSupportTicket,
  updateSupportTicket,
  submitSupportTicketUserFeedback,
} from '../api/chat-api'

export const sessionActions = {
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
  this.activeSupportTicketHistory = []
  this.supportTicketHistoryPageNo = 0
  this.supportTicketHistoryHasMore = false
  this.supportTicketError = null
  this.supportTicketFormDirty = false
  this.supportTicketStale = false
  const ticketRequestSequence = ++this._supportTicketRequestSequence
  this.loadSupportTicket(sessionId, ticketRequestSequence)
  this.loadSupportTicketHistory(sessionId)
  this.loadTransferLogs(sessionId)
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


}


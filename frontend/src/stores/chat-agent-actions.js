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

export const agentActions = {
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

async loadTransferLogs(sessionId, requestSequence = ++this._transferLogRequestSequence) {
  if (!sessionId) { this.transferLogs = []; return }
  try {
    const result = await findTransferLogs(sessionId)
    if (this.activeSessionId === sessionId && requestSequence === this._transferLogRequestSequence) {
      this.transferLogs = result?.data || result || []
    }
  } catch (e) {
    if (this.activeSessionId === sessionId && requestSequence === this._transferLogRequestSequence) {
      this.transferLogs = []
      this.error = e.message
    }
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
      this.supportTicketFormDirty = false
      this.supportTicketStale = false
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

async loadSupportTicketHistory(sessionId, requestSequence = ++this._supportTicketHistoryRequestSequence) {
  if (!sessionId) {
    this.activeSupportTicketHistory = []
    return []
  }
  try {
    const result = await getSupportTicketHistoryPage(sessionId, 1, 20)
    const page = result && Object.prototype.hasOwnProperty.call(result, 'data') ? result.data : result
    const history = page?.records || []
    if (this.activeSessionId === sessionId && requestSequence === this._supportTicketHistoryRequestSequence) {
      this.activeSupportTicketHistory = history
      this.supportTicketHistoryPageNo = page?.pageNo || 1
      this.supportTicketHistoryHasMore = (page?.pageNo || 1) < (page?.pages || 0)
    }
    return history || []
  } catch (e) {
    if (this.activeSessionId === sessionId && requestSequence === this._supportTicketHistoryRequestSequence) {
      this.activeSupportTicketHistory = []
    }
    return []
  }
},

async createSupportTicket(sessionId, data) {
  try {
    const result = await createSupportTicket(sessionId, data)
    const ticket = result && Object.prototype.hasOwnProperty.call(result, 'data') ? result.data : result
    if (this.activeSessionId === sessionId) {
      this.activeSupportTicket = ticket
      this.loadSupportTicketHistory(sessionId)
    }
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
    if (this.activeSessionId === ticket?.sessionId) {
      this.activeSupportTicket = ticket
      this.loadSupportTicketHistory(ticket.sessionId)
    }
    return ticket
  } catch (e) {
    this.error = e.message
    throw e
  }
},

async loadMoreSupportTicketHistory() {
  if (!this.activeSessionId || !this.supportTicketHistoryHasMore) return
  const result = await getSupportTicketHistoryPage(this.activeSessionId, this.supportTicketHistoryPageNo + 1, 20)
  const page = result?.data || result
  this.activeSupportTicketHistory.push(...(page?.records || []))
  this.supportTicketHistoryPageNo = page?.pageNo || this.supportTicketHistoryPageNo
  this.supportTicketHistoryHasMore = this.supportTicketHistoryPageNo < (page?.pages || 0)
},

async respondToTicketResolution(ticketNo, action, version) {
  try {
    const result = await submitSupportTicketUserFeedback(ticketNo, { action, version })
    const ticket = result && Object.prototype.hasOwnProperty.call(result, 'data') ? result.data : result
    if (this.activeSessionId === ticket?.sessionId) {
      this.activeSupportTicket = ticket
      this.loadSupportTicketHistory(ticket.sessionId)
    }
    return ticket
  } catch (e) {
    this.error = e.message
    throw e
  }
},

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
    if (['MY_TICKETS', 'MY_PARTICIPATED_TICKETS'].includes(viewCode) && this.ticketStatusFilter) {
      requestParams.ticketStatus = this.ticketStatusFilter
    }
    if (['MY_TICKETS', 'MY_PARTICIPATED_TICKETS'].includes(viewCode) && this.ticketKeyword) {
      requestParams.ticketKeyword = this.ticketKeyword
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

async filterAgentTickets(ticketStatus, ticketKeyword = this.ticketKeyword) {
  this.ticketStatusFilter = ticketStatus || ''
  this.ticketKeyword = (ticketKeyword || '').trim()
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

}

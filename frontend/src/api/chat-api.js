import { request, handleUnauthorized } from '../services/http-client'
import { useAuthStore } from '../stores/auth'

/**
 * List sessions for the current user (USER scope) or agent (AGENT scope).
 * Params: { status, archiveStatus, pageNo, pageSize }
 */
export const listSessions = (params = {}) => {
  const qs = new URLSearchParams(params).toString()
  return request(`/chat/sessions${qs ? '?' + qs : ''}`)
}

/**
 * List agent fixed views (AGENT only).
 */
export const listAgentViews = () => request('/chat/agent/views')

/**
 * List sessions for a specific agent fixed view (AGENT only).
 * Params: { pageNo, pageSize, ticketStatus, ticketKeyword }
 */
export const listAgentViewSessions = (viewCode, params = {}) => {
  const qs = new URLSearchParams(params).toString()
  return request(`/chat/agent/views/${viewCode}/sessions${qs ? '?' + qs : ''}`)
}

/**
 * Get current queue status: online agents, queue size, my position, estimated wait.
 */
export const getQueueStatus = () => request('/chat/queue-status')

export const cancelQueue = () => request('/chat/queue/cancel', {
  method: 'POST',
})

/**
 * Get the rating for a closed session (USER only).
 */
export const getSessionRating = (sessionId) =>
  request(`/chat/sessions/${sessionId}/rating`)

/**
 * Submit a rating for a closed session (USER only).
 * data: { rating: 1-5, comment?: string }
 */
export const submitRating = (sessionId, data) =>
  request(`/chat/sessions/${sessionId}/rating`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  })

/**
 * Upload an attachment to a session (multipart form).
 * Returns Result<ChatAttachmentVO>.
 */
export const uploadAttachment = (sessionId, file) => {
  const auth = useAuthStore()
  const formData = new FormData()
  formData.append('sessionId', sessionId)
  formData.append('file', file)
  const headers = {}
  if (auth.token) headers.Authorization = `Bearer ${auth.token}`
  return fetch('/chat/attachments', {
    method: 'POST',
    headers,
    body: formData,
  }).then(async (res) => {
    if (res.status === 401) await handleUnauthorized()
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    return res.json()
  })
}

/**
 * Fetch an attachment blob with auth header (for IMAGE/FILE rendering).
 * Returns a blob URL string.
 */
export const fetchAttachmentBlob = async (contentUrl) => {
  const headers = {}
  let url
  try {
    url = new URL(contentUrl, window.location.origin)
  } catch {
    throw new Error('附件地址无效')
  }
  const isLocalAttachment = url.origin === window.location.origin
    && /^\/chat\/attachments\/[A-Za-z0-9]{32,64}\/content$/.test(url.pathname)
  if (isLocalAttachment) {
    const auth = useAuthStore()
    if (auth.token) headers.Authorization = `Bearer ${auth.token}`
  }
  const res = await fetch(contentUrl, { headers })
  if (res.status === 401) await handleUnauthorized()
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  const blob = await res.blob()
  const disposition = res.headers.get('content-disposition') || ''
  const encodedName = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
  const plainName = disposition.match(/filename="?([^";]+)"?/i)?.[1]
  return {
    url: URL.createObjectURL(blob),
    name: encodedName ? decodeURIComponent(encodedName) : (plainName || '附件'),
    size: blob.size,
    type: blob.type,
  }
}

// ─── Agent workspace APIs ────────────────────────────────────────

/**
 * Set the current agent online.
 */
export const agentOnline = () =>
  request('/chat/agent/online', { method: 'POST' })

/**
 * Set the current agent offline.
 */
export const agentOffline = () =>
  request('/chat/agent/offline', { method: 'POST' })

export const findAgentDashboard = () => request('/chat/agent/dashboard')

export const findAgentRatingSummary = (params = {}) => {
  const qs = new URLSearchParams(params).toString()
  return request(`/chat/agent/ratings/summary${qs ? '?' + qs : ''}`)
}

export const findTransferLogs = (sessionId) =>
  request(`/chat/sessions/${encodeURIComponent(sessionId)}/transfers`)

export const getSupportTicket = (sessionId) =>
  request(`/chat/sessions/${encodeURIComponent(sessionId)}/ticket`)

export const getSupportTicketHistory = (sessionId) =>
  request(`/chat/sessions/${encodeURIComponent(sessionId)}/ticket/history`)

export const getSupportTicketHistoryPage = (sessionId, pageNo = 1, pageSize = 20) =>
  request(`/chat/sessions/${encodeURIComponent(sessionId)}/ticket/history/page?pageNo=${pageNo}&pageSize=${pageSize}`)

export const createSupportTicket = (sessionId, data) =>
  request(`/chat/sessions/${encodeURIComponent(sessionId)}/ticket`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  })

export const updateSupportTicket = (ticketNo, data) =>
  request(`/chat/tickets/${encodeURIComponent(ticketNo)}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  })

export const submitSupportTicketUserFeedback = (ticketNo, data) =>
  request(`/chat/tickets/${encodeURIComponent(ticketNo)}/user-feedback`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  })

/**
 * Get session metadata (title, priority, category, tags).
 */
export const getSessionMetadata = (sessionId) =>
  request(`/chat/sessions/${sessionId}/metadata`)

/**
 * Update session metadata (AGENT only, assigned agent).
 * data: { title, priority, category?, tags? }
 */
export const updateSessionMetadata = (sessionId, data) =>
  request(`/chat/sessions/${sessionId}/metadata`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  })

/**
 * Update session archive status (AGENT only).
 * data: { archiveStatus, remark? }
 */
export const setArchiveStatus = (sessionId, data) =>
  request(`/chat/sessions/${sessionId}/archive-status`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  })

export const saveArchiveRemark = (sessionId, data) =>
  request(`/chat/sessions/${sessionId}/archive-remark`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  })

/**
 * Get user profile sidebar for a session (AGENT only).
 */
export const getUserProfile = (sessionId) =>
  request(`/chat/sessions/${sessionId}/user-profile`)

/**
 * List current agent's quick replies.
 */
export const listQuickReplies = () =>
  request('/chat/quick-replies')

/**
 * Create a new quick reply.
 * data: { content }
 */
export const createQuickReply = (data) =>
  request('/chat/quick-replies', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  })

/**
 * Update an existing quick reply.
 * data: { content }
 */
export const updateQuickReply = (id, data) =>
  request(`/chat/quick-replies/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  })

/**
 * Delete a quick reply.
 */
export const deleteQuickReply = (id) =>
  request(`/chat/quick-replies/${id}`, { method: 'DELETE' })

export const searchAgentMessages = (params = {}) => {
  const qs = new URLSearchParams(params).toString()
  return request(`/chat/agent/messages/search?${qs}`)
}

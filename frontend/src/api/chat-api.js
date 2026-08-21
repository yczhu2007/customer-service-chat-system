import { request } from '../services/http-client'
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
 * Params: { pageNo, pageSize }
 */
export const listAgentViewSessions = (viewCode, params = {}) => {
  const qs = new URLSearchParams(params).toString()
  return request(`/chat/agent/views/${viewCode}/sessions${qs ? '?' + qs : ''}`)
}

/**
 * Get current queue status: online agents, queue size, my position, estimated wait.
 */
export const getQueueStatus = () => request('/chat/queue-status')

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
  }).then((res) => {
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    return res.json()
  })
}

/**
 * Fetch an attachment blob with auth header (for IMAGE/FILE rendering).
 * Returns a blob URL string.
 */
export const fetchAttachmentBlob = async (attachmentId) => {
  const auth = useAuthStore()
  const headers = {}
  if (auth.token) headers.Authorization = `Bearer ${auth.token}`
  const res = await fetch(`/chat/attachments/${attachmentId}/content`, { headers })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  const blob = await res.blob()
  return URL.createObjectURL(blob)
}

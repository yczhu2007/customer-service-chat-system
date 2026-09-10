import { request } from '../services/http-client'

// ─── User Management ────────────────────────────────────────

export function listUsers(params = {}) {
  const qs = new URLSearchParams()
  if (params.pageNo) qs.set('pageNo', params.pageNo)
  if (params.pageSize) qs.set('pageSize', params.pageSize)
  if (params.keyword?.trim()) qs.set('keyword', params.keyword.trim())
  return request(`/users?${qs.toString()}`)
}

export function createUser(data) {
  return request('/users', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export function updateUser(id, data) {
  return request(`/users/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  })
}

export function deleteUser(id) {
  return request(`/users/${id}`, { method: 'DELETE' })
}

export function resetUserPassword(id) {
  return request(`/users/${id}/reset-password`, { method: 'POST' })
}

// ─── Role Management ────────────────────────────────────────

export function listRoles(params = {}) {
  const qs = new URLSearchParams()
  if (params.pageNo) qs.set('pageNo', params.pageNo)
  if (params.pageSize) qs.set('pageSize', params.pageSize)
  return request(`/roles?${qs.toString()}`)
}

export function createRole(data) {
  return request('/roles', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export function updateRole(id, data) {
  return request(`/roles/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  })
}

export function deleteRole(id) {
  return request(`/roles/${id}`, { method: 'DELETE' })
}

export function findRolePermissions(roleId) {
  return request(`/roles/${roleId}/permissions`)
}

export function assignPermissionToRole(roleId, permissionId) {
  return request(`/roles/${roleId}/permissions/${permissionId}`, {
    method: 'PUT',
  })
}

export function removePermissionFromRole(roleId, permissionId) {
  return request(`/roles/${roleId}/permissions/${permissionId}`, {
    method: 'DELETE',
  })
}

// ─── Permission Management ──────────────────────────────────

export function listPermissions(params = {}) {
  const qs = new URLSearchParams()
  if (params.pageNo) qs.set('pageNo', params.pageNo)
  if (params.pageSize) qs.set('pageSize', params.pageSize)
  return request(`/permissions?${qs.toString()}`)
}

// ─── Dead Letter Management ─────────────────────────────────

export function findDeadLetters(params = {}) {
  const qs = new URLSearchParams()
  if (params.pageNo) qs.set('pageNo', params.pageNo)
  if (params.pageSize) qs.set('pageSize', params.pageSize)
  return request(`/chat/admin/deadletters?${qs.toString()}`)
}

export function replayDeadLetter(messageId) {
  return request(`/chat/admin/deadletters/${messageId}/replay`, {
    method: 'POST',
  })
}

// ─── Archive Statistics ─────────────────────────────────────

export function findAdminDashboard() {
  return request('/chat/admin/dashboard')
}

export function findAdminReportOverview(params = {}) {
  const qs = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).trim() !== '') qs.set(key, value)
  })
  return request(`/chat/admin/reports/overview${qs.size ? `?${qs}` : ''}`)
}

export function findSystemMonitoringSnapshot() {
  return request('/chat/admin/monitoring')
}

function ticketQueryString(params = {}, includePaging = true) {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).trim() !== '' && (includePaging || !['pageNo', 'pageSize'].includes(key))) {
      query.set(key, value)
    }
  })
  return query.toString()
}

export function listAdminTickets(params = {}) {
  return request(`/chat/admin/tickets?${ticketQueryString(params)}`)
}

export function findAdminTicketStatusCounts(params = {}) {
  return request(`/chat/admin/tickets/status-counts?${ticketQueryString(params, false)}`)
}

export function findAdminRatingSummary(params = {}) {
  const qs = new URLSearchParams(params).toString()
  return request(`/chat/admin/ratings/summary${qs ? '?' + qs : ''}`)
}

export function findTransferLogs(sessionId) {
  return request(`/chat/sessions/${encodeURIComponent(sessionId)}/transfers`)
}

export function deleteDeadLetter(messageId) {
  return request(`/chat/admin/deadletters/${encodeURIComponent(messageId)}`, {
    method: 'DELETE',
  })
}

export function createAdminSession(data) {
  return request('/chat/admin/sessions', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export function updateAdminSession(sessionId, data) {
  return request(`/chat/admin/sessions/${encodeURIComponent(sessionId)}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  })
}

export function deleteAdminSession(sessionId) {
  return request(`/chat/admin/sessions/${encodeURIComponent(sessionId)}`, {
    method: 'DELETE',
  })
}

export function listAdminSessions(params = {}) {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).trim() !== '') query.set(key, value)
  })
  return request(`/chat/admin/sessions?${query}`)
}

export function findSessionMetadata(sessionId) {
  return request(`/chat/sessions/${encodeURIComponent(sessionId)}/metadata`)
}

export function findArchiveStats() {
  return request('/chat/admin/archive-stats')
}

// ─── VIP Skill Group ────────────────────────────────────────

export function findVipSkillAgents() {
  return request('/chat/agents/vip-skill')
}

export function addVipSkill(agentLoginNumber) {
  return request(`/chat/agents/${encodeURIComponent(agentLoginNumber)}/vip-skill`, {
    method: 'PUT',
  })
}

export function removeVipSkill(agentLoginNumber) {
  return request(`/chat/agents/${encodeURIComponent(agentLoginNumber)}/vip-skill`, {
    method: 'DELETE',
  })
}

export function searchAdminMessages(params = {}) {
  const qs = new URLSearchParams(params).toString()
  return request(`/chat/admin/messages/search?${qs}`)
}

export function deleteAdminMessage(messageId) {
  return request(`/chat/admin/messages/${encodeURIComponent(messageId)}`, {
    method: 'DELETE',
  })
}

export function createPermission(data) {
  return request('/permissions', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export function updatePermission(id, data) {
  return request(`/permissions/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  })
}

export function deletePermission(id) {
  return request(`/permissions/${encodeURIComponent(id)}`, { method: 'DELETE' })
}

export function findUserRoles(userId) {
  return request(`/users/${encodeURIComponent(userId)}/roles`)
}

export function assignRoleToUser(userId, roleId) {
  return request(`/users/${encodeURIComponent(userId)}/roles/${encodeURIComponent(roleId)}`, {
    method: 'PUT',
  })
}

export function removeRoleFromUser(userId, roleId) {
  return request(`/users/${encodeURIComponent(userId)}/roles/${encodeURIComponent(roleId)}`, {
    method: 'DELETE',
  })
}

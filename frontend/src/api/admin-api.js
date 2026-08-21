import { request } from '../services/http-client'

// ─── User Management ────────────────────────────────────────

export function listUsers(params = {}) {
  const qs = new URLSearchParams()
  if (params.pageNo) qs.set('pageNo', params.pageNo)
  if (params.pageSize) qs.set('pageSize', params.pageSize)
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

export function updateUserPassword(id, data) {
  return request(`/users/${id}/password`, {
    method: 'PUT',
    body: JSON.stringify(data),
  })
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

export function findArchiveStats() {
  return request('/chat/admin/archive-stats')
}

// ─── VIP Skill Group ────────────────────────────────────────

export function findVipSkillAgents() {
  return request('/chat/agents/vip-skill')
}

export function addVipSkill(agentId) {
  return request(`/chat/agents/${agentId}/vip-skill`, {
    method: 'PUT',
  })
}

export function removeVipSkill(agentId) {
  return request(`/chat/agents/${agentId}/vip-skill`, {
    method: 'DELETE',
  })
}

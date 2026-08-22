import { defineStore } from 'pinia'

const KEY = 'customer-service-auth'
const ROLE_ORDER = ['ADMIN', 'AGENT', 'USER']
const normalizeRoles = (roles, role) => [...new Set((Array.isArray(roles) ? roles : [role]).filter((value) => ROLE_ORDER.includes(value)))]
export const useAuthStore = defineStore('auth', {
  state: () => ({ token: null, userId: null, username: null, roles: [], activeRole: null, role: null }),
  getters: {
    isAuthenticated: (state) => Boolean(state.token && state.userId && state.activeRole && state.roles.includes(state.activeRole)),
    homeRole: (state) => state.activeRole || 'USER',
    hasRole: (state) => (role) => state.roles.includes(role),
  },
  actions: {
    restore() {
      try {
        const saved = JSON.parse(localStorage.getItem(KEY) || '{}')
        const roles = normalizeRoles(saved.roles, saved.role)
        const activeRole = roles.includes(saved.activeRole) ? saved.activeRole : ROLE_ORDER.find((role) => roles.includes(role))
        if (typeof saved.token !== 'string' || !saved.token || typeof saved.userId !== 'string' || !saved.userId || !activeRole) throw new Error('invalid auth state')
        Object.assign(this, { token: saved.token, userId: saved.userId, username: saved.username || saved.userId, roles, activeRole, role: activeRole })
      } catch { this.logout() }
    },
    login(payload) {
      const roles = normalizeRoles(payload.roles, payload.role)
      const activeRole = roles.includes(payload.activeRole) ? payload.activeRole : ROLE_ORDER.find((role) => roles.includes(role))
      if (!activeRole) throw new Error('登录响应缺少有效角色')
      this.token = payload.token
      this.userId = payload.userId
      this.username = payload.username || payload.userId
      this.roles = roles
      this.activeRole = activeRole
      this.role = activeRole
      localStorage.setItem(KEY, JSON.stringify({ token: this.token, userId: this.userId, username: this.username, roles: this.roles, activeRole: this.activeRole }))
    },
    setActiveRole(role) {
      if (!this.roles.includes(role)) return false
      this.activeRole = role
      this.role = role
      localStorage.setItem(KEY, JSON.stringify({ token: this.token, userId: this.userId, username: this.username, roles: this.roles, activeRole: this.activeRole }))
      return true
    },
    logout() {
      this.$reset()
      localStorage.removeItem(KEY)
    },
  },
})

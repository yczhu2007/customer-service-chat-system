import { defineStore } from 'pinia'

const KEY = 'customer-service-auth'
export const useAuthStore = defineStore('auth', {
  state: () => ({ token: null, userId: null, role: null }),
  getters: {
    isAuthenticated: (state) => Boolean(state.token && state.role),
    homeRole: (state) => state.role || 'USER',
  },
  actions: {
    restore() {
      try { Object.assign(this, JSON.parse(localStorage.getItem(KEY) || '{}')) } catch { this.logout() }
    },
    login(payload) {
      this.token = payload.token
      this.userId = payload.userId
      this.role = payload.role
      localStorage.setItem(KEY, JSON.stringify({ token: this.token, userId: this.userId, role: this.role }))
    },
    logout() {
      this.$reset()
      localStorage.removeItem(KEY)
    },
  },
})

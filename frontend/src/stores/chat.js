import { defineStore } from 'pinia'
export const useChatStore = defineStore('chat', { state: () => ({ sessions: [], activeSessionId: null, connected: false }) })

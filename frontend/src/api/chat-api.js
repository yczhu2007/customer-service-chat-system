import { request } from '../services/http-client'
export const listSessions = (params = {}) => request(`/chat/sessions?${new URLSearchParams(params)}`)
export const listAgentViews = () => request('/chat/agent/views')
export const listAgentViewSessions = (viewCode, params = {}) => request(`/chat/agent/views/${viewCode}/sessions?${new URLSearchParams(params)}`)

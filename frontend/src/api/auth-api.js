import { request } from '../services/http-client'
export const login = (credentials) => request('/chat/login', { method: 'POST', body: JSON.stringify(credentials) })

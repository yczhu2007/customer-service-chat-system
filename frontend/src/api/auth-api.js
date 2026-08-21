import { request } from '../services/http-client'
export const login = (credentials) => request('/auth/login', { method: 'POST', body: JSON.stringify(credentials) })

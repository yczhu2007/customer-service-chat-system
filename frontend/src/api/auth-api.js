import { request } from '../services/http-client'
export const login = (credentials) => request('/chat/login', { method: 'POST', body: JSON.stringify(credentials) })

export const logout = () => request('/chat/logout', { method: 'POST' })

export const register = (data) => request('/account/register', {
  method: 'POST',
  body: JSON.stringify(data),
})

export const resetPassword = (data) => request('/account/forgot-password', {
  method: 'POST',
  body: JSON.stringify(data),
})

export const getProfile = () => request('/account/profile')

export const updateProfile = (data) => request('/account/profile', {
  method: 'PUT',
  body: JSON.stringify(data),
})

export const updatePassword = (data) => request('/account/password', {
  method: 'PUT',
  body: JSON.stringify(data),
})

export const regenerateRecoveryCode = () => request('/account/recovery-code', {
  method: 'POST',
})

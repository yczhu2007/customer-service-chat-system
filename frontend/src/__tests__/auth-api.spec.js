import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { login, logout, register, resetPassword, getProfile, updateProfile, updatePassword, regenerateRecoveryCode } from '../api/auth-api'
import { useAuthStore } from '../stores/auth'

describe('authentication API', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ data: { token: 'token' } }),
    }))
  })

  it('posts credentials to the backend login endpoint', async () => {
    await login({ username: 'user', password: 'password' })

    expect(fetch).toHaveBeenCalledWith('/chat/login', expect.objectContaining({ method: 'POST' }))
  })

  it('posts to the backend logout endpoint with the current token', async () => {
    const auth = useAuthStore()
    auth.login({ token: 'session-token', userId: 'user-1', role: 'USER' })

    await logout()

    expect(fetch).toHaveBeenCalledWith('/chat/logout', expect.objectContaining({
      method: 'POST',
      headers: expect.objectContaining({ Authorization: 'Bearer session-token' }),
    }))
  })

  it('supports account registration and password recovery endpoints', async () => {
    await register({ username: 'new_user', password: 'password123' })
    await resetPassword({ username: 'new_user', recoveryCode: '1111-2222-3333-4444', newPassword: 'password123' })

    expect(fetch).toHaveBeenNthCalledWith(1, '/account/register', expect.objectContaining({ method: 'POST' }))
    expect(fetch).toHaveBeenNthCalledWith(2, '/account/forgot-password', expect.objectContaining({ method: 'POST' }))
  })

  it('uses the authenticated account endpoints for profile, password, and recovery code', async () => {
    const auth = useAuthStore()
    auth.login({ token: 'session-token', userId: 'user-1', role: 'USER' })

    await getProfile()
    await updateProfile({ nickname: '新的昵称' })
    await updatePassword({ currentPassword: 'old-password', newPassword: 'new-password' })
    await regenerateRecoveryCode()

    expect(fetch).toHaveBeenCalledWith('/account/profile', expect.objectContaining({ headers: expect.any(Object) }))
    expect(fetch).toHaveBeenCalledWith('/account/profile', expect.objectContaining({ method: 'PUT' }))
    expect(fetch).toHaveBeenCalledWith('/account/password', expect.objectContaining({ method: 'PUT' }))
    expect(fetch).toHaveBeenCalledWith('/account/recovery-code', expect.objectContaining({ method: 'POST' }))
  })

  it('provides user role assignment endpoints for the admin user panel', async () => {
    const { findUserRoles, assignRoleToUser, removeRoleFromUser } = await import('../api/admin-api')

    await findUserRoles('user-1')
    await assignRoleToUser('user-1', 'agent-role')
    await removeRoleFromUser('user-1', 'agent-role')

    expect(fetch).toHaveBeenNthCalledWith(1, '/users/user-1/roles', expect.any(Object))
    expect(fetch).toHaveBeenNthCalledWith(2, '/users/user-1/roles/agent-role', expect.objectContaining({ method: 'PUT' }))
    expect(fetch).toHaveBeenNthCalledWith(3, '/users/user-1/roles/agent-role', expect.objectContaining({ method: 'DELETE' }))
  })
})

import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../stores/auth'
import { useChatStore } from '../stores/chat'
import { request } from '../services/http-client'
import { fetchAttachmentBlob } from '../api/chat-api'

if (typeof globalThis.URL.createObjectURL !== 'function') {
  Object.defineProperty(globalThis.URL, 'createObjectURL', { value: () => 'blob:test' })
}

describe('HTTP client authentication expiry', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false,
      status: 401,
      json: () => Promise.resolve({ message: 'authentication expired' }),
    }))
  })

  it('disconnects and clears the previous chat connection before logging out on 401', async () => {
    const auth = useAuthStore()
    auth.login({ token: 'expired-token', userId: 'old-user', role: 'USER' })
    const chat = useChatStore()
    const deactivateStomp = vi.fn()
    chat._deactivateStomp = deactivateStomp
    chat.connected = true
    chat.sessions = [{ sessionId: 'old-session' }]

    await expect(request('/chat/sessions')).rejects.toThrow('authentication expired')

    expect(deactivateStomp).toHaveBeenCalled()
    expect(chat.sessions).toEqual([])
    expect(chat.activeSessionId).toBeNull()
    expect(auth.isAuthenticated).toBe(false)
  })

  it('does not send the application token to an external attachment URL', async () => {
    const auth = useAuthStore()
    auth.login({ token: 'secret-token', userId: 'user-1', role: 'USER' })
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      headers: { get: () => 'attachment; filename="file.txt"' },
      blob: () => Promise.resolve(new Blob(['file'], { type: 'text/plain' })),
    })

    await fetchAttachmentBlob('https://attacker.example/file.txt')

    expect(fetch).toHaveBeenCalledWith('https://attacker.example/file.txt', { headers: {} })
  })

  it('clears authentication when loading a local attachment returns 401', async () => {
    const auth = useAuthStore()
    auth.login({ token: 'expired-token', userId: 'user-1', role: 'USER' })
    const chat = useChatStore()
    chat.sessions = [{ sessionId: 's1' }]

    await expect(fetchAttachmentBlob(
      '/chat/attachments/0123456789abcdef0123456789abcdef/content'
    )).rejects.toThrow('HTTP 401')

    expect(auth.isAuthenticated).toBe(false)
    expect(chat.sessions).toEqual([])
  })
})

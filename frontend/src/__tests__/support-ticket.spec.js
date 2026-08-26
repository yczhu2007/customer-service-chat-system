import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { readFile } from 'node:fs/promises'
import { join } from 'node:path'
import { mount } from '@vue/test-utils'
import { getSupportTicket, createSupportTicket, updateSupportTicket } from '../api/chat-api'
import { useChatStore } from '../stores/chat'
import { useAuthStore } from '../stores/auth'
import SupportTicketPanel from '../components/session/SupportTicketPanel.vue'

describe('会话关联工单', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    const auth = useAuthStore()
    auth.login({ token: 'test-token', userId: 'agent-1', role: 'AGENT' })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ data: { ticketNo: 'TK-00000125' } }),
    }))
  })

  it('uses the documented ticket endpoints', async () => {
    await getSupportTicket('session-1')
    await createSupportTicket('session-1', { description: '支付失败' })
    await updateSupportTicket('TK-00000125', {
      status: 'IN_PROGRESS', description: '支付失败', version: 0,
    })

    expect(fetch).toHaveBeenNthCalledWith(1, '/chat/sessions/session-1/ticket', expect.any(Object))
    expect(fetch).toHaveBeenNthCalledWith(2, '/chat/sessions/session-1/ticket', expect.objectContaining({ method: 'POST' }))
    expect(fetch).toHaveBeenNthCalledWith(3, '/chat/tickets/TK-00000125', expect.objectContaining({ method: 'PATCH' }))
  })

  it('refreshes the active ticket when receiving a ticket event', () => {
    const chat = useChatStore()
    chat.activeSessionId = 'session-1'
    chat.loadSupportTicket = vi.fn()

    chat._handleChatEvent({ event: 'TICKET_UPDATED', sessionId: 'session-1' })

    expect(chat.loadSupportTicket).toHaveBeenCalledWith('session-1')
  })

  it('does not let an earlier request overwrite a newer request for the same session', async () => {
    let resolveOldRequest
    fetch
      .mockReturnValueOnce(new Promise((resolve) => { resolveOldRequest = resolve }))
      .mockResolvedValueOnce({ ok: true, status: 200, json: () => Promise.resolve({ data: { ticketNo: 'TK-00000002', sessionId: 'session-2' } }) })
      .mockResolvedValueOnce({ ok: true, status: 200, json: () => Promise.resolve({ data: { ticketNo: 'TK-00000003', sessionId: 'session-1' } }) })
    const chat = useChatStore()

    chat.activeSessionId = 'session-1'
    const oldRequest = chat.loadSupportTicket('session-1')
    chat.activeSessionId = 'session-2'
    await chat.loadSupportTicket('session-2')
    chat.activeSessionId = 'session-1'
    await chat.loadSupportTicket('session-1')
    resolveOldRequest({ ok: true, status: 200, json: () => Promise.resolve({ data: { ticketNo: 'TK-00000001', sessionId: 'session-1' } }) })
    await oldRequest

    expect(chat.activeSupportTicket.ticketNo).toBe('TK-00000003')
  })

  it('keeps agent editing and user read-only ticket text in the interface', async () => {
    const component = await readFile(join(process.cwd(), 'src/components/session/SupportTicketPanel.vue'), 'utf8')

    expect(component).toContain('创建工单')
    expect(component).toContain('更新工单')
    expect(component).toContain('问题描述')
    expect(component).toContain('处理结果')
  })

  it('shows an edit form only to the assigned agent', () => {
    const chat = useChatStore()
    chat.activeSessionId = 'session-1'
    chat.sessions = [{ sessionId: 'session-1', agentId: 'agent-1' }]
    const agentPanel = mount(SupportTicketPanel)

    expect(agentPanel.text()).toContain('创建工单')
    expect(agentPanel.find('textarea').exists()).toBe(true)

    const auth = useAuthStore()
    auth.login({ token: 'test-token', userId: 'user-1', role: 'USER' })
    chat.activeSupportTicket = {
      ticketNo: 'TK-00000125', sessionId: 'session-1', status: 'IN_PROGRESS',
      title: '支付咨询', priority: 'HIGH', category: 'PAYMENT', agentNickname: '客服一',
      description: '支付失败', resolution: '正在处理', updatedAt: '2026-08-26T10:00:00',
    }
    const userPanel = mount(SupportTicketPanel)

    expect(userPanel.text()).toContain('处理中')
    expect(userPanel.text()).toContain('支付咨询')
    expect(userPanel.find('textarea').exists()).toBe(false)
    expect(userPanel.text()).not.toContain('创建工单')
    expect(userPanel.text()).not.toContain('更新工单')
  })
})

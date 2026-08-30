import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { readFile } from 'node:fs/promises'
import { join } from 'node:path'
import { flushPromises, mount } from '@vue/test-utils'
import { ElMessage } from 'element-plus'
import { getSupportTicket, getSupportTicketHistory, createSupportTicket, updateSupportTicket } from '../api/chat-api'
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
    await getSupportTicketHistory('session-1')
    await createSupportTicket('session-1', { description: '支付失败' })
    await updateSupportTicket('TK-00000125', {
      status: 'IN_PROGRESS', description: '支付失败', version: 0,
    })

    expect(fetch).toHaveBeenNthCalledWith(1, '/chat/sessions/session-1/ticket', expect.any(Object))
    expect(fetch).toHaveBeenNthCalledWith(2, '/chat/sessions/session-1/ticket/history', expect.any(Object))
    expect(fetch).toHaveBeenNthCalledWith(3, '/chat/sessions/session-1/ticket', expect.objectContaining({ method: 'POST' }))
    expect(fetch).toHaveBeenNthCalledWith(4, '/chat/tickets/TK-00000125', expect.objectContaining({ method: 'PATCH' }))
  })

  it('marks a dirty ticket form as stale instead of overwriting it on a ticket event', () => {
    const chat = useChatStore()
    chat.activeSessionId = 'session-1'
    chat.supportTicketFormDirty = true
    chat.loadSupportTicket = vi.fn()

    chat._handleChatEvent({ event: 'TICKET_UPDATED', sessionId: 'session-1' })

    expect(chat.supportTicketStale).toBe(true)
    expect(chat.loadSupportTicket).not.toHaveBeenCalled()
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

  it('starts loading the ticket without waiting for message history', async () => {
    let finishHistory
    const chat = useChatStore()
    chat.loadHistory = vi.fn(() => new Promise((resolve) => { finishHistory = resolve }))
    chat.loadSupportTicket = vi.fn().mockResolvedValue(null)

    const selection = chat.selectSession('session-1')
    await Promise.resolve()

    expect(chat.loadSupportTicket).toHaveBeenCalledWith('session-1', expect.any(Number))
    finishHistory()
    await selection
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

  it('shows only valid quick status actions for the current ticket state', () => {
    const chat = useChatStore()
    chat.activeSessionId = 'session-1'
    chat.sessions = [{ sessionId: 'session-1', agentId: 'agent-1' }]
    chat.activeSupportTicket = {
      ticketNo: 'TK-00000125', sessionId: 'session-1', status: 'IN_PROGRESS',
      description: '支付失败', version: 0,
    }

    const wrapper = mount(SupportTicketPanel)

    expect(wrapper.text()).toContain('标记为等待用户')
    expect(wrapper.text()).toContain('标记为已解决')
    expect(wrapper.text()).not.toContain('标记为待处理')
  })

  it('keeps the draft after a non-conflict update failure', async () => {
    const chat = useChatStore()
    chat.activeSessionId = 'session-1'
    chat.sessions = [{ sessionId: 'session-1', agentId: 'agent-1' }]
    chat.activeSupportTicket = {
      ticketNo: 'TK-00000125', sessionId: 'session-1', status: 'IN_PROGRESS',
      description: '支付失败', version: 0,
    }
    chat.updateSupportTicket = vi.fn().mockRejectedValue(Object.assign(new Error('网络错误'), { status: 500 }))
    chat.loadSupportTicket = vi.fn()

    const wrapper = mount(SupportTicketPanel)
    await wrapper.get('button.update-ticket').trigger('click')
    await flushPromises()

    expect(chat.loadSupportTicket).not.toHaveBeenCalled()
    expect(wrapper.find('textarea').element.value).toBe('支付失败')
  })

  it('reloads the ticket after an update version conflict', async () => {
    const chat = useChatStore()
    chat.activeSessionId = 'session-1'
    chat.sessions = [{ sessionId: 'session-1', agentId: 'agent-1' }]
    chat.activeSupportTicket = {
      ticketNo: 'TK-00000125', sessionId: 'session-1', status: 'IN_PROGRESS',
      description: '支付失败', version: 0,
    }
    chat.updateSupportTicket = vi.fn().mockRejectedValue(Object.assign(new Error('版本冲突'), { status: 409 }))
    chat.loadSupportTicket = vi.fn()
    chat.loadSupportTicketHistory = vi.fn()
    chat.loadTransferLogs = vi.fn()

    const wrapper = mount(SupportTicketPanel)
    await wrapper.get('button.update-ticket').trigger('click')
    await flushPromises()

    expect(chat.loadSupportTicket).toHaveBeenCalledWith('session-1')
    expect(wrapper.text()).toContain('已加载最新内容')
  })

  it('shows a retry state instead of a create form when ticket loading fails', () => {
    const chat = useChatStore()
    chat.activeSessionId = 'session-1'
    chat.sessions = [{ sessionId: 'session-1', agentId: 'agent-1' }]
    chat.supportTicketError = '工单加载失败'

    const panel = mount(SupportTicketPanel)

    expect(panel.text()).toContain('工单加载失败')
    expect(panel.text()).toContain('重新加载')
    expect(panel.text()).not.toContain('创建工单')
  })

  it('reloads an existing ticket after a duplicate create conflict', async () => {
    const chat = useChatStore()
    chat.activeSessionId = 'session-1'
    chat.sessions = [{ sessionId: 'session-1', agentId: 'agent-1' }]
    chat.createSupportTicket = vi.fn().mockRejectedValue(new Error('该会话已创建工单'))
    chat.loadSupportTicket = vi.fn(async () => {
      chat.activeSupportTicket = {
        ticketNo: 'TK-00000125', sessionId: 'session-1', status: 'OPEN',
        description: '已有工单', version: 0,
      }
    })
    const panel = mount(SupportTicketPanel)
    await panel.get('textarea').setValue('重复创建')

    await panel.get('button.create-ticket').trigger('click')
    await flushPromises()

    expect(chat.loadSupportTicket).toHaveBeenCalledWith('session-1')
    expect(panel.text()).toContain('工单已存在，已加载最新内容')
    expect(panel.text()).toContain('TK-00000125')
  })

  it('shows ticket status history and the existing transfer records together', () => {
    const chat = useChatStore()
    chat.activeSessionId = 'session-1'
    chat.sessions = [{ sessionId: 'session-1', agentId: 'agent-1' }]
    chat.activeSupportTicket = {
      ticketNo: 'TK-00000125', sessionId: 'session-1', status: 'IN_PROGRESS',
      description: '支付失败', version: 0,
    }
    chat.activeSupportTicketHistory = [{
      id: 1, fromStatus: 'OPEN', toStatus: 'IN_PROGRESS', operatorNickname: '客服一', createdAt: '2026-08-28T10:00:00',
    }]
    chat.transferLogs = [{
      id: 'transfer-1', sourceAgentNickname: '客服一', targetAgentNickname: '客服二', createTime: '2026-08-28T10:01:00',
    }]

    const wrapper = mount(SupportTicketPanel)

    expect(wrapper.text()).toContain('操作历史')
    expect(wrapper.text()).toContain('待处理 → 处理中')
    expect(wrapper.text()).toContain('负责人变更')
    expect(wrapper.text()).toContain('客服一 → 客服二')
  })

  it('lets a user confirm a resolved ticket or request further handling', async () => {
    const chat = useChatStore()
    const auth = useAuthStore()
    auth.login({ token: 'test-token', userId: 'user-1', role: 'USER' })
    chat.activeSessionId = 'session-1'
    chat.sessions = [{ sessionId: 'session-1', userId: 'user-1', agentId: 'agent-1' }]
    chat.activeSupportTicket = {
      ticketNo: 'TK-00000125', sessionId: 'session-1', status: 'RESOLVED',
      description: '支付失败', resolution: '已修复', version: 0,
    }
    chat.respondToTicketResolution = vi.fn().mockResolvedValue()

    const wrapper = mount(SupportTicketPanel)
    expect(wrapper.text()).toContain('确认已解决')
    expect(wrapper.text()).toContain('申请继续处理')

    await wrapper.get('button.confirm-resolution').trigger('click')
    expect(chat.respondToTicketResolution).toHaveBeenCalledWith('TK-00000125', 'CONFIRM', 0)
  })

  it('offers a copy action for the displayed ticket number', () => {
    const chat = useChatStore()
    chat.activeSessionId = 'session-1'
    chat.sessions = [{ sessionId: 'session-1', agentId: 'agent-1' }]
    chat.activeSupportTicket = {
      ticketNo: 'TK-00000125', sessionId: 'session-1', status: 'IN_PROGRESS',
      description: '支付失败', version: 0,
    }

    const wrapper = mount(SupportTicketPanel)
    expect(wrapper.find('button.copy-ticket-no').exists()).toBe(true)
  })

  it('shows a global success message after copying a ticket number', async () => {
    const chat = useChatStore()
    chat.activeSessionId = 'session-1'
    chat.sessions = [{ sessionId: 'session-1', agentId: 'agent-1' }]
    chat.activeSupportTicket = {
      ticketNo: 'TK-00000125', sessionId: 'session-1', status: 'IN_PROGRESS',
      description: '支付失败', version: 0,
    }
    vi.stubGlobal('navigator', { clipboard: { writeText: vi.fn().mockResolvedValue() } })
    const success = vi.spyOn(ElMessage, 'success')

    const wrapper = mount(SupportTicketPanel)
    await wrapper.get('button.copy-ticket-no').trigger('click')
    await flushPromises()

    expect(success).toHaveBeenCalledWith('工单编号已复制')
    expect(wrapper.get('.copy-success').text()).toBe('已复制')
  })
})

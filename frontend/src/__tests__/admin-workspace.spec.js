import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { ref, nextTick } from 'vue'

// ─── Mock admin-api ──────────────────────────────────────────
vi.mock('../api/admin-api', () => ({
  listUsers: vi.fn(() => Promise.resolve({ data: { records: [], total: 0 } })),
  createUser: vi.fn(),
  updateUser: vi.fn(),
  deleteUser: vi.fn(),
  updateUserPassword: vi.fn(),
  listRoles: vi.fn(() => Promise.resolve({ data: { records: [], total: 0 } })),
  createRole: vi.fn(),
  updateRole: vi.fn(),
  deleteRole: vi.fn(),
  findRolePermissions: vi.fn(() => Promise.resolve({ data: [] })),
  assignPermissionToRole: vi.fn(),
  removePermissionFromRole: vi.fn(),
  listPermissions: vi.fn(() => Promise.resolve({ data: { records: [], total: 0 } })),
  findDeadLetters: vi.fn(() => Promise.resolve({ data: { records: [], total: 0 } })),
  replayDeadLetter: vi.fn(() => Promise.resolve({ message: '重放成功' })),
  findArchiveStats: vi.fn(() =>
    Promise.resolve({
      data: {
        total: 42,
        completed: 20,
        pending: 10,
        onHold: 5,
        other: 3,
        unarchived: 4,
      },
    })
  ),
  findVipSkillAgents: vi.fn(() => Promise.resolve({ data: [] })),
  addVipSkill: vi.fn(),
  removeVipSkill: vi.fn(),
  findTransferLogs: vi.fn(() => Promise.resolve({ data: [] })),
}))

// ─── Mock http-client ────────────────────────────────────────
vi.mock('../services/http-client', () => ({
  request: vi.fn(() => Promise.resolve({ data: { records: [], total: 0 } })),
}))

describe('AdminWorkspaceView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('switches tabs when clicking tab buttons', async () => {
    const AdminWorkspaceView = (await import('../views/AdminWorkspaceView.vue')).default
    const wrapper = mount(AdminWorkspaceView, {
      global: {
        stubs: {
          AdminDashboard: { template: '<div class="stub-dashboard">仪表盘</div>' },
          UserManagementPanel: { template: '<div class="stub-users">用户管理</div>' },
          RoleManagementPanel: { template: '<div class="stub-roles">角色管理</div>' },
          SessionAuditPanel: { template: '<div class="stub-sessions">会话审计</div>' },
          ArchiveStatsPanel: { template: '<div class="stub-archive">归档统计</div>' },
          DeadLetterPanel: { template: '<div class="stub-deadletters">死信管理</div>' },
          VipSkillPanel: { template: '<div class="stub-vip">VIP技能组</div>' },
        },
      },
    })

    // Default tab should be dashboard
    expect(wrapper.find('.stub-dashboard').exists()).toBe(true)

    // Click "用户管理" tab
    const tabs = wrapper.findAll('.tab')
    await tabs[1].trigger('click')
    await nextTick()
    expect(wrapper.find('.stub-users').exists()).toBe(true)

    // Click "归档统计" tab
    await tabs[4].trigger('click')
    await nextTick()
    expect(wrapper.find('.stub-archive').exists()).toBe(true)

    // Click "死信管理" tab
    await tabs[5].trigger('click')
    await nextTick()
    expect(wrapper.find('.stub-deadletters').exists()).toBe(true)
  })
})

describe('ArchiveStatsPanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('displays archive statistics after loading', async () => {
    const { findArchiveStats } = await import('../api/admin-api')
    const ArchiveStatsPanel = (await import('../components/admin/ArchiveStatsPanel.vue')).default
    const wrapper = mount(ArchiveStatsPanel)

    // Wait for async loading
    await vi.dynamicImportSettled()
    await nextTick()
    await new Promise((r) => setTimeout(r, 50))
    await nextTick()

    expect(findArchiveStats).toHaveBeenCalled()
    // Should show total
    expect(wrapper.text()).toContain('42')
    // Should show the status cards
    expect(wrapper.text()).toContain('已完成')
    expect(wrapper.text()).toContain('待处理')
    expect(wrapper.text()).toContain('搁置')
  })
})

describe('DeadLetterPanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('shows replay confirmation dialog when replay button is clicked', async () => {
    const { findDeadLetters } = await import('../api/admin-api')
    findDeadLetters.mockResolvedValueOnce({
      data: {
        records: [
          { messageId: 'msg-001', failedAt: '2026-08-21T10:00:00', payloadAvailable: true },
        ],
        total: 1,
      },
    })

    const DeadLetterPanel = (await import('../components/admin/DeadLetterPanel.vue')).default
    const wrapper = mount(DeadLetterPanel)

    await vi.dynamicImportSettled()
    await nextTick()
    await new Promise((r) => setTimeout(r, 50))
    await nextTick()

    // Should show dead letter data
    expect(wrapper.text()).toContain('msg-001')
    expect(wrapper.text()).toContain('30 天')

    // Click replay button
    const replayBtn = wrapper.find('.btn-primary.btn-sm')
    await replayBtn.trigger('click')
    await nextTick()

    // Confirmation dialog should appear
    expect(wrapper.text()).toContain('确认重放')
    expect(wrapper.text()).toContain('msg-001')
  })
})

describe('SessionAuditPanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders an empty audit table without a runtime scope error', async () => {
    const SessionAuditPanel = (await import('../components/admin/SessionAuditPanel.vue')).default
    const wrapper = mount(SessionAuditPanel)

    await nextTick()
    await new Promise((r) => setTimeout(r, 0))
    await nextTick()

    expect(wrapper.text()).toContain('暂无数据')
  })

  it('only offers ACTIVE and CLOSED in the status filter', async () => {
    const SessionAuditPanel = (await import('../components/admin/SessionAuditPanel.vue')).default
    const wrapper = mount(SessionAuditPanel)
    await nextTick()

    const statusOptions = wrapper.find('select[aria-label="Status"]').findAll('option')
    expect(statusOptions.map((option) => option.attributes('value'))).toEqual(['', 'ACTIVE', 'CLOSED'])
  })

  it('uses one explicit date-time format and normalizes it for the API', async () => {
    const { request } = await import('../services/http-client')
    const SessionAuditPanel = (await import('../components/admin/SessionAuditPanel.vue')).default
    const wrapper = mount(SessionAuditPanel)
    await nextTick()
    request.mockClear()

    const dateInputs = wrapper.findAll('.date-field')
    expect(dateInputs[0].attributes('type')).toBe('datetime-local')
    expect(dateInputs[1].attributes('type')).toBe('datetime-local')

    await dateInputs[0].setValue('2026-08-22T09:30')
    await dateInputs[1].setValue('2026-08-22T18:45')
    await wrapper.find('.btn-primary').trigger('click')

    const requestUrl = new URL(request.mock.calls.at(-1)[0], 'http://localhost')
    expect(requestUrl.searchParams.get('from')).toBe('2026-08-22T09:30')
    expect(requestUrl.searchParams.get('to')).toBe('2026-08-22T18:45')
  })

  it('renders returned audit records and their transfer row safely', async () => {
    const { request } = await import('../services/http-client')
    request.mockResolvedValueOnce({
      data: {
        records: [{ sessionId: 's1', userId: 'u1', status: 'CLOSED', rating: 5 }],
        total: 1,
      },
    })
    const SessionAuditPanel = (await import('../components/admin/SessionAuditPanel.vue')).default
    const wrapper = mount(SessionAuditPanel)

    await new Promise((r) => setTimeout(r, 0))
    await nextTick()

    expect(wrapper.text()).toContain('s1')
    expect(wrapper.text()).toContain('u1')
  })
})

describe('RoleManagementPanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders roleCode and roleName returned by the role API', async () => {
    const { listRoles } = await import('../api/admin-api')
    listRoles.mockResolvedValueOnce({
      data: { records: [{ id: 'role-agent', roleCode: 'AGENT', roleName: '客服' }], total: 1 },
    })
    const RoleManagementPanel = (await import('../components/admin/RoleManagementPanel.vue')).default
    const wrapper = mount(RoleManagementPanel)

    await vi.dynamicImportSettled()
    await nextTick()

    expect(wrapper.text()).toContain('AGENT')
    expect(wrapper.text()).toContain('客服')
  })

  it('renders permissionCode and permissionName returned by the permission API', async () => {
    const { listPermissions } = await import('../api/admin-api')
    listPermissions.mockResolvedValueOnce({
      data: { records: [{ id: 'perm-chat', permissionCode: 'chat:read', permissionName: '查看会话' }], total: 1 },
    })
    const RoleManagementPanel = (await import('../components/admin/RoleManagementPanel.vue')).default
    const wrapper = mount(RoleManagementPanel)

    await vi.dynamicImportSettled()
    await nextTick()

    expect(wrapper.text()).toContain('chat:read')
    expect(wrapper.text()).toContain('查看会话')
  })
})

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
  resetUserPassword: vi.fn(),
  findUserRoles: vi.fn(() => Promise.resolve({ data: [] })),
  assignRoleToUser: vi.fn(),
  removeRoleFromUser: vi.fn(),
  listRoles: vi.fn(() => Promise.resolve({ data: { records: [], total: 0 } })),
  createRole: vi.fn(),
  updateRole: vi.fn(),
  deleteRole: vi.fn(),
  findRolePermissions: vi.fn(() => Promise.resolve({ data: [] })),
  assignPermissionToRole: vi.fn(),
  removePermissionFromRole: vi.fn(),
  listPermissions: vi.fn(() => Promise.resolve({ data: { records: [], total: 0 } })),
  createPermission: vi.fn(),
  updatePermission: vi.fn(),
  deletePermission: vi.fn(),
  findDeadLetters: vi.fn(() => Promise.resolve({ data: { records: [], total: 0 } })),
  replayDeadLetter: vi.fn(() => Promise.resolve({ message: '重放成功' })),
  deleteDeadLetter: vi.fn(),
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
  findAdminDashboard: vi.fn(() => Promise.resolve({ data: {} })),
  findVipSkillAgents: vi.fn(() => Promise.resolve({ data: [] })),
  addVipSkill: vi.fn(),
  removeVipSkill: vi.fn(),
  findTransferLogs: vi.fn(() => Promise.resolve({ data: [] })),
  createAdminSession: vi.fn(),
  updateAdminSession: vi.fn(),
  deleteAdminSession: vi.fn(),
  searchAdminMessages: vi.fn(() => Promise.resolve({ data: { records: [], total: 0 } })),
  deleteAdminMessage: vi.fn(),
}))

// ─── Mock http-client ────────────────────────────────────────
vi.mock('../services/http-client', () => ({
  request: vi.fn(() => Promise.resolve({ data: { records: [], total: 0 } })),
}))

describe('AdminWorkspaceView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders vertical admin navigation and switches panels from the sidebar', async () => {
    const AdminWorkspaceView = (await import('../views/AdminWorkspaceView.vue')).default
    const wrapper = mount(AdminWorkspaceView, {
      global: {
        stubs: {
          AdminDashboard: { template: '<div class="stub-dashboard">仪表盘</div>' },
          UserManagementPanel: { template: '<div class="stub-users">用户管理</div>' },
          RoleManagementPanel: { template: '<div class="stub-roles">角色管理</div>' },
          SessionAuditPanel: { template: '<div class="stub-sessions">会话审计</div>' },
          AdminMessageSearchPanel: { template: '<div class="stub-message-search">消息搜索</div>' },
          ArchiveStatsPanel: { template: '<div class="stub-archive">归档统计</div>' },
          DeadLetterPanel: { template: '<div class="stub-deadletters">死信管理</div>' },
          VipSkillPanel: { template: '<div class="stub-vip">VIP技能组</div>' },
        },
      },
    })

    const sidebar = wrapper.get('aside.admin-sidebar')
    expect(sidebar.attributes('aria-label')).toBe('管理员功能导航')

    // Default panel should be dashboard
    expect(wrapper.find('.stub-dashboard').exists()).toBe(true)

    // Click "用户管理" in the sidebar
    const navigationItems = sidebar.findAll('.sidebar-item')
    await navigationItems[1].trigger('click')
    await nextTick()
    expect(wrapper.find('.stub-users').exists()).toBe(true)

    // Click "归档统计" tab
    await navigationItems.find((item) => item.text() === '归档统计').trigger('click')
    await nextTick()
    expect(wrapper.find('.stub-archive').exists()).toBe(true)

    // Click "死信管理" tab
    await navigationItems.find((item) => item.text() === '死信管理').trigger('click')
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

  it('offers a refresh action that reloads the session management list', async () => {
    const { request } = await import('../services/http-client')
    const SessionAuditPanel = (await import('../components/admin/SessionAuditPanel.vue')).default
    const wrapper = mount(SessionAuditPanel)
    await new Promise((resolve) => setTimeout(resolve, 0))
    await nextTick()
    const initialCalls = request.mock.calls.length

    await wrapper.get('button.refresh-sessions').trigger('click')

    expect(request.mock.calls.length).toBe(initialCalls + 1)
  })

  it('only offers ACTIVE and CLOSED in the status filter', async () => {
    const SessionAuditPanel = (await import('../components/admin/SessionAuditPanel.vue')).default
    const wrapper = mount(SessionAuditPanel)
    await nextTick()

    const statusOptions = wrapper.find('select[aria-label="Status"]').findAll('option')
    expect(statusOptions.map((option) => option.attributes('value'))).toEqual(['', 'ACTIVE', 'CLOSED'])
  })

  it('searches sessions by user and agent login numbers', async () => {
    const { request } = await import('../services/http-client')
    const SessionAuditPanel = (await import('../components/admin/SessionAuditPanel.vue')).default
    const wrapper = mount(SessionAuditPanel)
    await nextTick()
    request.mockClear()

    const inputs = wrapper.findAll('.filters input')
    await inputs[0].setValue('user004')
    await inputs[1].setValue('agent001')
    await wrapper.findAll('.filters button')[0].trigger('click')

    const requestUrl = new URL(request.mock.calls.at(-1)[0], 'http://localhost')
    expect(requestUrl.searchParams.get('userLoginNumber')).toBe('user004')
    expect(requestUrl.searchParams.get('agentLoginNumber')).toBe('agent001')
    expect(wrapper.text()).not.toContain('用户 ID')
    expect(wrapper.text()).not.toContain('客服 ID')
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
        records: [{ sessionId: 's1', username: 'user004', agentUsername: 'agent001', status: 'CLOSED', rating: 5 }],
        total: 1,
      },
    })
    const SessionAuditPanel = (await import('../components/admin/SessionAuditPanel.vue')).default
    const wrapper = mount(SessionAuditPanel)

    await new Promise((r) => setTimeout(r, 0))
    await nextTick()

    expect(wrapper.text()).toContain('新咨询')
    expect(wrapper.text()).not.toContain('会话 ID')
    expect(wrapper.text()).toContain('user004')
  })

  it('renders Element Plus pagination and reloads the selected audit page', async () => {
    const { request } = await import('../services/http-client')
    request.mockResolvedValue({
      data: { records: [], total: 41 },
    })
    const SessionAuditPanel = (await import('../components/admin/SessionAuditPanel.vue')).default
    const wrapper = mount(SessionAuditPanel)

    await new Promise((r) => setTimeout(r, 0))
    await nextTick()
    request.mockClear()

    const pagination = wrapper.findComponent({ name: 'ElPagination' })
    expect(pagination.exists()).toBe(true)
    expect(pagination.props('pageSize')).toBe(20)
    expect(pagination.props('total')).toBe(41)

    pagination.vm.$emit('current-change', 2)
    await nextTick()
    expect(request).toHaveBeenCalledWith(expect.stringContaining('pageNo=2'))
  })
})

describe('AdminDashboard', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('offers a refresh action for dashboard data', async () => {
    const { findAdminDashboard } = await import('../api/admin-api')
    findAdminDashboard.mockResolvedValue({ data: {} })
    const AdminDashboard = (await import('../components/admin/AdminDashboard.vue')).default
    const wrapper = mount(AdminDashboard)
    await new Promise((resolve) => setTimeout(resolve, 0))
    await nextTick()

    const initialCalls = findAdminDashboard.mock.calls.length
    await wrapper.get('button.refresh-dashboard').trigger('click')
    expect(findAdminDashboard.mock.calls.length).toBe(initialCalls + 1)
  })
})

describe('RoleManagementPanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('offers a refresh action for role and permission data', async () => {
    const { listRoles, listPermissions } = await import('../api/admin-api')
    listRoles.mockResolvedValue({ data: { records: [], total: 0 } })
    listPermissions.mockResolvedValue({ data: { records: [], total: 0 } })
    const RoleManagementPanel = (await import('../components/admin/RoleManagementPanel.vue')).default
    const wrapper = mount(RoleManagementPanel)
    await new Promise((resolve) => setTimeout(resolve, 0))
    await nextTick()

    const initialRoleCalls = listRoles.mock.calls.length
    const initialPermissionCalls = listPermissions.mock.calls.length
    await wrapper.get('button.refresh-roles').trigger('click')
    expect(listRoles.mock.calls.length).toBe(initialRoleCalls + 1)
    expect(listPermissions.mock.calls.length).toBe(initialPermissionCalls + 1)
  })

  it('offers a permission create action when permission management is available', async () => {
    const RoleManagementPanel = (await import('../components/admin/RoleManagementPanel.vue')).default
    const wrapper = mount(RoleManagementPanel)
    await nextTick()

    expect(wrapper.text()).toContain('新增权限')
  })

  it('selects a role from the dropdown and loads its permissions', async () => {
    const { listRoles, findRolePermissions } = await import('../api/admin-api')
    listRoles.mockResolvedValueOnce({
      data: { records: [{ id: 'role-agent', roleCode: 'AGENT', roleName: '客服' }], total: 1 },
    })
    findRolePermissions.mockResolvedValueOnce({ data: [] })
    const RoleManagementPanel = (await import('../components/admin/RoleManagementPanel.vue')).default
    const wrapper = mount(RoleManagementPanel)

    await vi.dynamicImportSettled()
    await nextTick()

    const roleSelect = wrapper.findComponent({ name: 'ElSelect' })
    expect(roleSelect.classes()).toContain('role-selector')
    expect(roleSelect.props('filterable')).toBe(false)

    roleSelect.vm.$emit('update:modelValue', 'role-agent')
    await nextTick()
    roleSelect.vm.$emit('change', 'role-agent')
    await nextTick()
    await vi.dynamicImportSettled()
    await nextTick()

    expect(findRolePermissions).toHaveBeenCalledWith('role-agent')
    expect(roleSelect.props('modelValue')).toBe('role-agent')
    expect(roleSelect.find('.el-select__placeholder').text()).toBe('客服（AGENT）')
    expect(wrapper.text()).not.toContain('当前角色: AGENT')
  })

  it('clears the selected role without requesting permissions for an empty value', async () => {
    const { listRoles, findRolePermissions } = await import('../api/admin-api')
    listRoles.mockResolvedValueOnce({
      data: { records: [{ id: 'role-agent', roleCode: 'AGENT', roleName: '客服' }], total: 1 },
    })
    const RoleManagementPanel = (await import('../components/admin/RoleManagementPanel.vue')).default
    const wrapper = mount(RoleManagementPanel)

    await vi.dynamicImportSettled()
    await nextTick()

    const roleSelect = wrapper.findComponent({ name: 'ElSelect' })
    roleSelect.vm.$emit('change', undefined)
    await nextTick()

    expect(findRolePermissions).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('请选择需要维护的角色')
  })

  it('places role CRUD controls beside the selector and leaves only refresh in the header', async () => {
    const RoleManagementPanel = (await import('../components/admin/RoleManagementPanel.vue')).default
    const wrapper = mount(RoleManagementPanel)
    await vi.dynamicImportSettled()
    await nextTick()

    expect(wrapper.get('.panel-actions').text()).toBe('刷新')

    const roleToolbar = wrapper.get('.role-management-toolbar')
    expect(roleToolbar.findComponent({ name: 'ElSelect' }).exists()).toBe(true)
    expect(roleToolbar.text()).toContain('新增角色')
    expect(roleToolbar.text()).toContain('编辑角色')
    expect(roleToolbar.text()).toContain('删除角色')
    expect(roleToolbar.findAll('button').filter((button) => button.text() === '编辑角色')[0].attributes('disabled')).toBeDefined()
    expect(roleToolbar.findAll('button').filter((button) => button.text() === '删除角色')[0].attributes('disabled')).toBeDefined()
  })
})

describe('AdminMessageSearchPanel', () => {
  it('paginates search results and reloads the selected page', async () => {
    const { searchAdminMessages } = await import('../api/admin-api')
    searchAdminMessages.mockResolvedValue({
      data: {
        records: [{ messageId: 'm1', content: '测试消息' }],
        total: 21,
      },
    })
    const AdminMessageSearchPanel = (await import('../components/admin/AdminMessageSearchPanel.vue')).default
    const wrapper = mount(AdminMessageSearchPanel)

    await wrapper.find('.el-input__inner').setValue('测试')
    await wrapper.find('.el-button--primary').trigger('click')
    await nextTick()

    const pagination = wrapper.findComponent({ name: 'ElPagination' })
    expect(pagination.exists()).toBe(true)
    expect(pagination.props('pageSize')).toBe(20)
    expect(searchAdminMessages).toHaveBeenLastCalledWith({ keyword: '测试', pageNo: 1, pageSize: 20 })

    pagination.vm.$emit('current-change', 2)
    await nextTick()
    expect(searchAdminMessages).toHaveBeenLastCalledWith({ keyword: '测试', pageNo: 2, pageSize: 20 })
  })
})

describe('VipSkillPanel', () => {
  it('paginates a large VIP agent list on the client', async () => {
    const { findVipSkillAgents } = await import('../api/admin-api')
    findVipSkillAgents.mockResolvedValue({
      data: Array.from({ length: 21 }, (_, index) => `agent-${index + 1}`),
    })
    const VipSkillPanel = (await import('../components/admin/VipSkillPanel.vue')).default
    const wrapper = mount(VipSkillPanel)

    await new Promise((r) => setTimeout(r, 0))
    await nextTick()

    const pagination = wrapper.findComponent({ name: 'ElPagination' })
    expect(pagination.exists()).toBe(true)
    expect(wrapper.text()).toContain('agent-1')
    expect(wrapper.text()).not.toContain('agent-21')
    pagination.vm.$emit('current-change', 2)
    await nextTick()
    expect(wrapper.text()).toContain('agent-21')
  })
})

describe('RoleManagementPanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('loads roleCode and roleName into the role selector', async () => {
    const { listRoles } = await import('../api/admin-api')
    listRoles.mockResolvedValueOnce({
      data: { records: [{ id: 'role-agent', roleCode: 'AGENT', roleName: '客服' }], total: 1 },
    })
    const RoleManagementPanel = (await import('../components/admin/RoleManagementPanel.vue')).default
    const wrapper = mount(RoleManagementPanel)

    await vi.dynamicImportSettled()
    await nextTick()

    const roleSelect = wrapper.findComponent({ name: 'ElSelect' })
    const options = roleSelect.findAllComponents({ name: 'ElOption' })
    expect(options).toHaveLength(1)
    expect(options[0].props('label')).toBe('客服（AGENT）')
    expect(options[0].props('value')).toBe('role-agent')
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

describe('UserManagementPanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('offers a refresh action that reloads the user list', async () => {
    const { listUsers } = await import('../api/admin-api')
    listUsers.mockResolvedValue({ data: { records: [], total: 0 } })
    const UserManagementPanel = (await import('../components/admin/UserManagementPanel.vue')).default
    const wrapper = mount(UserManagementPanel)

    await new Promise((resolve) => setTimeout(resolve, 0))
    await nextTick()
    const initialCalls = listUsers.mock.calls.length
    await wrapper.get('button.refresh-users').trigger('click')

    expect(listUsers.mock.calls.length).toBe(initialCalls + 1)
    expect(wrapper.text()).toContain('刷新')
  })

  it('queries users by login number or nickname from the first page', async () => {
    const { listUsers } = await import('../api/admin-api')
    listUsers.mockResolvedValue({ data: { records: [], total: 0 } })
    const UserManagementPanel = (await import('../components/admin/UserManagementPanel.vue')).default
    const wrapper = mount(UserManagementPanel)

    await new Promise((resolve) => setTimeout(resolve, 0))
    await wrapper.get('.user-search-input input').setValue('agent001')
    await wrapper.get('button.search-users').trigger('click')

    expect(listUsers).toHaveBeenLastCalledWith({ pageNo: 1, pageSize: 20, keyword: 'agent001' })
  })

  it('confirms the fixed administrator password reset instead of accepting a password input', async () => {
    const { listUsers, resetUserPassword } = await import('../api/admin-api')
    listUsers.mockResolvedValue({
      data: { records: [{ id: 'user-1', username: 'zhangsan', nickname: '张三' }], total: 1 },
    })
    const UserManagementPanel = (await import('../components/admin/UserManagementPanel.vue')).default
    const wrapper = mount(UserManagementPanel)

    await vi.dynamicImportSettled()
    await nextTick()
    const resetButton = wrapper.findAll('button').find((button) => button.text() === '重置密码')
    await resetButton.trigger('click')

    expect(wrapper.text()).toContain('12345678')
    const confirmButton = wrapper.findAll('button').find((button) => button.text() === '确认重置')
    await confirmButton.trigger('click')

    expect(resetUserPassword).toHaveBeenCalledWith('user-1')
  })
})

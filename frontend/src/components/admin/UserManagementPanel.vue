<script setup>
import { ref, onMounted, watch } from 'vue'
import {
  listUsers,
  createUser,
  updateUser,
  deleteUser,
  resetUserPassword,
  listRoles,
  findUserRoles,
  assignRoleToUser,
  removeRoleFromUser,
} from '../../api/admin-api'

const loading = ref(false)
const error = ref(null)
const users = ref([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)
const keyword = ref('')

// Dialog state
const showDialog = ref(false)
const dialogMode = ref('create') // 'create' | 'edit'
const formData = ref(emptyForm())
const formError = ref(null)

// Password dialog
const showPasswordDialog = ref(false)
const passwordUser = ref(null)
const passwordError = ref(null)

// Delete confirmation
const showDeleteConfirm = ref(false)
const deleteUserId = ref(null)

const showRoleDialog = ref(false)
const roleUser = ref(null)
const roles = ref([])
const selectedRoleCodes = ref([])
const originalRoleCodes = ref([])
const roleLoading = ref(false)
const roleError = ref(null)

function emptyForm() {
  return {
    username: '',
    nickname: '',
    status: 'ENABLED',
    vipLevel: 0,
    password: '',
  }
}

async function loadUsers() {
  loading.value = true
  error.value = null
  try {
    const params = { pageNo: pageNo.value, pageSize: pageSize.value }
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    const res = await listUsers(params)
    users.value = res.data?.records ?? []
    total.value = res.data?.total ?? 0
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

onMounted(loadUsers)
watch(pageNo, loadUsers)

function openCreate() {
  dialogMode.value = 'create'
  formData.value = emptyForm()
  formError.value = null
  showDialog.value = true
}

function openEdit(user) {
  dialogMode.value = 'edit'
  formData.value = {
    username: user.username,
    nickname: user.nickname || '',
    status: user.status || 'ENABLED',
    vipLevel: user.vipLevel || 0,
    password: '',
  }
  formError.value = null
  showDialog.value = true
  // Store id on the form for submission
  formData.value._id = user.id
}

async function submitForm() {
  formError.value = null
  try {
    if (dialogMode.value === 'create') {
      const payload = {
        username: formData.value.username,
        nickname: formData.value.nickname,
        status: formData.value.status,
        vipLevel: formData.value.vipLevel,
      }
      if (formData.value.password) payload.password = formData.value.password
      await createUser(payload)
    } else {
      const payload = {
        nickname: formData.value.nickname,
        status: formData.value.status,
        vipLevel: formData.value.vipLevel,
      }
      await updateUser(formData.value._id, payload)
    }
    showDialog.value = false
    await loadUsers()
  } catch (e) {
    formError.value = e.message
  }
}

function confirmDelete(userId) {
  deleteUserId.value = userId
  showDeleteConfirm.value = true
}

async function executeDelete() {
  try {
    await deleteUser(deleteUserId.value)
    showDeleteConfirm.value = false
    await loadUsers()
  } catch (e) {
    error.value = e.message
  }
}

function openPasswordDialog(user) {
  passwordUser.value = user
  passwordError.value = null
  showPasswordDialog.value = true
}

async function resetPassword() {
  passwordError.value = null
  try {
    await resetUserPassword(passwordUser.value.id)
    showPasswordDialog.value = false
  } catch (e) {
    passwordError.value = e.message
  }
}

async function searchUsers() {
  if (pageNo.value === 1) {
    await loadUsers()
  } else {
    pageNo.value = 1
  }
}

async function openRoleDialog(user) {
  roleUser.value = user
  roleError.value = null
  roleLoading.value = true
  showRoleDialog.value = true
  try {
    const [roleResult, userRoleResult] = await Promise.all([
      listRoles({ pageNo: 1, pageSize: 100 }),
      findUserRoles(user.id),
    ])
    roles.value = roleResult.data?.records ?? []
    originalRoleCodes.value = [...(userRoleResult.data ?? [])]
    selectedRoleCodes.value = [...originalRoleCodes.value]
  } catch (e) {
    roleError.value = e.message
  } finally {
    roleLoading.value = false
  }
}

async function saveRoles() {
  if (!roleUser.value) return
  roleError.value = null
  roleLoading.value = true
  try {
    const previous = new Set(originalRoleCodes.value)
    const selected = new Set(selectedRoleCodes.value)
    const additions = roles.value.filter((role) => selected.has(role.roleCode) && !previous.has(role.roleCode))
    const removals = roles.value.filter((role) => !selected.has(role.roleCode) && previous.has(role.roleCode))
    await Promise.all([
      ...additions.map((role) => assignRoleToUser(roleUser.value.id, role.id)),
      ...removals.map((role) => removeRoleFromUser(roleUser.value.id, role.id)),
    ])
    roleUser.value.roles = [...selected]
    showRoleDialog.value = false
    await loadUsers()
  } catch (e) {
    roleError.value = e.message
  } finally {
    roleLoading.value = false
  }
}

const totalPages = () => Math.max(1, Math.ceil(total.value / pageSize.value))
</script>

<template>
  <section class="user-panel">
    <div class="panel-header">
      <h2>用户管理</h2>
      <div class="panel-actions">
        <div class="user-query">
          <el-input
            v-model="keyword"
            class="user-search-input"
            clearable
            placeholder="搜索登录编号或昵称"
            @keyup.enter="searchUsers"
          />
          <el-button class="btn search-users" :disabled="loading" @click="searchUsers">查询</el-button>
        </div>
        <button class="btn refresh-users" :disabled="loading" @click="loadUsers">刷新</button>
        <button class="btn btn-primary" @click="openCreate">+ 新增用户</button>
      </div>
    </div>

    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="error" class="error">{{ error }}</div>
    <template v-else>
      <el-table v-loading="loading" class="data-table" :data="users" row-key="id">
        <el-table-column prop="username" label="登录编号" />
        <el-table-column label="昵称">
          <template #default="{ row }">{{ row.nickname || '-' }}</template>
        </el-table-column>
        <el-table-column label="角色" width="110">
          <template #default="{ row }">
            <span v-if="row.roles?.length" class="role-list" :title="row.roles.join('、')">{{ row.roles.join('、') }}</span>
            <span v-else class="muted">未分配</span>
          </template>
        </el-table-column>
        <el-table-column label="状态">
          <template #default="{ row }">
            <span :class="['status-badge', row.status === 'ENABLED' ? 'enabled' : 'disabled']">
              {{ row.status === 'ENABLED' ? '启用' : '禁用' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="vipLevel" label="VIP 等级">
          <template #default="{ row }">{{ row.vipLevel ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="220">
          <template #default="{ row }">
            <div class="actions">
              <el-button class="btn btn-sm" size="small" @click="openEdit(row)">编辑</el-button>
              <el-button class="btn btn-sm" size="small" @click="openRoleDialog(row)">分配角色</el-button>
              <el-button class="btn btn-sm" size="small" @click="openPasswordDialog(row)">重置密码</el-button>
              <el-button class="btn btn-sm btn-danger" size="small" @click="confirmDelete(row.id)">删除</el-button>
            </div>
          </template>
        </el-table-column>
        <template #empty><div class="empty">暂无数据</div></template>
      </el-table>

      <el-pagination
        class="pagination"
        layout="prev, slot, next"
        :current-page="pageNo"
        :page-size="pageSize"
        :total="total"
        :disabled="loading"
        @current-change="pageNo = $event"
      >
        <span>{{ pageNo }} / {{ totalPages() }}</span>
      </el-pagination>
    </template>

    <el-dialog v-model="showDialog" class="dialog" :title="dialogMode === 'create' ? '新增用户' : '编辑用户'" width="420px">
      <div v-if="formError" class="error">{{ formError }}</div>
      <el-form label-position="top" @submit.prevent="submitForm">
        <el-form-item v-if="dialogMode === 'create'" label="登录编号">
          <el-input v-model="formData.username" required maxlength="64" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="formData.nickname" maxlength="64" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="formData.status">
            <el-option label="启用" value="ENABLED" />
            <el-option label="禁用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="VIP 等级">
          <el-input-number v-model="formData.vipLevel" :min="0" :max="99" />
        </el-form-item>
        <el-form-item v-if="dialogMode === 'create'" label="密码">
          <el-input v-model="formData.password" type="password" show-password maxlength="128" />
        </el-form-item>
        <div class="dialog-actions">
          <el-button class="btn" @click="showDialog = false">取消</el-button>
          <el-button class="btn btn-primary" native-type="submit">确定</el-button>
        </div>
      </el-form>
    </el-dialog>

    <el-dialog v-model="showPasswordDialog" class="dialog" title="强制重置密码" width="420px">
      <div v-if="passwordError" class="error">{{ passwordError }}</div>
      <p>确定将“{{ passwordUser?.nickname || passwordUser?.username }}”的密码重置为 <strong>12345678</strong> 吗？</p>
      <div class="dialog-actions">
        <el-button class="btn" @click="showPasswordDialog = false">取消</el-button>
        <el-button class="btn btn-primary" @click="resetPassword">确认重置</el-button>
      </div>
    </el-dialog>

    <el-dialog v-model="showRoleDialog" class="dialog" title="分配用户角色" width="460px">
      <div v-if="roleError" class="error">{{ roleError }}</div>
      <div v-loading="roleLoading" class="role-dialog-body">
        <p class="role-target">账号：{{ roleUser?.nickname || '未设置昵称' }}</p>
        <el-checkbox-group v-model="selectedRoleCodes" class="role-options">
          <el-checkbox v-for="role in roles" :key="role.id" :value="role.roleCode">
            {{ role.roleCode }}<span v-if="role.roleName">（{{ role.roleName }}）</span>
          </el-checkbox>
        </el-checkbox-group>
      </div>
      <template #footer>
        <div class="dialog-actions">
          <el-button class="btn" @click="showRoleDialog = false">取消</el-button>
          <el-button class="btn btn-primary" :loading="roleLoading" @click="saveRoles">保存角色</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="showDeleteConfirm" class="dialog" title="确认删除" width="420px">
      <p>确定要删除该用户吗？此操作不可撤销。</p>
      <template #footer>
        <div class="dialog-actions">
          <el-button class="btn" @click="showDeleteConfirm = false">取消</el-button>
          <el-button class="btn btn-danger" @click="executeDelete">删除</el-button>
        </div>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.user-panel {
  padding: 1rem;
  width: 100%;
  box-sizing: border-box;
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}
.data-table {
  width: 100%;
  border-collapse: collapse;
}
.data-table th,
.data-table td {
  padding: 0.5rem 0.75rem;
  border-bottom: 1px solid #e5e7eb;
  text-align: left;
}
.data-table thead {
  background: #f9fafb;
}
.status-badge {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 0.85rem;
}
.status-badge.enabled {
  background: #dcfce7;
  color: #166534;
}
.status-badge.disabled {
  background: #fee2e2;
  color: #991b1b;
}
.actions {
  display: flex;
  gap: 0.5rem;
  white-space: nowrap;
}
.empty {
  text-align: center;
  color: #888;
  padding: 2rem 0;
}
.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 1rem;
  margin-top: 1rem;
}
.btn {
  padding: 0.4rem 0.75rem;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
  font-size: 0.9rem;
}
.btn:hover {
  background: #f3f4f6;
}
.btn-primary {
  background: #2563eb;
  color: #fff;
  border-color: #2563eb;
}
.btn-primary:hover {
  background: #1d4ed8;
}
.btn-danger {
  background: #dc2626;
  color: #fff;
  border-color: #dc2626;
}
.btn-danger:hover {
  background: #b91c1c;
}
.btn-sm {
  padding: 0.25rem 0.5rem;
  font-size: 0.8rem;
}
.loading, .error {
  padding: 1rem;
}
.error {
  color: #dc2626;
}
.panel-actions { display: flex; align-items: center; gap: 0.5rem; }
.user-query { display: flex; gap: 0.5rem; }
.user-search-input { width: 220px; }
.muted { color: #888; }
.role-list { display: inline-block; max-width: 90px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; vertical-align: bottom; }
.role-dialog-body { min-height: 80px; }
.role-target { margin: 0 0 14px; color: var(--color-muted); }
.role-options { display: grid; gap: 10px; }
.dialog-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}
.dialog {
  background: #fff;
  border-radius: 8px;
  padding: 1.5rem;
  min-width: 320px;
  max-width: 480px;
}
.dialog h3 {
  margin-top: 0;
  margin-bottom: 1rem;
}
.dialog label {
  display: block;
  margin-bottom: 0.75rem;
}
.dialog input,
.dialog select {
  display: block;
  width: 100%;
  margin-top: 0.25rem;
  padding: 0.4rem;
  border: 1px solid #d1d5db;
  border-radius: 4px;
}
.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  margin-top: 1rem;
}
.data-table { width: 100%; }
.data-table :deep(.el-table__header-wrapper th.el-table__cell) { padding: 0.5rem 0.75rem; background: #f9fafb; color: inherit; font-weight: 600; }
.data-table :deep(.el-table__body-wrapper td.el-table__cell) { padding: 0.5rem 0.75rem; }
.data-table :deep(.el-table__body-wrapper .cell) { white-space: nowrap; }
.data-table :deep(.el-table__inner-wrapper::before) { background-color: #e5e7eb; }
.data-table :deep(.el-table__empty-text) { color: #888; }
.pagination :deep(.el-pagination__total), .pagination :deep(.btn-prev), .pagination :deep(.btn-next), .pagination :deep(.el-pager li) { font-size: 0.9rem; }
.pagination :deep(.el-pagination__total) { margin: 0; }
.dialog :deep(.el-dialog) { border: 1px solid var(--color-line); border-radius: 8px; }
.dialog :deep(.el-dialog__header) { margin: 0; padding: 1.5rem 1.5rem 1rem; }
.dialog :deep(.el-dialog__title) { font-size: 1.1rem; font-weight: 600; }
.dialog :deep(.el-dialog__body) { padding: 0 1.5rem 1rem; }
.dialog :deep(.el-dialog__footer) { padding: 0 1.5rem 1.5rem; }
.dialog :deep(.el-form-item) { margin-bottom: 0.75rem; }
.dialog :deep(.el-form-item__label) { margin-bottom: 0.25rem; }
.dialog :deep(.el-select), .dialog :deep(.el-input-number) { width: 100%; }
.dialog-actions :deep(.el-button) { min-height: 32px; }
</style>

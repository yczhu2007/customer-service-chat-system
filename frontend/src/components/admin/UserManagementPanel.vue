<script setup>
import { ref, onMounted, watch } from 'vue'
import {
  listUsers,
  createUser,
  updateUser,
  deleteUser,
  updateUserPassword,
} from '../../api/admin-api'

const loading = ref(false)
const error = ref(null)
const users = ref([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)

// Dialog state
const showDialog = ref(false)
const dialogMode = ref('create') // 'create' | 'edit'
const formData = ref(emptyForm())
const formError = ref(null)

// Password dialog
const showPasswordDialog = ref(false)
const passwordUserId = ref(null)
const passwordForm = ref({ newPassword: '' })
const passwordError = ref(null)

// Delete confirmation
const showDeleteConfirm = ref(false)
const deleteUserId = ref(null)

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
    const res = await listUsers({ pageNo: pageNo.value, pageSize: pageSize.value })
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

function openPasswordDialog(userId) {
  passwordUserId.value = userId
  passwordForm.value = { newPassword: '' }
  passwordError.value = null
  showPasswordDialog.value = true
}

async function submitPassword() {
  passwordError.value = null
  try {
    await updateUserPassword(passwordUserId.value, passwordForm.value)
    showPasswordDialog.value = false
  } catch (e) {
    passwordError.value = e.message
  }
}

const totalPages = () => Math.max(1, Math.ceil(total.value / pageSize.value))
</script>

<template>
  <section class="user-panel">
    <div class="panel-header">
      <h2>用户管理</h2>
      <button class="btn btn-primary" @click="openCreate">+ 新增用户</button>
    </div>

    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="error" class="error">{{ error }}</div>
    <template v-else>
      <table class="data-table">
        <thead>
          <tr>
            <th>用户名</th>
            <th>昵称</th>
            <th>状态</th>
            <th>VIP 等级</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="user in users" :key="user.id">
            <td>{{ user.username }}</td>
            <td>{{ user.nickname || '-' }}</td>
            <td>
              <span :class="['status-badge', user.status === 'ENABLED' ? 'enabled' : 'disabled']">
                {{ user.status === 'ENABLED' ? '启用' : '禁用' }}
              </span>
            </td>
            <td>{{ user.vipLevel ?? 0 }}</td>
            <td class="actions">
              <button class="btn btn-sm" @click="openEdit(user)">编辑</button>
              <button class="btn btn-sm" @click="openPasswordDialog(user.id)">密码</button>
              <button class="btn btn-sm btn-danger" @click="confirmDelete(user.id)">删除</button>
            </td>
          </tr>
          <tr v-if="users.length === 0">
            <td colspan="5" class="empty">暂无数据</td>
          </tr>
        </tbody>
      </table>

      <div class="pagination">
        <button :disabled="pageNo <= 1" @click="pageNo--">上一页</button>
        <span>{{ pageNo }} / {{ totalPages() }}</span>
        <button :disabled="pageNo >= totalPages()" @click="pageNo++">下一页</button>
      </div>
    </template>

    <!-- Create/Edit Dialog -->
    <div v-if="showDialog" class="dialog-overlay" @click.self="showDialog = false">
      <div class="dialog">
        <h3>{{ dialogMode === 'create' ? '新增用户' : '编辑用户' }}</h3>
        <div v-if="formError" class="error">{{ formError }}</div>
        <form @submit.prevent="submitForm">
          <label v-if="dialogMode === 'create'">
            用户名
            <input v-model="formData.username" required maxlength="64" />
          </label>
          <label>
            昵称
            <input v-model="formData.nickname" maxlength="64" />
          </label>
          <label>
            状态
            <select v-model="formData.status">
              <option value="ENABLED">启用</option>
              <option value="DISABLED">禁用</option>
            </select>
          </label>
          <label>
            VIP 等级
            <input v-model.number="formData.vipLevel" type="number" min="0" max="99" />
          </label>
          <label v-if="dialogMode === 'create'">
            密码
            <input v-model="formData.password" type="password" maxlength="128" />
          </label>
          <div class="dialog-actions">
            <button type="button" class="btn" @click="showDialog = false">取消</button>
            <button type="submit" class="btn btn-primary">确定</button>
          </div>
        </form>
      </div>
    </div>

    <!-- Password Dialog -->
    <div v-if="showPasswordDialog" class="dialog-overlay" @click.self="showPasswordDialog = false">
      <div class="dialog">
        <h3>修改密码</h3>
        <div v-if="passwordError" class="error">{{ passwordError }}</div>
        <form @submit.prevent="submitPassword">
          <label>
            新密码
            <input v-model="passwordForm.newPassword" type="password" required maxlength="128" />
          </label>
          <div class="dialog-actions">
            <button type="button" class="btn" @click="showPasswordDialog = false">取消</button>
            <button type="submit" class="btn btn-primary">确定</button>
          </div>
        </form>
      </div>
    </div>

    <!-- Delete Confirmation -->
    <div v-if="showDeleteConfirm" class="dialog-overlay" @click.self="showDeleteConfirm = false">
      <div class="dialog">
        <h3>确认删除</h3>
        <p>确定要删除该用户吗？此操作不可撤销。</p>
        <div class="dialog-actions">
          <button class="btn" @click="showDeleteConfirm = false">取消</button>
          <button class="btn btn-danger" @click="executeDelete">删除</button>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.user-panel {
  padding: 1rem;
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
</style>

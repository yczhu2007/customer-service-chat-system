<script setup>
import { ref, onMounted, watch } from 'vue'
import {
  listRoles,
  createRole,
  updateRole,
  deleteRole,
  findRolePermissions,
  assignPermissionToRole,
  removePermissionFromRole,
  listPermissions,
} from '../../api/admin-api'

// ─── Roles ──────────────────────────────────────────────────
const loading = ref(false)
const error = ref(null)
const roles = ref([])
const totalRoles = ref(0)
const rolePageNo = ref(1)
const rolePageSize = ref(20)

// ─── Permissions ────────────────────────────────────────────
const permissionsLoading = ref(false)
const permissions = ref([])
const totalPermissions = ref(0)
const permPageNo = ref(1)
const permPageSize = ref(20)

// ─── Role-Permission assignment ─────────────────────────────
const selectedRoleId = ref(null)
const selectedRolePerms = ref(new Set())
const permAssignLoading = ref(false)

// ─── Dialogs ────────────────────────────────────────────────
const showRoleDialog = ref(false)
const roleDialogMode = ref('create')
const roleForm = ref({ code: '', name: '', description: '' })
const roleFormError = ref(null)
const showDeleteConfirm = ref(false)
const deleteRoleId = ref(null)

async function loadRoles() {
  loading.value = true
  error.value = null
  try {
    const res = await listRoles({ pageNo: rolePageNo.value, pageSize: rolePageSize.value })
    roles.value = res.data?.records ?? []
    totalRoles.value = res.data?.total ?? 0
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

async function loadPermissions() {
  permissionsLoading.value = true
  try {
    const res = await listPermissions({ pageNo: permPageNo.value, pageSize: permPageSize.value })
    permissions.value = res.data?.records ?? []
    totalPermissions.value = res.data?.total ?? 0
  } catch (e) {
    error.value = e.message
  } finally {
    permissionsLoading.value = false
  }
}

async function loadRolePermissions(roleId) {
  selectedRoleId.value = roleId
  try {
    const res = await findRolePermissions(roleId)
    selectedRolePerms.value = new Set(res.data ?? [])
  } catch (e) {
    error.value = e.message
  }
}

onMounted(async () => {
  await Promise.all([loadRoles(), loadPermissions()])
})
watch(rolePageNo, loadRoles)
watch(permPageNo, loadPermissions)

function openCreateRole() {
  roleDialogMode.value = 'create'
  roleForm.value = { code: '', name: '', description: '' }
  roleFormError.value = null
  showRoleDialog.value = true
}

function openEditRole(role) {
  roleDialogMode.value = 'edit'
  roleForm.value = { code: role.code || '', name: role.name || '', description: role.description || '' }
  roleForm.value._id = role.id
  roleFormError.value = null
  showRoleDialog.value = true
}

async function submitRoleForm() {
  roleFormError.value = null
  try {
    if (roleDialogMode.value === 'create') {
      await createRole({ code: roleForm.value.code, name: roleForm.value.name, description: roleForm.value.description })
    } else {
      await updateRole(roleForm.value._id, { name: roleForm.value.name, description: roleForm.value.description })
    }
    showRoleDialog.value = false
    await loadRoles()
  } catch (e) {
    roleFormError.value = e.message
  }
}

function confirmDeleteRole(id) {
  deleteRoleId.value = id
  showDeleteConfirm.value = true
}

async function executeDeleteRole() {
  try {
    await deleteRole(deleteRoleId.value)
    showDeleteConfirm.value = false
    if (selectedRoleId.value === deleteRoleId.value) {
      selectedRoleId.value = null
      selectedRolePerms.value = new Set()
    }
    await loadRoles()
  } catch (e) {
    error.value = e.message
  }
}

async function togglePermission(permId) {
  if (!selectedRoleId.value) return
  permAssignLoading.value = true
  try {
    if (selectedRolePerms.value.has(permId)) {
      await removePermissionFromRole(selectedRoleId.value, permId)
      selectedRolePerms.value.delete(permId)
    } else {
      await assignPermissionToRole(selectedRoleId.value, permId)
      selectedRolePerms.value.add(permId)
    }
    // Force reactivity
    selectedRolePerms.value = new Set(selectedRolePerms.value)
  } catch (e) {
    error.value = e.message
  } finally {
    permAssignLoading.value = false
  }
}

const roleTotalPages = () => Math.max(1, Math.ceil(totalRoles.value / rolePageSize.value))
const permTotalPages = () => Math.max(1, Math.ceil(totalPermissions.value / permPageSize.value))
</script>

<template>
  <section class="role-panel">
    <div class="panel-header">
      <h2>角色管理</h2>
      <button class="btn btn-primary" @click="openCreateRole">+ 新增角色</button>
    </div>

    <div v-if="error" class="error">{{ error }}</div>

    <div class="two-col">
      <!-- Role List -->
      <div class="col">
        <h3>角色列表</h3>
        <div v-if="loading" class="loading">加载中...</div>
        <table v-else class="data-table">
          <thead>
            <tr>
              <th>编码</th>
              <th>名称</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="role in roles"
              :key="role.id"
              :class="{ selected: selectedRoleId === role.id }"
              @click="loadRolePermissions(role.id)"
            >
              <td>{{ role.code }}</td>
              <td>{{ role.name }}</td>
              <td class="actions">
                <button class="btn btn-sm" @click.stop="openEditRole(role)">编辑</button>
                <button class="btn btn-sm btn-danger" @click.stop="confirmDeleteRole(role.id)">删除</button>
              </td>
            </tr>
            <tr v-if="roles.length === 0">
              <td colspan="3" class="empty">暂无数据</td>
            </tr>
          </tbody>
        </table>
        <div class="pagination">
          <button :disabled="rolePageNo <= 1" @click="rolePageNo--">上一页</button>
          <span>{{ rolePageNo }} / {{ roleTotalPages() }}</span>
          <button :disabled="rolePageNo >= roleTotalPages()" @click="rolePageNo++">下一页</button>
        </div>
      </div>

      <!-- Permission List / Assignment -->
      <div class="col">
        <h3>权限管理</h3>
        <p v-if="selectedRoleId" class="hint">
          点击勾选为选中角色分配权限（当前角色: {{ roles.find(r => r.id === selectedRoleId)?.code }})
        </p>
        <p v-else class="hint">请先点击左侧角色以查看/编辑其权限</p>
        <div v-if="permissionsLoading" class="loading">加载中...</div>
        <table v-else class="data-table">
          <thead>
            <tr>
              <th style="width:40px">✓</th>
              <th>编码</th>
              <th>名称</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="perm in permissions" :key="perm.id">
              <td>
                <input
                  type="checkbox"
                  :checked="selectedRolePerms.has(perm.id)"
                  :disabled="!selectedRoleId || permAssignLoading"
                  @change="togglePermission(perm.id)"
                />
              </td>
              <td>{{ perm.code }}</td>
              <td>{{ perm.name }}</td>
            </tr>
            <tr v-if="permissions.length === 0">
              <td colspan="3" class="empty">暂无数据</td>
            </tr>
          </tbody>
        </table>
        <div class="pagination">
          <button :disabled="permPageNo <= 1" @click="permPageNo--">上一页</button>
          <span>{{ permPageNo }} / {{ permTotalPages() }}</span>
          <button :disabled="permPageNo >= permTotalPages()" @click="permPageNo++">下一页</button>
        </div>
      </div>
    </div>

    <!-- Role Dialog -->
    <div v-if="showRoleDialog" class="dialog-overlay" @click.self="showRoleDialog = false">
      <div class="dialog">
        <h3>{{ roleDialogMode === 'create' ? '新增角色' : '编辑角色' }}</h3>
        <div v-if="roleFormError" class="error">{{ roleFormError }}</div>
        <form @submit.prevent="submitRoleForm">
          <label v-if="roleDialogMode === 'create'">
            角色编码
            <input v-model="roleForm.code" required maxlength="64" />
          </label>
          <label>
            角色名称
            <input v-model="roleForm.name" required maxlength="64" />
          </label>
          <label>
            描述
            <input v-model="roleForm.description" maxlength="256" />
          </label>
          <div class="dialog-actions">
            <button type="button" class="btn" @click="showRoleDialog = false">取消</button>
            <button type="submit" class="btn btn-primary">确定</button>
          </div>
        </form>
      </div>
    </div>

    <!-- Delete Confirmation -->
    <div v-if="showDeleteConfirm" class="dialog-overlay" @click.self="showDeleteConfirm = false">
      <div class="dialog">
        <h3>确认删除</h3>
        <p>确定要删除该角色吗？此操作不可撤销。</p>
        <div class="dialog-actions">
          <button class="btn" @click="showDeleteConfirm = false">取消</button>
          <button class="btn btn-danger" @click="executeDeleteRole">删除</button>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.role-panel {
  padding: 1rem;
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}
.two-col {
  display: flex;
  gap: 1.5rem;
}
.col {
  flex: 1;
  min-width: 0;
}
.col h3 {
  margin-top: 0;
}
.hint {
  color: #888;
  font-size: 0.9rem;
  margin-bottom: 0.5rem;
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
.data-table tr.selected {
  background: #eff6ff;
}
.data-table tr {
  cursor: pointer;
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
  margin-top: 0.75rem;
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
.dialog input {
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

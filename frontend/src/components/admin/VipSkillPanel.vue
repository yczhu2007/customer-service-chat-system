<script setup>
import { ref, computed, onMounted } from 'vue'
import { findVipSkillAgents, addVipSkill, removeVipSkill } from '../../api/admin-api'

const loading = ref(false)
const error = ref(null)
const agents = ref(new Set())
const newAgentLoginNumber = ref('')
const actionLoading = ref(false)
const actionError = ref(null)
const actionSuccess = ref(null)
const editingAgentLoginNumber = ref('')
const editAgentLoginNumber = ref('')
const pageNo = ref(1)
const pageSize = ref(20)
const agentList = computed(() => Array.from(agents.value))
const visibleAgents = computed(() => agentList.value.slice((pageNo.value - 1) * pageSize.value, pageNo.value * pageSize.value))

async function loadAgents() {
  loading.value = true
  error.value = null
  try {
    const res = await findVipSkillAgents()
    agents.value = new Set(res.data ?? [])
    pageNo.value = Math.min(pageNo.value, Math.max(1, Math.ceil(agentList.value.length / pageSize.value)))
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function handlePageChange(nextPage) {
  pageNo.value = nextPage
}

onMounted(loadAgents)

async function handleAdd() {
  const loginNumber = newAgentLoginNumber.value.trim()
  if (!loginNumber) return
  actionLoading.value = true
  actionError.value = null
  actionSuccess.value = null
  try {
    await addVipSkill(loginNumber)
    actionSuccess.value = `已将 ${loginNumber} 加入 VIP 技能组`
    newAgentLoginNumber.value = ''
    await loadAgents()
  } catch (e) {
    actionError.value = e.message
  } finally {
    actionLoading.value = false
  }
}

async function handleRemove(agentLoginNumber) {
  actionLoading.value = true
  actionError.value = null
  actionSuccess.value = null
  try {
    await removeVipSkill(agentLoginNumber)
    actionSuccess.value = `已将 ${agentLoginNumber} 移出 VIP 技能组`
    await loadAgents()
  } catch (e) {
    actionError.value = e.message
  } finally {
    actionLoading.value = false
  }
}

function beginEdit(agentLoginNumber) {
  editingAgentLoginNumber.value = agentLoginNumber
  editAgentLoginNumber.value = agentLoginNumber
  actionError.value = null
  actionSuccess.value = null
}

function cancelEdit() {
  editingAgentLoginNumber.value = ''
  editAgentLoginNumber.value = ''
}

async function handleUpdate() {
  const previousLoginNumber = editingAgentLoginNumber.value
  const nextLoginNumber = editAgentLoginNumber.value.trim()
  if (!previousLoginNumber || !nextLoginNumber) return
  if (previousLoginNumber === nextLoginNumber) {
    cancelEdit()
    return
  }
  actionLoading.value = true
  actionError.value = null
  actionSuccess.value = null
  try {
    await addVipSkill(nextLoginNumber)
    await removeVipSkill(previousLoginNumber)
    actionSuccess.value = `已将 ${previousLoginNumber} 替换为 ${nextLoginNumber}`
    cancelEdit()
    await loadAgents()
  } catch (e) {
    actionError.value = e.message
  } finally {
    actionLoading.value = false
  }
}
</script>

<template>
  <section class="vip-panel">
    <div class="panel-header">
      <h2>VIP 技能组</h2>
      <button class="btn" @click="loadAgents" :disabled="loading">刷新</button>
    </div>

    <div class="add-form">
      <input
        v-model="newAgentLoginNumber"
        placeholder="输入客服登录编号"
        maxlength="64"
        @keyup.enter="handleAdd"
      />
      <button class="btn btn-primary" :disabled="actionLoading || !newAgentLoginNumber.trim()" @click="handleAdd">
        添加
      </button>
    </div>

    <div v-if="actionSuccess" class="success-msg">{{ actionSuccess }}</div>
    <div v-if="actionError" class="error-msg">{{ actionError }}</div>

    <div v-if="loading" class="loading">加载中…</div>
    <div v-else-if="error" class="error">{{ error }}</div>
    <template v-else>
      <div v-if="agents.size === 0" class="empty">暂无 VIP 技能组客服</div>
      <div v-else class="agent-list">
        <div v-for="agentLoginNumber in visibleAgents" :key="agentLoginNumber" class="agent-card">
          <template v-if="editingAgentLoginNumber === agentLoginNumber">
            <input v-model="editAgentLoginNumber" class="edit-vip-agent-input" maxlength="64" aria-label="新的客服登录编号" @keyup.enter="handleUpdate" />
            <button class="btn btn-sm btn-primary save-vip-agent" :disabled="actionLoading || !editAgentLoginNumber.trim()" @click="handleUpdate">保存</button>
            <button class="btn btn-sm" :disabled="actionLoading" @click="cancelEdit">取消</button>
          </template>
          <template v-else>
            <span class="agent-id">{{ agentLoginNumber }}</span>
            <button class="btn btn-sm edit-vip-agent" :disabled="actionLoading" @click="beginEdit(agentLoginNumber)">编辑</button>
            <button class="btn btn-sm btn-danger" :disabled="actionLoading" @click="handleRemove(agentLoginNumber)">移除</button>
          </template>
        </div>
      </div>
      <el-pagination
        v-if="agentList.length > pageSize"
        class="pagination"
        layout="prev, pager, next"
        :current-page="pageNo"
        :page-size="pageSize"
        :total="agentList.length"
        :disabled="actionLoading"
        @current-change="handlePageChange"
      />
    </template>
  </section>
</template>

<style scoped>
.vip-panel {
  padding: 1rem;
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}
.add-form {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 1rem;
}
.add-form input {
  flex: 1;
  padding: 0.4rem 0.5rem;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  font-size: 0.9rem;
}
.agent-list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
}
.agent-card {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 0.75rem 1rem;
}
.edit-vip-agent-input {
  min-width: 160px;
  padding: 0.25rem 0.5rem;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  font: inherit;
}
.pagination {
  display: flex;
  justify-content: center;
  margin-top: 1rem;
}
.agent-id {
  font-family: monospace;
  font-weight: 600;
  color: #1e293b;
}
.empty {
  text-align: center;
  color: #888;
  padding: 2rem 0;
}
.btn {
  padding: 0.4rem 0.75rem;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
  font-size: 0.9rem;
}
.btn:hover:not(:disabled) {
  background: #f3f4f6;
}
.btn-primary {
  background: #2563eb;
  color: #fff;
  border-color: #2563eb;
}
.btn-primary:hover:not(:disabled) {
  background: #1d4ed8;
}
.btn-danger {
  background: #dc2626;
  color: #fff;
  border-color: #dc2626;
}
.btn-danger:hover:not(:disabled) {
  background: #b91c1c;
}
.btn-sm {
  padding: 0.25rem 0.5rem;
  font-size: 0.8rem;
}
.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.loading, .error {
  padding: 1rem;
}
.error {
  color: #dc2626;
}
.success-msg {
  background: #dcfce7;
  color: #166534;
  padding: 0.5rem 1rem;
  border-radius: 4px;
  margin-bottom: 1rem;
}
.error-msg {
  background: #fee2e2;
  color: #991b1b;
  padding: 0.5rem 1rem;
  border-radius: 4px;
  margin-bottom: 1rem;
}
</style>

<script setup>
import { ref, onMounted } from 'vue'
import { findVipSkillAgents, addVipSkill, removeVipSkill } from '../../api/admin-api'

const loading = ref(false)
const error = ref(null)
const agents = ref(new Set())
const newAgentId = ref('')
const actionLoading = ref(false)
const actionError = ref(null)
const actionSuccess = ref(null)

async function loadAgents() {
  loading.value = true
  error.value = null
  try {
    const res = await findVipSkillAgents()
    agents.value = new Set(res.data ?? [])
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

onMounted(loadAgents)

async function handleAdd() {
  const id = newAgentId.value.trim()
  if (!id) return
  actionLoading.value = true
  actionError.value = null
  actionSuccess.value = null
  try {
    await addVipSkill(id)
    actionSuccess.value = `已将 ${id} 加入 VIP 技能组`
    newAgentId.value = ''
    await loadAgents()
  } catch (e) {
    actionError.value = e.message
  } finally {
    actionLoading.value = false
  }
}

async function handleRemove(agentId) {
  actionLoading.value = true
  actionError.value = null
  actionSuccess.value = null
  try {
    await removeVipSkill(agentId)
    actionSuccess.value = `已将 ${agentId} 移出 VIP 技能组`
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
        v-model="newAgentId"
        placeholder="输入客服 ID"
        maxlength="64"
        @keyup.enter="handleAdd"
      />
      <button class="btn btn-primary" :disabled="actionLoading || !newAgentId.trim()" @click="handleAdd">
        添加
      </button>
    </div>

    <div v-if="actionSuccess" class="success-msg">{{ actionSuccess }}</div>
    <div v-if="actionError" class="error-msg">{{ actionError }}</div>

    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="error" class="error">{{ error }}</div>
    <template v-else>
      <div v-if="agents.size === 0" class="empty">暂无 VIP 技能组客服</div>
      <div v-else class="agent-list">
        <div v-for="agentId in Array.from(agents)" :key="agentId" class="agent-card">
          <span class="agent-id">{{ agentId }}</span>
          <button
            class="btn btn-sm btn-danger"
            :disabled="actionLoading"
            @click="handleRemove(agentId)"
          >
            移除
          </button>
        </div>
      </div>
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

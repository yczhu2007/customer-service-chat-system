<script setup>
import { ref, onMounted } from 'vue'
import { findAdminDashboard } from '../../api/admin-api'

const emit = defineEmits(['navigate'])

const loading = ref(true)
const error = ref(null)
const dashboard = ref(null)

async function loadDashboard() {
  loading.value = true
  error.value = null
  try {
    const dashboardRes = await findAdminDashboard()
    dashboard.value = dashboardRes.data
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

onMounted(loadDashboard)

const quickLinks = [
  { label: '用户管理', panel: 'users', icon: '👥' },
  { label: '角色管理', panel: 'roles', icon: '🔑' },
  { label: '归档统计', panel: 'archive', icon: '📊' },
  { label: '死信管理', panel: 'deadletters', icon: '💀' },
  { label: 'VIP 技能组', panel: 'vip', icon: '⭐' },
]
</script>

<template>
  <section class="admin-dashboard">
    <h2>管理仪表盘</h2>

    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="error" class="error">{{ error }}</div>
    <template v-else>
      <div class="summary-cards">
        <div class="card">
          <div class="card-value">{{ dashboard?.onlineAgentCount ?? 0 }}</div>
          <div class="card-label">在线客服</div>
        </div>
        <div class="card">
          <div class="card-value">{{ dashboard?.totalQueueSize ?? 0 }}</div>
          <div class="card-label">当前排队数</div>
        </div>
        <div class="card">
          <div class="card-value">{{ dashboard?.todaySessionCount ?? 0 }}</div>
          <div class="card-label">今日会话数</div>
        </div>
        <div class="card">
          <div class="card-value">{{ dashboard?.todayMessageCount ?? 0 }}</div>
          <div class="card-label">今日消息数</div>
        </div>
      </div>

      <h3>快捷入口</h3>
      <div class="quick-links">
        <button
          v-for="link in quickLinks"
          :key="link.panel"
          class="quick-link"
          @click="emit('navigate', link.panel)"
        >
          <span class="ql-icon">{{ link.icon }}</span>
          <span>{{ link.label }}</span>
        </button>
      </div>
    </template>
  </section>
</template>

<style scoped>
.admin-dashboard {
  padding: 1rem;
}
.summary-cards {
  display: flex;
  gap: 1rem;
  margin-bottom: 1.5rem;
}
.card {
  background: #f0f4ff;
  border-radius: 8px;
  padding: 1.25rem 2rem;
  text-align: center;
  min-width: 120px;
}
.card-value {
  font-size: 2rem;
  font-weight: 700;
  color: #2563eb;
}
.card-label {
  margin-top: 0.25rem;
  color: #555;
}
.quick-links {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
}
.quick-link {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1.25rem;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  font-size: 1rem;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.quick-link:hover {
  border-color: #2563eb;
  box-shadow: 0 2px 8px rgba(37,99,235,0.15);
}
.ql-icon {
  font-size: 1.25rem;
}
.loading, .error {
  padding: 1rem;
}
.error {
  color: #dc2626;
}
</style>

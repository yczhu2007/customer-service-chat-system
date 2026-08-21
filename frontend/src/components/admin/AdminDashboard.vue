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
  { label: '用户管理', panel: 'users' },
  { label: '角色管理', panel: 'roles' },
  { label: '归档统计', panel: 'archive' },
  { label: '死信管理', panel: 'deadletters' },
  { label: 'VIP 技能组', panel: 'vip' },
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
          <span>{{ link.label }}</span>
        </button>
      </div>
    </template>
  </section>
</template>

<style scoped>
.admin-dashboard {
  max-width: 1180px;
  margin: 0 auto;
}
.admin-dashboard h2 { margin: 0 0 18px; font-size: 19px; }
.summary-cards {
  display: flex;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 24px;
}
.card {
  background: #fff;
  border: 1px solid var(--color-line);
  border-radius: 8px;
  padding: 12px;
  text-align: left;
  min-width: 120px;
}
.card-value {
  font-size: 2rem;
  font-weight: 700;
  color: var(--color-ink);
}
.card-label {
  margin-top: 0.25rem;
  color: var(--color-muted);
}
.quick-links {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.quick-link {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 9px 14px;
  border: 1px solid var(--color-line-strong);
  border-radius: 8px;
  background: var(--color-paper);
  cursor: pointer;
  font-size: 13px;
  transition: border-color .12s ease;
}
.quick-link:hover {
  border-color: var(--color-primary);
}
.loading, .error {
  padding: 1rem;
}
.error {
  color: var(--color-danger);
}
</style>

<script setup>
import { ref, onMounted } from 'vue'
import { findAdminDashboard } from '../../api/admin-api'

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
</script>

<template>
  <section class="admin-dashboard">
    <div class="panel-header">
      <h2>管理仪表盘</h2>
      <button class="btn refresh-dashboard" :disabled="loading" @click="loadDashboard">刷新</button>
    </div>

    <div v-if="loading" class="loading">加载中…</div>
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

    </template>
  </section>
</template>

<style scoped>
.admin-dashboard {
  max-width: 1180px;
  margin: 0 auto;
}
.admin-dashboard h2 { margin: 0 0 18px; font-size: 19px; }
.panel-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px; }
.panel-header h2 { margin: 0; }
.btn { padding: 0.4rem 0.75rem; border: 1px solid #d1d5db; border-radius: 4px; background: #fff; cursor: pointer; font-size: 0.9rem; }
.btn:hover:not(:disabled) { background: #f3f4f6; }
.btn:disabled { opacity: .6; cursor: not-allowed; }
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
.loading, .error {
  padding: 1rem;
}
.error {
  color: var(--color-danger);
}
</style>

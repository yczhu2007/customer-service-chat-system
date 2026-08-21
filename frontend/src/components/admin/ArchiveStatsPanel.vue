<script setup>
import { ref, onMounted } from 'vue'
import { findArchiveStats } from '../../api/admin-api'

const loading = ref(false)
const error = ref(null)
const stats = ref(null)

async function loadStats() {
  loading.value = true
  error.value = null
  try {
    const res = await findArchiveStats()
    stats.value = res.data
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

onMounted(loadStats)

const statusCards = [
  { key: 'completed', label: '已完成 (COMPLETED)', color: '#238636' },
  { key: 'pending', label: '待处理 (PENDING)', color: '#b7791f' },
  { key: 'onHold', label: '搁置 (ON_HOLD)', color: '#4f6f9f' },
  { key: 'other', label: '其他 (OTHER)', color: '#6b7280' },
  { key: 'unarchived', label: '未归档', color: '#94a3b8' },
]

const total = () => statusCards.reduce((sum, card) => sum + (stats.value?.[card.key] ?? 0), 0)
</script>

<template>
  <section class="archive-stats">
    <div class="panel-header">
      <h2>归档统计</h2>
      <button class="btn" @click="loadStats" :disabled="loading">
        {{ loading ? '刷新中...' : '刷新' }}
      </button>
    </div>

    <div v-if="loading && !stats" class="loading">加载中...</div>
    <div v-else-if="error" class="error">{{ error }}</div>
    <template v-else-if="stats">
      <div class="total-card">
        <div class="total-value">{{ total() }}</div>
        <div class="total-label">已结束会话总数</div>
      </div>

      <div class="cards-grid">
        <div
          v-for="card in statusCards"
          :key="card.key"
          class="stat-card"
          :style="{ borderLeftColor: card.color }"
        >
          <div class="card-body">
            <div class="card-count">{{ stats[card.key] ?? 0 }}</div>
            <div class="card-label">{{ card.label }}</div>
          </div>
        </div>
      </div>
    </template>
  </section>
</template>

<style scoped>
.archive-stats {
  padding: 1rem;
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
}
.total-card {
  text-align: center;
  background: #fff;
  color: #1f2937;
  border: 1px solid #d7dde5;
  border-radius: 4px;
  padding: 1.5rem;
  margin-bottom: 1.5rem;
}
.total-value {
  font-size: 3rem;
  font-weight: 800;
}
.total-label {
  font-size: 1.1rem;
  opacity: 0.9;
  margin-top: 0.25rem;
}
.cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 1rem;
}
.stat-card {
  display: flex;
  align-items: center;
  gap: 1rem;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-left: 4px solid;
  border-radius: 8px;
  padding: 1rem 1.25rem;
}
.card-count {
  font-size: 1.75rem;
  font-weight: 700;
  color: #1e293b;
}
.card-label {
  font-size: 0.9rem;
  color: #64748b;
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
</style>

<script setup>
import { ref, onMounted } from 'vue'
import { findArchiveStats } from '../../api/admin-api'
import { archiveStatusLabel } from '../../constants/session-ui'

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
  { key: 'completed', status: 'COMPLETED', color: '#238636' },
  { key: 'pending', status: 'PENDING', color: '#b7791f' },
  { key: 'onHold', status: 'ON_HOLD', color: '#4f6f9f' },
  { key: 'other', status: 'OTHER', color: '#6b7280' },
  { key: 'unarchived', label: '未归档', color: '#94a3b8' },
]

const total = () => statusCards.reduce((sum, card) => sum + (stats.value?.[card.key] ?? 0), 0)
</script>

<template>
  <section class="archive-stats">
    <div class="panel-header">
      <h2>归档统计</h2>
      <button class="btn" @click="loadStats" :disabled="loading">
        {{ loading ? '刷新中…' : '刷新' }}
      </button>
    </div>

    <div v-if="loading && !stats" class="loading">加载中…</div>
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
            <div class="card-label">{{ card.label || archiveStatusLabel(card.status) }}</div>
          </div>
        </div>
      </div>
    </template>
  </section>
</template>

<style scoped>
.archive-stats {
  max-width: 980px;
  margin: 0 auto;
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 18px;
}
.total-card {
  text-align: center;
  background: var(--color-paper);
  color: var(--color-ink);
  border: 1px solid var(--color-line);
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 12px;
  text-align: left;
}
.total-value {
  font-size: 24px;
  font-weight: 700;
}
.total-label {
  color: var(--color-muted);
  font-size: 12px;
  margin-top: 2px;
}
.cards-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 0;
  overflow: hidden;
  border: 1px solid var(--color-line);
  border-radius: 8px;
}
.stat-card {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 44px;
  background: var(--color-paper);
  border: 0;
  border-left: 4px solid;
  border-bottom: 1px solid var(--color-line);
  border-radius: 0;
  padding: 9px 12px;
}
.card-count {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-ink);
}
.card-label {
  font-size: 12px;
  color: var(--color-muted);
}
.btn {
  padding: 0.4rem 0.75rem;
  border: 1px solid var(--color-line-strong);
  border-radius: 8px;
  background: var(--color-paper);
  cursor: pointer;
  font-size: 0.9rem;
}
.btn:hover:not(:disabled) {
  background: rgba(77, 107, 254, .04);
}
.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.loading, .error {
  padding: 1rem;
}
.error {
  color: var(--color-danger);
}
</style>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { findSystemMonitoringSnapshot } from '../../api/admin-api'

const snapshot = ref(null)
const loading = ref(false)
const error = ref('')
let refreshTimer

function formatMillis(value) {
  if (value === null || value === undefined) return '暂无样本'
  return `${Number(value).toFixed(value < 10 ? 1 : 0)} ms`
}

function formatCollectedAt(value) {
  if (!value) return '尚未更新'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

async function loadMonitoring(background = false) {
  if (!background) loading.value = true
  try {
    const response = await findSystemMonitoringSnapshot()
    snapshot.value = response.data
    error.value = ''
  } catch (exception) {
    error.value = exception.message || '系统监控加载失败，请稍后重试。'
  } finally {
    if (!background) loading.value = false
  }
}

onMounted(() => {
  loadMonitoring()
  refreshTimer = window.setInterval(() => loadMonitoring(true), 15_000)
})

onBeforeUnmount(() => {
  window.clearInterval(refreshTimer)
})
</script>

<template>
  <section class="system-monitoring" aria-labelledby="system-monitoring-title">
    <div class="monitoring-header">
      <div>
        <h3 id="system-monitoring-title">系统监控</h3>
        <p>实时运行快照，每 15 秒自动更新。</p>
      </div>
      <button class="btn refresh-monitoring" :disabled="loading" @click="loadMonitoring(false)">
        {{ loading ? '刷新中…' : '刷新' }}
      </button>
    </div>

    <p v-if="error" class="monitoring-error" role="status">{{ error }}</p>
    <div v-if="!snapshot && loading" class="monitoring-empty">监控数据加载中…</div>
    <div v-else-if="!snapshot" class="monitoring-empty">暂时无法读取监控数据。</div>
    <template v-else>
      <div class="monitoring-grid">
        <article class="monitor-card" :class="{ unavailable: !snapshot.redisAvailable }">
          <span>排队长度</span>
          <strong>{{ snapshot.redisAvailable ? snapshot.queueLength : '暂不可用' }}</strong>
          <small>当前等待用户</small>
        </article>
        <article class="monitor-card" :class="{ unavailable: !snapshot.redisAvailable || snapshot.deadLetterBacklog > 0 }">
          <span>死信堆积</span>
          <strong>{{ snapshot.redisAvailable ? snapshot.deadLetterBacklog : '暂不可用' }}</strong>
          <small>待人工处理消息</small>
        </article>
        <article class="monitor-card">
          <span>WebSocket 在线</span>
          <strong>{{ snapshot.onlineConnections.user }} / {{ snapshot.onlineConnections.agent }}</strong>
          <small>用户 / 客服连接</small>
        </article>
        <article class="monitor-card">
          <span>消息落库延迟 P95</span>
          <strong>{{ formatMillis(snapshot.messagePersistenceLatency.p95Millis) }}</strong>
          <small>平均 {{ formatMillis(snapshot.messagePersistenceLatency.averageMillis) }} · {{ snapshot.messagePersistenceLatency.count }} 个样本</small>
        </article>
        <article class="monitor-card" :class="{ unavailable: snapshot.sessionLockLatency.failureCount > 0 }">
          <span>Redis 会话锁 P95</span>
          <strong>{{ formatMillis(snapshot.sessionLockLatency.p95Millis) }}</strong>
          <small>平均 {{ formatMillis(snapshot.sessionLockLatency.averageMillis) }} · 失败 {{ snapshot.sessionLockLatency.failureCount }} 次</small>
        </article>
      </div>
      <p class="monitoring-updated">采集时间：{{ formatCollectedAt(snapshot.collectedAt) }}</p>
    </template>
  </section>
</template>

<style scoped>
.system-monitoring { margin-bottom: 18px; padding: 14px; border: 1px solid var(--color-line); border-radius: 8px; background: var(--color-paper); }
.monitoring-header { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; margin-bottom: 12px; }
.monitoring-header h3 { margin: 0; font-size: 1rem; color: var(--color-ink); }
.monitoring-header p, .monitoring-updated { margin: 5px 0 0; color: var(--color-muted); font-size: .8rem; }
.monitoring-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 8px; }
.monitor-card { min-width: 0; padding: 12px; border: 1px solid var(--color-line); border-left: 3px solid var(--color-line-strong); border-radius: 6px; display: grid; gap: 7px; }
.monitor-card.unavailable { border-left-color: var(--color-danger); }
.monitor-card span, .monitor-card small { color: var(--color-muted); font-size: .78rem; }
.monitor-card strong { color: var(--color-ink); font-size: 1.15rem; overflow-wrap: anywhere; }
.monitoring-error { margin: 0 0 10px; color: var(--color-danger); font-size: .82rem; }
.monitoring-empty { padding: 12px 0; color: var(--color-muted); }
.refresh-monitoring:focus-visible { outline: 2px solid var(--color-ink); outline-offset: 2px; }
@media (max-width: 1000px) { .monitoring-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 560px) { .monitoring-grid { grid-template-columns: 1fr; } }
</style>

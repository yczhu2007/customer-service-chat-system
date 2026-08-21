<script setup>
import { onMounted } from 'vue'
import { useChatStore } from '../../stores/chat'

const chat = useChatStore()
onMounted(() => chat.loadAgentDashboard())
async function refresh() { await chat.loadAgentDashboard() }
</script>
<template>
  <section class="overview-panel">
    <div class="overview-header"><h2>接待概况</h2><button @click="refresh">刷新</button></div>
    <div v-if="chat.error && !chat.agentDashboard" class="error">{{ chat.error }}</div>
    <div v-else class="metrics">
      <div><strong>{{ chat.agentDashboard?.queueSize ?? 0 }}</strong><span>当前排队</span></div>
      <div><strong>{{ chat.agentDashboard?.myActiveSessions?.length ?? 0 }}</strong><span>处理中</span></div>
      <div><strong>{{ chat.agentDashboard?.todayClosedCount ?? 0 }}</strong><span>今日关闭</span></div>
      <div><strong>{{ chat.agentRatingSummary?.averageRating ?? '—' }}</strong><span>平均满意度</span></div>
    </div>
  </section>
</template>
<style scoped>
.overview-panel{padding:10px 16px;background:var(--color-paper);border-bottom:1px solid var(--color-line)}.overview-header{display:flex;justify-content:space-between;align-items:center}.overview-header h2{margin:0;font-size:14px}.overview-header button{border:1px solid var(--color-line-strong);background:white;border-radius:4px;padding:3px 8px;font-size:11px}.metrics{display:grid;grid-template-columns:repeat(4,1fr);gap:8px;margin-top:8px}.metrics div{padding:7px;border:1px solid var(--color-line);border-radius:6px}.metrics strong,.metrics span{display:block}.metrics strong{font-size:16px;color:var(--color-ink)}.metrics span{margin-top:2px;font-size:10px;color:var(--color-muted)}.error{color:var(--color-danger);font-size:12px}
</style>

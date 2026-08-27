<script setup>
import { onMounted } from 'vue'
import { useChatStore } from '../../stores/chat'

const chat = useChatStore()
onMounted(() => chat.loadAgentDashboard())
async function refresh() { await chat.loadAgentDashboard() }
function openTickets(status) { chat.filterAgentTickets(status) }
</script>
<template>
  <section class="overview-panel">
    <div class="overview-header"><h2>接待概况</h2><button @click="refresh">刷新</button></div>
    <div v-if="chat.error && !chat.agentDashboard" class="error">{{ chat.error }}</div>
    <div v-else class="overview-metrics">
      <div class="metric-card"><strong>{{ chat.agentDashboard?.queueSize ?? 0 }}</strong><span>当前排队</span></div>
      <div class="metric-card"><strong>{{ chat.agentDashboard?.myActiveSessions?.length ?? 0 }}</strong><span>处理中会话</span></div>
      <div class="metric-card"><strong>{{ chat.agentDashboard?.todayClosedCount ?? 0 }}</strong><span>今日关闭</span></div>
      <div class="metric-card"><strong>{{ chat.agentRatingSummary?.averageRating ?? '—' }}</strong><span>平均满意度</span></div>
      <button class="metric-card ticket-card" @click="openTickets('OPEN')"><strong>{{ chat.agentDashboard?.openTicketCount ?? 0 }}</strong><span>待处理工单</span></button>
      <button class="metric-card ticket-card" @click="openTickets('IN_PROGRESS')"><strong>{{ chat.agentDashboard?.inProgressTicketCount ?? 0 }}</strong><span>处理中工单</span></button>
      <button class="metric-card ticket-card" @click="openTickets('WAITING_USER')"><strong>{{ chat.agentDashboard?.waitingUserTicketCount ?? 0 }}</strong><span>等待用户</span></button>
    </div>
  </section>
</template>
<style scoped>
.overview-panel{padding:10px 16px;background:var(--color-paper);border-bottom:1px solid var(--color-line)}.overview-header{display:flex;justify-content:space-between;align-items:center}.overview-header h2{margin:0;font-size:14px}.overview-header button{border:1px solid var(--color-line-strong);background:white;border-radius:4px;padding:3px 8px;font-size:11px}.overview-metrics{display:grid;grid-template-columns:repeat(7,minmax(76px,1fr));gap:8px;margin-top:8px}.metric-card{min-width:0;padding:7px;text-align:left;border:1px solid var(--color-line);border-radius:6px;background:white}.metric-card strong,.metric-card span{display:block}.metric-card strong{font-size:16px;color:var(--color-ink)}.metric-card span{margin-top:2px;font-size:10px;color:var(--color-muted);white-space:nowrap}.ticket-card{cursor:pointer;border-color:#d9defd}.ticket-card:hover{background:#f7f8ff}.error{color:var(--color-danger);font-size:12px}@media (max-width:900px){.overview-metrics{grid-template-columns:repeat(4,minmax(76px,1fr))}}
</style>

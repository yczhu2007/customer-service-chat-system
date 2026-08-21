<script setup>
import { watch } from 'vue'
import { useChatStore } from '../../stores/chat'

const props = defineProps({ sessionId: { type: String, default: null } })
const chat = useChatStore()
watch(() => props.sessionId, (id) => chat.loadTransferLogs(id), { immediate: true })
function formatTime(value) { return value ? new Date(value).toLocaleString('zh-CN') : '' }
</script>
<template>
  <section class="transfer-panel">
    <h3>转接记录</h3>
    <div v-if="!props.sessionId" class="empty">选择会话后查看</div>
    <div v-else-if="!chat.transferLogs.length" class="empty">暂无转接记录</div>
    <ul v-else><li v-for="log in chat.transferLogs" :key="log.id">
      <div>{{ log.sourceAgentUsername || log.sourceAgentId }} → {{ log.targetAgentUsername || log.targetAgentId }}</div>
      <small>{{ formatTime(log.createTime) }}<span v-if="log.reason"> · {{ log.reason }}</span></small>
    </li></ul>
  </section>
</template>
<style scoped>
.transfer-panel{padding:10px 12px;border-top:1px solid var(--color-line);background:var(--color-paper)}h3{margin:0 0 7px;font-size:13px}.empty{color:var(--color-faint);font-size:11px}.transfer-panel ul{list-style:none;margin:0;padding:0;max-height:130px;overflow:auto}.transfer-panel li{padding:5px 0;border-bottom:1px solid var(--color-line);font-size:11px}.transfer-panel small{color:var(--color-faint)}
</style>

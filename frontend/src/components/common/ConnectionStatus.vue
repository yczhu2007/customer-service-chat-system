<script setup>
import { computed, onUnmounted, ref } from 'vue'
import { useChatStore } from '../../stores/chat'

const chat = useChatStore()
const now = ref(Date.now())
const timer = setInterval(() => { now.value = Date.now() }, 1000)
const stateLabel = computed(() => ({
  connected: '已连接', connecting: '连接中', reconnecting: '重连中', error: '连接异常', disconnected: '未连接',
}[chat.connectionState] || '未连接'))
const reconnectWaitSeconds = computed(() => {
  if (chat.connectionState !== 'reconnecting' || !chat.nextReconnectAt) return null
  return Math.max(0, Math.ceil((chat.nextReconnectAt - now.value) / 1000))
})
function reconnect() { chat.reconnectAttempts = 0; chat.reconnectStomp() }
onUnmounted(() => clearInterval(timer))
</script>
<template>
  <div class="connection-status" :class="chat.connectionState">
    <el-tag :type="chat.connectionState === 'connected' ? 'success' : chat.connectionState === 'error' ? 'danger' : 'info'" size="small">
      {{ stateLabel }}
    </el-tag>
    <span v-if="chat.connectionError" class="error">原因：{{ chat.connectionError }}</span>
    <span v-if="chat.connectionState === 'reconnecting' && chat.reconnectAttempts" class="reconnect-detail">
      第 {{ chat.reconnectAttempts }} 次重连<span v-if="reconnectWaitSeconds !== null">，{{ reconnectWaitSeconds }} 秒后重试</span>
    </span>
    <span v-if="chat.lastActivityAt" class="activity">最近活动 {{ new Date(chat.lastActivityAt).toLocaleTimeString('zh-CN') }}</span>
    <el-button v-if="chat.connectionState !== 'connected'" size="small" @click="reconnect">重连</el-button>
  </div>
</template>
<style scoped>
.connection-status{display:flex;align-items:center;gap:6px;color:var(--color-muted);font-size:11px}.status-dot{width:7px;height:7px;border-radius:50%;background:#9ca3af}.connected .status-dot{background:#16a34a}.error .status-dot,.reconnecting .status-dot{background:#dc2626}.connection-status button{padding:3px 7px;border:1px solid var(--color-line-strong);border-radius:4px;background:white;color:inherit;font-size:11px;cursor:pointer}.activity{color:var(--color-faint)}.error{color:var(--color-danger)}.reconnect-detail{color:var(--color-muted)}details{position:relative}summary{cursor:pointer;color:var(--color-primary)}details div{position:absolute;right:0;top:20px;z-index:4;width:230px;padding:8px;background:white;border:1px solid var(--color-line);box-shadow:0 6px 18px #0001}
</style>

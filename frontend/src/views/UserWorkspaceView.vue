<script setup>
import { computed, onMounted, onUnmounted } from 'vue'
import { useChatStore } from '../stores/chat'
import { useAuthStore } from '../stores/auth'
import ChatWindow from '../components/chat/ChatWindow.vue'
import UserSessionList from '../components/session/UserSessionList.vue'
import SessionRatingForm from '../components/session/SessionRatingForm.vue'
import ConnectionStatus from '../components/common/ConnectionStatus.vue'

const chat = useChatStore()
const auth = useAuthStore()
let queueTimer = null

const activeSession = computed(() => chat.activeSession)
const isClosed = computed(() => activeSession.value?.status === 'CLOSED')
const showRating = computed(() => isClosed.value && activeSession.value)
const consultationButtonLabel = computed(() => {
  if (chat.consultationState === 'STARTING') return '正在提交…'
  if (chat.consultationState === 'QUEUED') return '排队中'
  if (chat.consultationState === 'ACTIVE') return '咨询进行中'
  return chat.hasClosedConsultation ? '重新咨询' : '发起咨询'
})

/** Queue status display */
const inQueue = computed(() => chat.queueStatus?.myPosition != null)
const queuePosition = computed(() => chat.queueStatus?.myPosition)
const estimatedWait = computed(() => {
  const sec = chat.queueStatus?.estimatedWaitSeconds
  if (sec == null) return null
  return sec < 60 ? `${sec}秒` : `${Math.ceil(sec / 60)}分钟`
})

/** Start a new consultation */
function startConsultation() {
  chat.startConsultation()
}

/** Refresh queue status */
function refreshQueue() {
  chat.loadQueueStatus()
}

async function cancelQueue() {
  if (!inQueue.value || !window.confirm('确认取消排队吗？')) return
  await chat.cancelQueue()
}

onMounted(() => {
  // Connect STOMP
  chat.connectStomp()
  // Load queue status
  chat.loadQueueStatus()
  // Load sessions
  chat.loadSessions()
  // Refresh queue status periodically
  queueTimer = setInterval(() => {
    chat.loadQueueStatus()
  }, 15000)
})

onUnmounted(() => {
  if (queueTimer) {
    clearInterval(queueTimer)
    queueTimer = null
  }
})
</script>

<template>
  <el-container class="user-workspace">
    <el-aside class="left-panel">
      <!-- Queue status panel -->
      <div class="queue-panel">
        <h3>排队状态</h3>
        <div class="queue-info">
          <div class="queue-row">
            <span class="label">在线客服</span>
            <span class="value">{{ chat.queueStatus?.onlineAgentCount ?? '—' }}</span>
          </div>
          <div class="queue-row">
            <span class="label">排队人数</span>
            <span class="value">{{ chat.queueStatus?.queueSize ?? '—' }}</span>
          </div>
          <div v-if="inQueue" class="queue-row highlight">
            <span class="label">我的位置</span>
            <span class="value">第 {{ queuePosition }} 位</span>
          </div>
          <div v-if="estimatedWait" class="queue-row">
            <span class="label">预估等待</span>
            <span class="value">{{ estimatedWait }}</span>
          </div>
        </div>
        <div class="queue-actions">
          <button class="consult-btn" :disabled="!chat.canStartConsultation" @click="startConsultation">
            {{ consultationButtonLabel }}
          </button>
          <button class="refresh-queue-btn" @click="refreshQueue">刷新</button>
          <button v-if="inQueue" class="cancel-queue-btn" @click="cancelQueue">取消排队</button>
        </div>
        <p v-if="chat.queueNotice" class="queue-notice">{{ chat.queueNotice }}</p>
      </div>
      <ConnectionStatus class="connection-panel" />

      <!-- Session list -->
      <UserSessionList class="session-list-container" />
    </el-aside>

    <el-main class="center-panel">
      <ChatWindow />

      <!-- Rating form (shown at bottom when session is closed) -->
      <SessionRatingForm
        v-if="showRating"
        :session-id="activeSession.sessionId"
      />
    </el-main>
  </el-container>
</template>

<style scoped>
.user-workspace {
  --el-main-padding: 0;
  display: flex;
  height: calc(100vh - 54px);
  min-height: 0;
  background: var(--color-bg);
  overflow: hidden;
}
.left-panel {
  width: 320px;
  min-width: 280px;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--color-line);
  background: var(--color-paper);
  min-height: 0;
}
.queue-panel {
  padding: 1rem;
  border-bottom: 1px solid var(--color-line);
  background: var(--color-paper);
}
.queue-panel h3 {
  margin: 0 0 0.75rem 0;
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--color-ink);
}
.queue-info {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  margin-bottom: 0.75rem;
}
.queue-row {
  display: flex;
  justify-content: space-between;
  font-size: 0.85rem;
}
.queue-row .label {
  color: var(--color-muted);
}
.queue-row .value {
  font-weight: 500;
  color: var(--color-ink);
}
.queue-row.highlight {
  background: rgba(77, 107, 254, .06);
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
}
.queue-actions {
  display: flex;
  gap: 0.5rem;
}
.consult-btn {
  flex: 1;
  padding: 0.5rem;
  background: var(--color-primary);
  color: white;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-size: 0.85rem;
  font-weight: 500;
}
.consult-btn:hover {
  background: var(--color-primary-hover);
}
.refresh-queue-btn {
  padding: 0.5rem 0.75rem;
  background: var(--color-paper);
  border: 1px solid var(--color-line-strong);
  border-radius: 8px;
  cursor: pointer;
  font-size: 0.85rem;
  color: var(--color-ink);
}
.refresh-queue-btn:hover {
  background: rgba(77, 107, 254, .04);
}
.session-list-container {
  flex: 1;
  overflow: hidden;
}
.center-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}
.queue-notice { margin: .5rem 0 0; color: var(--color-muted); font-size: .8rem; line-height: 1.4; }
</style>

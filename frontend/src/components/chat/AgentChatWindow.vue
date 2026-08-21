<script setup>
import { ref, computed, watch } from 'vue'
import { useChatStore } from '../../stores/chat'
import MessageList from './MessageList.vue'
import MessageComposer from './MessageComposer.vue'

const chat = useChatStore()
const props = defineProps({
  quickReplyContent: { type: String, default: '' },
})
const emit = defineEmits(['quick-reply-inserted'])
const session = computed(() => chat.activeSession)
const isClosed = computed(() => session.value?.status === 'CLOSED')
const canSend = computed(() => session.value && !isClosed.value && chat.connected)

/** Status display text */
const statusText = computed(() => {
  if (!session.value) return ''
  const s = session.value.status
  if (s === 'ACTIVE') return '进行中'
  if (s === 'QUEUED') return '排队中'
  if (s === 'CLOSED') return '已结束'
  return s
})

/** Priority badge color */
const priorityClass = computed(() => {
  const p = session.value?.priority
  if (p === 'URGENT') return 'priority-urgent'
  if (p === 'HIGH') return 'priority-high'
  if (p === 'NORMAL') return 'priority-normal'
  return 'priority-low'
})

// Transfer
const showTransfer = ref(false)
const targetAgentId = ref('')
const transferring = ref(false)

function startTransfer() {
  showTransfer.value = true
  targetAgentId.value = ''
}

function cancelTransfer() {
  showTransfer.value = false
  targetAgentId.value = ''
}

async function doTransfer() {
  if (!targetAgentId.value.trim()) return
  transferring.value = true
  try {
    if (chat._stomp && chat.connected) {
      chat._stomp.publish('/app/chat.transfer', {
        sessionId: chat.activeSessionId,
        targetAgentId: targetAgentId.value.trim(),
      })
    }
    showTransfer.value = false
    targetAgentId.value = ''
  } finally {
    transferring.value = false
  }
}

// End session
async function endSession() {
  if (!confirm('确定结束此会话？')) return
  if (chat._stomp && chat.connected) {
    chat._stomp.publish('/app/chat.end', {
      sessionId: chat.activeSessionId,
    })
  }
}

// Quick reply insertion
const composerRef = ref(null)
const insertText = ref('')

watch(() => props.quickReplyContent, (content) => {
  if (!content) return
  insertText.value = content
  emit('quick-reply-inserted')
})
</script>

<template>
  <div class="agent-chat-window">
    <!-- Session info header with agent actions -->
    <header v-if="session" class="session-header">
      <div class="header-main">
        <span class="session-title">{{ session.title || '会话' }}</span>
        <span class="status-badge" :class="session.status?.toLowerCase()">{{ statusText }}</span>
        <span v-if="session.priority" class="priority-badge" :class="priorityClass">{{ session.priority }}</span>
        <span v-if="session.category" class="category-badge">{{ session.category }}</span>
      </div>
      <div class="header-meta">
        <span v-if="session.userId" class="user-info">用户: {{ session.userId }}</span>
        <span v-if="session.tags?.length" class="tags">
          <span v-for="tag in session.tags" :key="tag" class="tag">{{ tag }}</span>
        </span>
      </div>
      <div class="header-actions">
        <button
          v-if="!isClosed"
          class="action-btn transfer-btn"
          @click="startTransfer"
        >
          转接
        </button>
        <button
          v-if="!isClosed"
          class="action-btn end-btn"
          @click="endSession"
        >
          结束会话
        </button>
      </div>

      <!-- Transfer dialog -->
      <div v-if="showTransfer" class="transfer-dialog">
        <input
          v-model="targetAgentId"
          type="text"
          class="transfer-input"
          placeholder="目标客服账号或ID（如 agent002 / A002）"
          maxlength="64"
        />
        <button :disabled="transferring || !targetAgentId.trim()" class="transfer-confirm" @click="doTransfer">
          确认转接
        </button>
        <button class="transfer-cancel" @click="cancelTransfer">取消</button>
      </div>
    </header>

    <div v-if="!session" class="no-session">
      <p>请从左侧选择一个会话</p>
    </div>

    <template v-else>
      <!-- Message list -->
      <MessageList :session-id="session.sessionId" />

      <!-- Composer -->
      <MessageComposer
        ref="composerRef"
        :session-id="session.sessionId"
        :disabled="!canSend"
        :insert-text="insertText"
        @inserted="insertText = ''"
      />
    </template>
  </div>
</template>

<style scoped>
.agent-chat-window {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: #f9fafb;
  border-radius: 0.5rem;
  overflow: hidden;
}
.session-header {
  padding: 0.75rem 1rem;
  background: white;
  border-bottom: 1px solid #e5e7eb;
}
.header-main {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}
.session-title {
  font-weight: 600;
  font-size: 1rem;
  color: #111827;
}
.status-badge {
  font-size: 0.75rem;
  padding: 0.125rem 0.5rem;
  border-radius: 9999px;
  background: #e5e7eb;
  color: #374151;
}
.status-badge.active {
  background: #d1fae5;
  color: #065f46;
}
.status-badge.queued {
  background: #fef3c7;
  color: #92400e;
}
.status-badge.closed {
  background: #fee2e2;
  color: #991b1b;
}
.priority-badge {
  font-size: 0.7rem;
  padding: 0.1rem 0.4rem;
  border-radius: 4px;
}
.priority-urgent {
  background: #fee2e2;
  color: #dc2626;
}
.priority-high {
  background: #ffedd5;
  color: #ea580c;
}
.priority-normal {
  background: #e0f2fe;
  color: #0284c7;
}
.priority-low {
  background: #f3f4f6;
  color: #6b7280;
}
.category-badge {
  font-size: 0.7rem;
  padding: 0.1rem 0.4rem;
  border-radius: 4px;
  background: #ede9fe;
  color: #6d28d9;
}
.header-meta {
  margin-top: 0.25rem;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
  font-size: 0.8rem;
  color: #6b7280;
}
.tags {
  display: flex;
  gap: 0.25rem;
  flex-wrap: wrap;
}
.tag {
  background: #f3f4f6;
  padding: 0.05rem 0.35rem;
  border-radius: 3px;
  font-size: 0.7rem;
}
.header-actions {
  margin-top: 0.5rem;
  display: flex;
  gap: 0.5rem;
}
.action-btn {
  font-size: 0.8rem;
  padding: 0.3rem 0.75rem;
  border: 1px solid;
  border-radius: 0.375rem;
  cursor: pointer;
  background: white;
}
.transfer-btn {
  border-color: #3b82f6;
  color: #3b82f6;
}
.transfer-btn:hover {
  background: #eff6ff;
}
.end-btn {
  border-color: #ef4444;
  color: #ef4444;
}
.end-btn:hover {
  background: #fef2f2;
}
.transfer-dialog {
  margin-top: 0.5rem;
  display: flex;
  gap: 0.375rem;
  align-items: center;
}
.transfer-input {
  flex: 1;
  font-size: 0.8rem;
  padding: 0.3rem 0.5rem;
  border: 1px solid #d1d5db;
  border-radius: 0.375rem;
  outline: none;
}
.transfer-input:focus {
  border-color: #3b82f6;
}
.transfer-confirm {
  font-size: 0.75rem;
  padding: 0.3rem 0.6rem;
  background: #3b82f6;
  color: white;
  border: none;
  border-radius: 0.375rem;
  cursor: pointer;
}
.transfer-confirm:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.transfer-cancel {
  font-size: 0.75rem;
  padding: 0.3rem 0.6rem;
  background: #f3f4f6;
  border: 1px solid #d1d5db;
  border-radius: 0.375rem;
  cursor: pointer;
}
.no-session {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9ca3af;
}
</style>

<script setup>
import { computed } from 'vue'
import { useChatStore } from '../../stores/chat'
import MessageList from './MessageList.vue'
import MessageComposer from './MessageComposer.vue'
import { categoryLabel, categoryStyle, priorityLabel, statusLabel, tagLabel } from '../../constants/session-ui'

const chat = useChatStore()
const session = computed(() => chat.activeSession)
const isClosed = computed(() => session.value?.status === 'CLOSED')
const canSend = computed(() => session.value && !isClosed.value && chat.connected)

const statusText = computed(() => statusLabel(session.value?.status))

const priorityClass = computed(() => {
  const p = session.value?.priority
  if (p === 'URGENT') return 'priority-urgent'
  if (p === 'HIGH') return 'priority-high'
  if (p === 'NORMAL') return 'priority-normal'
  return 'priority-low'
})
</script>

<template>
  <div class="chat-window">
    <header v-if="session" class="session-header">
      <div class="header-main">
        <span class="session-title">{{ session.title || '会话' }}</span>
        <span class="status-badge" :class="session.status?.toLowerCase()">{{ statusText }}</span>
        <span v-if="session.priority" class="priority-badge" :class="priorityClass">{{ priorityLabel(session.priority) }}</span>
        <span v-if="session.category" class="category-badge" :style="categoryStyle(session.category)">{{ categoryLabel(session.category) }}</span>
      </div>
      <div class="header-meta">
        <span v-if="session.tags?.length" class="tags">
          <span v-for="tag in session.tags" :key="tag" class="tag">{{ tagLabel(tag) }}</span>
        </span>
      </div>
    </header>

    <div v-if="!session" class="no-session">
      <p>请从左侧选择一个会话</p>
    </div>

    <template v-else>
      <MessageList :session-id="session.sessionId" :closed="isClosed" />

      <MessageComposer :session-id="session.sessionId" :disabled="!canSend" />
    </template>
  </div>
</template>

<style scoped>
.chat-window {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: var(--color-bg);
  border: 1px solid var(--color-line);
  border-radius: 10px;
  overflow: hidden;
}
.session-header {
  padding: 0.75rem 1rem;
  background: var(--color-paper);
  border-bottom: 1px solid var(--color-line);
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
  color: var(--color-ink);
}
.status-badge {
  font-size: 0.75rem;
  padding: 0.125rem 0.5rem;
  border-radius: 9999px;
  background: #f2f3f5;
  color: var(--color-muted);
}
.status-badge.active {
  background: #e7f4ee;
  color: #1f8a5f;
}
.status-badge.queued {
  background: #fef3c7;
  color: #b7791f;
}
.status-badge.closed {
  background: #fdecec;
  color: #dc2626;
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
  color: var(--color-muted);
}
.category-badge {
  font-size: 0.7rem;
  padding: 0.1rem 0.4rem;
  border-radius: 4px;
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
  background: #eef0ff;
  color: #635bce;
  padding: 0.05rem 0.35rem;
  border-radius: 3px;
  font-size: 0.7rem;
}
.no-session {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-faint);
}
</style>

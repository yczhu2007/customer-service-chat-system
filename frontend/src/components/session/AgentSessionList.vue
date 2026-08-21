<script setup>
import { computed } from 'vue'
import { useChatStore } from '../../stores/chat'

const chat = useChatStore()
const emit = defineEmits(['select'])

const sessions = computed(() => chat.sortedSessions)

/** Priority badge styling */
function priorityClass(priority) {
  if (priority === 'URGENT') return 'priority-urgent'
  if (priority === 'HIGH') return 'priority-high'
  if (priority === 'NORMAL') return 'priority-normal'
  return 'priority-low'
}

/** Short category label */
function categoryLabel(cat) {
  const map = {
    ACCOUNT: '账户',
    PAYMENT: '支付',
    TECHNICAL: '技术',
    AFTER_SALES: '售后',
    OTHER: '其他',
  }
  return map[cat] || cat
}

/** Format relative or short time */
function formatTime(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  if (isNaN(d.getTime())) return ''
  const now = new Date()
  const isToday = d.toDateString() === now.toDateString()
  if (isToday) {
    return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  return d.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}

/** Archive status label */
function archiveLabel(status) {
  const map = {
    COMPLETED: '已完成',
    PENDING: '待处理',
    ON_HOLD: '搁置',
    OTHER: '其他',
  }
  return map[status] || null
}

function selectSession(sessionId) {
  chat.selectAgentSession(sessionId)
  emit('select', sessionId)
}
</script>

<template>
  <div class="agent-session-list">
    <div v-if="chat.sessionsLoading" class="loading">加载中…</div>
    <div v-else-if="!sessions.length" class="empty">暂无会话</div>
    <ul v-else class="session-items">
      <li
        v-for="s in sessions"
        :key="s.sessionId"
        class="session-item"
        :class="{ active: s.sessionId === chat.activeSessionId }"
        @click="selectSession(s.sessionId)"
      >
        <div class="session-top">
          <span class="session-title">{{ s.title || '会话' }}</span>
          <span class="session-time">{{ formatTime(s.lastMessageTime || s.createTime) }}</span>
        </div>
        <div class="session-bottom">
          <span v-if="s.priority" class="priority-tag" :class="priorityClass(s.priority)">
            {{ s.priority }}
          </span>
          <span v-if="s.category" class="category-tag">{{ categoryLabel(s.category) }}</span>
          <span v-if="s.archiveStatus" class="archive-tag">{{ archiveLabel(s.archiveStatus) }}</span>
          <span v-if="s.unreadCount > 0" class="unread-badge">{{ s.unreadCount }}</span>
        </div>
        <div v-if="s.lastMessageContent" class="session-preview">
          {{ s.lastMessageContent.slice(0, 50) }}{{ s.lastMessageContent.length > 50 ? '…' : '' }}
        </div>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.agent-session-list {
  overflow-y: auto;
  flex: 1;
}
.loading,
.empty {
  text-align: center;
  color: #9ca3af;
  padding: 2rem 1rem;
  font-size: 0.875rem;
}
.session-items {
  list-style: none;
  margin: 0;
  padding: 0;
}
.session-item {
  padding: 0.625rem 1rem;
  cursor: pointer;
  border-bottom: 1px solid #f3f4f6;
  transition: background 0.15s;
}
.session-item:hover {
  background: #f9fafb;
}
.session-item.active {
  background: #eff6ff;
  border-left: 3px solid #3b82f6;
}
.session-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.5rem;
}
.session-title {
  font-size: 0.875rem;
  font-weight: 500;
  color: #111827;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}
.session-time {
  font-size: 0.7rem;
  color: #9ca3af;
  white-space: nowrap;
}
.session-bottom {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  margin-top: 0.25rem;
  flex-wrap: wrap;
}
.priority-tag,
.category-tag,
.archive-tag {
  font-size: 0.65rem;
  padding: 0.05rem 0.35rem;
  border-radius: 3px;
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
.category-tag {
  background: #ede9fe;
  color: #6d28d9;
}
.archive-tag {
  background: #fef3c7;
  color: #92400e;
}
.unread-badge {
  font-size: 0.65rem;
  min-width: 1.25rem;
  text-align: center;
  padding: 0.05rem 0.35rem;
  border-radius: 9999px;
  background: #ef4444;
  color: white;
  font-weight: 600;
}
.session-preview {
  margin-top: 0.25rem;
  font-size: 0.75rem;
  color: #9ca3af;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>

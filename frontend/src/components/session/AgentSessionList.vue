<script setup>
import { computed } from 'vue'
import { useChatStore } from '../../stores/chat'
import { archiveStatusLabel, archiveStatusStyle, categoryLabel, categoryStyle, priorityLabel, ticketStatusLabel, TICKET_STATUS_OPTIONS } from '../../constants/session-ui'

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

function preview(content) {
  return content?.startsWith('/chat/attachments/') ? '附件消息' : content
}

function selectSession(sessionId) {
  chat.selectAgentSession(sessionId)
  emit('select', sessionId)
}

function loadMore() {
  chat.loadAllRemainingAgentSessions()
}

function filterTickets(value) {
  chat.filterAgentTickets(value)
}
</script>

<template>
  <div class="agent-session-list">
    <div v-if="chat.activeAgentView === 'MY_TICKETS'" class="ticket-filter">
      <el-select :model-value="chat.ticketStatusFilter" placeholder="全部工单状态" clearable @change="filterTickets">
        <el-option v-for="item in TICKET_STATUS_OPTIONS" :key="item.code" :label="item.label" :value="item.code" />
      </el-select>
    </div>
    <div v-if="chat.sessionsLoading && !sessions.length" class="loading">加载中…</div>
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
            {{ priorityLabel(s.priority) }}
          </span>
          <span v-if="s.category" class="category-tag" :style="categoryStyle(s.category)">{{ categoryLabel(s.category) }}</span>
          <span v-if="s.archiveStatus" class="archive-tag" :style="archiveStatusStyle(s.archiveStatus)">{{ archiveStatusLabel(s.archiveStatus) }}</span>
          <span v-if="s.ticketStatus" class="ticket-tag">工单 · {{ ticketStatusLabel(s.ticketStatus) }}</span>
          <span v-if="s.unreadCount > 0" class="unread-badge">{{ s.unreadCount }}</span>
        </div>
        <div v-if="s.lastMessageContent" class="session-preview">
          {{ preview(s.lastMessageContent).slice(0, 50) }}{{ preview(s.lastMessageContent).length > 50 ? '…' : '' }}
        </div>
      </li>
    </ul>
    <button
      v-if="chat.sessionsHasMore"
      class="load-more-btn"
      :disabled="chat.sessionsLoadingMore"
      @click="loadMore"
    >
      {{ chat.sessionsLoadingMore ? '正在加载全部会话…' : '加载更多会话' }}
    </button>
  </div>
</template>

<style scoped>
.agent-session-list {
  overflow-y: auto;
  flex: 1;
}
.ticket-filter { padding: 0.6rem 1rem; border-bottom: 1px solid #f3f4f6; }
.ticket-filter :deep(.el-select) { width: 100%; }
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
.archive-tag,
.ticket-tag {
  font-size: 0.65rem;
  padding: 0.05rem 0.35rem;
  border-radius: 3px;
}
.ticket-tag { background: #eef0ff; color: #4f46a5; }
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
.load-more-btn {
  display: block;
  width: calc(100% - 2rem);
  margin: 0.75rem 1rem;
  padding: 0.5rem;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  background: white;
  color: #374151;
  cursor: pointer;
}
.load-more-btn:hover:not(:disabled) {
  background: #f9fafb;
}
.load-more-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>

<script setup>
import { ref, onMounted } from 'vue'
import { useChatStore } from '../../stores/chat'

const chat = useChatStore()
const archiveFilter = ref('')

/** Status label */
function statusLabel(status) {
  if (status === 'ACTIVE') return '进行中'
  if (status === 'QUEUED') return '排队中'
  if (status === 'CLOSED') return '已结束'
  return status || ''
}

/** Status CSS class */
function statusClass(status) {
  return (status || '').toLowerCase()
}

/** Format datetime for display */
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

/** Truncate long text */
function truncate(text, max = 30) {
  if (!text) return ''
  return text.length > max ? text.slice(0, max) + '…' : text
}

/** Select a session */
function selectSession(sessionId) {
  chat.selectSession(sessionId)
}

/** Refresh sessions */
function refresh() {
  const params = {}
  if (archiveFilter.value) params.archiveStatus = archiveFilter.value
  chat.loadSessions(params)
}

/** Archive filter options */
const archiveOptions = [
  { label: '全部', value: '' },
  { label: '未归档', value: 'NONE' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '待处理', value: 'PENDING' },
  { label: '搁置', value: 'ON_HOLD' },
]

onMounted(() => {
  if (!chat.sessions.length) refresh()
})
</script>

<template>
  <div class="session-list-panel">
    <div class="panel-header">
      <h3>我的会话</h3>
      <button class="refresh-btn" @click="refresh" :disabled="chat.sessionsLoading">
        {{ chat.sessionsLoading ? '加载中…' : '刷新' }}
      </button>
    </div>

    <!-- Archive status filter -->
    <div class="filter-row">
      <select v-model="archiveFilter" class="archive-select" @change="refresh">
        <option v-for="opt in archiveOptions" :key="opt.value" :value="opt.value">
          {{ opt.label }}
        </option>
      </select>
    </div>

    <!-- Loading state -->
    <div v-if="chat.sessionsLoading" class="loading-state">加载中…</div>

    <!-- Error state -->
    <div v-else-if="chat.error" class="error-state">{{ chat.error }}</div>

    <!-- Empty state -->
    <div v-else-if="!chat.sessions.length" class="empty-state">暂无会话</div>

    <!-- Session list -->
    <ul v-else class="session-items">
      <li
        v-for="session in chat.sortedSessions"
        :key="session.sessionId"
        class="session-item"
        :class="{ active: session.sessionId === chat.activeSessionId }"
        @click="selectSession(session.sessionId)"
      >
        <div class="item-header">
          <span class="session-title">{{ session.title || '会话' }}</span>
          <span class="time">{{ formatTime(session.lastMessageTime || session.createTime) }}</span>
        </div>
        <div class="item-body">
          <span class="last-msg">{{ truncate(session.lastMessageContent) || '暂无消息' }}</span>
          <span class="status-badge" :class="statusClass(session.status)">
            {{ statusLabel(session.status) }}
          </span>
        </div>
        <div class="item-footer">
          <span v-if="session.priority" class="priority-tag" :class="(session.priority || '').toLowerCase()">
            {{ session.priority }}
          </span>
          <span v-if="chat.unreadCounts[session.sessionId]" class="unread-badge">
            {{ chat.unreadCounts[session.sessionId] }}
          </span>
          <span v-if="session.archiveStatus" class="archive-tag">{{ session.archiveStatus }}</span>
        </div>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.session-list-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: white;
  border-right: 1px solid #e5e7eb;
}
.panel-header {
  padding: 0.75rem 1rem;
  border-bottom: 1px solid #e5e7eb;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.panel-header h3 {
  margin: 0;
  font-size: 1rem;
  font-weight: 600;
}
.refresh-btn {
  background: none;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  padding: 0.25rem 0.5rem;
  cursor: pointer;
  font-size: 0.8rem;
  color: #374151;
}
.refresh-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.filter-row {
  padding: 0.5rem 1rem;
  border-bottom: 1px solid #f3f4f6;
}
.archive-select {
  width: 100%;
  padding: 0.35rem;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  font-size: 0.85rem;
}
.session-items {
  list-style: none;
  margin: 0;
  padding: 0;
  overflow-y: auto;
  flex: 1;
}
.session-item {
  padding: 0.75rem 1rem;
  border-bottom: 1px solid #f3f4f6;
  cursor: pointer;
  transition: background 0.15s;
}
.session-item:hover {
  background: #f9fafb;
}
.session-item.active {
  background: #eff6ff;
  border-left: 3px solid #3b82f6;
}
.item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.25rem;
}
.session-title {
  font-weight: 500;
  font-size: 0.9rem;
  color: #111827;
}
.time {
  font-size: 0.75rem;
  color: #9ca3af;
}
.item-body {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.5rem;
}
.last-msg {
  font-size: 0.8rem;
  color: #6b7280;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.status-badge {
  font-size: 0.7rem;
  padding: 0.1rem 0.4rem;
  border-radius: 9999px;
  white-space: nowrap;
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
.item-footer {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 0.25rem;
}
.priority-tag {
  font-size: 0.65rem;
  padding: 0.05rem 0.3rem;
  border-radius: 3px;
}
.priority-tag.urgent {
  background: #fee2e2;
  color: #dc2626;
}
.priority-tag.high {
  background: #ffedd5;
  color: #ea580c;
}
.priority-tag.normal {
  background: #e0f2fe;
  color: #0284c7;
}
.priority-tag.low {
  background: #f3f4f6;
  color: #6b7280;
}
.unread-badge {
  background: #ef4444;
  color: white;
  font-size: 0.7rem;
  min-width: 18px;
  height: 18px;
  border-radius: 9999px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 4px;
}
.archive-tag {
  font-size: 0.65rem;
  color: #9ca3af;
  background: #f3f4f6;
  padding: 0.05rem 0.3rem;
  border-radius: 3px;
}
.loading-state,
.error-state,
.empty-state {
  padding: 2rem 1rem;
  text-align: center;
  color: #9ca3af;
  font-size: 0.9rem;
}
.error-state {
  color: #ef4444;
}
</style>

<script setup>
import { onMounted } from 'vue'
import { useChatStore } from '../../stores/chat'
import { archiveStatusLabel, archiveStatusStyle, formatListTime, priorityLabel, statusLabel } from '../../constants/session-ui'

const chat = useChatStore()

function statusClass(status) {
  return (status || '').toLowerCase()
}

function truncate(text, max = 30) {
  if (!text) return ''
  return text.length > max ? text.slice(0, max) + '…' : text
}

function preview(content) {
  return content?.startsWith('/chat/attachments/') ? '附件消息' : content
}

function selectSession(sessionId) {
  chat.selectSession(sessionId)
}

function refresh() {
  chat.loadSessions()
}

function loadMore() {
  chat.loadNextSessionsPage()
}

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

    <div v-if="chat.sessionsLoading" class="loading-state">加载中…</div>

    <div v-else-if="chat.sessionsError" class="error-state">{{ chat.sessionsError }}</div>

    <div v-else-if="!chat.sessions.length" class="empty-state">暂无会话</div>

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
          <span class="time">{{ formatListTime(session.lastMessageTime || session.createTime) }}</span>
        </div>
        <div class="item-body">
          <span class="last-msg">{{ truncate(preview(session.lastMessageContent)) || '暂无消息' }}</span>
          <span class="status-badge" :class="statusClass(session.status)">
            {{ statusLabel(session.status) }}
          </span>
        </div>
        <div class="item-footer">
          <span v-if="session.priority" class="priority-tag" :class="(session.priority || '').toLowerCase()">
            {{ priorityLabel(session.priority) }}
          </span>
          <span v-if="chat.unreadCounts[session.sessionId]" class="unread-badge">
            {{ chat.unreadCounts[session.sessionId] }}
          </span>
          <span v-if="session.archiveStatus" class="archive-tag" :style="archiveStatusStyle(session.archiveStatus)">{{ archiveStatusLabel(session.archiveStatus) }}</span>
        </div>
      </li>
    </ul>
    <div v-if="chat.sessionsError && chat.sessions.length" class="refresh-error">
      会话刷新失败：{{ chat.sessionsError }}
    </div>
    <button
      v-if="chat.sessionsHasMore"
      class="load-more-btn"
      :disabled="chat.sessionsLoading"
      @click="loadMore"
    >
      {{ chat.sessionsLoading ? '加载中…' : '加载更多会话' }}
    </button>
  </div>
</template>

<style scoped>
.session-list-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
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
.load-more-btn {
  flex: 0 0 auto;
  margin: 0.6rem 1rem;
  padding: 0.45rem;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  background: white;
  color: #374151;
  cursor: pointer;
}
.load-more-btn:disabled { opacity: 0.5; cursor: not-allowed; }
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

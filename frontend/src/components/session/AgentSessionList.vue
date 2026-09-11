<script setup>
import { computed, ref } from 'vue'
import { useChatStore } from '../../stores/chat'
import { archiveStatusLabel, archiveStatusStyle, CATEGORY_OPTIONS, categoryLabel, categoryStyle, formatListTime, priorityLabel, statusLabel, ticketStatusLabel, TICKET_STATUS_OPTIONS } from '../../constants/session-ui'

const chat = useChatStore()
const emit = defineEmits(['select'])

const ticketPriority = ref('')
const ticketCategory = ref('')
const priorityOptions = ['LOW', 'NORMAL', 'HIGH', 'URGENT']
const sessions = computed(() => chat.sortedSessions.filter((session) =>
  (!ticketPriority.value || session.priority === ticketPriority.value)
  && (!ticketCategory.value || session.category === ticketCategory.value)
))

function priorityClass(priority) {
  if (priority === 'URGENT') return 'priority-urgent'
  if (priority === 'HIGH') return 'priority-high'
  if (priority === 'NORMAL') return 'priority-normal'
  return 'priority-low'
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

function searchTickets() {
  chat.filterAgentTickets(chat.ticketStatusFilter, ticketKeyword.value)
}

const ticketKeyword = ref(chat.ticketKeyword || '')
</script>

<template>
  <div class="agent-session-list">
    <div v-if="['MY_TICKETS', 'MY_PARTICIPATED_TICKETS'].includes(chat.activeAgentView)" class="ticket-filter">
      <div class="ticket-search-group">
        <el-input v-model="ticketKeyword" class="ticket-keyword" placeholder="工单号、会话标题、问题或处理结果" clearable @keyup.enter="searchTickets" />
        <el-button class="ticket-search-btn" size="small" type="primary" @click="searchTickets">搜索</el-button>
      </div>
      <el-select :model-value="chat.ticketStatusFilter" placeholder="全部工单状态" clearable @change="filterTickets">
        <el-option v-for="item in TICKET_STATUS_OPTIONS" :key="item.code" :label="item.label" :value="item.code" />
      </el-select>
      <el-select v-model="ticketPriority" class="ticket-priority" placeholder="全部优先级" clearable>
        <el-option v-for="item in priorityOptions" :key="item" :label="priorityLabel(item)" :value="item" />
      </el-select>
      <el-select v-model="ticketCategory" class="ticket-category" placeholder="全部分类" clearable>
        <el-option v-for="item in CATEGORY_OPTIONS" :key="item.code" :label="item.label" :value="item.code" />
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
          <span class="session-time">{{ formatListTime(s.lastMessageTime || s.createTime) }}</span>
        </div>
        <div class="session-bottom">
          <span v-if="s.status" class="session-status">{{ statusLabel(s.status) }}</span>
          <span v-if="s.priority" class="priority-tag" :class="priorityClass(s.priority)">
            {{ priorityLabel(s.priority) }}
          </span>
          <span v-if="s.category" class="category-tag" :style="categoryStyle(s.category)">{{ categoryLabel(s.category) }}</span>
          <span v-if="s.archiveStatus" class="archive-tag" :style="archiveStatusStyle(s.archiveStatus)">{{ archiveStatusLabel(s.archiveStatus) }}</span>
          <span v-if="s.ticketStatus" class="ticket-tag">工单 · {{ s.ticketNo }} · {{ ticketStatusLabel(s.ticketStatus) }}</span>
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
.ticket-filter { display: flex; flex-wrap: wrap; gap: 0.4rem; padding: 0.6rem 1rem; border-bottom: 1px solid #f3f4f6; }
.ticket-search-group { display: flex; flex: 1 1 100%; gap: 0.4rem; min-width: 0; }
.ticket-keyword { flex: 1; min-width: 0; }
.ticket-search-btn { flex: 0 0 auto; }
.ticket-filter :deep(.el-select) { flex: 1 1 120px; width: 120px; }
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
.session-status,
.category-tag,
.archive-tag,
.ticket-tag {
  font-size: 0.65rem;
  padding: 0.05rem 0.35rem;
  border-radius: 3px;
}
.session-status { background: #e7f4ee; color: #1f8a5f; }
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

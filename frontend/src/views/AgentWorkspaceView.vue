<script setup>
import { onMounted, onUnmounted } from 'vue'
import { useChatStore } from '../stores/chat'
import { useAuthStore } from '../stores/auth'
import { agentOnline, agentOffline } from '../api/chat-api'
import AgentViewNav from '../components/session/AgentViewNav.vue'
import AgentSessionList from '../components/session/AgentSessionList.vue'
import AgentChatWindow from '../components/chat/AgentChatWindow.vue'
import SessionMetadataEditor from '../components/session/SessionMetadataEditor.vue'
import SessionArchiveActions from '../components/session/SessionArchiveActions.vue'
import UserProfileSidebar from '../components/agent/UserProfileSidebar.vue'
import QuickReplyPanel from '../components/agent/QuickReplyPanel.vue'

const chat = useChatStore()
const auth = useAuthStore()

const QUICK_REPLY_LABELS = {
  MY_ACTIVE: '处理中',
  MY_UNREAD: '未读',
  MY_HIGH_PRIORITY: '高优先级',
  MY_UNARCHIVED: '未归档',
  MY_RECENT_CLOSED: '最近关闭',
}

/** Set agent online and connect STOMP */
async function goOnline() {
  try {
    await agentOnline()
    chat.agentOnline = true
    chat.connectStomp()
    // Load initial view
    await chat.loadAgentViewCounts()
    await chat.loadAgentViewSessions(chat.activeAgentView)
  } catch {
    // Error handled by store
  }
}

/** Set agent offline */
async function goOffline() {
  try {
    await agentOffline()
    chat.agentOnline = false
    chat.disconnectStomp()
  } catch {
    // Error handled by store
  }
}

/** Handle quick reply insert into composer */
function onQuickReplyInsert(content) {
  // This will be passed to AgentChatWindow via the insertText mechanism
  chat._pendingInsertText = content
}

onMounted(async () => {
  // Auto-go online if not already
  if (!chat.agentOnline) {
    await goOnline()
  }
})

onUnmounted(() => {
  // Optionally go offline on leave (commented out to persist across navigation)
  // goOffline()
})
</script>

<template>
  <div class="agent-workspace">
    <!-- Top bar -->
    <header class="workspace-header">
      <h1 class="workspace-title">客服工作台</h1>
      <div class="header-right">
        <span class="agent-id">{{ auth.userId }}</span>
        <span class="online-status" :class="{ online: chat.agentOnline }">
          {{ chat.agentOnline ? '在线' : '离线' }}
        </span>
        <button v-if="!chat.agentOnline" class="online-btn" @click="goOnline">上线</button>
        <button v-else class="offline-btn" @click="goOffline">下线</button>
        <span class="ws-status" :class="{ connected: chat.connected }">
          {{ chat.connected ? 'WS已连接' : 'WS未连接' }}
        </span>
      </div>
    </header>

    <div class="workspace-body">
      <!-- Left sidebar: view nav + session list -->
      <aside class="left-sidebar">
        <AgentViewNav />
        <div class="sidebar-divider" />
        <div class="session-list-header">
          <span class="list-title">{{ QUICK_REPLY_LABELS[chat.activeAgentView] || '会话' }}</span>
          <button class="refresh-btn" @click="chat.loadAgentViewSessions(chat.activeAgentView)">刷新</button>
        </div>
        <AgentSessionList />
      </aside>

      <!-- Center: chat window -->
      <main class="chat-area">
        <AgentChatWindow />
      </main>

      <!-- Right sidebar: metadata + user profile -->
      <aside class="right-sidebar">
        <UserProfileSidebar />
        <div class="sidebar-divider" />
        <SessionMetadataEditor />
      </aside>
    </div>

    <!-- Bottom panel: quick replies + archive actions -->
    <footer class="workspace-footer">
      <div class="footer-section quick-reply-section">
        <QuickReplyPanel @insert="onQuickReplyInsert" />
      </div>
      <div class="footer-divider" />
      <div class="footer-section archive-section">
        <SessionArchiveActions />
      </div>
    </footer>
  </div>
</template>

<style scoped>
.agent-workspace {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f3f4f6;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

/* ─── Header ─── */
.workspace-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.5rem 1rem;
  background: white;
  border-bottom: 1px solid #e5e7eb;
  flex-shrink: 0;
}
.workspace-title {
  font-size: 1.1rem;
  font-weight: 700;
  color: #111827;
  margin: 0;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}
.agent-id {
  font-size: 0.8rem;
  color: #6b7280;
}
.online-status {
  font-size: 0.75rem;
  padding: 0.1rem 0.5rem;
  border-radius: 9999px;
  background: #fee2e2;
  color: #991b1b;
}
.online-status.online {
  background: #d1fae5;
  color: #065f46;
}
.online-btn,
.offline-btn {
  font-size: 0.8rem;
  padding: 0.25rem 0.75rem;
  border-radius: 0.375rem;
  cursor: pointer;
  border: 1px solid;
}
.online-btn {
  background: #3b82f6;
  color: white;
  border-color: #3b82f6;
}
.online-btn:hover {
  background: #2563eb;
}
.offline-btn {
  background: white;
  color: #ef4444;
  border-color: #ef4444;
}
.offline-btn:hover {
  background: #fef2f2;
}
.ws-status {
  font-size: 0.7rem;
  color: #9ca3af;
}
.ws-status.connected {
  color: #059669;
}

/* ─── Body layout ─── */
.workspace-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

/* ─── Left sidebar ─── */
.left-sidebar {
  width: 280px;
  min-width: 240px;
  background: white;
  border-right: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.sidebar-divider {
  height: 1px;
  background: #e5e7eb;
}
.session-list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.5rem 1rem;
}
.list-title {
  font-size: 0.8rem;
  font-weight: 600;
  color: #374151;
}
.refresh-btn {
  font-size: 0.7rem;
  padding: 0.15rem 0.5rem;
  background: #f3f4f6;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  cursor: pointer;
  color: #6b7280;
}
.refresh-btn:hover {
  background: #e5e7eb;
}

/* ─── Center chat area ─── */
.chat-area {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

/* ─── Right sidebar ─── */
.right-sidebar {
  width: 280px;
  min-width: 240px;
  background: white;
  border-left: 1px solid #e5e7eb;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

/* ─── Footer ─── */
.workspace-footer {
  display: flex;
  background: white;
  border-top: 1px solid #e5e7eb;
  flex-shrink: 0;
  max-height: 240px;
  overflow: hidden;
}
.footer-section {
  flex: 1;
  overflow-y: auto;
}
.footer-divider {
  width: 1px;
  background: #e5e7eb;
}
.quick-reply-section {
  flex: 1.5;
}
.archive-section {
  flex: 1;
}
</style>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
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
import ConnectionStatus from '../components/common/ConnectionStatus.vue'
import AgentOverviewPanel from '../components/agent/AgentOverviewPanel.vue'
import TransferLogPanel from '../components/session/TransferLogPanel.vue'

const chat = useChatStore()
const auth = useAuthStore()
const pendingQuickReply = ref('')
const presenceError = ref('')

const QUICK_REPLY_LABELS = {
  MY_ACTIVE: '处理中',
  MY_UNREAD: '未读',
  MY_HIGH_PRIORITY: '高优先级',
  MY_UNARCHIVED: '未归档',
  MY_RECENT_CLOSED: '最近关闭',
}

/** Set agent online and connect STOMP */
async function goOnline() {
  presenceError.value = ''
  try {
    await agentOnline()
    chat.agentOnline = true
    chat.connectStomp()
    // Load initial view
    await chat.loadAgentViewCounts()
    await chat.loadAgentViewSessions(chat.activeAgentView)
  } catch (e) {
    chat.agentOnline = false
    presenceError.value = e.message || '上线失败，请稍后重试'
  }
}

/** Set agent offline */
async function goOffline() {
  presenceError.value = ''
  try {
    await agentOffline()
  } catch (e) {
    presenceError.value = e.message || '下线失败，请稍后重试'
  } finally {
    chat.agentOnline = false
    chat.disconnectStomp()
  }
}

/** Handle quick reply insert into composer */
function onQuickReplyInsert(content) {
  pendingQuickReply.value = content
}

onMounted(async () => {
  // Auto-go online if not already
  if (!chat.agentOnline) {
    await goOnline()
  }
})

onUnmounted(() => {
  goOffline()
})
</script>

<template>
  <div class="agent-workspace">
    <AgentOverviewPanel />
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
        <ConnectionStatus />
      </div>
      <p v-if="presenceError" class="presence-error">{{ presenceError }}</p>
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
        <AgentChatWindow
          :quick-reply-content="pendingQuickReply"
          @quick-reply-inserted="pendingQuickReply = ''"
        />
      </main>

      <!-- Right sidebar: metadata + user profile -->
      <aside class="right-sidebar">
        <UserProfileSidebar />
        <div class="sidebar-divider" />
        <SessionMetadataEditor />
        <TransferLogPanel :session-id="chat.activeSessionId" />
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
.presence-error { margin: 0; color: var(--color-danger); font-size: .8rem; }
</style>

<style scoped>
.agent-workspace {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 54px);
  min-height: 0;
  background: var(--color-bg);
  font-family: var(--font-sans);
  overflow: hidden;
}

/* ─── Header ─── */
.workspace-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.5rem 1rem;
  background: var(--color-paper);
  border-bottom: 1px solid var(--color-line);
  flex-shrink: 0;
}
.workspace-title {
  font-size: 1.1rem;
  font-weight: 700;
  color: var(--color-ink);
  margin: 0;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}
.agent-id {
  font-size: 0.8rem;
  color: var(--color-muted);
}
.online-status {
  font-size: 0.75rem;
  padding: 0.1rem 0.5rem;
  border-radius: 9999px;
  background: #fdecec;
  color: var(--color-danger);
}
.online-status.online {
  background: #e7f4ee;
  color: var(--color-success);
}
.online-btn,
.offline-btn {
  font-size: 0.8rem;
  padding: 0.25rem 0.75rem;
  border-radius: 8px;
  cursor: pointer;
  border: 1px solid;
}
.online-btn {
  background: var(--color-primary);
  color: white;
  border-color: var(--color-primary);
}
.online-btn:hover {
  background: var(--color-primary-hover);
}
.offline-btn {
  background: white;
  color: var(--color-danger);
  border-color: var(--color-danger);
}
.offline-btn:hover {
  background: #fef2f2;
}
.ws-status {
  font-size: 0.7rem;
  color: var(--color-faint);
}
.ws-status.connected {
  color: var(--color-success);
}

/* ─── Body layout ─── */
.workspace-body {
  flex: 1;
  display: flex;
  overflow: hidden;
  min-height: 0;
}

/* ─── Left sidebar ─── */
.left-sidebar {
  width: 280px;
  min-width: 240px;
  background: var(--color-paper);
  border-right: 1px solid var(--color-line);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.sidebar-divider {
  height: 1px;
  background: var(--color-line);
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
  color: var(--color-ink);
}
.refresh-btn {
  font-size: 0.7rem;
  padding: 0.15rem 0.5rem;
  background: var(--color-paper);
  border: 1px solid var(--color-line-strong);
  border-radius: 4px;
  cursor: pointer;
  color: var(--color-muted);
}
.refresh-btn:hover {
  background: rgba(77, 107, 254, .04);
}

/* ─── Center chat area ─── */
.chat-area {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

/* ─── Right sidebar ─── */
.right-sidebar {
  width: 280px;
  min-width: 240px;
  background: var(--color-paper);
  border-left: 1px solid var(--color-line);
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

/* ─── Footer ─── */
.workspace-footer {
  display: flex;
  background: var(--color-paper);
  border-top: 1px solid var(--color-line);
  flex-shrink: 0;
  max-height: 190px;
  overflow: hidden;
}
.footer-section {
  flex: 1;
  overflow-y: auto;
}
.footer-divider {
  width: 1px;
  background: var(--color-line);
}
.quick-reply-section {
  flex: 1.5;
}
.archive-section {
  flex: 1;
}
</style>

<script setup>
import { useRouter } from 'vue-router'
import { logout as logoutRequest } from '../../api/auth-api'
import { agentOffline } from '../../api/chat-api'
import { useAuthStore } from '../../stores/auth'
import { useChatStore } from '../../stores/chat'

const router = useRouter()
const auth = useAuthStore()
const chat = useChatStore()

async function signOut() {
  try {
    if (auth.hasRole('AGENT')) {
      try { await agentOffline() } catch { /* Continue with token logout and local cleanup. */ }
    }
    if (auth.token) await logoutRequest()
  } catch {
    // The local session must still be cleared so another account can sign in.
  } finally {
    chat.disconnectStomp({ manual: true })
    auth.logout()
    await router.replace('/login')
  }
}

async function switchWorkspace(role) {
  if (!auth.setActiveRole(role)) return
  await router.push(`/${role.toLowerCase()}`)
}
</script>

<template>
  <main class="app-shell">
    <header class="app-header">
      <div class="brand">
        <span class="brand-name">Customer Service Chat</span>
        <span class="brand-subtitle">SERVICE WORKSPACE</span>
      </div>
      <div v-if="auth.isAuthenticated" class="header-actions">
        <div v-if="auth.roles.length > 1" class="workspace-switcher">
          <button v-for="role in auth.roles" :key="role" :class="{ active: auth.activeRole === role }" type="button" @click="switchWorkspace(role)">
            {{ role === 'ADMIN' ? '管理' : role === 'AGENT' ? '客服' : '用户' }}
          </button>
        </div>
        <RouterLink class="account-link" to="/account">{{ auth.username || auth.userId }}</RouterLink>
        <button class="logout-button" type="button" @click="signOut">退出登录</button>
      </div>
    </header>
    <slot />
  </main>
</template>
<style scoped>
.app-shell { min-height: 100vh; background: var(--color-bg); }
.app-header { display: flex; align-items: center; justify-content: space-between; min-height: 54px; padding: 0 24px; border-bottom: 1px solid var(--color-line); background: var(--color-paper); }
.brand { display: flex; align-items: baseline; gap: 10px; }
.brand-name { color: var(--color-ink); font-size: 15px; font-weight: 700; }
.brand-subtitle { color: var(--color-faint); font: 10px/1 var(--font-mono); letter-spacing: .16em; }
.header-actions { display: flex; align-items: center; gap: 14px; }
.workspace-switcher { display: flex; gap: 4px; }
.workspace-switcher button { border: 1px solid var(--color-line); border-radius: 4px; padding: 4px 7px; background: var(--color-paper); color: var(--color-muted); font-size: 11px; cursor: pointer; }
.workspace-switcher button.active { border-color: var(--color-primary); color: var(--color-primary); }
.signed-in-user { color: var(--color-muted); font: 12px/1 var(--font-mono); }
.account-link { color: var(--color-muted); font: 12px/1 var(--font-mono); text-decoration: none; }
.account-link:hover { color: var(--color-primary); }
.logout-button { border: 1px solid var(--color-line-strong); border-radius: 4px; padding: 6px 10px; color: var(--color-ink); background: var(--color-paper); font: 12px/1 var(--font-sans); cursor: pointer; }
.logout-button:hover { border-color: var(--color-primary); color: var(--color-primary); }

@media (max-width: 560px) {
  .app-header { padding: 0 16px; }
  .brand-subtitle, .signed-in-user { display: none; }
}
</style>

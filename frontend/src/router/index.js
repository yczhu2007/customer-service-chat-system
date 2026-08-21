import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import LoginView from '../views/LoginView.vue'
import UserWorkspaceView from '../views/UserWorkspaceView.vue'
import AgentWorkspaceView from '../views/AgentWorkspaceView.vue'
import AdminWorkspaceView from '../views/AdminWorkspaceView.vue'

const routes = [
  { path: '/login', component: LoginView },
  { path: '/user', component: UserWorkspaceView, meta: { role: 'USER' } },
  { path: '/agent', component: AgentWorkspaceView, meta: { role: 'AGENT' } },
  { path: '/admin', component: AdminWorkspaceView, meta: { role: 'ADMIN' } },
  { path: '/', redirect: '/login' },
]

const router = createRouter({ history: createWebHistory(), routes })
router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.path === '/login') return auth.isAuthenticated ? `/${auth.homeRole.toLowerCase()}` : true
  if (!auth.isAuthenticated) return { path: '/login', query: { redirect: to.fullPath } }
  return to.meta.role === auth.homeRole ? true : `/${auth.homeRole.toLowerCase()}`
})
export default router

import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import LoginView from '../views/LoginView.vue'
import UserWorkspaceView from '../views/UserWorkspaceView.vue'
import AgentWorkspaceView from '../views/AgentWorkspaceView.vue'
import AdminWorkspaceView from '../views/AdminWorkspaceView.vue'
import RegisterView from '../views/RegisterView.vue'
import ForgotPasswordView from '../views/ForgotPasswordView.vue'
import AccountView from '../views/AccountView.vue'

const routes = [
  { path: '/index.html', redirect: '/login' },
  { path: '/login', component: LoginView },
  { path: '/register', component: RegisterView },
  { path: '/forgot-password', component: ForgotPasswordView },
  { path: '/account', component: AccountView, meta: { requiresAuth: true } },
  { path: '/user', component: UserWorkspaceView, meta: { role: 'USER' } },
  { path: '/agent', component: AgentWorkspaceView, meta: { role: 'AGENT' } },
  { path: '/admin', component: AdminWorkspaceView, meta: { role: 'ADMIN' } },
  { path: '/', redirect: '/login' },
]

const router = createRouter({ history: createWebHistory(import.meta.env.BASE_URL), routes })
router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.path === '/login' || to.path === '/register' || to.path === '/forgot-password') {
    return auth.isAuthenticated ? `/${auth.homeRole.toLowerCase()}` : true
  }
  if (!auth.isAuthenticated) return { path: '/login', query: { redirect: to.fullPath } }
  if (!to.meta.role) return true
  return to.meta.role === auth.homeRole ? true : `/${auth.homeRole.toLowerCase()}`
})
export default router

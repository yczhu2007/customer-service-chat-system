import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const routes = [
  { path: '/index.html', redirect: '/login' },
  { path: '/login', component: () => import('../views/LoginView.vue') },
  { path: '/register', component: () => import('../views/RegisterView.vue') },
  { path: '/forgot-password', component: () => import('../views/ForgotPasswordView.vue') },
  { path: '/account', component: () => import('../views/AccountView.vue'), meta: { requiresAuth: true } },
  { path: '/user', component: () => import('../views/UserWorkspaceView.vue'), meta: { requiresAuth: true, role: 'USER' } },
  { path: '/agent', component: () => import('../views/AgentWorkspaceView.vue'), meta: { requiresAuth: true, role: 'AGENT' } },
  { path: '/admin', component: () => import('../views/AdminWorkspaceView.vue'), meta: { requiresAuth: true, role: 'ADMIN' } },
  { path: '/', redirect: '/login' },
]

const router = createRouter({ history: createWebHistory(import.meta.env.BASE_URL), routes })
router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.path === '/login' || to.path === '/register' || to.path === '/forgot-password') {
    return auth.isAuthenticated ? `/${auth.homeRole.toLowerCase()}` : true
  }
  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (!auth.isAuthenticated) return true
  if (!to.meta.role) return true
  if (!auth.hasRole(to.meta.role)) return `/${auth.homeRole.toLowerCase()}`
  auth.setActiveRole(to.meta.role)
  return true
})
export default router

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { login } from '../api/auth-api'

const router = useRouter()
const auth = useAuthStore()
const username = ref('')
const password = ref('')
const submitting = ref(false)
const error = ref('')

async function submit() {
  submitting.value = true
  error.value = ''
  try {
    const result = await login({ username: username.value, password: password.value })
    const payload = result?.data
    const roles = payload?.roles || []
    const role = roles.includes('ADMIN') ? 'ADMIN' : roles.includes('AGENT') ? 'AGENT' : 'USER'
    if (!payload?.token || !payload?.userId) {
      throw new Error('登录响应缺少认证信息')
    }
    auth.login({ token: payload.token, userId: payload.userId, role })
    await router.push(`/${role.toLowerCase()}`)
  } catch (e) {
    error.value = e.message || '登录失败'
  } finally {
    submitting.value = false
  }
}
</script>
<template>
  <section class="login-screen">
    <form class="login-card" @submit.prevent="submit">
      <div class="login-brand">
        <h1>客服工作台</h1>
        <p>Customer Service Chat</p>
      </div>
      <label>
        用户名
        <input v-model="username" required autocomplete="username" placeholder="请输入用户名">
      </label>
      <label>
        密码
        <input v-model="password" required type="password" autocomplete="current-password" placeholder="请输入密码">
      </label>
      <p v-if="error" class="login-error" role="alert">{{ error }}</p>
      <button class="login-submit" :disabled="submitting">{{ submitting ? '登录中…' : '进入工作台' }}</button>
      <div class="login-links">
        <RouterLink to="/register">注册普通用户</RouterLink>
        <RouterLink to="/forgot-password">忘记密码</RouterLink>
      </div>
    </form>
  </section>
</template>

<style scoped>
.login-screen { display: grid; min-height: calc(100vh - 54px); place-items: center; padding: 24px; }
.login-card { width: min(400px, 100%); padding: 32px; border: 1px solid var(--color-line); border-radius: 14px; background: var(--color-paper); box-shadow: 0 12px 32px rgba(24, 29, 38, .08); }
.login-brand { margin-bottom: 26px; text-align: center; }
.login-brand h1 { margin: 0; font-size: 22px; }
.login-brand p { margin: 6px 0 0; color: var(--color-faint); font: 11px var(--font-mono); letter-spacing: .14em; text-transform: uppercase; }
label { display: grid; gap: 6px; margin-bottom: 14px; color: var(--color-muted); font-size: 12px; }
input { width: 100%; padding: 9px 11px; }
.login-error { margin: 0 0 12px; color: var(--color-danger); font-size: 12px; }
.login-submit { width: 100%; padding: 9px 14px; border-color: var(--color-primary); background: var(--color-primary); color: #fff; font-weight: 600; }
.login-submit:hover:not(:disabled) { border-color: var(--color-primary-hover); background: var(--color-primary-hover); color: #fff; }
</style>

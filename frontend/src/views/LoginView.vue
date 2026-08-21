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
  <section>
    <h1>登录</h1>
    <form @submit.prevent="submit">
      <input v-model="username" required autocomplete="username" placeholder="用户名">
      <input v-model="password" required type="password" autocomplete="current-password" placeholder="密码">
      <p v-if="error" role="alert">{{ error }}</p>
      <button :disabled="submitting">{{ submitting ? '登录中…' : '进入工作台' }}</button>
    </form>
  </section>
</template>

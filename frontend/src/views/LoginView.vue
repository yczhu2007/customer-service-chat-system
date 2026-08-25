<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { login } from '../api/auth-api'

const router = useRouter()
const auth = useAuthStore()
const formRef = ref()
const form = ref({ username: '', password: '' })
const submitting = ref(false)
const error = ref('')

const rules = {
  username: [{ required: true, message: '请输入登录编号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function submit() {
  error.value = ''
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    const result = await login({ username: form.value.username, password: form.value.password })
    const payload = result?.data
    const roles = payload?.roles || []
    if (!payload?.token || !payload?.userId) {
      throw new Error('登录响应缺少认证信息')
    }
    auth.login({ token: payload.token, userId: payload.userId, username: payload.username || form.value.username.trim(), nickname: payload.nickname, roles })
    await router.push(`/${auth.homeRole.toLowerCase()}`)
  } catch (e) {
    error.value = e.message || '登录失败'
  } finally {
    submitting.value = false
  }
}
</script>
<template>
  <section class="login-screen">
    <el-form ref="formRef" class="login-card" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
      <div class="login-brand">
        <h1>客服工作台</h1>
        <p>Customer Service Chat</p>
      </div>
      <el-form-item label="登录编号" prop="username">
        <el-input v-model="form.username" autocomplete="username" placeholder="请输入登录编号" />
      </el-form-item>
      <div class="password-field">
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password autocomplete="current-password" placeholder="请输入密码" />
        </el-form-item>
        <RouterLink class="forgot-link" to="/forgot-password">忘记密码</RouterLink>
      </div>
      <el-alert v-if="error" class="login-error" :title="error" type="error" :closable="false" />
      <el-button class="login-submit" native-type="submit" type="primary" :loading="submitting">{{ submitting ? '登录中…' : '进入工作台' }}</el-button>
      <p class="register-prompt">还没有账号？<RouterLink to="/register">立即注册</RouterLink></p>
    </el-form>
  </section>
</template>

<style scoped>
.login-screen { display: grid; min-height: calc(100vh - 54px); place-items: center; padding: 24px; }
.login-card { width: min(400px, 100%); padding: 32px; border: 1px solid var(--color-line); border-radius: 14px; background: var(--color-paper); box-shadow: 0 12px 32px rgba(24, 29, 38, .08); }
.login-brand { margin-bottom: 26px; text-align: center; }
.login-brand h1 { margin: 0; font-size: 22px; }
.login-brand p { margin: 6px 0 0; color: var(--color-faint); font: 11px var(--font-mono); letter-spacing: .14em; text-transform: uppercase; }
.login-card :deep(.el-form-item) { margin-bottom: 14px; }
.login-card :deep(.el-form-item__label) { margin-bottom: 6px; color: var(--color-muted); font-size: 12px; }
.login-card :deep(.el-input__wrapper) { min-height: 40px; }
.login-error { padding: 0; border: 0; background: transparent; }
.login-error :deep(.el-alert__title) { color: var(--color-danger); font-size: 12px; font-weight: 400; }
.login-submit { height: 40px; padding: 9px 14px; }
.password-field { position: relative; }
.forgot-link { position: absolute; top: 0; right: 0; color: var(--color-primary); font-size: 12px; text-decoration: none; }
.forgot-link:hover { text-decoration: underline; }
.login-error { margin: 0 0 12px; }
.login-submit { width: 100%; border-color: var(--color-primary); background: var(--color-primary); color: #fff; font-weight: 600; }
.login-submit:hover:not(:disabled) { border-color: var(--color-primary-hover); background: var(--color-primary-hover); color: #fff; }
.register-prompt { margin: 16px 0 0; color: var(--color-muted); text-align: center; font-size: 12px; }
.register-prompt a { color: var(--color-primary); text-decoration: none; font-weight: 600; }
.register-prompt a:hover { text-decoration: underline; }
</style>

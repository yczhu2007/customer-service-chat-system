<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { register } from '../api/auth-api'

const router = useRouter()
const username = ref('')
const password = ref('')
const submitting = ref(false)
const error = ref('')
const success = ref('')

async function submit() {
  error.value = ''
  success.value = ''
  if (password.value.length < 8) { error.value = '密码至少需要8个字符'; return }
  submitting.value = true
  try {
    const result = await register({ username: username.value.trim(), password: password.value })
    const recoveryCode = result?.data?.recoveryCode
    success.value = recoveryCode ? `注册成功，请保存恢复码：${recoveryCode}` : '注册成功，请返回登录'
  } catch (e) { error.value = e.message || '注册失败' } finally { submitting.value = false }
}
</script>
<template>
  <section class="account-screen"><form class="account-card" @submit.prevent="submit">
    <h1>注册普通用户</h1>
    <label>用户名<input v-model="username" required minlength="3" maxlength="64" pattern="[A-Za-z0-9_]+" autocomplete="username"></label>
    <label>密码<input v-model="password" required type="password" minlength="8" autocomplete="new-password"></label>
    <p v-if="error" class="error">{{ error }}</p><p v-if="success" class="success">{{ success }}</p>
    <button :disabled="submitting">{{ submitting ? '提交中…' : '注册' }}</button>
    <RouterLink to="/login">返回登录</RouterLink>
  </form></section>
</template>
<style scoped>
.account-screen{display:grid;min-height:calc(100vh - 54px);place-items:center;padding:24px}.account-card{width:min(400px,100%);display:grid;gap:14px;padding:30px;border:1px solid var(--color-line);border-radius:14px;background:var(--color-paper)}h1{margin:0 0 8px;font-size:22px}label{display:grid;gap:6px;color:var(--color-muted);font-size:12px}input{width:100%;padding:9px 11px;box-sizing:border-box}.account-card button{padding:10px;border:0;border-radius:8px;background:var(--color-primary);color:#fff}.account-card a{text-align:center;color:var(--color-primary);font-size:13px}.error{color:var(--color-danger);margin:0}.success{color:var(--color-success);margin:0;word-break:break-word}
</style>

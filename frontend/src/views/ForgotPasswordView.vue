<script setup>
import { ref } from 'vue'
import { resetPassword } from '../api/auth-api'

const form = ref({ username: '', recoveryCode: '', newPassword: '' })
const error = ref('')
const result = ref('')
const submitting = ref(false)
async function submit() {
  error.value = ''; result.value = ''
  if (form.value.newPassword.length < 8) { error.value = '新密码至少需要8个字符'; return }
  submitting.value = true
  try {
    const response = await resetPassword(form.value)
    result.value = response?.data?.recoveryCode ? `密码已重置，新恢复码：${response.data.recoveryCode}` : '密码已重置，请返回登录'
  } catch (e) { error.value = e.message || '密码重置失败' } finally { submitting.value = false }
}
</script>
<template>
  <section class="account-screen"><form class="account-card" @submit.prevent="submit">
    <h1>重置密码</h1>
    <label>用户名<input v-model="form.username" required autocomplete="username"></label>
    <label>恢复码<input v-model="form.recoveryCode" required placeholder="例如 1234-abcd-5678-efgh"></label>
    <label>新密码<input v-model="form.newPassword" required type="password" minlength="8" autocomplete="new-password"></label>
    <p v-if="error" class="error">{{ error }}</p><p v-if="result" class="success">{{ result }}</p>
    <button :disabled="submitting">{{ submitting ? '提交中…' : '重置密码' }}</button>
    <RouterLink to="/login">返回登录</RouterLink>
  </form></section>
</template>
<style scoped>
.account-screen{display:grid;min-height:calc(100vh - 54px);place-items:center;padding:24px}.account-card{width:min(400px,100%);display:grid;gap:14px;padding:30px;border:1px solid var(--color-line);border-radius:14px;background:var(--color-paper)}h1{margin:0 0 8px;font-size:22px}label{display:grid;gap:6px;color:var(--color-muted);font-size:12px}input{width:100%;padding:9px 11px;box-sizing:border-box}.account-card button{padding:10px;border:0;border-radius:8px;background:var(--color-primary);color:#fff}.account-card a{text-align:center;color:var(--color-primary);font-size:13px}.error{color:var(--color-danger);margin:0}.success{color:var(--color-success);margin:0;word-break:break-word}
</style>

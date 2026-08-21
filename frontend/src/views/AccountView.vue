<script setup>
import { onMounted, ref } from 'vue'
import { getProfile, updateProfile, updatePassword, regenerateRecoveryCode } from '../api/auth-api'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const username = ref(auth.userId || '')
const currentPassword = ref('')
const newPassword = ref('')
const recoveryCode = ref('')
const message = ref('')
const error = ref('')
const loading = ref(false)
onMounted(async () => { try { const result = await getProfile(); username.value = result?.data?.username || username.value } catch (e) { error.value = e.message } })
async function saveProfile() { await run(async () => { const result = await updateProfile({ username: username.value.trim() }); username.value = result?.data?.username || username.value; auth.userId = username.value; message.value = '用户名已更新' }) }
async function savePassword() { await run(async () => { await updatePassword({ currentPassword: currentPassword.value, newPassword: newPassword.value }); currentPassword.value = ''; newPassword.value = ''; message.value = '密码已更新，请重新登录' }) }
async function createRecoveryCode() { await run(async () => { const result = await regenerateRecoveryCode(); recoveryCode.value = result?.data?.recoveryCode || ''; message.value = '请立即保存新的恢复码' }) }
async function run(action) { error.value = ''; message.value = ''; loading.value = true; try { await action() } catch (e) { error.value = e.message || '操作失败' } finally { loading.value = false } }
</script>
<template>
  <section class="account-screen"><div class="account-card">
    <h1>账号管理</h1>
    <p v-if="error" class="error">{{ error }}</p><p v-if="message" class="success">{{ message }}</p>
    <label>用户名<input v-model="username" minlength="3" maxlength="64"></label>
    <button :disabled="loading" @click="saveProfile">保存用户名</button>
    <hr>
    <label>当前密码<input v-model="currentPassword" type="password"></label>
    <label>新密码<input v-model="newPassword" type="password" minlength="8"></label>
    <button :disabled="loading" @click="savePassword">修改密码</button>
    <hr><button :disabled="loading" @click="createRecoveryCode">生成新的恢复码</button>
    <p v-if="recoveryCode" class="recovery">{{ recoveryCode }}</p>
  </div></section>
</template>
<style scoped>
.account-screen{display:grid;min-height:calc(100vh - 54px);place-items:start center;padding:32px 24px}.account-card{width:min(520px,100%);display:grid;gap:12px;padding:30px;border:1px solid var(--color-line);border-radius:14px;background:var(--color-paper)}h1{margin:0 0 8px;font-size:22px}label{display:grid;gap:6px;color:var(--color-muted);font-size:12px}input{width:100%;padding:9px 11px;box-sizing:border-box}.account-card button{padding:10px;border:0;border-radius:8px;background:var(--color-primary);color:#fff;cursor:pointer}.account-card button:disabled{opacity:.55}hr{width:100%;border:0;border-top:1px solid var(--color-line)}.error{color:var(--color-danger);margin:0}.success{color:var(--color-success);margin:0}.recovery{padding:10px;background:#f6f8ff;color:var(--color-primary);font-family:var(--font-mono);word-break:break-all}
</style>

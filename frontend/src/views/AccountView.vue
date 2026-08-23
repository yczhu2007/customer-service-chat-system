<script setup>
import { onMounted, ref } from 'vue'
import { getProfile, updateProfile, updatePassword, regenerateRecoveryCode } from '../api/auth-api'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const formRef = ref()
const form = ref({ username: '', currentPassword: '', newPassword: '' })
const recoveryCode = ref('')
const message = ref('')
const error = ref('')
const loading = ref(false)

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 64, message: '用户名长度为 3-64 个字符', trigger: 'blur' },
  ],
  currentPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, message: '新密码至少需要 8 个字符', trigger: 'blur' },
  ],
}

onMounted(async () => {
  try {
    const result = await getProfile()
    form.value.username = result?.data?.username || auth.userId || ''
  } catch (e) {
    error.value = e.message
  }
})

async function validateFields(fields) {
  try {
    await formRef.value.validateField(fields)
    return true
  } catch {
    return false
  }
}

async function saveProfile() {
  if (!await validateFields('username')) return
  await run(async () => {
    const result = await updateProfile({ username: form.value.username.trim() })
    form.value.username = result?.data?.username || form.value.username
    auth.updateUsername(form.value.username)
    message.value = '用户名已更新'
  })
}

async function savePassword() {
  if (!await validateFields(['currentPassword', 'newPassword'])) return
  await run(async () => {
    await updatePassword({ currentPassword: form.value.currentPassword, newPassword: form.value.newPassword })
    form.value.currentPassword = ''
    form.value.newPassword = ''
    message.value = '密码已更新，请重新登录'
  })
}

async function createRecoveryCode() { await run(async () => { const result = await regenerateRecoveryCode(); recoveryCode.value = result?.data?.recoveryCode || ''; message.value = '请立即保存新的恢复码' }) }
async function run(action) { error.value = ''; message.value = ''; loading.value = true; try { await action() } catch (e) { error.value = e.message || '操作失败' } finally { loading.value = false } }
</script>
<template>
  <section class="account-screen"><div class="account-card">
    <h1>账号管理</h1>
    <el-alert v-if="error" class="error" :title="error" type="error" :closable="false" />
    <el-alert v-if="message" class="success" :title="message" type="success" :closable="false" />
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item label="用户名" prop="username">
        <el-input v-model="form.username" />
      </el-form-item>
      <el-button type="primary" :loading="loading" @click="saveProfile">保存用户名</el-button>
      <el-divider />
      <el-form-item label="当前密码" prop="currentPassword">
        <el-input v-model="form.currentPassword" type="password" show-password />
      </el-form-item>
      <el-form-item label="新密码" prop="newPassword">
        <el-input v-model="form.newPassword" type="password" show-password />
      </el-form-item>
      <el-button type="primary" :loading="loading" @click="savePassword">修改密码</el-button>
      <el-divider />
      <el-button type="primary" plain :loading="loading" @click="createRecoveryCode">生成新的恢复码</el-button>
    </el-form>
    <el-alert v-if="recoveryCode" class="recovery" :title="recoveryCode" type="info" :closable="false" />
  </div></section>
</template>
<style scoped>
.account-screen{display:grid;min-height:calc(100vh - 54px);place-items:start center;padding:32px 24px}.account-card{width:min(520px,100%);display:grid;gap:12px;padding:30px;border:1px solid var(--color-line);border-radius:14px;background:var(--color-paper)}h1{margin:0 0 8px;font-size:22px}label{display:grid;gap:6px;color:var(--color-muted);font-size:12px}input{width:100%;padding:9px 11px;box-sizing:border-box}.account-card button{padding:10px;border:0;border-radius:8px;background:var(--color-primary);color:#fff;cursor:pointer}.account-card button:disabled{opacity:.55}hr{width:100%;border:0;border-top:1px solid var(--color-line)}.error{color:var(--color-danger);margin:0}.success{color:var(--color-success);margin:0}.recovery{padding:10px;background:#f6f8ff;color:var(--color-primary);font-family:var(--font-mono);word-break:break-all}
.account-card { display: block; }
.account-card > :deep(.el-alert) { margin-bottom: 12px; padding: 0; border: 0; background: transparent; }
.account-card > :deep(.el-form) { display: grid; gap: 12px; }
.account-card :deep(.el-form-item) { margin-bottom: 0; }
.account-card :deep(.el-form-item__label) { margin-bottom: 6px; color: var(--color-muted); font-size: 12px; }
.account-card :deep(.el-input__wrapper) { min-height: 40px; }
.account-card :deep(.el-button) { min-height: 40px; }
.account-card :deep(.el-divider) { margin: 0; }
.account-card > .recovery { padding: 10px; background: #f6f8ff; }
.account-card > .recovery :deep(.el-alert__title) { color: var(--color-primary); font-family: var(--font-mono); font-size: 14px; font-weight: 400; word-break: break-all; }
.account-card > :deep(.el-alert--error .el-alert__title) { color: var(--color-danger); font-size: 14px; font-weight: 400; }
.account-card > :deep(.el-alert--success .el-alert__title) { color: var(--color-success); font-size: 14px; font-weight: 400; }
</style>

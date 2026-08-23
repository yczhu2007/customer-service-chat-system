<script setup>
import { ref } from 'vue'
import { resetPassword } from '../api/auth-api'

const formRef = ref()
const form = ref({ username: '', recoveryCode: '', newPassword: '' })
const error = ref('')
const result = ref('')
const submitting = ref(false)

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  recoveryCode: [{ required: true, message: '请输入恢复码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, message: '新密码至少需要8个字符', trigger: 'blur' },
  ],
}

async function submit() {
  error.value = ''
  result.value = ''
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    const response = await resetPassword(form.value)
    result.value = response?.data?.recoveryCode ? `密码已重置，新恢复码：${response.data.recoveryCode}` : '密码已重置，请返回登录'
  } catch (e) { error.value = e.message || '密码重置失败' } finally { submitting.value = false }
}
</script>
<template>
  <section class="account-screen"><el-form ref="formRef" class="account-card" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
    <h1>重置密码</h1>
    <el-form-item label="用户名" prop="username">
      <el-input v-model="form.username" autocomplete="username" />
    </el-form-item>
    <el-form-item label="恢复码" prop="recoveryCode">
      <el-input v-model="form.recoveryCode" placeholder="例如 1234-abcd-5678-efgh" />
    </el-form-item>
    <el-form-item label="新密码" prop="newPassword">
      <el-input v-model="form.newPassword" type="password" show-password autocomplete="new-password" />
    </el-form-item>
    <el-alert v-if="error" class="error" :title="error" type="error" :closable="false" />
    <el-alert v-if="result" class="success" :title="result" type="success" :closable="false" />
    <el-button native-type="submit" type="primary" :loading="submitting">{{ submitting ? '提交中…' : '重置密码' }}</el-button>
    <RouterLink to="/login">返回登录</RouterLink>
  </el-form></section>
</template>
<style scoped>
.account-screen{display:grid;min-height:calc(100vh - 54px);place-items:center;padding:24px}.account-card{width:min(400px,100%);display:grid;gap:14px;padding:30px;border:1px solid var(--color-line);border-radius:14px;background:var(--color-paper)}h1{margin:0 0 8px;font-size:22px}label{display:grid;gap:6px;color:var(--color-muted);font-size:12px}input{width:100%;padding:9px 11px;box-sizing:border-box}.account-card button{padding:10px;border:0;border-radius:8px;background:var(--color-primary);color:#fff}.account-card a{text-align:center;color:var(--color-primary);font-size:13px}.error{color:var(--color-danger);margin:0}.success{color:var(--color-success);margin:0;word-break:break-word}
.account-card :deep(.el-form-item) { margin-bottom: 14px; }
.account-card :deep(.el-form-item__label) { margin-bottom: 6px; color: var(--color-muted); font-size: 12px; }
.account-card :deep(.el-input__wrapper) { min-height: 40px; }
.account-card :deep(.el-button) { width: 100%; height: 40px; }
.account-card :deep(.el-alert) { padding: 0; border: 0; background: transparent; }
.account-card :deep(.el-alert__title) { font-size: 12px; font-weight: 400; }
.account-card :deep(.el-alert--error .el-alert__title) { color: var(--color-danger); }
.account-card :deep(.el-alert--success .el-alert__title) { color: var(--color-success); }
</style>

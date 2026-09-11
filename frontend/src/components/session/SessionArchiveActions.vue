<script setup>
import { ref, computed, watch } from 'vue'
import { useChatStore } from '../../stores/chat'
import { ARCHIVE_STATUS_OPTIONS, archiveStatusLabel, archiveStatusStyle } from '../../constants/session-ui'

const chat = useChatStore()

const ARCHIVE_STATUSES = ARCHIVE_STATUS_OPTIONS

const VALID_TRANSITIONS = {
  null: ['COMPLETED', 'PENDING', 'ON_HOLD', 'OTHER'],
  COMPLETED: ['COMPLETED', 'PENDING', 'ON_HOLD', 'OTHER'],
  PENDING: ['COMPLETED', 'PENDING', 'ON_HOLD', 'OTHER'],
  ON_HOLD: ['COMPLETED', 'PENDING', 'ON_HOLD', 'OTHER'],
  OTHER: ['COMPLETED', 'PENDING', 'ON_HOLD', 'OTHER'],
}

const remark = ref('')
const saving = ref(false)
const savingRemark = ref(false)
const errorMsg = ref('')
const successMsg = ref('')

const session = computed(() => chat.activeSession)
const currentArchiveStatus = computed(() => session.value?.archiveStatus || null)
const isClosed = computed(() => session.value?.status === 'CLOSED')

watch(() => chat.activeSessionId, () => {
  remark.value = chat.activeSession?.archiveRemark || ''
  errorMsg.value = ''
  successMsg.value = ''
}, { immediate: true })

const availableStatuses = computed(() => {
  const valid = VALID_TRANSITIONS[currentArchiveStatus.value] || []
  return ARCHIVE_STATUSES.filter((s) => valid.includes(s.code))
})

async function setArchive(code) {
  errorMsg.value = ''
  successMsg.value = ''

  if (!chat.activeSessionId) {
    errorMsg.value = '请先选择会话'
    return
  }

  saving.value = true
  try {
    await chat.updateArchiveStatus(chat.activeSessionId, {
      archiveStatus: code,
    })
    successMsg.value = `已归档为：${archiveStatusLabel(code)}`
    setTimeout(() => { successMsg.value = '' }, 2000)
  } catch (e) {
    errorMsg.value = e.message || '归档操作失败'
  } finally {
    saving.value = false
  }
}

async function saveRemark() {
  errorMsg.value = ''
  successMsg.value = ''
  if (!chat.activeSessionId) {
    errorMsg.value = '请先选择会话'
    return
  }
  if (remark.value.length > 255) {
    errorMsg.value = '备注不能超过255个字符'
    return
  }
  savingRemark.value = true
  try {
    const savedRemark = remark.value.trim()
    await chat.saveArchiveRemark(chat.activeSessionId, savedRemark)
    remark.value = savedRemark
    successMsg.value = '备注已保存'
    setTimeout(() => { successMsg.value = '' }, 2000)
  } catch (e) {
    errorMsg.value = e.message || '备注保存失败'
  } finally {
    savingRemark.value = false
  }
}
</script>

<template>
  <div class="archive-actions">
    <h4 class="archive-title">归档操作</h4>

    <div v-if="!session" class="no-data">请先选择会话</div>
    <div v-else-if="!isClosed" class="no-data">会话结束后可进行归档</div>

    <template v-else>
      <div v-if="currentArchiveStatus" class="current-status">
        当前状态：
        <el-tag
          size="small"
          effect="light"
          class="current-badge"
          :style="archiveStatusStyle(currentArchiveStatus)"
        >
          {{ ARCHIVE_STATUSES.find((s) => s.code === currentArchiveStatus)?.label || currentArchiveStatus }}
        </el-tag>
      </div>

      <div class="field">
        <label class="field-label">备注</label>
        <el-input
          v-model="remark"
          type="textarea"
          :rows="2"
          maxlength="255"
          show-word-limit
          class="remark-input"
          placeholder="归档备注（可选）"
        />
        <el-button size="small" :loading="savingRemark" :disabled="saving" @click="saveRemark">
          保存备注
        </el-button>
      </div>

      <div class="btn-group">
        <el-button
          v-for="s in availableStatuses"
          :key="s.code"
          size="small"
          plain
          :disabled="saving || s.code === currentArchiveStatus"
          class="archive-btn"
          :style="{ borderColor: s.color, color: s.color }"
          @click="setArchive(s.code)"
        >
          {{ s.label }}
        </el-button>
      </div>

      <el-alert v-if="errorMsg" :title="errorMsg" type="error" :closable="false" class="msg" />
      <el-alert v-if="successMsg" :title="successMsg" type="success" :closable="false" class="msg" />
    </template>
  </div>
</template>

<style scoped>
.archive-actions {
  padding: 0.75rem;
}
.archive-title {
  font-size: 0.85rem;
  font-weight: 600;
  color: #111827;
  margin: 0 0 0.75rem;
}
.no-data {
  color: #9ca3af;
  font-size: 0.8rem;
  text-align: center;
  padding: 0.5rem 0;
}
.current-status {
  font-size: 0.8rem;
  color: #374151;
  margin-bottom: 0.5rem;
}
.current-badge {
  display: inline-block;
  padding: 0.1rem 0.4rem;
  border-radius: 4px;
  font-size: 0.75rem;
  font-weight: 500;
}
.field {
  margin-bottom: 0.5rem;
}
.field-label {
  display: block;
  font-size: 0.75rem;
  color: #6b7280;
  margin-bottom: 0.2rem;
}
.remark-input {
  width: 100%;
  font-size: 0.825rem;
  padding: 0.35rem 0.5rem;
  border: 1px solid #d1d5db;
  border-radius: 0.375rem;
  outline: none;
  resize: none;
  box-sizing: border-box;
  font-family: inherit;
}
.remark-input:focus {
  border-color: #3b82f6;
}
.btn-group {
  display: flex;
  gap: 0.375rem;
  flex-wrap: wrap;
}
.archive-btn {
  flex: 1;
  min-width: 0;
  padding: 0.35rem 0.5rem;
  background: white;
  border: 1px solid;
  border-radius: 0.375rem;
  cursor: pointer;
  font-size: 0.75rem;
  transition: background 0.15s;
}
.archive-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.archive-btn:not(:disabled):hover {
  background: #f9fafb;
}
.msg {
  font-size: 0.75rem;
  margin-top: 0.375rem;
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
}
.error {
  background: #fee2e2;
  color: #991b1b;
}
.success {
  background: #d1fae5;
  color: #065f46;
}
</style>

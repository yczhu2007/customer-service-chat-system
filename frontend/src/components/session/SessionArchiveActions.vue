<script setup>
import { ref, computed } from 'vue'
import { useChatStore } from '../../stores/chat'

const chat = useChatStore()

const ARCHIVE_STATUSES = [
  { code: 'COMPLETED', label: '已完成', color: '#059669' },
  { code: 'PENDING', label: '待处理', color: '#d97706' },
  { code: 'ON_HOLD', label: '搁置', color: '#6366f1' },
  { code: 'OTHER', label: '其他', color: '#6b7280' },
]

/**
 * Valid state transitions (mirrors backend rules).
 * A session can always be transitioned to any status (reopen is allowed).
 * The backend allows free transitions; we keep the same policy.
 */
const VALID_TRANSITIONS = {
  null: ['COMPLETED', 'PENDING', 'ON_HOLD', 'OTHER'],
  COMPLETED: ['COMPLETED', 'PENDING', 'ON_HOLD', 'OTHER'],
  PENDING: ['COMPLETED', 'PENDING', 'ON_HOLD', 'OTHER'],
  ON_HOLD: ['COMPLETED', 'PENDING', 'ON_HOLD', 'OTHER'],
  OTHER: ['COMPLETED', 'PENDING', 'ON_HOLD', 'OTHER'],
}

const remark = ref('')
const saving = ref(false)
const errorMsg = ref('')
const successMsg = ref('')

const session = computed(() => chat.activeSession)
const currentArchiveStatus = computed(() => session.value?.archiveStatus || null)
const isClosed = computed(() => session.value?.status === 'CLOSED')

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

  if (remark.value.length > 255) {
    errorMsg.value = '备注不能超过255个字符'
    return
  }

  saving.value = true
  try {
    await chat.updateArchiveStatus(chat.activeSessionId, {
      archiveStatus: code,
      remark: remark.value.trim() || undefined,
    })
    successMsg.value = `已归档为: ${ARCHIVE_STATUSES.find((s) => s.code === code)?.label || code}`
    remark.value = ''
    setTimeout(() => { successMsg.value = '' }, 2000)
  } catch (e) {
    errorMsg.value = e.message || '归档操作失败'
  } finally {
    saving.value = false
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
        当前状态:
        <span class="current-badge">
          {{ ARCHIVE_STATUSES.find((s) => s.code === currentArchiveStatus)?.label || currentArchiveStatus }}
        </span>
      </div>

      <div class="field">
        <label class="field-label">备注</label>
        <textarea
          v-model="remark"
          rows="2"
          maxlength="255"
          class="remark-input"
          placeholder="归档备注（可选）"
        />
      </div>

      <div class="btn-group">
        <button
          v-for="s in availableStatuses"
          :key="s.code"
          :disabled="saving || s.code === currentArchiveStatus"
          class="archive-btn"
          :style="{ borderColor: s.color, color: s.color }"
          @click="setArchive(s.code)"
        >
          {{ s.label }}
        </button>
      </div>

      <div v-if="errorMsg" class="msg error">{{ errorMsg }}</div>
      <div v-if="successMsg" class="msg success">{{ successMsg }}</div>
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
  background: #e0f2fe;
  color: #0284c7;
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

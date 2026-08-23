<script setup>
import { ref, watch, computed } from 'vue'
import { useChatStore } from '../../stores/chat'
import { useAuthStore } from '../../stores/auth'
import { categoryLabel } from '../../constants/session-ui'

const chat = useChatStore()
const auth = useAuthStore()

const PRIORITIES = ['LOW', 'NORMAL', 'HIGH', 'URGENT']
const CATEGORIES = ['ACCOUNT', 'PAYMENT', 'TECHNICAL', 'AFTER_SALES', 'OTHER']

const title = ref('')
const priority = ref('NORMAL')
const category = ref('')
const tagsInput = ref('')
const saving = ref(false)
const errorMsg = ref('')
const successMsg = ref('')

/** Whether the current user is the assigned agent for this session */
const isAssignedAgent = computed(() => {
  const session = chat.activeSession
  return session && session.agentId === auth.userId
})

/** Populate form from metadata */
watch(
  () => chat.activeMetadata,
  (m) => {
    if (m) {
      title.value = m.title || ''
      priority.value = m.priority || 'NORMAL'
      category.value = m.category || ''
      tagsInput.value = (m.tags || []).join(', ')
    }
  },
  { immediate: true }
)

/** Parse comma-separated tags and normalize/deduplicate them. */
function parseTags() {
  return [...new Set(
    tagsInput.value
      .split(',')
      .map((t) => t.trim().toLowerCase())
      .filter(Boolean)
  )]
}

async function save() {
  errorMsg.value = ''
  successMsg.value = ''

  if (!title.value.trim()) {
    errorMsg.value = '标题不能为空'
    return
  }
  if (title.value.length > 100) {
    errorMsg.value = '标题长度不能超过100个字符'
    return
  }

  const tags = parseTags()
  if (tags.length > 10) {
    errorMsg.value = '标签数量不能超过10个'
    return
  }

  saving.value = true
  try {
    await chat.updateSessionMetadata(chat.activeSessionId, {
      title: title.value.trim(),
      priority: priority.value,
      category: category.value || undefined,
      tags,
    })
    successMsg.value = '元数据已更新'
    setTimeout(() => { successMsg.value = '' }, 2000)
  } catch (e) {
    errorMsg.value = e.message || '更新失败'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="metadata-editor">
    <h4 class="editor-title">会话元数据</h4>

    <div v-if="!chat.activeMetadata" class="no-data">请先选择会话</div>

    <template v-else>
      <div class="field">
        <label class="field-label">标题</label>
        <el-input
          v-model="title"
          maxlength="100"
          show-word-limit
          :disabled="!isAssignedAgent"
          class="field-input"
          placeholder="会话标题"
        />
      </div>

      <div class="field">
        <label class="field-label">优先级</label>
        <el-select v-model="priority" :disabled="!isAssignedAgent" class="field-select">
          <el-option v-for="p in PRIORITIES" :key="p" :label="p" :value="p" />
        </el-select>
      </div>

      <div class="field">
        <label class="field-label">分类</label>
        <el-select v-model="category" :disabled="!isAssignedAgent" class="field-select">
          <el-option label="未分类" value="" />
          <el-option v-for="c in CATEGORIES" :key="c" :label="categoryLabel(c)" :value="c" />
        </el-select>
      </div>

      <div class="field">
        <label class="field-label">标签 <span class="hint">（逗号分隔，最多10个）</span></label>
        <el-input
          v-model="tagsInput"
          :disabled="!isAssignedAgent"
          class="field-input"
          placeholder="tag1, tag2, tag3"
        />
      </div>

      <el-alert v-if="errorMsg" :title="errorMsg" type="error" :closable="false" class="msg" />
      <el-alert v-if="successMsg" :title="successMsg" type="success" :closable="false" class="msg" />

      <el-button
        v-if="isAssignedAgent"
        type="primary"
        :loading="saving"
        class="save-btn"
        @click="save"
      >
        {{ saving ? '保存中…' : '保存' }}
      </el-button>
      <p v-else class="not-allowed">仅分配的客服可编辑元数据</p>
    </template>
  </div>
</template>

<style scoped>
.metadata-editor {
  padding: 0.75rem;
}
.editor-title {
  font-size: 0.85rem;
  font-weight: 600;
  color: #111827;
  margin: 0 0 0.75rem;
}
.no-data {
  color: #9ca3af;
  font-size: 0.8rem;
  text-align: center;
  padding: 1rem 0;
}
.field {
  margin-bottom: 0.625rem;
}
.field-label {
  display: block;
  font-size: 0.75rem;
  color: #6b7280;
  margin-bottom: 0.2rem;
}
.hint {
  font-size: 0.65rem;
  color: #9ca3af;
}
.field-input,
.field-select {
  width: 100%;
  font-size: 0.825rem;
}
.field-input :deep(.el-input__wrapper),
.field-select :deep(.el-select__wrapper) {
  min-height: 34px;
  padding: 1px 8px;
  border-radius: 0.375rem;
}

.msg {
  font-size: 0.75rem;
  margin: 0.375rem 0;
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
.save-btn {
  width: 100%;
  padding: 0.4rem;
  background: #3b82f6;
  color: white;
  border: none;
  border-radius: 0.375rem;
  cursor: pointer;
  font-size: 0.825rem;
  margin-top: 0.5rem;
}
.save-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.save-btn:not(:disabled):hover {
  background: #2563eb;
}
.not-allowed {
  font-size: 0.75rem;
  color: #9ca3af;
  text-align: center;
  margin-top: 0.5rem;
}
</style>

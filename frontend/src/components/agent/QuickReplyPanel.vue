<script setup>
import { ref, onMounted } from 'vue'
import {
  listQuickReplies,
  createQuickReply,
  updateQuickReply,
  deleteQuickReply,
} from '../../api/chat-api'

const emit = defineEmits(['insert'])

const replies = ref([])
const loading = ref(false)
const errorMsg = ref('')

// Form state
const editing = ref(false) // false = create, object = editing
const formTitle = ref('')
const formContent = ref('')
const formSortOrder = ref(0)
const saving = ref(false)

async function loadReplies() {
  loading.value = true
  errorMsg.value = ''
  try {
    const result = await listQuickReplies()
    replies.value = result?.data || result || []
  } catch (e) {
    errorMsg.value = e.message
  } finally {
    loading.value = false
  }
}

function startCreate() {
  editing.value = null
  formTitle.value = ''
  formContent.value = ''
  formSortOrder.value = 0
}

function startEdit(reply) {
  editing.value = reply
  formTitle.value = reply.title
  formContent.value = reply.content
  formSortOrder.value = reply.sortOrder || 0
}

function cancelForm() {
  editing.value = false
  formTitle.value = ''
  formContent.value = ''
  formSortOrder.value = 0
}

async function saveForm() {
  errorMsg.value = ''
  if (!formTitle.value.trim()) {
    errorMsg.value = '标题不能为空'
    return
  }
  if (!formContent.value.trim()) {
    errorMsg.value = '内容不能为空'
    return
  }
  if (formTitle.value.length > 50) {
    errorMsg.value = '标题不能超过50个字符'
    return
  }
  if (formContent.value.length > 1000) {
    errorMsg.value = '内容不能超过1000个字符'
    return
  }

  const data = {
    title: formTitle.value.trim(),
    content: formContent.value.trim(),
    sortOrder: formSortOrder.value || 0,
  }

  saving.value = true
  try {
    if (editing.value && editing.value.id) {
      await updateQuickReply(editing.value.id, data)
    } else {
      await createQuickReply(data)
    }
    cancelForm()
    await loadReplies()
  } catch (e) {
    errorMsg.value = e.message
  } finally {
    saving.value = false
  }
}

async function removeReply(id) {
  if (!confirm('确定删除此快捷回复？')) return
  try {
    await deleteQuickReply(id)
    await loadReplies()
  } catch (e) {
    errorMsg.value = e.message
  }
}

function insertReply(reply) {
  emit('insert', reply.content)
}

onMounted(loadReplies)
</script>

<template>
  <div class="quick-reply-panel">
    <div class="panel-header">
      <h4 class="panel-title">快捷回复</h4>
      <button class="add-btn" @click="startCreate">+ 新增</button>
    </div>

    <div v-if="loading" class="loading">加载中…</div>
    <div v-else-if="errorMsg && !replies.length" class="error">{{ errorMsg }}</div>

    <!-- Reply list -->
    <ul v-if="replies.length && editing === false" class="reply-list">
      <li v-for="r in replies" :key="r.id" class="reply-item">
        <div class="reply-info" @click="insertReply(r)">
          <span class="reply-title">{{ r.title }}</span>
          <span class="reply-preview">{{ r.content.slice(0, 60) }}{{ r.content.length > 60 ? '…' : '' }}</span>
        </div>
        <div class="reply-actions">
          <button class="action-btn" @click="startEdit(r)">编辑</button>
          <button class="action-btn danger" @click="removeReply(r.id)">删除</button>
        </div>
      </li>
    </ul>

    <div v-if="!replies.length && !loading && editing === false" class="empty">
      暂无快捷回复，点击"新增"创建
    </div>

    <!-- Edit/Create form -->
    <div v-if="editing !== false" class="form-area">
      <div class="form-field">
        <label class="form-label">标题</label>
        <input v-model="formTitle" maxlength="50" class="form-input" placeholder="快捷回复标题" />
      </div>
      <div class="form-field">
        <label class="form-label">内容</label>
        <textarea v-model="formContent" rows="4" maxlength="1000" class="form-textarea" placeholder="回复内容" />
      </div>
      <div class="form-field">
        <label class="form-label">排序</label>
        <input v-model.number="formSortOrder" type="number" min="0" max="9999" class="form-input short" />
      </div>
      <div v-if="errorMsg" class="error">{{ errorMsg }}</div>
      <div class="form-buttons">
        <button :disabled="saving" class="save-btn" @click="saveForm">
          {{ saving ? '保存中…' : '保存' }}
        </button>
        <button class="cancel-btn" @click="cancelForm">取消</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.quick-reply-panel {
  padding: 0.75rem;
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.5rem;
}
.panel-title {
  font-size: 0.85rem;
  font-weight: 600;
  color: #111827;
  margin: 0;
}
.add-btn {
  font-size: 0.75rem;
  padding: 0.2rem 0.6rem;
  background: #3b82f6;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}
.add-btn:hover {
  background: #2563eb;
}
.loading,
.empty {
  text-align: center;
  color: #9ca3af;
  font-size: 0.8rem;
  padding: 0.75rem 0;
}
.error {
  font-size: 0.75rem;
  color: #991b1b;
  background: #fee2e2;
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  margin-bottom: 0.5rem;
}
.reply-list {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: 200px;
  overflow-y: auto;
}
.reply-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.375rem 0;
  border-bottom: 1px solid #f3f4f6;
}
.reply-info {
  flex: 1;
  cursor: pointer;
  min-width: 0;
}
.reply-info:hover .reply-title {
  color: #2563eb;
}
.reply-title {
  display: block;
  font-size: 0.8rem;
  font-weight: 500;
  color: #111827;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.reply-preview {
  display: block;
  font-size: 0.7rem;
  color: #9ca3af;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.reply-actions {
  display: flex;
  gap: 0.25rem;
  flex-shrink: 0;
}
.action-btn {
  font-size: 0.65rem;
  padding: 0.15rem 0.4rem;
  background: #f3f4f6;
  border: 1px solid #d1d5db;
  border-radius: 3px;
  cursor: pointer;
}
.action-btn:hover {
  background: #e5e7eb;
}
.action-btn.danger {
  color: #dc2626;
  border-color: #fca5a5;
}
.action-btn.danger:hover {
  background: #fee2e2;
}
.form-area {
  margin-top: 0.5rem;
}
.form-field {
  margin-bottom: 0.5rem;
}
.form-label {
  display: block;
  font-size: 0.75rem;
  color: #6b7280;
  margin-bottom: 0.15rem;
}
.form-input,
.form-textarea {
  width: 100%;
  font-size: 0.8rem;
  padding: 0.3rem 0.5rem;
  border: 1px solid #d1d5db;
  border-radius: 0.375rem;
  outline: none;
  box-sizing: border-box;
  font-family: inherit;
}
.form-input:focus,
.form-textarea:focus {
  border-color: #3b82f6;
}
.form-input.short {
  width: 6rem;
}
.form-textarea {
  resize: none;
}
.form-buttons {
  display: flex;
  gap: 0.5rem;
}
.save-btn {
  flex: 1;
  padding: 0.35rem;
  background: #3b82f6;
  color: white;
  border: none;
  border-radius: 0.375rem;
  cursor: pointer;
  font-size: 0.8rem;
}
.save-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.cancel-btn {
  padding: 0.35rem 0.75rem;
  background: #f3f4f6;
  border: 1px solid #d1d5db;
  border-radius: 0.375rem;
  cursor: pointer;
  font-size: 0.8rem;
}
</style>

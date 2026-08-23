<script setup>
import { ref, watch } from 'vue'
import { useChatStore } from '../../stores/chat'

const props = defineProps({
  sessionId: { type: String, required: true },
  disabled: { type: Boolean, default: false },
  /** Optional text to insert into the composer (e.g. from quick reply) */
  insertText: { type: String, default: '' },
})

const emit = defineEmits(['inserted'])

const chat = useChatStore()
const text = ref('')
const uploadRef = ref(null)
const uploading = ref(false)

watch(
  () => props.insertText,
  (val) => {
    if (val) {
      text.value = val
      emit('inserted')
    }
  }
)

function sendText() {
  const content = text.value.trim()
  if (!content || props.disabled) return
  chat.sendMessage(props.sessionId, 'TEXT', content)
  text.value = ''
}

function onKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendText()
  }
}

async function uploadFile(options) {
  const file = options.file
  if (!file || props.disabled) return
  uploading.value = true
  try {
    const result = await chat.uploadAttachment(props.sessionId, file)
    const type = result?.messageType || (file.type.startsWith('image/') ? 'IMAGE' : 'FILE')
    chat.sendMessage(props.sessionId, type, result?.contentUrl)
    options.onSuccess?.(result)
  } catch (e) {
    chat.error = e.message || '附件上传失败，请稍后重试'
    options.onError?.(e)
  } finally {
    uploading.value = false
    uploadRef.value?.clearFiles()
  }
}

</script>

<template>
  <div class="composer">
    <div class="text-row">
      <el-input
        v-model="text"
        type="textarea"
        :disabled="disabled"
        placeholder="输入消息…"
        :rows="2"
        resize="none"
        class="text-input"
        @keydown="onKeydown"
      />
      <el-button
        type="primary"
        :disabled="disabled || !text.trim()"
        class="send-btn"
        @click="sendText"
      >
        发送
      </el-button>
    </div>
    <div class="file-row">
      <el-upload
        ref="uploadRef"
        :http-request="uploadFile"
        :show-file-list="false"
        :auto-upload="true"
        :disabled="disabled || uploading"
        class="file-upload"
      >
        <el-button size="small" :loading="uploading" :disabled="disabled || uploading">选择附件</el-button>
      </el-upload>
      <span v-if="uploading" class="upload-hint">上传中…</span>
    </div>
  </div>
</template>

<style scoped>
.composer {
  flex: 0 0 auto;
  min-width: 0;
  border-top: 1px solid #e5e7eb;
  padding: 0.75rem;
  background: white;
}
.text-row {
  display: flex;
  gap: 0.5rem;
  align-items: flex-end;
}
.text-input {
  flex: 1;
  min-width: 0;
}
.text-input :deep(.el-textarea__inner) {
  min-height: 60px !important;
  padding: 0.5rem;
  font-size: 0.9rem;
  line-height: 1.5;
  border-radius: 0.375rem;
  box-shadow: none;
}
.text-input :deep(.el-textarea__inner:focus) {
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.15);
}
.send-btn {
  padding: 0.5rem 1.25rem;
  background: #3b82f6;
  color: white;
  border: none;
  border-radius: 0.375rem;
  cursor: pointer;
  font-size: 0.9rem;
  white-space: nowrap;
}
.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.send-btn:not(:disabled):hover {
  background: #2563eb;
}
.file-row {
  margin-top: 0.5rem;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.file-input {
  font-size: 0.8rem;
}
.upload-hint {
  font-size: 0.8rem;
  color: #6b7280;
}
</style>

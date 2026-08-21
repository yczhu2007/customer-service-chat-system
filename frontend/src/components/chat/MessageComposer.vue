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
const fileInput = ref(null)
const uploading = ref(false)

/** Watch for external insert text (quick reply) */
watch(
  () => props.insertText,
  (val) => {
    if (val) {
      text.value = val
      emit('inserted')
    }
  }
)

/** Send text message on Enter or button click */
function sendText() {
  const content = text.value.trim()
  if (!content || props.disabled) return
  chat.sendMessage(props.sessionId, 'TEXT', content)
  text.value = ''
}

/** Handle Enter key */
function onKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendText()
  }
}

/** Upload file attachment */
async function onFileChange(e) {
  const file = e.target.files?.[0]
  if (!file || props.disabled) return
  uploading.value = true
  try {
    const result = await chat.uploadAttachment(props.sessionId, file)
    // Send as IMAGE or FILE message via STOMP
    const type = file.type.startsWith('image/') ? 'IMAGE' : 'FILE'
    chat.sendMessage(props.sessionId, type, result?.id || file.name)
  } catch {
    // Error handled by store
  } finally {
    uploading.value = false
    if (fileInput.value) fileInput.value.value = ''
  }
}
</script>

<template>
  <div class="composer">
    <div class="text-row">
      <textarea
        v-model="text"
        :disabled="disabled"
        placeholder="输入消息…"
        rows="2"
        class="text-input"
        @keydown="onKeydown"
      />
      <button
        :disabled="disabled || !text.trim()"
        class="send-btn"
        @click="sendText"
      >
        发送
      </button>
    </div>
    <div class="file-row">
      <input
        ref="fileInput"
        type="file"
        :disabled="disabled || uploading"
        class="file-input"
        @change="onFileChange"
      />
      <span v-if="uploading" class="upload-hint">上传中…</span>
    </div>
  </div>
</template>

<style scoped>
.composer {
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
  resize: none;
  border: 1px solid #d1d5db;
  border-radius: 0.375rem;
  padding: 0.5rem;
  font-size: 0.9rem;
  font-family: inherit;
  outline: none;
}
.text-input:focus {
  border-color: #3b82f6;
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

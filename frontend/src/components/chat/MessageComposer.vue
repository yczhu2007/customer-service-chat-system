<script setup>
import { ref, watch, onUnmounted } from 'vue'
import { useChatStore } from '../../stores/chat'
import { useAuthStore } from '../../stores/auth'

const props = defineProps({
  sessionId: { type: String, required: true },
  disabled: { type: Boolean, default: false },
  /** Optional text to insert into the composer (e.g. from quick reply) */
  insertText: { type: String, default: '' },
})

const emit = defineEmits(['inserted'])

const chat = useChatStore()
const auth = useAuthStore()
const text = ref('')
const draftTimer = ref(null)
const uploadRef = ref(null)
const uploading = ref(false)
const draggingImage = ref(false)
const typingTimer = ref(null)
const typingActive = ref(false)
const pendingAttachment = ref(null)
const attachmentPreviewUrl = ref(null)
const attachmentPreviewName = ref('')

function draftKey(sessionId = props.sessionId) {
  return `chat-draft:${auth.userId || 'anonymous'}:${auth.role || 'unknown'}:${sessionId}`
}

function restoreDraft(sessionId = props.sessionId) {
  if (draftTimer.value) clearTimeout(draftTimer.value)
  text.value = sessionId ? (localStorage.getItem(draftKey(sessionId)) || '') : ''
}

function saveDraft() {
  if (!props.sessionId) return
  if (draftTimer.value) clearTimeout(draftTimer.value)
  draftTimer.value = setTimeout(() => {
    const value = text.value
    if (value) localStorage.setItem(draftKey(), value)
    else localStorage.removeItem(draftKey())
  }, 500)
}

function clearDraft() {
  if (draftTimer.value) clearTimeout(draftTimer.value)
  localStorage.removeItem(draftKey())
}

watch(() => props.sessionId, (sessionId, previousSessionId) => {
  if (typingTimer.value) clearTimeout(typingTimer.value)
  if (typingActive.value && previousSessionId) chat.sendTyping(previousSessionId, false)
  typingActive.value = false
  clearPendingAttachment()
  restoreDraft(sessionId)
}, { immediate: true })
watch(() => props.disabled, (disabled) => {
  if (disabled) chat.clearReplyTarget()
})

watch(text, () => {
  saveDraft()
  notifyTyping()
})

onUnmounted(() => {
  if (draftTimer.value) clearTimeout(draftTimer.value)
  stopTyping()
  clearAttachmentPreview()
})
watch(
  () => props.insertText,
  (val) => {
    if (val) {
      text.value = val
      emit('inserted')
    }
  }
)

function stopTyping() {
  if (typingTimer.value) clearTimeout(typingTimer.value)
  if (typingActive.value) chat.sendTyping(props.sessionId, false)
  typingActive.value = false
}

function notifyTyping() {
  if (!text.value.trim() || props.disabled) return stopTyping()
  if (!typingActive.value) {
    chat.sendTyping(props.sessionId, true)
    typingActive.value = true
  }
  if (typingTimer.value) clearTimeout(typingTimer.value)
  typingTimer.value = setTimeout(stopTyping, 3500)
}

async function uploadDroppedFile(file) {
  if (!file || props.disabled || uploading.value) return
  if (file.size <= 0) { chat.error = '文件不能为空'; return }
  if (file.size > 10 * 1024 * 1024) { chat.error = '文件不能超过 10MB'; return }
  await uploadFile({ file, onSuccess: () => {}, onError: () => {} })
}

function onPaste(event) {
  const image = [...(event.clipboardData?.files || [])].find((file) => file.type.startsWith('image/'))
  if (!image) return
  event.preventDefault()
  uploadDroppedFile(image)
}

function onDragOver(event) {
  if ([...(event.dataTransfer?.types || [])].includes('Files')) { event.preventDefault(); draggingImage.value = true }
}

function onDrop(event) {
  event.preventDefault()
  draggingImage.value = false
  const file = [...(event.dataTransfer?.files || [])][0]
  if (file) uploadDroppedFile(file)
  else chat.error = '请拖入文件'
}

function sendPendingAttachment() {
  if (!pendingAttachment.value || props.disabled) return
  const { type, contentUrl } = pendingAttachment.value
  chat.sendMessage(props.sessionId, type, contentUrl)
  clearPendingAttachment()
}

function clearPendingAttachment() {
  pendingAttachment.value = null
  clearAttachmentPreview()
}

function setAttachmentPreview(file) {
  clearAttachmentPreview()
  if (!file?.type?.startsWith('image/') || typeof URL.createObjectURL !== 'function') return
  attachmentPreviewUrl.value = URL.createObjectURL(file)
  attachmentPreviewName.value = file.name
}

function clearAttachmentPreview() {
  if (attachmentPreviewUrl.value && typeof URL.revokeObjectURL === 'function') {
    URL.revokeObjectURL(attachmentPreviewUrl.value)
  }
  attachmentPreviewUrl.value = null
  attachmentPreviewName.value = ''
}

function sendText() {
  const content = text.value.trim()
  if (!content || props.disabled) return
  chat.sendMessage(props.sessionId, 'TEXT', content)
  chat.clearReplyTarget()
  stopTyping()
  clearDraft()
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
  setAttachmentPreview(file)
  uploading.value = true
  try {
    const result = await chat.uploadAttachment(props.sessionId, file)
    const type = result?.messageType || (file.type.startsWith('image/') ? 'IMAGE' : 'FILE')
    pendingAttachment.value = { type, contentUrl: result?.contentUrl, name: file.name }
    options.onSuccess?.(result)
  } catch (e) {
    clearAttachmentPreview()
    chat.error = e.message || '附件上传失败，请稍后重试'
    options.onError?.(e)
  } finally {
    uploading.value = false
    uploadRef.value?.clearFiles()
  }
}

</script>

<template>
  <div
    class="composer"
    :class="{ 'is-dragging': draggingImage }"
    @paste="onPaste"
    @dragover="onDragOver"
    @dragleave="draggingImage = false"
    @drop="onDrop"
  >
    <div v-if="chat.replyingTo" class="reply-card">
      <div><strong>回复：{{ chat.replyingTo.senderRole === 'AGENT' ? '客服' : '用户' }}</strong></div>
      <span>{{ chat.replyingTo.recalled ? '原消息已撤回' : (chat.replyingTo.content || '附件消息') }}</span>
      <button type="button" @click="chat.clearReplyTarget()">取消引用</button>
    </div>
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
      <div v-if="attachmentPreviewUrl" class="attachment-preview">
        <img :src="attachmentPreviewUrl" :alt="attachmentPreviewName" class="attachment-image-preview" />
      </div>
      <span v-if="uploading" class="upload-hint">上传中…</span>
      <template v-else-if="pendingAttachment">
        <span class="upload-hint">已选择：{{ pendingAttachment.name }}</span>
        <el-button size="small" type="primary" :disabled="disabled" @click="sendPendingAttachment">发送附件</el-button>
        <el-button size="small" :disabled="disabled" @click="clearPendingAttachment">取消</el-button>
      </template>
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
.composer.is-dragging { background: #eff6ff; outline: 2px dashed #3b82f6; outline-offset: -4px; }
.reply-card { display: flex; align-items: center; gap: .5rem; margin-bottom: .5rem; padding: .4rem .6rem; border-left: 3px solid #3b82f6; background: #f3f4f6; color: #4b5563; font-size: .8rem; }
.reply-card span { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.reply-card button { border: 0; background: transparent; color: #2563eb; cursor: pointer; font-size: .75rem; }
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
.attachment-preview { display: flex; align-items: center; }
.attachment-image-preview { width: 40px; height: 40px; object-fit: cover; border: 1px solid #dbeafe; border-radius: .3rem; }
</style>

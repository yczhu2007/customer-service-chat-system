<script setup>
import { ref, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { useChatStore } from '../../stores/chat'
import { useAuthStore } from '../../stores/auth'
import { fetchAttachmentBlob } from '../../api/chat-api'

const props = defineProps({
  sessionId: { type: String, default: null },
  closed: { type: Boolean, default: false },
})

const chat = useChatStore()
const auth = useAuthStore()
const listEl = ref(null)
const blobCache = ref({})
const unavailableAttachmentUrls = new Set()

function replyMessageId(message) {
  return message.replyToMessageId || message.reply_to_message_id || null
}

function replySource(message) {
  const messageId = replyMessageId(message)
  return messageId ? (chat.messages.find((item) => item.id === messageId) || null) : null
}

function replyAuthorLabel(message) {
  const source = replySource(message)
  const role = source?.senderRole || message.replyPreviewSenderRole || message.reply_preview_sender_role
  return role === 'AGENT' ? '客服' : role === 'USER' ? '用户' : '消息'
}

function replySummary(message) {
  const source = replySource(message)
  if (source) return source.recalled ? '原消息已撤回' : (source.content || '附件消息')
  return message.replyPreview || message.reply_preview || '原消息暂未加载'
}

async function scrollToReply(message) {
  const messageId = replyMessageId(message)
  if (!messageId) return
  let target = document.getElementById(`message-${messageId}`)
  while (!target && chat.historyHasMore && !chat.historyLoadingMore) {
    await chat.loadMoreHistory(props.sessionId)
    await nextTick()
    target = document.getElementById(`message-${messageId}`)
  }
  if (target) target.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

/** Determine message ownership */
function isMine(msg) {
  return msg.senderId === auth.userId
}

function isSystem(msg) {
  return !msg.senderId || msg.type === 'SYSTEM'
}

/** Format time for display */
function formatTime(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  if (isNaN(d.getTime())) return ''
  return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

function formatFileSize(size) {
  if (!Number.isFinite(size)) return ''
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / (1024 * 1024)).toFixed(1)} MB`
}

function releaseBlob(messageId) {
  const cached = blobCache.value[messageId]
  if (cached?.url && typeof URL.revokeObjectURL === 'function') {
    URL.revokeObjectURL(cached.url)
  }
  delete blobCache.value[messageId]
}

function releaseAllBlobs() {
  Object.keys(blobCache.value).forEach((messageId) => releaseBlob(messageId))
}

/** Fetch blob URL for IMAGE/FILE messages */
async function loadBlob(msg) {
  if (!msg.id || unavailableAttachmentUrls.has(msg.content)) {
    if (msg.id) blobCache.value[msg.id] = null
    return
  }
  if (blobCache.value[msg.id]) return
  const sessionId = props.sessionId
  try {
    const attachment = await fetchAttachmentBlob(msg.content)
    if (sessionId !== props.sessionId || !chat.messages.some((item) => item.id === msg.id)) {
      if (attachment?.url && typeof URL.revokeObjectURL === 'function') {
        URL.revokeObjectURL(attachment.url)
      }
      return
    }
    blobCache.value[msg.id] = attachment
  } catch (error) {
    if (error?.message === 'HTTP 404') unavailableAttachmentUrls.add(msg.content)
    blobCache.value[msg.id] = null
  }
}

function previewImages() {
  return chat.messages
    .filter((message) => message.type === 'IMAGE' && !message.recalled && blobCache.value[message.id]?.url)
    .map((message) => blobCache.value[message.id].url)
}

/** Scroll to bottom */
function scrollToBottom() {
  nextTick(() => {
    if (listEl.value) {
      listEl.value.scrollTop = listEl.value.scrollHeight
    }
  })
}

watch(
  () => [props.sessionId, ...chat.messages.map((msg) => msg.id || msg.clientMsgId)],
  () => {
    const messageIds = new Set(chat.messages.map((msg) => msg.id).filter(Boolean))
    Object.keys(blobCache.value)
      .filter((messageId) => !messageIds.has(messageId))
      .forEach((messageId) => releaseBlob(messageId))
    scrollToBottom()
    chat.messages
      .filter((msg) => (msg.type === 'IMAGE' || msg.type === 'FILE') && !msg.recalled)
      .forEach((msg) => loadBlob(msg))
  },
  { immediate: true }
)

onMounted(() => scrollToBottom())
onUnmounted(releaseAllBlobs)
</script>

<template>
  <div ref="listEl" class="message-list" role="log" aria-live="polite">
    <button
      v-if="chat.activeSessionId === props.sessionId && chat.historyHasMore"
      class="load-more-btn"
      :disabled="chat.historyLoadingMore"
      @click="chat.loadMoreHistory(props.sessionId)"
    >
      {{ chat.historyLoadingMore ? '加载中…' : '加载更早消息' }}
    </button>
    <div v-if="chat.messagesLoading" class="loading-hint">加载历史消息中…</div>
    <template v-for="msg in chat.messages" :key="msg.clientMsgId || msg.id">
      <!-- System messages -->
      <div v-if="isSystem(msg)" class="message-row system">
        <div class="message-bubble system-bubble">
          <span>{{ msg.content }}</span>
        </div>
      </div>

      <!-- Regular messages -->
      <div v-else :id="msg.id ? `message-${msg.id}` : null" class="message-row" :class="{ mine: isMine(msg), other: !isMine(msg) }">
        <div class="message-meta">
          <span class="sender">{{ isMine(msg) ? '我' : (msg.senderRole === 'AGENT' ? '客服' : '用户') }}</span>
          <span class="time">{{ formatTime(msg.createTime) }}</span>
          <span v-if="msg.recalled" class="recalled-tag">(已撤回)</span>
          <span v-if="msg.sendState === 'SENDING'" class="message-state">发送中…</span>
          <span v-else-if="msg.sendState === 'FAILED'" class="message-state failed" :title="msg.failureReason">发送失败</span>
          <span v-else-if="msg.ackStatus === 'STORED'" class="message-state">已保存</span>
          <span v-else-if="msg.ackStatus === 'DELIVERED'" class="message-state">已送达</span>
          <span v-if="isMine(msg) && msg.readByPeer" class="message-state">已读</span>
        </div>

        <!-- TEXT message -->
        <div v-if="msg.type === 'TEXT' && !msg.recalled" class="message-bubble">
          <button v-if="replyMessageId(msg)" class="reply-preview" @click="scrollToReply(msg)">
            <span class="reply-preview-label">引用{{ replyAuthorLabel(msg) }}</span>
            <span class="reply-preview-content">{{ replySummary(msg) }}</span>
          </button>
          <span>{{ msg.content }}</span>
          <div class="message-actions">
            <button v-if="!closed && msg.id && !msg.recalled" class="message-action" @click="chat.setReplyTarget(msg)">引用</button>
            <button v-if="isMine(msg) && msg.sendState === 'FAILED'" class="message-action" @click="chat.retryMessage(msg.clientMsgId)">重新发送</button>
            <button v-if="!closed && isMine(msg) && msg.id" class="message-action" @click="chat.recallMessage(msg.id)">撤回</button>
          </div>
        </div>

        <!-- IMAGE message -->
        <div v-else-if="msg.type === 'IMAGE'" class="message-bubble image-bubble">
          <template v-if="msg.recalled">
            <span class="recalled-hint">图片已撤回</span>
          </template>
          <template v-else>
            <button v-if="replyMessageId(msg)" class="reply-preview" @click="scrollToReply(msg)">
              <span class="reply-preview-label">引用{{ replyAuthorLabel(msg) }}</span>
              <span class="reply-preview-content">{{ replySummary(msg) }}</span>
            </button>
            <el-image
              v-if="blobCache[msg.id]"
              :src="blobCache[msg.id].url"
              :preview-src-list="previewImages()"
              fit="contain"
              alt="图片消息"
              class="msg-image"
            />
            <span v-else-if="blobCache[msg.id] === null" class="load-error">图片加载失败</span>
            <button v-else class="load-btn" @click="loadBlob(msg)">加载图片</button>
          </template>
          <div v-if="isMine(msg) && !msg.recalled" class="message-actions">
            <button v-if="msg.sendState === 'FAILED'" class="message-action" @click="chat.retryMessage(msg.clientMsgId)">重新发送</button>
            <button v-if="!closed && isMine(msg) && msg.id" class="message-action" @click="chat.recallMessage(msg.id)">撤回</button>
          </div>
        </div>

        <!-- FILE message -->
        <div v-else-if="msg.type === 'FILE'" class="message-bubble file-bubble">
          <template v-if="msg.recalled">
            <span class="recalled-hint">文件已撤回</span>
          </template>
          <template v-else>
            <button v-if="replyMessageId(msg)" class="reply-preview" @click="scrollToReply(msg)">
              <span class="reply-preview-label">引用{{ replyAuthorLabel(msg) }}</span>
              <span class="reply-preview-content">{{ replySummary(msg) }}</span>
            </button>
            <a
              v-if="blobCache[msg.id]"
              :href="blobCache[msg.id].url"
              :download="blobCache[msg.id].name"
              class="file-link"
            >
              <strong>{{ blobCache[msg.id].name }}</strong>
              <span class="file-info">Download file<span v-if="blobCache[msg.id].size"> · {{ formatFileSize(blobCache[msg.id].size) }}</span></span>
            </a>
            <span v-else-if="blobCache[msg.id] === null" class="load-error">文件加载失败</span>
            <span v-else class="load-error">文件加载中…</span>
          </template>
          <div v-if="isMine(msg) && !msg.recalled" class="message-actions">
            <button v-if="msg.sendState === 'FAILED'" class="message-action" @click="chat.retryMessage(msg.clientMsgId)">重新发送</button>
            <button v-if="!closed && isMine(msg) && msg.id" class="message-action" @click="chat.recallMessage(msg.id)">撤回</button>
          </div>
        </div>

        <!-- Recalled text -->
        <div v-else-if="msg.recalled" class="message-bubble recalled-bubble">
          <span class="recalled-hint">消息已撤回</span>
        </div>
      </div>
    </template>

    <div v-if="chat.typingBySession[props.sessionId]" class="typing-hint">对方正在输入…</div>

    <div v-if="!chat.messages.length && !chat.messagesLoading" class="empty-hint">
      暂无消息
    </div>
  </div>
</template>

<style scoped>
.message-list {
  flex: 1;
  min-height: 0;
  min-width: 0;
  overflow-y: auto;
  padding: 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  background: #f9fafb;
}
.message-row {
  display: flex;
  flex-direction: column;
  max-width: 75%;
  min-width: 0;
}
.message-row.mine {
  align-self: flex-end;
  align-items: flex-end;
}
.message-row.other {
  align-self: flex-start;
  align-items: flex-start;
}
.message-row.system {
  align-self: center;
  max-width: 100%;
}
.message-meta {
  font-size: 0.75rem;
  color: #6b7280;
  margin-bottom: 2px;
  display: flex;
  gap: 0.5rem;
  align-items: center;
}
.message-bubble {
  max-width: 100%;
  padding: 0.5rem 0.75rem;
  border-radius: 0.5rem;
  word-break: break-word;
  white-space: pre-wrap;
  font-size: 0.9rem;
  line-height: 1.5;
}
.mine .message-bubble {
  background: #3b82f6;
  color: white;
}
.other .message-bubble {
  background: white;
  color: #1f2937;
  border: 1px solid #e5e7eb;
}
.system-bubble {
  background: transparent;
  color: #9ca3af;
  font-size: 0.8rem;
  text-align: center;
}
.image-bubble {
  padding: 0.25rem;
}
.msg-image {
  max-width: min(240px, 62vw);
  max-height: min(240px, 42vh);
  border-radius: 0.375rem;
  display: block;
}
.file-bubble {
  padding: 0.5rem 0.75rem;
}
.file-link {
  color: inherit;
  text-decoration: underline;
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
}
.load-more-btn { align-self: center; padding: 0.35rem 0.75rem; border: 1px solid #d1d5db; border-radius: 999px; background: white; color: #374151; cursor: pointer; font-size: 0.75rem; }
.load-more-btn:disabled { opacity: 0.55; cursor: not-allowed; }
.reply-preview {
  display: flex;
  flex-direction: column;
  width: 100%;
  margin: 0 0 .45rem;
  padding: .4rem .55rem;
  border: 1px solid rgba(255,255,255,.45);
  border-left: 3px solid currentColor;
  border-radius: .3rem;
  background: rgba(15,23,42,.14);
  color: inherit;
  text-align: left;
  cursor: pointer;
}
.other .reply-preview {
  border-color: #cbd5e1;
  border-left-color: #3b82f6;
  background: #f1f5f9;
}
.reply-preview-label { font-size: .7rem; font-weight: 700; opacity: .82; }
.reply-preview-content { max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: .8rem; }
.message-actions { display: flex; gap: 0.35rem; margin-top: 0.35rem; }
.message-action { border: 0; padding: 0; background: transparent; color: inherit; font-size: 0.7rem; cursor: pointer; opacity: 0.7; }
.message-action:hover { opacity: 1; text-decoration: underline; }
.message-state { font-size: 0.7rem; color: #6b7280; }
.message-state.failed { color: #dc2626; }
.file-info { font-size: 0.75rem; opacity: 0.75; }
.mine .file-link {
  color: #dbeafe;
}
.load-btn {
  background: none;
  border: 1px solid currentColor;
  border-radius: 4px;
  padding: 0.25rem 0.5rem;
  cursor: pointer;
  font-size: 0.8rem;
  color: inherit;
}
.load-error,
.recalled-hint {
  font-size: 0.8rem;
  opacity: 0.6;
  font-style: italic;
}
.recalled-tag {
  font-size: 0.7rem;
  opacity: 0.5;
}
.typing-hint { align-self: flex-start; padding: .3rem .6rem; color: #6b7280; font-size: .8rem; font-style: italic; }
.loading-hint,
.empty-hint {
  text-align: center;
  color: #9ca3af;
  padding: 2rem;
  font-size: 0.9rem;
}
</style>

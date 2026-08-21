<script setup>
import { ref, watch, nextTick, onMounted } from 'vue'
import { useChatStore } from '../../stores/chat'
import { useAuthStore } from '../../stores/auth'
import { fetchAttachmentBlob } from '../../api/chat-api'

const props = defineProps({
  sessionId: { type: String, default: null },
})

const chat = useChatStore()
const auth = useAuthStore()
const listEl = ref(null)
const blobCache = ref({})

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

/** Fetch blob URL for IMAGE/FILE messages */
async function loadBlob(msg) {
  if (!msg.id) return
  if (blobCache.value[msg.id]) return
  try {
    blobCache.value[msg.id] = await fetchAttachmentBlob(msg.content)
  } catch {
    blobCache.value[msg.id] = null
  }
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
  () => chat.messages.length,
  () => {
    scrollToBottom()
    chat.messages
      .filter((msg) => (msg.type === 'IMAGE' || msg.type === 'FILE') && !msg.recalled)
      .forEach((msg) => loadBlob(msg))
  },
  { immediate: true }
)

onMounted(() => scrollToBottom())
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
      <div v-else class="message-row" :class="{ mine: isMine(msg), other: !isMine(msg) }">
        <div class="message-meta">
          <span class="sender">{{ isMine(msg) ? '我' : (msg.senderRole === 'AGENT' ? '客服' : '用户') }}</span>
          <span class="time">{{ formatTime(msg.createTime) }}</span>
          <span v-if="msg.edited" class="edited-tag">(已编辑)</span>
          <span v-if="msg.recalled" class="recalled-tag">(已撤回)</span>
          <span v-if="msg.ackStatus === 'STORED'" class="message-state">已保存</span>
          <span v-else-if="msg.ackStatus === 'DELIVERED'" class="message-state">已送达</span>
        </div>

        <div v-if="chat.editingMessageId === msg.id" class="edit-area">
          <input v-model="chat.editingContent" class="edit-input" @keyup.enter="chat.editMessage(msg.id)" />
          <button class="message-action" @click="chat.editMessage(msg.id)">保存</button>
          <button class="message-action" @click="chat.cancelEditing">取消</button>
        </div>

        <!-- TEXT message -->
        <div v-else-if="msg.type === 'TEXT' && !msg.recalled" class="message-bubble">
          <span>{{ msg.content }}</span>
          <div v-if="isMine(msg)" class="message-actions">
            <button class="message-action" @click="chat.startEditing(msg)">编辑</button>
            <button class="message-action" @click="chat.recallMessage(msg.id)">撤回</button>
          </div>
        </div>

        <!-- IMAGE message -->
        <div v-else-if="msg.type === 'IMAGE'" class="message-bubble image-bubble">
          <template v-if="msg.recalled">
            <span class="recalled-hint">图片已撤回</span>
          </template>
          <template v-else>
            <img
              v-if="blobCache[msg.id]"
              :src="blobCache[msg.id].url"
              alt="图片消息"
              class="msg-image"
            />
            <span v-else-if="blobCache[msg.id] === null" class="load-error">图片加载失败</span>
            <button v-else class="load-btn" @click="loadBlob(msg)">加载图片</button>
          </template>
        </div>

        <!-- FILE message -->
        <div v-else-if="msg.type === 'FILE'" class="message-bubble file-bubble">
          <template v-if="msg.recalled">
            <span class="recalled-hint">文件已撤回</span>
          </template>
          <template v-else>
            <a
              v-if="blobCache[msg.id]"
              :href="blobCache[msg.id].url"
              :download="blobCache[msg.id].name"
              class="file-link"
            >
              <strong>{{ blobCache[msg.id].name }}</strong>
              <span class="file-info">{{ blobCache[msg.id].type || '文件' }} · {{ formatFileSize(blobCache[msg.id].size) }}</span>
            </a>
            <span v-else-if="blobCache[msg.id] === null" class="load-error">文件加载失败</span>
            <span v-else class="load-error">文件加载中…</span>
          </template>
        </div>

        <!-- Recalled text -->
        <div v-else-if="msg.recalled" class="message-bubble recalled-bubble">
          <span class="recalled-hint">消息已撤回</span>
        </div>
      </div>
    </template>

    <div v-if="!chat.messages.length && !chat.messagesLoading" class="empty-hint">
      暂无消息
    </div>
  </div>
</template>

<style scoped>
.message-list {
  flex: 1;
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
  max-width: 240px;
  max-height: 240px;
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
.message-actions { display: flex; gap: 0.35rem; margin-top: 0.35rem; }
.message-action { border: 0; padding: 0; background: transparent; color: inherit; font-size: 0.7rem; cursor: pointer; opacity: 0.7; }
.message-action:hover { opacity: 1; text-decoration: underline; }
.edit-area { display: flex; gap: 0.35rem; align-items: center; }
.edit-input { min-width: 180px; padding: 0.35rem; border: 1px solid #cbd5e1; border-radius: 4px; }
.message-state { font-size: 0.7rem; color: #6b7280; }
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
.edited-tag,
.recalled-tag {
  font-size: 0.7rem;
  opacity: 0.5;
}
.loading-hint,
.empty-hint {
  text-align: center;
  color: #9ca3af;
  padding: 2rem;
  font-size: 0.9rem;
}
</style>

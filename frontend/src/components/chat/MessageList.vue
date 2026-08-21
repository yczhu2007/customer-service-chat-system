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

/** Fetch blob URL for IMAGE/FILE messages */
async function loadBlob(msg) {
  if (!msg.id) return
  if (blobCache.value[msg.id]) return
  // Content contains the attachment ID for IMAGE/FILE types
  try {
    const url = await fetchAttachmentBlob(msg.content)
    blobCache.value[msg.id] = url
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
  () => scrollToBottom()
)

onMounted(() => scrollToBottom())
</script>

<template>
  <div ref="listEl" class="message-list" role="log" aria-live="polite">
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
        </div>

        <!-- TEXT message -->
        <div v-if="msg.type === 'TEXT' && !msg.recalled" class="message-bubble">
          <span>{{ msg.content }}</span>
        </div>

        <!-- IMAGE message -->
        <div v-else-if="msg.type === 'IMAGE'" class="message-bubble image-bubble">
          <template v-if="msg.recalled">
            <span class="recalled-hint">图片已撤回</span>
          </template>
          <template v-else>
            <img
              v-if="blobCache[msg.id]"
              :src="blobCache[msg.id]"
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
              :href="blobCache[msg.id]"
              :download="msg.content"
              class="file-link"
            >
              📎 {{ msg.content }}
            </a>
            <span v-else-if="blobCache[msg.id] === null" class="load-error">文件加载失败</span>
            <button v-else class="load-btn" @click="loadBlob(msg)">下载文件</button>
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
}
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

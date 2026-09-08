<script setup>
import { ref, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { useChatStore } from '../../stores/chat'
import { useAuthStore } from '../../stores/auth'
import { fetchAttachmentBlob, fetchAttachmentMetadataBatch, fetchAttachmentPreview } from '../../api/chat-api'
import { previewCellText } from './xlsx-preview'

const props = defineProps({
  sessionId: { type: String, default: null },
  closed: { type: Boolean, default: false },
})

const chat = useChatStore()
const auth = useAuthStore()
const listEl = ref(null)
const blobCache = ref({})
const pendingBlobLoads = new Map()
const legacyPreviewCache = new Map()
const attachmentMetadata = ref({})
const pendingMetadataLoads = new Set()
const unavailableAttachmentUrls = new Set()
const highlightedMessageId = ref(null)
const filePreview = ref(null)
const docxPreviewEl = ref(null)
let highlightTimeout = null
let imageObserver = null

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
  if (filePreview.value?.url === cached?.url) filePreview.value = null
  if (cached?.url && typeof URL.revokeObjectURL === 'function') {
    URL.revokeObjectURL(cached.url)
  }
  delete blobCache.value[messageId]
}

function releaseAllBlobs() {
  Object.keys(blobCache.value).forEach((messageId) => releaseBlob(messageId))
  legacyPreviewCache.forEach((attachment) => {
    if (attachment.url && typeof URL.revokeObjectURL === 'function') URL.revokeObjectURL(attachment.url)
  })
  legacyPreviewCache.clear()
}

function closeFilePreview() {
  if (filePreview.value?.revokeOnClose && filePreview.value.url && typeof URL.revokeObjectURL === 'function') {
    URL.revokeObjectURL(filePreview.value.url)
  }
  filePreview.value = null
}

/** Fetch blob URL for IMAGE/FILE messages */
async function loadBlob(msg) {
  if (!msg.id || unavailableAttachmentUrls.has(msg.content)) {
    if (msg.id) blobCache.value[msg.id] = null
    return null
  }
  if (Object.hasOwn(blobCache.value, msg.id)) return blobCache.value[msg.id]
  if (pendingBlobLoads.has(msg.id)) return pendingBlobLoads.get(msg.id)
  const load = (async () => {
    const sessionId = props.sessionId
    try {
      const attachment = await fetchAttachmentBlob(msg.content)
      if (sessionId !== props.sessionId || !chat.messages.some((item) => item.id === msg.id)) {
        if (attachment?.url && typeof URL.revokeObjectURL === 'function') {
          URL.revokeObjectURL(attachment.url)
        }
        return null
      }
      blobCache.value[msg.id] = attachment
      return attachment
    } catch (error) {
      if (error?.message === 'HTTP 404') unavailableAttachmentUrls.add(msg.content)
      blobCache.value[msg.id] = null
      return null
    }
  })()
  pendingBlobLoads.set(msg.id, load)
  try {
    return await load
  } finally {
    pendingBlobLoads.delete(msg.id)
  }
}

function attachmentId(contentUrl) {
  return contentUrl?.match(/^\/chat\/attachments\/([A-Za-z0-9]{32,64})\/content$/)?.[1] || null
}

function observeImage(el, msg) {
  if (!msg) return
  el._imageMessage = msg
  if (typeof IntersectionObserver === 'undefined') {
    loadBlob(msg)
    return
  }
  if (!imageObserver) {
    imageObserver = new IntersectionObserver((entries) => {
      entries.filter((entry) => entry.isIntersecting).forEach((entry) => {
        imageObserver.unobserve(entry.target)
        loadBlob(entry.target._imageMessage)
      })
    }, { root: listEl.value, rootMargin: '240px 0px' })
  }
  imageObserver.observe(el)
}

const vLoadImage = {
  mounted(el, binding) { observeImage(el, binding.value) },
  updated(el, binding) {
    if (el._imageMessage?.id !== binding.value?.id) observeImage(el, binding.value)
  },
  unmounted(el) { imageObserver?.unobserve(el) },
}

async function loadAttachmentMetadata(messages) {
  const pending = messages
    .map((msg) => ({ msg, id: attachmentId(msg.content) }))
    .filter(({ msg, id }) => msg.id && id && !Object.hasOwn(attachmentMetadata.value, msg.id) && !pendingMetadataLoads.has(msg.id))
  if (!pending.length) return
  pending.forEach(({ msg }) => pendingMetadataLoads.add(msg.id))
  try {
    const result = await fetchAttachmentMetadataBatch(pending.map(({ id }) => id))
    const records = result?.data || result || []
    const metadataById = new Map((Array.isArray(records) ? records : [records])
      .filter((attachment) => attachment?.id)
      .map((attachment) => [attachment.id, attachment]))
    if (pending.length === 1 && metadataById.size === 0 && records?.originalName) {
      metadataById.set(pending[0].id, records)
    }
    pending.forEach(({ msg, id }) => {
      if (props.sessionId === msg.sessionId && chat.messages.some((item) => item.id === msg.id)) {
        attachmentMetadata.value[msg.id] = metadataById.get(id) || null
      }
    })
  } catch {
    pending.forEach(({ msg }) => { attachmentMetadata.value[msg.id] = null })
  } finally {
    pending.forEach(({ msg }) => pendingMetadataLoads.delete(msg.id))
  }
}

function previewImages() {
  return chat.messages
    .filter((message) => message.type === 'IMAGE' && !message.recalled && blobCache.value[message.id]?.url)
    .map((message) => blobCache.value[message.id].url)
}

function previewKind(attachment) {
  const name = attachment?.name?.toLowerCase() || ''
  if (attachment?.type === 'application/pdf' || name.endsWith('.pdf')) return 'pdf'
  if (attachment?.type === 'text/plain' || name.endsWith('.txt')) return 'text'
  if (attachment?.type === 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' || name.endsWith('.docx')) return 'docx'
  if (attachment?.type === 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' || name.endsWith('.xlsx')) return 'xlsx'
  if (attachment?.type === 'application/vnd.ms-excel' || name.endsWith('.xls')) return 'xlsx'
  if (attachment?.type === 'application/msword' || name.endsWith('.doc')) return 'legacy-office'
  if (attachment?.type === 'application/vnd.ms-powerpoint' || name.endsWith('.ppt')) return 'legacy-office'
  if (attachment?.type === 'application/vnd.openxmlformats-officedocument.presentationml.presentation' || name.endsWith('.pptx')) return 'legacy-office'
  return null
}

async function openFilePreview(attachment) {
  const kind = previewKind(attachment)
  if (!kind) return
  if (kind === 'pdf') {
    filePreview.value = { kind, name: attachment.name, url: attachment.url }
    return
  }
  if (kind === 'docx') {
    filePreview.value = { kind, name: attachment.name, loading: true }
    await nextTick()
    try {
      const { renderAsync } = await import('docx-preview')
      await renderAsync(attachment.blob, docxPreviewEl.value, null, { inWrapper: false })
      filePreview.value.loading = false
    } catch (error) {
      filePreview.value = { kind, name: attachment.name, error: `DOCX 预览加载失败：${error.message || '文件格式无效'}` }
    }
    return
  }
  if (kind === 'xlsx') {
    filePreview.value = { kind, name: attachment.name, loading: true, sheets: [], sheetIndex: 0 }
    const isLegacyXls = (attachment.name || '').toLowerCase().endsWith('.xls')
    try {
      if (isLegacyXls) {
        /* 老格式 xls 用 SheetJS 在前端解析，无需服务端转换 */
        const XLSX = await import('xlsx')
        const workbook = XLSX.read(await attachment.blob.arrayBuffer(), { type: 'array' })
        filePreview.value = {
          kind,
          name: attachment.name,
          sheetIndex: 0,
          sheets: workbook.SheetNames.map((sheetName) => ({
            name: sheetName,
            rows: XLSX.utils
              .sheet_to_json(workbook.Sheets[sheetName], { header: 1, raw: false, defval: '' })
              .slice(0, 100)
              .map((row) => row.slice(0, 20)),
          })),
        }
        return
      }
      const { Workbook } = await import('exceljs')
      const workbook = new Workbook()
      await workbook.xlsx.load(await attachment.blob.arrayBuffer())
      filePreview.value = {
        kind,
        name: attachment.name,
        sheetIndex: 0,
        sheets: workbook.worksheets.map((sheet) => ({
          name: sheet.name,
          rows: Array.from({ length: Math.min(sheet.rowCount, 100) }, (_, rowIndex) =>
            Array.from({ length: Math.min(sheet.columnCount, 20) }, (_, columnIndex) => {
              const cell = sheet.getRow(rowIndex + 1).getCell(columnIndex + 1)
              return previewCellText(cell)
            })
          ),
        })),
      }
    } catch (error) {
      filePreview.value = { kind, name: attachment.name, error: `${isLegacyXls ? 'XLS' : 'XLSX'} 预览加载失败：${error.message || '文件格式无效'}` }
    }
    return
  }
  filePreview.value = {
    kind,
    name: attachment.name,
    text: attachment.blob ? '加载中…' : '文本预览不可用',
  }
  attachment.blob?.text()
    .then((text) => { if (filePreview.value?.name === attachment.name) filePreview.value.text = text })
    .catch(() => { if (filePreview.value?.name === attachment.name) filePreview.value.text = '文本预览加载失败' })
}

async function previewFile(msg) {
  const metadata = attachmentMetadata.value[msg.id]
  if (previewKind({ name: metadata?.originalName, type: metadata?.contentType }) === 'legacy-office') {
    const id = attachmentId(msg.content)
    if (!id) return
    const cached = legacyPreviewCache.get(msg.id)
    if (cached) {
      filePreview.value = { kind: 'pdf', name: metadata.originalName, url: cached.url }
      return
    }
    const sessionId = props.sessionId
    closeFilePreview()
    filePreview.value = { kind: 'legacy-office', messageId: msg.id, name: metadata.originalName, loading: true }
    try {
      const attachment = await fetchAttachmentPreview(id)
      if (sessionId !== props.sessionId || !chat.messages.some((item) => item.id === msg.id)) {
        if (attachment.url && typeof URL.revokeObjectURL === 'function') URL.revokeObjectURL(attachment.url)
        if (filePreview.value?.messageId === msg.id) filePreview.value = null
        return
      }
      legacyPreviewCache.set(msg.id, attachment)
      filePreview.value = {
        kind: 'pdf',
        name: metadata.originalName,
        url: attachment.url,
      }
    } catch (error) {
      if (sessionId !== props.sessionId || !chat.messages.some((item) => item.id === msg.id)) {
        if (filePreview.value?.messageId === msg.id) filePreview.value = null
        return
      }
      filePreview.value = {
        kind: 'legacy-office',
        name: metadata.originalName,
        error: `文件预览转换失败：${error.message || '请稍后重试'}`,
      }
    }
    return
  }
  const attachment = await loadBlob(msg)
  if (attachment) openFilePreview(attachment)
}

async function downloadFile(msg) {
  const attachment = await loadBlob(msg)
  if (!attachment) return
  const link = document.createElement('a')
  link.href = attachment.url
  link.download = attachment.name
  link.click()
}

/** Scroll to bottom */
function scrollToBottom() {
  nextTick(() => {
    if (listEl.value) {
      listEl.value.scrollTop = listEl.value.scrollHeight
    }
  })
}

function focusMessage(messageId) {
  if (!messageId || props.sessionId !== chat.activeSessionId) return
  const target = document.getElementById(`message-${messageId}`)
  if (target) {
    target.scrollIntoView({ behavior: 'smooth', block: 'center' })
    highlightedMessageId.value = messageId
    if (highlightTimeout) clearTimeout(highlightTimeout)
    highlightTimeout = setTimeout(() => { highlightedMessageId.value = null }, 2000)
    chat.clearFocusedMessage(messageId)
    return
  }
  if (!chat.messagesLoading && chat.historyHasMore && !chat.historyLoadingMore) {
    chat.loadMoreHistory(props.sessionId)
  }
}

watch(
  () => [props.sessionId, ...chat.messages.map((msg) => msg.id || msg.clientMsgId)],
  () => {
    const messageIds = new Set(chat.messages.map((msg) => msg.id).filter(Boolean))
    Object.keys(blobCache.value)
      .filter((messageId) => !messageIds.has(messageId))
      .forEach((messageId) => releaseBlob(messageId))
    Object.keys(attachmentMetadata.value)
      .filter((messageId) => !messageIds.has(messageId))
      .forEach((messageId) => delete attachmentMetadata.value[messageId])
    legacyPreviewCache.forEach((attachment, messageId) => {
      if (!messageIds.has(messageId)) {
        if (attachment.url && typeof URL.revokeObjectURL === 'function') URL.revokeObjectURL(attachment.url)
        legacyPreviewCache.delete(messageId)
      }
    })
    if (chat.focusedMessageId) nextTick(() => focusMessage(chat.focusedMessageId))
    else scrollToBottom()
    loadAttachmentMetadata(chat.messages.filter((msg) => msg.type === 'FILE' && !msg.recalled))
  },
  { immediate: true }
)

onMounted(() => scrollToBottom())
onUnmounted(() => {
  if (highlightTimeout) clearTimeout(highlightTimeout)
  imageObserver?.disconnect()
  imageObserver = null
  closeFilePreview()
  releaseAllBlobs()
})
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
      <div v-if="isSystem(msg)" :id="msg.id ? `message-${msg.id}` : null" class="message-row system" :class="{ 'is-focused': highlightedMessageId === msg.id }">
        <div class="message-bubble system-bubble">
          <span>{{ msg.content }}</span>
        </div>
      </div>

      <!-- Regular messages -->
      <div v-else :id="msg.id ? `message-${msg.id}` : null" class="message-row" :class="{ mine: isMine(msg), other: !isMine(msg), 'is-focused': highlightedMessageId === msg.id }">
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
        <div v-else-if="msg.type === 'IMAGE'" v-load-image="msg.recalled ? null : msg" class="message-bubble image-bubble">
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
            <div v-if="attachmentMetadata[msg.id]" class="file-link">
              <strong>{{ attachmentMetadata[msg.id].originalName }}</strong>
              <span class="file-info">文件 · {{ formatFileSize(attachmentMetadata[msg.id].fileSize) }}</span>
            </div>
            <button
              v-if="attachmentMetadata[msg.id] && previewKind({ name: attachmentMetadata[msg.id].originalName, type: attachmentMetadata[msg.id].contentType })"
              type="button"
              class="file-preview-btn"
              @click="previewFile(msg)"
            >预览</button>
            <button v-if="attachmentMetadata[msg.id]" type="button" class="file-preview-btn" @click="downloadFile(msg)">下载</button>
            <span v-else-if="attachmentMetadata[msg.id] === null" class="load-error">文件信息加载失败</span>
            <span v-else class="file-info">文件信息加载中…</span>
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
    <el-dialog
      v-if="filePreview"
      :model-value="true"
      :title="filePreview.name"
      width="min(900px, 92vw)"
      @close="closeFilePreview"
    >
      <iframe v-if="filePreview.kind === 'pdf'" :src="filePreview.url" class="pdf-preview" :title="filePreview.name" />
      <pre v-else-if="filePreview.kind === 'text'" class="text-preview">{{ filePreview.text }}</pre>
      <div v-else-if="filePreview.kind === 'docx'" ref="docxPreviewEl" class="docx-preview">
        <span v-if="filePreview.loading">加载中…</span>
        <span v-else-if="filePreview.error">{{ filePreview.error }}</span>
      </div>
      <template v-else-if="filePreview.kind === 'xlsx'">
        <span v-if="filePreview.loading">加载中…</span>
        <span v-else-if="filePreview.error">{{ filePreview.error }}</span>
        <template v-else>
          <select v-model="filePreview.sheetIndex" class="sheet-select">
            <option v-for="(sheet, index) in filePreview.sheets" :key="sheet.name" :value="index">{{ sheet.name }}</option>
          </select>
          <div class="xlsx-preview"><table><tbody><tr v-for="(row, rowIndex) in filePreview.sheets[filePreview.sheetIndex]?.rows" :key="rowIndex"><td v-for="(cell, columnIndex) in row" :key="columnIndex">{{ cell }}</td></tr></tbody></table></div>
        </template>
      </template>
      <span v-else-if="filePreview.kind === 'legacy-office'">
        {{ filePreview.loading ? '正在转换预览…' : filePreview.error }}
      </span>
    </el-dialog>
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
.message-row.is-focused .message-bubble { box-shadow: 0 0 0 3px rgba(77, 107, 254, .32); }
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
.file-preview-btn { margin-top: .4rem; border: 0; padding: 0; background: transparent; color: inherit; text-decoration: underline; cursor: pointer; font-size: .8rem; }
.pdf-preview { display: block; width: 100%; height: min(70vh, 720px); border: 0; }
.text-preview { max-height: min(70vh, 720px); margin: 0; overflow: auto; white-space: pre-wrap; word-break: break-word; font: inherit; }
.docx-preview, .xlsx-preview { max-height: min(70vh, 720px); overflow: auto; }
.docx-preview :deep(img) { max-width: 100%; }
.sheet-select { margin-bottom: .75rem; max-width: 100%; }
.xlsx-preview table { border-collapse: collapse; font-size: .85rem; }
.xlsx-preview td { min-width: 5rem; padding: .3rem .45rem; border: 1px solid #d1d5db; white-space: pre-wrap; vertical-align: top; }
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

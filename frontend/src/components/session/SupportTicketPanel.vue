<script setup>
import { computed, ref, watch } from 'vue'
import { useAuthStore } from '../../stores/auth'
import { useChatStore } from '../../stores/chat'
import { categoryLabel, priorityLabel } from '../../constants/session-ui'

const chat = useChatStore()
const auth = useAuthStore()

const statusOptions = [
  { value: 'OPEN', label: '待处理' },
  { value: 'IN_PROGRESS', label: '处理中' },
  { value: 'WAITING_USER', label: '等待用户' },
  { value: 'RESOLVED', label: '已解决' },
]
const description = ref('')
const resolution = ref('')
const status = ref('OPEN')
const saving = ref(false)
const errorMessage = ref('')
const successMessage = ref('')

const ticket = computed(() => chat.activeSupportTicket)
const canEdit = computed(() => chat.activeSession?.agentId === auth.userId && auth.role === 'AGENT')
const statusLabel = computed(() => statusOptions.find((item) => item.value === ticket.value?.status)?.label || '未知状态')
const quickStatuses = computed(() => ({
  OPEN: ['IN_PROGRESS', 'WAITING_USER', 'RESOLVED'],
  IN_PROGRESS: ['WAITING_USER', 'RESOLVED'],
  WAITING_USER: ['IN_PROGRESS', 'RESOLVED'],
  RESOLVED: ['IN_PROGRESS'],
})[ticket.value?.status] || [])
const selectableStatuses = computed(() => {
  const allowed = new Set([ticket.value?.status, ...quickStatuses.value])
  return statusOptions.filter((item) => allowed.has(item.value))
})
const history = computed(() => chat.activeSupportTicketHistory || [])

function formatTime(value) {
  if (!value) return '-'
  const time = new Date(value)
  return Number.isNaN(time.getTime()) ? '-' : time.toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  })
}

watch(ticket, (value) => {
  description.value = value?.description || ''
  resolution.value = value?.resolution || ''
  status.value = value?.status || 'OPEN'
  errorMessage.value = ''
  successMessage.value = ''
}, { immediate: true })

function markDirty() {
  chat.supportTicketFormDirty = true
}

function statusText(value) {
  return statusOptions.find((item) => item.value === value)?.label || '未知状态'
}

async function createTicket() {
  if (!description.value.trim()) {
    errorMessage.value = '问题描述不能为空'
    return
  }
  saving.value = true
  errorMessage.value = ''
  try {
    await chat.createSupportTicket(chat.activeSessionId, { description: description.value.trim() })
    successMessage.value = '工单已创建'
  } catch (error) {
    await chat.loadSupportTicket(chat.activeSessionId)
    if (chat.activeSupportTicket) {
      successMessage.value = '工单已存在，已加载最新内容'
    } else {
      errorMessage.value = error.message || '创建工单失败'
    }
  } finally {
    saving.value = false
  }
}

function retryTicket() {
  errorMessage.value = ''
  return refreshTicket()
}

async function refreshTicket() {
  chat.supportTicketFormDirty = false
  chat.supportTicketStale = false
  await Promise.all([
    chat.loadSupportTicket(chat.activeSessionId),
    chat.loadSupportTicketHistory(chat.activeSessionId),
    chat.loadTransferLogs(chat.activeSessionId),
  ])
}

async function updateTicket() {
  if (!description.value.trim()) {
    errorMessage.value = '问题描述不能为空'
    return
  }
  if (status.value === 'RESOLVED' && !resolution.value.trim()) {
    errorMessage.value = '处理结果不能为空'
    return
  }
  saving.value = true
  errorMessage.value = ''
  try {
    await chat.updateSupportTicket(ticket.value.ticketNo, {
      status: status.value,
      description: description.value.trim(),
      resolution: resolution.value.trim() || undefined,
      version: ticket.value.version,
    })
    successMessage.value = '工单已更新'
  } catch (error) {
    if (error.status === 409) {
      await refreshTicket()
      errorMessage.value = '工单已被其他操作修改，已加载最新内容'
    } else {
      errorMessage.value = error.message || '更新工单失败'
    }
  } finally {
    saving.value = false
  }
}

async function quickUpdate(nextStatus) {
  status.value = nextStatus
  markDirty()
  await updateTicket()
}
</script>

<template>
  <section class="ticket-panel">
    <h3>关联工单</h3>
    <p v-if="!chat.activeSessionId" class="empty">选择会话后查看</p>
    <p v-else-if="chat.supportTicketLoading" class="empty">正在加载工单…</p>
    <div v-else-if="chat.supportTicketError" class="ticket-error">
      <p class="message error">{{ chat.supportTicketError }}</p>
      <el-button size="small" @click="retryTicket">重新加载</el-button>
    </div>

    <template v-else-if="ticket">
      <div class="ticket-head">
        <strong>{{ ticket.ticketNo }}</strong>
        <el-tag size="small" effect="plain">{{ statusLabel }}</el-tag>
      </div>
      <div v-if="chat.supportTicketStale" class="stale-notice">
        工单已有新更新，当前仍保留未保存内容。
        <el-button link type="primary" size="small" @click="refreshTicket">刷新</el-button>
      </div>
      <template v-if="canEdit">
        <p class="timestamps">创建于 {{ formatTime(ticket.createdAt) }} · 更新于 {{ formatTime(ticket.updatedAt) }}</p>
        <label>工单状态</label>
        <el-select v-model="status" class="field" placeholder="选择" @change="markDirty">
          <el-option v-for="item in selectableStatuses" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <div class="quick-actions">
          <el-button v-for="item in quickStatuses" :key="item" size="small" @click="quickUpdate(item)">
            标记为{{ statusOptions.find((option) => option.value === item)?.label }}
          </el-button>
        </div>
        <label>问题描述</label>
        <el-input v-model="description" class="field" type="textarea" :rows="3" maxlength="1000" show-word-limit @input="markDirty" />
        <label>处理结果</label>
        <el-input v-model="resolution" class="field" type="textarea" :rows="2" maxlength="1000" show-word-limit placeholder="解决工单时必填" @input="markDirty" />
        <el-button type="primary" :loading="saving" class="action" @click="updateTicket">更新工单</el-button>
      </template>
      <dl v-else class="readonly-details">
        <dt>会话标题</dt><dd>{{ ticket.title || '新咨询' }}</dd>
        <dt>工单状态</dt><dd>{{ statusLabel }}</dd>
        <template v-if="ticket.priority"><dt>优先级</dt><dd>{{ priorityLabel(ticket.priority) }}</dd></template>
        <template v-if="ticket.category"><dt>分类</dt><dd>{{ categoryLabel(ticket.category) }}</dd></template>
        <template v-if="ticket.agentNickname"><dt>负责客服</dt><dd>{{ ticket.agentNickname }}</dd></template>
        <dt>问题描述</dt><dd>{{ ticket.description }}</dd>
        <template v-if="ticket.resolution"><dt>处理结果</dt><dd>{{ ticket.resolution }}</dd></template>
        <dt>更新时间</dt><dd>{{ formatTime(ticket.updatedAt) }}</dd>
      </dl>
      <div class="ticket-history">
        <h4>操作历史</h4>
        <p v-if="!history.length" class="empty">暂无状态变更</p>
        <ul v-else>
          <li v-for="item in history" :key="item.id">
            <span>{{ item.fromStatus ? `${statusText(item.fromStatus)} → ${statusText(item.toStatus)}` : `创建为${statusText(item.toStatus)}` }}</span>
            <small>{{ item.operatorNickname || '未知用户' }} · {{ formatTime(item.createdAt) }}</small>
          </li>
        </ul>
      </div>
      <div class="ticket-history">
        <h4>负责人变更</h4>
        <p v-if="!chat.transferLogs.length" class="empty">暂无负责人变更</p>
        <ul v-else>
          <li v-for="item in chat.transferLogs" :key="item.id">
            <span>{{ item.sourceAgentNickname || '未知客服' }} → {{ item.targetAgentNickname || '未知客服' }}</span>
            <small>{{ formatTime(item.createTime) }}<template v-if="item.reason"> · {{ item.reason }}</template></small>
          </li>
        </ul>
      </div>
    </template>

    <template v-else-if="canEdit">
      <label>问题描述</label>
      <el-input v-model="description" class="field" type="textarea" :rows="3" maxlength="1000" show-word-limit placeholder="描述需要跟进的问题" @input="markDirty" />
      <el-button type="primary" :loading="saving" class="action" @click="createTicket">创建工单</el-button>
    </template>
    <p v-else class="empty">该会话暂未创建工单</p>

    <p v-if="errorMessage" class="message error">{{ errorMessage }}</p>
    <p v-if="successMessage" class="message success">{{ successMessage }}</p>
  </section>
</template>

<style scoped>
.ticket-panel { padding: 10px 12px; border-top: 1px solid var(--color-line); background: var(--color-paper); }
h3 { margin: 0 0 8px; font-size: 13px; color: var(--color-ink); }
label { display: block; margin: 8px 0 4px; color: var(--color-muted); font-size: 12px; }
.empty { margin: 0; color: var(--color-faint); font-size: 12px; }
.ticket-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; font-size: 12px; }
.timestamps { margin: 0 0 8px; color: var(--color-faint); font-size: 11px; }
.field { width: 100%; }
.quick-actions { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 7px; }
.action { width: 100%; margin-top: 10px; }
.readonly-details { display: grid; grid-template-columns: 64px 1fr; gap: 7px 8px; margin: 0; font-size: 12px; }
.readonly-details dt { color: var(--color-muted); }
.readonly-details dd { margin: 0; color: var(--color-ink); white-space: pre-wrap; word-break: break-word; }
.message { margin: 8px 0 0; font-size: 12px; }
.ticket-error .message { margin: 0 0 8px; }
.stale-notice { margin-bottom: 8px; padding: 6px 8px; background: #fff7e6; color: var(--color-muted); font-size: 12px; }
.ticket-history { margin-top: 12px; }
.ticket-history h4 { margin: 0 0 6px; color: var(--color-ink); font-size: 12px; }
.ticket-history ul { margin: 0; padding: 0; list-style: none; }
.ticket-history li { padding: 5px 0; border-top: 1px solid var(--color-line); font-size: 12px; }
.ticket-history li span, .ticket-history small { display: block; }
.ticket-history small { margin-top: 2px; color: var(--color-faint); font-size: 11px; }
.error { color: var(--color-danger); }
.success { color: var(--color-success); }
</style>

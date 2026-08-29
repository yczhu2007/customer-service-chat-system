<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { request } from '../../services/http-client'
import { createAdminSession, deleteAdminSession, findTransferLogs, updateAdminSession } from '../../api/admin-api'
import { getSupportTicket } from '../../api/chat-api'
import { ARCHIVE_STATUS_OPTIONS, CATEGORY_OPTIONS, formatDateTime, priorityLabel, statusLabel, tagLabel, ticketStatusLabel, TICKET_STATUS_OPTIONS } from '../../constants/session-ui'
import AdminMessageSearchPanel from './AdminMessageSearchPanel.vue'

const loading = ref(false)
const error = ref(null)
const sessions = ref([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)
const expandedSessionId = ref(null)
const transferLogs = ref({})
const showCreateDialog = ref(false)
const showEditDialog = ref(false)
const saving = ref(false)
const editingSession = ref(null)
const formError = ref(null)
const createForm = ref({ userLoginNumber: '', agentLoginNumber: '' })
const editForm = ref({ title: '', priority: 'NORMAL', category: 'OTHER', tags: [] })
const tagInput = ref('')
const showTicketDialog = ref(false)
const ticketLoading = ref(false)
const ticketError = ref(null)
const selectedTicket = ref(null)
const ticketSessionId = ref(null)
const sessionTable = ref(null)
const topTableScroll = ref(null)
const tableScrollWidth = ref(0)
let tableResizeObserver

// Filters
const filters = ref({
  userLoginNumber: '',
  agentLoginNumber: '',
  status: '',
  archiveStatus: '',
  rating: '',
  ticketNo: '',
  ticketStatus: '',
  from: '',
  to: '',
})

const statusOptions = ['', 'ACTIVE', 'CLOSED']

function normalizeDateTime(value) {
  const normalized = value.trim()
  if (!normalized) return ''
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(normalized)) {
    throw new Error('请选择有效的开始时间和结束时间')
  }
  const parsed = new Date(`${normalized}:00`)
  if (Number.isNaN(parsed.getTime())) {
    throw new Error('请选择有效的开始时间和结束时间')
  }
  return normalized
}

async function loadSessions() {
  loading.value = true
  error.value = null
  try {
    const qs = new URLSearchParams()
    qs.set('pageNo', pageNo.value)
    qs.set('pageSize', pageSize.value)
    if (filters.value.userLoginNumber) qs.set('userLoginNumber', filters.value.userLoginNumber)
    if (filters.value.agentLoginNumber) qs.set('agentLoginNumber', filters.value.agentLoginNumber)
    if (filters.value.status) qs.set('status', filters.value.status)
    if (filters.value.rating) qs.set('rating', filters.value.rating)
    if (filters.value.archiveStatus) qs.set('archiveStatus', filters.value.archiveStatus)
    if (filters.value.ticketNo) qs.set('ticketNo', filters.value.ticketNo)
    if (filters.value.ticketStatus) qs.set('ticketStatus', filters.value.ticketStatus)
    if (filters.value.from) qs.set('from', normalizeDateTime(filters.value.from))
    if (filters.value.to) qs.set('to', normalizeDateTime(filters.value.to))

    const res = await request(`/chat/admin/sessions?${qs.toString()}`)
    sessions.value = res.data?.records ?? []
    total.value = res.data?.total ?? 0
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
    await nextTick()
    refreshTableScrollWidth()
    observeTableSize()
  }
}

onMounted(loadSessions)
onBeforeUnmount(() => tableResizeObserver?.disconnect())

function refreshTableScrollWidth() {
  const tableElement = sessionTable.value?.$el?.querySelector('.el-table__body')
  tableScrollWidth.value = Math.max(tableElement?.scrollWidth ?? 0, tableElement?.clientWidth ?? 0, topTableScroll.value?.clientWidth ?? 0)
}

function observeTableSize() {
  const tableElement = sessionTable.value?.$el
  if (!tableElement || typeof ResizeObserver === 'undefined') return
  tableResizeObserver?.disconnect()
  tableResizeObserver = new ResizeObserver(refreshTableScrollWidth)
  tableResizeObserver.observe(tableElement)
}

function syncTableScroll(event) {
  sessionTable.value?.setScrollLeft(event.target.scrollLeft)
}

function syncTopScroll({ scrollLeft }) {
  if (topTableScroll.value && topTableScroll.value.scrollLeft !== scrollLeft) {
    topTableScroll.value.scrollLeft = scrollLeft
  }
}

function applyFilters() {
  pageNo.value = 1
  loadSessions()
}

function clearFilters() {
  filters.value = { userLoginNumber: '', agentLoginNumber: '', status: '', archiveStatus: '', rating: '', ticketNo: '', ticketStatus: '', from: '', to: '' }
  pageNo.value = 1
  loadSessions()
}

function handlePageChange(nextPage) {
  if (nextPage === pageNo.value) return
  pageNo.value = nextPage
  loadSessions()
}

async function toggleTransferLogs(sessionId) {
  if (expandedSessionId.value === sessionId) { expandedSessionId.value = null; return }
  expandedSessionId.value = sessionId
  if (!transferLogs.value[sessionId]) {
    try { const result = await findTransferLogs(sessionId); transferLogs.value[sessionId] = result?.data || [] }
    catch (e) { error.value = e.message }
  }
}

function ratingStars(rating) {
  if (!rating && rating !== 0) return '-'
  return '★'.repeat(rating) + '☆'.repeat(5 - rating)
}

function openCreateDialog() {
  createForm.value = { userLoginNumber: '', agentLoginNumber: '' }
  formError.value = null
  showCreateDialog.value = true
}

async function createSession() {
  saving.value = true
  formError.value = null
  try {
    await createAdminSession(createForm.value)
    showCreateDialog.value = false
    await loadSessions()
  } catch (e) { formError.value = e.message } finally { saving.value = false }
}

async function openEditDialog(row) {
  formError.value = null
  editingSession.value = row
  try {
    const result = await request(`/chat/sessions/${encodeURIComponent(row.sessionId)}/metadata`)
    const metadata = result.data || {}
    editForm.value = {
      title: metadata.title || '',
      priority: metadata.priority || 'NORMAL',
      category: metadata.category || 'OTHER',
      tags: [...(metadata.tags || [])],
    }
    tagInput.value = ''
    showEditDialog.value = true
  } catch (e) { formError.value = e.message }
}

async function saveSessionMetadata() {
  saving.value = true
  formError.value = null
  try {
    await updateAdminSession(editingSession.value.sessionId, {
      title: editForm.value.title,
      priority: editForm.value.priority,
      category: editForm.value.category,
      tags: editForm.value.tags,
    })
    showEditDialog.value = false
    await loadSessions()
  } catch (e) { formError.value = e.message } finally { saving.value = false }
}

async function deleteSession(row) {
  try {
    await ElMessageBox.confirm(`确定永久删除会话 ${row.sessionId} 吗？关联消息、工单、评价和附件将无法恢复。`, '删除会话', {
      type: 'warning', confirmButtonText: '永久删除', cancelButtonText: '取消',
    })
  } catch { return }
  try {
    await deleteAdminSession(row.sessionId)
    ElMessage.success('会话已永久删除')
    await loadSessions()
  } catch (e) { error.value = e.message }
}

async function openTicket(row) {
  const sessionId = row.sessionId
  ticketSessionId.value = sessionId
  selectedTicket.value = null
  ticketError.value = null
  ticketLoading.value = true
  showTicketDialog.value = true
  try {
    const result = await getSupportTicket(sessionId)
    if (ticketSessionId.value === sessionId) selectedTicket.value = result?.data ?? null
  } catch (e) {
    if (ticketSessionId.value === sessionId) ticketError.value = e.message
  } finally {
    if (ticketSessionId.value === sessionId) ticketLoading.value = false
  }
}

function addTag() {
  const tag = tagInput.value.trim()
  if (!tag || editForm.value.tags.includes(tag)) return
  if (editForm.value.tags.length >= 10) { formError.value = '标签数量不能超过10个'; return }
  editForm.value.tags.push(tag)
  tagInput.value = ''
}

function removeTag(tag) {
  editForm.value.tags = editForm.value.tags.filter((item) => item !== tag)
}

</script>

<template>
  <section class="session-audit">
    <div class="panel-header">
      <h2>会话管理</h2>
      <div class="header-actions">
        <button class="btn refresh-sessions" :disabled="loading" @click="loadSessions">刷新</button>
        <button class="btn btn-create" @click="openCreateDialog">新建会话</button>
      </div>
    </div>

    <div class="filters">
      <input v-model="filters.userLoginNumber" class="user-login-number" placeholder="用户登录编号" />
      <input v-model="filters.agentLoginNumber" class="agent-login-number" placeholder="客服登录编号" />
      <input v-model="filters.ticketNo" class="ticket-number" placeholder="工单编号" />
      <select v-model="filters.ticketStatus" aria-label="工单状态">
        <option value="">全部工单状态</option>
        <option v-for="item in TICKET_STATUS_OPTIONS" :key="item.code" :value="item.code">{{ item.label }}</option>
      </select>
      <select v-model="filters.status" aria-label="Status">
        <option v-for="s in statusOptions" :key="s" :value="s">{{ s ? statusLabel(s) : '全部状态' }}</option>
      </select>
      <select v-model="filters.archiveStatus" aria-label="Archive status">
        <option value="">全部归档</option><option value="NONE">未归档</option>
        <option v-for="option in ARCHIVE_STATUS_OPTIONS" :key="option.code" :value="option.code">{{ option.label }}</option>
      </select>
      <input v-model="filters.rating" class="rating-field" type="number" min="1" max="5" placeholder="评分 1–5" aria-label="评分" />
      <input v-model="filters.from" class="date-field" type="datetime-local" aria-label="开始时间" title="开始时间" />
      <input v-model="filters.to" class="date-field" type="datetime-local" aria-label="结束时间" title="结束时间" />
      <button class="btn btn-primary" @click="applyFilters">查询</button>
      <button class="btn" @click="clearFilters">清空</button>
    </div>

    <div v-if="loading" class="loading">加载中…</div>
    <div v-else-if="error" class="error">{{ error }}</div>
    <template v-else>
      <div ref="topTableScroll" class="table-top-scroll" aria-label="会话表格横向滚动条" @scroll="syncTableScroll">
        <div class="table-top-scroll-content" :style="{ width: `${tableScrollWidth}px` }"></div>
      </div>
      <el-table ref="sessionTable" v-loading="loading" class="data-table" :data="sessions" row-key="sessionId" @scroll="syncTopScroll">
        <el-table-column label="会话" min-width="170">
          <template #default="{ row }">{{ row.title || '新咨询' }}</template>
        </el-table-column>
        <el-table-column prop="username" label="用户登录编号" min-width="120">
          <template #default="{ row }"><span class="mono">{{ row.username || '-' }}</span></template>
        </el-table-column>
        <el-table-column prop="agentUsername" label="客服登录编号" min-width="120">
          <template #default="{ row }"><span class="mono">{{ row.agentUsername || '-' }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><span class="status-cell">{{ statusLabel(row.status) }}</span></template>
        </el-table-column>
        <el-table-column label="工单" min-width="130">
          <template #default="{ row }"><span v-if="row.ticketNo">{{ row.ticketNo }} · {{ ticketStatusLabel(row.ticketStatus) }}</span><span v-else>-</span></template>
        </el-table-column>
        <el-table-column label="评分" width="110">
          <template #default="{ row }"><span class="rating-cell" :title="row.rating + '/5'">{{ ratingStars(row.rating) }}</span></template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="145">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="结束时间" min-width="145">
          <template #default="{ row }">{{ formatDateTime(row.endTime) }}</template>
        </el-table-column>
        <el-table-column label="转接记录" min-width="170">
          <template #default="{ row }">
            <el-button class="btn" size="small" @click="toggleTransferLogs(row.sessionId)">
              {{ expandedSessionId === row.sessionId ? '收起' : '查看' }}
            </el-button>
            <div v-if="expandedSessionId === row.sessionId" class="transfer-details">
              <div v-if="!transferLogs[row.sessionId]?.length" class="empty">暂无转接记录</div>
              <div v-for="log in transferLogs[row.sessionId]" :key="log.id">
                {{ log.sourceAgentNickname || '未知客服' }} → {{ log.targetAgentNickname || '未知客服' }}，{{ formatDateTime(log.createTime) }}
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="250">
          <template #default="{ row }">
            <el-button class="open-ticket" size="small" @click="openTicket(row)">工单</el-button>
            <el-button size="small" @click="openEditDialog(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="deleteSession(row)">删除会话</el-button>
          </template>
        </el-table-column>
        <template #empty><div class="empty">暂无数据</div></template>
      </el-table>

      <el-pagination
        class="pagination"
        layout="prev, pager, next"
        :current-page="pageNo"
        :page-size="pageSize"
        :total="total"
        :disabled="loading"
        @current-change="handlePageChange"
      />

    </template>

    <el-dialog v-model="showCreateDialog" title="新建会话" width="480px">
      <div class="form-grid">
        <label>用户登录编号<input v-model="createForm.userLoginNumber" placeholder="例如 user001" /></label>
        <label>客服登录编号<input v-model="createForm.agentLoginNumber" placeholder="例如 agent001" /></label>
      </div>
      <p v-if="formError" class="error">{{ formError }}</p>
      <template #footer><el-button @click="showCreateDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="createSession">创建</el-button></template>
    </el-dialog>

    <el-dialog v-model="showEditDialog" title="编辑会话" width="520px">
      <div class="form-grid">
        <label>标题<input v-model="editForm.title" /></label>
        <label>优先级<select v-model="editForm.priority"><option value="LOW">{{ priorityLabel('LOW') }}</option><option value="NORMAL">{{ priorityLabel('NORMAL') }}</option><option value="HIGH">{{ priorityLabel('HIGH') }}</option><option value="URGENT">{{ priorityLabel('URGENT') }}</option></select></label>
        <label>分类<select v-model="editForm.category"><option v-for="option in CATEGORY_OPTIONS" :key="option.code" :value="option.code">{{ option.label }}</option></select></label>
        <label>标签<div class="tag-input-row"><input v-model="tagInput" placeholder="输入标签" @keyup.enter="addTag" /><button class="btn" type="button" @click="addTag">添加</button></div></label>
        <div v-if="editForm.tags.length" class="tag-list"><el-tag v-for="tag in editForm.tags" :key="tag" closable @close="removeTag(tag)">{{ tagLabel(tag) }}</el-tag></div>
      </div>
      <p v-if="formError" class="error">{{ formError }}</p>
      <template #footer><el-button @click="showEditDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveSessionMetadata">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="showTicketDialog" title="关联工单" width="520px">
      <div v-loading="ticketLoading" class="ticket-detail">
        <p v-if="ticketError" class="error">{{ ticketError }}</p>
        <p v-else-if="!ticketLoading && !selectedTicket" class="empty">该会话暂未创建工单</p>
        <dl v-else-if="selectedTicket">
          <dt>工单编号</dt><dd>{{ selectedTicket.ticketNo }}</dd>
          <dt>工单状态</dt><dd>{{ ticketStatusLabel(selectedTicket.status) }}</dd>
          <dt>会话标题</dt><dd>{{ selectedTicket.title || '新咨询' }}</dd>
          <dt>负责客服</dt><dd>{{ selectedTicket.agentNickname || '-' }}</dd>
          <dt>问题描述</dt><dd>{{ selectedTicket.description || '-' }}</dd>
          <template v-if="selectedTicket.resolution"><dt>处理结果</dt><dd>{{ selectedTicket.resolution }}</dd></template>
          <dt>更新时间</dt><dd>{{ formatDateTime(selectedTicket.updatedAt) }}</dd>
        </dl>
      </div>
    </el-dialog>
  </section>
</template>

<style scoped>
.session-audit {
  padding: 1rem;
}
.panel-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; }
.header-actions { display: flex; gap: 0.5rem; }
.form-grid { display: grid; gap: 0.85rem; }
.form-grid label { display: grid; gap: 0.35rem; color: #374151; font-size: 0.9rem; }
.form-grid input, .form-grid select { padding: 0.5rem; border: 1px solid #d1d5db; border-radius: 4px; }
.tag-input-row { display: flex; gap: 0.5rem; }
.tag-input-row input { flex: 1; }
.tag-list { display: flex; flex-wrap: wrap; gap: 0.35rem; }
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-bottom: 1rem;
  align-items: center;
  overflow: visible;
  padding-bottom: 0.25rem;
}
.filters input,
.filters select,
.filters button {
  flex: 0 0 auto;
}
.filters input,
.filters select {
  padding: 0.35rem 0.5rem;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  font-size: 0.9rem;
}
.user-login-number,
.agent-login-number,
.ticket-number { width: 150px; }
.filters select { width: 130px; }
.date-field { min-width: 0; width: 180px; }
.rating-field { width: 92px; }
.data-table {
  width: 100%;
  border-collapse: collapse;
}
.data-table th,
.data-table td {
  padding: 0.5rem 0.75rem;
  border-bottom: 1px solid #e5e7eb;
  text-align: left;
}
.data-table thead {
  background: #f9fafb;
}
.mono {
  font-family: monospace;
  font-size: 0.85rem;
}
.empty {
  text-align: center;
  color: #888;
  padding: 2rem 0;
}
.status-cell { display: inline-block; white-space: nowrap; overflow: visible; text-overflow: clip; }
.rating-cell { display: inline-block; white-space: nowrap; overflow: visible; text-overflow: clip; }
.data-table :deep(td:nth-child(6) .cell) { overflow: visible; white-space: nowrap; text-overflow: clip; }
.data-table :deep(td:nth-child(4) .cell) { overflow: visible; white-space: nowrap; text-overflow: clip; }
.btn {
  padding: 0.4rem 0.75rem;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
  font-size: 0.9rem;
}
.btn:hover {
  background: #f3f4f6;
}
.btn-primary {
  background: #2563eb;
  color: #fff;
  border-color: #2563eb;
}
.btn-primary:hover {
  background: #1d4ed8;
}
.loading, .error {
  padding: 1rem;
}
.error {
  color: #dc2626;
}
.data-table { width: 100%; }
.table-top-scroll {
  height: 14px;
  margin-bottom: 0.35rem;
  overflow-x: auto;
  overflow-y: hidden;
}
.table-top-scroll-content { height: 1px; }
.table-top-scroll::-webkit-scrollbar { height: 8px; }
.table-top-scroll::-webkit-scrollbar-thumb { border-radius: 4px; background: var(--color-line-strong); }
.pagination {
  display: flex;
  justify-content: center;
  margin-top: 1rem;
}
.data-table :deep(.el-table__header-wrapper th.el-table__cell) { padding: 0.5rem 0.75rem; background: #f9fafb; color: inherit; font-weight: 600; }
.data-table :deep(.el-table__body-wrapper td.el-table__cell) { padding: 0.5rem 0.75rem; vertical-align: top; }
.data-table :deep(.el-table__inner-wrapper::before) { background-color: #e5e7eb; }
.status-cell { display: inline-block; white-space: nowrap; }
.data-table :deep(.el-table__body-wrapper td.el-table__cell:nth-child(4)) { white-space: nowrap; }
.transfer-details { margin-top: 0.5rem; color: #6b7280; font-size: 0.8rem; line-height: 1.5; }
.ticket-detail dl { display: grid; grid-template-columns: 76px 1fr; gap: 0.7rem 0.8rem; margin: 0; font-size: 0.9rem; }
.ticket-detail dt { color: #6b7280; }
.ticket-detail dd { margin: 0; color: #1f2937; white-space: pre-wrap; word-break: break-word; }
</style>

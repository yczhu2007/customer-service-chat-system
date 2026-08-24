<script setup>
import { ref, onMounted } from 'vue'
import { request } from '../../services/http-client'
import { findTransferLogs } from '../../api/admin-api'
import { ARCHIVE_STATUS_OPTIONS } from '../../constants/session-ui'
import AdminMessageSearchPanel from './AdminMessageSearchPanel.vue'

const loading = ref(false)
const error = ref(null)
const sessions = ref([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(100)
const expandedSessionId = ref(null)
const transferLogs = ref({})

// Filters
const filters = ref({
  userId: '',
  agentId: '',
  status: '',
  archiveStatus: '',
  rating: '',
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
    if (filters.value.userId) qs.set('userId', filters.value.userId)
    if (filters.value.agentId) qs.set('agentId', filters.value.agentId)
    if (filters.value.status) qs.set('status', filters.value.status)
    if (filters.value.rating) qs.set('rating', filters.value.rating)
    if (filters.value.archiveStatus) qs.set('archiveStatus', filters.value.archiveStatus)
    if (filters.value.from) qs.set('from', normalizeDateTime(filters.value.from))
    if (filters.value.to) qs.set('to', normalizeDateTime(filters.value.to))

    const res = await request(`/chat/admin/sessions?${qs.toString()}`)
    sessions.value = res.data?.records ?? []
    total.value = res.data?.total ?? 0
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

onMounted(loadSessions)

function applyFilters() {
  pageNo.value = 1
  loadSessions()
}

function clearFilters() {
  filters.value = { userId: '', agentId: '', status: '', archiveStatus: '', rating: '', from: '', to: '' }
  pageNo.value = 1
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

function formatDate(dt) {
  if (!dt) return '-'
  const value = new Date(dt)
  const pad = (number) => String(number).padStart(2, '0')
  return `${value.getFullYear()}/${pad(value.getMonth() + 1)}/${pad(value.getDate())} ${pad(value.getHours())}:${pad(value.getMinutes())}`
}

function ratingStars(rating) {
  if (!rating && rating !== 0) return '-'
  return '★'.repeat(rating) + '☆'.repeat(5 - rating)
}

</script>

<template>
  <section class="session-audit">
    <h2>会话审计</h2>

    <div class="filters">
      <input v-model="filters.userId" placeholder="用户 ID" />
      <input v-model="filters.agentId" placeholder="客服 ID" />
      <select v-model="filters.status" aria-label="Status">
        <option v-for="s in statusOptions" :key="s" :value="s">{{ s || '全部状态' }}</option>
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

    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="error" class="error">{{ error }}</div>
    <template v-else>
      <el-table v-loading="loading" class="data-table" :data="sessions" row-key="sessionId">
        <el-table-column prop="sessionId" label="会话 ID" min-width="170">
          <template #default="{ row }"><span class="mono">{{ row.sessionId }}</span></template>
        </el-table-column>
        <el-table-column prop="userId" label="用户 ID" min-width="120">
          <template #default="{ row }"><span class="mono">{{ row.userId }}</span></template>
        </el-table-column>
        <el-table-column prop="agentId" label="客服 ID" min-width="120">
          <template #default="{ row }"><span class="mono">{{ row.agentId || '-' }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><span class="status-cell">{{ row.status }}</span></template>
        </el-table-column>
        <el-table-column label="评分" width="110">
          <template #default="{ row }"><span class="rating-cell" :title="row.rating + '/5'">{{ ratingStars(row.rating) }}</span></template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="145">
          <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="结束时间" min-width="145">
          <template #default="{ row }">{{ formatDate(row.endTime) }}</template>
        </el-table-column>
        <el-table-column label="转接记录" min-width="170">
          <template #default="{ row }">
            <el-button class="btn" size="small" @click="toggleTransferLogs(row.sessionId)">
              {{ expandedSessionId === row.sessionId ? '收起' : '查看' }}
            </el-button>
            <div v-if="expandedSessionId === row.sessionId" class="transfer-details">
              <div v-if="!transferLogs[row.sessionId]?.length" class="empty">暂无转接记录</div>
              <div v-for="log in transferLogs[row.sessionId]" :key="log.id">
                {{ log.sourceAgentUsername || log.sourceAgentId }} → {{ log.targetAgentUsername || log.targetAgentId }}，{{ formatDate(log.createTime) }}
              </div>
            </div>
          </template>
        </el-table-column>
        <template #empty><div class="empty">暂无数据</div></template>
      </el-table>

    </template>
  </section>
</template>

<style scoped>
.session-audit {
  padding: 1rem;
}
.filters {
  display: flex;
  flex-wrap: nowrap;
  gap: 0.5rem;
  margin-bottom: 1rem;
  align-items: center;
  overflow-x: auto;
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
.date-field { min-width: 200px; }
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
.data-table :deep(td:nth-child(5) .cell) { overflow: visible; white-space: nowrap; text-overflow: clip; }
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
.data-table :deep(.el-table__header-wrapper th.el-table__cell) { padding: 0.5rem 0.75rem; background: #f9fafb; color: inherit; font-weight: 600; }
.data-table :deep(.el-table__body-wrapper td.el-table__cell) { padding: 0.5rem 0.75rem; vertical-align: top; }
.data-table :deep(.el-table__inner-wrapper::before) { background-color: #e5e7eb; }
.status-cell { display: inline-block; white-space: nowrap; }
.data-table :deep(.el-table__body-wrapper td.el-table__cell:nth-child(4)) { white-space: nowrap; }
.transfer-details { margin-top: 0.5rem; color: #6b7280; font-size: 0.8rem; line-height: 1.5; }
</style>

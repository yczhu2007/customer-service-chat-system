<script setup>
import { ref, onMounted } from 'vue'
import { request } from '../../services/http-client'

const loading = ref(false)
const error = ref(null)
const sessions = ref([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)

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

const statusOptions = ['', 'ACTIVE', 'CLOSED', 'PENDING']
const archiveStatusOptions = ['', 'COMPLETED', 'PENDING', 'ON_HOLD', 'OTHER', 'UNARCHIVED']

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
    if (filters.value.archiveStatus) qs.set('archiveStatus', filters.value.archiveStatus)
    if (filters.value.rating) qs.set('rating', filters.value.rating)
    if (filters.value.from) qs.set('from', filters.value.from)
    if (filters.value.to) qs.set('to', filters.value.to)

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

function formatDate(dt) {
  if (!dt) return '-'
  return new Date(dt).toLocaleString('zh-CN')
}

function ratingStars(rating) {
  if (!rating && rating !== 0) return '-'
  return '★'.repeat(rating) + '☆'.repeat(5 - rating)
}

const totalPages = () => Math.max(1, Math.ceil(total.value / pageSize.value))
</script>

<template>
  <section class="session-audit">
    <h2>会话审计</h2>

    <div class="filters">
      <input v-model="filters.userId" placeholder="用户 ID" />
      <input v-model="filters.agentId" placeholder="客服 ID" />
      <select v-model="filters.status">
        <option v-for="s in statusOptions" :key="s" :value="s">{{ s || '全部状态' }}</option>
      </select>
      <select v-model="filters.archiveStatus">
        <option v-for="s in archiveStatusOptions" :key="s" :value="s">{{ s || '全部归档状态' }}</option>
      </select>
      <input v-model="filters.rating" placeholder="评分 (1-5)" type="number" min="1" max="5" style="width:80px" />
      <input v-model="filters.from" type="datetime-local" title="开始时间" />
      <input v-model="filters.to" type="datetime-local" title="结束时间" />
      <button class="btn btn-primary" @click="applyFilters">查询</button>
      <button class="btn" @click="clearFilters">清空</button>
    </div>

    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="error" class="error">{{ error }}</div>
    <template v-else>
      <table class="data-table">
        <thead>
          <tr>
            <th>会话 ID</th>
            <th>用户 ID</th>
            <th>客服 ID</th>
            <th>状态</th>
            <th>归档状态</th>
            <th>评分</th>
            <th>创建时间</th>
            <th>更新时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="s in sessions" :key="s.sessionId">
            <td class="mono">{{ s.sessionId }}</td>
            <td class="mono">{{ s.userId }}</td>
            <td class="mono">{{ s.agentId || '-' }}</td>
            <td>{{ s.status }}</td>
            <td>{{ s.archiveStatus || '-' }}</td>
            <td :title="s.rating + '/5'">{{ ratingStars(s.rating) }}</td>
            <td>{{ formatDate(s.createdAt) }}</td>
            <td>{{ formatDate(s.updatedAt) }}</td>
          </tr>
          <tr v-if="sessions.length === 0">
            <td colspan="8" class="empty">暂无数据</td>
          </tr>
        </tbody>
      </table>

      <div class="pagination">
        <button :disabled="pageNo <= 1" @click="pageNo--; loadSessions()">上一页</button>
        <span>{{ pageNo }} / {{ totalPages() }} (共 {{ total }} 条)</span>
        <button :disabled="pageNo >= totalPages()" @click="pageNo++; loadSessions()">下一页</button>
      </div>
    </template>
  </section>
</template>

<style scoped>
.session-audit {
  padding: 1rem;
}
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-bottom: 1rem;
  align-items: center;
}
.filters input,
.filters select {
  padding: 0.35rem 0.5rem;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  font-size: 0.9rem;
}
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
.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 1rem;
  margin-top: 1rem;
}
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
</style>

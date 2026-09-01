<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { findAdminTicketStatusCounts, listAdminTickets } from '../../api/admin-api'
import { CATEGORY_OPTIONS, categoryLabel, formatDateTime, priorityLabel, ticketStatusLabel, TICKET_STATUS_OPTIONS } from '../../constants/session-ui'

const emit = defineEmits(['locate-session'])
const filters = ref({ keyword: '', status: '', priority: '', category: '', agentKeyword: '', createdFrom: '', createdTo: '' })
const tickets = ref([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = 20
const loading = ref(false)
const error = ref('')
const statusCounts = ref([])
const selectedTicket = ref(null)
const detailVisible = ref(false)
const priorityOptions = ['LOW', 'NORMAL', 'HIGH', 'URGENT']
const summaries = computed(() => TICKET_STATUS_OPTIONS.map((item) => ({
  ...item,
  count: statusCounts.value.find((count) => count.status === item.code)?.count || 0,
})))

function requestParams() {
  return { ...filters.value, pageNo: pageNo.value, pageSize }
}

async function loadTickets() {
  loading.value = true
  error.value = ''
  try {
    const params = requestParams()
    const [page, counts] = await Promise.all([
      listAdminTickets(params),
      findAdminTicketStatusCounts(),
    ])
    tickets.value = page?.data?.records || []
    total.value = page?.data?.total || 0
    statusCounts.value = counts?.data || []
  } catch (e) {
    error.value = e.message || '加载工单失败'
  } finally {
    loading.value = false
  }
}

function searchTickets() {
  pageNo.value = 1
  loadTickets()
}

function resetTickets() {
  filters.value = { keyword: '', status: '', priority: '', category: '', agentKeyword: '', createdFrom: '', createdTo: '' }
  searchTickets()
}

function openTicketDetail(ticket) {
  selectedTicket.value = ticket
  detailVisible.value = true
}

async function copyTicketNo() {
  try {
    await navigator.clipboard.writeText(selectedTicket.value.ticketNo)
    ElMessage.success('工单编号已复制')
  } catch {
    error.value = '复制工单编号失败'
  }
}

function locateTicketSession() {
  if (!selectedTicket.value?.sessionId) return
  detailVisible.value = false
  emit('locate-session', {
    sessionId: selectedTicket.value.sessionId,
    ticketNo: selectedTicket.value.ticketNo,
  })
}

onMounted(loadTickets)
</script>

<template>
  <section class="admin-ticket-panel">
    <div class="panel-header">
      <h2>工单管理</h2>
      <button class="btn" :disabled="loading" @click="loadTickets">刷新</button>
    </div>

    <div class="ticket-summary" aria-label="工单状态统计">
      <span v-for="item in summaries" :key="item.code">{{ item.label }} {{ item.count }}</span>
    </div>

    <div class="ticket-filters">
      <input v-model="filters.keyword" class="ticket-keyword" placeholder="工单号、标题、问题或处理结果" @keyup.enter="searchTickets" />
      <select v-model="filters.status" class="ticket-status" aria-label="工单状态"><option value="">全部工单状态</option><option v-for="item in TICKET_STATUS_OPTIONS" :key="item.code" :value="item.code">{{ item.label }}</option></select>
      <select v-model="filters.priority" aria-label="优先级"><option value="">全部优先级</option><option v-for="item in priorityOptions" :key="item" :value="item">{{ priorityLabel(item) }}</option></select>
      <select v-model="filters.category" aria-label="分类"><option value="">全部分类</option><option v-for="item in CATEGORY_OPTIONS" :key="item.code" :value="item.code">{{ item.label }}</option></select>
      <input v-model="filters.agentKeyword" placeholder="负责客服" />
      <input v-model="filters.createdFrom" type="datetime-local" aria-label="创建开始时间" />
      <input v-model="filters.createdTo" type="datetime-local" aria-label="创建结束时间" />
      <button class="btn btn-primary search-tickets" :disabled="loading" @click="searchTickets">查询</button>
      <button class="btn" :disabled="loading" @click="resetTickets">重置</button>
    </div>

    <p v-if="error" class="error">{{ error }}</p>
    <el-table v-loading="loading" :data="tickets" class="data-table" empty-text="暂无工单">
      <el-table-column prop="ticketNo" label="工单号" min-width="130" />
      <el-table-column label="状态" width="105"><template #default="{ row }">{{ ticketStatusLabel(row.status) }}</template></el-table-column>
      <el-table-column label="优先级" width="90"><template #default="{ row }">{{ priorityLabel(row.priority) }}</template></el-table-column>
      <el-table-column label="分类" width="105"><template #default="{ row }">{{ categoryLabel(row.category) }}</template></el-table-column>
      <el-table-column prop="title" label="会话标题" min-width="150" />
      <el-table-column prop="userNickname" label="用户" width="110" />
      <el-table-column prop="agentNickname" label="负责客服" width="120" />
      <el-table-column label="最近更新时间" width="165"><template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template></el-table-column>
      <el-table-column label="操作" width="100"><template #default="{ row }"><el-button class="view-ticket-detail" size="small" @click="openTicketDetail(row)">详情</el-button></template></el-table-column>
    </el-table>
    <el-pagination v-if="total > pageSize" v-model:current-page="pageNo" :page-size="pageSize" :total="total" layout="prev, pager, next" @current-change="loadTickets" />

    <el-dialog v-model="detailVisible" title="工单详情" width="540px" append-to-body>
      <dl v-if="selectedTicket" class="ticket-detail">
        <dt>工单编号</dt><dd>{{ selectedTicket.ticketNo }} <el-button class="copy-ticket-no" link type="primary" size="small" @click="copyTicketNo">复制</el-button></dd>
        <dt>工单状态</dt><dd>{{ ticketStatusLabel(selectedTicket.status) }}</dd>
        <dt>关联会话</dt><dd>{{ selectedTicket.title || selectedTicket.sessionId }}</dd>
        <dt>负责客服</dt><dd>{{ selectedTicket.agentNickname || '-' }}</dd>
        <dt>问题描述</dt><dd>{{ selectedTicket.description || '-' }}</dd>
        <dt>处理结果</dt><dd>{{ selectedTicket.resolution || '-' }}</dd>
        <dt>更新时间</dt><dd>{{ formatDateTime(selectedTicket.updatedAt) }}</dd>
      </dl>
      <template #footer><el-button class="locate-ticket-session" @click="locateTicketSession">查看关联会话</el-button><el-button @click="detailVisible = false">关闭</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.admin-ticket-panel { max-width: 1180px; margin: 0 auto; }
.panel-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18px; }
.panel-header h2 { margin: 0; font-size: 19px; }
.ticket-summary { display: flex; flex-wrap: wrap; gap: 0.6rem; margin-bottom: 14px; }
.ticket-summary span { padding: 0.3rem 0.65rem; border-radius: 999px; background: #eef5ff; color: #2563eb; font-size: 0.82rem; }
.ticket-filters { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 14px; }
.ticket-filters input, .ticket-filters select { min-height: 32px; box-sizing: border-box; padding: 0.35rem 0.5rem; border: 1px solid #d1d5db; border-radius: 6px; }
.ticket-keyword { width: 240px; }
.ticket-filters select { width: 130px; }
.error { color: #b91c1c; margin: 0 0 12px; }
.ticket-detail { display: grid; grid-template-columns: 84px 1fr; gap: 0.7rem 0.8rem; margin: 0; font-size: 0.9rem; }
.ticket-detail dt { color: #6b7280; }
.ticket-detail dd { margin: 0; white-space: pre-wrap; word-break: break-word; }
</style>

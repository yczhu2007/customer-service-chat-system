<script setup>
import { ref } from 'vue'
import { deleteAdminMessage, searchAdminMessages } from '../../api/admin-api'

const keyword = ref('')
const loading = ref(false)
const error = ref('')
const results = ref([])
const total = ref(0)
const hasSearched = ref(false)
const pageNo = ref(1)
const pageSize = ref(20)

async function search() { if (!keyword.value.trim()) return; pageNo.value = 1; await loadResults() }
async function loadResults() {
  if (!keyword.value.trim()) return
  hasSearched.value = true; loading.value = true; error.value = ''
  try {
    const result = await searchAdminMessages({ keyword: keyword.value.trim(), pageNo: pageNo.value, pageSize: pageSize.value })
    results.value = result?.data?.records || []; total.value = result?.data?.total || 0
  } catch (exception) { error.value = exception.message || '搜索失败' } finally { loading.value = false }
}
function handlePageChange(nextPage) { if (nextPage === pageNo.value) return; pageNo.value = nextPage; loadResults() }
function clear() { keyword.value = ''; results.value = []; total.value = 0; error.value = ''; hasSearched.value = false; pageNo.value = 1 }
async function removeMessage(messageId) {
  if (!window.confirm(`确定删除消息 ${messageId} 吗？`)) return
  try { await deleteAdminMessage(messageId); await loadResults() } catch (exception) { error.value = exception.message || '删除失败' }
}
</script>

<template>
  <section class="message-search">
    <div class="panel-header"><h2>消息搜索</h2><p>在全部未撤回聊天消息中搜索</p></div>
    <div class="search-row"><el-input v-model="keyword" clearable placeholder="输入消息关键词" @keyup.enter="search"/><el-button type="primary" :loading="loading" :disabled="!keyword.trim()" @click="search">搜索</el-button><el-button :disabled="!keyword && !results.length" @click="clear">清空</el-button></div>
    <p v-if="error" class="error">{{ error }}</p><p v-else-if="hasSearched && !loading" class="summary">共找到 {{ total }} 条匹配消息</p>
    <el-table v-if="results.length" :data="results" class="data-table" row-key="messageId">
      <el-table-column prop="sessionTitle" label="会话" min-width="150"/><el-table-column prop="content" label="消息内容" min-width="300"/><el-table-column prop="senderUsername" label="发送者登录编号" min-width="140"/><el-table-column prop="senderRole" label="角色" width="100"/><el-table-column prop="createTime" label="发送时间" min-width="170"/>
      <el-table-column label="操作" width="100"><template #default="{ row }"><el-button type="danger" link @click="removeMessage(row.messageId)">删除</el-button></template></el-table-column>
    </el-table>
    <el-pagination v-if="results.length && total > pageSize" class="pagination" layout="prev, pager, next" :current-page="pageNo" :page-size="pageSize" :total="total" :disabled="loading" @current-change="handlePageChange"/>
    <div v-if="!results.length && hasSearched && !loading && !error" class="empty">暂无匹配消息</div>
  </section>
</template>

<style scoped>
.message-search { padding: 1rem; }.panel-header { margin-bottom: 1rem; }.panel-header h2 { margin: 0; color: var(--color-ink); }.panel-header p { margin: .35rem 0 0; color: var(--color-muted); font-size: .9rem; }.search-row { display: flex; gap: .5rem; max-width: 680px; }.search-row :deep(.el-input) { flex: 1; }.summary { margin: 1rem 0 .5rem; color: var(--color-muted); }.error { color: #dc2626; }.empty { padding: 2rem; color: var(--color-faint); text-align: center; }
</style>

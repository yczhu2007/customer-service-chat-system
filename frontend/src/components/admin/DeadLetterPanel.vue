<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteDeadLetter, findDeadLetters, replayDeadLetter } from '../../api/admin-api'
import { formatDateTime } from '../../constants/session-ui'

const loading = ref(false)
const error = ref(null)
const deadLetters = ref([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)

// Replay confirmation
const showReplayConfirm = ref(false)
const replayId = ref(null)
const replayLoading = ref(false)
const replayResult = ref(null)

async function loadDeadLetters() {
  loading.value = true
  error.value = null
  try {
    const res = await findDeadLetters({ pageNo: pageNo.value, pageSize: pageSize.value })
    deadLetters.value = res.data?.records ?? []
    total.value = res.data?.total ?? 0
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

onMounted(loadDeadLetters)

function confirmReplay(messageId) {
  replayId.value = messageId
  replayResult.value = null
  showReplayConfirm.value = true
}

async function executeReplay() {
  replayLoading.value = true
  replayResult.value = null
  try {
    const res = await replayDeadLetter(replayId.value)
    replayResult.value = { success: true, message: res.message || '重放成功' }
    await loadDeadLetters()
  } catch (e) {
    replayResult.value = { success: false, message: e.message }
  } finally {
    replayLoading.value = false
  }
}

async function removeDeadLetter(messageId) {
  try {
    await ElMessageBox.confirm(`确定删除死信消息 ${messageId} 吗？`, '删除死信消息', {
      type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消',
    })
  } catch { return }
  try {
    await deleteDeadLetter(messageId)
    ElMessage.success('死信消息已删除')
    await loadDeadLetters()
  } catch (e) { error.value = e.message }
}

function prevPage() {
  if (pageNo.value > 1) {
    pageNo.value--
    loadDeadLetters()
  }
}

function nextPage() {
  if (pageNo.value < totalPages()) {
    pageNo.value++
    loadDeadLetters()
  }
}

const totalPages = () => Math.max(1, Math.ceil(total.value / pageSize.value))
</script>

<template>
  <section class="dead-letter-panel">
    <div class="panel-header">
      <h2>死信管理</h2>
      <button class="btn" @click="loadDeadLetters" :disabled="loading">刷新</button>
    </div>

    <div class="info-box">
      <strong>提示：</strong>系统自动保留最近 30 天的死信消息，超过 30 天将自动清理。
    </div>

    <div v-if="loading" class="loading">加载中…</div>
    <div v-else-if="error" class="error">{{ error }}</div>
    <template v-else>
      <el-table v-loading="loading" class="data-table" :data="deadLetters" row-key="messageId">
        <el-table-column prop="messageId" label="消息 ID" min-width="260">
          <template #default="{ row }"><span class="mono">{{ row.messageId }}</span></template>
        </el-table-column>
        <el-table-column label="失败时间" min-width="180">
          <template #default="{ row }">{{ formatDateTime(row.failedAt) }}</template>
        </el-table-column>
        <el-table-column label="载荷可用" width="110">
          <template #default="{ row }">
            <span :class="row.payloadAvailable ? 'badge yes' : 'badge no'">
              {{ row.payloadAvailable ? '是' : '否' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button
              class="btn btn-sm btn-primary"
              size="small"
              :disabled="!row.payloadAvailable"
              @click="confirmReplay(row.messageId)"
            >
              重放
            </el-button>
            <el-button class="btn btn-sm" size="small" @click="removeDeadLetter(row.messageId)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty><div class="empty">暂无死信消息</div></template>
      </el-table>

      <el-pagination
        class="pagination"
        layout="prev, slot, next"
        :current-page="pageNo"
        :page-size="pageSize"
        :total="total"
        :disabled="loading"
        @current-change="pageNo = $event; loadDeadLetters()"
      >
        <span>{{ pageNo }} / {{ totalPages() }} (共 {{ total }} 条)</span>
      </el-pagination>
    </template>

    <el-dialog v-model="showReplayConfirm" class="dialog" title="确认重放" width="460px">
      <p>确定要重放消息 <strong class="mono">{{ replayId }}</strong> 吗？</p>
      <p class="warn">重放操作将重新处理该消息，请谨慎操作。</p>
      <div v-if="replayResult" :class="replayResult.success ? 'success-msg' : 'error-msg'">
        {{ replayResult.message }}
      </div>
      <template #footer>
        <div class="dialog-actions">
          <el-button class="btn" @click="showReplayConfirm = false">取消</el-button>
          <el-button class="btn btn-primary" :loading="replayLoading" @click="executeReplay">
            {{ replayLoading ? '重放中…' : '确认重放' }}
          </el-button>
        </div>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.dead-letter-panel {
  padding: 1rem;
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}
.info-box {
  background: #fffbeb;
  border: 1px solid #fcd34d;
  border-radius: 6px;
  padding: 0.75rem 1rem;
  margin-bottom: 1rem;
  font-size: 0.9rem;
  color: #92400e;
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
.badge {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 0.85rem;
}
.badge.yes {
  background: #dcfce7;
  color: #166534;
}
.badge.no {
  background: #f3f4f6;
  color: #6b7280;
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
.btn:hover:not(:disabled) {
  background: #f3f4f6;
}
.btn-primary {
  background: #2563eb;
  color: #fff;
  border-color: #2563eb;
}
.btn-primary:hover:not(:disabled) {
  background: #1d4ed8;
}
.btn-sm {
  padding: 0.25rem 0.5rem;
  font-size: 0.8rem;
}
.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.loading, .error {
  padding: 1rem;
}
.error {
  color: #dc2626;
}
.dialog-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}
.dialog {
  background: #fff;
  border-radius: 8px;
  padding: 1.5rem;
  min-width: 360px;
  max-width: 480px;
}
.dialog h3 {
  margin-top: 0;
  margin-bottom: 0.75rem;
}
.warn {
  color: #b45309;
  font-size: 0.9rem;
}
.success-msg {
  background: #dcfce7;
  color: #166534;
  padding: 0.5rem;
  border-radius: 4px;
  margin-top: 0.5rem;
}
.error-msg {
  background: #fee2e2;
  color: #991b1b;
  padding: 0.5rem;
  border-radius: 4px;
  margin-top: 0.5rem;
}
.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  margin-top: 1rem;
}
.data-table { width: 100%; }
.data-table :deep(.el-table__header-wrapper th.el-table__cell) { padding: 0.5rem 0.75rem; background: #f9fafb; color: inherit; font-weight: 600; }
.data-table :deep(.el-table__body-wrapper td.el-table__cell) { padding: 0.5rem 0.75rem; }
.data-table :deep(.el-table__inner-wrapper::before) { background-color: #e5e7eb; }
.data-table :deep(.el-table__empty-text) { color: #888; }
.pagination :deep(.btn-prev), .pagination :deep(.btn-next), .pagination :deep(.el-pager li) { font-size: 0.9rem; }
.dialog :deep(.el-dialog) { border: 1px solid var(--color-line); border-radius: 8px; }
.dialog :deep(.el-dialog__header) { margin: 0; padding: 1.5rem 1.5rem 1rem; }
.dialog :deep(.el-dialog__title) { font-size: 1.1rem; font-weight: 600; }
.dialog :deep(.el-dialog__body) { padding: 0 1.5rem 1rem; }
.dialog :deep(.el-dialog__footer) { padding: 0 1.5rem 1.5rem; }
.dialog-actions :deep(.el-button) { min-height: 32px; }
</style>

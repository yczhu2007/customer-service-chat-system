<script setup>
import { computed, onMounted, ref } from 'vue'
import { findAdminReportOverview } from '../../api/admin-api'
import { createRecent30DayRange } from '../../utils/admin-date-range'
import ReportChart from './ReportChart.vue'
import {
  buildAgentReceptionOption,
  buildDurationOption,
  buildSatisfactionOption,
  buildSessionTrendOption,
  buildTicketStatusOption,
} from './report-options'

const loading = ref(true)
const error = ref('')
const report = ref(null)
const granularity = ref('DAY')
const defaultDateRange = createRecent30DayRange()
const from = ref(defaultDateRange.from)
const to = ref(defaultDateRange.to)

async function loadReport() {
  loading.value = true
  error.value = ''
  try {
    const response = await findAdminReportOverview({
      from: from.value,
      to: to.value,
      granularity: granularity.value,
    })
    report.value = response.data
  } catch (exception) {
    error.value = exception.message || '报表加载失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

function formatSeconds(seconds) {
  if (seconds === null || seconds === undefined) return '暂无数据'
  if (seconds < 60) return `${seconds} 秒`
  return `${Math.floor(seconds / 60)} 分 ${seconds % 60} 秒`
}

const trendOption = computed(() => buildSessionTrendOption(report.value?.sessionTrend || []))
const agentOption = computed(() => buildAgentReceptionOption(report.value?.agentReceptionRanking || []))
const durationOption = computed(() => buildDurationOption(report.value?.sessionDurationDistribution || []))
const satisfactionOption = computed(() => buildSatisfactionOption(report.value?.satisfaction || {}))
const ticketOption = computed(() => buildTicketStatusOption(report.value?.ticketStatusDistribution || []))

onMounted(loadReport)
</script>

<template>
  <section class="admin-report-panel">
    <div class="panel-header">
      <div>
        <h2>报表</h2>
        <p>基于所选时间范围内创建的会话、评价和工单统计。</p>
      </div>
      <div class="report-filters">
        <input v-model="from" type="datetime-local" aria-label="开始时间" />
        <input v-model="to" type="datetime-local" aria-label="结束时间" />
        <select v-model="granularity" aria-label="会话趋势粒度">
          <option value="DAY">按日</option>
          <option value="WEEK">按周</option>
        </select>
        <button class="btn refresh-report" :disabled="loading" @click="loadReport">刷新</button>
      </div>
    </div>

    <div v-if="loading" class="loading">报表加载中…</div>
    <div v-else-if="error" class="error">{{ error }}</div>
    <template v-else>
      <div class="report-kpis">
        <article class="kpi-card"><span>平均首响时长</span><strong>{{ formatSeconds(report?.averageFirstResponseSeconds) }}</strong></article>
        <article class="kpi-card"><span>满意度均分</span><strong>{{ report?.satisfaction?.averageRating ?? '暂无数据' }}</strong></article>
        <article class="kpi-card"><span>低分占比</span><strong>{{ report?.satisfaction?.lowRatingRate == null ? '暂无数据' : `${report.satisfaction.lowRatingRate}%` }}</strong></article>
      </div>

      <div class="report-grid">
        <article class="chart-card chart-card-wide"><h3>会话量趋势</h3><ReportChart :option="trendOption" /></article>
        <article class="chart-card"><h3>客服接待量排行</h3><ReportChart :option="agentOption" /></article>
        <article class="chart-card"><h3>会话时长分布</h3><ReportChart :option="durationOption" /></article>
        <article class="chart-card"><h3>满意度与低分占比</h3><ReportChart :option="satisfactionOption" /></article>
        <article class="chart-card"><h3>工单状态分布</h3><ReportChart :option="ticketOption" /></article>
      </div>
    </template>
  </section>
</template>

<style scoped>
.admin-report-panel { max-width: 1180px; margin: 0 auto; }
.panel-header { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; margin-bottom: 18px; }
.panel-header h2 { margin: 0; font-size: 19px; }
.panel-header p { margin: 6px 0 0; color: var(--color-muted); font-size: .9rem; }
.report-filters { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px; }
.report-filters input, .report-filters select { min-height: 32px; border: 1px solid var(--color-line-strong); border-radius: 6px; padding: 0 8px; background: var(--color-paper); color: var(--color-ink); }
.report-kpis { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; margin-bottom: 12px; }
.kpi-card, .chart-card { background: var(--color-paper); border: 1px solid var(--color-line); border-radius: 8px; }
.kpi-card { padding: 14px; display: grid; gap: 8px; }
.kpi-card span, .chart-card h3 { color: var(--color-muted); font-size: .88rem; font-weight: 500; }
.kpi-card strong { color: var(--color-ink); font-size: 1.45rem; }
.report-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.chart-card { padding: 14px; min-width: 0; }
.chart-card-wide { grid-column: 1 / -1; }
.chart-card h3 { margin: 0; }
.loading, .error { padding: 1rem; }
.error { color: var(--color-danger); }
@media (max-width: 900px) { .panel-header { flex-direction: column; } .report-filters { justify-content: flex-start; } }
@media (max-width: 680px) { .report-kpis, .report-grid { grid-template-columns: 1fr; } .chart-card-wide { grid-column: auto; } }
</style>

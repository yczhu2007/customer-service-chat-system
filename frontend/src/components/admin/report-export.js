import { ticketStatusLabel } from '../../constants/session-ui'

const granularityLabel = (granularity) => granularity === 'WEEK' ? '按周' : '按日'
const display = (value) => value === null || value === undefined ? '' : String(value)

function csvCell(value) {
  const text = display(value)
  return /[",\r\n]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text
}

function appendSection(rows, title, headers, records) {
  rows.push([title], headers, ...records, [])
}

export function buildReportCsv({ report = {}, from, to, granularity, exportedAt = new Date().toISOString() }) {
  const satisfaction = report.satisfaction || {}
  const rows = [
    ['报表条件'],
    ['开始时间', from],
    ['结束时间', to],
    ['趋势粒度', granularityLabel(granularity)],
    ['导出时间', exportedAt],
    [],
    ['核心指标'],
    ['平均首响时长（秒）', report.averageFirstResponseSeconds],
    ['评价数量', satisfaction.ratingCount],
    ['满意度均分', satisfaction.averageRating],
    ['低分评价数', satisfaction.lowRatingCount],
    ['低分占比', satisfaction.lowRatingRate == null ? '' : `${satisfaction.lowRatingRate}%`],
    [],
  ]

  appendSection(rows, '会话量趋势', ['周期', '会话量'], (report.sessionTrend || []).map(({ bucket, count }) => [bucket, count]))
  appendSection(rows, '客服接待量排行', ['排名', '客服 ID', '客服名称', '接待会话数'], (report.agentReceptionRanking || [])
    .map(({ agentId, agentName, sessionCount }, index) => [index + 1, agentId, agentName, sessionCount]))
  appendSection(rows, '会话时长分布', ['时长区间', '已结束会话数'], (report.sessionDurationDistribution || [])
    .map(({ bucket, count }) => [bucket, count]))
  appendSection(rows, '满意度构成', ['指标', '数值'], [
    ['评价数量', satisfaction.ratingCount],
    ['低分（1-2 星）数量', satisfaction.lowRatingCount],
    ['非低分（3-5 星）数量', Math.max(0, (satisfaction.ratingCount || 0) - (satisfaction.lowRatingCount || 0))],
  ])
  appendSection(rows, '工单状态分布', ['状态', '数量'], (report.ticketStatusDistribution || [])
    .map(({ status, count }) => [ticketStatusLabel(status), count]))

  return `\uFEFF${rows.map((row) => row.map(csvCell).join(',')).join('\r\n')}`
}

export function createReportFilename(from, to, granularity) {
  return `客服系统报表_${display(from).slice(0, 10)}至${display(to).slice(0, 10)}_${granularityLabel(granularity)}.csv`
}

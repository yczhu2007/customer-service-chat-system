const ticketStatusLabels = {
  OPEN: '待处理',
  IN_PROGRESS: '处理中',
  WAITING_USER: '等待用户',
  RESOLVED: '已解决',
}

const baseTooltip = { trigger: 'axis' }
const reportBlue = '#2563EB'

function shortDate(bucket) {
  return typeof bucket === 'string' && bucket.length === 10 ? bucket.slice(5) : bucket
}

export function buildSessionTrendOption(records = []) {
  return {
    tooltip: baseTooltip,
    grid: { left: 42, right: 20, top: 24, bottom: 32 },
    xAxis: { type: 'category', boundaryGap: false, data: records.map(({ bucket }) => shortDate(bucket)) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{ name: '会话量', type: 'line', smooth: true, data: records.map(({ count }) => count) }],
  }
}

export function buildAgentReceptionOption(records = []) {
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 92, right: 20, top: 16, bottom: 24 },
    xAxis: { type: 'value', minInterval: 1 },
    yAxis: { type: 'category', data: records.map((item) => item.agentName || item.agentId).reverse() },
    series: [{ name: '接待会话', type: 'bar', itemStyle: { color: reportBlue }, data: records.map(({ sessionCount }) => sessionCount).reverse() }],
  }
}

export function buildDurationOption(records = []) {
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 42, right: 20, top: 24, bottom: 32 },
    xAxis: { type: 'category', data: records.map(({ bucket }) => bucket) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{ name: '已结束会话', type: 'bar', itemStyle: { color: reportBlue }, data: records.map(({ count }) => count) }],
  }
}

export function buildSatisfactionOption(satisfaction = {}) {
  const lowRatingCount = satisfaction.lowRatingCount || 0
  const ratingCount = satisfaction.ratingCount || 0
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie',
      radius: ['42%', '68%'],
      data: [
        { name: '低分（1-2 星）', value: lowRatingCount },
        { name: '非低分（3-5 星）', value: Math.max(0, ratingCount - lowRatingCount) },
      ],
    }],
  }
}

export function buildTicketStatusOption(records = []) {
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie',
      radius: ['42%', '68%'],
      data: records.map(({ status, count }) => ({ name: ticketStatusLabels[status] || status, value: count })),
    }],
  }
}

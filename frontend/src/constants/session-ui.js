export const STATUS_LABELS = {
  ACTIVE: '进行中',
  QUEUED: '排队中',
  CLOSED: '已结束',
  PENDING: '待处理',
}

export const ARCHIVE_STATUS_OPTIONS = [
  { code: 'COMPLETED', label: 'Solved', color: '#1f8a5f', backgroundColor: '#e7f4ee' },
  { code: 'PENDING', label: 'Pending', color: '#b7791f', backgroundColor: '#fef3c7' },
  { code: 'ON_HOLD', label: 'On-hold', color: '#635bce', backgroundColor: '#eef0ff' },
  { code: 'OTHER', label: 'Other', color: '#6a7280', backgroundColor: '#f2f3f5' },
]

export const ARCHIVE_STATUS_LABELS = Object.fromEntries(
  ARCHIVE_STATUS_OPTIONS.map(({ code, label }) => [code, label])
)

export const CATEGORY_OPTIONS = [
  { code: 'ACCOUNT', label: 'Account', color: '#2563eb', backgroundColor: '#dbeafe' },
  { code: 'PAYMENT', label: 'Payment', color: '#b45309', backgroundColor: '#fef3c7' },
  { code: 'TECHNICAL', label: 'Technical', color: '#7c3aed', backgroundColor: '#ede9fe' },
  { code: 'AFTER_SALES', label: 'After-sales', color: '#047857', backgroundColor: '#d1fae5' },
  { code: 'OTHER', label: 'Other', color: '#4b5563', backgroundColor: '#f3f4f6' },
]

export const CATEGORY_LABELS = Object.fromEntries(
  CATEGORY_OPTIONS.map(({ code, label }) => [code, label])
)

export function statusLabel(status) {
  return STATUS_LABELS[status] || status || ''
}

export function archiveStatusLabel(status) {
  return ARCHIVE_STATUS_LABELS[status] || status || ''
}

export function categoryLabel(category) {
  return CATEGORY_LABELS[category] || category || ''
}

function optionStyle(options, code) {
  const option = options.find((item) => item.code === code)
  return option ? { color: option.color, backgroundColor: option.backgroundColor } : {}
}

export function archiveStatusStyle(status) {
  return optionStyle(ARCHIVE_STATUS_OPTIONS, status)
}

export function categoryStyle(category) {
  return optionStyle(CATEGORY_OPTIONS, category)
}

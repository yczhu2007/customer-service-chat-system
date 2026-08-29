export const STATUS_LABELS = {
  ACTIVE: '进行中',
  QUEUED: '排队中',
  CLOSED: '已结束',
  PENDING: '待处理',
}

export const AGENT_VIEW_OPTIONS = [
  { code: 'MY_ACTIVE', label: '处理中' },
  { code: 'MY_TICKETS', label: '我的工单' },
  { code: 'MY_HIGH_PRIORITY', label: '高优先级' },
  { code: 'MY_UNARCHIVED', label: '未归档' },
  { code: 'MY_RECENT_CLOSED', label: '最近关闭' },
  { code: 'MY_ARCHIVED_COMPLETED', label: '已解决' },
  { code: 'MY_ARCHIVED_PENDING', label: '待处理' },
  { code: 'MY_ARCHIVED_ON_HOLD', label: '暂停' },
  { code: 'MY_ARCHIVED_OTHER', label: '其他' },
]

export const TICKET_STATUS_OPTIONS = [
  { code: 'OPEN', label: '待处理' },
  { code: 'IN_PROGRESS', label: '处理中' },
  { code: 'WAITING_USER', label: '等待用户' },
  { code: 'RESOLVED', label: '已解决' },
]

export function ticketStatusLabel(status) {
  return TICKET_STATUS_OPTIONS.find((item) => item.code === status)?.label || status || ''
}

export const ARCHIVE_STATUS_OPTIONS = [
  { code: 'COMPLETED', label: '已解决', color: '#1f8a5f', backgroundColor: '#e7f4ee' },
  { code: 'PENDING', label: '待处理', color: '#b7791f', backgroundColor: '#fef3c7' },
  { code: 'ON_HOLD', label: '暂停', color: '#635bce', backgroundColor: '#eef0ff' },
  { code: 'OTHER', label: '其他', color: '#6a7280', backgroundColor: '#f2f3f5' },
]

export const ARCHIVE_STATUS_LABELS = Object.fromEntries(
  ARCHIVE_STATUS_OPTIONS.map(({ code, label }) => [code, label])
)

export const CATEGORY_OPTIONS = [
  { code: 'ACCOUNT', label: '账号问题', color: '#2563eb', backgroundColor: '#dbeafe' },
  { code: 'PAYMENT', label: '支付问题', color: '#b45309', backgroundColor: '#fef3c7' },
  { code: 'TECHNICAL', label: '技术问题', color: '#7c3aed', backgroundColor: '#ede9fe' },
  { code: 'AFTER_SALES', label: '售后问题', color: '#047857', backgroundColor: '#d1fae5' },
  { code: 'OTHER', label: '其他', color: '#4b5563', backgroundColor: '#f3f4f6' },
]

export const PRIORITY_LABELS = {
  LOW: '低',
  NORMAL: '普通',
  HIGH: '高',
  URGENT: '紧急',
}

export const ROLE_LABELS = {
  USER: '用户',
  AGENT: '客服',
  ADMIN: '管理员',
}

const TAG_LABELS = {
  vip: 'VIP',
  urgent: '紧急',
  billing: '账单',
  payment: '支付',
  account: '账号',
  technical: '技术',
  after_sales: '售后',
  new: '新建',
}

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

export function priorityLabel(priority) {
  return PRIORITY_LABELS[priority] || priority || ''
}

export function roleLabel(role) {
  return ROLE_LABELS[role] || role || ''
}

export function formatDateTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  const twoDigits = (number) => String(number).padStart(2, '0')
  return `${date.getFullYear()}-${twoDigits(date.getMonth() + 1)}-${twoDigits(date.getDate())} ${twoDigits(date.getHours())}:${twoDigits(date.getMinutes())}`
}

export function formatListTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const now = new Date()
  const twoDigits = (number) => String(number).padStart(2, '0')
  if (date.toDateString() === now.toDateString()) {
    return `${twoDigits(date.getHours())}:${twoDigits(date.getMinutes())}`
  }
  return `${twoDigits(date.getMonth() + 1)}-${twoDigits(date.getDate())}`
}

export function tagLabel(tag) {
  return TAG_LABELS[String(tag || '').trim().toLowerCase()] || tag || ''
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

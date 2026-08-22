export const STATUS_LABELS = {
  ACTIVE: 'Open',
  QUEUED: 'Pending',
  CLOSED: 'Closed',
  PENDING: 'Pending',
}

export const ARCHIVE_STATUS_OPTIONS = [
  { code: 'COMPLETED', label: 'Solved', color: '#1f8a5f' },
  { code: 'PENDING', label: 'Pending', color: '#b7791f' },
  { code: 'ON_HOLD', label: 'On-hold', color: '#635bce' },
  { code: 'OTHER', label: 'Other', color: '#6a7280' },
]

export const ARCHIVE_STATUS_LABELS = Object.fromEntries(
  ARCHIVE_STATUS_OPTIONS.map(({ code, label }) => [code, label])
)

export const CATEGORY_LABELS = {
  ACCOUNT: 'Account',
  PAYMENT: 'Payment',
  TECHNICAL: 'Technical',
  AFTER_SALES: 'After-sales',
  OTHER: 'Other',
}

export function statusLabel(status) {
  return STATUS_LABELS[status] || status || ''
}

export function archiveStatusLabel(status) {
  return ARCHIVE_STATUS_LABELS[status] || status || ''
}

export function categoryLabel(category) {
  return CATEGORY_LABELS[category] || category || ''
}

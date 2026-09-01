function formatDateTimeLocal(date) {
  const pad = (value) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

export function createRecent30DayRange(now = new Date()) {
  const from = new Date(now)
  from.setDate(from.getDate() - 30)
  from.setHours(0, 0, 0, 0)
  const to = new Date(now)
  to.setDate(to.getDate() + 1)
  to.setHours(0, 0, 0, 0)
  return { from: formatDateTimeLocal(from), to: formatDateTimeLocal(to) }
}

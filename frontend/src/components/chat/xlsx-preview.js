// Excel 日期序列值的合理区间：20000 ≈ 1954-09-26，80000 ≈ 2119-01-15
// 用于在 numFmt 丢失时（如 WPS 中文内建格式 numFmtId=58 不被 exceljs 识别）兜底识别日期
const MIN_DATE_SERIAL = 20000
const MAX_DATE_SERIAL = 80000

function pad2(n) {
  return String(n).padStart(2, '0')
}

// 去掉格式串里的干扰token：区域前缀 [$-804]、[DBNum1]、反斜杠转义；引号字面量保留内容（如 m"月"d"日"）
function normalizeFormat(numFmt) {
  return String(numFmt)
    .replace(/\[[^\]]*\]/g, '')
    .replace(/"([^"]*)"/g, '$1')
    .replace(/\\./g, '')
    .toLowerCase()
}

// m 单独出现时分/月歧义：只有伴随 y/d/月/日 才视为日期格式
function isDateFormat(format) {
  return /[yd年月日]/.test(format) && !/[0#]/.test(format)
}

function isTimeFormat(format) {
  return /[h时分]/.test(format) && /[ms分秒]/.test(format) && !isDateFormat(format)
}

function serialToDate(serial) {
  const days = Math.floor(serial)
  const ms = Math.round((serial - days) * 86400000)
  return new Date(Date.UTC(1899, 11, 30 + days) + ms)
}

function formatDate(date, format) {
  const year = date.getUTCFullYear()
  const month = format.includes('mm') ? pad2(date.getUTCMonth() + 1) : String(date.getUTCMonth() + 1)
  const day = format.includes('dd') ? pad2(date.getUTCDate()) : String(date.getUTCDate())
  if (format.includes('月') && format.includes('日')) {
    return format.includes('y') || format.includes('年') ? `${year}年${month}月${day}日` : `${month}月${day}日`
  }
  if (!format.includes('y')) return format.includes('/') ? `${month}/${day}` : `${month}-${day}`
  const separator = format.includes('/') ? '/' : (format.includes('.') ? '.' : '-')
  return `${year}${separator}${month}${separator}${day}`
}

function formatTime(date, format) {
  const h = format.includes('hh') ? pad2(date.getUTCHours()) : String(date.getUTCHours())
  const m = pad2(date.getUTCMinutes())
  const s = pad2(date.getUTCSeconds())
  if (format.includes('时')) return format.includes('秒') ? `${h}时${m}分${s}秒` : `${h}时${m}分`
  return format.includes('s') || format.includes('ss') ? `${h}:${m}:${s}` : `${h}:${m}`
}

function excelDateText(value, numFmt) {
  const format = numFmt ? normalizeFormat(numFmt) : ''
  const date = value instanceof Date ? value : (typeof value === 'number' ? serialToDate(value) : null)
  if (!date) return null
  if (isDateFormat(format)) return formatDate(date, format)
  if (isTimeFormat(format)) return formatTime(date, format)
  if (format) return null // 有明确格式但不是日期/时间格式，不猜
  // numFmt 缺失：只有数值落在合理日期序列区间才按日期兜底（Date 对象一定是日期）
  if (value instanceof Date) return formatDate(date, 'yyyy-mm-dd')
  if (value >= MIN_DATE_SERIAL && value <= MAX_DATE_SERIAL && value % 1 === 0) return formatDate(date, 'yyyy-m-d')
  return null
}

export function previewCellText(cell) {
  const value = cell.value
  if (value == null) return ''
  const dateText = excelDateText(value, cell.numFmt)
  if (dateText) return dateText
  if (typeof value === 'object') {
    if (Array.isArray(value.richText)) return value.richText.map((part) => part.text || '').join('')
    if (value.text != null) return String(value.text)
    if (value.result != null) {
      // 公式单元格：结果按同样规则渲染（日期序列值会被正确识别）
      const resultDateText = excelDateText(value.result, cell.numFmt)
      if (resultDateText) return resultDateText
      return typeof value.result === 'object' ? JSON.stringify(value.result) : String(value.result)
    }
  }
  const text = cell.text
  return typeof text === 'string' && text !== '[object Object]' ? text : (JSON.stringify(value) || '')
}

import { ticketStatusLabel } from '../../constants/session-ui'

const granularityLabel = (granularity) => granularity === 'MONTH' ? '按月' : granularity === 'WEEK' ? '按周' : '按日'
const trendTitle = (granularity) => granularity === 'MONTH' ? '每月会话量趋势' : granularity === 'WEEK' ? '每周会话量趋势' : '每日会话量趋势'
const detailTitle = (granularity) => granularity === 'MONTH' ? '每月明细' : granularity === 'WEEK' ? '每周明细' : '每日明细'
const bucketLabel = (granularity) => granularity === 'MONTH' ? '月份' : granularity === 'WEEK' ? '周起始日' : '日期'
const display = (value) => value === null || value === undefined ? '' : String(value)
const colors = {
  navy: 'FF16324F', blue: 'FF2563EB', blueSoft: 'FFEAF2FF', cyanSoft: 'FFE8F7F5',
  amberSoft: 'FFFFF4DA', ink: 'FF172033', muted: 'FF667085', border: 'FFD9E2EC',
  panel: 'FFF8FAFC', white: 'FFFFFFFF',
}

const fill = (argb) => ({ type: 'pattern', pattern: 'solid', fgColor: { argb } })
const font = (options = {}) => ({ name: 'Microsoft YaHei', color: { argb: colors.ink }, ...options })

function styleSectionTitle(worksheet, range, title) {
  worksheet.mergeCells(range)
  const cell = worksheet.getCell(range.split(':')[0])
  cell.value = title
  cell.fill = fill(colors.blue)
  cell.font = font({ bold: true, color: { argb: colors.white }, size: 11 })
  cell.alignment = { vertical: 'middle', horizontal: 'left' }
  worksheet.getRow(cell.row).height = 23
}

function writeTable(worksheet, { row, column, endColumn, title, headers, records }) {
  styleSectionTitle(worksheet, `${worksheet.getCell(row, column).address}:${worksheet.getCell(row, endColumn).address}`, title)
  const headerRow = row + 1
  headers.forEach((value, index) => {
    const cell = worksheet.getCell(headerRow, column + index)
    cell.value = value
    cell.fill = fill(colors.blueSoft)
    cell.font = font({ bold: true, color: { argb: colors.navy } })
    cell.alignment = { vertical: 'middle', horizontal: 'center' }
    cell.border = { bottom: { style: 'thin', color: { argb: colors.border } } }
  })
  const rows = records.length ? records : [['暂无数据']]
  rows.forEach((values, recordIndex) => {
    values.forEach((value, valueIndex) => {
      const cell = worksheet.getCell(headerRow + 1 + recordIndex, column + valueIndex)
      cell.value = value
      cell.font = font({ size: 10 })
      cell.alignment = { vertical: 'middle', horizontal: typeof value === 'number' ? 'right' : 'left' }
      cell.border = { bottom: { style: 'thin', color: { argb: colors.border } } }
    })
  })
  return headerRow + rows.length
}

function writeKpi(worksheet, range, label, value, panelFill, numberFormat) {
  const [start, end] = range.split(':')
  const startCell = worksheet.getCell(start)
  const endCell = worksheet.getCell(end)
  const labelRange = `${startCell.address}:${worksheet.getCell(startCell.row, endCell.col).address}`
  const valueRange = `${worksheet.getCell(startCell.row + 1, startCell.col).address}:${endCell.address}`
  worksheet.mergeCells(labelRange)
  worksheet.mergeCells(valueRange)
  const labelCell = worksheet.getCell(start)
  labelCell.value = label
  labelCell.font = font({ bold: true, color: { argb: colors.muted }, size: 10 })
  labelCell.alignment = { vertical: 'middle', horizontal: 'center' }
  const valueCell = worksheet.getCell(startCell.row + 1, startCell.col)
  valueCell.value = value ?? ''
  valueCell.font = font({ bold: true, color: { argb: colors.navy }, size: 20 })
  valueCell.alignment = { vertical: 'middle', horizontal: 'center' }
  valueCell.numFmt = numberFormat
  for (let row = startCell.row; row <= endCell.row; row += 1) {
    for (let column = startCell.col; column <= endCell.col; column += 1) {
      const cell = worksheet.getCell(row, column)
      cell.fill = fill(panelFill)
      cell.border = {
        top: row === startCell.row ? { style: 'thin', color: { argb: colors.border } } : undefined,
        bottom: row === endCell.row ? { style: 'thin', color: { argb: colors.border } } : undefined,
        left: column === startCell.col ? { style: 'thin', color: { argb: colors.border } } : undefined,
        right: column === endCell.col ? { style: 'thin', color: { argb: colors.border } } : undefined,
      }
    }
  }
}

function formatExportTime(value) {
  const date = new Date(value)
  return Number.isNaN(date.valueOf()) ? display(value) : date.toLocaleString('zh-CN', { hour12: false }).replaceAll('/', '-')
}

function addDataBar(worksheet, range, color) {
  worksheet.addConditionalFormatting({
    ref: range,
    rules: [{ type: 'dataBar', cfvo: [{ type: 'min' }, { type: 'max' }], color: { argb: color }, gradient: true }],
  })
}

export async function buildReportXlsx({ report = {}, from, to, granularity, exportedAt = new Date().toISOString(), trendChartImage }) {
  const { Workbook } = await import('exceljs')
  const workbook = new Workbook()
  const worksheet = workbook.addWorksheet('报表', {
    views: [{ state: 'frozen', ySplit: 4, showGridLines: false }],
    pageSetup: { orientation: 'landscape', fitToPage: true, fitToWidth: 1, fitToHeight: 0 },
  })
  const satisfaction = report.satisfaction || {}
  const sessionTrend = report.sessionTrend || []
  const agentRows = (report.agentReceptionRanking || []).map(({ agentId, agentName, sessionCount }, index) => [index + 1, agentId, agentName, sessionCount])
  const durationRows = (report.sessionDurationDistribution || []).map(({ bucket, count }) => [bucket, count])
  const satisfactionRows = [
    ['评价数量', satisfaction.ratingCount],
    ['低分（1-2 星）数量', satisfaction.lowRatingCount],
    ['非低分（3-5 星）数量', Math.max(0, (satisfaction.ratingCount || 0) - (satisfaction.lowRatingCount || 0))],
  ]
  const ticketRows = (report.ticketStatusDistribution || []).map(({ status, count }) => [ticketStatusLabel(status), count])

  worksheet.mergeCells('A1:N2')
  const title = worksheet.getCell('A1')
  title.value = '客服系统运营报表'
  title.fill = fill(colors.navy)
  title.font = font({ bold: true, size: 20, color: { argb: colors.white } })
  title.alignment = { horizontal: 'center', vertical: 'middle' }
  worksheet.getRow(1).height = 27
  worksheet.getRow(2).height = 27

  ;[['A3:B3', '统计范围'], ['I3:J3', '趋势粒度'], ['A4:B4', '导出时间'], ['I4:J4', '报表说明']].forEach(([range, label]) => {
    worksheet.mergeCells(range)
    const cell = worksheet.getCell(range.split(':')[0])
    cell.value = label
    cell.fill = fill(colors.panel)
    cell.font = font({ bold: true, color: { argb: colors.muted } })
  })
  ;[['C3:H3', `${display(from).slice(0, 10)} 至 ${display(to).slice(0, 10)}`], ['K3:N3', granularityLabel(granularity)], ['C4:H4', formatExportTime(exportedAt)], ['K4:N4', '运营数据快照']].forEach(([range, value]) => {
    worksheet.mergeCells(range)
    const cell = worksheet.getCell(range.split(':')[0])
    cell.value = value
    cell.fill = fill(colors.panel)
    cell.font = font()
  })

  writeKpi(worksheet, 'A6:D9', '平均首响时长（秒）', report.averageFirstResponseSeconds, colors.blueSoft, '0 "秒"')
  writeKpi(worksheet, 'F6:I9', '满意度均分', satisfaction.averageRating, colors.cyanSoft, '0.00 "分"')
  writeKpi(worksheet, 'K6:N9', '低分占比', satisfaction.lowRatingRate == null ? '' : satisfaction.lowRatingRate / 100, colors.amberSoft, '0.0%')

  styleSectionTitle(worksheet, 'A11:I11', trendTitle(granularity))
  if (trendChartImage) {
    const imageId = workbook.addImage({ base64: trendChartImage, extension: 'png' })
    worksheet.addImage(imageId, { tl: { col: 0, row: 11 }, br: { col: 9, row: 29 }, editAs: 'oneCell' })
  } else {
    worksheet.mergeCells('A12:I29')
    const emptyChart = worksheet.getCell('A12')
    emptyChart.value = '趋势图暂不可用，请查看右侧明细'
    emptyChart.font = font({ color: { argb: colors.muted } })
    emptyChart.alignment = { vertical: 'middle', horizontal: 'center' }
  }

  styleSectionTitle(worksheet, 'K11:N11', detailTitle(granularity))
  ;[['K12', bucketLabel(granularity)], ['L12', '会话量']].forEach(([address, value]) => {
    const cell = worksheet.getCell(address)
    cell.value = value
    cell.fill = fill(colors.blueSoft)
    cell.font = font({ bold: true, color: { argb: colors.navy } })
    cell.alignment = { horizontal: 'center', vertical: 'middle' }
  })
  sessionTrend.forEach(({ bucket, count }, index) => {
    const row = 13 + index
    worksheet.getCell(row, 11).value = bucket
    worksheet.getCell(row, 12).value = count
    worksheet.getCell(row, 12).numFmt = '#,##0'
    for (const column of [11, 12]) {
      const cell = worksheet.getCell(row, column)
      cell.font = font({ size: 10 })
      cell.alignment = { horizontal: column === 12 ? 'right' : 'left', vertical: 'middle' }
      cell.border = { bottom: { style: 'thin', color: { argb: colors.border } } }
    }
  })
  if (sessionTrend.length) addDataBar(worksheet, `L13:L${12 + sessionTrend.length}`, colors.blue)

  const upperTableEnd = Math.max(34 + agentRows.length, 34 + durationRows.length)
  const lowerSectionRow = Math.max(40, upperTableEnd + 1)
  writeTable(worksheet, { row: 32, column: 1, endColumn: 4, title: '客服接待量排行', headers: ['排名', '客服 ID', '客服名称', '接待会话数'], records: agentRows })
  const durationEnd = writeTable(worksheet, { row: 32, column: 6, endColumn: 9, title: '会话时长分布', headers: ['时长区间', '已结束会话数'], records: durationRows })
  if (durationRows.length) addDataBar(worksheet, `G34:G${durationEnd}`, 'FF14B8A6')
  writeTable(worksheet, { row: lowerSectionRow, column: 1, endColumn: 4, title: '满意度构成', headers: ['指标', '数值'], records: satisfactionRows })
  writeTable(worksheet, { row: lowerSectionRow, column: 6, endColumn: 9, title: '工单状态分布', headers: ['状态', '数量'], records: ticketRows })

  const detailEndRow = 12 + sessionTrend.length
  const lowerEndRow = lowerSectionRow + Math.max(satisfactionRows.length, ticketRows.length, 1) + 1
  const noteRow = Math.max(47, detailEndRow + 2, lowerEndRow + 2)
  worksheet.mergeCells(noteRow, 1, noteRow, 14)
  const note = worksheet.getCell(noteRow, 1)
  note.value = '说明：本文件由管理员报表页面导出，统计数据与当前筛选条件一致。'
  note.fill = fill(colors.panel)
  note.font = font({ italic: true, color: { argb: colors.muted }, size: 9 })

  ;[22, 10, 15, 12, 3, 12, 12, 3, 14, 3, 16, 11, 3, 3].forEach((width, index) => { worksheet.getColumn(index + 1).width = width })
  worksheet.eachRow((row) => { row.height ||= 21 })
  worksheet.pageSetup.printArea = `A1:N${noteRow}`
  return workbook
}

export function createReportFilename(from, to, granularity) {
  return `客服系统报表_${display(from).slice(0, 10)}至${display(to).slice(0, 10)}_${granularityLabel(granularity)}.xlsx`
}

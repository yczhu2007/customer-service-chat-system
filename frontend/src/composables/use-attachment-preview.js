import { nextTick, ref } from 'vue'
import { previewCellText } from '../components/chat/xlsx-preview'

export function previewKind(attachment) {
  const name = attachment?.name?.toLowerCase() || ''
  if (attachment?.type === 'application/pdf' || name.endsWith('.pdf')) return 'pdf'
  if (attachment?.type === 'text/plain' || name.endsWith('.txt')) return 'text'
  if (attachment?.type === 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' || name.endsWith('.docx')) return 'docx'
  if (attachment?.type === 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' || name.endsWith('.xlsx')) return 'xlsx'
  if (attachment?.type === 'application/vnd.ms-excel' || name.endsWith('.xls')) return 'xlsx'
  if (attachment?.type === 'application/msword' || name.endsWith('.doc')) return 'legacy-office'
  if (attachment?.type === 'application/vnd.ms-powerpoint' || name.endsWith('.ppt')) return 'legacy-office'
  if (attachment?.type === 'application/vnd.openxmlformats-officedocument.presentationml.presentation' || name.endsWith('.pptx')) return 'legacy-office'
  return null
}

export function useAttachmentPreview() {
  const filePreview = ref(null)
  const docxPreviewEl = ref(null)

  function closeFilePreview() {
    if (filePreview.value?.revokeOnClose && filePreview.value.url && typeof URL.revokeObjectURL === 'function') {
      URL.revokeObjectURL(filePreview.value.url)
    }
    filePreview.value = null
  }

  async function openFilePreview(attachment) {
    const kind = previewKind(attachment)
    if (!kind) return
    if (kind === 'pdf') {
      filePreview.value = { kind, name: attachment.name, url: attachment.url }
      return
    }
    if (kind === 'docx') {
      filePreview.value = { kind, name: attachment.name, loading: true }
      await nextTick()
      try {
        const { renderAsync } = await import('docx-preview')
        await renderAsync(attachment.blob, docxPreviewEl.value, null, { inWrapper: false })
        filePreview.value.loading = false
      } catch (error) {
        filePreview.value = { kind, name: attachment.name, error: `DOCX 预览加载失败：${error.message || '文件格式无效'}` }
      }
      return
    }
    if (kind === 'xlsx') {
      filePreview.value = { kind, name: attachment.name, loading: true, sheets: [], sheetIndex: 0 }
      const isLegacyXls = (attachment.name || '').toLowerCase().endsWith('.xls')
      try {
        if (isLegacyXls) {
          const XLSX = await import('xlsx')
          const workbook = XLSX.read(await attachment.blob.arrayBuffer(), { type: 'array' })
          filePreview.value = {
            kind,
            name: attachment.name,
            sheetIndex: 0,
            sheets: workbook.SheetNames.map((sheetName) => ({
              name: sheetName,
              rows: XLSX.utils.sheet_to_json(
                workbook.Sheets[sheetName],
                { header: 1, raw: false, defval: '' }
              ).slice(0, 100).map((row) => row.slice(0, 20)),
            })),
          }
          return
        }
        const { Workbook } = await import('exceljs')
        const workbook = new Workbook()
        await workbook.xlsx.load(await attachment.blob.arrayBuffer())
        filePreview.value = {
          kind,
          name: attachment.name,
          sheetIndex: 0,
          sheets: workbook.worksheets.map((sheet) => ({
            name: sheet.name,
            rows: Array.from({ length: Math.min(sheet.rowCount, 100) }, (_, rowIndex) =>
              Array.from({ length: Math.min(sheet.columnCount, 20) }, (_, columnIndex) =>
                previewCellText(sheet.getRow(rowIndex + 1).getCell(columnIndex + 1))
              )
            ),
          })),
        }
      } catch (error) {
        filePreview.value = { kind, name: attachment.name, error: `${isLegacyXls ? 'XLS' : 'XLSX'} 预览加载失败：${error.message || '文件格式无效'}` }
      }
      return
    }
    filePreview.value = {
      kind,
      name: attachment.name,
      text: attachment.blob ? '加载中…' : '文本预览不可用',
    }
    attachment.blob?.text()
      .then((text) => { if (filePreview.value?.name === attachment.name) filePreview.value.text = text })
      .catch(() => { if (filePreview.value?.name === attachment.name) filePreview.value.text = '文本预览加载失败' })
  }

  return { filePreview, docxPreviewEl, closeFilePreview, openFilePreview }
}

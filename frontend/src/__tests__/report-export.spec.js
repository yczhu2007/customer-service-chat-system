import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { buildReportXlsx, createReportFilename } from '../components/admin/report-export'

vi.mock('../api/admin-api', () => ({ findAdminReportOverview: vi.fn() }))
vi.mock('../components/admin/report-export', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, buildReportXlsx: vi.fn(actual.buildReportXlsx) }
})

const report = {
  sessionTrend: [{ bucket: '2026-08-02', count: 3 }],
  agentReceptionRanking: [{ agentId: 'A001', agentName: '客服,小王', sessionCount: 2 }],
  averageFirstResponseSeconds: 65,
  sessionDurationDistribution: [{ bucket: '0-5 分钟', count: 1 }],
  satisfaction: { ratingCount: 4, averageRating: 4.5, lowRatingCount: 1, lowRatingRate: 25 },
  ticketStatusDistribution: [{ status: 'OPEN', count: 2 }],
}

const onePixelPng = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M/wHwAF/gL+X8W9WQAAAABJRU5ErkJggg=='

describe('admin report export', () => {
  it('exports a compact dashboard layout instead of stacking every section vertically', async () => {
    const workbook = await buildReportXlsx({
      report,
      from: '2026-08-01T00:00',
      to: '2026-09-01T00:00',
      granularity: 'DAY',
      exportedAt: '2026-09-01T10:00:00',
      trendChartImage: onePixelPng,
    })

    const worksheet = workbook.getWorksheet('报表')
    expect(worksheet.getCell('A1').value).toBe('客服系统运营报表')
    expect(worksheet.getCell('N1').isMerged).toBe(true)
    expect(worksheet.views[0]).toMatchObject({ state: 'frozen', ySplit: 4, showGridLines: false })
    expect(worksheet.getCell('A7').value).toBe(65)
    expect(worksheet.getCell('F7').value).toBe(4.5)
    expect(worksheet.getCell('K7').value).toBe(0.25)
    expect(worksheet.getCell('K7').numFmt).toBe('0.0%')
    expect(worksheet.getCell('A11').value).toBe('每日会话量趋势')
    expect(worksheet.getCell('K12').value).toBe('日期')
    expect(worksheet.getCell('K13').value).toBe('2026-08-02')
    expect(worksheet.getCell('A32').value).toBe('客服接待量排行')
    expect(worksheet.getCell('F32').value).toBe('会话时长分布')
    expect(worksheet.getCell('A40').value).toBe('满意度构成')
    expect(worksheet.getCell('F40').value).toBe('工单状态分布')
    expect(worksheet.getImages()).toHaveLength(1)
  })

  it('uses a safe filename derived from the selected range and granularity', () => {
    expect(createReportFilename('2026-08-01T00:00', '2026-09-01T00:00', 'WEEK'))
      .toBe('客服系统报表_2026-08-01至2026-09-01_按周.xlsx')
  })

  it('passes the rendered trend chart into the XLSX export after the report has loaded', async () => {
    const { findAdminReportOverview } = await import('../api/admin-api')
    findAdminReportOverview.mockResolvedValueOnce({ data: report })
    buildReportXlsx.mockResolvedValueOnce({ xlsx: { writeBuffer: vi.fn().mockResolvedValue(new ArrayBuffer(0)) } })
    URL.createObjectURL = vi.fn(() => 'blob:report')
    URL.revokeObjectURL = vi.fn()
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    const AdminReportPanel = (await import('../components/admin/AdminReportPanel.vue')).default

    const wrapper = mount(AdminReportPanel, {
      global: {
        stubs: {
          ReportChart: {
            template: '<div />',
            setup(_, { expose }) {
              expose({ getDataURL: () => onePixelPng })
            },
          },
        },
      },
    })
    await flushPromises()

    expect(wrapper.get('button.export-report').text()).toBe('导出 XLSX')
    expect(wrapper.get('button.export-report').attributes('disabled')).toBeUndefined()
    await wrapper.get('button.export-report').trigger('click')
    await flushPromises()
    expect(buildReportXlsx).toHaveBeenLastCalledWith(expect.objectContaining({ trendChartImage: onePixelPng }))
    click.mockRestore()
  })
})

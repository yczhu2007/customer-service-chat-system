import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { buildReportCsv, createReportFilename } from '../components/admin/report-export'

vi.mock('../api/admin-api', () => ({ findAdminReportOverview: vi.fn() }))

const report = {
  sessionTrend: [{ bucket: '2026-08-02', count: 3 }],
  agentReceptionRanking: [{ agentId: 'A001', agentName: '客服,小王', sessionCount: 2 }],
  averageFirstResponseSeconds: 65,
  sessionDurationDistribution: [{ bucket: '0-5 分钟', count: 1 }],
  satisfaction: { ratingCount: 4, averageRating: 4.5, lowRatingCount: 1, lowRatingRate: 25 },
  ticketStatusDistribution: [{ status: 'OPEN', count: 2 }],
}

describe('admin report export', () => {
  it('exports the selected range and every report section as Excel-readable CSV', () => {
    const csv = buildReportCsv({
      report,
      from: '2026-08-01T00:00',
      to: '2026-09-01T00:00',
      granularity: 'DAY',
      exportedAt: '2026-09-01T10:00:00',
    })

    expect(csv).toContain('\uFEFF报表条件')
    expect(csv).toContain('开始时间,2026-08-01T00:00')
    expect(csv).toContain('趋势粒度,按日')
    expect(csv).toContain('平均首响时长（秒）,65')
    expect(csv).toContain('会话量趋势\r\n周期,会话量\r\n2026-08-02,3')
    expect(csv).toContain('客服接待量排行\r\n排名,客服 ID,客服名称,接待会话数\r\n1,A001,"客服,小王",2')
    expect(csv).toContain('满意度构成\r\n指标,数值\r\n评价数量,4')
    expect(csv).toContain('工单状态分布\r\n状态,数量\r\n待处理,2')
  })

  it('uses a safe filename derived from the selected range and granularity', () => {
    expect(createReportFilename('2026-08-01T00:00', '2026-09-01T00:00', 'WEEK'))
      .toBe('客服系统报表_2026-08-01至2026-09-01_按周.csv')
  })

  it('offers CSV export only after the current report has loaded', async () => {
    const { findAdminReportOverview } = await import('../api/admin-api')
    findAdminReportOverview.mockResolvedValueOnce({ data: report })
    const AdminReportPanel = (await import('../components/admin/AdminReportPanel.vue')).default

    const wrapper = mount(AdminReportPanel, { global: { stubs: { ReportChart: true } } })
    await flushPromises()

    expect(wrapper.get('button.export-report').text()).toBe('导出 CSV')
    expect(wrapper.get('button.export-report').attributes('disabled')).toBeUndefined()
  })
})

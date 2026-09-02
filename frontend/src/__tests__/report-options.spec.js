import { describe, expect, it } from 'vitest'
import { buildAgentReceptionOption, buildDurationOption, buildSatisfactionOption, buildSessionTrendOption } from '../components/admin/report-options'

describe('admin report chart options', () => {
  it('builds a zero-friendly session trend line', () => {
    const option = buildSessionTrendOption([
      { bucket: '2026-08-01', count: 0 },
      { bucket: '2026-08-02', count: 3 },
    ])

    expect(option.xAxis.data).toEqual(['08-01', '08-02'])
    expect(option.series[0].data).toEqual([0, 3])
  })

  it('uses the shared blue for session bar charts', () => {
    expect(buildAgentReceptionOption([{ agentName: '客服', sessionCount: 2 }]).series[0].itemStyle.color)
      .toBe('#2563EB')
    expect(buildDurationOption([{ bucket: '0-5 分钟', count: 1 }]).series[0].itemStyle.color)
      .toBe('#2563EB')
  })

  it('builds satisfaction data that clearly labels the low-score share', () => {
    const option = buildSatisfactionOption({ ratingCount: 4, lowRatingCount: 1 })

    expect(option.series[0].data).toEqual([
      { name: '低分（1-2 星）', value: 1 },
      { name: '非低分（3-5 星）', value: 3 },
    ])
  })
})

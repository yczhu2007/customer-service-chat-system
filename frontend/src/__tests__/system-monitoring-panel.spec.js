import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'

vi.mock('../api/admin-api', () => ({
  findSystemMonitoringSnapshot: vi.fn(),
}))

const snapshot = {
  redisAvailable: true,
  queueLength: 12,
  deadLetterBacklog: 2,
  onlineConnections: { user: 7, agent: 3 },
  messagePersistenceLatency: { count: 5, averageMillis: 18.2, p95Millis: 30.4, maxMillis: 44.1, failureCount: 0 },
  sessionLockLatency: { count: 8, averageMillis: 2.2, p95Millis: 4.5, maxMillis: 6.1, failureCount: 1 },
  collectedAt: '2026-09-09T05:00:00Z',
}

async function flushLoading() {
  await vi.dynamicImportSettled()
  await Promise.resolve()
  await nextTick()
}

describe('SystemMonitoringPanel', () => {
  beforeEach(async () => {
    vi.useFakeTimers()
    const { findSystemMonitoringSnapshot } = await import('../api/admin-api')
    findSystemMonitoringSnapshot.mockReset().mockResolvedValue({ data: snapshot })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('loads immediately and refreshes every fifteen seconds', async () => {
    const { findSystemMonitoringSnapshot } = await import('../api/admin-api')
    const SystemMonitoringPanel = (await import('../components/admin/SystemMonitoringPanel.vue')).default
    const wrapper = mount(SystemMonitoringPanel)
    await flushLoading()

    expect(findSystemMonitoringSnapshot).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(15_000)
    await nextTick()
    expect(findSystemMonitoringSnapshot).toHaveBeenCalledTimes(2)

    wrapper.unmount()
  })

  it('keeps the last snapshot when a background refresh fails', async () => {
    const { findSystemMonitoringSnapshot } = await import('../api/admin-api')
    findSystemMonitoringSnapshot
      .mockResolvedValueOnce({ data: snapshot })
      .mockRejectedValueOnce(new Error('连接监控接口失败'))
    const SystemMonitoringPanel = (await import('../components/admin/SystemMonitoringPanel.vue')).default
    const wrapper = mount(SystemMonitoringPanel)
    await flushLoading()

    await vi.advanceTimersByTimeAsync(15_000)
    await nextTick()

    expect(wrapper.text()).toContain('12')
    expect(wrapper.text()).toContain('连接监控接口失败')
    wrapper.unmount()
  })

  it('clears its polling timer when unmounted', async () => {
    const SystemMonitoringPanel = (await import('../components/admin/SystemMonitoringPanel.vue')).default
    const wrapper = mount(SystemMonitoringPanel)
    await flushLoading()

    expect(vi.getTimerCount()).toBe(1)
    wrapper.unmount()
    expect(vi.getTimerCount()).toBe(0)
  })
})

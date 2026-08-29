import { describe, expect, it } from 'vitest'
import {
  AGENT_VIEW_OPTIONS,
  ARCHIVE_STATUS_OPTIONS,
  CATEGORY_OPTIONS,
  archiveStatusStyle,
  categoryStyle,
} from '../constants/session-ui'
import * as sessionUi from '../constants/session-ui'

describe('session enum presentation', () => {
  it('keeps one frontend agent view list without the unread view', () => {
    expect(AGENT_VIEW_OPTIONS.map((option) => option.code)).toEqual([
      'MY_ACTIVE',
      'MY_TICKETS',
      'MY_HIGH_PRIORITY',
      'MY_UNARCHIVED',
      'MY_RECENT_CLOSED',
      'MY_ARCHIVED_COMPLETED',
      'MY_ARCHIVED_PENDING',
      'MY_ARCHIVED_ON_HOLD',
      'MY_ARCHIVED_OTHER',
    ])
  })

  it('shows Chinese labels for selectable archive and category values', () => {
    expect(ARCHIVE_STATUS_OPTIONS.map((option) => option.label)).toEqual([
      '已解决', '待处理', '暂停', '其他',
    ])
    expect(CATEGORY_OPTIONS.map((option) => option.label)).toEqual([
      '账号问题', '支付问题', '技术问题', '售后问题', '其他',
    ])
  })

  it('gives each selectable archive and category value its own color', () => {
    expect(new Set(ARCHIVE_STATUS_OPTIONS.map((option) => archiveStatusStyle(option.code).color)).size)
      .toBe(ARCHIVE_STATUS_OPTIONS.length)
    expect(new Set(CATEGORY_OPTIONS.map((option) => categoryStyle(option.code).color)).size)
      .toBe(CATEGORY_OPTIONS.length)
  })

  it('formats role and detail time values for every workspace', () => {
    expect(sessionUi.roleLabel?.('AGENT')).toBe('客服')
    expect(sessionUi.roleLabel?.('ADMIN')).toBe('管理员')
    expect(sessionUi.formatDateTime?.('2026-08-28T09:05:00')).toBe('2026-08-28 09:05')
  })
})

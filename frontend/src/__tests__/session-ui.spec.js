import { describe, expect, it } from 'vitest'
import {
  ARCHIVE_STATUS_OPTIONS,
  CATEGORY_OPTIONS,
  archiveStatusStyle,
  categoryStyle,
} from '../constants/session-ui'

describe('session enum presentation', () => {
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
})

import { describe, expect, it } from 'vitest'
import {
  ARCHIVE_STATUS_OPTIONS,
  CATEGORY_OPTIONS,
  archiveStatusStyle,
  categoryStyle,
} from '../constants/session-ui'

describe('session enum presentation', () => {
  it('keeps Zendesk-style English labels for selectable archive and category values', () => {
    expect(ARCHIVE_STATUS_OPTIONS.map((option) => option.label)).toEqual([
      'Solved', 'Pending', 'On-hold', 'Other',
    ])
    expect(CATEGORY_OPTIONS.map((option) => option.label)).toEqual([
      'Account', 'Payment', 'Technical', 'After-sales', 'Other',
    ])
  })

  it('gives each selectable archive and category value its own color', () => {
    expect(new Set(ARCHIVE_STATUS_OPTIONS.map((option) => archiveStatusStyle(option.code).color)).size)
      .toBe(ARCHIVE_STATUS_OPTIONS.length)
    expect(new Set(CATEGORY_OPTIONS.map((option) => categoryStyle(option.code).color)).size)
      .toBe(CATEGORY_OPTIONS.length)
  })
})

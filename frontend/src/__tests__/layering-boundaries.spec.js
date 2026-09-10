// @vitest-environment node
import { describe, expect, it } from 'vitest'
import { readFile } from 'node:fs/promises'
import { join } from 'node:path'

const source = (...parts) => readFile(join(process.cwd(), 'src', ...parts), 'utf8')

describe('frontend responsibility boundaries', () => {
  it('keeps attachment parsing out of MessageList', async () => {
    const messageList = await source('components', 'chat', 'MessageList.vue')
    const preview = await source('composables', 'use-attachment-preview.js').catch(() => '')

    expect(messageList).not.toContain("await import('exceljs')")
    expect(messageList).not.toContain("await import('docx-preview')")
    expect(preview).toContain("await import('exceljs')")
    expect(preview).toContain("await import('docx-preview')")
  })

  it('keeps session audit URLs in the admin API module', async () => {
    const panel = await source('components', 'admin', 'SessionAuditPanel.vue')
    const api = await source('api', 'admin-api.js')

    expect(panel).not.toContain("import { request } from '../../services/http-client'")
    expect(panel).not.toContain('request(`/chat/')
    expect(api).toContain('listAdminSessions')
    expect(api).toContain('findSessionMetadata')
  })
})

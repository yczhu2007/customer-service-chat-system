// @vitest-environment node
import { describe, expect, it } from 'vitest'
import { readdir, readFile } from 'node:fs/promises'
import { join } from 'node:path'

const sourceRoot = join(process.cwd(), 'src')

async function vueFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true })
  const nested = await Promise.all(entries.map((entry) => {
    const path = join(directory, entry.name)
    return entry.isDirectory() ? vueFiles(path) : entry.name.endsWith('.vue') ? [path] : []
  }))
  return nested.flat()
}

describe('workspace visual text', () => {
  it('does not use emoji in Vue interface files', async () => {
    const files = await vueFiles(sourceRoot)
    const contents = await Promise.all(files.map((file) => readFile(file, 'utf8')))

    expect(contents.join('\n')).not.toMatch(/\p{Extended_Pictographic}/u)
  })

  it('defines the stomp-test primary color as a global design token', async () => {
    const app = await readFile(join(sourceRoot, 'App.vue'), 'utf8')

    expect(app).toContain('--color-primary: #4d6bfe')
  })

  it('keeps ordinary ticket interface copy in Chinese', async () => {
    const files = [
      'components/session/UserSessionList.vue',
      'components/session/SessionArchiveActions.vue',
      'components/session/SessionMetadataEditor.vue',
      'components/admin/SessionAuditPanel.vue',
    ]
    const contents = (await Promise.all(files.map((file) => readFile(join(sourceRoot, file), 'utf8')))).join('\n')

    expect(contents).toContain('我的会话')
    expect(contents).toContain('归档操作')
    expect(contents).toContain('当前状态')
    expect(contents).toContain('会话元数据')
    expect(contents).toContain('开始时间')
    expect(contents).toContain('结束时间')
  })

  it('keeps the category selector visually neutral', async () => {
    const metadataEditor = await readFile(
      join(sourceRoot, 'components/session/SessionMetadataEditor.vue'),
      'utf8'
    )

    expect(metadataEditor).not.toContain(':style="category ? categoryStyle(category) : undefined"')
  })

  it('uses the requested short Chinese placeholder for the agent category selector', async () => {
    const metadataEditor = await readFile(
      join(sourceRoot, 'components/session/SessionMetadataEditor.vue'),
      'utf8'
    )

    expect(metadataEditor).toContain('placeholder="选择"')
  })

  it('does not duplicate admin navigation in the dashboard content', async () => {
    const dashboard = await readFile(
      join(sourceRoot, 'components/admin/AdminDashboard.vue'),
      'utf8'
    )

    expect(dashboard).not.toContain('快捷入口')
    expect(dashboard).not.toContain('rating-summary')
    expect(dashboard).not.toContain('quickLinks')
  })
})

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
})

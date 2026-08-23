// @vitest-environment node
import { describe, expect, it } from 'vitest'
import config from '../../vite.config.js'

function resolveConfig(command) {
  return typeof config === 'function' ? config({ command, mode: 'test' }) : config
}

describe('asset base paths', () => {
  it('publishes Vue assets below the Spring Boot frontend path in production', () => {
    expect(resolveConfig('build').base).toBe('/frontend/')
  })

  it('serves the Vue app from the root during Vite development', () => {
    expect(resolveConfig('serve').base).toBe('/')
  })
})

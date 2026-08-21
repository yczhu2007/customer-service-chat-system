// @vitest-environment node
import { describe, expect, it } from 'vitest'
import config from '../../vite.config.js'

describe('production asset base path', () => {
  it('publishes Vue assets below the Spring Boot frontend path', () => {
    expect(config.base).toBe('/frontend/')
  })
})

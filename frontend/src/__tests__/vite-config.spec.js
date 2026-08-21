// @vitest-environment node
import { describe, expect, it } from 'vitest'
import config from '../../vite.config.js'

describe('production asset base path', () => {
  it('publishes Vue assets at the Vercel site root', () => {
    expect(config.base).toBe('/')
  })
})

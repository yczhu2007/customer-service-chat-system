// @vitest-environment node
import { readFileSync } from 'node:fs'
import { fileURLToPath, URL } from 'node:url'
import { describe, expect, it } from 'vitest'
import config from '../../vite.config.js'

const frontendPackage = JSON.parse(readFileSync(fileURLToPath(new URL('../../package.json', import.meta.url)), 'utf8'))
const applicationPom = readFileSync(fileURLToPath(new URL('../../../application/pom.xml', import.meta.url)), 'utf8')

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

  it('uses the available local development address', () => {
    expect(resolveConfig('serve').server.host).toBe('127.0.0.1')
    expect(resolveConfig('serve').server.port).toBe(4173)
  })

  it('uses Vite default chunking', () => {
    expect(resolveConfig('build').build?.rollupOptions).toBeUndefined()
  })

  it('separates frontend bundling from backend static synchronization', () => {
    expect(frontendPackage.scripts.build).toBe('vite build')
    expect(frontendPackage.scripts['build:sync']).toBe('npm run build && node scripts/sync-backend-static.mjs')
  })

  it('does not download a private Node runtime during Maven builds', () => {
    expect(applicationPom).not.toContain('frontend-maven-plugin')
    expect(applicationPom).not.toContain('install-node-and-npm')
  })
})

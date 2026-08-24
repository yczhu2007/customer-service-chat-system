import { cp, mkdir, rm } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const source = path.join(frontendRoot, 'dist')
const destination = path.resolve(frontendRoot, '..', 'application', 'target', 'classes', 'static', 'frontend')

await mkdir(path.dirname(destination), { recursive: true })
await rm(destination, { recursive: true, force: true })
await cp(source, destination, { recursive: true })
console.log(`Frontend bundle synced to ${destination}`)

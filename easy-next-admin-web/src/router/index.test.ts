import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const routerSource = readFileSync(fileURLToPath(new URL('./index.ts', import.meta.url)), 'utf-8')

describe('router dynamic route bootstrap', () => {
  it('preserves query and hash when replaying a dynamic route after menu sync', () => {
    expect(routerSource).toContain('path: to.path, query: to.query, hash: to.hash, replace: true')
    expect(routerSource).not.toContain('path: to.fullPath, replace: true')
  })
})

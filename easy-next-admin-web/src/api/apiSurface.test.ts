import { readdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

describe('api infrastructure surface', () => {
  it('keeps business api wrappers inside feature modules', () => {
    const apiFiles = readdirSync(resolve(__dirname))
      .filter((file) => file.endsWith('.ts') && !file.endsWith('.test.ts'))
      .sort()

    expect(apiFiles).toEqual(['request.ts', 'trace.ts', 'types.ts'])
  })
})

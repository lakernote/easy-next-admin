import { readFileSync } from 'node:fs'
import { fileURLToPath, URL } from 'node:url'
import { describe, expect, it } from 'vitest'

const source = readFileSync(fileURLToPath(new URL('./EasyChart.vue', import.meta.url)), 'utf-8')

describe('EasyChart', () => {
  it('registers the data zoom component used by diagnosis trend charts', () => {
    expect(source).toContain('DataZoomComponent')
    expect(source).toMatch(/use\(\[[\s\S]*DataZoomComponent[\s\S]*\]\)/)
  })
})

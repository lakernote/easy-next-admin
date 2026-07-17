import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const viewSource = readFileSync(resolve(__dirname, 'BatchTaskView.vue'), 'utf-8')

describe('batch task view observability', () => {
  it('shows item worker and lease ownership', () => {
    expect(viewSource).toContain('row.workerId')
    expect(viewSource).toContain('row.leaseUntil')
    expect(viewSource).toContain('执行实例 / 租约')
  })
})

import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const viewSource = readFileSync(resolve(__dirname, 'JobView.vue'), 'utf-8')

describe('schedule job view layout', () => {
  it('uses a list-first page with row-level log drawer', () => {
    expect(viewSource).toContain('schedule-job-list')
    expect(viewSource).toContain('openJobLogs(row)')
    expect(viewSource).toContain('logDrawerVisible')
    expect(viewSource).toContain('全部日志')
    expect(viewSource).not.toContain('schedule-workbench')
  })

  it('exposes singleton and broadcast execution modes', () => {
    expect(viewSource).toContain('jobForm.executionMode')
    expect(viewSource).toContain('单实例')
    expect(viewSource).toContain('广播')
    expect(viewSource).toContain('executionModeText')
  })
})

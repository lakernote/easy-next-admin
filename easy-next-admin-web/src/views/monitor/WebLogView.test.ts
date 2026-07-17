import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const viewSource = readFileSync(resolve(__dirname, 'WebLogView.vue'), 'utf-8')
const apiSource = readFileSync(resolve(__dirname, '../../features/monitor/api.ts'), 'utf-8')

describe('monitor WebLog view permissions', () => {
  it('exposes log level adjustment only through the dedicated permission', () => {
    expect(viewSource).toContain('PermissionCodes.monitor.weblogLevel')
    expect(viewSource).toContain('openLevelDialog')
    expect(viewSource).toContain('submitLogLevel')
    expect(apiSource).toContain('configureWebLogLevel')
    expect(apiSource).toContain("'/monitor/weblog/level'")
  })

  it('uses the logback file snapshot instead of the request table feed', () => {
    expect(viewSource).toContain('getWebLogFileSnapshot')
    expect(viewSource).toContain('weblog-console')
    expect(viewSource).not.toContain('<el-table')
    expect(apiSource).toContain("'/monitor/weblog/file/snapshot'")
  })
})

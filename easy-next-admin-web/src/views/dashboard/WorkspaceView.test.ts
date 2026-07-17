import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const viewSource = readFileSync(fileURLToPath(new URL('./WorkspaceView.vue', import.meta.url)), 'utf-8')

describe('dashboard workspace enterprise interactions', () => {
  it('keeps personal workflow lists count-aware and directly navigable', () => {
    expect(viewSource).toContain('class="section-actions workspace-flow-actions"')
    expect(viewSource).toContain('activeFlowTargetPath')
    expect(viewSource).toContain('startedTabLabel')
    expect(viewSource).toContain('ccTabLabel')
    expect(viewSource).toContain('class="flow-list-summary"')
    expect(viewSource).toContain('.personal-flow-panel .section-head')
  })

  it('keeps the workbench top area compact for enterprise use', () => {
    expect(viewSource).toContain('class="surface quick-application-strip"')
    expect(viewSource).not.toContain('workspace-header')
    expect(viewSource).not.toContain('summary-strip')
    expect(viewSource).not.toContain('工作总览')
    expect(viewSource).not.toContain('当前账号数据')
  })

  it('lets stacked mobile workbench panels grow instead of clipping their content', () => {
    expect(viewSource).toContain('@media (max-width: 1100px)')
    expect(viewSource).toContain('height: auto;')
    expect(viewSource).toContain('flex: 0 0 auto;')
    expect(viewSource).toContain('overflow: visible;')
    expect(viewSource).toContain('min-height: 220px;')
  })

  it('does not repeat the global page refresh inside common applications', () => {
    expect(viewSource).toContain('quick-application-strip')
    expect(viewSource).not.toContain('class="quick-application-actions"')
    expect(viewSource).not.toContain('@click="loadOverview">刷新</el-button>')
  })

  it('keeps the error retry explicit because it is a recovery action', () => {
    expect(viewSource).toContain('@click="loadOverview">重新加载</el-button>')
  })
})

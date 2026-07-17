import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const viewSource = readFileSync(fileURLToPath(new URL('./WorkflowTaskCenterView.vue', import.meta.url)), 'utf-8')

describe('workflow task center routing', () => {
  it('keeps deep-linked task center tabs in sync with the route query', () => {
    expect(viewSource).toContain('syncActiveTabFromRoute')
    expect(viewSource).toContain("watch(() => route.query.tab, syncActiveTabFromRoute, { immediate: true })")
    expect(viewSource).toContain('const nextTab = resolveWorkflowTaskCenterTab(tab)')
    expect(viewSource).toContain('if (activeTab.value !== nextTab)')
  })

  it('shows enterprise pending task context before actions', () => {
    expect(viewSource).toContain('pending-workbench-toolbar')
    expect(viewSource).toContain('申请人')
    expect(viewSource).toContain('等待时长')
    expect(viewSource).toContain('taskBusinessSummary')
    expect(viewSource).toContain('rowActionGroups')
  })

  it('wires pending task approval from the instance detail drawer', () => {
    expect(viewSource).toContain(':current-user-id="auth.user?.userId"')
    expect(viewSource).toContain(':can-approve-task="canUsePermission(PermissionCodes.workflow.taskApprove)"')
    expect(viewSource).toContain(':can-reject-task="canUsePermission(PermissionCodes.workflow.taskReject)"')
    expect(viewSource).toContain('@task-action="submitDetailTaskAction"')
    expect(viewSource).toContain('submitDetailTaskAction')
    expect(viewSource).toContain('performTaskAction')
  })
})

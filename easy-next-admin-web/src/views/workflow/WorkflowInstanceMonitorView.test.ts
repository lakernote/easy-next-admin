import { readFileSync } from 'node:fs'
import { fileURLToPath, URL } from 'node:url'
import { describe, expect, it } from 'vitest'

const viewSource = readFileSync(
  fileURLToPath(new URL('./WorkflowInstanceMonitorView.vue', import.meta.url)),
  'utf-8'
)

describe('workflow instance monitor view', () => {
  it('exposes an administrator workflow instance surface with enterprise operations', () => {
    expect(viewSource).toContain('流程实例')
    expect(viewSource).toContain('pageWorkflowInstances')
    expect(viewSource).toContain('manage: true')
    expect(viewSource).toContain('运行中流程')
    expect(viewSource).toContain('历史流程')
    expect(viewSource).toContain('scope: query.scope')
    expect(viewSource).not.toContain('流程参数')
    expect(viewSource).not.toContain('查看变量')
    expect(viewSource).not.toContain('查看待办')
    expect(viewSource).not.toContain('流转记录')
    expect(viewSource).toContain('remindWorkflowTask')
    expect(viewSource).toContain('transferWorkflowTask')
    expect(viewSource).toContain('terminateWorkflowInstance')
  })

  it('uses the workflow instance manage permission instead of super admin coupling', () => {
    expect(viewSource).toContain('PermissionCodes.workflow.instanceManage')
    expect(viewSource).toContain('auth.hasAnyPermission([PermissionCodes.workflow.instanceManage])')
    expect(viewSource).not.toContain('auth.isSuperAdmin')
  })

  it('lets the instance table fill the available monitor width', () => {
    expect(viewSource).not.toContain(':fit="false"')
    expect(viewSource).toContain('class="admin-table instance-monitor-table"')
    expect(viewSource).toContain('min-width="300"')
  })

  it('allows the current assignee to approve or reject from the detail drawer', () => {
    expect(viewSource).toContain(':current-user-id="auth.user?.userId"')
    expect(viewSource).toContain(':can-approve-task="canUsePermission(PermissionCodes.workflow.taskApprove)"')
    expect(viewSource).toContain(':can-reject-task="canUsePermission(PermissionCodes.workflow.taskReject)"')
    expect(viewSource).toContain('@task-action="submitDetailTaskAction"')
    expect(viewSource).toContain('approveWorkflowTask')
    expect(viewSource).toContain('rejectWorkflowTask')
  })
})

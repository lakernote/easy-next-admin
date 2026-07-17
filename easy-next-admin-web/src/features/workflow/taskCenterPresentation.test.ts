import { describe, expect, it } from 'vitest'
import {
  formatWorkflowTime,
  pendingTaskMatchesFilter,
  workflowBusinessTypeText,
  workflowInstanceStatusTagType,
  workflowInstanceStatusText,
  workflowTaskActionSubmitText,
  workflowTaskActionTitle,
  workflowTaskApplicantAccount,
  workflowTaskApplicantName,
  workflowTaskBusinessSummary,
  workflowTaskDueHint,
  workflowTaskStatusTagType,
  workflowTaskStatusText,
  workflowTaskUrgencyLevel,
  workflowTaskUrgencyTagType,
  workflowTaskUrgencyText,
  workflowTaskWaitingText,
} from './taskCenterPresentation'
import type { WorkflowAssigneeOption, WorkflowTask } from './types'

const now = new Date('2026-06-22T12:00:00.000Z').getTime()

describe('workflow task center presentation helpers', () => {
  it('classifies pending task urgency with stable waiting text and filter matches', () => {
    const todayTask = pendingTask('2026-06-22T09:30:00.000Z')
    const agingTask = pendingTask('2026-06-22T02:30:00.000Z')
    const overdueTask = pendingTask('2026-06-21T09:00:00.000Z')

    expect(workflowTaskUrgencyLevel(todayTask, now)).toBe('today')
    expect(workflowTaskUrgencyText(todayTask, now)).toBe('今日到达')
    expect(workflowTaskUrgencyTagType(todayTask, now)).toBe('primary')
    expect(workflowTaskDueHint(todayTask, now)).toBe('今日到达')
    expect(workflowTaskWaitingText(todayTask.startedAt, now)).toBe('2小时')

    expect(workflowTaskUrgencyLevel(agingTask, now)).toBe('aging')
    expect(workflowTaskUrgencyText(agingTask, now)).toBe('需关注')
    expect(workflowTaskWaitingText(agingTask.startedAt, now)).toBe('9小时')

    expect(workflowTaskUrgencyLevel(overdueTask, now)).toBe('overdue')
    expect(workflowTaskDueHint(overdueTask, now)).toBe('超过24小时')
    expect(workflowTaskWaitingText(overdueTask.startedAt, now)).toBe('1天3小时')

    expect(pendingTaskMatchesFilter(overdueTask, 'overdue', now)).toBe(true)
    expect(pendingTaskMatchesFilter(overdueTask, 'today', now)).toBe(false)
  })

  it('formats applicant, business and status labels for enterprise workflow rows', () => {
    const assignees: WorkflowAssigneeOption[] = [
      { value: '1001', name: '张三', userName: 'zhangsan' }
    ]
    const task = {
      ...pendingTask('2026-06-22T09:30:00.000Z'),
      instanceInitiatorId: 1001,
      businessType: 'purchase',
      businessId: 'PO-2026-001',
      status: 'APPROVED'
    }

    expect(workflowTaskApplicantName(task, assignees)).toBe('张三')
    expect(workflowTaskApplicantAccount(task, assignees)).toBe('@zhangsan')
    expect(workflowTaskBusinessSummary(task)).toBe('采购 / PO-2026-001')
    expect(workflowBusinessTypeText('repair')).toBe('报修')
    expect(workflowTaskStatusText(task.status)).toBe('已同意')
    expect(workflowTaskStatusTagType(task.status)).toBe('success')
    expect(workflowInstanceStatusText('TERMINATED')).toBe('已终止')
    expect(workflowInstanceStatusTagType('TERMINATED')).toBe('danger')
    expect(formatWorkflowTime('2026-06-22T09:30:00')).toBe('2026-06-22 09:30:00')
  })

  it('uses resubmit copy for submit-node approvals', () => {
    const submitTask = {
      ...pendingTask('2026-06-22T09:30:00.000Z'),
      nodeKey: 'submit'
    }

    expect(workflowTaskActionTitle('approveWithComment', submitTask)).toBe('重新提交')
    expect(workflowTaskActionSubmitText('approveWithComment', submitTask)).toBe('确认提交')
    expect(workflowTaskActionTitle('reject', submitTask)).toBe('驳回任务')
  })
})

function pendingTask(startedAt: string): WorkflowTask {
  return {
    id: 1,
    instanceId: 100,
    instanceTitle: '采购申请',
    businessType: 'purchase',
    businessId: 'PO-2026-001',
    nodeKey: 'approve',
    nodeName: '部门审批',
    status: 'PENDING',
    startedAt
  }
}

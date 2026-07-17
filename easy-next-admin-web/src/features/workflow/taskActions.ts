import { PermissionCodes } from '@/permissions/codes'

export type TaskActionType =
  | 'approve'
  | 'approveWithComment'
  | 'reject'
  | 'transfer'
  | 'delegate'
  | 'return'
  | 'addSign'
  | 'removeSign'
  | 'remind'

export interface WorkflowTaskActionMeta {
  type: TaskActionType
  label: string
  title: string
  submitText: string
  permission: string
  direct: boolean
  danger: boolean
  requiresComment: boolean
  requiresUserTarget: boolean
}

export const workflowTaskActions: readonly WorkflowTaskActionMeta[] = [
  {
    type: 'approve',
    label: '同意',
    title: '同意任务',
    submitText: '确认同意',
    permission: PermissionCodes.workflow.taskApprove,
    direct: true,
    danger: false,
    requiresComment: false,
    requiresUserTarget: false
  },
  {
    type: 'approveWithComment',
    label: '填写意见后同意',
    title: '同意任务',
    submitText: '确认同意',
    permission: PermissionCodes.workflow.taskApprove,
    direct: false,
    danger: false,
    requiresComment: false,
    requiresUserTarget: false
  },
  {
    type: 'reject',
    label: '驳回',
    title: '驳回任务',
    submitText: '确认驳回',
    permission: PermissionCodes.workflow.taskReject,
    direct: false,
    danger: true,
    requiresComment: true,
    requiresUserTarget: false
  },
  {
    type: 'transfer',
    label: '转办',
    title: '转办任务',
    submitText: '确认转办',
    permission: PermissionCodes.workflow.taskTransfer,
    direct: false,
    danger: false,
    requiresComment: true,
    requiresUserTarget: true
  },
  {
    type: 'delegate',
    label: '委派',
    title: '委派任务',
    submitText: '确认委派',
    permission: PermissionCodes.workflow.taskDelegate,
    direct: false,
    danger: false,
    requiresComment: true,
    requiresUserTarget: true
  },
  {
    type: 'return',
    label: '退回',
    title: '退回任务',
    submitText: '确认退回',
    permission: PermissionCodes.workflow.taskReturn,
    direct: false,
    danger: true,
    requiresComment: true,
    requiresUserTarget: false
  },
  {
    type: 'addSign',
    label: '加签',
    title: '加签任务',
    submitText: '确认加签',
    permission: PermissionCodes.workflow.taskAddSign,
    direct: false,
    danger: false,
    requiresComment: true,
    requiresUserTarget: true
  },
  {
    type: 'removeSign',
    label: '减签',
    title: '减签任务',
    submitText: '确认减签',
    permission: PermissionCodes.workflow.taskRemoveSign,
    direct: false,
    danger: false,
    requiresComment: true,
    requiresUserTarget: true
  },
  {
    type: 'remind',
    label: '催办',
    title: '催办任务',
    submitText: '确认催办',
    permission: PermissionCodes.workflow.taskRemind,
    direct: false,
    danger: false,
    requiresComment: false,
    requiresUserTarget: false
  }
]

export function workflowTaskAction(type: TaskActionType): WorkflowTaskActionMeta | undefined {
  return workflowTaskActions.find((action) => action.type === type)
}

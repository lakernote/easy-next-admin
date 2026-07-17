import { workflowTaskAction, type TaskActionType, type WorkflowTaskActionMeta } from './taskActions'
import type { WorkflowAssigneeOption, WorkflowCc, WorkflowTask } from './types'
import type { EntityId } from '@/features/system/types'

export type WorkflowPendingUrgencyFilter = 'all' | 'today' | 'aging' | 'overdue'
export type WorkflowPendingUrgencyLevel = 'normal' | 'today' | 'aging' | 'overdue'

export const workflowTaskCenterApplicationEntries = [
  { title: '请假申请', path: '/workflow/leave' },
  { title: '采购申请', path: '/workflow/purchase' },
  { title: '报修申请', path: '/workflow/repair' }
] as const

export const workflowTaskCenterRowActionGroups: ReadonlyArray<{ label: string; actions: WorkflowTaskActionMeta[] }> = [
  { label: '审批处理', actions: [requireWorkflowTaskAction('reject')] },
  { label: '协同处理', actions: [requireWorkflowTaskAction('transfer'), requireWorkflowTaskAction('delegate'), requireWorkflowTaskAction('remind')] },
  { label: '节点调整', actions: [requireWorkflowTaskAction('return'), requireWorkflowTaskAction('addSign'), requireWorkflowTaskAction('removeSign')] }
]

export function requireWorkflowTaskAction(type: TaskActionType) {
  const action = workflowTaskAction(type)
  if (!action) throw new Error(`Unknown workflow task action: ${type}`)
  return action
}

export function workflowTaskActionTitle(type: TaskActionType, task?: Pick<WorkflowTask, 'nodeKey'>) {
  if (isSubmitNodeApproval(type, task)) return '重新提交'
  return workflowTaskAction(type)?.title || '处理任务'
}

export function workflowTaskActionHelperText(type: TaskActionType, task?: Pick<WorkflowTask, 'nodeKey'>) {
  const helpers: Record<TaskActionType, string> = {
    approve: '同意后，流程会自动进入下一审批节点。',
    approveWithComment: isSubmitNodeApproval(type, task) ? '补充意见后，流程会重新提交到下一审批节点。' : '同意后，流程会自动进入下一审批节点。',
    reject: '驳回会结束本次申请，请写清楚原因。',
    transfer: '转办后，当前待办将交给新的处理人。',
    delegate: '委派用于临时请他人协助处理，并保留委派记录。',
    return: '可退回发起人补充，也可退回上一审批节点。',
    addSign: '加签会在当前节点追加处理人，追加人员处理完后才继续流转。',
    removeSign: '减签用于移除尚未处理的加签待办。',
    remind: '催办只记录提醒，不改变流程状态。'
  }
  return helpers[type]
}

export function workflowTaskActionSubmitText(type: TaskActionType, task?: Pick<WorkflowTask, 'nodeKey'>) {
  if (isSubmitNodeApproval(type, task)) return '确认提交'
  return workflowTaskAction(type)?.submitText || '确认处理'
}

export function workflowActionCommentPlaceholder(required: boolean) {
  return required ? '请填写原因或处理意见' : '可补充处理意见'
}

export function workflowTaskActionButtonType(action?: Pick<WorkflowTaskActionMeta, 'danger'>) {
  return action?.danger ? 'danger' : 'primary'
}

function isSubmitNodeApproval(type: TaskActionType, task?: Pick<WorkflowTask, 'nodeKey'>) {
  return task?.nodeKey === 'submit' && (type === 'approve' || type === 'approveWithComment')
}

export function workflowInstanceTitle(row: Pick<WorkflowTask | WorkflowCc, 'instanceId' | 'instanceTitle'>) {
  return row.instanceTitle || `流程 ${row.instanceId}`
}

export function workflowInstanceMeta(row: Pick<WorkflowTask | WorkflowCc, 'businessType' | 'businessId'>) {
  const segments = [row.businessType ? workflowBusinessTypeText(row.businessType) : '', row.businessId].filter(Boolean)
  return segments.length ? segments.join(' / ') : '流程'
}

export function workflowTaskBusinessSummary(row: Pick<WorkflowTask, 'businessType' | 'businessId'>) {
  const typeText = workflowBusinessTypeText(row.businessType || 'workflow')
  return row.businessId ? `${typeText} / ${row.businessId}` : typeText
}

export function workflowTaskApplicantUserId(row: Pick<WorkflowTask, 'instanceInitiatorId' | 'createdBy'>) {
  return row.instanceInitiatorId || row.createdBy
}

export function workflowTaskApplicantName(
  row: Pick<WorkflowTask, 'instanceInitiatorId' | 'createdBy'>,
  assignees: readonly WorkflowAssigneeOption[]
) {
  const applicantId = workflowTaskApplicantUserId(row)
  const applicant = workflowAssigneeOption(applicantId, assignees)
  if (applicant) return applicant.name
  return applicantId ? `用户 ${applicantId}` : '未记录'
}

export function workflowTaskApplicantAccount(
  row: Pick<WorkflowTask, 'instanceInitiatorId' | 'createdBy'>,
  assignees: readonly WorkflowAssigneeOption[]
) {
  const applicant = workflowAssigneeOption(workflowTaskApplicantUserId(row), assignees)
  return applicant?.userName ? `@${applicant.userName}` : ''
}

function workflowAssigneeOption(userId: EntityId | undefined, assignees: readonly WorkflowAssigneeOption[]) {
  if (!userId) return undefined
  return assignees.find((item) => String(item.value) === String(userId))
}

export function workflowTaskNodeFlowHint(row: Pick<WorkflowTask, 'nodeKey'>) {
  if (row.nodeKey === 'submit') return '等待补充后重新提交'
  return '待你处理'
}

export function pendingTaskMatchesFilter(
  task: Pick<WorkflowTask, 'startedAt'>,
  filter: WorkflowPendingUrgencyFilter,
  now: number | Date = Date.now()
) {
  if (filter === 'all') return true
  return workflowTaskUrgencyLevel(task, now) === filter
}

export function workflowTaskUrgencyLevel(
  row: Pick<WorkflowTask, 'startedAt'>,
  now: number | Date = Date.now()
): WorkflowPendingUrgencyLevel {
  const hours = hoursSince(row.startedAt, now)
  if (hours === undefined) return 'normal'
  if (hours >= 24) return 'overdue'
  if (hours >= 8) return 'aging'
  if (isSameLocalDate(row.startedAt, now)) return 'today'
  return 'normal'
}

export function workflowTaskUrgencyText(row: Pick<WorkflowTask, 'startedAt'>, now: number | Date = Date.now()) {
  const level = workflowTaskUrgencyLevel(row, now)
  if (level === 'overdue') return '已超时'
  if (level === 'aging') return '需关注'
  if (level === 'today') return '今日到达'
  return '正常'
}

export function workflowTaskUrgencyTagType(row: Pick<WorkflowTask, 'startedAt'>, now: number | Date = Date.now()) {
  const level = workflowTaskUrgencyLevel(row, now)
  if (level === 'overdue') return 'danger'
  if (level === 'aging') return 'warning'
  if (level === 'today') return 'primary'
  return 'info'
}

export function workflowTaskDueHint(row: Pick<WorkflowTask, 'startedAt'>, now: number | Date = Date.now()) {
  const level = workflowTaskUrgencyLevel(row, now)
  if (level === 'overdue') return '超过24小时'
  if (level === 'aging') return '超过8小时'
  if (level === 'today') return '今日到达'
  return '正常流转'
}

export function workflowTaskWaitingText(value?: string, now: number | Date = Date.now()) {
  const hours = hoursSince(value, now)
  if (hours === undefined) return '-'
  if (hours < 1) return '1小时内'
  if (hours < 24) return `${Math.floor(hours)}小时`
  const days = Math.floor(hours / 24)
  const remainHours = Math.floor(hours % 24)
  return remainHours > 0 ? `${days}天${remainHours}小时` : `${days}天`
}

function hoursSince(value: string | undefined, now: number | Date) {
  const time = parseWorkflowTime(value)
  if (!time) return undefined
  const nowTime = typeof now === 'number' ? now : now.getTime()
  return Math.max(0, (nowTime - time.getTime()) / 3_600_000)
}

function isSameLocalDate(value: string | undefined, now: number | Date) {
  const time = parseWorkflowTime(value)
  if (!time) return false
  const nowTime = new Date(now)
  return time.getFullYear() === nowTime.getFullYear()
    && time.getMonth() === nowTime.getMonth()
    && time.getDate() === nowTime.getDate()
}

function parseWorkflowTime(value?: string) {
  if (!value) return undefined
  const normalized = value.includes('T') ? value : value.replace(' ', 'T')
  const time = new Date(normalized)
  return Number.isNaN(time.getTime()) ? undefined : time
}

export function workflowBusinessTypeText(type: string) {
  const textMap: Record<string, string> = {
    LEAVE: '请假',
    leave: '请假',
    expense: '报销',
    purchase: '采购',
    repair: '报修',
    workflow: '流程'
  }
  return textMap[type] || type
}

export function formatWorkflowTotal(total?: number) {
  return total ?? 0
}

export function formatWorkflowTime(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}

export function workflowTaskStatusText(status: string) {
  const textMap: Record<string, string> = {
    PENDING: '待处理',
    APPROVED: '已同意',
    REJECTED: '已驳回',
    TRANSFERRED: '已转办',
    DELEGATED: '已委派',
    CANCELED: '已取消'
  }
  return textMap[status] || status
}

export function workflowTaskStatusTagType(status: string) {
  if (status === 'PENDING') return 'warning'
  if (status === 'APPROVED') return 'success'
  if (status === 'REJECTED') return 'danger'
  return 'info'
}

export function workflowInstanceStatusText(status: string) {
  const textMap: Record<string, string> = {
    RUNNING: '审批中',
    APPROVED: '已通过',
    REJECTED: '已驳回',
    REVOKED: '已撤回',
    TERMINATED: '已终止'
  }
  return textMap[status] || status
}

export function workflowInstanceStatusTagType(status: string) {
  if (status === 'RUNNING') return 'warning'
  if (status === 'APPROVED') return 'success'
  if (status === 'REJECTED' || status === 'TERMINATED') return 'danger'
  return 'info'
}

import request from '@/api/request'
import { toData, toPageResult, type ApiResponse, type PageApiResponse, type PageQuery } from '@/api/types'
import type {
  WorkflowCc,
  WorkflowInstanceDetail,
  WorkflowTask,
  WorkflowTaskActionPayload,
  WorkflowTaskCenterSummary
} from './types'

const taskEndpoints = {
  tasks: '/workflow/tasks',
  taskCenterSummary: '/workflow/task-center/summary',
  taskAction: (id: WorkflowTask['id'], action: string) => `/workflow/tasks/${id}/${action}`,
  cc: '/workflow/cc',
  ccRead: (id: WorkflowCc['id']) => `/workflow/cc/${id}/read`
}

// 任务中心聚合待办、已办和抄送，因此任务与抄送读取接口放在同一文件。
export async function pageWorkflowTasks(
  query: PageQuery & {
    status?: string
    statuses?: readonly string[]
    instanceId?: WorkflowTask['instanceId']
    assigneeId?: WorkflowTask['assigneeId']
    mine?: boolean
  } = {}
) {
  const response = await request.get<PageApiResponse<WorkflowTask>>(taskEndpoints.tasks, {
    params: {
      page: query.page || 1,
      limit: query.limit || 10,
      status: query.status,
      statuses: query.statuses?.join(','),
      instanceId: query.instanceId,
      assigneeId: query.assigneeId,
      mine: query.mine
    }
  })
  return toPageResult(response.data)
}

export async function getWorkflowTaskCenterSummary() {
  const response = await request.get<ApiResponse<WorkflowTaskCenterSummary>>(taskEndpoints.taskCenterSummary)
  return toData(response.data)
}

export async function approveWorkflowTask(id: WorkflowTask['id'], payload: WorkflowTaskActionPayload = {}) {
  return submitWorkflowTaskAction(id, 'approve', payload)
}

export async function rejectWorkflowTask(id: WorkflowTask['id'], payload: WorkflowTaskActionPayload = {}) {
  return submitWorkflowTaskAction(id, 'reject', payload)
}

export async function transferWorkflowTask(id: WorkflowTask['id'], payload: WorkflowTaskActionPayload) {
  return submitWorkflowTaskAction(id, 'transfer', payload)
}

export async function delegateWorkflowTask(id: WorkflowTask['id'], payload: WorkflowTaskActionPayload) {
  return submitWorkflowTaskAction(id, 'delegate', payload)
}

export async function returnWorkflowTask(id: WorkflowTask['id'], payload: WorkflowTaskActionPayload) {
  return submitWorkflowTaskAction(id, 'return', payload)
}

export async function remindWorkflowTask(id: WorkflowTask['id'], payload: WorkflowTaskActionPayload = {}) {
  return submitWorkflowTaskAction(id, 'remind', payload)
}

export async function addSignWorkflowTask(id: WorkflowTask['id'], payload: WorkflowTaskActionPayload) {
  return submitWorkflowTaskAction(id, 'add-sign', payload)
}

export async function removeSignWorkflowTask(id: WorkflowTask['id'], payload: WorkflowTaskActionPayload) {
  return submitWorkflowTaskAction(id, 'remove-sign', payload)
}

export async function pageWorkflowCc(query: PageQuery & { readStatus?: number; mine?: boolean } = {}) {
  const response = await request.get<PageApiResponse<WorkflowCc>>(taskEndpoints.cc, {
    params: {
      page: query.page || 1,
      limit: query.limit || 10,
      readStatus: query.readStatus,
      mine: query.mine
    }
  })
  return toPageResult(response.data)
}

export async function markWorkflowCcRead(id: WorkflowCc['id'], historic = false) {
  const response = await request.put<ApiResponse<boolean>>(taskEndpoints.ccRead(id), null, {
    params: { historic }
  })
  return toData(response.data)
}

async function submitWorkflowTaskAction(
  id: WorkflowTask['id'],
  action: string,
  payload: WorkflowTaskActionPayload
) {
  const response = await request.put<ApiResponse<WorkflowInstanceDetail>>(taskEndpoints.taskAction(id, action), payload)
  return toData(response.data)
}

import request from '@/api/request'
import { toData, toPageResult, type ApiResponse, type PageApiResponse, type PageQuery } from '@/api/types'
import type {
  LeaveApplyPayload,
  LeaveRequest,
  PurchaseApplyPayload,
  PurchaseRequest,
  StartWorkflowPayload,
  RepairApplyPayload,
  RepairAttachment,
  RepairRequest,
  WorkflowCc,
  WorkflowAssigneeOption,
  WorkflowDefinition,
  WorkflowInstance,
  WorkflowInstanceActionPayload,
  WorkflowInstanceDetail,
  WorkflowTask,
  WorkflowTaskActionPayload,
  WorkflowTaskCenterSummary
} from './types'

export type WorkflowInstanceScope = 'RUNTIME' | 'HISTORY'

export async function pageWorkflowDefinitions(query: PageQuery & { keyword?: string; status?: string } = {}) {
  const response = await request.get<PageApiResponse<WorkflowDefinition>>('/workflow/definitions', {
    params: {
      page: query.page || 1,
      limit: query.limit || 10,
      keyword: query.keyword,
      status: query.status
    }
  })
  return toPageResult(response.data)
}

export async function listWorkflowAssignees() {
  const response = await request.get<ApiResponse<WorkflowAssigneeOption[]>>('/system/users/assignees')
  return toData(response.data)
}

export async function getWorkflowDefinition(id: WorkflowDefinition['id']) {
  const response = await request.get<ApiResponse<WorkflowDefinition>>(`/workflow/definitions/${id}`)
  return toData(response.data)
}

export async function saveWorkflowDefinition(definition: Partial<WorkflowDefinition>) {
  const response = await request.post<ApiResponse<WorkflowDefinition>>('/workflow/definitions', definition)
  return toData(response.data)
}

export async function updateWorkflowDefinitionStatus(id: WorkflowDefinition['id'], status: string) {
  const response = await request.put<ApiResponse<boolean>>(`/workflow/definitions/${id}/status`, null, {
    params: { status }
  })
  return toData(response.data)
}

export async function publishWorkflowDefinition(id: WorkflowDefinition['id']) {
  const response = await request.put<ApiResponse<WorkflowDefinition>>(`/workflow/definitions/${id}/publish`)
  return toData(response.data)
}

export async function deleteWorkflowDefinition(id: WorkflowDefinition['id']) {
  const response = await request.delete<ApiResponse<boolean>>(`/workflow/definitions/${id}`)
  return toData(response.data)
}

export async function pageWorkflowTasks(
  query: PageQuery & { status?: string; statuses?: readonly string[]; instanceId?: WorkflowTask['instanceId']; assigneeId?: WorkflowTask['assigneeId']; mine?: boolean } = {}
) {
  const response = await request.get<PageApiResponse<WorkflowTask>>('/workflow/tasks', {
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
  const response = await request.get<ApiResponse<WorkflowTaskCenterSummary>>('/workflow/task-center/summary')
  return toData(response.data)
}

export async function pageWorkflowInstances(query: PageQuery & { scope: WorkflowInstanceScope; status?: string; businessType?: string; keyword?: string; mine?: boolean; manage?: boolean }) {
  const response = await request.get<PageApiResponse<WorkflowInstance>>('/workflow/instances', {
    params: {
      page: query.page || 1,
      limit: query.limit || 10,
      status: query.status,
      businessType: query.businessType,
      keyword: query.keyword,
      mine: query.mine,
      manage: query.manage,
      scope: query.scope
    }
  })
  return toPageResult(response.data)
}

export async function startWorkflowInstance(payload: StartWorkflowPayload) {
  const response = await request.post<ApiResponse<WorkflowInstanceDetail>>('/workflow/instances', payload)
  return toData(response.data)
}

export async function getWorkflowInstance(id: WorkflowInstance['id']) {
  const response = await request.get<ApiResponse<WorkflowInstanceDetail>>(`/workflow/instances/${id}`)
  return toData(response.data)
}

export async function revokeWorkflowInstance(id: WorkflowInstance['id'], payload: WorkflowInstanceActionPayload = {}) {
  const response = await request.put<ApiResponse<WorkflowInstanceDetail>>(`/workflow/instances/${id}/revoke`, payload)
  return toData(response.data)
}

export async function terminateWorkflowInstance(id: WorkflowInstance['id'], payload: WorkflowInstanceActionPayload = {}) {
  const response = await request.put<ApiResponse<WorkflowInstanceDetail>>(`/workflow/instances/${id}/terminate`, payload)
  return toData(response.data)
}

export async function approveWorkflowTask(id: WorkflowTask['id'], payload: WorkflowTaskActionPayload = {}) {
  const response = await request.put<ApiResponse<WorkflowInstanceDetail>>(`/workflow/tasks/${id}/approve`, payload)
  return toData(response.data)
}

export async function rejectWorkflowTask(id: WorkflowTask['id'], payload: WorkflowTaskActionPayload = {}) {
  const response = await request.put<ApiResponse<WorkflowInstanceDetail>>(`/workflow/tasks/${id}/reject`, payload)
  return toData(response.data)
}

export async function transferWorkflowTask(id: WorkflowTask['id'], payload: WorkflowTaskActionPayload) {
  const response = await request.put<ApiResponse<WorkflowInstanceDetail>>(`/workflow/tasks/${id}/transfer`, payload)
  return toData(response.data)
}

export async function delegateWorkflowTask(id: WorkflowTask['id'], payload: WorkflowTaskActionPayload) {
  const response = await request.put<ApiResponse<WorkflowInstanceDetail>>(`/workflow/tasks/${id}/delegate`, payload)
  return toData(response.data)
}

export async function returnWorkflowTask(id: WorkflowTask['id'], payload: WorkflowTaskActionPayload) {
  const response = await request.put<ApiResponse<WorkflowInstanceDetail>>(`/workflow/tasks/${id}/return`, payload)
  return toData(response.data)
}

export async function remindWorkflowTask(id: WorkflowTask['id'], payload: WorkflowTaskActionPayload = {}) {
  const response = await request.put<ApiResponse<WorkflowInstanceDetail>>(`/workflow/tasks/${id}/remind`, payload)
  return toData(response.data)
}

export async function addSignWorkflowTask(id: WorkflowTask['id'], payload: WorkflowTaskActionPayload) {
  const response = await request.put<ApiResponse<WorkflowInstanceDetail>>(`/workflow/tasks/${id}/add-sign`, payload)
  return toData(response.data)
}

export async function removeSignWorkflowTask(id: WorkflowTask['id'], payload: WorkflowTaskActionPayload) {
  const response = await request.put<ApiResponse<WorkflowInstanceDetail>>(`/workflow/tasks/${id}/remove-sign`, payload)
  return toData(response.data)
}

export async function pageWorkflowCc(query: PageQuery & { readStatus?: number; mine?: boolean } = {}) {
  const response = await request.get<PageApiResponse<WorkflowCc>>('/workflow/cc', {
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
  const response = await request.put<ApiResponse<boolean>>(`/workflow/cc/${id}/read`, null, {
    params: { historic }
  })
  return toData(response.data)
}

export async function applyLeaveRequest(payload: LeaveApplyPayload) {
  const response = await request.post<ApiResponse<LeaveRequest>>('/workflow/leave/requests', payload)
  return toData(response.data)
}

export async function applyPurchaseRequest(payload: PurchaseApplyPayload) {
  const response = await request.post<ApiResponse<PurchaseRequest>>('/workflow/purchase/requests', payload)
  return toData(response.data)
}

export async function applyRepairRequest(payload: RepairApplyPayload) {
  const response = await request.post<ApiResponse<RepairRequest>>('/workflow/repair/requests', payload)
  return toData(response.data)
}

export async function uploadRepairAttachment(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  const response = await request.post<ApiResponse<RepairAttachment>>('/workflow/repair/requests/attachments', formData)
  return toData(response.data)
}

export async function downloadRepairAttachment(fileId: RepairAttachment['fileId']) {
  const response = await request.get<Blob>(`/workflow/repair/requests/attachments/${fileId}`, {
    responseType: 'blob'
  })
  return response.data
}

export async function getRepairRequestByWorkflowInstance(instanceId: RepairRequest['workflowInstanceId']) {
  const response = await request.get<ApiResponse<RepairRequest>>(`/workflow/repair/requests/by-workflow-instance/${instanceId}`)
  return toData(response.data)
}

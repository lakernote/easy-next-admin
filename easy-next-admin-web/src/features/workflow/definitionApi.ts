import request from '@/api/request'
import { toData, toPageResult, type ApiResponse, type PageApiResponse, type PageQuery } from '@/api/types'
import type { WorkflowAssigneeOption, WorkflowDefinition } from './types'

const definitionEndpoints = {
  definitions: '/workflow/definitions',
  assignees: '/system/users/assignees',
  detail: (id: WorkflowDefinition['id']) => `/workflow/definitions/${id}`,
  status: (id: WorkflowDefinition['id']) => `/workflow/definitions/${id}/status`,
  publish: (id: WorkflowDefinition['id']) => `/workflow/definitions/${id}/publish`
}

// 流程配置页使用的接口：定义维护和节点处理人候选项。
export async function pageWorkflowDefinitions(query: PageQuery & { keyword?: string; status?: string } = {}) {
  const response = await request.get<PageApiResponse<WorkflowDefinition>>(definitionEndpoints.definitions, {
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
  const response = await request.get<ApiResponse<WorkflowAssigneeOption[]>>(definitionEndpoints.assignees)
  return toData(response.data)
}

export async function getWorkflowDefinition(id: WorkflowDefinition['id']) {
  const response = await request.get<ApiResponse<WorkflowDefinition>>(definitionEndpoints.detail(id))
  return toData(response.data)
}

export async function saveWorkflowDefinition(definition: Partial<WorkflowDefinition>) {
  const response = await request.post<ApiResponse<WorkflowDefinition>>(definitionEndpoints.definitions, definition)
  return toData(response.data)
}

export async function updateWorkflowDefinitionStatus(id: WorkflowDefinition['id'], status: string) {
  const response = await request.put<ApiResponse<boolean>>(definitionEndpoints.status(id), null, {
    params: { status }
  })
  return toData(response.data)
}

export async function publishWorkflowDefinition(id: WorkflowDefinition['id']) {
  const response = await request.put<ApiResponse<WorkflowDefinition>>(definitionEndpoints.publish(id))
  return toData(response.data)
}

export async function deleteWorkflowDefinition(id: WorkflowDefinition['id']) {
  const response = await request.delete<ApiResponse<boolean>>(definitionEndpoints.detail(id))
  return toData(response.data)
}

import request from '@/api/request'
import { toData, toPageResult, type ApiResponse, type PageApiResponse, type PageQuery } from '@/api/types'
import type {
  StartWorkflowPayload,
  WorkflowInstance,
  WorkflowInstanceActionPayload,
  WorkflowInstanceDetail
} from './types'

export type WorkflowInstanceScope = 'RUNTIME' | 'HISTORY'

const instanceEndpoints = {
  instances: '/workflow/instances',
  detail: (id: WorkflowInstance['id']) => `/workflow/instances/${id}`,
  revoke: (id: WorkflowInstance['id']) => `/workflow/instances/${id}/revoke`,
  terminate: (id: WorkflowInstance['id']) => `/workflow/instances/${id}/terminate`
}

// 实例接口负责流程运行态和历史态查询，scope 由页面显式传入。
export async function pageWorkflowInstances(
  query: PageQuery & {
    scope: WorkflowInstanceScope
    status?: string
    businessType?: string
    keyword?: string
    mine?: boolean
    manage?: boolean
  }
) {
  const response = await request.get<PageApiResponse<WorkflowInstance>>(instanceEndpoints.instances, {
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
  const response = await request.post<ApiResponse<WorkflowInstanceDetail>>(instanceEndpoints.instances, payload)
  return toData(response.data)
}

export async function getWorkflowInstance(id: WorkflowInstance['id']) {
  const response = await request.get<ApiResponse<WorkflowInstanceDetail>>(instanceEndpoints.detail(id))
  return toData(response.data)
}

export async function revokeWorkflowInstance(id: WorkflowInstance['id'], payload: WorkflowInstanceActionPayload = {}) {
  const response = await request.put<ApiResponse<WorkflowInstanceDetail>>(instanceEndpoints.revoke(id), payload)
  return toData(response.data)
}

export async function terminateWorkflowInstance(id: WorkflowInstance['id'], payload: WorkflowInstanceActionPayload = {}) {
  const response = await request.put<ApiResponse<WorkflowInstanceDetail>>(instanceEndpoints.terminate(id), payload)
  return toData(response.data)
}

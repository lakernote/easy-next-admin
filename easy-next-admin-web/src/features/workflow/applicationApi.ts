import request from '@/api/request'
import { toData, type ApiResponse } from '@/api/types'
import type { LeaveApplyPayload, LeaveRequest, PurchaseApplyPayload, PurchaseRequest } from './types'

const applicationEndpoints = {
  leaveRequests: '/workflow/leave/requests',
  purchaseRequests: '/workflow/purchase/requests'
}

// 业务申请页只提交业务表单，后端负责创建对应流程实例。
export async function applyLeaveRequest(payload: LeaveApplyPayload) {
  const response = await request.post<ApiResponse<LeaveRequest>>(applicationEndpoints.leaveRequests, payload)
  return toData(response.data)
}

export async function applyPurchaseRequest(payload: PurchaseApplyPayload) {
  const response = await request.post<ApiResponse<PurchaseRequest>>(applicationEndpoints.purchaseRequests, payload)
  return toData(response.data)
}

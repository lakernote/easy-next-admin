import request from '@/api/request'
import { toData, type ApiResponse } from '@/api/types'
import type { EntityId, SystemRole, SystemUserOption } from './types'

const userOptionEndpoints = {
  assignableRoles: '/system/users/assignable-roles',
  assignees: '/system/users/assignees'
}

// 用户候选项会被用户、组织和流程页面复用，单独拆出避免循环依赖。
export async function listAssignableRoles(userId?: EntityId) {
  const response = await request.get<ApiResponse<SystemRole[]>>(userOptionEndpoints.assignableRoles, {
    params: { userId }
  })
  return toData(response.data)
}

export async function listUserAssignees(userId?: EntityId) {
  const response = await request.get<ApiResponse<SystemUserOption[]>>(userOptionEndpoints.assignees, {
    params: { userId }
  })
  return toData(response.data)
}

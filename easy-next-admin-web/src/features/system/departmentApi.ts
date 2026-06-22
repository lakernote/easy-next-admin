import request from '@/api/request'
import { toData, type ApiResponse } from '@/api/types'
import type { EntityId, SystemDepartment } from './types'
import { toDepartmentTree } from './tree'

const departmentEndpoints = {
  departments: '/system/departments',
  tree: '/system/departments/tree',
  batchDelete: (departmentIds: EntityId[]) => `/system/departments/batch/${departmentIds.join(',')}`
}

// 组织架构保存时只提交后端需要的字段，避免把前端树节点临时字段带回服务端。
export async function treeDepartments() {
  const response = await request.get<ApiResponse<SystemDepartment[]>>(departmentEndpoints.tree)
  return toDepartmentTree(toData(response.data))
}

export async function saveDepartment(data: Partial<SystemDepartment>) {
  const response = await request.post<ApiResponse<boolean>>(departmentEndpoints.departments, toDepartmentPayload(data))
  return toData(response.data)
}

export async function deleteDepartment(deptId: EntityId) {
  const response = await request.delete<ApiResponse<boolean>>(`${departmentEndpoints.departments}/${deptId}`)
  return toData(response.data)
}

export async function batchDeleteDepartments(departmentIds: EntityId[]) {
  const response = await request.delete<ApiResponse<boolean>>(departmentEndpoints.batchDelete(departmentIds))
  return toData(response.data)
}

function toDepartmentPayload(data: Partial<SystemDepartment>) {
  return {
    deptId: data.deptId,
    deptName: optionalText(data.deptName),
    address: optionalText(data.address),
    pid: data.pid,
    leaderUserId: data.leaderUserId || null,
    status: data.status ?? true,
    sort: data.sort ?? 99
  }
}

function optionalText(value?: string | null) {
  if (typeof value !== 'string') return null
  const trimmed = value.trim()
  return trimmed ? trimmed : null
}

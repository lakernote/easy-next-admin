import request from '@/api/request'
import { toData, toPageResult, type ApiResponse, type PageApiResponse } from '@/api/types'
import type {
  EntityId,
  SystemMenu,
  SystemRole,
  SystemRoleAuthorization,
  SystemRolePageQuery,
  SystemRolePermission
} from './types'
import { toSystemMenuTree } from './tree'

const roleEndpoints = {
  roles: '/system/roles',
  permissions: (roleId: EntityId) => `/system/roles/${roleId}/permissions`,
  permissionResources: '/system/roles/permission-resources'
}

// 角色授权同时包含菜单权限、按钮权限和数据范围，入口集中在角色 API。
export async function pageRoles(query: SystemRolePageQuery) {
  const response = await request.get<PageApiResponse<SystemRole>>(roleEndpoints.roles, {
    params: {
      current: query.page,
      size: query.limit,
      keyword: query.keyword,
      enable: query.enable
    }
  })
  return toPageResult(response.data)
}

export async function saveRole(data: Partial<SystemRole>) {
  const response = await request.post<ApiResponse<SystemRole>>(roleEndpoints.roles, data)
  return toData(response.data)
}

export async function deleteRole(roleId: EntityId) {
  const response = await request.delete<ApiResponse<boolean>>(`${roleEndpoints.roles}/${roleId}`)
  return toData(response.data)
}

export async function getRolePermissions(roleId: EntityId) {
  const response = await request.get<ApiResponse<SystemRolePermission>>(roleEndpoints.permissions(roleId))
  return toData(response.data)
}

export async function listRolePermissionResources() {
  const response = await request.get<ApiResponse<SystemMenu[]>>(roleEndpoints.permissionResources)
  return toSystemMenuTree(toData(response.data))
}

export async function saveRolePermissions(payload: SystemRolePermission) {
  const response = await request.put<ApiResponse<boolean>>(roleEndpoints.permissions(payload.roleId), payload)
  return toData(response.data)
}

export async function saveRoleAuthorization(payload: SystemRoleAuthorization) {
  return saveRolePermissions({
    roleId: payload.roleId,
    dataScope: payload.dataScope,
    deptIds: payload.deptIds,
    permissionCodes: payload.permissionCodes
  })
}

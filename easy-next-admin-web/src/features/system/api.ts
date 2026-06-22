import request from '@/api/request'
import { toData, toPageResult, type ApiResponse, type PageApiResponse } from '@/api/types'
import { resolveDownloadFileName } from '@/utils/download'
import type {
  EntityId,
  SystemDepartment,
  SystemMenu,
  SystemMenuResourcePayload,
  SystemRole,
  SystemRoleAuthorization,
  SystemRolePageQuery,
  SystemRolePermission,
  SystemUser,
  SystemUserOption,
  SystemUserPageQuery,
  UserImportResult
} from './types'

export { resolveDownloadFileName } from '@/utils/download'

const systemEndpoints = {
  users: '/system/users',
  userBatchDelete: (userIds: EntityId[]) => `/system/users/batch/${userIds.join(',')}`,
  userImportTemplate: '/system/users/import-template',
  userImport: '/system/users/import',
  userExport: '/system/users/export',
  userResetPassword: (userId: EntityId) => `/system/users/resetPwd/${userId}`,
  userAssignableRoles: '/system/users/assignable-roles',
  userAssignees: '/system/users/assignees',
  roles: '/system/roles',
  rolePermissions: (roleId: EntityId) => `/system/roles/${roleId}/permissions`,
  rolePermissionResources: '/system/roles/permission-resources',
  menus: '/system/menus',
  menuList: '/system/menus/list',
  departments: '/system/departments',
  departmentBatchDelete: (departmentIds: EntityId[]) => `/system/departments/batch/${departmentIds.join(',')}`,
  departmentTree: '/system/departments/tree'
}

export async function pageUsers(query: SystemUserPageQuery) {
  const response = await request.get<PageApiResponse<SystemUser>>(systemEndpoints.users, {
    params: {
      page: query.page,
      limit: query.limit,
      keyWord: query.keyWord,
      deptId: query.deptId,
      enable: query.enable
    }
  })
  return toPageResult(response.data)
}

export async function getUser(userId: EntityId) {
  const response = await request.get<ApiResponse<SystemUser>>(`${systemEndpoints.users}/${userId}`)
  return toData(response.data)
}

export async function saveUser(data: Partial<SystemUser>) {
  const payload = toUserPayload(data)
  const response = data.userId
    ? await request.put<ApiResponse<SystemUser>>(`${systemEndpoints.users}/${data.userId}`, payload)
    : await request.post<ApiResponse<SystemUser>>(systemEndpoints.users, payload)
  return toData(response.data)
}

export async function downloadUserImportTemplate() {
  const response = await request.get<Blob>(systemEndpoints.userImportTemplate, { responseType: 'blob' })
  return {
    blob: response.data,
    fileName: resolveDownloadFileName(response.headers['content-disposition']) || '用户导入模板.csv'
  }
}

export async function importUsers(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  const response = await request.post<ApiResponse<UserImportResult>>(systemEndpoints.userImport, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  return toData(response.data)
}

export async function exportUsers(query: Partial<SystemUserPageQuery>) {
  const response = await request.get<Blob>(systemEndpoints.userExport, {
    params: {
      keyWord: query.keyWord,
      deptId: query.deptId,
      enable: query.enable
    },
    responseType: 'blob'
  })
  return {
    blob: response.data,
    fileName: resolveDownloadFileName(response.headers['content-disposition']) || '用户导出.csv'
  }
}

export async function switchUserStatus(userId: EntityId, enable: 0 | 1) {
  const response = await request.put<ApiResponse<boolean>>(`${systemEndpoints.users}/switch`, { userId, enable })
  return toData(response.data)
}

export async function resetUserPassword(userId: EntityId) {
  const response = await request.put<ApiResponse<boolean>>(systemEndpoints.userResetPassword(userId))
  return toData(response.data)
}

export async function deleteUser(userId: EntityId) {
  const response = await request.delete<ApiResponse<boolean>>(`${systemEndpoints.users}/${userId}`)
  return toData(response.data)
}

export async function batchDeleteUsers(userIds: EntityId[]) {
  const response = await request.delete<ApiResponse<boolean>>(systemEndpoints.userBatchDelete(userIds))
  return toData(response.data)
}

export async function pageRoles(query: SystemRolePageQuery) {
  const response = await request.get<PageApiResponse<SystemRole>>(systemEndpoints.roles, {
    params: {
      current: query.page,
      size: query.limit,
      keyword: query.keyword,
      enable: query.enable
    }
  })
  return toPageResult(response.data)
}

export async function listAssignableRoles(userId?: EntityId) {
  const response = await request.get<ApiResponse<SystemRole[]>>(systemEndpoints.userAssignableRoles, {
    params: { userId }
  })
  return toData(response.data)
}

export async function listUserAssignees(userId?: EntityId) {
  const response = await request.get<ApiResponse<SystemUserOption[]>>(systemEndpoints.userAssignees, {
    params: { userId }
  })
  return toData(response.data)
}

export async function saveRole(data: Partial<SystemRole>) {
  const response = await request.post<ApiResponse<SystemRole>>(systemEndpoints.roles, data)
  return toData(response.data)
}

export async function deleteRole(roleId: EntityId) {
  const response = await request.delete<ApiResponse<boolean>>(`${systemEndpoints.roles}/${roleId}`)
  return toData(response.data)
}

export async function getRolePermissions(roleId: EntityId) {
  const response = await request.get<ApiResponse<SystemRolePermission>>(systemEndpoints.rolePermissions(roleId))
  return toData(response.data)
}

export async function listRolePermissionResources() {
  const response = await request.get<ApiResponse<SystemMenu[]>>(systemEndpoints.rolePermissionResources)
  return toMenuTree(toData(response.data))
}

export async function saveRolePermissions(payload: SystemRolePermission) {
  const response = await request.put<ApiResponse<boolean>>(systemEndpoints.rolePermissions(payload.roleId), payload)
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

export async function listSystemMenus() {
  const response = await request.get<ApiResponse<SystemMenu[]>>(systemEndpoints.menuList)
  return toMenuTree(toData(response.data))
}

export async function saveSystemMenuResource(data: SystemMenuResourcePayload) {
  const response = await request.post<ApiResponse<SystemMenu>>(systemEndpoints.menus, data)
  return toData(response.data)
}

export async function deleteSystemMenuResource(menuId: EntityId) {
  const response = await request.delete<ApiResponse<boolean>>(`${systemEndpoints.menus}/${menuId}`)
  return toData(response.data)
}

export async function treeDepartments() {
  const response = await request.get<ApiResponse<SystemDepartment[]>>(systemEndpoints.departmentTree)
  return toDepartmentTree(toData(response.data))
}

export async function saveDepartment(data: Partial<SystemDepartment>) {
  const response = await request.post<ApiResponse<boolean>>(systemEndpoints.departments, toDepartmentPayload(data))
  return toData(response.data)
}

export async function deleteDepartment(deptId: EntityId) {
  const response = await request.delete<ApiResponse<boolean>>(`${systemEndpoints.departments}/${deptId}`)
  return toData(response.data)
}

export async function batchDeleteDepartments(departmentIds: EntityId[]) {
  const response = await request.delete<ApiResponse<boolean>>(systemEndpoints.departmentBatchDelete(departmentIds))
  return toData(response.data)
}

function toUserPayload(data: Partial<SystemUser>) {
  const roleIds = normalizeRoleIds(data.roleIds)
  const nickName = optionalText(data.nickName)
  const payload: Record<string, unknown> = {
    userId: data.userId,
    userName: optionalText(data.userName),
    nickName,
    realName: optionalText(data.realName) || nickName,
    employeeNo: optionalText(data.employeeNo),
    positionName: optionalText(data.positionName),
    deptId: data.deptId,
    managerUserId: data.managerUserId || null,
    roleIds: roleIds.join(','),
    phone: optionalText(data.phone),
    email: optionalText(data.email),
    enable: data.enable ?? 1
  }
  if (!data.userId && data.password) {
    payload.password = optionalText(data.password)
  }
  return payload
}

function optionalText(value?: string | null) {
  if (typeof value !== 'string') return null
  const trimmed = value.trim()
  return trimmed ? trimmed : null
}

function normalizeRoleIds(roleIds?: SystemUser['roleIds']) {
  if (Array.isArray(roleIds)) {
    return roleIds.map((roleId) => String(roleId)).filter(Boolean)
  }
  if (typeof roleIds === 'string') {
    return roleIds
      .split(',')
      .map((roleId) => roleId.trim())
      .filter(Boolean)
  }
  return []
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

function toMenuTree(list: SystemMenu[]) {
  return toTree([...list].sort(compareSystemMenus), 'menuId', 'pid')
}

function toDepartmentTree(list: SystemDepartment[]) {
  return toTree(list, 'deptId', 'pid')
}

function compareSystemMenus(left: SystemMenu, right: SystemMenu) {
  return (
    entityIdOrder(left.pid) - entityIdOrder(right.pid) ||
    orderValue(left.sort) - orderValue(right.sort) ||
    entityIdOrder(left.menuId) - entityIdOrder(right.menuId)
  )
}

function orderValue(value?: number) {
  return typeof value === 'number' ? value : 0
}

function entityIdOrder(value?: EntityId) {
  const numeric = Number(value)
  return Number.isFinite(numeric) ? numeric : 0
}

function toTree<T extends object>(list: T[], idKey: keyof T, parentKey: keyof T) {
  const nodeMap = new Map<string, T & { children?: Array<T & { children?: T[] }> }>()
  const roots: Array<T & { children?: Array<T & { children?: T[] }> }> = []

  list.forEach((item) => {
    nodeMap.set(String(item[idKey] ?? ''), { ...item, children: [] })
  })

  nodeMap.forEach((node) => {
    const parentId = String(node[parentKey] ?? '')
    const parent = parentId && parentId !== '0' ? nodeMap.get(parentId) : undefined
    if (parent) {
      parent.children?.push(node)
    } else {
      roots.push(node)
    }
  })

  return roots.map((node) => {
    if (!node.children?.length) {
      delete node.children
    }
    return node
  })
}

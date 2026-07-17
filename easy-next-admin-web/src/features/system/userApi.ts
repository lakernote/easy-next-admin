import request from '@/api/request'
import { toData, toPageResult, type ApiResponse, type PageApiResponse } from '@/api/types'
import { resolveDownloadFileName } from '@/utils/download'
import type { EntityId, SystemUser, SystemUserPageQuery, UserImportResult } from './types'

const userEndpoints = {
  users: '/system/users',
  batchDelete: (userIds: EntityId[]) => `/system/users/batch/${userIds.join(',')}`,
  importTemplate: '/system/users/import-template',
  importUsers: '/system/users/import',
  exportUsers: '/system/users/export',
  resetPassword: (userId: EntityId) => `/system/users/resetPwd/${userId}`
}

// 用户管理页只关心账号 CRUD 和导入导出，角色候选项放在 userOptionsApi。
export async function pageUsers(query: SystemUserPageQuery) {
  const response = await request.get<PageApiResponse<SystemUser>>(userEndpoints.users, {
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
  const response = await request.get<ApiResponse<SystemUser>>(`${userEndpoints.users}/${userId}`)
  return toData(response.data)
}

export async function saveUser(data: Partial<SystemUser>) {
  const payload = toUserPayload(data)
  const response = data.userId
    ? await request.put<ApiResponse<SystemUser>>(`${userEndpoints.users}/${data.userId}`, payload)
    : await request.post<ApiResponse<SystemUser>>(userEndpoints.users, payload)
  return toData(response.data)
}

export async function downloadUserImportTemplate() {
  const response = await request.get<Blob>(userEndpoints.importTemplate, { responseType: 'blob' })
  return {
    blob: response.data,
    fileName: resolveDownloadFileName(response.headers['content-disposition']) || '用户导入模板.csv'
  }
}

export async function importUsers(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  const response = await request.post<ApiResponse<UserImportResult>>(userEndpoints.importUsers, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  return toData(response.data)
}

export async function exportUsers(query: Partial<SystemUserPageQuery>) {
  const response = await request.get<Blob>(userEndpoints.exportUsers, {
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
  const response = await request.put<ApiResponse<boolean>>(`${userEndpoints.users}/switch`, { userId, enable })
  return toData(response.data)
}

export async function resetUserPassword(userId: EntityId) {
  const response = await request.put<ApiResponse<boolean>>(userEndpoints.resetPassword(userId))
  return toData(response.data)
}

export async function deleteUser(userId: EntityId) {
  const response = await request.delete<ApiResponse<boolean>>(`${userEndpoints.users}/${userId}`)
  return toData(response.data)
}

export async function batchDeleteUsers(userIds: EntityId[]) {
  const response = await request.delete<ApiResponse<boolean>>(userEndpoints.batchDelete(userIds))
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

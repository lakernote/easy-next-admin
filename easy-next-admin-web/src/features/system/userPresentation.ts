import type { EntityId, SystemDepartment, SystemUser, SystemUserOption } from './types'

export interface SystemUserRelationMaps {
  roleNameById: ReadonlyMap<string, string>
  deptNameById: ReadonlyMap<string, string>
  assigneeNameById: ReadonlyMap<string, string>
}

export function flattenSystemDepartments(items: SystemDepartment[]): SystemDepartment[] {
  return items.flatMap((item) => [item, ...flattenSystemDepartments(item.children || [])])
}

export function systemDepartmentOptions(items: SystemDepartment[]) {
  return flattenSystemDepartments(items).map((item) => ({
    ...item,
    label: item.pid ? `  ${item.deptName}` : item.deptName
  }))
}

export function buildSystemUserForm(user?: SystemUser): Partial<SystemUser> {
  return {
    userId: user?.userId,
    userName: user?.userName || '',
    nickName: user?.nickName || '',
    realName: user?.realName || user?.nickName || '',
    employeeNo: user?.employeeNo || '',
    positionName: user?.positionName || '',
    deptId: user?.deptId,
    deptName: user?.deptName,
    managerUserId: user?.managerUserId,
    managerName: user?.managerName,
    departmentLeaderUserId: user?.departmentLeaderUserId,
    departmentLeaderName: user?.departmentLeaderName,
    upperDepartmentLeaderUserId: user?.upperDepartmentLeaderUserId,
    upperDepartmentLeaderName: user?.upperDepartmentLeaderName,
    roleIds: normalizeSystemUserRoleIds(user?.roleIds),
    phone: user?.phone || '',
    email: user?.email || '',
    enable: user?.enable ?? 1
  }
}

export function normalizeSystemUserRoleIds(roleIds?: SystemUser['roleIds']) {
  if (Array.isArray(roleIds)) return roleIds.map((roleId) => String(roleId)).filter(Boolean)
  if (typeof roleIds === 'string') {
    return roleIds
      .split(',')
      .map((roleId) => roleId.trim())
      .filter(Boolean)
  }
  return []
}

export function enrichSystemUserRelations(user: SystemUser, maps: SystemUserRelationMaps): SystemUser {
  const roleIds = normalizeSystemUserRoleIds(user.roleIds)
  const backendRoleNames = Array.isArray(user.roleNames) ? user.roleNames.filter(Boolean) : []
  const optionRoleNames = roleIds.map((roleId) => maps.roleNameById.get(String(roleId))).filter(Boolean) as string[]
  const roleNames = Array.from(new Set([...backendRoleNames, ...optionRoleNames]))
  return {
    ...user,
    roleIds,
    roleNames,
    deptName: user.deptName || (user.deptId ? maps.deptNameById.get(String(user.deptId)) : undefined),
    managerName: user.managerName || userNameFromMap(user.managerUserId, maps.assigneeNameById),
    departmentLeaderName: user.departmentLeaderName || userNameFromMap(user.departmentLeaderUserId, maps.assigneeNameById),
    upperDepartmentLeaderName: user.upperDepartmentLeaderName || userNameFromMap(user.upperDepartmentLeaderUserId, maps.assigneeNameById)
  }
}

export function displaySystemUserRoleNames(row: SystemUser, roleNameById: ReadonlyMap<string, string>) {
  if (Array.isArray(row.roleNames) && row.roleNames.length > 0) {
    return row.roleNames
  }
  return normalizeSystemUserRoleIds(row.roleIds)
    .map((roleId) => roleNameById.get(String(roleId)) || '')
    .filter(Boolean)
}

export function compactSystemUserMeta(row: SystemUser, roleNameById: ReadonlyMap<string, string>) {
  const roleText = displaySystemUserRoleNames(row, roleNameById).join(' / ')
  const relationText = row.managerName ? `上级 ${compactSystemPersonName(row.managerName)}` : ''
  const parts = [row.deptName, row.positionName, roleText, relationText, row.phone].filter(Boolean)
  return parts.length ? parts.join(' · ') : '未补充组织信息'
}

function userNameFromMap(userId: EntityId | undefined, assigneeNameById: ReadonlyMap<string, string>) {
  return userId ? assigneeNameById.get(String(userId)) : undefined
}

export function userManagerDisplayName(row: SystemUser, assigneeNameById: ReadonlyMap<string, string>) {
  return row.managerName || userNameFromMap(row.managerUserId, assigneeNameById) || '未设置'
}

export function compactSystemPersonName(name?: string) {
  return (name || '未设置').replace(/（[^）]+）/g, '').replace(/\([^)]*\)/g, '').trim() || '未设置'
}

export function ensureSystemUserAssigneeOption(options: SystemUserOption[], userId?: EntityId, name?: string) {
  if (!userId || !name || options.some((item) => sameEntityId(item.value, userId))) {
    return options
  }
  return [
    ...options,
    {
      value: userId,
      name
    }
  ]
}

export function sameEntityId(left?: EntityId, right?: EntityId) {
  return left !== undefined && left !== null && right !== undefined && right !== null && String(left) === String(right)
}

export function isBuiltinSystemUser(row: SystemUser) {
  return row.userName === 'admin' || row.roleNames?.includes('超级管理员')
}

export function userStatusDisabledReason(canEditUser: boolean, row: SystemUser) {
  if (!canEditUser) return '缺少用户编辑权限'
  if (isBuiltinSystemUser(row)) return '系统管理员不可停用'
  return ''
}

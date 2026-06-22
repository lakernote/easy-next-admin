import type { EntityId, RoleDataScope, SystemDepartment, SystemRole } from './types'

export type RoleAuthorizationStep = 'menu' | 'button' | 'scope'

export interface RolePermissionPresentationMeta {
  label: string
  step: string
  summary: string
  hint: string
  resourceLabel: string
  countUnit: string
  alertType: 'success' | 'warning' | 'info' | 'error'
  tagType: 'success' | 'warning' | 'info' | 'primary' | 'danger'
}

export interface RolePermissionGroupLike {
  permissionCodes: string[]
}

export interface RolePermissionTabSummary extends RolePermissionPresentationMeta {
  type: RoleAuthorizationStep
  total: number
  checked: number
}

export function buildRoleForm(role?: SystemRole): Partial<SystemRole> {
  return {
    roleId: role?.roleId,
    roleName: role?.roleName || '',
    roleCode: role?.roleCode || '',
    details: role?.details || '',
    roleLevel: role?.roleLevel ?? 50,
    enable: role?.enable ?? true,
    userCount: role?.userCount || 0
  }
}

export function roleStatusDisabledReason(canEditRole: boolean, role: SystemRole) {
  if (!canEditRole) return '缺少角色维护权限'
  if (role.roleCode === 'admin') return '超级管理员角色不可停用'
  return ''
}

export function normalizeRolePermissionCodes(codes: string[] = [], availableCodes: ReadonlySet<string>) {
  return Array.from(new Set(codes.filter((code) => availableCodes.has(code))))
}

export function permissionGroupKey(permission: Pick<{ featureId: string; group: string }, 'featureId' | 'group'>) {
  return `${permission.featureId}:${permission.group}`
}

export function buildRolePermissionTabs({
  tabTypes,
  config,
  groupsForType,
  checkedCount,
  dataScope
}: {
  tabTypes: RoleAuthorizationStep[]
  config: Record<RoleAuthorizationStep, RolePermissionPresentationMeta>
  groupsForType: (type: 'menu' | 'button') => RolePermissionGroupLike[]
  checkedCount: (codes: string[]) => number
  dataScope?: RoleDataScope
}): RolePermissionTabSummary[] {
  return tabTypes.map((type) => {
    if (type === 'scope') {
      return {
        type,
        ...config[type],
        total: 1,
        checked: dataScope ? 1 : 0
      }
    }
    const codes = groupsForType(type).flatMap((group) => group.permissionCodes)
    return {
      type,
      ...config[type],
      total: codes.length,
      checked: checkedCount(codes)
    }
  })
}

export function buildRoleAuthorizationSteps(
  stepTypes: RoleAuthorizationStep[],
  tabs: RolePermissionTabSummary[]
) {
  const tabMap = new Map(tabs.map((tab) => [tab.type, tab]))
  return stepTypes.map((type) => tabMap.get(type)).filter((tab): tab is RolePermissionTabSummary => Boolean(tab))
}

export function collectDepartmentMap(departments: SystemDepartment[]) {
  const departmentMap = new Map<string, SystemDepartment>()
  collectDepartments(departments, departmentMap)
  return departmentMap
}

function collectDepartments(departments: SystemDepartment[], departmentMap: Map<string, SystemDepartment>) {
  departments.forEach((department) => {
    departmentMap.set(String(department.deptId), department)
    if (department.children?.length) {
      collectDepartments(department.children, departmentMap)
    }
  })
}

export function filterRoleDepartmentNode(keyword: string, department: SystemDepartment) {
  if (!keyword) return true
  const normalizedKeyword = keyword.trim().toLowerCase()
  return [department.deptName, department.fullName]
    .filter(Boolean)
    .some((text) => String(text).toLowerCase().includes(normalizedKeyword))
}

export function sameRoleEntityId(left?: EntityId, right?: EntityId) {
  return left !== undefined && right !== undefined && String(left) === String(right)
}

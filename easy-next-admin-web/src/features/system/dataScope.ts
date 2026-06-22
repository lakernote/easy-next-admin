import type { RoleDataScope } from './types'

export type DataScopeRisk = 'normal' | 'medium' | 'high'

export interface DataScopeOption {
  label: string
  value: RoleDataScope
  description: string
  className: string
  risk: DataScopeRisk
  riskLabel: string
  riskTagType: 'warning' | 'danger' | 'info'
}

export interface DataScopeRiskAlert {
  type: 'warning' | 'error'
  title: string
}

export const defaultRoleDataScope: RoleDataScope = 'SELF'
export const customDepartmentDataScope: RoleDataScope = 'DEPT_SETS'

export const roleDataScopeOptions: DataScopeOption[] = [
  {
    label: '全部数据',
    value: 'ALL',
    description: '可以查看企业内全部数据，通常只授予系统管理员或集团管理角色。',
    className: 'is-all',
    risk: 'high',
    riskLabel: '高风险',
    riskTagType: 'danger'
  },
  {
    label: '本部门及以下',
    value: 'DEPT_AND_CHILDREN',
    description: '可以查看本部门和下级部门数据，适合部门负责人、区域负责人。',
    className: 'is-dept-tree',
    risk: 'medium',
    riskLabel: '需确认',
    riskTagType: 'warning'
  },
  {
    label: '本部门',
    value: 'DEPT',
    description: '只能查看本部门数据，适合部门内管理岗位。',
    className: 'is-dept',
    risk: 'normal',
    riskLabel: '标准',
    riskTagType: 'info'
  },
  {
    label: '本人数据',
    value: 'SELF',
    description: '只能查看自己创建、负责或参与的数据，适合普通员工。',
    className: 'is-self',
    risk: 'normal',
    riskLabel: '标准',
    riskTagType: 'info'
  },
  {
    label: '自定义部门',
    value: customDepartmentDataScope,
    description: '精确选择一个或多个部门，适合跨部门协作、区域管理和临时审计角色。',
    className: 'is-custom',
    risk: 'medium',
    riskLabel: '需确认',
    riskTagType: 'warning'
  }
]

export const roleDataScopeOptionMap = roleDataScopeOptions.reduce<Record<RoleDataScope, DataScopeOption>>((map, option) => {
  map[option.value] = option
  return map
}, {} as Record<RoleDataScope, DataScopeOption>)

export function normalizeRoleDataScope(scope?: string): RoleDataScope | undefined {
  if (!scope) return undefined
  // 前端只接受后端标准 code；中文 label 只用于展示，不作为接口值提交。
  if (scope in roleDataScopeOptionMap) return scope as RoleDataScope
  return undefined
}

export function normalizeAssignableDataScopes(scopes?: Array<RoleDataScope | string>) {
  const normalized = (scopes || [])
    .map((scope) => normalizeRoleDataScope(scope))
    .filter((scope): scope is RoleDataScope => Boolean(scope))
  return normalized.length > 0 ? Array.from(new Set(normalized)) : [defaultRoleDataScope]
}

export function roleDataScopeLabel(scope?: string) {
  const normalizedScope = normalizeRoleDataScope(scope)
  return normalizedScope ? roleDataScopeOptionMap[normalizedScope].label : '未配置'
}

export function roleDataScopeClass(scope?: string) {
  const normalizedScope = normalizeRoleDataScope(scope)
  return ['role-scope-badge', normalizedScope ? roleDataScopeOptionMap[normalizedScope].className : 'is-empty']
}

export function roleDataScopeRiskAlert(scope: RoleDataScope): DataScopeRiskAlert | undefined {
  if (scope === 'ALL') {
    return {
      type: 'error',
      title: '全部数据会开放企业全量业务数据，保存前请确认角色成员。'
    }
  }
  if (scope === 'DEPT_AND_CHILDREN') {
    return {
      type: 'warning',
      title: '本部门及以下会包含下级组织数据，适合明确的组织负责人。'
    }
  }
  if (scope === customDepartmentDataScope) {
    return {
      type: 'warning',
      title: '自定义部门只按所选部门生效，请确认部门清单完整。'
    }
  }
  return undefined
}

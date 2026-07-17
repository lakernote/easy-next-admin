import { describe, expect, it } from 'vitest'
import {
  buildRoleAuthorizationSteps,
  buildRoleForm,
  buildRolePermissionTabs,
  collectDepartmentMap,
  filterRoleDepartmentNode,
  normalizeRolePermissionCodes,
  permissionGroupKey,
  roleStatusDisabledReason,
  sameRoleEntityId,
} from './rolePresentation'
import type { SystemDepartment, SystemRole } from './types'

describe('system role presentation helpers', () => {
  it('builds role drawer defaults without leaking authorization fields', () => {
    const existing = buildRoleForm(role({ roleId: 9, roleName: '审计员', dataScope: 'ALL', roleLevel: 20, enable: false }))

    expect(buildRoleForm()).toEqual({
      roleId: undefined,
      roleName: '',
      roleCode: '',
      details: '',
      roleLevel: 50,
      enable: true,
      userCount: 0
    })
    expect(existing).toEqual({
      roleId: 9,
      roleName: '审计员',
      roleCode: 'auditor',
      details: '',
      roleLevel: 20,
      enable: false,
      userCount: 0
    })
    expect(existing).not.toHaveProperty('dataScope')
  })

  it('protects admin role status and compares mixed id types', () => {
    expect(roleStatusDisabledReason(false, role({ roleCode: 'auditor' }))).toBe('缺少角色维护权限')
    expect(roleStatusDisabledReason(true, role({ roleCode: 'admin' }))).toBe('超级管理员角色不可停用')
    expect(roleStatusDisabledReason(true, role({ roleCode: 'auditor' }))).toBe('')
    expect(sameRoleEntityId(1, '1')).toBe(true)
    expect(sameRoleEntityId(undefined, '1')).toBe(false)
  })

  it('normalizes available permission codes and builds stable group keys', () => {
    const availableCodes = new Set(['system:user:list', 'system:user:add'])

    expect(normalizeRolePermissionCodes(['system:user:add', 'unknown', 'system:user:add'], availableCodes)).toEqual(['system:user:add'])
    expect(permissionGroupKey({ featureId: 'system', group: '用户管理' })).toBe('system:用户管理')
  })

  it('builds permission tab and authorization step summaries', () => {
    const tabs = buildRolePermissionTabs({
      tabTypes: ['menu', 'button', 'scope'],
      config: {
        menu: meta('菜单权限', 'primary'),
        button: meta('按钮权限', 'warning'),
        scope: meta('数据范围', 'success')
      },
      groupsForType: (type) => type === 'menu'
        ? [{ permissionCodes: ['menu:user', 'menu:role'] }]
        : [{ permissionCodes: ['button:add'] }],
      checkedCount: (codes) => codes.filter((code) => code.includes('user') || code.includes('add')).length,
      dataScope: 'SELF'
    })

    expect(tabs).toEqual([
      expect.objectContaining({ type: 'menu', label: '菜单权限', total: 2, checked: 1 }),
      expect.objectContaining({ type: 'button', label: '按钮权限', total: 1, checked: 1 }),
      expect.objectContaining({ type: 'scope', label: '数据范围', total: 1, checked: 1 })
    ])
    expect(buildRoleAuthorizationSteps(['menu', 'button', 'scope'], tabs)).toEqual(tabs)
  })

  it('collects and filters departments for custom data scope review', () => {
    const departments: SystemDepartment[] = [
      {
        deptId: 1,
        deptName: '总部',
        fullName: '杭州总部',
        status: true,
        children: [
          { deptId: 2, deptName: '研发部', fullName: '杭州总部 / 研发部', pid: 1, status: true }
        ]
      }
    ]

    expect(Array.from(collectDepartmentMap(departments).keys())).toEqual(['1', '2'])
    expect(filterRoleDepartmentNode('研发', departments[0].children![0])).toBe(true)
    expect(filterRoleDepartmentNode('财务', departments[0].children![0])).toBe(false)
    expect(filterRoleDepartmentNode('', departments[0])).toBe(true)
  })
})

function role(overrides: Partial<SystemRole>): SystemRole {
  return {
    roleId: 1,
    roleName: '审计员',
    roleCode: 'auditor',
    enable: true,
    ...overrides
  }
}

function meta(label: string, tagType: 'primary' | 'warning' | 'success') {
  return {
    label,
    step: '1',
    summary: label,
    hint: label,
    resourceLabel: label,
    countUnit: '个',
    alertType: 'info' as const,
    tagType
  }
}

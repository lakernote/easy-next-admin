import { describe, expect, it } from 'vitest'
import {
  buildSystemUserForm,
  compactSystemPersonName,
  compactSystemUserMeta,
  displaySystemUserRoleNames,
  enrichSystemUserRelations,
  ensureSystemUserAssigneeOption,
  flattenSystemDepartments,
  isBuiltinSystemUser,
  normalizeSystemUserRoleIds,
  sameEntityId,
  systemDepartmentOptions,
  userManagerDisplayName,
  userStatusDisabledReason,
} from './userPresentation'
import type { SystemDepartment, SystemUser, SystemUserOption } from './types'

describe('system user presentation helpers', () => {
  it('flattens departments and builds form select labels', () => {
    const departments: SystemDepartment[] = [
      {
        deptId: 1,
        deptName: '总部',
        status: true,
        children: [
          { deptId: 2, pid: 1, deptName: '研发部', status: true }
        ]
      }
    ]

    expect(flattenSystemDepartments(departments).map((dept) => dept.deptName)).toEqual(['总部', '研发部'])
    expect(systemDepartmentOptions(departments)).toEqual([
      expect.objectContaining({ deptId: 1, label: '总部' }),
      expect.objectContaining({ deptId: 2, label: '  研发部' })
    ])
  })

  it('normalizes roles and enriches user relation names from option maps', () => {
    const user = systemUser({
      roleIds: '1, 2, ,1',
      roleNames: ['员工', ''],
      deptId: 10,
      managerUserId: '1001'
    })
    const enriched = enrichSystemUserRelations(user, {
      roleNameById: new Map([
        ['1', '员工'],
        ['2', '审批人']
      ]),
      deptNameById: new Map([['10', '研发部']]),
      assigneeNameById: new Map([['1001', '王经理']])
    })

    expect(normalizeSystemUserRoleIds(user.roleIds)).toEqual(['1', '2', '1'])
    expect(enriched.roleIds).toEqual(['1', '2', '1'])
    expect(enriched.roleNames).toEqual(['员工', '审批人'])
    expect(enriched.deptName).toBe('研发部')
    expect(enriched.managerName).toBe('王经理')
    expect(displaySystemUserRoleNames(enriched, new Map())).toEqual(['员工', '审批人'])
  })

  it('builds compact user metadata and manager display text', () => {
    const roleNameById = new Map([['2', '审批人']])
    const user = systemUser({
      deptName: '研发部',
      positionName: '产品经理',
      roleIds: [2],
      managerName: '王经理（研发中心）',
      phone: '13800000000'
    })

    expect(compactSystemPersonName(user.managerName)).toBe('王经理')
    expect(compactSystemUserMeta(user, roleNameById)).toBe('研发部 · 产品经理 · 审批人 · 上级 王经理 · 13800000000')
    expect(userManagerDisplayName(systemUser({ managerUserId: '1001' }), new Map([['1001', '李主管']]))).toBe('李主管')
    expect(userManagerDisplayName(systemUser({} ), new Map())).toBe('未设置')
  })

  it('protects builtin administrators and preserves manager options', () => {
    const options: SystemUserOption[] = [{ value: 1, name: '已有人员' }]

    expect(isBuiltinSystemUser(systemUser({ userName: 'admin' }))).toBe(true)
    expect(isBuiltinSystemUser(systemUser({ roleNames: ['超级管理员'] }))).toBe(true)
    expect(userStatusDisabledReason(false, systemUser({ userName: 'lisi' }))).toBe('缺少用户编辑权限')
    expect(userStatusDisabledReason(true, systemUser({ userName: 'admin' }))).toBe('系统管理员不可停用')
    expect(userStatusDisabledReason(true, systemUser({ userName: 'lisi' }))).toBe('')
    expect(sameEntityId(1, '1')).toBe(true)
    expect(ensureSystemUserAssigneeOption(options, '2', '新上级')).toEqual([
      { value: 1, name: '已有人员' },
      { value: '2', name: '新上级' }
    ])
    expect(ensureSystemUserAssigneeOption(options, 1, '已有人员')).toBe(options)
  })

  it('builds drawer form defaults from optional user detail', () => {
    expect(buildSystemUserForm()).toMatchObject({
      userName: '',
      nickName: '',
      realName: '',
      roleIds: [],
      enable: 1
    })

    expect(buildSystemUserForm(systemUser({ nickName: '林员工', roleIds: '3,4', enable: 0 }))).toMatchObject({
      userName: 'zhangsan',
      nickName: '林员工',
      realName: '林员工',
      roleIds: ['3', '4'],
      enable: 0
    })
  })
})

function systemUser(overrides: Partial<SystemUser>): SystemUser {
  return {
    userId: 1,
    userName: 'zhangsan',
    nickName: '张三',
    enable: 1,
    ...overrides
  }
}

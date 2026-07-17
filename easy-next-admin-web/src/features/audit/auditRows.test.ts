import { describe, expect, it } from 'vitest'
import { AUDIT_TABS, auditDetailItems, toAuditRow } from './auditRows'

describe('audit row presentation', () => {
  it('splits enterprise audit records without slow interface audit', () => {
    expect(AUDIT_TABS.map((tab) => tab.label)).toEqual(['操作审计', '登录审计', '敏感变更', '异常审计', '接口访问'])
    expect(AUDIT_TABS.map((tab) => tab.label).join(',')).not.toContain('慢接口')
  })

  it('maps failed login records to a traceable audit row', () => {
    const row = toAuditRow('login', {
      id: 1,
      userName: 'manager',
      loginResult: 'FAIL',
      failReason: '密码错误',
      ip: '127.0.0.1',
      clientType: 'web',
      loginTime: '2026-05-08 10:00:00'
    })

    expect(row.event).toBe('登录失败')
    expect(row.resultType).toBe('danger')
    expect(row.source).toBe('127.0.0.1')
    expect(auditDetailItems('login', row).some((item) => item.label === '失败原因' && item.value === '密码错误')).toBe(true)
  })

  it('keeps operation and data change records readable for enterprise review', () => {
    const operation = toAuditRow('operation', {
      id: 2,
      module: '角色权限',
      action: '编辑角色',
      operatorName: 'admin',
      requestMethod: 'PUT',
      requestUri: '/api/system/roles',
      responseStatus: 'SUCCESS',
      durationMs: 23,
      createdAt: '2026-05-08 10:01:00'
    })
    const dataChange = toAuditRow('dataChange', {
      id: 3,
      bizType: '角色权限',
      bizId: 'R001',
      tableName: 'sys_role',
      changeType: 'UPDATE',
      changedFields: 'role_name,role_code',
      operatorId: 1,
      operator: { realName: '王审计', userName: 'auditor' },
      createdAt: '2026-05-08 10:02:00'
    })

    expect(operation.event).toBe('角色权限 / 编辑角色')
    expect(operation.result).toBe('成功')
    expect(dataChange.actor).toBe('王审计')
    expect(dataChange.result).toBe('修改')
    expect(dataChange.summary).toBe('role_name,role_code')
  })

  it('presents expected business failures separately from system exceptions', () => {
    const row = toAuditRow('operation', {
      id: 4,
      module: '认证授权',
      action: '登录',
      operatorName: 'manager',
      requestUri: '/api/auth/login',
      responseStatus: 'FAIL',
      errorMessage: '用户名或密码不正确',
      createdAt: '2026-05-08 10:03:00'
    })

    expect(row.actor).toBe('manager')
    expect(row.result).toBe('失败')
    expect(row.resultType).toBe('warning')
  })
})

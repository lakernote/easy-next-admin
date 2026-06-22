import { describe, expect, it } from 'vitest'
import {
  customDepartmentDataScope,
  defaultRoleDataScope,
  normalizeAssignableDataScopes,
  normalizeRoleDataScope,
  roleDataScopeClass,
  roleDataScopeLabel,
  roleDataScopeRiskAlert
} from './dataScope'

describe('system role data scope domain rules', () => {
  it('keeps API values on stable backend codes', () => {
    expect(normalizeRoleDataScope('ALL')).toBe('ALL')
    expect(normalizeRoleDataScope(customDepartmentDataScope)).toBe(customDepartmentDataScope)
    expect(normalizeRoleDataScope('全部数据')).toBeUndefined()
    expect(normalizeAssignableDataScopes(['DEPT', 'DEPT', '未知'])).toEqual(['DEPT'])
    expect(normalizeAssignableDataScopes([])).toEqual([defaultRoleDataScope])
  })

  it('provides table labels and risk hints from one dictionary', () => {
    expect(roleDataScopeLabel('SELF')).toBe('本人数据')
    expect(roleDataScopeClass('ALL')).toEqual(['role-scope-badge', 'is-all'])
    expect(roleDataScopeRiskAlert('ALL')?.type).toBe('error')
    expect(roleDataScopeRiskAlert('DEPT')?.type).toBeUndefined()
  })
})

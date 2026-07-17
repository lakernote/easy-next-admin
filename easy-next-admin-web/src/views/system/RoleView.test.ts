import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const viewSource = readFileSync(resolve(__dirname, 'RoleView.vue'), 'utf-8')
const toolbarSource = readFileSync(resolve(__dirname, '../../components/table/TableToolbar.vue'), 'utf-8')
const dataScopeSource = readFileSync(resolve(__dirname, '../../features/system/dataScope.ts'), 'utf-8')
const rolePresentationSource = readFileSync(resolve(__dirname, '../../features/system/rolePresentation.ts'), 'utf-8')

describe('system role view table controls', () => {
  it('keeps role table controls compact without a repeated card heading', () => {
    expect(viewSource).toContain('class="table-control-row role-table-controls"')
    expect(viewSource).toContain('class="filter-bar role-filter-bar"')
    expect(viewSource).not.toContain('title="角色列表"')
    expect(viewSource).not.toContain('description="角色承载页面、按钮和数据范围策略。"')
  })

  it('does not repeat refresh inside the role table toolbar', () => {
    expect(viewSource).toContain('<TableToolbar')
    expect(viewSource).not.toContain('v-model:search-visible')
    expect(viewSource).not.toContain('@refresh="loadRoles"')
    expect(toolbarSource).not.toContain('隐藏筛选条件')
    expect(toolbarSource).not.toContain('searchToggle')
    expect(toolbarSource).not.toContain('refreshable')
    expect(toolbarSource).not.toContain("import { Filter")
    expect(toolbarSource).not.toContain("import { Grid, Refresh")
  })

  it('keeps authorization as the primary row action and hides metadata actions in more menu', () => {
    expect(viewSource).toContain('class="admin-table role-table"')
    expect(viewSource).toContain('label="操作" width="176" fixed="right"')
    expect(viewSource).toContain('class="role-authorize-button"')
    expect(viewSource).toContain('class="role-row-more"')
    expect(viewSource).toContain('role-edit-menu-')
    expect(viewSource).toContain('role-delete-menu-')
    expect(viewSource).not.toContain('label="操作" width="270"')
  })

  it('renders data scope and status as compact enterprise table signals', () => {
    expect(viewSource).toContain('roleDataScopeClass(row.dataScope)')
    expect(viewSource).toContain('role-scope-badge')
    expect(dataScopeSource).toContain("value: 'ALL'")
    expect(dataScopeSource).toContain("className: 'is-self'")
    expect(viewSource).toContain('class="role-status-switch"')
    expect(viewSource).toContain('--el-table-fixed-right-column')
  })

  it('keeps role level editable because it is a backend authorization boundary', () => {
    expect(viewSource).toContain('label="角色层级"')
    expect(viewSource).toContain('v-model="roleForm.roleLevel"')
    expect(viewSource).toContain('buildRoleForm(role)')
    expect(rolePresentationSource).toContain('roleLevel: role?.roleLevel')
    expect(viewSource).toContain('roleLevel: roleForm.roleLevel')
  })

  it('keeps data scope authorization bounded and reviewable in the drawer', () => {
    expect(viewSource).toContain('assignableDataScopes')
    expect(viewSource).toContain(':disabled="!canAssignDataScope(option.value)"')
    expect(viewSource).toContain('authorizationBlockedReason')
    expect(viewSource).toContain('高风险数据授权')
    expect(viewSource).toContain('permission-data-impact')
  })

  it('supports searching and reviewing selected custom departments', () => {
    expect(viewSource).toContain('v-model="departmentFilterText"')
    expect(viewSource).toContain(':filter-node-method="filterDepartmentNode"')
    expect(viewSource).toContain('visibleSelectedDepartments')
    expect(viewSource).toContain('removeSelectedDepartment')
    expect(viewSource).toContain('clearSelectedDepartments')
  })
})

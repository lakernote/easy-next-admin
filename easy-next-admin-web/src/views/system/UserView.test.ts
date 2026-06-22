import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const viewSource = readFileSync(resolve(__dirname, 'UserView.vue'), 'utf-8')

describe('system user view filters', () => {
  it('supports department filtering in the enterprise user list', () => {
    expect(viewSource).toContain('class="department-filter"')
    expect(viewSource).toContain('v-model="query.deptId"')
    expect(viewSource).toContain('placeholder="全部部门"')
    expect(viewSource).toContain('handleDepartmentFilterChange')
    expect(viewSource).toContain('query.deptId = undefined')
  })

  it('uses a dedicated reset password permission instead of generic user edit', () => {
    expect(viewSource).toContain('PermissionCodes.system.user.resetPassword')
    expect(viewSource).toContain('canResetUserPassword')
  })

  it('keeps approval relation display focused on the direct manager', () => {
    expect(viewSource).toContain('label="直接上级"')
    expect(viewSource).toContain("{ key: 'approval', label: '直接上级' }")
    expect(viewSource).toContain('ensureAssigneeOption(user?.managerUserId, user?.managerName)')
    expect(viewSource).not.toContain('approval-relation-preview')
    expect(viewSource).not.toContain('selectedDepartmentLeaderName')
    expect(viewSource).not.toContain('上级部门负责人')
  })

  it('keeps page refresh centralized in the top tag bar instead of the table toolbar', () => {
    expect(viewSource).not.toContain('<el-button :icon="Refresh" :loading="loading" @click="loadUsers">刷新</el-button>')
    expect(viewSource).toContain('class="table-control-row"')
    expect(viewSource).toContain('<TableToolbar v-model:columns="columns" class="table-toolbar-inline" />')
    expect(viewSource).not.toContain('v-model:search-visible')
  })

  it('keeps sensitive row actions behind a compact more menu', () => {
    expect(viewSource).toContain('user-row-actions')
    expect(viewSource).toContain('class="user-row-more"')
    expect(viewSource).toContain('label="操作" :width="isCompactUserTable ? 112 : 168"')
    expect(viewSource).toContain('user-reset-password-menu')
    expect(viewSource).toContain('user-delete-menu')
    expect(viewSource).toContain(':deep(.user-more-button.el-button)')
    expect(viewSource).not.toContain('type="warning"')
  })

  it('uses a page-scoped table class for refined fixed columns', () => {
    expect(viewSource).toContain('class="admin-table user-table"')
    expect(viewSource).toContain('--el-table-fixed-right-column')
  })
})

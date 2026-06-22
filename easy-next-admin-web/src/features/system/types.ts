import type { PageQuery } from '@/api/types'

export type EntityId = string | number

export interface SystemUserPageQuery extends PageQuery {
  keyWord?: string
  deptId?: EntityId
  enable?: number
}

export interface SystemRolePageQuery extends PageQuery {
  keyword?: string
  enable?: boolean
}

export interface SystemUser {
  userId: EntityId
  userName: string
  nickName: string
  password?: string
  deptId?: EntityId
  deptName?: string
  managerUserId?: EntityId
  managerName?: string
  departmentLeaderUserId?: EntityId
  departmentLeaderName?: string
  upperDepartmentLeaderUserId?: EntityId
  upperDepartmentLeaderName?: string
  roleIds?: EntityId[] | string
  roleNames?: string[]
  employeeNo?: string
  realName?: string
  positionName?: string
  lastLoginTime?: string
  phone?: string
  email?: string
  avatar?: string
  enable: 0 | 1
  createTime?: string
}

export interface UserImportRowError {
  rowNumber: number
  userName?: string
  message: string
}

export interface UserImportResult {
  totalRows: number
  successRows: number
  failedRows: number
  errors: UserImportRowError[]
}

export interface SystemRole {
  roleId: EntityId
  roleName: string
  roleCode: string
  details?: string
  enable: boolean
  roleLevel?: number
  userCount?: number
  dataScope?: string
  checked?: boolean
  createTime?: string
}

export type RoleDataScope = 'ALL' | 'DEPT_AND_CHILDREN' | 'DEPT' | 'SELF' | 'DEPT_SETS'

export interface SystemRolePermission {
  roleId: EntityId
  dataScope?: RoleDataScope | string
  deptIds?: EntityId[]
  permissionCodes: string[]
  assignableDataScopes?: Array<RoleDataScope | string>
  roleUserCount?: number
}

export interface SystemRoleAuthorization extends SystemRolePermission {
  roleName?: string
  roleCode?: string
  dataScope?: RoleDataScope | string
  pageCodes: string[]
  actionCodes: string[]
}

export interface SystemMenu {
  menuId: EntityId
  pid?: EntityId
  title: string
  icon?: string
  href?: string
  sort?: number
  enable?: boolean
  remark?: string
  type: 0 | 1 | 2
  powerCode?: string
  componentPath?: string
  visible?: boolean
  children?: SystemMenu[]
}

export interface SystemMenuResourcePayload {
  menuId?: EntityId
  pid?: EntityId
  title: string
  icon?: string
  href?: string
  sort?: number
  enable?: boolean
  remark?: string
  type: 0 | 1 | 2
  powerCode?: string
  componentPath?: string
  visible?: boolean
}

export interface SystemDepartment {
  deptId: EntityId
  deptName: string
  fullName?: string
  address?: string
  pid?: EntityId
  leaderUserId?: EntityId
  leaderName?: string
  status: boolean
  sort?: number
  children?: SystemDepartment[]
}

export interface SystemUserOption {
  name: string
  value: EntityId
  userName?: string
  avatar?: string
}

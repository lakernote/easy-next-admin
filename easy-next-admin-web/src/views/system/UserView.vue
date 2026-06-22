<template>
  <section class="resource-page">
    <div class="resource-hero">
      <div>
        <h1>用户管理</h1>
        <p>维护账号、部门、状态和角色绑定。</p>
      </div>
      <div class="resource-actions">
        <el-button
          v-permission="PermissionCodes.system.user.import"
          :icon="Upload"
          data-testid="user-import-button"
          @click="openUserImportDialog"
        >
          导入用户
        </el-button>
        <el-button
          v-permission="PermissionCodes.system.user.export"
          :icon="Download"
          :loading="exportingUsers"
          data-testid="user-export-button"
          @click="handleExportUsers"
        >
          导出用户
        </el-button>
        <el-button
          v-permission="PermissionCodes.system.user.add"
          type="primary"
          :icon="Plus"
          data-testid="user-add-button"
          @click="openUserDrawer()"
        >
          新增用户
        </el-button>
      </div>
    </div>

    <section ref="tablePanelRef" class="surface resource-panel is-fluid-table">
      <div class="table-control-row">
        <el-form :inline="true" class="filter-bar user-filter-bar" @submit.prevent>
          <el-form-item label="关键词" class="keyword-filter">
            <el-input
              v-model="query.keyWord"
              :prefix-icon="Search"
              placeholder="用户名 / 姓名 / 部门"
              clearable
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item v-if="canReadDepartments" label="所属部门" class="department-filter">
            <el-tree-select
              v-model="query.deptId"
              :data="departmentTree"
              :props="departmentTreeProps"
              node-key="deptId"
              value-key="deptId"
              check-strictly
              filterable
              clearable
              default-expand-all
              popper-class="user-department-select-popper"
              placeholder="全部部门"
              @change="handleDepartmentFilterChange"
            >
              <template #default="{ data }">
                <span class="department-option">
                  <span>{{ data.deptName }}</span>
                </span>
              </template>
            </el-tree-select>
          </el-form-item>
          <el-form-item label="状态" class="status-filter">
            <el-select v-model="query.enable" placeholder="全部" clearable style="width: 140px">
              <el-option label="启用" :value="1" />
              <el-option label="停用" :value="0" />
            </el-select>
          </el-form-item>
          <el-form-item class="filter-actions">
            <el-button type="primary" plain :icon="Search" @click="handleSearch">查询</el-button>
            <el-button @click="resetFilters">重置</el-button>
          </el-form-item>
        </el-form>
        <TableToolbar v-model:columns="columns" class="table-toolbar-inline" />
      </div>

      <el-table
        v-loading="loading"
        :data="users"
        row-key="userId"
        :height="tableHeight"
        class="admin-table user-table"
        empty-text="暂无用户数据"
        @selection-change="handleUserSelectionChange"
      >
        <el-table-column v-if="!isCompactUserTable" type="selection" width="44" :selectable="isUserSelectable" />
        <el-table-column v-if="displayColumns.user" prop="userName" label="用户" :min-width="isCompactUserTable ? 220 : 180">
          <template #default="{ row }">
            <div class="entity-cell">
              <UserAvatar class="entity-avatar" :user="row" />
              <span>
                <strong>{{ row.userName }}</strong>
                <small>{{ row.nickName || '-' }}<template v-if="row.employeeNo"> · {{ row.employeeNo }}</template></small>
                <small v-if="isCompactUserTable" class="user-compact-meta">{{ compactUserMeta(row) }}</small>
                <span v-if="isCompactUserTable" class="user-compact-status">
                  <EnableStatusSwitch
                    class="user-status-switch"
                    :model-value="row.enable === 1"
                    :loading="switchingUserId === row.userId"
                    :disabled="!canEditUser || isBuiltinAdmin(row)"
                    :disabled-reason="userStatusDisabledReason(row)"
                    :target-name="row.nickName || row.userName"
                    @toggle="handleToggleUserStatus(row)"
                  />
                </span>
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="displayColumns.org" label="组织岗位" min-width="210">
          <template #default="{ row }">
            <div class="user-org-cell">
              <strong>{{ row.deptName || '未设置部门' }}</strong>
              <small>{{ row.positionName || '未设置岗位' }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="displayColumns.approval" label="直接上级" min-width="150">
          <template #default="{ row }">
            <div class="direct-manager-cell">
              <strong :title="userManagerName(row)">{{ compactPersonName(userManagerName(row)) }}</strong>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="displayColumns.roles" label="角色" min-width="184">
          <template #default="{ row }">
            <div v-if="displayRoleNames(row).length" class="user-role-list">
              <el-tooltip v-for="role in displayRoleNames(row)" :key="role" :content="role" placement="top" :show-after="300">
                <el-tag class="inline-tag user-role-tag" effect="plain">{{ role }}</el-tag>
              </el-tooltip>
            </div>
            <span v-if="displayRoleNames(row).length === 0" class="muted-text">未绑定角色</span>
          </template>
        </el-table-column>
        <el-table-column v-if="displayColumns.contact" label="联系方式" min-width="168">
          <template #default="{ row }">
            <div class="user-contact-cell">
              <span>{{ row.phone || '-' }}</span>
              <small v-if="row.email">{{ row.email }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="displayColumns.status" prop="enable" label="状态" width="112" fixed="right" align="center" header-align="center">
          <template #default="{ row }">
            <EnableStatusSwitch
              class="user-status-switch"
              :model-value="row.enable === 1"
              :loading="switchingUserId === row.userId"
              :disabled="!canEditUser || isBuiltinAdmin(row)"
              :disabled-reason="userStatusDisabledReason(row)"
              :target-name="row.nickName || row.userName"
              @toggle="handleToggleUserStatus(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" :width="isCompactUserTable ? 112 : 168" fixed="right" align="center" header-align="center">
          <template #default="{ row }">
            <div class="row-actions user-row-actions">
              <el-button
                v-permission="PermissionCodes.system.user.edit"
                text
                type="primary"
                :icon="EditPen"
                :loading="loadingUserDetailId === row.userId"
                :data-testid="`user-edit-${row.userId}`"
                @click="openUserDrawer(row)"
              >
                编辑
              </el-button>
              <el-dropdown v-if="canResetUserPassword || canDeleteUser" class="user-row-more" trigger="click" placement="bottom-end">
                <el-button class="user-more-button" text :icon="MoreFilled" title="更多操作" aria-label="更多操作" @click.stop>
                  更多
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu class="user-row-action-menu">
                    <el-dropdown-item
                      v-if="canResetUserPassword"
                      :disabled="isBuiltinAdmin(row) || resettingPasswordUserId === row.userId"
                      :data-testid="`user-reset-password-menu-${row.userId}`"
                      @click.stop="handleResetPassword(row)"
                    >
                      <el-icon><Key /></el-icon>
                      <span>重置密码</span>
                    </el-dropdown-item>
                    <el-dropdown-item
                      v-if="canDeleteUser"
                      class="is-danger"
                      :disabled="isBuiltinAdmin(row) || deletingUserId === row.userId"
                      :data-testid="`user-delete-menu-${row.userId}`"
                      @click.stop="handleDeleteUser(row)"
                    >
                      <el-icon><Delete /></el-icon>
                      <span>删除用户</span>
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer is-split">
        <div v-if="!isCompactUserTable" class="table-bulk-actions">
          <el-button
            v-permission:disable="{ permissions: PermissionCodes.system.user.delete, reason: '缺少用户删除权限' }"
            :icon="Delete"
            :disabled="deletableSelectedUsers.length === 0"
            :loading="batchDeletingUsers"
            @click="handleBatchDeleteUsers"
          >
            批量删除
          </el-button>
          <span>已选 {{ selectedUsers.length }} 项</span>
        </div>
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="total"
          :current-page="query.page"
          :page-size="query.limit"
          :page-sizes="[10, 20, 50]"
          @current-change="handleCurrentChange"
          @size-change="handleSizeChange"
        />
      </div>
    </section>

    <el-drawer v-model="userDrawerVisible" :title="userDrawerTitle" size="min(560px, 92vw)" data-testid="user-drawer">
      <el-form ref="userFormRef" :model="userForm" :rules="userFormRules" label-position="top" class="drawer-form">
        <div class="form-section-title">基础信息</div>
        <div class="form-grid">
          <el-form-item label="用户名" prop="userName">
            <el-input
              v-model="userForm.userName"
              :disabled="Boolean(userForm.userId)"
              placeholder="例如 wang"
              data-testid="user-form-username"
            />
          </el-form-item>
          <el-form-item label="姓名/昵称" prop="nickName">
            <el-input v-model="userForm.nickName" placeholder="例如 王经理" data-testid="user-form-nickname" />
          </el-form-item>
        </div>

        <div class="form-grid">
          <el-form-item label="员工编号" prop="employeeNo">
            <el-input v-model="userForm.employeeNo" placeholder="例如 EA000201" />
          </el-form-item>
          <el-form-item label="岗位" prop="positionName">
            <el-input v-model="userForm.positionName" placeholder="例如 产品经理 / 客户成功经理" />
          </el-form-item>
        </div>

        <div class="form-grid">
          <el-form-item label="手机号" prop="phone">
            <el-input v-model="userForm.phone" placeholder="请输入手机号" data-testid="user-form-phone" />
          </el-form-item>
          <el-form-item label="邮箱" prop="email">
            <el-input v-model="userForm.email" placeholder="name@company.com" data-testid="user-form-email" />
          </el-form-item>
        </div>

        <div class="form-section-title">组织与权限</div>
        <el-form-item label="所属部门" prop="deptId">
          <el-select
            v-model="userForm.deptId"
            placeholder="请选择部门"
            filterable
            style="width: 100%"
            data-testid="user-form-department"
          >
            <el-option v-for="dept in departmentOptions" :key="dept.deptId" :label="dept.label" :value="dept.deptId" />
          </el-select>
        </el-form-item>
        <el-form-item label="直接上级">
          <el-select
            v-model="userForm.managerUserId"
            placeholder="请选择直接上级"
            clearable
            filterable
            style="width: 100%"
            data-testid="user-form-manager"
          >
            <el-option
              v-for="assignee in managerOptions"
              :key="assignee.value"
              :label="assignee.name"
              :value="assignee.value"
              :disabled="sameId(assignee.value, userForm.userId)"
            />
          </el-select>
          <small class="form-help">用于流程中的“发起人直接上级”审批节点。</small>
        </el-form-item>
        <el-form-item label="绑定角色" prop="roleIds">
          <el-select
            v-model="userForm.roleIds"
            placeholder="请选择角色"
            multiple
            filterable
            style="width: 100%"
            data-testid="user-form-roles"
            :loading="loadingRoleOptions"
          >
            <el-option v-for="role in roleOptions" :key="role.roleId" :label="role.roleName" :value="String(role.roleId)" />
          </el-select>
        </el-form-item>
        <el-form-item label="账号状态" prop="enable">
          <div class="account-status-control">
            <el-switch
              v-model="userForm.enable"
              :active-value="1"
              :inactive-value="0"
              active-text="启用"
              inactive-text="停用"
              aria-label="账号启用状态"
            />
            <div class="account-status-copy">
              <strong>{{ userForm.enable === 1 ? '当前可登录' : '当前不可登录' }}</strong>
              <span>{{ userForm.enable === 1 ? '账号可登录并使用已授权功能。' : '账号停用后无法登录，历史数据仍会保留。' }}</span>
            </div>
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="drawer-footer">
          <el-button @click="userDrawerVisible = false">取消</el-button>
          <el-button type="primary" :loading="savingUser" data-testid="user-form-save" @click="handleSaveUser">
            保存
          </el-button>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="userImportDialogVisible" title="导入用户" width="min(640px, 92vw)" destroy-on-close>
      <div class="import-dialog">
        <div class="import-template-row">
          <span>CSV 使用部门名称和角色编码，单次最多 1000 行、2MB，重复用户名会跳过。</span>
          <el-button :icon="Download" :loading="downloadingTemplate" @click="handleDownloadImportTemplate">下载模板</el-button>
        </div>
        <el-upload
          ref="importUploadRef"
          drag
          accept=".csv,text/csv"
          :auto-upload="false"
          :limit="1"
          :on-change="handleImportFileChange"
          :on-remove="handleImportFileRemove"
          :on-exceed="handleImportFileExceed"
        >
          <el-icon class="el-icon--upload"><Upload /></el-icon>
          <div class="el-upload__text">拖入 CSV 文件，或点击选择</div>
        </el-upload>

        <el-alert
          v-if="importResult"
          :type="importResult.failedRows > 0 ? 'warning' : 'success'"
          :closable="false"
          :title="`共 ${importResult.totalRows} 行，成功 ${importResult.successRows} 行，失败 ${importResult.failedRows} 行`"
        />
        <el-table v-if="importResult?.errors.length" :data="importResult.errors" max-height="220" class="admin-table import-error-table">
          <el-table-column prop="rowNumber" label="行号" width="80" />
          <el-table-column prop="userName" label="用户名" min-width="130" />
          <el-table-column prop="message" label="失败原因" min-width="240" show-overflow-tooltip />
        </el-table>
      </div>
      <template #footer>
        <div class="drawer-footer">
          <el-button @click="userImportDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="importingUsers" :disabled="!importFile" @click="handleImportUsers">上传并导入</el-button>
        </div>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type UploadFile, type UploadInstance } from 'element-plus'
import { Delete, Download, EditPen, Key, MoreFilled, Plus, Search, Upload } from '@element-plus/icons-vue'
import {
  batchDeleteUsers,
  deleteUser,
  downloadUserImportTemplate,
  exportUsers,
  getUser,
  importUsers,
  listAssignableRoles,
  listUserAssignees,
  pageUsers,
  resetUserPassword,
  saveUser,
  switchUserStatus,
  treeDepartments
} from '@/features/system/api'
import type { EntityId, SystemDepartment, SystemRole, SystemUser, SystemUserOption, SystemUserPageQuery, UserImportResult } from '@/features/system/types'
import { useAuthStore } from '@/stores/auth'
import { PermissionCodes } from '@/permissions/codes'
import TableToolbar from '@/components/table/TableToolbar.vue'
import EnableStatusSwitch from '@/components/table/EnableStatusSwitch.vue'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import { createTableColumnState, visibleColumnMap } from '@/components/table/tableColumns'
import { useFluidTableHeight } from '@/components/table/useFluidTableHeight'
import { downloadBlob } from '@/utils/download'

const query = reactive<Required<Pick<SystemUserPageQuery, 'page' | 'limit'>> & Omit<SystemUserPageQuery, 'page' | 'limit'>>({
  page: 1,
  limit: 10,
  keyWord: '',
  deptId: undefined,
  enable: undefined
})
const users = ref<SystemUser[]>([])
const total = ref(0)
const loading = ref(false)
const tablePanelRef = ref<HTMLElement>()
const userDrawerVisible = ref(false)
const savingUser = ref(false)
const loadingUserDetailId = ref<EntityId>()
const deletingUserId = ref<EntityId>()
const resettingPasswordUserId = ref<EntityId>()
const batchDeletingUsers = ref(false)
const switchingUserId = ref<EntityId>()
const loadingRoleOptions = ref(false)
const selectedUsers = ref<SystemUser[]>([])
const userImportDialogVisible = ref(false)
const downloadingTemplate = ref(false)
const importingUsers = ref(false)
const exportingUsers = ref(false)
const importFile = ref<File>()
const importResult = ref<UserImportResult>()
const importUploadRef = ref<UploadInstance>()
const isCompactUserTable = ref(false)
const columns = ref(createTableColumnState([
  { key: 'user', label: '用户', required: true },
  { key: 'org', label: '组织岗位' },
  { key: 'approval', label: '直接上级' },
  { key: 'roles', label: '角色' },
  { key: 'contact', label: '联系方式' },
  { key: 'status', label: '状态' }
]))
const userFormRef = ref<FormInstance>()
const userForm = reactive<Partial<SystemUser>>({
  enable: 1,
  roleIds: []
})
const roleOptions = ref<SystemRole[]>([])
const assigneeOptions = ref<SystemUserOption[]>([])
const departmentTree = ref<SystemDepartment[]>([])
const auth = useAuthStore()
const { tableHeight, updateTableHeight } = useFluidTableHeight(tablePanelRef)
const canEditUser = computed(() => auth.hasAnyPermission([PermissionCodes.system.user.edit]))
const canResetUserPassword = computed(() => auth.hasAnyPermission([PermissionCodes.system.user.resetPassword]))
const visibleColumns = computed(() => visibleColumnMap(columns.value))
const canDeleteUser = computed(() => auth.hasAnyPermission([PermissionCodes.system.user.delete]))
const canReadDepartments = computed(() => auth.hasAnyPermission([PermissionCodes.system.dept.list]))
const displayColumns = computed(() => {
  if (!isCompactUserTable.value) return visibleColumns.value
  return {
    ...visibleColumns.value,
    org: false,
    approval: false,
    roles: false,
    contact: false,
    status: false
  }
})
const userDrawerTitle = computed(() => (userForm.userId ? '编辑用户' : '新增用户'))
const deletableSelectedUsers = computed(() => selectedUsers.value.filter((user) => isUserSelectable(user)))
const departmentOptions = computed(() =>
  flattenDepartments(departmentTree.value).map((item) => ({
    ...item,
    label: item.pid ? `  ${item.deptName}` : item.deptName
  }))
)
const roleNameById = computed(() => new Map(roleOptions.value.map((role) => [String(role.roleId), role.roleName])))
const assigneeNameById = computed(() => new Map(assigneeOptions.value.map((user) => [String(user.value), user.name])))
const deptNameById = computed(() => new Map(flattenDepartments(departmentTree.value).map((dept) => [String(dept.deptId), dept.deptName])))
const managerOptions = computed(() => assigneeOptions.value.filter((assignee) => !sameId(assignee.value, userForm.userId)))
const departmentTreeProps = {
  label: 'deptName',
  value: 'deptId',
  children: 'children'
}
const userFormRules: FormRules = {
  userName: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  nickName: [{ required: true, message: '请输入姓名/昵称', trigger: 'blur' }],
  deptId: [{ required: true, message: '请选择部门', trigger: 'change' }],
  roleIds: [{ type: 'array', required: true, min: 1, message: '请选择角色', trigger: 'change' }],
  phone: [
    {
      pattern: /^1[3-9]\d{9}$/,
      message: '请输入有效的手机号',
      trigger: 'blur'
    }
  ],
  email: [{ type: 'email', message: '请输入有效的邮箱', trigger: 'blur' }]
}
let compactUserTableQuery: MediaQueryList | undefined

function flattenDepartments(items: SystemDepartment[]): SystemDepartment[] {
  return items.flatMap((item) => [item, ...flattenDepartments(item.children || [])])
}

function handleCompactUserTableChange(event: MediaQueryListEvent) {
  isCompactUserTable.value = event.matches
}

function setupCompactUserTableWatcher() {
  if (typeof window === 'undefined') return
  compactUserTableQuery = window.matchMedia('(max-width: 640px)')
  isCompactUserTable.value = compactUserTableQuery.matches
  compactUserTableQuery.addEventListener('change', handleCompactUserTableChange)
}

async function loadUsers() {
  loading.value = true
  try {
    const result = await pageUsers(query)
    users.value = result.list.map(enrichUserRelations)
    total.value = result.total
    selectedUsers.value = []
  } finally {
    loading.value = false
    updateTableHeight()
  }
}

async function loadFormOptions(userId?: EntityId) {
  loadingRoleOptions.value = true
  const [rolesResult, assigneeResult] = await Promise.all([
    listAssignableRoles(userId).catch(() => [] as SystemRole[]),
    listUserAssignees(userId).catch(() => [] as SystemUserOption[])
  ])
  roleOptions.value = rolesResult
  assigneeOptions.value = assigneeResult
  loadingRoleOptions.value = false
  if (canReadDepartments.value) {
    departmentTree.value = await treeDepartments().catch(() => [] as SystemDepartment[])
  } else {
    departmentTree.value = []
  }
  users.value = users.value.map(enrichUserRelations)
}

function resetUserForm(user?: SystemUser) {
  Object.keys(userForm).forEach((key) => {
    delete userForm[key as keyof SystemUser]
  })
  Object.assign(userForm, {
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
    roleIds: normalizeRoleIds(user?.roleIds),
    phone: user?.phone || '',
    email: user?.email || '',
    enable: user?.enable ?? 1
  })
  ensureAssigneeOption(user?.managerUserId, user?.managerName)
  userFormRef.value?.clearValidate()
}

async function openUserDrawer(user?: SystemUser) {
  if (!user?.userId) {
    await loadFormOptions()
    resetUserForm()
    userDrawerVisible.value = true
    return
  }
  loadingUserDetailId.value = user.userId
  try {
    const [detail] = await Promise.all([
      getUser(user.userId),
      loadFormOptions(user.userId)
    ])
    resetUserForm({
      ...user,
      ...detail,
      deptName: detail.deptName ?? user.deptName,
      managerName: detail.managerName ?? user.managerName,
      departmentLeaderName: detail.departmentLeaderName ?? user.departmentLeaderName,
      upperDepartmentLeaderName: detail.upperDepartmentLeaderName ?? user.upperDepartmentLeaderName,
      roleIds: detail.roleIds ?? user.roleIds,
      roleNames: detail.roleNames ?? user.roleNames
    })
    userDrawerVisible.value = true
  } catch {
    ElMessage.error('用户详情加载失败，请稍后重试')
  } finally {
    loadingUserDetailId.value = undefined
  }
}

async function handleSaveUser() {
  const valid = await userFormRef.value?.validate().catch(() => false)
  if (!valid) return
  savingUser.value = true
  try {
    await saveUser(userForm)
    ElMessage.success('用户已保存')
    userDrawerVisible.value = false
    await loadUsers()
  } finally {
    savingUser.value = false
  }
}

function openUserImportDialog() {
  importFile.value = undefined
  importResult.value = undefined
  importUploadRef.value?.clearFiles()
  userImportDialogVisible.value = true
}

async function handleDownloadImportTemplate() {
  downloadingTemplate.value = true
  try {
    const file = await downloadUserImportTemplate()
    downloadBlob(file.blob, file.fileName)
  } finally {
    downloadingTemplate.value = false
  }
}

function handleImportFileChange(uploadFile: UploadFile) {
  if (!uploadFile.raw) {
    return
  }
  const fileName = uploadFile.raw.name.toLowerCase()
  if (!fileName.endsWith('.csv')) {
    ElMessage.warning('请选择 CSV 文件')
    importUploadRef.value?.clearFiles()
    importFile.value = undefined
    return
  }
  if (uploadFile.raw.size > 2 * 1024 * 1024) {
    ElMessage.warning('导入文件不能超过 2MB')
    importUploadRef.value?.clearFiles()
    importFile.value = undefined
    return
  }
  importFile.value = uploadFile.raw
  importResult.value = undefined
}

function handleImportFileRemove() {
  importFile.value = undefined
}

function handleImportFileExceed() {
  ElMessage.warning('一次只能上传一个 CSV 文件')
}

async function handleImportUsers() {
  if (!importFile.value) {
    ElMessage.warning('请选择要导入的 CSV 文件')
    return
  }
  importingUsers.value = true
  try {
    importResult.value = await importUsers(importFile.value)
    if (importResult.value.failedRows > 0) {
      ElMessage.warning('用户导入完成，存在失败行')
    } else {
      ElMessage.success('用户导入完成')
    }
    await loadUsers()
  } finally {
    importingUsers.value = false
  }
}

async function handleExportUsers() {
  exportingUsers.value = true
  try {
    const file = await exportUsers(query)
    downloadBlob(file.blob, file.fileName)
    ElMessage.success('用户导出已生成')
  } finally {
    exportingUsers.value = false
  }
}

async function handleDeleteUser(row: SystemUser) {
  if (isBuiltinAdmin(row)) {
    ElMessage.warning('系统管理员不可删除')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确认删除用户「${row.nickName || row.userName}」吗？删除后该账号将无法登录。`,
      '删除用户',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger',
        distinguishCancelAndClose: true
      }
    )
  } catch {
    return
  }

  deletingUserId.value = row.userId
  try {
    await deleteUser(row.userId)
    ElMessage.success('用户已删除')
    if (users.value.length === 1 && query.page > 1) {
      query.page -= 1
    }
    await loadUsers()
  } finally {
    deletingUserId.value = undefined
  }
}

async function handleResetPassword(row: SystemUser) {
  if (!canResetUserPassword.value) {
    ElMessage.warning('缺少重置密码权限')
    return
  }
  if (isBuiltinAdmin(row)) {
    ElMessage.warning('系统管理员密码不可在用户管理中重置')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认将用户「${row.nickName || row.userName}」的登录密码重置为系统默认密码吗？`,
      '重置密码',
      {
        type: 'warning',
        confirmButtonText: '重置密码',
        cancelButtonText: '取消',
        distinguishCancelAndClose: true
      }
    )
  } catch {
    return
  }

  resettingPasswordUserId.value = row.userId
  try {
    await resetUserPassword(row.userId)
    ElMessage.success('密码已重置为系统默认密码')
  } finally {
    resettingPasswordUserId.value = undefined
  }
}

function handleUserSelectionChange(selection: SystemUser[]) {
  selectedUsers.value = selection
}

function isUserSelectable(row: SystemUser) {
  return canDeleteUser.value && !isBuiltinAdmin(row)
}

async function handleBatchDeleteUsers() {
  const usersToDelete = deletableSelectedUsers.value
  if (!usersToDelete.length) {
    ElMessage.warning('请选择可删除的用户')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确认删除已选的 ${usersToDelete.length} 个用户吗？删除后这些账号将无法登录。`,
      '批量删除用户',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger',
        distinguishCancelAndClose: true
      }
    )
  } catch {
    return
  }

  batchDeletingUsers.value = true
  try {
    await batchDeleteUsers(usersToDelete.map((user) => user.userId))
    ElMessage.success('用户已批量删除')
    if (users.value.length === usersToDelete.length && query.page > 1) {
      query.page -= 1
    }
    await loadUsers()
  } finally {
    batchDeletingUsers.value = false
  }
}

async function handleToggleUserStatus(row: SystemUser) {
  if (!canEditUser.value) {
    ElMessage.warning('缺少用户编辑权限')
    return
  }
  if (isBuiltinAdmin(row)) {
    ElMessage.warning('系统管理员状态不可停用')
    return
  }

  const nextEnable: 0 | 1 = row.enable === 1 ? 0 : 1
  if (nextEnable === 0) {
    try {
      await ElMessageBox.confirm(`确认停用“${row.nickName || row.userName}”？停用后该账号将无法登录。`, '停用用户', {
        confirmButtonText: '停用',
        cancelButtonText: '取消',
        type: 'warning'
      })
    } catch {
      return
    }
  }
  switchingUserId.value = row.userId
  try {
    await switchUserStatus(row.userId, nextEnable)
    ElMessage.success(`用户已${nextEnable === 1 ? '启用' : '停用'}`)
    await loadUsers()
  } finally {
    switchingUserId.value = undefined
  }
}

function handleSearch() {
  query.page = 1
  loadUsers()
}

function resetFilters() {
  query.keyWord = ''
  query.deptId = undefined
  query.enable = undefined
  handleSearch()
}

function handleDepartmentFilterChange() {
  query.page = 1
  loadUsers()
}

function handleCurrentChange(page: number) {
  query.page = page
  loadUsers()
}

function handleSizeChange(limit: number) {
  query.limit = limit
  query.page = 1
  loadUsers()
}

function normalizeRoleIds(roleIds?: SystemUser['roleIds']) {
  if (Array.isArray(roleIds)) return roleIds.map((roleId) => String(roleId)).filter(Boolean)
  if (typeof roleIds === 'string') {
    return roleIds
      .split(',')
      .map((roleId) => roleId.trim())
      .filter(Boolean)
  }
  return []
}

function enrichUserRelations(user: SystemUser): SystemUser {
  const roleIds = normalizeRoleIds(user.roleIds)
  const backendRoleNames = Array.isArray(user.roleNames) ? user.roleNames.filter(Boolean) : []
  const optionRoleNames = roleIds.map((roleId) => roleNameById.value.get(String(roleId))).filter(Boolean) as string[]
  const roleNames = Array.from(new Set([...backendRoleNames, ...optionRoleNames]))
  return {
    ...user,
    roleIds,
    roleNames,
    deptName: user.deptName || (user.deptId ? deptNameById.value.get(String(user.deptId)) : undefined),
    managerName: user.managerName || userNameOf(user.managerUserId),
    departmentLeaderName: user.departmentLeaderName || userNameOf(user.departmentLeaderUserId),
    upperDepartmentLeaderName: user.upperDepartmentLeaderName || userNameOf(user.upperDepartmentLeaderUserId)
  }
}

function displayRoleNames(row: SystemUser) {
  if (Array.isArray(row.roleNames) && row.roleNames.length > 0) {
    return row.roleNames
  }
  return normalizeRoleIds(row.roleIds)
    .map((roleId) => roleNameById.value.get(String(roleId)) || '')
    .filter(Boolean)
}

function compactUserMeta(row: SystemUser) {
  const roleText = displayRoleNames(row).join(' / ')
  const relationText = row.managerName ? `上级 ${compactPersonName(row.managerName)}` : ''
  const parts = [row.deptName, row.positionName, roleText, relationText, row.phone].filter(Boolean)
  return parts.length ? parts.join(' · ') : '未补充组织信息'
}

function userNameOf(userId?: EntityId) {
  return userId ? assigneeNameById.value.get(String(userId)) : undefined
}

function userManagerName(row: SystemUser) {
  return row.managerName || userNameOf(row.managerUserId) || '未设置'
}

function compactPersonName(name?: string) {
  return (name || '未设置').replace(/（[^）]+）/g, '').replace(/\([^)]*\)/g, '').trim() || '未设置'
}

function ensureAssigneeOption(userId?: EntityId, name?: string) {
  if (!userId || !name || assigneeOptions.value.some((item) => sameId(item.value, userId))) {
    return
  }
  assigneeOptions.value = [
    ...assigneeOptions.value,
    {
      value: userId,
      name
    }
  ]
}

function sameId(left?: EntityId, right?: EntityId) {
  return left !== undefined && left !== null && right !== undefined && right !== null && String(left) === String(right)
}

function isBuiltinAdmin(row: SystemUser) {
  return row.userName === 'admin' || row.roleNames?.includes('超级管理员')
}

function userStatusDisabledReason(row: SystemUser) {
  if (!canEditUser.value) return '缺少用户编辑权限'
  if (isBuiltinAdmin(row)) return '系统管理员不可停用'
  return ''
}

onMounted(async () => {
  setupCompactUserTableWatcher()
  await loadUsers()
  await loadFormOptions()
})

onBeforeUnmount(() => {
  compactUserTableQuery?.removeEventListener('change', handleCompactUserTableChange)
})
</script>

<style scoped>
.user-filter-bar {
  align-items: flex-end;
  justify-content: flex-start;
  gap: 12px;
  padding: 0;
}

.user-filter-bar :deep(.el-form-item) {
  margin-bottom: 0;
}

.keyword-filter {
  flex: 0 1 320px;
  min-width: min(280px, 100%);
}

.keyword-filter :deep(.el-form-item__content),
.keyword-filter :deep(.el-input) {
  width: 100%;
}

.keyword-filter :deep(.el-input) {
  max-width: none;
}

.department-filter {
  flex: 0 0 244px;
}

.department-filter :deep(.el-tree-select),
.department-filter :deep(.el-select) {
  width: 180px !important;
}

.department-filter :deep(.el-select__wrapper) {
  padding-left: 14px;
  padding-right: 36px;
}

.department-filter :deep(.el-select__input),
.department-filter :deep(.el-select__input:focus),
.department-filter :deep(.el-select__input:focus-visible) {
  outline: none;
}

.department-option {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  max-width: 100%;
}

.department-option span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-filter {
  flex: 0 0 152px;
}

.status-filter :deep(.el-select) {
  width: 120px !important;
}

.filter-actions {
  flex: 0 0 auto;
  margin-left: 0;
}

.filter-actions :deep(.el-form-item__content) {
  flex-wrap: nowrap;
}

.user-table {
  --el-table-fixed-right-column: inset -5px 0 7px -7px rgba(15, 23, 42, 0.05);
}

.user-role-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  min-width: 0;
}

.user-role-tag {
  max-width: 132px;
}

.user-role-tag :deep(.el-tag__content) {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-org-cell,
.user-contact-cell {
  display: grid;
  gap: 4px;
  min-width: 0;
  line-height: 1.45;
}

.user-org-cell strong,
.user-contact-cell span {
  min-width: 0;
  overflow: hidden;
  color: #334155;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-org-cell small,
.user-contact-cell small {
  min-width: 0;
  overflow: hidden;
  color: #64748b;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.direct-manager-cell {
  display: flex;
  align-items: center;
  min-width: 0;
}

.direct-manager-cell strong {
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
  color: #1f2937;
  font-size: 13px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account-status-control {
  display: flex;
  align-items: center;
  gap: 14px;
  width: 100%;
  min-height: 48px;
}

.account-status-copy {
  display: grid;
  gap: 2px;
  min-width: 0;
  color: #64748b;
  line-height: 1.45;
}

.account-status-copy strong {
  color: #0f172a;
  font-size: 13px;
  font-weight: 700;
}

.account-status-copy span {
  font-size: 12px;
}

.user-status-switch :deep(.enable-status-switch__button) {
  min-height: 28px;
  padding: 2px 4px;
  font-size: 12px;
  font-weight: 600;
}

.user-status-switch :deep(.enable-status-switch__track) {
  width: 34px;
  height: 20px;
  flex-basis: 34px;
}

.user-status-switch :deep(.enable-status-switch__thumb) {
  width: 12px;
  height: 12px;
}

.user-status-switch :deep(.enable-status-switch__button.is-active .enable-status-switch__thumb) {
  transform: translate(16px, -50%);
}

.user-status-switch :deep(.enable-status-switch__label) {
  min-width: 24px;
  color: #475569;
  font-weight: 600;
}

.user-compact-meta {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-compact-status {
  display: inline-flex;
  margin-top: 7px;
}

.user-row-actions {
  justify-content: center;
  gap: 4px;
}

.user-row-actions :deep(.el-button.is-text) {
  min-height: 28px;
  padding-right: 5px;
  padding-left: 5px;
  font-weight: 600;
}

.user-row-actions :deep(.user-more-button.el-button) {
  --el-button-text-color: #64748b;
  --el-button-hover-text-color: var(--ea-primary);
  --el-button-active-text-color: #64748b;
  color: #64748b;
}

.user-row-actions :deep(.user-more-button.el-button .el-icon),
.user-row-actions :deep(.user-more-button.el-button span) {
  color: inherit;
}

.user-row-actions :deep(.user-more-button.el-button:hover) {
  color: var(--ea-primary);
}

.user-row-actions :deep(.user-more-button.el-button:focus:not(:hover)),
.user-row-actions :deep(.user-more-button.el-button:active:not(:hover)) {
  color: #64748b;
}

.user-row-more {
  display: inline-flex;
}

.import-dialog {
  display: grid;
  gap: 16px;
}

.import-template-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  color: var(--el-text-color-secondary);
}

.import-error-table {
  margin-top: 0;
}

@media (max-width: 900px) {
  .user-filter-bar {
    align-items: stretch;
  }

  .keyword-filter,
  .department-filter,
  .status-filter,
  .filter-actions {
    flex: 1 1 100%;
    width: 100%;
  }

  .keyword-filter :deep(.el-input),
  .department-filter :deep(.el-tree-select),
  .department-filter :deep(.el-select),
  .status-filter :deep(.el-select) {
    max-width: none;
    width: 100% !important;
  }

  .filter-actions {
    margin-left: 0;
  }

  .filter-actions :deep(.el-form-item__content) {
    justify-content: flex-start;
  }
}

@media (max-width: 640px) {
  .user-table :deep(.entity-cell) {
    align-items: flex-start;
  }

  .user-table :deep(.entity-cell > span) {
    min-width: 0;
  }

  .user-row-actions {
    gap: 0;
  }

  .user-row-actions :deep(.el-button.is-text) {
    padding-right: 3px;
    padding-left: 3px;
  }
}

:global(.user-department-select-popper) {
  min-width: 240px !important;
}

:global(.user-department-select-popper .el-select-dropdown__wrap) {
  max-height: 320px;
}

:global(.user-department-select-popper .el-tree) {
  padding: 6px;
}

:global(.user-department-select-popper .el-tree-node__content) {
  height: 38px;
  border-radius: 8px;
  padding-right: 10px;
}

:global(.user-department-select-popper .el-tree-node__content:hover) {
  background: var(--el-fill-color-light);
}

:global(.user-department-select-popper .el-tree-node.is-current > .el-tree-node__content) {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-weight: 700;
}

:global(.user-row-action-menu .el-dropdown-menu__item) {
  gap: 8px;
  min-width: 132px;
  font-weight: 600;
}

:global(.user-row-action-menu .el-dropdown-menu__item .el-icon) {
  margin-right: 0;
}

:global(.user-row-action-menu .el-dropdown-menu__item.is-danger:not(.is-disabled)) {
  color: #dc2626;
}
</style>

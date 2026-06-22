<template>
  <section class="resource-page">
    <div class="resource-hero">
      <div>
        <h1>角色权限</h1>
        <p>按角色维护页面、按钮和数据范围授权。</p>
      </div>
      <div class="resource-actions">
        <el-button v-permission="PermissionCodes.system.role.edit" type="primary" :icon="Plus" @click="openRoleDrawer()">新增角色</el-button>
      </div>
    </div>

    <section ref="tablePanelRef" class="surface resource-panel is-fluid-table">
      <div class="table-control-row role-table-controls">
        <el-form :inline="true" class="filter-bar role-filter-bar">
          <el-form-item label="关键词">
            <el-input v-model="query.keyword" placeholder="角色名称 / 编码" clearable @keyup.enter="handleSearch" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.enable" placeholder="全部" clearable style="width: 120px">
              <el-option label="启用" :value="true" />
              <el-option label="停用" :value="false" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" plain :icon="Search" @click="handleSearch">查询</el-button>
            <el-button @click="resetRoleFilters">重置</el-button>
          </el-form-item>
        </el-form>
        <TableToolbar
          v-model:columns="columns"
          class="table-toolbar-inline"
        />
      </div>

      <el-table v-loading="loading" :data="roles" row-key="roleId" :height="tableHeight" class="admin-table role-table" empty-text="暂无角色数据">
        <el-table-column v-if="visibleColumns.role" prop="roleName" label="角色" min-width="180">
          <template #default="{ row }">
            <div class="entity-cell">
              <span class="entity-avatar is-role">{{ row.roleName.slice(0, 1) }}</span>
              <span>
                <strong>{{ row.roleName }}</strong>
                <small>{{ row.roleCode }}</small>
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="visibleColumns.details" prop="details" label="说明" min-width="210" show-overflow-tooltip />
        <el-table-column v-if="visibleColumns.userCount" prop="userCount" label="用户数" width="84" align="center" header-align="center">
          <template #default="{ row }">
            <span class="role-user-count">{{ row.userCount || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="visibleColumns.dataScope" prop="dataScope" label="数据范围" width="140">
          <template #default="{ row }">
            <el-tag :class="roleDataScopeClass(row.dataScope)" effect="plain" disable-transitions>
              {{ roleDataScopeLabel(row.dataScope) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="visibleColumns.status" label="状态" width="112" fixed="right" align="center" header-align="center">
          <template #default="{ row }">
            <EnableStatusSwitch
              class="role-status-switch"
              :model-value="row.enable"
              :loading="switchingRoleId === row.roleId"
              :disabled="!canEditRole || row.roleCode === 'admin'"
              :disabled-reason="roleStatusDisabledReason(row)"
              :target-name="row.roleName"
              @toggle="handleToggleRoleStatus(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="176" fixed="right" align="center" header-align="center">
          <template #default="{ row }">
            <div class="row-actions role-row-actions">
              <el-button
                v-permission:disable="{ permissions: PermissionCodes.system.role.edit, reason: '缺少角色授权配置权限' }"
                class="role-authorize-button"
                text
                type="primary"
                :icon="Key"
                @click="openAuthorizationDrawer(row)"
              >
                授权
              </el-button>
              <el-dropdown v-if="canEditRole" class="role-row-more" trigger="click" placement="bottom-end">
                <el-button class="role-more-button" text :icon="MoreFilled" title="更多操作" aria-label="更多操作" @click.stop>
                  更多
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu class="role-row-action-menu">
                    <el-dropdown-item :data-testid="`role-edit-menu-${row.roleId}`" @click.stop="openRoleDrawer(row)">
                      <el-icon><EditPen /></el-icon>
                      <span>编辑资料</span>
                    </el-dropdown-item>
                    <el-dropdown-item class="is-danger" :data-testid="`role-delete-menu-${row.roleId}`" @click.stop="handleDeleteRole(row)">
                      <el-icon><Delete /></el-icon>
                      <span>删除角色</span>
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer">
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

    <el-drawer v-model="roleDrawerVisible" :title="roleDrawerTitle" size="min(520px, 92vw)">
      <el-form label-position="top" class="drawer-form">
        <el-form-item label="角色名称">
          <el-input v-model="roleForm.roleName" placeholder="例如 部门负责人" />
        </el-form-item>
        <el-form-item label="权限编码">
          <el-input v-model="roleForm.roleCode" placeholder="例如 dept_manager" />
        </el-form-item>
        <el-form-item label="角色层级">
          <el-input-number v-model="roleForm.roleLevel" :min="1" :max="99" :step="1" controls-position="right" />
          <p class="form-help-text">数值越小权限越高；普通业务角色建议使用 20-99。</p>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="roleForm.enable" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="roleForm.details" type="textarea" :rows="4" placeholder="描述该角色的业务边界" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="drawer-footer">
          <el-button @click="roleDrawerVisible = false">取消</el-button>
          <el-button :loading="savingRole" @click="handleSaveRole()">保存</el-button>
          <el-button type="primary" :loading="savingRole" @click="handleSaveRole(true)">保存并配置授权</el-button>
        </div>
      </template>
    </el-drawer>

    <el-drawer
      v-model="permissionDrawerVisible"
      :title="permissionDrawerTitle"
      size="min(760px, 92vw)"
      class="permission-drawer"
      data-testid="role-permission-drawer"
    >
      <div v-loading="permissionLoading" class="permission-body">
        <div class="permission-summary">
          <div>
            <strong>{{ currentRole?.roleName }}</strong>
            <span>{{ currentRole?.roleCode }} · {{ roleDataScopeLabel(authorizationForm.dataScope) }}</span>
          </div>
          <div class="permission-summary-tags">
            <el-tag
              v-for="permissionTab in permissionTabs"
              :key="permissionTab.type"
              effect="plain"
              :type="permissionTab.tagType"
            >
              <template v-if="permissionTab.type === 'scope'">数据范围 {{ roleDataScopeLabel(authorizationForm.dataScope) }}</template>
              <template v-else>{{ permissionTab.label }} {{ permissionTab.checked }}/{{ permissionTab.total }}</template>
            </el-tag>
          </div>
        </div>

        <el-alert
          :type="activePermissionMeta.alertType"
          show-icon
          :closable="false"
          :title="activePermissionMeta.hint"
        />

        <div class="permission-guide" aria-label="角色授权流程">
          <button
            v-for="permissionTab in authorizationSteps"
            :key="permissionTab.type"
            type="button"
            :class="[
              'permission-guide-item',
              {
                'is-active': activePermissionTab === permissionTab.type,
                'is-data': permissionTab.type === 'scope'
              }
            ]"
            @click="activatePermissionTab(permissionTab.type)"
          >
            <span>{{ permissionTab.step }}</span>
            <strong>{{ permissionTab.label }}</strong>
            <small>{{ permissionTab.summary }}</small>
          </button>
        </div>

        <section v-if="activePermissionTab === 'scope'" class="permission-data-panel">
          <div class="permission-data-head">
            <div>
              <strong>数据范围</strong>
              <span>选择角色能查看的数据范围。</span>
            </div>
          </div>

          <div class="permission-data-cards is-single">
            <div class="permission-data-card is-control">
              <span>数据范围策略</span>
              <el-select v-model="authorizationForm.dataScope" class="permission-data-scope-select" placeholder="请选择">
                <el-option
                  v-for="option in roleDataScopeOptions"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                  :disabled="!canAssignDataScope(option.value)"
                >
                  <div class="data-scope-option">
                    <span>{{ option.label }}</span>
                    <el-tag v-if="!canAssignDataScope(option.value)" size="small" type="info" effect="plain">不可授予</el-tag>
                    <el-tag v-else-if="option.risk !== 'normal'" size="small" :type="option.riskTagType" effect="plain">
                      {{ option.riskLabel }}
                    </el-tag>
                  </div>
                </el-option>
              </el-select>
              <small>{{ dataScopeDescription }}</small>
              <el-alert
                v-if="authorizationBlockedReason"
                class="permission-data-alert"
                type="warning"
                show-icon
                :closable="false"
                :title="authorizationBlockedReason"
              />
              <el-alert
                v-else-if="dataScopeRiskAlert"
                class="permission-data-alert"
                :type="dataScopeRiskAlert.type"
                show-icon
                :closable="false"
                :title="dataScopeRiskAlert.title"
              />
              <div class="permission-data-impact">
                <span>影响用户 <strong>{{ authorizationRoleUserCount }}</strong></span>
                <span>当前范围 <strong>{{ selectedDataScopeLabel }}</strong></span>
                <span v-if="authorizationForm.dataScope === customDepartmentDataScope">部门 <strong>{{ authorizationForm.deptIds.length }}</strong></span>
              </div>
            </div>
            <div v-if="authorizationForm.dataScope === customDepartmentDataScope" class="permission-data-card is-dept-tree">
              <div class="permission-data-tree-head">
                <span>自定义部门范围</span>
                <strong>已选 {{ authorizationForm.deptIds.length }} 个部门</strong>
              </div>
              <el-input
                v-model="departmentFilterText"
                class="permission-data-tree-search"
                placeholder="搜索部门"
                clearable
                :prefix-icon="Search"
              />
              <el-tree
                ref="roleDeptTreeRef"
                v-loading="departmentLoading"
                :data="departmentTree"
                node-key="deptId"
                show-checkbox
                check-strictly
                default-expand-all
                :props="departmentTreeProps"
                :filter-node-method="filterDepartmentNode"
                empty-text="暂无可选部门"
                @check="syncCheckedDeptIds"
              />
              <div v-if="selectedDepartments.length" class="permission-selected-depts">
                <div class="permission-selected-depts-head">
                  <span>已选部门</span>
                  <el-button type="primary" link @click="clearSelectedDepartments">清空</el-button>
                </div>
                <div class="permission-selected-dept-tags">
                  <el-tag
                    v-for="dept in visibleSelectedDepartments"
                    :key="dept.deptId"
                    closable
                    effect="plain"
                    @close="removeSelectedDepartment(dept.deptId)"
                  >
                    {{ dept.deptName }}
                  </el-tag>
                  <el-tag v-if="hiddenSelectedDepartmentCount > 0" type="info" effect="plain">
                    还有 {{ hiddenSelectedDepartmentCount }} 个
                  </el-tag>
                </div>
              </div>
              <small>精确选择该角色可以查看的组织范围；未选择部门时不会授予跨部门数据。</small>
            </div>
          </div>
        </section>

        <div v-else-if="activePermissionTab === 'menu' && permissionMenuSections.length" class="permission-menu-tree">
          <section v-for="section in permissionMenuSections" :key="section.key" class="permission-menu-section">
            <div class="permission-menu-section-head">
              <el-checkbox
                :model-value="isGroupChecked(section.menuCodes)"
                :indeterminate="isGroupIndeterminate(section.menuCodes)"
                @change="toggleGroup(section.menuCodes, $event)"
              >
                <span class="permission-menu-section-title">
                  <strong>{{ section.featureName }}</strong>
                  <small>已选 {{ checkedCount(section.menuCodes) }}/{{ section.menuCodes.length }} 个菜单</small>
                </span>
              </el-checkbox>
              <el-tag effect="plain" type="primary">菜单分组</el-tag>
            </div>
            <div class="permission-menu-list">
              <div
                v-for="node in section.nodes"
                :key="node.key"
                role="button"
                tabindex="0"
                :class="['permission-menu-row', { 'is-checked': node.menu && isPermissionChecked(node.menu.code) }]"
                @click="toggleMenuNode(node)"
                @keydown.enter.prevent="toggleMenuNode(node)"
                @keydown.space.prevent="toggleMenuNode(node)"
              >
                <el-checkbox
                  v-if="node.menu"
                  :model-value="isPermissionChecked(node.menu.code)"
                  @click.stop
                  @change="togglePermissionCode(node.menu.code, $event)"
                />
                <span class="permission-menu-branch" aria-hidden="true"></span>
                <span class="permission-menu-content">
                  <strong>{{ node.groupName }}</strong>
                </span>
              </div>
            </div>
          </section>
        </div>

        <div v-else-if="activePermissionTreeNodes.length" class="permission-resource-tree">
          <section v-for="node in activePermissionTreeNodes" :key="node.key" class="permission-tree-node">
            <div class="permission-tree-parent">
              <template v-if="activePermissionTab === 'menu' && node.menu">
                <el-checkbox
                  :model-value="isPermissionChecked(node.menu.code)"
                  @change="togglePermissionCode(node.menu.code, $event)"
                >
                  <span class="permission-tree-parent-label">
                    <strong>{{ node.featureName }} / {{ node.groupName }}</strong>
                  </span>
                </el-checkbox>
              </template>
              <template v-else>
                <div>
                  <strong>{{ node.groupName }}</strong>
                  <span>已选 {{ checkedCount(node.actionCodes) }}/{{ node.actionCodes.length }} 个按钮权限</span>
                </div>
                <el-checkbox
                  :model-value="isGroupChecked(node.actionCodes)"
                  :indeterminate="isGroupIndeterminate(node.actionCodes)"
                  :disabled="node.actions.length === 0"
                  @change="toggleGroup(node.actionCodes, $event)"
                >
                  全选
                </el-checkbox>
              </template>
            </div>
            <div v-if="activePermissionTab === 'button' && node.actions.length" class="permission-tree-children is-simple">
              <label v-for="permission in node.actions" :key="permission.code" class="permission-tree-child is-simple">
                <el-checkbox
                  :model-value="isPermissionChecked(permission.code)"
                  @change="togglePermissionCode(permission.code, $event)"
                />
                <span class="permission-tree-child-body">
                  <strong>{{ permission.name }}</strong>
                </span>
              </label>
            </div>
            <el-empty
              v-else-if="activePermissionTab === 'button'"
              class="permission-empty is-compact"
              description="这个菜单暂未声明按钮权限。"
            />
          </section>
        </div>

        <el-empty
          v-else
          class="permission-empty"
          :description="activeEmptyDescription"
        />
      </div>
      <template #footer>
        <div class="drawer-footer">
          <el-button @click="permissionDrawerVisible = false">取消</el-button>
          <el-button v-if="activePermissionTab !== 'menu'" @click="goPreviousAuthorizationStep">上一步</el-button>
          <el-button v-if="activePermissionTab !== 'scope'" @click="goNextAuthorizationStep">下一步</el-button>
          <el-button type="primary" :loading="savingPermissions" :disabled="!!authorizationBlockedReason" @click="handleSaveAuthorization">
            保存授权配置
          </el-button>
        </div>
      </template>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, EditPen, Key, MoreFilled, Plus, Search } from '@element-plus/icons-vue'
import {
  deleteRole,
  getRolePermissions,
  listRolePermissionResources,
  pageRoles,
  saveRole,
  saveRoleAuthorization
} from '@/features/system/roleApi'
import { treeDepartments } from '@/features/system/departmentApi'
import type { EntityId, RoleDataScope, SystemDepartment, SystemMenu, SystemRole, SystemRolePageQuery } from '@/features/system/types'
import {
  buildRolePermissionMenuSections,
  flattenRolePermissionMenuNodes,
  type RolePermission as NormalizedPermission,
  type RolePermissionMenuNode as PermissionMenuNode,
  type RolePermissionMenuSection as PermissionMenuSection,
  type RolePermissionType as PermissionType
} from '@/features/system/roleAuthorization'
import {
  customDepartmentDataScope,
  defaultRoleDataScope,
  normalizeAssignableDataScopes,
  normalizeRoleDataScope,
  roleDataScopeClass,
  roleDataScopeLabel,
  roleDataScopeOptionMap,
  roleDataScopeOptions,
  roleDataScopeRiskAlert as resolveRoleDataScopeRiskAlert
} from '@/features/system/dataScope'
import {
  buildRoleAuthorizationSteps,
  buildRoleForm,
  buildRolePermissionTabs,
  collectDepartmentMap,
  filterRoleDepartmentNode,
  normalizeRolePermissionCodes,
  permissionGroupKey,
  roleStatusDisabledReason as resolveRoleStatusDisabledReason,
  sameRoleEntityId as sameId,
  type RoleAuthorizationStep,
  type RolePermissionPresentationMeta
} from '@/features/system/rolePresentation'
import { useAuthStore } from '@/stores/auth'
import { PermissionCodes } from '@/permissions/codes'
import TableToolbar from '@/components/table/TableToolbar.vue'
import EnableStatusSwitch from '@/components/table/EnableStatusSwitch.vue'
import { createTableColumnState, visibleColumnMap } from '@/components/table/tableColumns'
import { useFluidTableHeight } from '@/components/table/useFluidTableHeight'

type AuthorizationStep = RoleAuthorizationStep

interface PermissionGroup {
  key: string
  type: PermissionType
  featureId: string
  featureName: string
  groupName: string
  permissions: NormalizedPermission[]
  permissionCodes: string[]
}

const permissionTypeConfig: Record<AuthorizationStep, RolePermissionPresentationMeta> = {
  menu: {
    label: '菜单权限',
    step: '1',
    summary: '能看到哪些菜单',
    hint: '先勾菜单权限：菜单决定侧边栏和路由入口，下一步再配置对应按钮权限。',
    resourceLabel: '菜单',
    countUnit: '个菜单',
    alertType: 'info',
    tagType: 'primary'
  },
  button: {
    label: '按钮权限',
    step: '2',
    summary: '基于已选菜单显示',
    hint: '只配置已选菜单下可点击的按钮权限。',
    resourceLabel: '按钮权限',
    countUnit: '个按钮权限',
    alertType: 'warning',
    tagType: 'warning'
  },
  scope: {
    label: '数据范围',
    step: '3',
    summary: '角色全局范围策略',
    hint: '数据范围独立于菜单和按钮；后端代码注解决定哪些查询应用该范围。',
    resourceLabel: '数据范围',
    countUnit: '个范围策略',
    alertType: 'success',
    tagType: 'success'
  }
}
const permissionTabTypes: AuthorizationStep[] = ['menu', 'button', 'scope']
const authorizationStepTypes: AuthorizationStep[] = ['menu', 'button', 'scope']
const departmentTreeProps = {
  label: 'deptName',
  children: 'children'
}
const query = reactive<Required<Pick<SystemRolePageQuery, 'page' | 'limit'>> & Omit<SystemRolePageQuery, 'page' | 'limit'>>({
  page: 1,
  limit: 10,
  keyword: '',
  enable: undefined
})
const roles = ref<SystemRole[]>([])
const total = ref(0)
const loading = ref(false)
const tablePanelRef = ref<HTMLElement>()
const { tableHeight, updateTableHeight } = useFluidTableHeight(tablePanelRef)
const columns = ref(createTableColumnState([
  { key: 'role', label: '角色', required: true },
  { key: 'details', label: '说明' },
  { key: 'userCount', label: '用户数' },
  { key: 'dataScope', label: '数据范围' },
  { key: 'status', label: '状态' }
]))
const roleDrawerVisible = ref(false)
const savingRole = ref(false)
const switchingRoleId = ref<EntityId>()
const roleForm = reactive<Partial<SystemRole>>({})
const currentRole = ref<SystemRole>()
const permissionDrawerVisible = ref(false)
const permissionLoading = ref(false)
const savingPermissions = ref(false)
const activePermissionTab = ref<AuthorizationStep>('menu')
const selectedPermissions = ref<string[]>([])
// 授权表单只保存后端标准 code 和部门 id，中文展示统一走 dataScope.ts 字典。
const authorizationForm = reactive<{ dataScope: RoleDataScope; deptIds: EntityId[] }>({ dataScope: defaultRoleDataScope, deptIds: [] })
const assignableDataScopes = ref<RoleDataScope[]>([defaultRoleDataScope])
const authorizationRoleUserCount = ref(0)
const systemMenuTree = ref<SystemMenu[]>([])
const departmentTree = ref<SystemDepartment[]>([])
const departmentLoading = ref(false)
const departmentFilterText = ref('')
const roleDeptTreeRef = ref<{
  getCheckedKeys: (leafOnly?: boolean) => EntityId[]
  setCheckedKeys: (keys: EntityId[]) => void
  filter: (keyword: string) => void
}>()
const auth = useAuthStore()

const roleDrawerTitle = computed(() => (roleForm.roleId ? '编辑角色' : '新增角色'))
const permissionDrawerTitle = computed(() => `授权配置${currentRole.value ? ` - ${currentRole.value.roleName}` : ''}`)
const canEditRole = computed(() => auth.hasAnyPermission([PermissionCodes.system.role.edit]))
const visibleColumns = computed(() => visibleColumnMap(columns.value))
const activePermissionMeta = computed(() => permissionTypeConfig[activePermissionTab.value])
const dataScopeDescription = computed(() => {
  return roleDataScopeOptionMap[authorizationForm.dataScope]?.description || '请选择角色的数据范围策略。'
})
const assignableDataScopeSet = computed(() => new Set(assignableDataScopes.value))
const selectedDataScopeLabel = computed(() => {
  return roleDataScopeOptionMap[authorizationForm.dataScope]?.label || '未配置'
})
const authorizationBlockedReason = computed(() => {
  if (canAssignDataScope(authorizationForm.dataScope)) return ''
  return '当前账号不能保存该数据范围，请选择不高于自身权限的范围。'
})
const dataScopeRiskAlert = computed(() => resolveRoleDataScopeRiskAlert(authorizationForm.dataScope))
const selectedDepartmentMap = computed(() => {
  return collectDepartmentMap(departmentTree.value)
})
const selectedDepartments = computed(() => {
  return authorizationForm.deptIds
    .map((deptId) => selectedDepartmentMap.value.get(String(deptId)))
    .filter((dept): dept is SystemDepartment => Boolean(dept))
})
const visibleSelectedDepartments = computed(() => selectedDepartments.value.slice(0, 10))
const hiddenSelectedDepartmentCount = computed(() => Math.max(selectedDepartments.value.length - visibleSelectedDepartments.value.length, 0))

function canAssignDataScope(scope: RoleDataScope) {
  return assignableDataScopeSet.value.has(scope)
}

const normalizedPermissions = computed<NormalizedPermission[]>(() => {
  const permissions = new Map<string, NormalizedPermission>()
  permissionMenuTree.value.forEach((node) => {
    if (node.menu) permissions.set(node.menu.code, node.menu)
    node.actions.forEach((action) => permissions.set(action.code, action))
  })
  return Array.from(permissions.values())
})

const permissionGroups = computed<PermissionGroup[]>(() => {
  const grouped = new Map<string, PermissionGroup>()
  normalizedPermissions.value.forEach((permission) => {
    const key = `${permission.featureId}:${permission.type}:${permission.group}`
    const group = grouped.get(key) || {
      key,
      type: permission.type,
      featureId: permission.featureId,
      featureName: permission.featureName,
      groupName: permission.group,
      permissions: [],
      permissionCodes: []
    }
    group.permissions.push(permission)
    group.permissionCodes.push(permission.code)
    grouped.set(key, group)
  })
  return Array.from(grouped.values())
})

const permissionMenuTree = computed<PermissionMenuNode[]>(() => {
  return flattenRolePermissionMenuNodes(permissionMenuSections.value)
})
const permissionMenuSections = computed<PermissionMenuSection[]>(() => {
  return buildRolePermissionMenuSections(systemMenuTree.value)
})

const permissionByCode = computed<Record<string, NormalizedPermission>>(() => {
  return normalizedPermissions.value.reduce<Record<string, NormalizedPermission>>((map, permission) => {
    map[permission.code] = permission
    return map
  }, {})
})
const selectedMenuGroupKeys = computed(() => {
  const groups = new Set<string>()
  selectedPermissions.value.forEach((code) => {
    const permission = permissionByCode.value[code]
    if (permission?.type === 'menu') {
      groups.add(permissionGroupKey(permission))
    }
  })
  return groups
})
const selectedPageCodes = computed(() =>
  selectedPermissions.value.filter((code) => permissionByCode.value[code]?.type === 'menu')
)
const selectedActionCodes = computed(() =>
  selectedPermissions.value.filter((code) => {
    const permission = permissionByCode.value[code]
    return permission?.type === 'button' && selectedMenuGroupKeys.value.has(permissionGroupKey(permission))
  })
)
const finalPermissionCodes = computed(() => Array.from(new Set([...selectedPageCodes.value, ...selectedActionCodes.value])))
const selectedPermissionMenuNodes = computed(() =>
  permissionMenuTree.value.filter((node) => node.menu && selectedPermissions.value.includes(node.menu.code))
)
const selectedActionPermissionNodes = computed(() => selectedPermissionMenuNodes.value.filter((node) => node.actions.length > 0))
const activePermissionTreeNodes = computed(() => {
  if (activePermissionTab.value === 'menu') return permissionMenuTree.value
  if (activePermissionTab.value === 'button') return selectedActionPermissionNodes.value
  return []
})
const activeEmptyDescription = computed(() => {
  if (activePermissionTab.value === 'button' && selectedMenuGroupKeys.value.size === 0) {
    return '请先在“菜单权限”里选择菜单，系统会展示这些菜单下的按钮权限。'
  }
  if (activePermissionTab.value === 'button') {
    return '已选菜单下暂无可配置按钮权限；在菜单配置里声明按钮权限后会出现在这里。'
  }
  return `当前没有可授权的${activePermissionMeta.value.resourceLabel}。`
})
const permissionTabs = computed(() => {
  return buildRolePermissionTabs({
    tabTypes: permissionTabTypes,
    config: permissionTypeConfig,
    groupsForType,
    checkedCount,
    dataScope: authorizationForm.dataScope
  })
})
const authorizationSteps = computed(() => {
  return buildRoleAuthorizationSteps(authorizationStepTypes, permissionTabs.value)
})

watch(selectedMenuGroupKeys, () => {
  pruneUnavailableScopedPermissions()
})

async function loadRoles() {
  loading.value = true
  try {
    const result = await pageRoles(query)
    roles.value = result.list
    total.value = result.total
  } finally {
    loading.value = false
    updateTableHeight()
  }
}

function handleSearch() {
  query.page = 1
  loadRoles()
}

function resetRoleFilters() {
  query.keyword = ''
  query.enable = undefined
  handleSearch()
}

function handleCurrentChange(page: number) {
  query.page = page
  loadRoles()
}

function handleSizeChange(limit: number) {
  query.limit = limit
  query.page = 1
  loadRoles()
}

function openRoleDrawer(role?: SystemRole) {
  Object.assign(roleForm, buildRoleForm(role))
  delete roleForm.dataScope
  roleDrawerVisible.value = true
}

async function handleSaveRole(assignAfterSave = false) {
  savingRole.value = true
  try {
    const rolePayload: Partial<SystemRole> = {
      roleId: roleForm.roleId,
      roleName: roleForm.roleName,
      roleCode: roleForm.roleCode,
      details: roleForm.details,
      roleLevel: roleForm.roleLevel,
      enable: roleForm.enable
    }
    const savedRole = {
      ...rolePayload,
      ...(await saveRole(rolePayload))
    } as SystemRole
    ElMessage.success('角色已保存')
    roleDrawerVisible.value = false
    await loadRoles()
    if (assignAfterSave && savedRole.roleId) {
      await openAuthorizationDrawer(savedRole)
    }
  } finally {
    savingRole.value = false
  }
}

async function handleDeleteRole(role: SystemRole) {
  if ((role.userCount || 0) > 0) {
    ElMessage.warning('该角色下还有用户，先调整用户角色后再删除。')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除“${role.roleName}”？删除后该角色的授权关系会一并清理。`, '删除角色', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
      confirmButtonClass: 'el-button--danger'
    })
  } catch {
    return
  }
  await deleteRole(role.roleId)
  ElMessage.success('角色已删除')
  await loadRoles()
}

async function handleToggleRoleStatus(role: SystemRole) {
  if (!canEditRole.value) {
    ElMessage.warning('缺少角色维护权限')
    return
  }
  if (role.roleCode === 'admin') {
    ElMessage.warning('超级管理员角色不可停用')
    return
  }
  const nextEnable = !role.enable
  if (!nextEnable) {
    try {
      await ElMessageBox.confirm(`确认停用“${role.roleName}”？停用后绑定该角色的用户会失去对应授权。`, '停用角色', {
        confirmButtonText: '停用',
        cancelButtonText: '取消',
        type: 'warning'
      })
    } catch {
      return
    }
  }
  switchingRoleId.value = role.roleId
  try {
    await saveRole({
      roleId: role.roleId,
      roleName: role.roleName,
      roleCode: role.roleCode,
      details: role.details,
      enable: nextEnable
    })
    ElMessage.success(`角色已${nextEnable ? '启用' : '停用'}`)
    await loadRoles()
  } finally {
    switchingRoleId.value = undefined
  }
}

function roleStatusDisabledReason(role: SystemRole) {
  return resolveRoleStatusDisabledReason(canEditRole.value, role)
}

async function openAuthorizationDrawer(role: SystemRole) {
  currentRole.value = role
  authorizationForm.dataScope = normalizeRoleDataScope(role.dataScope) || defaultRoleDataScope
  authorizationForm.deptIds = []
  assignableDataScopes.value = [defaultRoleDataScope]
  authorizationRoleUserCount.value = role.userCount || 0
  departmentFilterText.value = ''
  activePermissionTab.value = 'menu'
  selectedPermissions.value = []
  permissionDrawerVisible.value = true
  permissionLoading.value = true
  try {
    await loadSystemMenuTree()
    const result = await getRolePermissions(role.roleId)
    authorizationForm.dataScope = normalizeRoleDataScope(result.dataScope || role.dataScope) || defaultRoleDataScope
    authorizationForm.deptIds = result.deptIds || []
    assignableDataScopes.value = normalizeAssignableDataScopes(result.assignableDataScopes)
    authorizationRoleUserCount.value = result.roleUserCount ?? role.userCount ?? 0
    selectedPermissions.value = normalizePermissionCodes(result.permissionCodes)
    if (authorizationForm.dataScope === customDepartmentDataScope) {
      await loadDepartmentTree()
      await applyCheckedDeptIds()
    }
  } finally {
    permissionLoading.value = false
  }
}

function normalizePermissionCodes(codes: string[] = []) {
  return normalizeRolePermissionCodes(codes, new Set(normalizedPermissions.value.map((permission) => permission.code)))
}

function checkedCount(codes: string[]) {
  return codes.filter((code) => selectedPermissions.value.includes(code)).length
}

function isPermissionChecked(code: string) {
  return selectedPermissions.value.includes(code)
}

function isGroupChecked(codes: string[]) {
  return codes.length > 0 && checkedCount(codes) === codes.length
}

function isGroupIndeterminate(codes: string[]) {
  const count = checkedCount(codes)
  return count > 0 && count < codes.length
}

function toggleGroup(codes: string[], checked: string | number | boolean) {
  const next = new Set(selectedPermissions.value)
  if (Boolean(checked)) {
    codes.forEach((code) => next.add(code))
  } else {
    codes.forEach((code) => next.delete(code))
  }
  selectedPermissions.value = Array.from(next)
}

function togglePermissionCode(code: string, checked: string | number | boolean) {
  const next = new Set(selectedPermissions.value)
  if (Boolean(checked)) {
    next.add(code)
  } else {
    next.delete(code)
  }
  selectedPermissions.value = Array.from(next)
}

function toggleMenuNode(node: PermissionMenuNode) {
  if (!node.menu) return
  togglePermissionCode(node.menu.code, !isPermissionChecked(node.menu.code))
}

function activatePermissionTab(type: AuthorizationStep) {
  activePermissionTab.value = type
}

function goNextAuthorizationStep() {
  const currentIndex = authorizationStepTypes.indexOf(activePermissionTab.value)
  activePermissionTab.value = authorizationStepTypes[Math.min(currentIndex + 1, authorizationStepTypes.length - 1)]
}

function goPreviousAuthorizationStep() {
  const currentIndex = authorizationStepTypes.indexOf(activePermissionTab.value)
  activePermissionTab.value = authorizationStepTypes[Math.max(currentIndex - 1, 0)]
}

function groupsForType(type: PermissionType) {
  return permissionGroups.value.filter((group) => {
    if (group.type !== type) return false
    return type === 'menu' || selectedMenuGroupKeys.value.has(`${group.featureId}:${group.groupName}`)
  })
}

function pruneUnavailableScopedPermissions() {
  const next = selectedPermissions.value.filter((code) => {
    const permission = permissionByCode.value[code]
    if (!permission) return false
    return permission.type === 'menu' || selectedMenuGroupKeys.value.has(permissionGroupKey(permission))
  })
  if (next.length !== selectedPermissions.value.length || next.some((code, index) => code !== selectedPermissions.value[index])) {
    selectedPermissions.value = next
  }
}

async function loadSystemMenuTree() {
  if (systemMenuTree.value.length > 0) {
    return
  }
  systemMenuTree.value = await listRolePermissionResources()
}

async function handleSaveAuthorization() {
  if (!currentRole.value) return
  if (authorizationBlockedReason.value) {
    ElMessage.warning(authorizationBlockedReason.value)
    activePermissionTab.value = 'scope'
    return
  }
  if (authorizationForm.dataScope === customDepartmentDataScope) {
    // Element Plus 树组件的勾选态以组件为准，提交前同步一次避免遗漏最后一次点击。
    syncCheckedDeptIds()
    if (authorizationForm.deptIds.length === 0) {
      ElMessage.warning('请选择自定义部门范围')
      activePermissionTab.value = 'scope'
      return
    }
  }
  if (authorizationForm.dataScope === 'ALL') {
    try {
      await ElMessageBox.confirm(`确认授予“${currentRole.value.roleName}”全部数据范围？该角色下 ${authorizationRoleUserCount.value} 个用户会获得全量可见性。`, '高风险数据授权', {
        confirmButtonText: '确认授予',
        cancelButtonText: '取消',
        type: 'warning',
        confirmButtonClass: 'el-button--danger'
      })
    } catch {
      return
    }
  }
  savingPermissions.value = true
  try {
    await saveRoleAuthorization({
      roleId: currentRole.value.roleId,
      roleName: currentRole.value.roleName,
      roleCode: currentRole.value.roleCode,
      dataScope: authorizationForm.dataScope,
      deptIds: authorizationForm.dataScope === customDepartmentDataScope ? authorizationForm.deptIds : [],
      permissionCodes: finalPermissionCodes.value,
      pageCodes: selectedPageCodes.value,
      actionCodes: selectedActionCodes.value
    })
    currentRole.value.dataScope = authorizationForm.dataScope
    const role = roles.value.find((item) => sameId(item.roleId, currentRole.value?.roleId))
    if (role) {
      role.dataScope = authorizationForm.dataScope
    }
    ElMessage.success('授权配置已保存')
    permissionDrawerVisible.value = false
  } finally {
    savingPermissions.value = false
  }
}

async function loadDepartmentTree() {
  if (departmentTree.value.length > 0) {
    return
  }
  departmentLoading.value = true
  try {
    departmentTree.value = await treeDepartments()
  } finally {
    departmentLoading.value = false
  }
}

async function applyCheckedDeptIds() {
  await nextTick()
  roleDeptTreeRef.value?.setCheckedKeys(authorizationForm.deptIds)
}

function syncCheckedDeptIds() {
  authorizationForm.deptIds = roleDeptTreeRef.value?.getCheckedKeys(false) || []
}

function filterDepartmentNode(keyword: string, department: SystemDepartment) {
  return filterRoleDepartmentNode(keyword, department)
}

function removeSelectedDepartment(deptId: EntityId) {
  authorizationForm.deptIds = authorizationForm.deptIds.filter((selectedDeptId) => !sameId(selectedDeptId, deptId))
  roleDeptTreeRef.value?.setCheckedKeys(authorizationForm.deptIds)
}

function clearSelectedDepartments() {
  authorizationForm.deptIds = []
  roleDeptTreeRef.value?.setCheckedKeys([])
}

watch(departmentFilterText, (keyword) => {
  roleDeptTreeRef.value?.filter(keyword)
})

watch(
  () => authorizationForm.dataScope,
  async (scope) => {
    if (scope === customDepartmentDataScope) {
      await loadDepartmentTree()
      await applyCheckedDeptIds()
      roleDeptTreeRef.value?.filter(departmentFilterText.value)
    } else {
      departmentFilterText.value = ''
      authorizationForm.deptIds = []
      roleDeptTreeRef.value?.setCheckedKeys([])
    }
  }
)

onMounted(loadRoles)
</script>

<style scoped>
.role-table {
  --el-table-fixed-right-column: inset -8px 0 8px -8px rgba(15, 23, 42, 0.08);
}

.role-user-count {
  color: #475569;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.form-help-text {
  margin: 6px 0 0;
  color: #66758f;
  font-size: 12px;
  line-height: 1.5;
}

.role-scope-badge {
  border-radius: 999px;
  font-weight: 700;
}

.role-scope-badge.is-all {
  border-color: #fed7aa;
  background: #fff7ed;
  color: #c2410c;
}

.role-scope-badge.is-dept-tree,
.role-scope-badge.is-dept {
  border-color: #bfdbfe;
  background: #eff6ff;
  color: #1d4ed8;
}

.role-scope-badge.is-self {
  border-color: #e2e8f0;
  background: #f8fafc;
  color: #475569;
}

.role-scope-badge.is-custom {
  border-color: #ddd6fe;
  background: #f5f3ff;
  color: #6d28d9;
}

.role-scope-badge.is-empty {
  border-color: #e2e8f0;
  background: #f8fafc;
  color: #64748b;
}

.data-scope-option {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  font-weight: 700;
}

.permission-data-alert {
  margin-top: 10px;
}

.permission-data-impact {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-top: 12px;
}

.permission-data-impact span {
  min-width: 0;
  padding: 8px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
  color: #64748b;
  font-size: 12px;
  line-height: 1.4;
}

.permission-data-impact strong {
  display: block;
  margin-top: 4px;
  color: #0f172a;
  font-size: 18px;
  font-weight: 800;
  line-height: 1.2;
}

.permission-data-tree-search {
  display: block;
  width: 100%;
  margin-top: 12px;
  margin-bottom: 10px;
}

.permission-data-tree-search :deep(.el-input__wrapper) {
  width: 100%;
  min-height: 40px;
  box-sizing: border-box;
}

.permission-selected-depts {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #e2e8f0;
}

.permission-selected-depts-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
  color: #475569;
  font-size: 13px;
  font-weight: 700;
}

.permission-selected-dept-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.permission-selected-dept-tags :deep(.el-tag) {
  display: inline-flex;
  max-width: 260px;
  min-height: 32px;
  align-items: center;
  gap: 6px;
}

.permission-selected-dept-tags :deep(.el-tag__content) {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.permission-selected-dept-tags :deep(.el-tag__close) {
  flex: 0 0 auto;
  margin-left: 0;
}

@media (max-width: 640px) {
  .permission-data-impact {
    grid-template-columns: 1fr;
  }

  .permission-selected-dept-tags :deep(.el-tag) {
    max-width: 100%;
  }
}

.role-status-switch :deep(.enable-status-switch__button) {
  gap: 5px;
  min-height: 28px;
  padding-right: 0;
  padding-left: 0;
}

.role-status-switch :deep(.enable-status-switch__track) {
  width: 34px;
  height: 20px;
  flex-basis: 34px;
}

.role-status-switch :deep(.enable-status-switch__thumb) {
  width: 12px;
  height: 12px;
}

.role-status-switch :deep(.enable-status-switch__button.is-active .enable-status-switch__thumb) {
  transform: translate(16px, -50%);
}

.role-status-switch :deep(.enable-status-switch__label) {
  min-width: 24px;
  color: #475569;
  font-weight: 600;
}

.role-row-actions {
  justify-content: center;
  gap: 4px;
}

.role-row-actions :deep(.el-button.is-text) {
  min-height: 28px;
  padding-right: 5px;
  padding-left: 5px;
  font-weight: 600;
}

.role-row-actions :deep(.role-authorize-button.el-button) {
  color: var(--ea-primary);
}

.role-row-actions :deep(.role-more-button.el-button) {
  --el-button-text-color: #64748b;
  --el-button-hover-text-color: var(--ea-primary);
  --el-button-active-text-color: #64748b;
  color: #64748b;
}

.role-row-actions :deep(.role-more-button.el-button .el-icon),
.role-row-actions :deep(.role-more-button.el-button span) {
  color: inherit;
}

.role-row-actions :deep(.role-more-button.el-button:hover) {
  color: var(--ea-primary);
}

.role-row-actions :deep(.role-more-button.el-button:focus:not(:hover)),
.role-row-actions :deep(.role-more-button.el-button:active:not(:hover)) {
  color: #64748b;
}

.role-row-more {
  display: inline-flex;
}

:global(.role-row-action-menu .el-dropdown-menu__item) {
  gap: 8px;
  min-width: 132px;
  font-weight: 600;
}

:global(.role-row-action-menu .el-dropdown-menu__item .el-icon) {
  margin-right: 0;
}

:global(.role-row-action-menu .el-dropdown-menu__item.is-danger:not(.is-disabled)) {
  color: #dc2626;
}
</style>

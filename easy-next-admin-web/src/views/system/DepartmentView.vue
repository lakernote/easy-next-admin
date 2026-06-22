<template>
  <section class="resource-page">
    <div class="resource-hero">
      <div>
        <h1>组织架构</h1>
        <p>维护企业部门树、上下级关系、办公地点和启停状态，用户归属和数据权限都基于组织架构落地。</p>
      </div>
      <div class="resource-actions">
        <el-button v-permission="PermissionCodes.system.dept.edit" type="primary" :icon="Plus" @click="openDepartmentDrawer()">新增部门</el-button>
      </div>
    </div>

    <div class="org-layout">
      <aside class="surface org-tree-panel">
        <div class="section-head org-tree-head">
          <div>
            <h2>组织树</h2>
            <p>点击节点查看该部门及下级。</p>
          </div>
          <el-button v-if="activeDepartment" text type="primary" @click="clearSelectedDepartment">全部</el-button>
        </div>
        <div class="org-tree-summary">
          <span>
            <strong>{{ flatDepartments.length }}</strong>
            <small>部门</small>
          </span>
          <span>
            <strong>{{ enabledDepartmentCount }}</strong>
            <small>启用</small>
          </span>
          <span>
            <strong>{{ disabledDepartmentCount }}</strong>
            <small>停用</small>
          </span>
        </div>
        <el-input v-model="keyword" :prefix-icon="Search" placeholder="搜索部门、地点、负责人" clearable />
        <el-tree
          ref="treeRef"
          class="org-tree"
          :data="departments"
          node-key="deptId"
          default-expand-all
          highlight-current
          :current-node-key="selectedDepartmentId"
          :expand-on-click-node="false"
          :filter-node-method="filterTreeNode"
          @node-click="selectDepartment"
        >
          <template #default="{ data }">
            <span class="org-tree-node">
              <span class="org-tree-main">
                <strong>{{ data.deptName }}</strong>
                <i :class="['org-tree-status', data.status ? 'is-enabled' : 'is-disabled']" />
              </span>
              <small>{{ compactPersonName(leaderName(data)) }} · {{ data.address || '未设地点' }}</small>
            </span>
          </template>
        </el-tree>
      </aside>

      <section ref="tablePanelRef" class="surface resource-panel is-fluid-table">
        <div class="table-control-row">
          <div class="panel-toolbar">
            <div class="department-filter-summary">
              <strong>{{ activeDepartment ? `${activeDepartment.deptName}及下级` : '全部组织' }}</strong>
              <span>显示 {{ filteredFlatDepartments.length }} / {{ flatDepartments.length }} 个部门</span>
              <el-button v-if="activeDepartment" link type="primary" @click="clearSelectedDepartment">查看全部</el-button>
            </div>
            <div class="toolbar-filters">
              <el-input v-model="keyword" :prefix-icon="Search" placeholder="搜索部门、地点、负责人" clearable />
              <el-select v-model="statusFilter" placeholder="全部状态" clearable>
                <el-option label="启用" value="enabled" />
                <el-option label="停用" value="disabled" />
              </el-select>
              <el-button @click="resetDepartmentFilters">重置</el-button>
            </div>
          </div>
          <TableToolbar v-model:columns="columns" class="table-toolbar-inline" />
        </div>

        <el-table
          v-loading="loading"
          :data="filteredDepartments"
          row-key="deptId"
          :height="tableHeight"
          default-expand-all
          class="admin-table department-table"
          :empty-text="departmentEmptyText"
          :row-class-name="departmentRowClassName"
          @selection-change="handleDepartmentSelectionChange"
          @row-dblclick="handleDepartmentRowDblClick"
        >
          <el-table-column type="selection" width="44" :selectable="isDepartmentSelectable" />
          <el-table-column v-if="visibleColumns.dept" prop="deptName" label="部门" min-width="260">
            <template #default="{ row }">
              <div class="entity-cell department-identity-cell">
                <span class="entity-avatar is-dept">{{ row.deptName.slice(0, 1) }}</span>
                <span class="department-identity">
                  <strong :title="row.deptName">{{ row.deptName }}</strong>
                  <small class="department-identity-meta">
                    <span :title="`上级：${parentDepartmentName(row)}`">上级：{{ parentDepartmentName(row) }}</span>
                    <span aria-hidden="true" class="department-meta-separator">·</span>
                    <span :title="row.address || '未设地点'">{{ row.address || '未设地点' }}</span>
                  </small>
                </span>
              </div>
            </template>
          </el-table-column>
          <el-table-column v-if="visibleColumns.fullName" prop="fullName" label="组织全称" min-width="220">
            <template #default="{ row }">{{ row.fullName || '-' }}</template>
          </el-table-column>
          <el-table-column v-if="visibleColumns.address" prop="address" label="办公地点" min-width="140">
            <template #default="{ row }">{{ row.address || '-' }}</template>
          </el-table-column>
          <el-table-column v-if="visibleColumns.leader" label="部门负责人" min-width="150">
            <template #default="{ row }">
              <span class="department-leader-cell" :title="leaderName(row)">{{ compactPersonName(leaderName(row)) }}</span>
            </template>
          </el-table-column>
          <el-table-column v-if="visibleColumns.sort" prop="sort" label="排序" width="90" />
          <el-table-column v-if="visibleColumns.status" prop="status" label="状态" width="112" fixed="right" align="center" header-align="center">
            <template #default="{ row }">
              <EnableStatusSwitch
                class="department-status-switch"
                :model-value="row.status"
                :loading="switchingDepartmentId === row.deptId"
                :disabled="!canEditDepartment"
                disabled-reason="缺少部门维护权限"
                :target-name="row.deptName"
                @toggle="handleToggleDepartmentStatus(row)"
              />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="132" fixed="right" align="center" header-align="center">
            <template #default="{ row }">
              <div class="row-actions department-row-actions">
                <el-button
                  v-permission:disable="{ permissions: PermissionCodes.system.dept.edit, reason: '缺少部门维护权限' }"
                  class="department-edit-button"
                  text
                  type="primary"
                  :icon="EditPen"
                  @click="openDepartmentDrawer(row)"
                >
                  编辑
                </el-button>
                <el-dropdown v-if="canEditDepartment" class="department-row-more" trigger="click" placement="bottom-end">
                  <el-button class="department-more-button" text :icon="MoreFilled" title="更多操作" aria-label="更多操作" @click.stop>
                    <span class="sr-only">更多操作</span>
                  </el-button>
                  <template #dropdown>
                    <el-dropdown-menu class="department-row-action-menu">
                      <el-dropdown-item class="is-danger" :data-testid="`department-delete-menu-${row.deptId}`" @click.stop="handleDelete(row)">
                        <el-icon><Delete /></el-icon>
                        <span>删除部门</span>
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="table-footer is-split">
          <div class="table-bulk-actions">
            <el-button
              v-permission:disable="{ permissions: PermissionCodes.system.dept.edit, reason: '缺少部门维护权限' }"
              :icon="Delete"
              :disabled="deletableSelectedDepartments.length === 0"
              :loading="batchDeletingDepartments"
              @click="handleBatchDeleteDepartments"
            >
              批量删除
            </el-button>
            <span>已选 {{ selectedDepartments.length }} 项，仅可批量删除无下级部门</span>
          </div>
          <span>显示 {{ filteredFlatDepartments.length }} / 共 {{ flatDepartments.length }} 个部门</span>
        </div>
      </section>
    </div>

    <el-drawer v-model="drawerVisible" :title="drawerTitle" size="min(520px, 92vw)">
      <el-form ref="formRef" :model="departmentForm" :rules="rules" label-position="top" class="drawer-form">
        <el-form-item label="部门名称" prop="deptName">
          <el-input v-model="departmentForm.deptName" placeholder="例如 华南区 / 研发部" />
        </el-form-item>
        <el-form-item label="上级部门">
          <el-select v-model="departmentForm.pid" placeholder="作为顶级部门" clearable filterable>
            <el-option
              v-for="department in parentOptions"
              :key="department.deptId"
              :label="department.label"
              :value="department.deptId"
              :disabled="sameId(department.deptId, departmentForm.deptId)"
            />
          </el-select>
          <small class="form-help">顶级部门留空；避免把部门挂到自己下面。</small>
        </el-form-item>
        <el-form-item label="办公地点">
          <el-input v-model="departmentForm.address" placeholder="例如 深圳 / 广州 / 上海" />
        </el-form-item>
        <el-form-item label="部门负责人">
          <el-select v-model="departmentForm.leaderUserId" placeholder="请选择负责人" clearable filterable>
            <el-option v-for="user in assigneeOptions" :key="user.value" :label="user.name" :value="user.value" />
          </el-select>
          <small class="form-help">用于流程中的“发起人部门负责人”和“发起人上级部门负责人”节点。</small>
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="排序">
            <el-input-number v-model="departmentForm.sort" :min="0" :max="9999" controls-position="right" />
          </el-form-item>
          <el-form-item label="状态">
            <el-switch v-model="departmentForm.status" active-text="启用" inactive-text="停用" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <div class="drawer-footer">
          <el-button @click="drawerVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
        </div>
      </template>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox, type ElTree } from 'element-plus'
import { Delete, EditPen, MoreFilled, Plus, Search } from '@element-plus/icons-vue'
import { batchDeleteDepartments, deleteDepartment, listUserAssignees, saveDepartment, treeDepartments } from '@/features/system/api'
import type { EntityId, SystemDepartment, SystemUserOption } from '@/features/system/types'
import { useAuthStore } from '@/stores/auth'
import { PermissionCodes } from '@/permissions/codes'
import TableToolbar from '@/components/table/TableToolbar.vue'
import EnableStatusSwitch from '@/components/table/EnableStatusSwitch.vue'
import { createTableColumnState, visibleColumnMap } from '@/components/table/tableColumns'
import { useFluidTableHeight } from '@/components/table/useFluidTableHeight'

type DepartmentForm = Partial<SystemDepartment> & { status: boolean; sort: number }

const departments = ref<SystemDepartment[]>([])
const loading = ref(false)
const tablePanelRef = ref<HTMLElement>()
const { tableHeight, updateTableHeight } = useFluidTableHeight(tablePanelRef)
const saving = ref(false)
const batchDeletingDepartments = ref(false)
const switchingDepartmentId = ref<EntityId>()
const selectedDepartments = ref<SystemDepartment[]>([])
const assigneeOptions = ref<SystemUserOption[]>([])
const keyword = ref('')
const statusFilter = ref<'enabled' | 'disabled' | ''>('')
const selectedDepartmentId = ref<EntityId>()
const columns = ref(createTableColumnState([
  { key: 'dept', label: '部门', required: true },
  { key: 'fullName', label: '组织全称' },
  { key: 'address', label: '办公地点' },
  { key: 'leader', label: '部门负责人' },
  { key: 'sort', label: '排序', visible: false },
  { key: 'status', label: '状态' }
]))
const drawerVisible = ref(false)
const treeRef = ref<InstanceType<typeof ElTree>>()
const formRef = ref<FormInstance>()
const departmentForm = reactive<DepartmentForm>({
  deptName: '',
  address: '',
  status: true,
  sort: 99
})
const auth = useAuthStore()
const visibleColumns = computed(() => visibleColumnMap(columns.value))
const assigneeNameById = computed(() => new Map(assigneeOptions.value.map((user) => [String(user.value), user.name])))

const rules: FormRules = {
  deptName: [{ required: true, message: '请输入部门名称', trigger: 'blur' }]
}

const flatDepartments = computed(() => flattenDepartments(departments.value))
const drawerTitle = computed(() => (departmentForm.deptId ? '编辑部门' : '新增部门'))
const canEditDepartment = computed(() => auth.hasAnyPermission([PermissionCodes.system.dept.edit]))
const deletableSelectedDepartments = computed(() => selectedDepartments.value.filter((department) => isDepartmentSelectable(department)))
const enabledDepartmentCount = computed(() => flatDepartments.value.filter((department) => department.status).length)
const disabledDepartmentCount = computed(() => flatDepartments.value.length - enabledDepartmentCount.value)
const activeDepartment = computed(() =>
  selectedDepartmentId.value ? findDepartmentById(departments.value, selectedDepartmentId.value) : undefined
)
const activeDepartmentIds = computed(() => {
  if (!activeDepartment.value) return undefined
  return new Set(flattenDepartments([activeDepartment.value]).map((department) => String(department.deptId)))
})
const parentOptions = computed(() =>
  flatDepartments.value.map((item) => ({
    ...item,
    label: `${'  '.repeat(levelOf(item.deptId))}${item.deptName}`
  }))
)
const filteredDepartments = computed(() => filterDepartments(departments.value))
const filteredFlatDepartments = computed(() => flattenDepartments(filteredDepartments.value))
const departmentEmptyText = computed(() => (keyword.value || statusFilter.value || activeDepartment.value ? '暂无匹配部门' : '暂无部门数据'))

watch(keyword, (value) => {
  treeRef.value?.filter(value)
})

watch(flatDepartments, (items) => {
  if (selectedDepartmentId.value && !items.some((item) => sameId(item.deptId, selectedDepartmentId.value))) {
    selectedDepartmentId.value = undefined
  }
})

function flattenDepartments(items: SystemDepartment[]): SystemDepartment[] {
  return items.flatMap((item) => [item, ...flattenDepartments(item.children || [])])
}

function filterDepartments(items: SystemDepartment[]): SystemDepartment[] {
  const text = keyword.value.trim()
  const scopedIds = activeDepartmentIds.value
  return items
    .map((item) => {
      const children = item.children ? filterDepartments(item.children) : undefined
      const hitScope = !scopedIds || scopedIds.has(String(item.deptId))
      const hitText =
        !text ||
        [item.deptName, item.fullName, item.address, leaderName(item)]
          .filter((value) => value !== undefined && value !== null)
          .some((value) => String(value).includes(text))
      const hitStatus =
        !statusFilter.value ||
        (statusFilter.value === 'enabled' && item.status) ||
        (statusFilter.value === 'disabled' && !item.status)
      if (children?.length) return { ...item, children }
      return hitScope && hitText && hitStatus ? { ...item, children } : undefined
    })
    .filter(Boolean) as SystemDepartment[]
}

function filterTreeNode(value: string, data: SystemDepartment) {
  if (!value) return true
  return [data.deptName, data.fullName, data.address, leaderName(data)]
    .filter((item) => item !== undefined && item !== null)
    .some((item) => String(item).includes(value))
}

function levelOf(deptId: EntityId, items = departments.value, level = 0): number {
  for (const item of items) {
    if (sameId(item.deptId, deptId)) return level
    const childLevel = levelOf(deptId, item.children || [], level + 1)
    if (childLevel >= 0) return childLevel
  }
  return -1
}

function findDepartmentById(items: SystemDepartment[], deptId?: EntityId): SystemDepartment | undefined {
  if (deptId === undefined) return undefined
  for (const item of items) {
    if (sameId(item.deptId, deptId)) return item
    const child = findDepartmentById(item.children || [], deptId)
    if (child) return child
  }
  return undefined
}

function parentDepartmentName(row: SystemDepartment) {
  const pid = row.pid
  if (pid === undefined || pid === null || String(pid) === '0') return '顶级'
  const visibleParentName = flatDepartments.value.find((item) => sameId(item.deptId, pid))?.deptName
  if (visibleParentName) return visibleParentName

  // 当前账号可能只能看到子部门；用组织全称反推父级，避免把数据库 ID 暴露给业务用户。
  const fullNameParts = (row.fullName || '')
    .split('/')
    .map((item) => item.trim())
    .filter(Boolean)
  const currentIndex = fullNameParts.lastIndexOf(row.deptName)
  if (currentIndex > 0) return fullNameParts[currentIndex - 1]
  if (fullNameParts.length > 1) return fullNameParts[fullNameParts.length - 2]
  return '上级部门'
}

function leaderName(row: SystemDepartment) {
  return row.leaderName || (row.leaderUserId ? assigneeNameById.value.get(String(row.leaderUserId)) : undefined) || '未设置'
}

function compactPersonName(name?: string) {
  return (name || '未设置').replace(/（[^）]+）/g, '').replace(/\([^)]*\)/g, '').trim() || '未设置'
}

function selectDepartment(row: SystemDepartment) {
  selectedDepartmentId.value = row.deptId
}

function clearSelectedDepartment() {
  selectedDepartmentId.value = undefined
}

function resetDepartmentFilters() {
  keyword.value = ''
  statusFilter.value = ''
  selectedDepartmentId.value = undefined
}

function sameId(left?: EntityId, right?: EntityId) {
  return left !== undefined && right !== undefined && String(left) === String(right)
}

function resetForm() {
  Object.assign(departmentForm, {
    deptId: undefined,
    deptName: '',
    address: '',
    pid: undefined,
    leaderUserId: undefined,
    status: true,
    sort: 99
  })
  nextTick(() => formRef.value?.clearValidate())
}

function openDepartmentDrawer(row?: SystemDepartment) {
  resetForm()
  if (row) {
    Object.assign(departmentForm, {
      deptId: row.deptId,
      deptName: row.deptName,
      address: row.address || '',
      pid: row.pid,
      leaderUserId: row.leaderUserId,
      status: row.status,
      sort: row.sort ?? 99
    })
  }
  drawerVisible.value = true
}

async function handleSave() {
  const valid = await formRef.value?.validate()
  if (!valid) return
  saving.value = true
  try {
    await saveDepartment({
      deptId: departmentForm.deptId,
      deptName: normalizeOptionalText(departmentForm.deptName),
      address: normalizeOptionalText(departmentForm.address),
      pid: departmentForm.pid,
      leaderUserId: departmentForm.leaderUserId,
      status: departmentForm.status,
      sort: departmentForm.sort
    })
    ElMessage.success('部门已保存')
    drawerVisible.value = false
    await loadDepartments()
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: SystemDepartment) {
  await ElMessageBox.confirm(`确认删除“${row.deptName}”？删除后该部门下的用户归属需要重新维护。`, '删除部门', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    confirmButtonClass: 'el-button--danger'
  })
  await deleteDepartment(row.deptId)
  ElMessage.success('部门已删除')
  await loadDepartments()
}

function handleDepartmentSelectionChange(selection: SystemDepartment[]) {
  selectedDepartments.value = selection
}

function isDepartmentSelectable(row: SystemDepartment) {
  return canEditDepartment.value && !row.children?.length
}

function departmentRowClassName({ row }: { row: SystemDepartment }) {
  return activeDepartment.value && sameId(row.deptId, activeDepartment.value.deptId) ? 'is-active-department-row' : ''
}

function handleDepartmentRowDblClick(row: SystemDepartment) {
  if (canEditDepartment.value) {
    openDepartmentDrawer(row)
  }
}

async function handleBatchDeleteDepartments() {
  const departmentsToDelete = deletableSelectedDepartments.value
  if (!departmentsToDelete.length) {
    ElMessage.warning('请选择无下级的部门')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确认删除已选的 ${departmentsToDelete.length} 个部门吗？删除后相关用户归属需要重新维护。`,
      '批量删除部门',
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

  batchDeletingDepartments.value = true
  try {
    await batchDeleteDepartments(departmentsToDelete.map((department) => department.deptId))
    ElMessage.success('部门已批量删除')
    await loadDepartments()
  } finally {
    batchDeletingDepartments.value = false
  }
}

async function handleToggleDepartmentStatus(row: SystemDepartment) {
  if (!canEditDepartment.value) {
    ElMessage.warning('缺少部门维护权限')
    return
  }
  const nextStatus = !row.status
  if (!nextStatus) {
    try {
      await ElMessageBox.confirm(`确认停用“${row.deptName}”？停用后相关用户和数据范围需要按组织规则继续维护。`, '停用部门', {
        confirmButtonText: '停用',
        cancelButtonText: '取消',
        type: 'warning'
      })
    } catch {
      return
    }
  }
  switchingDepartmentId.value = row.deptId
  try {
    await saveDepartment({
      deptId: row.deptId,
      deptName: normalizeOptionalText(row.deptName),
      address: normalizeOptionalText(row.address),
      pid: row.pid,
      leaderUserId: row.leaderUserId,
      status: nextStatus,
      sort: row.sort
    })
    ElMessage.success(`部门已${nextStatus ? '启用' : '停用'}`)
    await loadDepartments()
  } finally {
    switchingDepartmentId.value = undefined
  }
}

async function loadDepartments() {
  loading.value = true
  try {
    const [departmentResult, assigneeResult] = await Promise.all([
      treeDepartments(),
      listUserAssignees().catch(() => [] as SystemUserOption[])
    ])
    departments.value = departmentResult
    assigneeOptions.value = assigneeResult
    selectedDepartments.value = []
  } finally {
    loading.value = false
    updateTableHeight()
  }
}

onMounted(loadDepartments)

function normalizeOptionalText(value?: string) {
  const text = value?.trim()
  return text || undefined
}
</script>

<style scoped>
.department-table {
  --el-table-fixed-right-column: inset -8px 0 8px -8px rgba(15, 23, 42, 0.08);
}

.department-table :deep(.el-table__cell) {
  padding: 10px 0;
}

.department-table :deep(.is-active-department-row > .el-table__cell) {
  background: #f0f7ff;
}

.department-table :deep(.is-active-department-row .entity-avatar) {
  background: #dbeafe;
  color: #1d4ed8;
}

.department-identity-cell {
  align-items: center;
}

.department-identity {
  min-width: 0;
}

.department-identity strong {
  overflow: hidden;
  color: #334155;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.department-identity-meta {
  display: flex;
  max-width: 100%;
  min-width: 0;
  align-items: center;
  gap: 5px;
}

.department-identity-meta span:not(.department-meta-separator) {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.department-meta-separator {
  flex: 0 0 auto;
  color: #cbd5e1;
}

.department-filter-summary {
  flex: 1 1 260px;
  min-width: 220px;
}

.department-filter-summary strong {
  color: #0f172a;
  font-size: 14px;
}

.department-filter-summary span {
  color: #64748b;
}

.department-filter-summary :deep(.el-button) {
  min-height: 24px;
  margin-top: 4px;
  padding: 0;
}

.department-filter-summary :deep(.el-button span) {
  display: inline;
  margin-top: 0;
}

.org-tree-head {
  align-items: center;
  margin-bottom: 12px;
}

.org-tree-head :deep(.el-button) {
  min-height: 28px;
  padding: 0;
}

.org-tree-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 12px;
}

.org-tree-summary span {
  min-width: 0;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 8px 10px;
  background: #f8fafc;
}

.org-tree-summary strong,
.org-tree-summary small {
  display: block;
}

.org-tree-summary strong {
  color: #0f172a;
  font-size: 17px;
  line-height: 1.2;
}

.org-tree-summary small {
  margin-top: 2px;
  color: #64748b;
  font-size: 12px;
}

.org-tree-node {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  align-items: center;
  justify-content: stretch;
  gap: 3px;
  padding: 5px 8px 5px 0;
}

.org-tree-main {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 6px;
}

.org-tree-main strong {
  min-width: 0;
}

.org-tree-status {
  flex: 0 0 auto;
  width: 7px;
  height: 7px;
  border-radius: 999px;
}

.org-tree-status.is-enabled {
  background: #22c55e;
}

.org-tree-status.is-disabled {
  background: #94a3b8;
}

.org-tree-node small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.department-status-switch :deep(.enable-status-switch__button) {
  gap: 5px;
  min-height: 28px;
  padding-right: 0;
  padding-left: 0;
}

.department-status-switch :deep(.enable-status-switch__track) {
  width: 34px;
  height: 20px;
  flex-basis: 34px;
}

.department-status-switch :deep(.enable-status-switch__thumb) {
  width: 12px;
  height: 12px;
}

.department-status-switch :deep(.enable-status-switch__button.is-active .enable-status-switch__thumb) {
  transform: translate(16px, -50%);
}

.department-status-switch :deep(.enable-status-switch__label) {
  min-width: 24px;
  color: #475569;
  font-weight: 600;
}

.department-row-actions {
  justify-content: center;
  gap: 4px;
}

.department-leader-cell {
  color: #334155;
  font-weight: 700;
}

.department-row-actions :deep(.el-button.is-text) {
  min-height: 28px;
  padding-right: 5px;
  padding-left: 5px;
  font-weight: 600;
}

.department-row-actions :deep(.department-edit-button.el-button) {
  color: var(--ea-primary);
}

.department-row-actions :deep(.department-more-button.el-button) {
  --el-button-text-color: #64748b;
  --el-button-hover-text-color: var(--ea-primary);
  --el-button-active-text-color: #64748b;
  width: 28px;
  min-width: 28px;
  color: #64748b;
}

.department-row-actions :deep(.department-more-button.el-button .el-icon),
.department-row-actions :deep(.department-more-button.el-button span) {
  color: inherit;
}

.department-row-actions :deep(.department-more-button.el-button:hover) {
  color: var(--ea-primary);
}

.department-row-actions :deep(.department-more-button.el-button:focus:not(:hover)),
.department-row-actions :deep(.department-more-button.el-button:active:not(:hover)) {
  color: #64748b;
}

.department-row-more {
  display: inline-flex;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
}

:global(.department-row-action-menu .el-dropdown-menu__item) {
  gap: 8px;
  min-width: 132px;
  font-weight: 600;
}

:global(.department-row-action-menu .el-dropdown-menu__item .el-icon) {
  margin-right: 0;
}

:global(.department-row-action-menu .el-dropdown-menu__item.is-danger:not(.is-disabled)) {
  color: #dc2626;
}
</style>

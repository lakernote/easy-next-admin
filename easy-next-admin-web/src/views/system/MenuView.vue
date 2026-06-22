<template>
  <section class="resource-page">
    <div class="resource-hero">
      <div>
        <h1>菜单配置</h1>
        <p>按左侧导航的真实层级维护页面入口和按钮权限。</p>
      </div>
      <div class="resource-actions">
        <el-button
          v-permission="PermissionCodes.system.menu.edit"
          :icon="Plus"
          data-testid="menu-add-directory"
          @click="openTopDirectoryDrawer"
        >
          新增分组
        </el-button>
        <el-button
          v-permission="PermissionCodes.system.menu.edit"
          type="primary"
          :icon="Plus"
          data-testid="menu-add-page"
          @click="openTopPageDrawer"
        >
          新增页面
        </el-button>
      </div>
    </div>

    <section class="surface resource-panel">
      <div class="table-control-row">
        <div class="panel-toolbar">
          <div class="toolbar-filters">
            <el-input v-model="keyword" :prefix-icon="Search" placeholder="搜索名称 / 权限码 / 路由" clearable />
            <el-select v-model="resourceTypeFilter" placeholder="全部节点" clearable style="width: 142px">
              <el-option v-for="item in allResourceTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-select v-model="enableFilter" placeholder="全部状态" clearable style="width: 126px">
              <el-option label="启用" :value="true" />
              <el-option label="停用" :value="false" />
            </el-select>
            <el-select v-model="visibleFilter" placeholder="侧栏显示" clearable style="width: 126px">
              <el-option label="显示" :value="true" />
              <el-option label="隐藏" :value="false" />
            </el-select>
            <el-button @click="resetFilters">重置</el-button>
          </div>
        </div>
      </div>

      <div class="menu-config-workbench">
        <aside class="menu-tree-panel">
          <div class="menu-tree-head">
            <div>
              <strong>导航树</strong>
              <span>{{ navigationNodes.length }} 个导航节点，{{ permissionTotal }} 个按钮权限</span>
            </div>
          </div>

          <el-tree
            v-loading="loading"
            class="navigation-config-tree"
            :data="filteredNavigationTree"
            node-key="id"
            default-expand-all
            highlight-current
            :current-node-key="activeNode?.id"
            :expand-on-click-node="false"
            empty-text="暂无导航节点"
            @node-click="selectNavigationNode"
          >
            <template #default="{ data }">
              <div class="menu-nav-node" :class="{ 'is-disabled': !data.enable, 'is-hidden': data.visible === false }">
                <span :class="['menu-node-icon', `is-${data.type}`]">
                  <el-icon><component :is="resolveMenuIcon(resourceTypeIcon(data.type, data.icon))" /></el-icon>
                </span>
                <span class="menu-nav-node-title">
                  <span class="menu-nav-node-line">
                    <strong>{{ data.name }}</strong>
                    <span :class="['menu-node-type', `is-${data.type}`]">{{ resourceTypeText(data.type) }}</span>
                  </span>
                  <small>{{ navigationNodeSubtitle(data) }}</small>
                  <span v-if="compactNavigationStateBadges(data).length" class="menu-nav-node-badges">
                    <span
                      v-for="badge in compactNavigationStateBadges(data)"
                      :key="badge.key"
                      :class="['menu-state-badge', `is-${badge.tone}`]"
                    >
                      {{ badge.label }}
                    </span>
                  </span>
                </span>
                <span class="menu-nav-node-count">{{ navigationNodeCountText(data) }}</span>
              </div>
            </template>
          </el-tree>
        </aside>

        <section ref="detailPanelRef" class="menu-detail-panel">
          <el-empty v-if="!activeNode && !loading" description="暂无菜单资源">
            <div class="empty-actions">
              <el-button v-permission="PermissionCodes.system.menu.edit" :icon="Plus" @click="openTopDirectoryDrawer">
                新增分组
              </el-button>
              <el-button v-permission="PermissionCodes.system.menu.edit" type="primary" :icon="Plus" @click="openTopPageDrawer">
                新增页面
              </el-button>
            </div>
          </el-empty>

          <template v-else-if="activeNode">
            <div class="menu-detail-hero">
              <div>
                <div class="menu-detail-title">
                  <span :class="['menu-detail-icon', `is-${activeNode.type}`]">
                    <el-icon><component :is="resolveMenuIcon(resourceTypeIcon(activeNode.type, activeNode.icon))" /></el-icon>
                  </span>
                  <h2>{{ activeNode.name }}</h2>
                  <span :class="['menu-node-type', `is-${activeNode.type}`]">{{ resourceTypeText(activeNode.type) }}</span>
                  <span
                    v-for="badge in navigationStateBadges(activeNode)"
                    :key="badge.key"
                    :class="['menu-state-badge', `is-${badge.tone}`]"
                  >
                    {{ badge.label }}
                  </span>
                </div>
                <p>{{ activeNode.description || defaultDescription(activeNode) }}</p>
              </div>
              <div class="menu-detail-actions">
                <template v-if="activeNode.type === 'directory'">
                  <el-button
                    v-permission:disable="{ permissions: PermissionCodes.system.menu.edit, reason: '缺少菜单维护权限' }"
                    type="primary"
                    plain
                    :icon="Plus"
                    @click="openChildPageDrawer(activeNode)"
                  >
                    新增子页面
                  </el-button>
                  <el-button
                    v-permission:disable="{ permissions: PermissionCodes.system.menu.edit, reason: '缺少菜单维护权限' }"
                    :icon="EditPen"
                    @click="openResourceDrawer('directory', activeNode)"
                  >
                    编辑分组
                  </el-button>
                  <el-tooltip :disabled="!hasDependentResources(activeNode)" content="先删除或迁移下级节点后再删除分组" placement="top">
                    <span v-permission:disable="{ permissions: PermissionCodes.system.menu.edit, reason: '缺少菜单维护权限' }">
                      <el-button
                        type="danger"
                        plain
                        :icon="Delete"
                        :disabled="hasDependentResources(activeNode) || deletingResourceId === activeNode.menuId"
                        :loading="deletingResourceId === activeNode.menuId"
                        @click="handleDeleteResource(activeNode)"
                      >
                        删除分组
                      </el-button>
                    </span>
                  </el-tooltip>
                </template>
                <template v-else>
                  <el-button
                    v-permission:disable="{ permissions: PermissionCodes.system.menu.edit, reason: '缺少菜单维护权限' }"
                    type="primary"
                    plain
                    :icon="Plus"
                    @click="openResourceDrawer('button', undefined, activeNode)"
                  >
                    新增按钮权限
                  </el-button>
                  <el-button
                    v-permission:disable="{ permissions: PermissionCodes.system.menu.edit, reason: '缺少菜单维护权限' }"
                    :icon="EditPen"
                    @click="openResourceDrawer('menu', activeNode)"
                  >
                    编辑页面
                  </el-button>
                  <el-tooltip :disabled="!hasDependentResources(activeNode)" content="先删除按钮权限后再删除页面" placement="top">
                    <span v-permission:disable="{ permissions: PermissionCodes.system.menu.edit, reason: '缺少菜单维护权限' }">
                      <el-button
                        type="danger"
                        plain
                        :icon="Delete"
                        :disabled="hasDependentResources(activeNode) || deletingResourceId === activeNode.menuId"
                        :loading="deletingResourceId === activeNode.menuId"
                        @click="handleDeleteResource(activeNode)"
                      >
                        删除页面
                      </el-button>
                    </span>
                  </el-tooltip>
                </template>
              </div>
            </div>

            <dl class="menu-detail-meta">
              <div>
                <dt>节点类型</dt>
                <dd>{{ resourceTypeText(activeNode.type) }}</dd>
              </div>
              <div>
                <dt>生效状态</dt>
                <dd>{{ activeNode.enable ? '启用' : '停用' }}</dd>
              </div>
              <div>
                <dt>侧边栏</dt>
                <dd>{{ activeNode.visible === false ? '隐藏' : '显示' }}</dd>
              </div>
              <div>
                <dt>{{ activeNode.type === 'directory' ? '节点用途' : '页面路由' }}</dt>
                <dd>{{ activeNode.type === 'directory' ? '导航分组' : activeNode.resource || '-' }}</dd>
              </div>
              <div>
                <dt>上级导航</dt>
                <dd>{{ parentNodeName(activeNode) }}</dd>
              </div>
              <div>
                <dt>权限码</dt>
                <dd class="code-text">{{ activeNode.code || '-' }}</dd>
              </div>
              <div>
                <dt>排序</dt>
                <dd>{{ activeNode.sort ?? 0 }}</dd>
              </div>
              <div>
                <dt>{{ activeNode.type === 'menu' ? '组件路径' : '下级节点' }}</dt>
                <dd>{{ activeNode.type === 'menu' ? activeNode.componentPath || '-' : `${activeNode.children.length} 个` }}</dd>
              </div>
            </dl>

            <div v-if="activeNode.type === 'directory'" class="menu-directory-summary">
              <strong>分组内容</strong>
              <p>当前分组下有 {{ activeNode.children.length }} 个导航节点。选择页面后维护按钮权限。</p>
              <div>
                <el-tag v-for="child in activeNode.children" :key="child.id" effect="plain" @click="selectNavigationNode(child)">
                  {{ child.name }}
                </el-tag>
              </div>
            </div>

            <div v-else class="menu-resource-sections">
              <section class="menu-resource-section is-action-permission">
                <div class="menu-resource-section-head">
                  <div>
                    <strong>按钮权限</strong>
                    <span>维护页面上的新增、编辑、删除、导出等按钮权限。</span>
                  </div>
                  <el-button
                    v-permission:disable="{ permissions: PermissionCodes.system.menu.edit, reason: '缺少菜单维护权限' }"
                    type="primary"
                    plain
                    :icon="Plus"
                    @click="openResourceDrawer('button', undefined, activeNode)"
                  >
                    新增按钮
                  </el-button>
                </div>
                <el-table :data="activeButtonRows" row-key="id" class="admin-table menu-resource-table" empty-text="暂无按钮权限">
                  <el-table-column prop="name" label="名称" min-width="150">
                    <template #default="{ row }">
                      <div class="menu-resource-name">
                        <span class="resource-node-dot"></span>
                        <span>
                          <strong>{{ row.name }}</strong>
                          <small>{{ row.description || '-' }}</small>
                        </span>
                      </div>
                    </template>
                  </el-table-column>
                  <el-table-column prop="code" label="权限码" min-width="150" show-overflow-tooltip>
                    <template #default="{ row }"><span class="code-text">{{ row.code || '-' }}</span></template>
                  </el-table-column>
                  <el-table-column prop="enable" label="状态" width="96">
                    <template #default="{ row }">
                      <EnableStatusSwitch
                        :model-value="Boolean(row.enable)"
                        :loading="switchingResourceId === row.menuId"
                        :disabled="!canEditMenu || !row.menuId"
                        :disabled-reason="menuStatusDisabledReason(row)"
                        :target-name="row.name"
                        @toggle="handleToggleResourceStatus(row)"
                      />
                    </template>
                  </el-table-column>
                  <el-table-column label="操作" width="144">
                    <template #default="{ row }">
                      <div class="row-actions">
                        <el-button
                          v-permission:disable="{ permissions: PermissionCodes.system.menu.edit, reason: '缺少菜单维护权限' }"
                          text
                          type="primary"
                          :icon="EditPen"
                          @click="openResourceDrawer('button', row, activeNode)"
                        >
                          编辑
                        </el-button>
                        <el-button
                          v-permission:disable="{ permissions: PermissionCodes.system.menu.edit, reason: '缺少菜单维护权限' }"
                          text
                          type="danger"
                          :icon="Delete"
                          :loading="deletingResourceId === row.menuId"
                          @click="handleDeleteResource(row)"
                        >
                          删除
                        </el-button>
                      </div>
                    </template>
                  </el-table-column>
                </el-table>
              </section>

            </div>
          </template>
        </section>
      </div>
    </section>

    <el-drawer
      v-model="resourceDrawerVisible"
      :title="resourceDrawerTitle"
      size="min(680px, 92vw)"
      data-testid="menu-resource-drawer"
      @opened="resetResourceDrawerScroll"
    >
      <el-form ref="resourceFormRef" :model="resourceForm" :rules="resourceRules" label-position="top" class="drawer-form">
        <el-form-item label="节点类型" prop="type">
          <el-segmented v-model="resourceForm.type" :options="drawerResourceTypeOptions" :disabled="Boolean(resourceForm.menuId)" />
          <small class="form-help">{{ resourceFormHelp }}</small>
        </el-form-item>

        <el-form-item :label="resourceNameLabel" prop="title">
          <el-input v-model="resourceForm.title" :placeholder="resourceNamePlaceholder" />
        </el-form-item>

        <el-form-item v-if="requiresParentSelector" :label="parentSelectorLabel" prop="parentMenuId">
          <el-select v-model="resourceForm.parentMenuId" :placeholder="parentSelectorPlaceholder" filterable style="width: 100%">
            <template v-if="isNavigationForm">
              <el-option :label="resourceForm.type === 'directory' ? '作为顶层分组' : '作为顶层页面'" :value="0" />
              <el-option
                v-for="node in navigationParentOptions"
                :key="node.id"
                :label="treeOptionLabel(node)"
                :value="node.menuId"
                :disabled="isNavigationParentDisabled(node)"
              />
            </template>
            <template v-else>
              <el-option v-for="node in menuResourceOptions" :key="node.id" :label="treeOptionLabel(node)" :value="node.menuId" />
            </template>
          </el-select>
        </el-form-item>

        <el-form-item v-if="requiresResourcePath" :label="resourcePathLabel" prop="resource">
          <el-input v-model="resourceForm.resource" :placeholder="resourcePathPlaceholder" />
        </el-form-item>

        <el-form-item v-if="requiresPermissionCode" :label="resourceCodeLabel" prop="code">
          <el-input v-model="resourceForm.code" :placeholder="resourceCodePlaceholder" />
        </el-form-item>

        <el-form-item v-if="isNavigationForm" label="图标">
          <div class="menu-icon-picker">
            <div class="menu-icon-picker-current">
              <span class="menu-icon-preview">
                <el-icon><component :is="resolveMenuIcon(resourceForm.icon)" /></el-icon>
              </span>
              <span>
                <strong>{{ selectedIconLabel || '未选择图标' }}</strong>
                <small>{{ resourceForm.icon || '保存后侧边栏使用默认菜单图标' }}</small>
              </span>
              <el-button v-if="resourceForm.icon" text type="primary" @click="clearMenuIcon">清空</el-button>
            </div>
            <el-input v-model="iconKeyword" :prefix-icon="Search" placeholder="搜索图标名称 / 组件名" clearable />
            <div class="menu-icon-sections">
              <section v-for="section in filteredIconSections" :key="section.title" class="menu-icon-section">
                <div>
                  <strong>{{ section.title }}</strong>
                  <span>{{ section.options.length }} 个</span>
                </div>
                <div class="menu-icon-grid">
                  <button
                    v-for="item in section.options"
                    :key="item.value"
                    type="button"
                    :class="['menu-icon-choice', { 'is-selected': resourceForm.icon === item.value }]"
                    @click="selectMenuIcon(item.value)"
                  >
                    <el-icon><component :is="resolveMenuIcon(item.value)" /></el-icon>
                    <span>{{ item.label }}</span>
                  </button>
                </div>
              </section>
              <el-empty v-if="!filteredIconSections.length" description="没有匹配图标" />
            </div>
          </div>
        </el-form-item>

        <div class="form-grid">
          <el-form-item label="排序">
            <el-input-number v-model="resourceForm.sort" :min="0" :max="9999" controls-position="right" />
          </el-form-item>
          <el-form-item label="状态">
            <el-switch v-model="resourceForm.enable" active-text="启用" inactive-text="停用" />
          </el-form-item>
          <el-form-item v-if="isNavigationForm" label="侧边栏">
            <el-switch v-model="resourceForm.visible" active-text="显示" inactive-text="隐藏" />
          </el-form-item>
        </div>

        <el-form-item label="说明">
          <el-input v-model="resourceForm.remark" type="textarea" :rows="3" placeholder="说明这个节点的用途" />
        </el-form-item>

        <el-collapse v-if="resourceForm.type === 'menu'" class="advanced-config">
          <el-collapse-item title="高级配置" name="advanced">
            <div class="form-grid">
              <el-form-item label="组件路径">
                <el-input v-model="resourceForm.componentPath" placeholder="@/views/module/PageView.vue" />
              </el-form-item>
            </div>
          </el-collapse-item>
        </el-collapse>
      </el-form>
      <template #footer>
        <el-button @click="resourceDrawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingResource" @click="submitResourceForm">保存</el-button>
      </template>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, type Component } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Delete, EditPen, Plus, Search } from '@element-plus/icons-vue'
import * as ElementPlusIcons from '@element-plus/icons-vue'
import { PermissionCodes } from '@/permissions/codes'
import EnableStatusSwitch from '@/components/table/EnableStatusSwitch.vue'
import { deleteSystemMenuResource, listSystemMenus, saveSystemMenuResource } from '@/features/system/api'
import {
  allResourceTypeOptions,
  menuIconOptions,
  menuIconSections,
  navigationResourceTypeOptions,
  navigationStateBadges,
  permissionResourceTypeOptions,
  resourceFormHelp as resourceFormHelpText,
  resourceNameLabel as resourceNameLabelText,
  resourceNamePlaceholder as resourceNamePlaceholderText,
  resourceTypeIcon,
  resourceTypeText
} from '@/features/system/menuResourcePresentation'
import type { EntityId, SystemMenu, SystemMenuResourcePayload } from '@/features/system/types'
import {
  buildNavigationTree,
  filterNavigationTree,
  flattenNavigationNodes,
  sameEntityId,
  type MenuResourceKind,
  type NavigationNode
} from '@/features/system/menuNavigation'
import { useAuthStore } from '@/stores/auth'

type ResourceKind = MenuResourceKind
type ResourceDrawerMode = 'navigation' | 'permission'

interface ResourceFormState {
  menuId?: EntityId
  parentMenuId?: EntityId
  type: ResourceKind
  title: string
  code: string
  resource: string
  icon: string
  componentPath: string
  sort: number
  enable: boolean
  visible: boolean
  remark: string
}

const apiTypeMap: Record<ResourceKind, SystemMenu['type']> = {
  directory: 0,
  menu: 1,
  button: 2
}

const auth = useAuthStore()
const loading = ref(false)
const savingResource = ref(false)
const backendMenus = ref<SystemMenu[]>([])
const keyword = ref('')
const resourceTypeFilter = ref<ResourceKind | ''>('')
const enableFilter = ref<boolean | ''>('')
const visibleFilter = ref<boolean | ''>('')
const activeNodeId = ref('')
const deletingResourceId = ref<EntityId>()
const switchingResourceId = ref<EntityId>()
const resourceDrawerVisible = ref(false)
const resourceDrawerMode = ref<ResourceDrawerMode>('navigation')
const resourceFormRef = ref<FormInstance>()
const detailPanelRef = ref<HTMLElement>()
const iconKeyword = ref('')

const resourceForm = reactive<ResourceFormState>(emptyResourceForm())
const resourceRules: FormRules<ResourceFormState> = {
  title: [{ required: true, message: '请输入节点名称', trigger: 'blur' }]
}

const canEditMenu = computed(() => auth.hasAnyPermission([PermissionCodes.system.menu.edit]))
const navigationTree = computed(() => buildNavigationTree(backendMenus.value))
const navigationNodes = computed(() => flattenNavigationNodes(navigationTree.value))
const permissionTotal = computed(() => navigationNodes.value.reduce((total, node) => total + node.permissionChildren.length, 0))
const filteredNavigationTree = computed(() => filterNavigationNodesByControls(filterNavigationTree(navigationTree.value, keyword.value)))
const filteredNavigationNodes = computed(() => flattenNavigationNodes(filteredNavigationTree.value))
const activeNode = computed(() => {
  return (
    navigationNodes.value.find((node) => node.id === activeNodeId.value) ||
    navigationNodes.value.find((node) => node.type === 'menu') ||
    navigationNodes.value[0]
  )
})
const activeButtonRows = computed(() => activeNode.value?.permissionChildren.filter((node) => node.type === 'button') || [])
const navigationNodeByMenuId = computed(() => {
  return new Map(navigationNodes.value.filter((node) => node.menuId !== undefined).map((node) => [String(node.menuId), node]))
})
const navigationParentOptions = computed(() => navigationNodes.value.filter((node) => node.type === 'directory' && node.menuId !== undefined))
const menuResourceOptions = computed(() => navigationNodes.value.filter((node) => node.type === 'menu' && node.menuId !== undefined))
const isNavigationForm = computed(() => resourceForm.type === 'directory' || resourceForm.type === 'menu')
const drawerResourceTypeOptions = computed(() => (resourceDrawerMode.value === 'navigation' ? navigationResourceTypeOptions : permissionResourceTypeOptions))
const requiresParentSelector = computed(() => true)
const requiresResourcePath = computed(() => resourceForm.type === 'menu')
const requiresPermissionCode = computed(() => resourceForm.type !== 'directory')
const resourceDrawerTitle = computed(() => `${resourceForm.menuId ? '编辑' : '新增'}${resourceTypeText(resourceForm.type)}`)
const resourceNameLabel = computed(() => resourceNameLabelText(resourceForm.type))
const resourceNamePlaceholder = computed(() => resourceNamePlaceholderText(resourceForm.type))
const parentSelectorLabel = computed(() => (isNavigationForm.value ? '上级导航' : '所属页面'))
const parentSelectorPlaceholder = computed(() => (isNavigationForm.value ? '选择分组或作为顶层节点' : '请选择页面'))
const resourcePathLabel = computed(() => '页面路由')
const resourcePathPlaceholder = computed(() => '/module/page')
const resourceCodeLabel = computed(() => '权限码')
const resourceCodePlaceholder = computed(() => {
  if (resourceForm.type === 'menu') return 'module:page:view'
  return 'module:page:action'
})
const resourceFormHelp = computed(() => resourceFormHelpText(resourceForm.type))
const selectedIconLabel = computed(() => {
  const selected = menuIconOptions.find((item) => item.value === resourceForm.icon)
  return selected ? `${selected.label} · ${selected.value}` : resourceForm.icon
})
const filteredIconSections = computed(() => {
  const text = iconKeyword.value.trim().toLowerCase()
  if (!text) return menuIconSections
  return menuIconSections
    .map((section) => ({
      ...section,
      options: section.options.filter((item) => `${item.label} ${item.value}`.toLowerCase().includes(text))
    }))
    .filter((section) => section.options.length)
})

onMounted(loadMenus)

async function loadMenus() {
  loading.value = true
  try {
    backendMenus.value = await listSystemMenus()
    ensureActiveNode()
  } catch (error) {
    ElMessage.error('菜单资源加载失败')
  } finally {
    loading.value = false
  }
}

function filterNavigationNodesByControls(nodes: NavigationNode[]): NavigationNode[] {
  return nodes
    .map((node) => {
      const children = filterNavigationNodesByControls(node.children)
      const permissionHitType = node.permissionChildren.some((child) => !resourceTypeFilter.value || child.type === resourceTypeFilter.value)
      const permissionHitStatus = node.permissionChildren.some((child) => enableFilter.value === '' || child.enable === enableFilter.value)
      const hitVisible = visibleFilter.value === '' || (node.visible !== false) === visibleFilter.value
      const hitType = !resourceTypeFilter.value || node.type === resourceTypeFilter.value || permissionHitType
      const hitStatus = enableFilter.value === '' || node.enable === enableFilter.value || permissionHitStatus
      if ((hitType && hitStatus && hitVisible) || children.length) {
        return { ...node, children }
      }
      return undefined
    })
    .filter(Boolean) as NavigationNode[]
}

function ensureActiveNode() {
  const nodes = navigationNodes.value
  if (!nodes.length) {
    activeNodeId.value = ''
    return
  }
  if (!activeNodeId.value || !nodes.some((node) => node.id === activeNodeId.value)) {
    activeNodeId.value = nodes.find((node) => node.type === 'menu')?.id || nodes[0].id
  }
}

function resetFilters() {
  keyword.value = ''
  resourceTypeFilter.value = ''
  enableFilter.value = ''
  visibleFilter.value = ''
}

function selectNavigationNode(node: NavigationNode) {
  activeNodeId.value = node.id
  nextTick(() => {
    detailPanelRef.value?.scrollTo({ top: 0 })
  })
}

function openTopDirectoryDrawer() {
  openNavigationDrawer('directory')
}

function openTopPageDrawer() {
  openNavigationDrawer('menu')
}

function openNavigationDrawer(type: Extract<ResourceKind, 'directory' | 'menu'>) {
  resourceDrawerMode.value = 'navigation'
  assignResourceForm(emptyResourceForm({ type, parentMenuId: 0 }))
  resourceDrawerVisible.value = true
}

function openChildPageDrawer(parent: NavigationNode) {
  resourceDrawerMode.value = 'navigation'
  assignResourceForm(emptyResourceForm({ type: 'menu', parentMenuId: parent.menuId || 0 }))
  resourceDrawerVisible.value = true
}

function openResourceDrawer(type: ResourceKind, row?: NavigationNode, parent?: NavigationNode) {
  if (type === 'button' && parent?.type !== 'menu' && !row?.parentMenuId) {
    ElMessage.warning('请先选择一个页面')
    return
  }
  resourceDrawerMode.value = type === 'button' ? 'permission' : 'navigation'
  assignResourceForm(row ? formFromNavigationNode(row, parent) : emptyResourceForm({ type, parentMenuId: defaultParentId(type, parent) }))
  resourceDrawerVisible.value = true
}

async function submitResourceForm() {
  if (!canEditMenu.value) {
    ElMessage.warning('缺少菜单维护权限')
    return
  }
  await resourceFormRef.value?.validate()
  if (!validateResourceForm()) return

  savingResource.value = true
  try {
    await saveSystemMenuResource(toResourcePayload(resourceForm))
    ElMessage.success('菜单资源已保存')
    resourceDrawerVisible.value = false
    await loadMenus()
  } catch (error) {
    ElMessage.error('菜单资源保存失败')
  } finally {
    savingResource.value = false
  }
}

async function handleToggleResourceStatus(row: NavigationNode) {
  if (!row.menuId || !canEditMenu.value) return
  if (row.enable) {
    try {
      await ElMessageBox.confirm(`确认停用“${row.name}”？停用后对应导航入口或按钮权限将不可用。`, '停用资源', {
        confirmButtonText: '停用',
        cancelButtonText: '取消',
        type: 'warning'
      })
    } catch {
      return
    }
  }
  switchingResourceId.value = row.menuId
  try {
    await saveSystemMenuResource({ ...toPayloadFromNode(row), enable: !row.enable })
    ElMessage.success(row.enable ? '已停用' : '已启用')
    await loadMenus()
  } catch (error) {
    ElMessage.error('状态更新失败')
  } finally {
    switchingResourceId.value = undefined
  }
}

function menuStatusDisabledReason(row: NavigationNode) {
  if (!canEditMenu.value) return '缺少菜单维护权限'
  if (!row.menuId) return '资源未保存，暂不能切换状态'
  return ''
}

async function handleDeleteResource(row: NavigationNode) {
  if (!row.menuId || !canEditMenu.value) return
  if (hasDependentResources(row)) {
    ElMessage.warning('请先删除或迁移下级资源')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除「${row.name}」吗？删除后相关授权需要重新维护。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch (error) {
    if (!isMessageBoxCancel(error)) {
      ElMessage.error('删除确认失败')
    }
    return
  }

  deletingResourceId.value = row.menuId
  try {
    await deleteSystemMenuResource(row.menuId)
    ElMessage.success('菜单资源已删除')
    await loadMenus()
  } catch (error) {
    ElMessage.error('菜单资源删除失败')
  } finally {
    deletingResourceId.value = undefined
  }
}

function validateResourceForm() {
  if (!resourceForm.title.trim()) {
    ElMessage.warning('请输入节点名称')
    return false
  }
  if (resourceForm.type === 'menu' && !resourceForm.resource.trim()) {
    ElMessage.warning('请输入页面路由')
    return false
  }
  if (resourceForm.type === 'menu' && !resourceForm.componentPath.trim()) {
    ElMessage.warning('请输入页面组件路径')
    return false
  }
  if (resourceForm.type === 'menu' && !resourceForm.componentPath.trim().startsWith('@/views/')) {
    ElMessage.warning('页面组件路径必须指向 src/views')
    return false
  }
  if (requiresPermissionCode.value && !resourceForm.code.trim()) {
    ElMessage.warning(`请输入${resourceCodeLabel.value}`)
    return false
  }
  if (resourceForm.type === 'button' && !resourceForm.parentMenuId) {
    ElMessage.warning('请选择所属页面')
    return false
  }
  if (isNavigationForm.value && resourceForm.parentMenuId && !navigationParentOptions.value.some((node) => sameEntityId(node.menuId, resourceForm.parentMenuId))) {
    ElMessage.warning('请选择有效的上级导航')
    return false
  }
  return true
}

function emptyResourceForm(partial: Partial<ResourceFormState> = {}): ResourceFormState {
  return {
    type: 'menu',
    title: '',
    code: '',
    resource: '',
    icon: '',
    componentPath: '',
    sort: 10,
    enable: true,
    visible: true,
    remark: '',
    parentMenuId: 0,
    ...partial
  }
}

function assignResourceForm(next: ResourceFormState) {
  iconKeyword.value = ''
  Object.assign(resourceForm, next)
}

function formFromNavigationNode(row: NavigationNode, parent?: NavigationNode): ResourceFormState {
  return emptyResourceForm({
    menuId: row.menuId,
    parentMenuId: parent?.menuId ?? row.parentMenuId ?? 0,
    type: row.type,
    title: row.name,
    code: row.code || '',
    resource: row.resource || '',
    icon: row.icon || '',
    componentPath: row.componentPath || '',
    sort: row.sort ?? 10,
    enable: row.enable,
    visible: row.visible ?? (row.type === 'directory' || row.type === 'menu'),
    remark: row.description || ''
  })
}

function defaultParentId(type: ResourceKind, parent?: NavigationNode) {
  if (type === 'button') {
    return parent?.type === 'menu' ? parent.menuId : undefined
  }
  if (parent?.type === 'directory') {
    return parent.menuId || 0
  }
  return 0
}

function toResourcePayload(row: ResourceFormState): SystemMenuResourcePayload {
  const isNavigation = row.type === 'directory' || row.type === 'menu'
  return {
    menuId: row.menuId,
    pid: resolveFormParentId(row),
    title: row.title.trim(),
    icon: isNavigation ? row.icon.trim() : '',
    href: row.type === 'menu' ? row.resource.trim() : '',
    sort: row.sort,
    enable: row.enable,
    remark: row.remark.trim(),
    type: apiTypeMap[row.type],
    powerCode: row.type === 'directory' ? '' : row.code.trim(),
    componentPath: row.type === 'menu' ? row.componentPath.trim() : '',
    visible: isNavigation ? row.visible : false
  }
}

function toPayloadFromNode(row: NavigationNode): SystemMenuResourcePayload {
  return {
    menuId: row.menuId,
    pid: row.parentMenuId ?? 0,
    title: row.name,
    icon: row.type === 'directory' || row.type === 'menu' ? row.icon || '' : '',
    href: row.type === 'menu' ? row.resource || '' : '',
    sort: row.sort ?? 10,
    enable: row.enable,
    remark: row.description || '',
    type: apiTypeMap[row.type],
    powerCode: row.type === 'directory' ? '' : row.code,
    componentPath: row.type === 'menu' ? row.componentPath || '' : '',
    visible: row.type === 'directory' || row.type === 'menu' ? row.visible ?? true : false
  }
}

function resolveFormParentId(row: ResourceFormState) {
  if (row.type === 'button') return row.parentMenuId
  return row.parentMenuId || 0
}

function hasDependentResources(row: NavigationNode) {
  return row.children.length > 0 || row.permissionChildren.length > 0
}

function isNavigationParentDisabled(node: NavigationNode) {
  return sameEntityId(node.menuId, resourceForm.menuId) || isDescendantOf(node, resourceForm.menuId)
}

function isDescendantOf(node: NavigationNode, ancestorId?: EntityId) {
  if (ancestorId === undefined) return false
  let parentId = node.parentMenuId
  while (parentId !== undefined && parentId !== 0) {
    if (sameEntityId(parentId, ancestorId)) return true
    parentId = navigationNodeByMenuId.value.get(String(parentId))?.parentMenuId
  }
  return false
}

function parentNodeName(node: NavigationNode) {
  if (!node.parentMenuId || sameEntityId(node.parentMenuId, 0)) return '顶层导航'
  return navigationNodeByMenuId.value.get(String(node.parentMenuId))?.name || '-'
}

function treeOptionLabel(node: NavigationNode) {
  const path = nodePath(node)
  return path.length ? path.join(' / ') : node.name
}

function nodePath(node: NavigationNode): string[] {
  const parent = node.parentMenuId ? navigationNodeByMenuId.value.get(String(node.parentMenuId)) : undefined
  return parent ? [...nodePath(parent), node.name] : [node.name]
}

function navigationNodeSubtitle(node: NavigationNode) {
  if (node.type === 'directory') return node.description || '导航分组'
  return node.resource || node.code || '未配置路由'
}

function navigationNodeCountText(node: NavigationNode) {
  if (node.type === 'directory') return `${node.children.length} 项`
  const buttonTotal = node.permissionChildren.filter((child) => child.type === 'button').length
  return `${buttonTotal} 个`
}

function compactNavigationStateBadges(node: NavigationNode) {
  return navigationStateBadges(node, { showNormal: false })
}

function defaultDescription(node: NavigationNode) {
  if (node.type === 'directory') return '用于组织左侧导航结构。'
  return '页面权限用于控制入口，按钮权限在下方维护。'
}

function isMessageBoxCancel(error: unknown) {
  return error === 'cancel' || error === 'close'
}

function selectMenuIcon(value: string) {
  resourceForm.icon = value
}

function clearMenuIcon() {
  resourceForm.icon = ''
}

function resetResourceDrawerScroll() {
  document.querySelector<HTMLElement>('[data-testid="menu-resource-drawer"] .el-drawer__body')?.scrollTo({ top: 0 })
}

function resolveMenuIcon(name?: string): Component {
  const icons = ElementPlusIcons as unknown as Record<string, Component>
  return icons[name || 'Menu'] || icons.Menu
}
</script>

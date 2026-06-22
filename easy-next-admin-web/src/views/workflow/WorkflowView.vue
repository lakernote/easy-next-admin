<template>
  <section class="workflow-page">
    <div class="page-header">
      <div class="page-header-main">
        <span class="header-kicker">流程管理</span>
        <h1>流程配置</h1>
        <p>维护流程定义、设计审批图、版本发布和启停状态。</p>
      </div>
      <div class="page-header-side">
        <div class="page-actions">
          <el-button v-permission="PermissionCodes.workflow.definitionEdit" type="primary" :icon="Plus" @click="openDefinitionEditor()">
            新增
          </el-button>
        </div>
      </div>
    </div>

    <div class="workflow-board">
      <div class="workflow-board-toolbar">
        <div class="toolbar-left">
          <el-input
            v-model="definitionKeyword"
            class="workflow-list-search"
            clearable
            :prefix-icon="Search"
            placeholder="流程名称 / 标识"
            @keyup.enter="searchDefinitions"
            @clear="searchDefinitions"
          />
          <el-button :icon="Search" type="primary" plain @click="searchDefinitions">查询</el-button>
          <el-button @click="resetDefinitionQuery">重置</el-button>
          <el-select
            v-if="canEditDefinition"
            v-model="definitionStatusFilter"
            class="workflow-status-filter"
            placeholder="状态"
            @change="searchDefinitions"
          >
            <el-option v-for="option in definitionStatusOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </div>
      </div>

      <div class="workflow-board-body">
        <section ref="definitionTablePanelRef" class="definition-table-panel">
          <el-table
            v-loading="loading"
            :data="filteredDefinitions"
            :height="tableHeight"
            class="admin-table workflow-definition-table"
            row-key="id"
            highlight-current-row
            empty-text="没有匹配的流程定义"
          >
            <el-table-column label="流程定义" min-width="280">
              <template #default="{ row }">
                <div class="workflow-name-cell">
                  <div class="workflow-name-main">
                    <span :class="['workflow-dot', statusClass(row.status)]"></span>
                    <strong>{{ row.processName }}</strong>
                  </div>
                  <small>{{ row.processKey }} · v{{ row.currentVersion }}</small>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="适用说明" min-width="420" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="workflow-remark">{{ row.remark || '未填写说明，建议补充适用场景和审批口径。' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="112">
              <template #default="{ row }">
                <div class="status-control">
                  <EnableStatusSwitch
                    v-if="row.status === 'ENABLED' || row.status === 'DISABLED'"
                    :model-value="row.status === 'ENABLED'"
                    :loading="isUpdatingStatus(row)"
                    :disabled="!canEditDefinition"
                    disabled-reason="缺少流程定义维护权限"
                    :target-name="row.processName"
                    @toggle="toggleDefinitionStatus(row)"
                  />
                  <span v-else :class="['status-pill', statusClass(row.status)]">
                    {{ definitionStatusText(row.status) }}
                  </span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="updatedAt" label="更新时间" width="180" />
            <el-table-column label="操作" width="168" fixed="right" align="center" header-align="center">
              <template #default="{ row }">
                <div class="workflow-row-actions">
                  <el-button
                    v-permission:disable="PermissionCodes.workflow.definitionEdit"
                    class="workflow-edit-button"
                    text
                    type="primary"
                    :icon="EditPen"
                    @click.stop="openDefinitionEditor(row)"
                  >
                    编辑
                  </el-button>
                  <el-dropdown trigger="click" placement="bottom-end">
                    <el-button
                      v-permission:disable="PermissionCodes.workflow.definitionEdit"
                      class="workflow-more-button"
                      text
                      :icon="MoreFilled"
                      title="更多操作"
                      aria-label="更多操作"
                      @click.stop
                    >
                      更多
                    </el-button>
                    <template #dropdown>
                      <el-dropdown-menu class="workflow-definition-row-action-menu">
                        <el-dropdown-item v-if="row.status === 'DRAFT'" :disabled="!canEditDefinition || isUpdatingStatus(row)" @click.stop="toggleDefinitionStatus(row)">
                          启用流程
                        </el-dropdown-item>
                        <el-dropdown-item :disabled="!canEditDefinition || isPublishingDefinition(row)" @click.stop="publishDefinitionRow(row)">
                          发布新版本
                        </el-dropdown-item>
                        <el-dropdown-item :disabled="!canEditDefinition" @click.stop="copyDefinition(row)">
                          复制
                        </el-dropdown-item>
                        <el-dropdown-item
                          class="is-danger"
                          :disabled="!canEditDefinition || isDeletingDefinition(row)"
                          @click.stop="deleteDefinition(row)"
                        >
                          删除流程
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </template>
            </el-table-column>
            <template #empty>
              <div class="workflow-table-empty">
                <strong>暂无流程定义</strong>
                <span>先创建一个请假、报销或采购流程，再在业务入口发起申请。</span>
                <el-button v-permission="PermissionCodes.workflow.definitionEdit" type="primary" :icon="Plus" @click="openDefinitionEditor()">
                  新增流程
                </el-button>
              </div>
            </template>
          </el-table>

          <div class="table-footer workflow-pagination">
            <el-pagination
              v-model:current-page="definitionPage"
              v-model:page-size="definitionPageSize"
              background
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              :total="definitionTotal"
              @size-change="handleDefinitionPageSizeChange"
              @current-change="loadWorkflow"
            />
          </div>
        </section>
      </div>
    </div>

    <el-dialog
      v-model="editorVisible"
      class="workflow-designer-dialog"
      width="min(1480px, calc(100vw - 32px))"
      top="16px"
      append-to-body
      destroy-on-close
      :close-on-click-modal="false"
      @opened="renderEditorDesigner"
      @closed="disposeEditorDesigner"
    >
      <template #header>
        <div class="designer-dialog-title">
          <div>
            <strong>{{ definitionForm.id ? '流程设计' : '新增流程' }}</strong>
            <span>使用左侧工具箱添加节点，在右侧配置处理人和流转条件。</span>
          </div>
          <el-tag :type="definitionForm.status === 'ENABLED' ? 'success' : 'info'" effect="plain">
            {{ definitionStatusText(String(definitionForm.status || 'DRAFT')) }}
          </el-tag>
        </div>
      </template>

      <div :class="['workflow-designer', { 'is-properties-empty': selectedElementType === 'none' }]">
        <aside class="designer-sidebar">
          <section class="definition-card">
            <h3>基础信息</h3>
            <el-form label-position="top">
              <el-form-item label="流程标识">
                <el-input v-model="definitionForm.processKey" placeholder="例如 expense_approval" />
              </el-form-item>
              <el-form-item label="流程名称">
                <el-input v-model="definitionForm.processName" placeholder="例如 报销审批" />
              </el-form-item>
              <el-form-item label="状态">
                <el-segmented
                  v-model="definitionForm.status"
                  :options="[
                    { label: '草稿', value: 'DRAFT' },
                    { label: '启用', value: 'ENABLED' },
                    { label: '停用', value: 'DISABLED' }
                  ]"
                  block
                />
              </el-form-item>
              <el-form-item label="说明">
                <el-input v-model="definitionForm.remark" type="textarea" :rows="3" placeholder="用于说明适用业务和审批口径" />
              </el-form-item>
            </el-form>
          </section>

          <section class="node-palette">
            <div class="panel-title">
              <h3>节点库</h3>
              <span>点击添加到画布，可继续拖动调整位置。</span>
            </div>
            <button
              v-for="item in workflowNodePalette"
              :key="item.kind"
              :class="['palette-node', `is-${item.kind}`]"
              type="button"
              @click="addEditorNode(item)"
            >
              <i :class="`node-shape is-${item.kind}`"></i>
              <span>
                <strong>{{ item.label }}</strong>
                <small>{{ item.description }}</small>
              </span>
            </button>
          </section>
        </aside>

        <main class="designer-workspace">
          <div class="designer-actions-bar">
            <div class="designer-action-group">
              <el-button type="primary" plain @click="createLeaveApprovalTemplate">请假模板</el-button>
              <el-button @click="createFoundationFlow">基础流程</el-button>
              <el-button @click="fitEditorView">适配画布</el-button>
              <el-button @click="optimizeEditorLayout">优化布局</el-button>
              <el-button type="danger" plain @click="deleteSelectedElement">删除选中</el-button>
              <el-button plain @click="clearEditorGraph">清空画布</el-button>
            </div>
            <span class="canvas-stat">{{ editorGraphStats.nodes }} 节点 / {{ editorGraphStats.edges }} 连线</span>
          </div>
          <div class="designer-canvas-shell">
            <div ref="editorDesignerRef" class="workflow-editor-canvas"></div>
            <div v-if="editorGraphStats.nodes === 0" class="designer-empty">
              <strong>从空白流程开始</strong>
              <span>先添加“开始”和“结束”，再添加提交、审批、抄送或条件节点；选中节点后在右侧配置处理人。</span>
              <div class="designer-empty-actions">
                <el-button type="primary" @click="createLeaveApprovalTemplate">生成请假模板</el-button>
                <el-button plain @click="createFoundationFlow">生成基础流程</el-button>
              </div>
            </div>
          </div>
        </main>

        <aside class="designer-properties">
          <template v-if="selectedElementType === 'node'">
            <div class="property-title">
              <div>
                <strong>{{ nodeForm.name || '节点属性' }}</strong>
                <span>{{ nodeTypeText(nodeForm.nodeType) }}</span>
              </div>
            </div>
            <el-form class="property-form" label-position="top">
              <el-form-item label="节点名称">
                <el-input v-model="nodeForm.name" placeholder="例如 部门负责人审批" />
              </el-form-item>
              <div class="node-type-summary">
                <span :class="['node-type-badge', `is-${nodeForm.nodeType.toLowerCase()}`]">
                  {{ nodeTypeText(nodeForm.nodeType) }}
                </span>
                <small>{{ nodeTypeDescription(nodeForm.nodeType) }}</small>
              </div>

              <template v-if="nodeRequiresApprover(nodeForm.nodeType)">
                <el-form-item label="审批方式">
                  <el-select v-model="nodeForm.approveType" style="width: 100%">
                    <el-option label="任一人审批" value="ANY_ONE" />
                    <el-option label="全部审批" value="ALL" />
                    <el-option label="顺序审批" value="SEQUENTIAL" />
                  </el-select>
                </el-form-item>
                <el-form-item label="处理人规则">
                  <el-select v-model="nodeForm.approverType" style="width: 100%" @change="handleApproverTypeChange">
                    <el-option label="指定用户" value="USER" />
                    <el-option label="职能角色" value="ROLE" />
                    <el-option label="发起人直属上级" value="MANAGER" />
                    <el-option label="发起人部门负责人" value="DEPT_LEADER" />
                    <el-option label="发起人上级部门负责人" value="UPPER_DEPT_LEADER" />
                    <el-option label="发起人自选" value="INITIATOR_SELECTED" />
                  </el-select>
                </el-form-item>
                <div class="preset-actions" aria-label="常用审批人规则">
                  <button v-for="preset in approverPresets" :key="preset.type" type="button" @click="applyApproverPreset(preset)">
                    {{ preset.label }}
                  </button>
                </div>
                <div class="approver-rule-summary">
                  <span>当前规则</span>
                  <strong>{{ approverRuleTitle(nodeForm.approverType) }}</strong>
                  <small>{{ approverRuleDescription(nodeForm.approverType) }}</small>
                </div>
                <el-form-item v-if="nodeForm.approverType === 'USER'" label="指定用户">
                  <el-select v-model="nodeForm.assigneeIds" multiple filterable collapse-tags collapse-tags-tooltip placeholder="请选择一个或多个审批人" style="width: 100%">
                    <el-option v-for="user in assigneeOptions" :key="user.value" :label="user.name" :value="user.value" />
                  </el-select>
                </el-form-item>
                <el-form-item v-if="nodeForm.approverType === 'ROLE'" label="角色编码">
                  <el-input v-model="nodeForm.roleCode" placeholder="例如 dept_manager" />
                </el-form-item>
              </template>

              <template v-if="nodeForm.nodeType === 'CC'">
                <el-form-item label="抄送人">
                  <el-select v-model="nodeForm.assigneeIds" multiple filterable collapse-tags collapse-tags-tooltip placeholder="请选择抄送人" style="width: 100%">
                    <el-option v-for="user in assigneeOptions" :key="user.value" :label="user.name" :value="user.value" />
                  </el-select>
                </el-form-item>
              </template>

              <el-form-item label="节点说明">
                <el-input v-model="nodeForm.description" type="textarea" :rows="3" placeholder="描述该节点的业务含义" />
              </el-form-item>
            </el-form>
            <div class="property-actions">
              <el-button type="primary" @click="applySelectedNodeProperties">应用节点配置</el-button>
              <el-button type="danger" plain @click="deleteSelectedElement">删除节点</el-button>
            </div>
          </template>

          <template v-else-if="selectedElementType === 'edge'">
            <div class="property-title">
              <div>
                <strong>连线属性</strong>
                <span>用于展示流转名称和条件提示。</span>
              </div>
              <el-tag effect="plain">{{ shortElementId(edgeForm.id) }}</el-tag>
            </div>
            <el-form class="property-form" label-position="top">
              <el-form-item label="连线名称">
                <el-input v-model="edgeForm.label" placeholder="例如 同意 / 大于5000" />
                <p class="edge-drag-hint">拖动画布上的连线文字即可调整位置，保存后会随流程定义持久化。</p>
              </el-form-item>
              <el-form-item label="条件类型">
                <el-select v-model="edgeForm.conditionType" style="width: 100%" @change="handleEdgeConditionTypeChange">
                  <el-option label="总是流转" value="ALWAYS" />
                  <el-option label="条件表达式" value="EXPRESSION" />
                </el-select>
              </el-form-item>
              <el-form-item v-if="edgeForm.conditionType === 'EXPRESSION'" label="条件表达式">
                <el-input v-model="edgeForm.conditionExpression" placeholder="amount > 5000" />
                <div class="preset-actions is-inline" aria-label="常用条件表达式">
                  <button v-for="preset in edgeConditionPresets" :key="preset.expression" type="button" @click="applyEdgePreset(preset)">
                    {{ preset.label }}
                  </button>
                </div>
              </el-form-item>
            </el-form>
            <div class="property-actions">
              <el-button type="primary" @click="applySelectedEdgeProperties">应用名称和条件</el-button>
              <el-button type="danger" plain @click="deleteSelectedElement">删除连线</el-button>
            </div>
          </template>

          <div v-else class="workflow-inspector">
            <div class="property-title">
              <div>
                <strong>流程检查</strong>
                <span>发布前校验流程结构和审批配置。</span>
              </div>
              <el-tag :type="designerGraphDiagnostics.ready ? 'success' : 'warning'" effect="plain">
                {{ designerGraphDiagnostics.ready ? '可发布' : '待完善' }}
              </el-tag>
            </div>
            <div class="inspector-score">
              <strong>{{ designerGraphDiagnostics.score }}</strong>
              <span>配置完整度</span>
            </div>
            <div class="inspector-metrics">
              <div>
                <strong>{{ editorGraphStats.nodes }}</strong>
                <span>节点</span>
              </div>
              <div>
                <strong>{{ editorGraphStats.edges }}</strong>
                <span>连线</span>
              </div>
              <div>
                <strong>{{ designerGraphDiagnostics.approvalNodes }}</strong>
                <span>审批</span>
              </div>
            </div>
            <section class="inspector-section">
              <h4>发布前检查</h4>
              <ul class="inspector-checks">
                <li v-for="item in designerGraphDiagnostics.checks" :key="item.label" :class="{ 'is-ok': item.ok }">
                  <i></i>
                  <span>{{ item.label }}</span>
                </li>
              </ul>
            </section>
            <section class="inspector-section">
              <h4>配置建议</h4>
              <p>先使用“优化布局”规整泳道，再逐个选择审批节点配置处理人规则；条件分支建议补充表达式，便于运行态追踪。</p>
            </section>
          </div>
        </aside>
      </div>

      <template #footer>
        <div class="designer-footer">
          <el-button v-if="definitionForm.id" type="danger" plain @click="deleteCurrentDefinition">删除流程</el-button>
          <span></span>
          <el-button @click="editorVisible = false">取消</el-button>
          <el-button :loading="saving" @click="saveDefinition()">保存当前版本</el-button>
          <el-button type="primary" :loading="publishing" @click="publishDefinition">发布新版本</el-button>
        </div>
      </template>
    </el-dialog>

  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import LogicFlow, { CircleNode, CircleNodeModel, DiamondNode, DiamondNodeModel, RectNode, RectNodeModel, TextMode, h } from '@logicflow/core'
import '@logicflow/core/lib/style/index.css'
import { EditPen, MoreFilled, Plus, Search, SwitchButton } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { PermissionCodes } from '@/permissions/codes'
import EnableStatusSwitch from '@/components/table/EnableStatusSwitch.vue'
import { useFluidTableHeight } from '@/components/table/useFluidTableHeight'
import { useAuthStore } from '@/stores/auth'
import { parseWorkflowGraphJson, sanitizeWorkflowGraphForSave, validateWorkflowGraphForEnable } from '@/features/workflow/source'
import {
  deleteWorkflowDefinition,
  getWorkflowDefinition,
  listWorkflowAssignees,
  pageWorkflowDefinitions,
  publishWorkflowDefinition,
  saveWorkflowDefinition,
  updateWorkflowDefinitionStatus,
} from '@/features/workflow/api'
import type {
  WorkflowAssigneeOption,
  WorkflowDefinition,
  WorkflowGraph,
} from '@/features/workflow/types'
import type { EntityId } from '@/features/system/types'

type WorkflowNodeKind = 'start' | 'submit' | 'approval' | 'cc' | 'condition' | 'end'
type WorkflowNodeType = 'START' | 'SUBMIT' | 'APPROVAL' | 'CC' | 'CONDITION' | 'END'
type WorkflowLogicNodeType = 'workflow-circle' | 'workflow-rect' | 'workflow-diamond'
type DesignerSelectedType = 'none' | 'node' | 'edge'
type DefinitionStatusFilter = 'ALL' | 'ENABLED' | 'DRAFT' | 'DISABLED'
type WorkflowIconName = 'play' | 'file' | 'user' | 'notify' | 'branch' | 'stop'
type WorkflowIconModel = RectNodeModel | CircleNodeModel | DiamondNodeModel
type EdgeLabelPlacement = 'AUTO' | 'ABOVE' | 'BELOW' | 'START' | 'END' | 'CUSTOM'
const workflowEditorEdgeType = 'polyline'

interface WorkflowRoutePoint {
  x: number
  y: number
}

interface WorkflowEdgeRoute {
  startPoint?: WorkflowRoutePoint
  endPoint?: WorkflowRoutePoint
  pointsList: WorkflowRoutePoint[]
}

interface WorkflowNodeDragStart {
  id: string
  x: number
  y: number
}

interface WorkflowPaletteItem {
  kind: WorkflowNodeKind
  label: string
  description: string
  logicType: WorkflowLogicNodeType
  nodeType: WorkflowNodeType
  defaultName: string
}

interface ApproverPreset {
  label: string
  type: string
  roleCode?: string
}

interface EdgeConditionPreset {
  label: string
  expression: string
}

interface DesignerNodeForm {
  id: string
  name: string
  nodeType: WorkflowNodeType
  approveType: string
  approverType: string
  assigneeIds: EntityId[]
  roleCode: string
  description: string
}

interface DesignerEdgeForm {
  id: string
  label: string
  conditionType: string
  conditionExpression: string
  labelPlacement: EdgeLabelPlacement
  labelOffsetX: number
  labelOffsetY: number
}

const workflowNodePalette: WorkflowPaletteItem[] = [
  { kind: 'start', label: '开始', description: '流程入口，只保留一个', logicType: 'workflow-circle', nodeType: 'START', defaultName: '开始' },
  { kind: 'submit', label: '提交节点', description: '发起人提交业务单据', logicType: 'workflow-rect', nodeType: 'SUBMIT', defaultName: '提交申请' },
  { kind: 'approval', label: '审批节点', description: '指定用户、角色或上级审批', logicType: 'workflow-rect', nodeType: 'APPROVAL', defaultName: '审批节点' },
  { kind: 'cc', label: '抄送节点', description: '通知相关人，不产生审批', logicType: 'workflow-rect', nodeType: 'CC', defaultName: '抄送' },
  { kind: 'condition', label: '条件分支', description: '按金额、类型等条件分流', logicType: 'workflow-diamond', nodeType: 'CONDITION', defaultName: '条件判断' },
  { kind: 'end', label: '结束', description: '流程通过后的结束点', logicType: 'workflow-circle', nodeType: 'END', defaultName: '结束' }
]
const approverPresets: ApproverPreset[] = [
  { label: '直属上级', type: 'MANAGER' },
  { label: '部门负责人', type: 'DEPT_LEADER' },
  { label: '上级部门负责人', type: 'UPPER_DEPT_LEADER' },
  { label: '发起人自选', type: 'INITIATOR_SELECTED' },
  { label: '财务/审计角色', type: 'ROLE', roleCode: 'auditor' }
]
const edgeDefaultColor = '#64748b'
const edgeConditionPresets: EdgeConditionPreset[] = [
  { label: '3 天内', expression: 'days <= 3' },
  { label: '超过 3 天', expression: 'days > 3' },
  { label: '金额小于等于 5000', expression: 'amount <= 5000' },
  { label: '金额超过 5000', expression: 'amount > 5000' }
]
const workflowLayoutXStart = 116
const workflowLayoutXGap = 240
const workflowLayoutCenterY = 296
const workflowLayoutYGap = 150
const workflowConditionSplitOffset = 72
const workflowMergeJoinOffset = 58
const workflowMergeLayerSpan = 1
const workflowLaneStackGap = 82

const auth = useAuthStore()
const editorDesignerRef = ref<HTMLDivElement>()
const definitionTablePanelRef = ref<HTMLElement>()
const { tableHeight, updateTableHeight } = useFluidTableHeight(definitionTablePanelRef)
const definitions = ref<WorkflowDefinition[]>([])
const assigneeOptions = ref<WorkflowAssigneeOption[]>([])
const definitionKeyword = ref('')
const definitionStatusFilter = ref<DefinitionStatusFilter>('ALL')
const definitionPage = ref(1)
const definitionPageSize = ref(10)
const definitionTotal = ref(0)
const loading = ref(false)
const saving = ref(false)
const publishing = ref(false)
const updatingStatusId = ref('')
const publishingDefinitionId = ref('')
const deletingDefinitionId = ref('')
const editorVisible = ref(false)
const definitionForm = reactive<Partial<WorkflowDefinition>>({})
const editorGraphStats = reactive({ nodes: 0, edges: 0 })
const editorGraphSnapshot = ref<WorkflowGraph>({ nodes: [], edges: [] })
const selectedElementType = ref<DesignerSelectedType>('none')
let editorEdgeRouteSnapshot = new Map<string, WorkflowEdgeRoute>()
let editorNodeDragStart: WorkflowNodeDragStart | undefined
let editorNodeDragged = false
let restoringEditorEdgeRoutes = false
const nodeForm = reactive<DesignerNodeForm>({
  id: '',
  name: '',
  nodeType: 'APPROVAL',
  approveType: 'ANY_ONE',
  approverType: 'USER',
  assigneeIds: [],
  roleCode: '',
  description: ''
})
const edgeForm = reactive<DesignerEdgeForm>({
  id: '',
  label: '',
  conditionType: 'ALWAYS',
  conditionExpression: '',
  labelPlacement: 'AUTO',
  labelOffsetX: 0,
  labelOffsetY: 0
})
let editorLogicFlow: LogicFlow | null = null

class WorkflowRectNode extends RectNode {
  getShape() {
    return h('g', {}, [super.getShape(), renderWorkflowNodeIcon(this.props.model)])
  }
}

class WorkflowCircleNode extends CircleNode {
  getShape() {
    return h('g', {}, [super.getShape(), renderWorkflowNodeIcon(this.props.model)])
  }
}

class WorkflowDiamondNode extends DiamondNode {
  getShape() {
    return h('g', {}, [super.getShape(), renderWorkflowNodeIcon(this.props.model)])
  }
}

const canEditDefinition = computed(() => auth.hasAnyPermission([PermissionCodes.workflow.definitionEdit]))
const startableDefinitions = computed(() => definitions.value.filter((item) => item.status === 'ENABLED'))
const visibleDefinitions = computed(() => (canEditDefinition.value ? definitions.value : startableDefinitions.value))
const definitionStatusOptions = [
  { label: '全部', value: 'ALL' },
  { label: '启用', value: 'ENABLED' },
  { label: '草稿', value: 'DRAFT' },
  { label: '停用', value: 'DISABLED' }
] satisfies Array<{ label: string; value: DefinitionStatusFilter }>
const filteredDefinitions = computed(() => visibleDefinitions.value)
const designerGraphDiagnostics = computed(() => {
  const graph = editorGraphSnapshot.value
  const nodeTypes = graph.nodes.map((node) => inferNodeType(node))
  const approvalNodes = graph.nodes.filter((node) => inferNodeType(node) === 'APPROVAL')
  const configuredApprovals = approvalNodes.filter((node) => approvalNodeConfigured(node)).length
  const conditionalEdges = graph.edges.filter((edge) => {
    const properties = graphProperties(edge)
    return stringProperty(properties.conditionType) === 'EXPRESSION' || Boolean(stringProperty(properties.conditionExpression))
  })
  const checks = [
    { label: '包含开始节点', ok: nodeTypes.includes('START') },
    { label: '包含结束节点', ok: nodeTypes.includes('END') },
    { label: '连线覆盖主干路径', ok: graph.nodes.length > 1 && graph.edges.length >= graph.nodes.length - 1 },
    { label: '审批节点已配置处理人', ok: approvalNodes.length === 0 || configuredApprovals === approvalNodes.length },
    { label: '条件分支已配置表达式', ok: !nodeTypes.includes('CONDITION') || conditionalEdges.length > 0 }
  ]
  const passed = checks.filter((item) => item.ok).length
  return {
    checks,
    approvalNodes: approvalNodes.length,
    ready: checks.every((item) => item.ok),
    score: graph.nodes.length ? `${Math.round((passed / checks.length) * 100)}%` : '0%'
  }
})
onMounted(loadWorkflow)
onBeforeUnmount(() => {
  editorLogicFlow = null
})

async function loadWorkflow() {
  loading.value = true
  try {
    const [definitionResult, assignees] = await Promise.all([
      pageWorkflowDefinitions({
        page: definitionPage.value,
        limit: definitionPageSize.value,
        keyword: definitionKeyword.value.trim() || undefined,
        status: definitionStatusFilter.value === 'ALL' ? undefined : definitionStatusFilter.value
      }),
      listWorkflowAssignees()
    ])
    definitions.value = definitionResult.list
    definitionTotal.value = definitionResult.total
    assigneeOptions.value = assignees
      .map(normalizeAssigneeOption)
      .filter((item): item is WorkflowAssigneeOption => Boolean(item))
  } finally {
    loading.value = false
    updateTableHeight()
  }
}

function searchDefinitions() {
  definitionPage.value = 1
  void loadWorkflow()
}

function resetDefinitionQuery() {
  definitionKeyword.value = ''
  definitionStatusFilter.value = 'ALL'
  definitionPage.value = 1
  void loadWorkflow()
}

function handleDefinitionPageSizeChange() {
  definitionPage.value = 1
  void loadWorkflow()
}

async function renderEditorDesigner() {
  await nextTick()
  if (!editorDesignerRef.value) return
  editorDesignerRef.value.innerHTML = ''
  editorLogicFlow = new LogicFlow({
    container: editorDesignerRef.value,
    grid: { size: 12, visible: false },
    snapGrid: true,
    edgeType: workflowEditorEdgeType,
    adjustEdge: false,
    adjustEdgeStartAndEnd: false,
    adjustEdgeMiddle: false,
    hideAnchors: false,
    edgeTextMode: TextMode.TEXT,
    edgeTextDraggable: true,
    edgeTextEdit: false,
    snapline: true,
    snaplineEpsilon: 6,
    outline: true,
    keyboard: { enabled: true },
    edgeGenerator: () => workflowEditorEdgeType,
    stopScrollGraph: false,
    stopZoomGraph: false,
    style: {
      baseEdge: {
        stroke: edgeDefaultColor,
        strokeWidth: 1.8,
        strokeLinecap: 'round',
        strokeLinejoin: 'round'
      },
      arrow: {
        offset: 11,
        verticalLength: 6,
        strokeLinecap: 'round',
        strokeLinejoin: 'round'
      },
      anchorLine: {
        stroke: edgeDefaultColor,
        strokeWidth: 1.8,
        strokeLinecap: 'round',
        strokeLinejoin: 'round'
      },
      line: {
        stroke: edgeDefaultColor,
        strokeWidth: 1.8,
        strokeLinecap: 'round',
        strokeLinejoin: 'round'
      },
      polyline: {
        stroke: edgeDefaultColor,
        strokeWidth: 1.8,
        strokeLinecap: 'round',
        strokeLinejoin: 'round'
      },
      bezier: {
        stroke: edgeDefaultColor,
        strokeWidth: 1.8,
        strokeLinecap: 'round',
        strokeLinejoin: 'round'
      },
      edgeText: {
        fontSize: 12,
        fontWeight: 700,
        textWidth: 88,
        background: {
          fill: '#ffffff',
          stroke: '#dbe7f5',
          strokeWidth: 1,
          radius: 4,
          wrapPadding: '3px 6px'
        }
      }
    }
  })
  registerWorkflowDesignerNodes(editorLogicFlow)
  editorLogicFlow.on('node:click', ({ data }: { data: Record<string, unknown> }) => selectEditorNode(data))
  editorLogicFlow.on('node:dragstart', ({ data }: { data: Record<string, unknown> }) => trackEditorNodeDragStart(data))
  editorLogicFlow.on('node:drag', ({ deltaX, deltaY }: { deltaX?: number; deltaY?: number }) => trackEditorNodeDrag(deltaX, deltaY))
  editorLogicFlow.on('node:drop', ({ data }: { data: Record<string, unknown> }) => settleEditorNodeDrop(data))
  editorLogicFlow.on('edge:click', ({ data }: { data: Record<string, unknown> }) => selectEditorEdge(data))
  editorLogicFlow.on('edge:add', ({ data }: { data: Record<string, unknown> }) => normalizeAddedEditorEdge(data))
  editorLogicFlow.on('text:drop', handleEditorTextDrop)
  editorLogicFlow.on('blank:click', clearEditorSelection)
  const graph = parseGraph(definitionForm.graphJson as string)
  const displayGraph = graph.nodes.length ? applyWorkflowGraphVisuals(graph) : graph
  editorLogicFlow.render(displayGraph as unknown as Parameters<LogicFlow['render']>[0])
  rememberEditorEdgeRoutes(displayGraph)
  refreshEditorGraphStats()
  scheduleEditorFitView()
}

function disposeEditorDesigner() {
  editorLogicFlow = null
  editorEdgeRouteSnapshot = new Map()
  editorNodeDragStart = undefined
  editorNodeDragged = false
  clearEditorSelection()
  editorGraphStats.nodes = 0
  editorGraphStats.edges = 0
  editorGraphSnapshot.value = { nodes: [], edges: [] }
}

function parseGraph(graphJson?: string): WorkflowGraph {
  return applyWorkflowGraphVisuals(parseWorkflowGraphJson(graphJson))
}

async function openDefinitionEditor(item?: WorkflowDefinition) {
  Object.keys(definitionForm).forEach((key) => delete definitionForm[key as keyof WorkflowDefinition])
  const detail = item?.id ? await getWorkflowDefinition(item.id) : undefined
  Object.assign(definitionForm, detail || {
    processKey: '',
    processName: '',
    currentVersion: 1,
    status: 'DRAFT',
    remark: '',
    graphJson: JSON.stringify({ nodes: [], edges: [] })
  })
  clearEditorSelection()
  editorVisible.value = true
}

async function copyDefinition(item: WorkflowDefinition) {
  if (!canEditDefinition.value) {
    ElMessage.warning('没有流程定义维护权限')
    return
  }
  const detail = await getWorkflowDefinition(item.id)
  const nextKey = uniqueProcessKey(`${detail.processKey}_copy`)
  Object.keys(definitionForm).forEach((key) => delete definitionForm[key as keyof WorkflowDefinition])
  Object.assign(definitionForm, {
    ...detail,
    id: undefined,
    processKey: nextKey,
    processName: `${detail.processName}副本`,
    status: 'DRAFT',
    remark: detail.remark ? `${detail.remark}（由 ${detail.processName} 复制）` : `由 ${detail.processName} 复制`
  })
  clearEditorSelection()
  editorVisible.value = true
}

async function saveDefinition(targetStatus?: 'DRAFT' | 'ENABLED' | 'DISABLED') {
  const payload = await buildDefinitionSavePayload(targetStatus)
  if (!payload) {
    return
  }
  saving.value = true
  try {
    await saveWorkflowDefinition(payload)
    ElMessage.success('当前版本已保存')
    editorVisible.value = false
    await loadWorkflow()
  } finally {
    saving.value = false
  }
}

async function publishDefinition() {
  const payload = await buildDefinitionSavePayload('ENABLED')
  if (!payload) {
    return
  }
  publishing.value = true
  try {
    const savedDefinition = await saveWorkflowDefinition(payload)
    const publishedDefinition = await publishWorkflowDefinition(savedDefinition.id)
    Object.assign(definitionForm, publishedDefinition)
    ElMessage.success(`已发布 v${publishedDefinition.currentVersion}`)
    editorVisible.value = false
    await loadWorkflow()
  } finally {
    publishing.value = false
  }
}

async function buildDefinitionSavePayload(targetStatus?: 'DRAFT' | 'ENABLED' | 'DISABLED') {
  if (!definitionForm.processKey || !definitionForm.processName) {
    ElMessage.warning('请填写流程标识和流程名称')
    return undefined
  }
  if (targetStatus) {
    definitionForm.status = targetStatus
  }
  await flushEditorTextDrop()
  const rawGraph = editorLogicFlow
    ? syncDraggedEdgeLabelLayouts(editorLogicFlow.getGraphData() as WorkflowGraph)
    : parseGraph(definitionForm.graphJson)
  const graph = sanitizeWorkflowGraphForSave(
    applyWorkflowGraphVisuals(rawGraph)
  )
  if (!validateDefinitionGraph(graph, definitionForm.status as string)) {
    return undefined
  }
  return {
    ...definitionForm,
    graphJson: JSON.stringify(graph)
  }
}

async function deleteDefinition(row: WorkflowDefinition) {
  const deleted = await confirmDeleteDefinition(row.id, row.processName)
  if (!deleted) return
  if (definitions.value.length === 1 && definitionPage.value > 1) {
    definitionPage.value -= 1
  }
  await loadWorkflow()
}

async function deleteCurrentDefinition() {
  if (!definitionForm.id) {
    clearEditorGraph()
    editorVisible.value = false
    return
  }
  const deleted = await confirmDeleteDefinition(definitionForm.id, definitionForm.processName)
  if (!deleted) return
  editorVisible.value = false
  if (definitions.value.length === 1 && definitionPage.value > 1) {
    definitionPage.value -= 1
  }
  await loadWorkflow()
}

async function confirmDeleteDefinition(id: WorkflowDefinition['id'], processName?: string) {
  const targetName = processName ? `“${processName}”` : '该流程定义'
  try {
    await ElMessageBox.confirm(`确认删除 ${targetName}？删除后不可恢复；已有流程实例的定义会被后端拦截，建议生产环境优先停用。`, '删除流程定义', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return false
  }
  deletingDefinitionId.value = String(id)
  try {
    await deleteWorkflowDefinition(id)
    ElMessage.success('流程定义已删除')
    return true
  } finally {
    deletingDefinitionId.value = ''
  }
}

function isUpdatingStatus(row: WorkflowDefinition) {
  return updatingStatusId.value === String(row.id)
}

function isDeletingDefinition(row: WorkflowDefinition) {
  return deletingDefinitionId.value === String(row.id)
}

function isPublishingDefinition(row: WorkflowDefinition) {
  return publishingDefinitionId.value === String(row.id)
}

async function publishDefinitionRow(row: WorkflowDefinition) {
  if (!canEditDefinition.value) {
    ElMessage.warning('没有流程定义维护权限')
    return
  }
  const detail = await getWorkflowDefinition(row.id)
  const graph = parseGraph(detail.graphJson)
  if (!validateDefinitionGraph(graph, 'ENABLED')) {
    return
  }
  try {
    await ElMessageBox.confirm(`确认基于当前设计发布“${row.processName}”的新版本？发布后新发起流程将使用新版本。`, '发布新版本', {
      confirmButtonText: '发布',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  publishingDefinitionId.value = String(row.id)
  try {
    const publishedDefinition = await publishWorkflowDefinition(row.id)
    ElMessage.success(`已发布 v${publishedDefinition.currentVersion}`)
    await loadWorkflow()
  } finally {
    publishingDefinitionId.value = ''
  }
}

async function toggleDefinitionStatus(row: WorkflowDefinition) {
  const nextStatus = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  if (nextStatus === 'ENABLED') {
    const detail = await getWorkflowDefinition(row.id)
    const graph = parseGraph(detail.graphJson)
    if (!validateDefinitionGraph(graph, 'ENABLED')) {
      return
    }
  }
  if (nextStatus === 'DISABLED') {
    try {
      await ElMessageBox.confirm('停用后，新申请不能再选择该流程；已发起实例不受影响。', '停用流程', {
        confirmButtonText: '停用',
        cancelButtonText: '取消',
        type: 'warning'
      })
    } catch {
      return
    }
  }
  updatingStatusId.value = String(row.id)
  try {
    await updateWorkflowDefinitionStatus(row.id, nextStatus)
    ElMessage.success(nextStatus === 'ENABLED' ? '流程已启用' : '流程已停用')
    await loadWorkflow()
  } finally {
    updatingStatusId.value = ''
  }
}

function addEditorNode(item: WorkflowPaletteItem) {
  if (!editorLogicFlow) return
  const graph = editorLogicFlow.getGraphData() as WorkflowGraph
  if ((item.kind === 'start' || item.kind === 'end') && graph.nodes.some((node) => inferNodeType(node) === item.nodeType)) {
    ElMessage.warning(`${item.label}节点只能保留一个`)
    return
  }
  const selectedNode = selectedEditorNode()
  const position = nextNodePosition(selectedNode)
  const id = uniqueNodeId(item.kind, graph)
  const node = {
    id,
    type: item.logicType,
    x: position.x,
    y: position.y,
    text: item.defaultName,
    properties: defaultNodeProperties(item)
  }
  editorLogicFlow.addNode(node)
  if (selectedNode && item.kind !== 'start') {
    editorLogicFlow.addEdge({
      id: uniqueEdgeId(graph),
      type: workflowEditorEdgeType,
      sourceNodeId: graphNodeId(selectedNode),
      targetNodeId: id,
      text: '',
      properties: edgeVisualProperties()
    })
  }
  editorLogicFlow.selectElementById(id)
  selectEditorNode(node)
  refreshEditorGraphStats()
  rememberEditorEdgeRoutes()
}

function createFoundationFlow() {
  if (!editorLogicFlow) return
  const replace = editorGraphStats.nodes > 0
  const run = () => {
    const graph: WorkflowGraph = {
      nodes: [
        { id: 'start', type: 'circle', x: 120, y: 210, text: '开始', properties: defaultNodeProperties(workflowNodePalette[0]) },
        { id: 'submit', type: 'rect', x: 320, y: 210, text: '提交申请', properties: defaultNodeProperties(workflowNodePalette[1]) },
        { id: 'approve', type: 'rect', x: 540, y: 210, text: '部门负责人审批', properties: {
          ...defaultNodeProperties(workflowNodePalette[2]),
          approverType: 'DEPT_LEADER'
        } },
        { id: 'end', type: 'circle', x: 780, y: 210, text: '结束', properties: defaultNodeProperties(workflowNodePalette[5]) }
      ],
      edges: [
        { id: 'edge_start_submit', sourceNodeId: 'start', targetNodeId: 'submit', type: workflowEditorEdgeType },
        { id: 'edge_submit_approve', sourceNodeId: 'submit', targetNodeId: 'approve', type: workflowEditorEdgeType },
        { id: 'edge_approve_end', sourceNodeId: 'approve', targetNodeId: 'end', type: workflowEditorEdgeType, text: '同意' }
      ]
    }
    const displayGraph = applyWorkflowGraphVisuals(optimizeWorkflowGraphLayout(graph))
    editorLogicFlow?.render(displayGraph as unknown as Parameters<LogicFlow['render']>[0])
    rememberEditorEdgeRoutes(displayGraph)
    editorLogicFlow?.selectElementById('approve')
    selectEditorNode(graph.nodes[2])
    refreshEditorGraphStats()
    scheduleEditorFitView()
  }
  if (!replace) {
    run()
    return
  }
  ElMessageBox.confirm('生成基础流程会替换当前画布内容。', '替换画布', {
    confirmButtonText: '替换',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(run)
    .catch(() => undefined)
}

function createLeaveApprovalTemplate() {
  if (!editorLogicFlow) return
  const run = () => {
    applyLeaveTemplateMetadata()
    const graph: WorkflowGraph = optimizeWorkflowGraphLayout({
      nodes: [
        { id: 'start', type: 'circle', x: 120, y: 260, text: '开始', properties: defaultNodeProperties(workflowNodePalette[0]) },
        { id: 'submit', type: 'rect', x: 320, y: 260, text: '提交请假', properties: defaultNodeProperties(workflowNodePalette[1]) },
        { id: 'days_condition', type: 'diamond', x: 520, y: 260, text: '时长判断', properties: {
          ...defaultNodeProperties(workflowNodePalette[4]),
          description: '按请假天数自动分支，3 天内走部门审批，超过 3 天追加总经办复核。'
        } },
        { id: 'dept_approve', type: 'rect', x: 720, y: 170, text: '部门负责人审批', properties: {
          ...defaultNodeProperties(workflowNodePalette[2]),
          approverType: 'DEPT_LEADER',
          description: '部门负责人处理 3 天内请假。'
        } },
        { id: 'dept_review', type: 'rect', x: 720, y: 350, text: '部门负责人复核', properties: {
          ...defaultNodeProperties(workflowNodePalette[2]),
          approverType: 'DEPT_LEADER',
          description: '部门先审核超过 3 天的请假。'
        } },
        { id: 'office_approve', type: 'rect', x: 920, y: 350, text: '总经办审批', properties: {
          ...defaultNodeProperties(workflowNodePalette[2]),
          approverType: 'ROLE',
          roleCode: 'admin',
          description: '超过 3 天追加总经办审批。'
        } },
        { id: 'cc_admin', type: 'rect', x: 1110, y: 260, text: '行政备案', properties: {
          ...defaultNodeProperties(workflowNodePalette[3]),
          description: '流程通过后抄送行政备案。'
        } },
        { id: 'end', type: 'circle', x: 1290, y: 260, text: '结束', properties: defaultNodeProperties(workflowNodePalette[5]) }
      ],
      edges: [
        { id: 'edge_start_submit', sourceNodeId: 'start', targetNodeId: 'submit', type: workflowEditorEdgeType },
        { id: 'edge_submit_condition', sourceNodeId: 'submit', targetNodeId: 'days_condition', type: workflowEditorEdgeType },
        { id: 'edge_condition_dept', sourceNodeId: 'days_condition', targetNodeId: 'dept_approve', type: workflowEditorEdgeType, text: '3天内', properties: { conditionType: 'EXPRESSION', conditionExpression: 'days <= 3', labelPlacement: 'START' } },
        { id: 'edge_condition_review', sourceNodeId: 'days_condition', targetNodeId: 'dept_review', type: workflowEditorEdgeType, text: '默认/超过3天', properties: { conditionType: 'ALWAYS', labelPlacement: 'AUTO' } },
        { id: 'edge_dept_cc', sourceNodeId: 'dept_approve', targetNodeId: 'cc_admin', type: workflowEditorEdgeType, text: '通过' },
        { id: 'edge_review_office', sourceNodeId: 'dept_review', targetNodeId: 'office_approve', type: workflowEditorEdgeType, text: '复核通过' },
        { id: 'edge_office_cc', sourceNodeId: 'office_approve', targetNodeId: 'cc_admin', type: workflowEditorEdgeType, text: '通过' },
        { id: 'edge_cc_end', sourceNodeId: 'cc_admin', targetNodeId: 'end', type: workflowEditorEdgeType }
      ]
    })
    const displayGraph = applyWorkflowGraphVisuals(graph)
    editorLogicFlow?.render(displayGraph as unknown as Parameters<LogicFlow['render']>[0])
    rememberEditorEdgeRoutes(displayGraph)
    editorLogicFlow?.selectElementById('dept_approve')
    selectEditorNode(graph.nodes[3])
    refreshEditorGraphStats()
    fitEditorView()
  }
  if (editorGraphStats.nodes === 0) {
    run()
    return
  }
  ElMessageBox.confirm('生成请假模板会替换当前画布内容。', '替换画布', {
    confirmButtonText: '替换',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(run)
    .catch(() => undefined)
}

function applyLeaveTemplateMetadata() {
  if (!definitionForm.processName) {
    definitionForm.processName = '请假审批'
  }
  if (!definitionForm.processKey) {
    definitionForm.processKey = uniqueProcessKey('leave_approval')
  }
  if (!definitionForm.remark) {
    definitionForm.remark = '按请假天数分流：3 天内部门负责人审批，超过 3 天追加总经办审批，并保留加签、转办、委派、退回和抄送记录。'
  }
}

function uniqueProcessKey(base: string) {
  const normalized = base
    .trim()
    .replace(/[^a-zA-Z0-9_]/g, '_')
    .replace(/_+/g, '_')
    .replace(/^_|_$/g, '') || 'workflow'
  const reserved = new Set(definitions.value.map((item) => item.processKey))
  if (!reserved.has(normalized)) return normalized
  const suffix = Date.now().toString().slice(-6)
  return `${normalized}_${suffix}`
}

function fitEditorView() {
  scheduleEditorFitView(44)
}

function scheduleEditorFitView(offset = 44) {
  if (!editorLogicFlow) return
  window.requestAnimationFrame(() => {
    if (!editorLogicFlow) return
    editorLogicFlow.resize()
    editorLogicFlow.fitView(offset, offset)
    window.requestAnimationFrame(() => {
      if (!editorLogicFlow) return
      editorLogicFlow.resize()
      editorLogicFlow.fitView(offset, offset)
    })
  })
}

function optimizeEditorLayout() {
  if (!editorLogicFlow) return
  if (editorGraphStats.nodes === 0) {
    ElMessage.warning('请先添加节点')
    return
  }
  const graph = editorLogicFlow.getGraphData() as WorkflowGraph
  const optimizedGraph = applyWorkflowGraphVisuals(optimizeWorkflowGraphLayout(graph))
  editorLogicFlow.render(optimizedGraph as unknown as Parameters<LogicFlow['render']>[0])
  rememberEditorEdgeRoutes(optimizedGraph)
  clearEditorSelection()
  refreshEditorGraphStats()
  scheduleEditorFitView()
  ElMessage.success('已按流程层级优化布局')
}

function deleteSelectedElement() {
  if (!editorLogicFlow) return
  const selected = editorLogicFlow.getSelectElements(true) as { nodes: Array<Record<string, unknown>>; edges: Array<Record<string, unknown>> }
  const edgeIds = selected.edges.map((edge) => graphNodeId(edge)).filter(Boolean)
  const nodeIds = selected.nodes.map((node) => graphNodeId(node)).filter(Boolean)
  if (edgeIds.length === 0 && nodeIds.length === 0) {
    ElMessage.warning('请先选中节点或连线')
    return
  }
  edgeIds.forEach((id) => editorLogicFlow?.deleteEdge(id))
  nodeIds.forEach((id) => editorLogicFlow?.deleteNode(id))
  clearEditorSelection()
  refreshEditorGraphStats()
}

function clearEditorGraph() {
  if (!editorLogicFlow) return
  if (editorGraphStats.nodes === 0) return
  ElMessageBox.confirm('清空后需要重新添加节点和连线。', '清空画布', {
    confirmButtonText: '清空',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(() => {
      editorLogicFlow?.clearData()
      clearEditorSelection()
      refreshEditorGraphStats()
    })
    .catch(() => undefined)
}

function selectEditorNode(data: Record<string, unknown>) {
  const properties = graphProperties(data)
  const nodeType = stringProperty(properties.nodeType) || inferNodeType(data)
  const assigneeIds = nodeType === 'CC' ? toEntityIds(properties.ccUserIds) : toEntityIds(properties.assigneeIds)
  const approverType = stringProperty(properties.approverType) || defaultApproverType(nodeType)
  selectedElementType.value = 'node'
  Object.assign(nodeForm, {
    id: graphNodeId(data),
    name: graphNodeText(data, graphNodeId(data)),
    nodeType,
    approveType: stringProperty(properties.approveType) || 'ANY_ONE',
    approverType,
    assigneeIds,
    roleCode: stringProperty(properties.roleCode),
    description: stringProperty(properties.description)
  })
  restoreEditorEdgeRoutesSoon()
}

function selectEditorEdge(data: Record<string, unknown>) {
  const properties = graphProperties(data)
  const conditionType = stringProperty(properties.conditionType) || 'ALWAYS'
  const conditionExpression = stringProperty(properties.conditionExpression)
  selectedElementType.value = 'edge'
  Object.assign(edgeForm, {
    id: graphNodeId(data),
    label: graphNodeText(data, ''),
    conditionType,
    conditionExpression,
    labelPlacement: normalizeEdgeLabelPlacement(properties.labelPlacement),
    labelOffsetX: numberProperty(properties.labelOffsetX, 0),
    labelOffsetY: numberProperty(properties.labelOffsetY, 0)
  })
}

function normalizeAddedEditorEdge(data: Record<string, unknown>) {
  const edgeId = graphNodeId(data)
  if (!editorLogicFlow || !edgeId) return
  const properties = graphProperties(data)
  const style = recordProperty(properties.style)
  if (style.stroke === edgeDefaultColor && style.strokeWidth === 1.8) return
  editorLogicFlow.setProperties(edgeId, {
    ...properties,
    ...edgeVisualProperties()
  })
  rememberEditorEdgeRoutes()
  refreshEditorGraphStats()
}

function trackEditorNodeDragStart(data: Record<string, unknown>) {
  editorNodeDragged = false
  editorNodeDragStart = {
    id: graphNodeId(data),
    x: graphNodeNumber(data.x, 0),
    y: graphNodeNumber(data.y, 0)
  }
}

function trackEditorNodeDrag(deltaX?: number, deltaY?: number) {
  if (Math.abs(deltaX || 0) > 0.5 || Math.abs(deltaY || 0) > 0.5) {
    editorNodeDragged = true
  }
}

function settleEditorNodeDrop(data: Record<string, unknown>) {
  const moved = editorNodeDragged || Boolean(editorNodeDragStart && (
    Math.abs(graphNodeNumber(data.x, editorNodeDragStart.x) - editorNodeDragStart.x) > 0.5 ||
    Math.abs(graphNodeNumber(data.y, editorNodeDragStart.y) - editorNodeDragStart.y) > 0.5
  ))
  editorNodeDragStart = undefined
  editorNodeDragged = false
  if (moved) {
    window.requestAnimationFrame(() => rememberEditorEdgeRoutes())
    return
  }
  restoreEditorEdgeRoutesSoon()
}

function rememberEditorEdgeRoutes(graph?: WorkflowGraph) {
  const currentGraph = graph || (editorLogicFlow?.getGraphData() as WorkflowGraph | undefined)
  if (!currentGraph?.edges?.length) {
    editorEdgeRouteSnapshot = new Map()
    return
  }
  editorEdgeRouteSnapshot = new Map(
    currentGraph.edges
      .map((edge) => {
        const id = graphNodeId(edge)
        const route = edgeRouteFromEdge(edge)
        return id && route ? [id, route] as const : undefined
      })
      .filter((item): item is readonly [string, WorkflowEdgeRoute] => Boolean(item))
  )
}

function edgeRouteFromEdge(edge: Record<string, unknown>): WorkflowEdgeRoute | undefined {
  const pointsList = cloneWorkflowPoints(edgePointsFromEdge(edge))
  if (!pointsList.length) return undefined
  const startPoint = cloneWorkflowPoint(pointProperty(edge.startPoint) || pointsList[0])
  const endPoint = cloneWorkflowPoint(pointProperty(edge.endPoint) || pointsList[pointsList.length - 1])
  return { startPoint, endPoint, pointsList }
}

function restoreEditorEdgeRoutesSoon() {
  restoreEditorEdgeRoutes()
  void Promise.resolve().then(restoreEditorEdgeRoutes)
}

function restoreEditorEdgeRoutes() {
  if (!editorLogicFlow || restoringEditorEdgeRoutes || editorEdgeRouteSnapshot.size === 0) return
  restoringEditorEdgeRoutes = true
  try {
    editorEdgeRouteSnapshot.forEach((route, edgeId) => {
      const edgeModel = editorLogicFlow?.graphModel.getEdgeModelById(edgeId) as unknown as {
        startPoint?: WorkflowRoutePoint
        endPoint?: WorkflowRoutePoint
        pointsList?: WorkflowRoutePoint[]
        points?: string
        updatePath?: (points: WorkflowRoutePoint[]) => void
      } | undefined
      if (!edgeModel || !route.pointsList.length) return
      const points = cloneWorkflowPoints(route.pointsList)
      edgeModel.startPoint = cloneWorkflowPoint(route.startPoint) || points[0]
      edgeModel.endPoint = cloneWorkflowPoint(route.endPoint) || points[points.length - 1]
      if (typeof edgeModel.updatePath === 'function') {
        edgeModel.updatePath(points)
      } else {
        edgeModel.pointsList = points
        edgeModel.points = points.map((point) => `${point.x},${point.y}`).join(' ')
      }
    })
  } finally {
    restoringEditorEdgeRoutes = false
  }
  refreshEditorGraphStats()
}

function cloneWorkflowPoints(points: WorkflowRoutePoint[]) {
  return points.map((point) => ({ x: point.x, y: point.y }))
}

function cloneWorkflowPoint(point?: WorkflowRoutePoint) {
  return point ? { x: point.x, y: point.y } : undefined
}

function handleEditorTextDrop({ data }: { data?: Record<string, unknown> }) {
  window.requestAnimationFrame(() => {
    persistDraggedEdgeLabel(data)
  })
}

function persistDraggedEdgeLabel(data?: Record<string, unknown>) {
  if (!editorLogicFlow) return
  const graph = editorLogicFlow.getGraphData() as WorkflowGraph
  const syncedGraph = applyDraggedEdgeLabelLayouts(graph)
  const draggedId = data ? graphNodeId(data) : ''
  const selectedEdgeId = selectedElementType.value === 'edge' ? edgeForm.id : ''
  const preferredIds = new Set([draggedId, selectedEdgeId].filter(Boolean))
  const changedEdges = syncedGraph.edges.filter((edge, index) => {
    if (preferredIds.size && !preferredIds.has(graphNodeId(edge))) return false
    return hasEdgeLabelLayoutChanged(graph.edges[index], edge)
  })
  const edgesToPersist = changedEdges.length
    ? changedEdges
    : syncedGraph.edges.filter((edge, index) => hasEdgeLabelLayoutChanged(graph.edges[index], edge))

  edgesToPersist.forEach((edge) => {
    const edgeId = graphNodeId(edge)
    const properties = graphProperties(edge)
    editorLogicFlow?.setProperties(edgeId, properties)
    if (selectedElementType.value === 'edge' && edgeForm.id === edgeId) {
      edgeForm.labelPlacement = normalizeEdgeLabelPlacement(properties.labelPlacement)
      edgeForm.labelOffsetX = numberProperty(properties.labelOffsetX, 0)
      edgeForm.labelOffsetY = numberProperty(properties.labelOffsetY, 0)
    }
  })
}

function syncDraggedEdgeLabelLayouts(graph: WorkflowGraph): WorkflowGraph {
  const syncedGraph = applyDraggedEdgeLabelLayouts(graph)
  if (!editorLogicFlow) return syncedGraph
  syncedGraph.edges.forEach((edge, index) => {
    if (!hasEdgeLabelLayoutChanged(graph.edges[index], edge)) return
    editorLogicFlow?.setProperties(graphNodeId(edge), graphProperties(edge))
  })
  if (selectedElementType.value === 'edge') {
    const selectedEdge = syncedGraph.edges.find((edge) => graphNodeId(edge) === edgeForm.id)
    if (selectedEdge) {
      const properties = graphProperties(selectedEdge)
      edgeForm.labelPlacement = normalizeEdgeLabelPlacement(properties.labelPlacement)
      edgeForm.labelOffsetX = numberProperty(properties.labelOffsetX, 0)
      edgeForm.labelOffsetY = numberProperty(properties.labelOffsetY, 0)
    }
  }
  return syncedGraph
}

function applyDraggedEdgeLabelLayouts(graph: WorkflowGraph): WorkflowGraph {
  const nodeMap = new Map(graph.nodes.map((node) => [graphNodeId(node), node]))
  return {
    nodes: graph.nodes,
    edges: graph.edges.map((edge) => {
      const sourceType = inferNodeType(nodeMap.get(graphEdgeEndpoint(edge, 'source')) || {})
      const fallbackPlacement: EdgeLabelPlacement = sourceType === 'CONDITION' ? 'START' : 'AUTO'
      const layout = edgeLabelLayoutFromDraggedText(edge, edgePointsFromEdge(edge), fallbackPlacement)
      if (!layout) return edge
      return {
        ...edge,
        properties: {
          ...graphProperties(edge),
          ...layout
        }
      }
    })
  }
}

function hasEdgeLabelLayoutChanged(previous: Record<string, unknown> | undefined, next: Record<string, unknown>) {
  if (!previous) return false
  const previousProperties = graphProperties(previous)
  const nextProperties = graphProperties(next)
  return normalizeEdgeLabelPlacement(previousProperties.labelPlacement) !== normalizeEdgeLabelPlacement(nextProperties.labelPlacement)
    || numberProperty(previousProperties.labelOffsetX, 0) !== numberProperty(nextProperties.labelOffsetX, 0)
    || numberProperty(previousProperties.labelOffsetY, 0) !== numberProperty(nextProperties.labelOffsetY, 0)
}

function flushEditorTextDrop() {
  return new Promise<void>((resolve) => {
    window.requestAnimationFrame(() => resolve())
  })
}

function clearEditorSelection() {
  selectedElementType.value = 'none'
  Object.assign(nodeForm, {
    id: '',
    name: '',
    nodeType: 'APPROVAL',
    approveType: 'ANY_ONE',
    approverType: 'USER',
    assigneeIds: [],
    roleCode: '',
    description: ''
  })
  Object.assign(edgeForm, {
    id: '',
    label: '',
    conditionType: 'ALWAYS',
    conditionExpression: '',
    labelPlacement: 'AUTO',
    labelOffsetX: 0,
    labelOffsetY: 0
  })
}

function applySelectedNodeProperties() {
  if (!editorLogicFlow || !nodeForm.id) return
  editorLogicFlow.updateText(nodeForm.id, nodeForm.name || nodeForm.id)
  const baseProperties = {
    nodeType: nodeForm.nodeType,
    description: nodeForm.description,
    ...nodeVisualProperties(nodeForm.nodeType)
  }
  const properties = nodeForm.nodeType === 'CC' ? {
    ...baseProperties,
    ccUserIds: nodeForm.assigneeIds
  } : nodeRequiresApprover(nodeForm.nodeType) ? {
    ...baseProperties,
    approveType: nodeForm.approveType,
    approverType: nodeForm.approverType,
    ...(nodeForm.approverType === 'USER' ? { assigneeIds: nodeForm.assigneeIds } : {}),
    ...(nodeForm.approverType === 'ROLE' ? { roleCode: nodeForm.roleCode } : {})
  } : baseProperties
  editorLogicFlow.setProperties(nodeForm.id, properties)
  refreshEditorGraphStats()
  ElMessage.success('节点配置已应用')
}

function handleApproverTypeChange() {
  if (nodeForm.approverType !== 'ROLE') {
    nodeForm.roleCode = ''
  }
  if (nodeForm.approverType !== 'USER') {
    nodeForm.assigneeIds = []
  }
}

function applyApproverPreset(preset: ApproverPreset) {
  nodeForm.approverType = preset.type
  nodeForm.roleCode = preset.roleCode || ''
  if (preset.type !== 'USER') {
    nodeForm.assigneeIds = []
  }
}

function applySelectedEdgeProperties() {
  if (!editorLogicFlow || !edgeForm.id) return
  const graph = syncDraggedEdgeLabelLayouts(editorLogicFlow.getGraphData() as WorkflowGraph)
  const updatedGraph = {
    nodes: graph.nodes,
    edges: graph.edges.map((edge) => {
      if (graphNodeId(edge) !== edgeForm.id) return edge
      const currentProperties = edgePropertiesWithoutColor(graphProperties(edge))
      const properties = {
        ...currentProperties,
        conditionType: edgeForm.conditionType,
        conditionExpression: edgeForm.conditionExpression,
        labelPlacement: normalizeEdgeLabelPlacement(currentProperties.labelPlacement),
        labelOffsetX: numberProperty(currentProperties.labelOffsetX, 0),
        labelOffsetY: numberProperty(currentProperties.labelOffsetY, 0),
        ...edgeVisualProperties()
      }
      const nextEdge = {
        ...edge,
        text: edgeForm.label,
        properties
      }
      return {
        ...nextEdge,
        text: positionedWorkflowEdgeText(nextEdge, edgePointsFromEdge(nextEdge))
      }
    })
  }
  editorLogicFlow.render(applyWorkflowGraphVisuals(updatedGraph) as unknown as Parameters<LogicFlow['render']>[0])
  rememberEditorEdgeRoutes()
  editorLogicFlow.selectElementById(edgeForm.id)
  const updatedEdge = updatedGraph.edges.find((edge) => graphNodeId(edge) === edgeForm.id)
  const updatedProperties = graphProperties(updatedEdge || {})
  editorLogicFlow.setProperties(edgeForm.id, {
    conditionType: edgeForm.conditionType,
    conditionExpression: edgeForm.conditionExpression,
    labelPlacement: normalizeEdgeLabelPlacement(updatedProperties.labelPlacement),
    labelOffsetX: numberProperty(updatedProperties.labelOffsetX, 0),
    labelOffsetY: numberProperty(updatedProperties.labelOffsetY, 0),
    ...edgeVisualProperties()
  })
  refreshEditorGraphStats()
  ElMessage.success('名称和条件已应用')
}

function applyEdgePreset(preset: EdgeConditionPreset) {
  edgeForm.conditionType = 'EXPRESSION'
  edgeForm.conditionExpression = preset.expression
  edgeForm.labelPlacement = 'START'
  edgeForm.labelOffsetX = 0
  edgeForm.labelOffsetY = 0
  if (!edgeForm.label) {
    edgeForm.label = preset.label
  }
}

function handleEdgeConditionTypeChange() {
  if (edgeForm.conditionType === 'EXPRESSION') {
    edgeForm.labelPlacement = 'START'
    edgeForm.labelOffsetX = 0
    edgeForm.labelOffsetY = 0
    return
  }
  if (edgeForm.conditionType === 'ALWAYS') {
    edgeForm.labelPlacement = 'AUTO'
    edgeForm.labelOffsetX = 0
    edgeForm.labelOffsetY = 0
    edgeForm.conditionExpression = ''
  }
}

function refreshEditorGraphStats() {
  const graph = editorLogicFlow?.getGraphData() as WorkflowGraph | undefined
  const nodes = graph?.nodes || []
  const edges = graph?.edges || []
  editorGraphStats.nodes = nodes.length
  editorGraphStats.edges = edges.length
  editorGraphSnapshot.value = { nodes, edges }
}

function validateDefinitionGraph(graph: WorkflowGraph, status?: string) {
  if (status !== 'ENABLED') return true
  const result = validateWorkflowGraphForEnable(graph)
  if (!result.valid) {
    ElMessage.warning(result.message || '流程定义校验未通过')
    return false
  }
  return true
}

function selectedEditorNode() {
  const selected = editorLogicFlow?.getSelectElements(true) as { nodes?: Array<Record<string, unknown>> } | undefined
  return selected?.nodes?.[0]
}

function nextNodePosition(selectedNode?: Record<string, unknown>) {
  const x = typeof selectedNode?.x === 'number' ? selectedNode.x + 220 : 160 + editorGraphStats.nodes * 48
  const y = typeof selectedNode?.y === 'number' ? selectedNode.y : 220 + (editorGraphStats.nodes % 3) * 42
  return { x, y }
}

function uniqueNodeId(kind: WorkflowNodeKind, graph: WorkflowGraph) {
  const reserved = new Set(graph.nodes.map((node) => graphNodeId(node)))
  const base = kind === 'approval' ? 'approve' : kind
  if ((kind === 'start' || kind === 'end') && !reserved.has(base)) return base
  let index = graph.nodes.length + 1
  let id = `${base}_${index}`
  while (reserved.has(id)) {
    index += 1
    id = `${base}_${index}`
  }
  return id
}

function uniqueEdgeId(graph: WorkflowGraph) {
  const reserved = new Set(graph.edges.map((edge) => graphNodeId(edge)))
  let index = graph.edges.length + 1
  let id = `edge_${index}`
  while (reserved.has(id)) {
    index += 1
    id = `edge_${index}`
  }
  return id
}

function defaultNodeProperties(item: WorkflowPaletteItem) {
  return {
    nodeType: item.nodeType,
    description: item.description,
    ...(item.nodeType === 'APPROVAL' ? {
      approveType: 'ANY_ONE',
      approverType: defaultApproverType(item.nodeType)
    } : {}),
    ...nodeVisualProperties(item.nodeType)
  }
}

function applyWorkflowGraphVisuals(graph: WorkflowGraph): WorkflowGraph {
  const visualNodes = graph.nodes.map((node) => {
    const properties = graphProperties(node)
    const nodeType = inferNodeType(node)
    return {
      ...node,
      type: workflowLogicType(nodeType),
      properties: {
        ...properties,
        ...nodeVisualProperties(nodeType)
      }
    }
  })
  const nodeMap = new Map(visualNodes.map((node) => [graphNodeId(node), node]))
  return {
    nodes: visualNodes,
    edges: graph.edges.map((edge) => {
      const properties = edgePropertiesWithoutColor(graphProperties(edge))
      const conditionType = stringProperty(properties.conditionType) || 'ALWAYS'
      const expression = stringProperty(properties.conditionExpression)
      const labelPlacement = normalizeEdgeLabelPlacement(properties.labelPlacement)
      const sourceType = inferNodeType(nodeMap.get(graphEdgeEndpoint(edge, 'source')) || {})
      const fallbackPlacement: EdgeLabelPlacement = sourceType === 'CONDITION' ? 'START' : 'AUTO'
      const nextEdge = {
        ...edge,
        type: workflowEditorEdgeType,
        properties: {
          ...properties,
          labelPlacement,
          labelOffsetX: numberProperty(properties.labelOffsetX, 0),
          labelOffsetY: numberProperty(properties.labelOffsetY, 0),
          ...edgeVisualProperties()
        }
      }
      const edgePoints = edgePointsFromEdge(nextEdge)
      const draggedLayout = edgeLabelLayoutFromDraggedText(nextEdge, edgePoints, fallbackPlacement)
      const visualEdge = draggedLayout ? {
        ...nextEdge,
        properties: {
          ...nextEdge.properties,
          ...draggedLayout
        }
      } : nextEdge
      return {
        ...visualEdge,
        text: normalizedWorkflowEdgeText(visualEdge, edgePoints, fallbackPlacement)
      }
    })
  }
}

function optimizeWorkflowGraphLayout(graph: WorkflowGraph): WorkflowGraph {
  if (!graph.nodes.length) return graph
  const nodeIds = graph.nodes.map((node, index) => graphNodeId(node) || `node_${index}`)
  const nodeMap = new Map(graph.nodes.map((node, index) => [nodeIds[index], node]))
  const incomingCount = new Map(nodeIds.map((id) => [id, 0]))
  const incomingMap = new Map<string, string[]>()
  const outgoingMap = new Map<string, string[]>()

  graph.edges.forEach((edge) => {
    const source = graphEdgeEndpoint(edge, 'source')
    const target = graphEdgeEndpoint(edge, 'target')
    if (!source || !target || !nodeMap.has(source) || !nodeMap.has(target)) return
    outgoingMap.set(source, [...(outgoingMap.get(source) || []), target])
    incomingMap.set(target, [...(incomingMap.get(target) || []), source])
    incomingCount.set(target, (incomingCount.get(target) || 0) + 1)
  })

  const layerMap = new Map<string, number>()
  const startIds = nodeIds.filter((id) => inferNodeType(nodeMap.get(id) || {}) === 'START')
  const rootIds = startIds.length ? startIds : nodeIds.filter((id) => (incomingCount.get(id) || 0) === 0)
  const queue = [...rootIds]
  queue.forEach((id) => layerMap.set(id, 0))

  while (queue.length) {
    const source = queue.shift()
    if (!source) continue
    const sourceLayer = layerMap.get(source) || 0
    ;(outgoingMap.get(source) || []).forEach((target) => {
      const nextLayer = sourceLayer + 1
      if ((layerMap.get(target) ?? -1) >= nextLayer) return
      layerMap.set(target, nextLayer)
      queue.push(target)
    })
  }
  normalizeWorkflowLayoutLayers(nodeIds, nodeMap, graph.edges, incomingCount, layerMap)

  let fallbackLayer = Math.max(0, ...Array.from(layerMap.values()))
  nodeIds.forEach((id) => {
    if (layerMap.has(id)) return
    fallbackLayer += 1
    layerMap.set(id, fallbackLayer)
  })

  const layers = new Map<number, Array<{ node: Record<string, unknown>; id: string; index: number }>>()
  graph.nodes.forEach((node, index) => {
    const id = nodeIds[index]
    const layer = layerMap.get(id) || 0
    layers.set(layer, [...(layers.get(layer) || []), { node, id, index }])
  })

  const orderedLayers = Array.from(layers.entries()).sort(([left], [right]) => left - right)
  const laneMap = workflowLayoutLaneMap(nodeIds, nodeMap, orderedLayers, incomingMap, outgoingMap)
  const layoutPositionMap = new Map<string, { x: number; y: number }>()

  orderedLayers.forEach(([layer, items]) => {
    const sortedItems = [...items].sort((left, right) => {
      const laneDiff = (laneMap.get(left.id) || 0) - (laneMap.get(right.id) || 0)
      if (Math.abs(laneDiff) > 0.01) return laneDiff
      const yDiff = graphNodeNumber(left.node.y, workflowLayoutCenterY) - graphNodeNumber(right.node.y, workflowLayoutCenterY)
      if (Math.abs(yDiff) > 1) return yDiff
      return left.index - right.index
    })
    const laneCounts = sortedItems.reduce((map, item) => {
      const lane = laneMap.get(item.id) || 0
      map.set(lane, (map.get(lane) || 0) + 1)
      return map
    }, new Map<number, number>())
    const laneOrders = new Map<number, number>()
    sortedItems.forEach((item) => {
      const lane = laneMap.get(item.id) || 0
      const laneOrder = laneOrders.get(lane) || 0
      laneOrders.set(lane, laneOrder + 1)
      const stackedOffset = ((laneOrder - ((laneCounts.get(lane) || 1) - 1) / 2) * workflowLaneStackGap)
      layoutPositionMap.set(item.id, {
        x: workflowLayoutXStart + layer * workflowLayoutXGap,
        y: workflowLayoutCenterY + lane * workflowLayoutYGap + stackedOffset
      })
    })
  })

  return {
    nodes: graph.nodes.map((node, index) => {
      const id = nodeIds[index]
      const position = layoutPositionMap.get(id)
      const text = graphNodeText(node, id)
      return position ? { ...node, x: position.x, y: position.y, text } : { ...node, text }
    }),
    edges: graph.edges.map((edge) => optimizedWorkflowEdge(edge, layoutPositionMap, nodeMap))
  }
}

function workflowLayoutLaneMap(
  nodeIds: string[],
  nodeMap: Map<string, Record<string, unknown>>,
  orderedLayers: Array<[number, Array<{ node: Record<string, unknown>; id: string; index: number }>]>,
  incomingMap: Map<string, string[]>,
  outgoingMap: Map<string, string[]>
) {
  const laneMap = new Map<string, number>()
  const nodeOriginalY = (id: string) => graphNodeNumber(nodeMap.get(id)?.y, workflowLayoutCenterY)
  const fixedCenterTypes = new Set(['START', 'SUBMIT', 'CONDITION', 'CC', 'END'])

  nodeIds.forEach((id) => {
    if (fixedCenterTypes.has(inferNodeType(nodeMap.get(id) || {}))) {
      laneMap.set(id, 0)
    }
  })

  nodeIds.forEach((id) => {
    if (inferNodeType(nodeMap.get(id) || {}) !== 'CONDITION') return
    const targets = uniqueIds(outgoingMap.get(id) || [])
    if (targets.length <= 1) return
    const lanes = branchLaneIndexes(targets.length)
    targets
      .sort((left, right) => {
        const yDiff = nodeOriginalY(left) - nodeOriginalY(right)
        return Math.abs(yDiff) > 1 ? yDiff : left.localeCompare(right)
      })
      .forEach((targetId, index) => {
        if (fixedCenterTypes.has(inferNodeType(nodeMap.get(targetId) || {}))) return
        laneMap.set(targetId, lanes[index] || 0)
      })
  })

  orderedLayers.forEach(([, items]) => {
    items.forEach((item) => {
      if (!laneMap.has(item.id)) {
        const inheritedLane = medianLane((incomingMap.get(item.id) || []).map((sourceId) => laneMap.get(sourceId)))
        laneMap.set(item.id, inheritedLane)
      }
      const outgoingIds = uniqueIds(outgoingMap.get(item.id) || [])
      if (outgoingIds.length !== 1) return
      const targetId = outgoingIds[0]
      const targetType = inferNodeType(nodeMap.get(targetId) || {})
      if (laneMap.has(targetId) || fixedCenterTypes.has(targetType)) return
      laneMap.set(targetId, laneMap.get(item.id) || 0)
    })
  })

  return laneMap
}

function normalizeWorkflowLayoutLayers(
  nodeIds: string[],
  nodeMap: Map<string, Record<string, unknown>>,
  edges: Array<Record<string, unknown>>,
  incomingCount: Map<string, number>,
  layerMap: Map<string, number>
) {
  const maxPasses = Math.max(1, nodeIds.length)
  for (let pass = 0; pass < maxPasses; pass += 1) {
    let changed = false
    edges.forEach((edge) => {
      const source = graphEdgeEndpoint(edge, 'source')
      const target = graphEdgeEndpoint(edge, 'target')
      if (!source || !target || !nodeMap.has(source) || !nodeMap.has(target)) return
      const targetType = inferNodeType(nodeMap.get(target) || {})
      const needsMergeCorridor = ['CC', 'END'].includes(targetType) && (incomingCount.get(target) || 0) > 1
      const requiredLayer = (layerMap.get(source) || 0) + (needsMergeCorridor ? workflowMergeLayerSpan : 1)
      if ((layerMap.get(target) || 0) >= requiredLayer) return
      layerMap.set(target, requiredLayer)
      changed = true
    })
    if (!changed) break
  }
}

function uniqueIds(ids: string[]) {
  return Array.from(new Set(ids.filter(Boolean)))
}

function branchLaneIndexes(count: number) {
  if (count <= 1) return [0]
  if (count === 2) return [-1, 1]
  const middle = (count - 1) / 2
  return Array.from({ length: count }, (_, index) => index - middle)
}

function medianLane(values: Array<number | undefined>) {
  const lanes = values.filter((value): value is number => typeof value === 'number' && Number.isFinite(value)).sort((left, right) => left - right)
  if (!lanes.length) return 0
  return lanes[Math.floor(lanes.length / 2)]
}

function optimizedWorkflowEdge(
  edge: Record<string, unknown>,
  layoutPositionMap: Map<string, { x: number; y: number }>,
  nodeMap: Map<string, Record<string, unknown>>
) {
  const { pointsList, startPoint, endPoint, ...nextEdge } = edge
  void pointsList
  void startPoint
  void endPoint
  const edgePoints = optimizedWorkflowEdgePoints(edge, layoutPositionMap, nodeMap)
  const sourceType = inferNodeType(nodeMap.get(graphEdgeEndpoint(edge, 'source')) || {})
  const properties = {
    ...edgePropertiesWithoutColor(graphProperties(nextEdge)),
    labelPlacement: sourceType === 'CONDITION' ? 'START' : 'AUTO',
    labelOffsetX: 0,
    labelOffsetY: 0
  }
  const routedEdge = {
    ...nextEdge,
    properties
  }
  return {
    ...routedEdge,
    type: workflowEditorEdgeType,
    startPoint: edgePoints[0],
    endPoint: edgePoints[edgePoints.length - 1],
    pointsList: edgePoints,
    text: optimizedWorkflowEdgeText(routedEdge, edgePoints, nodeMap)
  }
}

function optimizedWorkflowEdgePoints(
  edge: Record<string, unknown>,
  layoutPositionMap: Map<string, { x: number; y: number }>,
  nodeMap: Map<string, Record<string, unknown>>
) {
  const sourceId = graphEdgeEndpoint(edge, 'source')
  const targetId = graphEdgeEndpoint(edge, 'target')
  const source = layoutPositionMap.get(sourceId)
  const target = layoutPositionMap.get(targetId)
  if (!source || !target) return []
  const sourceSize = workflowNodeHalfSize(inferNodeType(nodeMap.get(sourceId) || {}))
  const targetSize = workflowNodeHalfSize(inferNodeType(nodeMap.get(targetId) || {}))
  const direction = target.x >= source.x ? 1 : -1
  const start = { x: Math.round(source.x + sourceSize.width * direction), y: Math.round(source.y) }
  const end = { x: Math.round(target.x - targetSize.width * direction), y: Math.round(target.y) }
  if (Math.abs(start.y - end.y) < 4) return [start, end]
  const sourceType = inferNodeType(nodeMap.get(sourceId) || {})
  const targetType = inferNodeType(nodeMap.get(targetId) || {})
  const isConditionBranch = sourceType === 'CONDITION'
  const isMergeIntoUtility = ['CC', 'END'].includes(targetType)
  if (isConditionBranch) {
    const horizontalDistance = Math.abs(end.x - start.x)
    const bendDistance = Math.max(42, Math.min(workflowConditionSplitOffset, Math.round(horizontalDistance * 0.34)))
    const bendX = Math.round(start.x + bendDistance * direction)
    return [start, { x: bendX, y: start.y }, { x: bendX, y: end.y }, end]
  }
  if (isMergeIntoUtility) {
    const bendX = Math.round(end.x - workflowMergeJoinOffset * direction)
    return [start, { x: bendX, y: start.y }, { x: bendX, y: end.y }, end]
  }
  const midX = Math.round(start.x + (end.x - start.x) / 2)
  return [start, { x: midX, y: start.y }, { x: midX, y: end.y }, end]
}

function workflowNodeHalfSize(nodeType: string) {
  if (nodeType === 'START' || nodeType === 'END') return { width: 34, height: 34 }
  if (nodeType === 'CONDITION') return { width: 52, height: 52 }
  return { width: 84, height: 32 }
}

function optimizedWorkflowEdgeText(edge: Record<string, unknown>, points: Array<{ x: number; y: number }>, nodeMap: Map<string, Record<string, unknown>>) {
  const sourceType = inferNodeType(nodeMap.get(graphEdgeEndpoint(edge, 'source')) || {})
  const targetType = inferNodeType(nodeMap.get(graphEdgeEndpoint(edge, 'target')) || {})
  const fallbackPlacement: EdgeLabelPlacement = sourceType === 'CONDITION' ? 'START' : 'AUTO'
  const placement = graphProperties(edge).labelPlacement ? normalizeEdgeLabelPlacement(graphProperties(edge).labelPlacement) : fallbackPlacement
  if (placement !== 'CUSTOM' && sourceType === 'CONDITION' && (placement === 'START' || placement === 'AUTO')) {
    return conditionBranchWorkflowEdgeText(edge, points)
  }
  if (placement !== 'CUSTOM' && ['CC', 'END'].includes(targetType) && points.length >= 4) {
    return mergeWorkflowEdgeText(edge, points)
  }
  return normalizedWorkflowEdgeText(edge, points, fallbackPlacement)
}

function conditionBranchWorkflowEdgeText(edge: Record<string, unknown>, points: Array<{ x: number; y: number }>) {
  const label = graphNodeText(edge, '')
  if (!label || points.length < 4) return normalizedWorkflowEdgeText(edge, points, 'START')
  const anchor = conditionBranchLabelAnchor(points)
  if (!anchor) return normalizedWorkflowEdgeText(edge, points, 'START')
  const offset = edgeLabelBaseOffset(points, 'START')
  return draggableWorkflowEdgeText(
    label,
    Math.round(anchor.x + offset.x),
    Math.round(anchor.y + offset.y)
  )
}

function mergeWorkflowEdgeText(edge: Record<string, unknown>, points: Array<{ x: number; y: number }>) {
  const label = graphNodeText(edge, '')
  if (!label || points.length < 4) return normalizedWorkflowEdgeText(edge, points)
  const start = points[0]
  const joinPoint = points[1]
  const direction = joinPoint.x >= start.x ? 1 : -1
  const labelDistance = Math.min(96, Math.max(56, Math.abs(joinPoint.x - start.x) * 0.28))
  const verticalDirection = Math.sign(points[2].y - start.y)
  return draggableWorkflowEdgeText(
    label,
    Math.round(start.x + labelDistance * direction),
    Math.round(start.y + (verticalDirection > 0 ? -18 : 22))
  )
}

function nodeVisualProperties(nodeType: string) {
  const baseText = { fontSize: 13, fontWeight: 700 }
  const visualMap: Record<string, Record<string, unknown>> = {
    START: {
      r: 32,
      style: { fill: '#f8fbff', stroke: '#2563eb', strokeWidth: 2 },
      textStyle: { ...baseText, fill: '#1e40af' }
    },
    SUBMIT: {
      width: 168,
      height: 64,
      radius: 8,
      style: { fill: '#ffffff', stroke: '#94a3b8', strokeWidth: 1.8 },
      textStyle: { ...baseText, fill: '#334155' }
    },
    APPROVAL: {
      width: 168,
      height: 64,
      radius: 8,
      style: { fill: '#f8fbff', stroke: '#2563eb', strokeWidth: 2 },
      textStyle: { ...baseText, fill: '#1e3a8a' }
    },
    CC: {
      width: 168,
      height: 64,
      radius: 8,
      style: { fill: '#fbfefc', stroke: '#16a34a', strokeWidth: 2, strokeDasharray: '5 4' },
      textStyle: { ...baseText, fill: '#166534' }
    },
    CONDITION: {
      rx: 52,
      ry: 52,
      style: { fill: '#fffbeb', stroke: '#d97706', strokeWidth: 2 },
      textStyle: { ...baseText, fill: '#9a3412' }
    },
    END: {
      r: 32,
      style: { fill: '#f8fafc', stroke: '#475569', strokeWidth: 2 },
      textStyle: { ...baseText, fill: '#334155' }
    }
  }
  return visualMap[nodeType] || {}
}

function registerWorkflowDesignerNodes(logicFlow: LogicFlow) {
  logicFlow.register({ type: 'workflow-rect', view: WorkflowRectNode, model: RectNodeModel })
  logicFlow.register({ type: 'workflow-circle', view: WorkflowCircleNode, model: CircleNodeModel })
  logicFlow.register({ type: 'workflow-diamond', view: WorkflowDiamondNode, model: DiamondNodeModel })
}

function workflowLogicType(nodeType: string): WorkflowLogicNodeType {
  if (nodeType === 'START' || nodeType === 'END') return 'workflow-circle'
  if (nodeType === 'CONDITION') return 'workflow-diamond'
  return 'workflow-rect'
}

function renderWorkflowNodeIcon(model: WorkflowIconModel) {
  const nodeType = stringProperty(model.properties?.nodeType) || inferNodeType(model.getData() as Record<string, unknown>)
  const meta = nodeIconMeta(nodeType)
  const position = nodeIconPosition(model, nodeType)
  return h('g', {
    className: 'workflow-node-icon',
    pointerEvents: 'none',
    'data-node-type': nodeType
  }, [
    h('circle', {
      cx: position.x,
      cy: position.y,
      r: 8.5,
      fill: meta.background,
      stroke: meta.border,
      strokeWidth: 1.2
    }),
    renderWorkflowIconGlyph(position.x, position.y, meta.icon, meta.color)
  ])
}

function nodeIconPosition(model: WorkflowIconModel, nodeType: string) {
  const width = typeof model.width === 'number' ? model.width : 84
  const height = typeof model.height === 'number' ? model.height : 84
  const inset = nodeType === 'CONDITION' ? 18 : 15
  return {
    x: model.x - width / 2 + inset,
    y: model.y - height / 2 + inset
  }
}

function nodeIconMeta(nodeType: string): { icon: WorkflowIconName; background: string; border: string; color: string } {
  const map: Record<string, { icon: WorkflowIconName; background: string; border: string; color: string }> = {
    START: { icon: 'play', background: '#ffffff', border: '#93c5fd', color: '#2563eb' },
    SUBMIT: { icon: 'file', background: '#ffffff', border: '#cbd5e1', color: '#475569' },
    APPROVAL: { icon: 'user', background: '#ffffff', border: '#93c5fd', color: '#2563eb' },
    CC: { icon: 'notify', background: '#ffffff', border: '#86efac', color: '#16a34a' },
    CONDITION: { icon: 'branch', background: '#ffffff', border: '#fdba74', color: '#d97706' },
    END: { icon: 'stop', background: '#ffffff', border: '#cbd5e1', color: '#475569' }
  }
  return map[nodeType] || map.APPROVAL
}

function renderWorkflowIconGlyph(cx: number, cy: number, icon: WorkflowIconName, color: string) {
  const strokeAttrs = {
    fill: 'none',
    stroke: color,
    strokeWidth: 1.5,
    strokeLinecap: 'round',
    strokeLinejoin: 'round'
  }
  if (icon === 'play') {
    return h('path', { d: `M ${cx - 2} ${cy - 4} L ${cx + 4} ${cy} L ${cx - 2} ${cy + 4} Z`, fill: color })
  }
  if (icon === 'file') {
    return h('g', {}, [
      h('path', { d: `M ${cx - 3.5} ${cy - 5} H ${cx + 1} L ${cx + 4} ${cy - 2} V ${cy + 5} H ${cx - 3.5} Z`, ...strokeAttrs }),
      h('path', { d: `M ${cx + 1} ${cy - 5} V ${cy - 2} H ${cx + 4}`, ...strokeAttrs }),
      h('path', { d: `M ${cx - 1.5} ${cy + 1} H ${cx + 2}`, ...strokeAttrs })
    ])
  }
  if (icon === 'user') {
    return h('g', {}, [
      h('circle', { cx, cy: cy - 2.4, r: 2.2, ...strokeAttrs }),
      h('path', { d: `M ${cx - 4.4} ${cy + 4.6} C ${cx - 3.5} ${cy + 1.4}, ${cx + 3.5} ${cy + 1.4}, ${cx + 4.4} ${cy + 4.6}`, ...strokeAttrs })
    ])
  }
  if (icon === 'notify') {
    return h('g', {}, [
      h('path', { d: `M ${cx - 4} ${cy + 1} C ${cx - 4} ${cy - 3}, ${cx - 2} ${cy - 5}, ${cx + 1} ${cy - 5} C ${cx + 4} ${cy - 5}, ${cx + 5} ${cy - 3}, ${cx + 5} ${cy} V ${cy + 3} H ${cx - 4} Z`, ...strokeAttrs }),
      h('path', { d: `M ${cx - 1} ${cy + 4.5} C ${cx + 1} ${cy + 5.5}, ${cx + 2.5} ${cy + 4.5}, ${cx + 2.5} ${cy + 4.5}`, ...strokeAttrs })
    ])
  }
  if (icon === 'branch') {
    return h('g', {}, [
      h('circle', { cx: cx - 4, cy: cy, r: 1.2, fill: color }),
      h('circle', { cx: cx + 4, cy: cy - 4, r: 1.2, fill: color }),
      h('circle', { cx: cx + 4, cy: cy + 4, r: 1.2, fill: color }),
      h('path', { d: `M ${cx - 2.8} ${cy} H ${cx + 1} L ${cx + 3} ${cy - 3}`, ...strokeAttrs }),
      h('path', { d: `M ${cx + 1} ${cy} L ${cx + 3} ${cy + 3}`, ...strokeAttrs })
    ])
  }
  return h('rect', { x: cx - 3.5, y: cy - 3.5, width: 7, height: 7, rx: 1.4, fill: color })
}

function edgeVisualProperties() {
  return {
    style: {
      stroke: edgeDefaultColor,
      strokeWidth: 1.8,
      strokeLinecap: 'round',
      strokeLinejoin: 'round'
    },
    textStyle: {
      fill: edgeDefaultColor,
      fontSize: 12,
      fontWeight: 700,
      background: {
        fill: '#ffffff',
        stroke: '#dbe7f5',
        strokeWidth: 1,
        radius: 4,
        wrapPadding: '3px 6px'
      }
    }
  }
}

function normalizeEdgeLabelPlacement(value: unknown): EdgeLabelPlacement {
  if (value === 'ABOVE' || value === 'BELOW' || value === 'START' || value === 'END' || value === 'CUSTOM') {
    return value
  }
  return 'AUTO'
}

function normalizedWorkflowEdgeText(
  edge: Record<string, unknown>,
  points: Array<{ x: number; y: number }>,
  fallbackPlacement: EdgeLabelPlacement = 'AUTO'
) {
  const label = graphNodeText(edge, '')
  if (!label) return ''
  return positionedWorkflowEdgeText(edge, points, fallbackPlacement)
}

function positionedWorkflowEdgeText(
  edge: Record<string, unknown>,
  points: Array<{ x: number; y: number }>,
  fallbackPlacement: EdgeLabelPlacement = 'AUTO'
) {
  const label = graphNodeText(edge, '')
  if (!label) return ''
  if (points.length < 2) return draggableWorkflowEdgeText(label)
  const properties = graphProperties(edge)
  const placement = properties.labelPlacement ? normalizeEdgeLabelPlacement(properties.labelPlacement) : fallbackPlacement
  const basePlacement = placement === 'CUSTOM' ? fallbackPlacement : placement
  const base = edgeLabelBasePoint(points, basePlacement)
  const { x: baseOffsetX, y: baseOffsetY } = edgeLabelBaseOffset(points, basePlacement)
  return draggableWorkflowEdgeText(
    label,
    Math.round(base.x + baseOffsetX + numberProperty(properties.labelOffsetX, 0)),
    Math.round(base.y + baseOffsetY + numberProperty(properties.labelOffsetY, 0))
  )
}

function draggableWorkflowEdgeText(label: string, x?: number, y?: number) {
  const text = {
    value: label,
    draggable: true,
    editable: false
  }
  return typeof x === 'number' && typeof y === 'number' ? { ...text, x, y } : text
}

function edgeLabelLayoutFromDraggedText(
  edge: Record<string, unknown>,
  points: Array<{ x: number; y: number }>,
  fallbackPlacement: EdgeLabelPlacement = 'AUTO'
) {
  const textPoint = pointProperty(edge.text)
  if (!textPoint || points.length < 2 || !graphNodeText(edge, '')) return undefined
  const properties = graphProperties(edge)
  const placement = properties.labelPlacement ? normalizeEdgeLabelPlacement(properties.labelPlacement) : fallbackPlacement
  const basePlacement = placement === 'CUSTOM' ? fallbackPlacement : placement
  const baselineEdge = {
    ...edge,
    properties: {
      ...properties,
      labelPlacement: basePlacement,
      labelOffsetX: 0,
      labelOffsetY: 0
    }
  }
  const baselineText = positionedWorkflowEdgeText(baselineEdge, points, fallbackPlacement)
  const currentText = positionedWorkflowEdgeText(edge, points, fallbackPlacement)
  if (!isPositionedWorkflowEdgeText(baselineText) || !isPositionedWorkflowEdgeText(currentText)) return undefined
  if (Math.abs(textPoint.x - currentText.x) <= 1 && Math.abs(textPoint.y - currentText.y) <= 1) return undefined
  return {
    labelPlacement: 'CUSTOM' as const,
    labelOffsetX: Math.round(textPoint.x - baselineText.x),
    labelOffsetY: Math.round(textPoint.y - baselineText.y)
  }
}

function isPositionedWorkflowEdgeText(value: unknown): value is { value: string; x: number; y: number } {
  if (!value || typeof value !== 'object') return false
  const text = value as { value?: unknown; x?: unknown; y?: unknown }
  return typeof text.value === 'string' && typeof text.x === 'number' && typeof text.y === 'number'
}

function edgeLabelBasePoint(points: Array<{ x: number; y: number }>, placement: EdgeLabelPlacement) {
  if (placement === 'START') {
    const branchAnchor = conditionBranchLabelAnchor(points)
    if (branchAnchor) return { x: branchAnchor.x, y: branchAnchor.y }
    return pointOnPolyline(points, 0.22)
  }
  if (placement === 'END') return pointOnPolyline(points, 0.72)
  if (placement === 'AUTO') {
    const firstSegmentAnchor = firstSegmentLabelAnchor(points)
    if (firstSegmentAnchor) return firstSegmentAnchor
  }
  return pointOnPolyline(points, 0.5)
}

function edgeLabelBaseOffset(points: Array<{ x: number; y: number }>, placement: EdgeLabelPlacement) {
  if (placement === 'ABOVE') return { x: 0, y: -18 }
  if (placement === 'BELOW') return { x: 0, y: 20 }
  if (placement === 'START') {
    const branchAnchor = conditionBranchLabelAnchor(points)
    if (branchAnchor) return { x: 0, y: branchAnchor.isUpperBranch ? 24 : -22 }
    return { x: 8, y: -18 }
  }
  if (placement === 'END') return { x: -8, y: -18 }
  if (placement === 'AUTO' && firstSegmentLabelAnchor(points)) return { x: 0, y: -20 }
  const first = points[0]
  const last = points[points.length - 1]
  const verticalTravel = Math.abs(last.y - first.y)
  return { x: 0, y: verticalTravel > 24 ? -16 : -18 }
}

function conditionBranchLabelAnchor(points: Array<{ x: number; y: number }>) {
  if (!orthogonalPolyline(points)) return undefined
  const start = points[0]
  const branchPoint = points[2]
  const end = points[points.length - 1]
  const horizontalProgress = 0.22
  return {
    x: Math.round(branchPoint.x + (end.x - branchPoint.x) * horizontalProgress),
    y: Math.round(branchPoint.y),
    isUpperBranch: end.y < start.y
  }
}

function firstSegmentLabelAnchor(points: Array<{ x: number; y: number }>) {
  if (!orthogonalPolyline(points)) return undefined
  const start = points[0]
  const bend = points[1]
  return {
    x: Math.round(start.x + (bend.x - start.x) * 0.58),
    y: Math.round(start.y)
  }
}

function orthogonalPolyline(points: Array<{ x: number; y: number }>) {
  if (points.length < 4) return false
  const start = points[0]
  const firstBend = points[1]
  const secondBend = points[2]
  const end = points[points.length - 1]
  return Math.abs(start.y - firstBend.y) < 4
    && Math.abs(firstBend.x - secondBend.x) < 4
    && Math.abs(secondBend.y - end.y) < 4
}

function pointOnPolyline(points: Array<{ x: number; y: number }>, ratio: number) {
  const segments = points.slice(1).map((point, index) => {
    const previous = points[index]
    return {
      start: previous,
      end: point,
      length: Math.hypot(point.x - previous.x, point.y - previous.y)
    }
  })
  const totalLength = segments.reduce((sum, segment) => sum + segment.length, 0)
  if (totalLength <= 0) return points[0]
  let distance = totalLength * ratio
  for (const segment of segments) {
    if (distance <= segment.length) {
      const segmentRatio = segment.length === 0 ? 0 : distance / segment.length
      return {
        x: segment.start.x + (segment.end.x - segment.start.x) * segmentRatio,
        y: segment.start.y + (segment.end.y - segment.start.y) * segmentRatio
      }
    }
    distance -= segment.length
  }
  return points[points.length - 1]
}

function edgePointsFromEdge(edge: Record<string, unknown>) {
  const points = edge.pointsList
  if (Array.isArray(points)) {
    return points
      .map((point) => {
        if (!point || typeof point !== 'object') return undefined
        const item = point as { x?: unknown; y?: unknown }
        if (typeof item.x !== 'number' || typeof item.y !== 'number') return undefined
        return { x: item.x, y: item.y }
      })
      .filter((point): point is { x: number; y: number } => Boolean(point))
  }
  const start = pointProperty(edge.startPoint)
  const end = pointProperty(edge.endPoint)
  return start && end ? [start, end] : []
}

function pointProperty(value: unknown) {
  if (!value || typeof value !== 'object') return undefined
  const point = value as { x?: unknown; y?: unknown }
  return typeof point.x === 'number' && typeof point.y === 'number' ? { x: point.x, y: point.y } : undefined
}

function defaultApproverType(nodeType: string) {
  if (nodeType === 'SUBMIT') return 'INITIATOR'
  if (nodeType === 'APPROVAL') return 'DEPT_LEADER'
  return ''
}

function nodeRequiresApprover(nodeType: string) {
  return nodeType === 'APPROVAL'
}

function approvalNodeConfigured(node: Record<string, unknown>) {
  const properties = graphProperties(node)
  const approverType = stringProperty(properties.approverType)
  if (!approverType || approverType === 'USER') {
    return toEntityIds(properties.assigneeIds).length > 0
  }
  if (approverType === 'ROLE') {
    return Boolean(stringProperty(properties.roleCode))
  }
  return ['INITIATOR', 'INITIATOR_SELECTED', 'MANAGER', 'DEPT_LEADER', 'UPPER_DEPT_LEADER'].includes(approverType)
}

function nodeTypeText(nodeType?: string) {
  const map: Record<string, string> = {
    START: '开始节点',
    SUBMIT: '提交节点',
    APPROVAL: '审批节点',
    CC: '抄送节点',
    CONDITION: '条件节点',
    END: '结束节点'
  }
  return nodeType ? map[nodeType] || nodeType : '-'
}

function nodeTypeDescription(nodeType?: string) {
  const map: Record<string, string> = {
    START: '系统自动入口，只允许一个，不需要配置处理人。',
    SUBMIT: '发起人提交业务单据，一般由业务页面触发。',
    APPROVAL: '真正需要配置处理人的审批环节。',
    CC: '只通知相关人员，不产生审批决策。',
    CONDITION: '按天数、金额等业务字段选择下一条连线。',
    END: '流程结束点，只允许一个，不需要配置处理人。'
  }
  return nodeType ? map[nodeType] || '节点类型由画布元素决定，如需更换请删除后重新添加。' : '节点类型由画布元素决定。'
}

function approverRuleTitle(approverType?: string) {
  const map: Record<string, string> = {
    USER: '指定用户',
    ROLE: '职能角色',
    MANAGER: '发起人直属上级',
    DEPT_LEADER: '发起人所在部门负责人',
    UPPER_DEPT_LEADER: '发起人所在部门的上级部门负责人',
    INITIATOR_SELECTED: '发起人提交时自选处理人',
    INITIATOR: '流程发起人'
  }
  return approverType ? map[approverType] || approverType : '未选择'
}

function approverRuleDescription(approverType?: string) {
  const map: Record<string, string> = {
    USER: '运行时按下方选择的用户生成待办；多人时遵循审批方式。',
    ROLE: '运行时按系统角色解析候选处理人，适合财务、审计、运维等职能岗位。',
    MANAGER: '运行时读取用户管理中的直属上级，适合中小企业一线主管审批。',
    DEPT_LEADER: '运行时读取组织架构中的部门负责人；如果负责人就是发起人，会自动上跳到上级部门负责人。',
    UPPER_DEPT_LEADER: '运行时读取组织架构中的上级部门负责人，用于跨部门或更高层级复核。',
    INITIATOR_SELECTED: '发起人在提交业务单据时选择下一步处理人。',
    INITIATOR: '运行时把待办分配给流程发起人。'
  }
  return approverType ? map[approverType] || '按后端约定解析处理人规则。' : '选择处理人规则后显示运行时解析方式。'
}

function inferNodeType(node: Record<string, unknown>): WorkflowNodeType {
  const properties = graphProperties(node)
  const configuredType = stringProperty(properties.nodeType)
  if (configuredType && ['START', 'SUBMIT', 'APPROVAL', 'CC', 'CONDITION', 'END'].includes(configuredType)) {
    return configuredType as WorkflowNodeType
  }
  const id = graphNodeId(node).toLowerCase()
  const type = String(node.type || '').toLowerCase()
  const text = graphNodeText(node, '')
  if (id === 'start' || text.includes('开始')) return 'START'
  if (id === 'end' || text.includes('结束')) return 'END'
  if (id.includes('submit') || text.startsWith('提交')) return 'SUBMIT'
  if (id.includes('cc') || text.includes('抄送')) return 'CC'
  if (id.includes('condition') || type.includes('diamond') || text.includes('条件') || text.includes('判断')) return 'CONDITION'
  return 'APPROVAL'
}

function graphProperties(data: Record<string, unknown>) {
  const properties = data.properties
  return properties && typeof properties === 'object' ? (properties as Record<string, unknown>) : {}
}

function recordProperty(value: unknown) {
  return value && typeof value === 'object' ? (value as Record<string, unknown>) : {}
}

function edgePropertiesWithoutColor(properties: Record<string, unknown>) {
  const { lineColor, ...rest } = properties
  void lineColor
  return rest
}

function stringProperty(value: unknown) {
  return typeof value === 'string' ? value : ''
}

function toEntityIds(value: unknown): EntityId[] {
  if (!value) return []
  if (Array.isArray(value)) {
    return value
      .map(normalizeEntityId)
      .filter((item): item is EntityId => Boolean(item))
  }
  const entityId = normalizeEntityId(value)
  if (entityId) return [entityId]
  return []
}

function normalizeAssigneeOption(option: WorkflowAssigneeOption): WorkflowAssigneeOption | undefined {
  const value = normalizeEntityId(option.value)
  if (!value || value === 'CurrentUser') return undefined
  return { ...option, value }
}

function normalizeEntityId(value: unknown): EntityId | undefined {
  if (typeof value === 'string') {
    const trimmed = value.trim()
    return trimmed ? trimmed : undefined
  }
  if (typeof value === 'number' && Number.isFinite(value)) {
    return String(value)
  }
  return undefined
}

function graphNodeId(node: Record<string, unknown>) {
  const id = node.id
  return typeof id === 'string' || typeof id === 'number' ? String(id) : ''
}

function graphEdgeEndpoint(edge: Record<string, unknown>, direction: 'source' | 'target') {
  const id = direction === 'source'
    ? edge.sourceNodeId
    : edge.targetNodeId
  return typeof id === 'string' || typeof id === 'number' ? String(id) : ''
}

function graphNodeText(node: Record<string, unknown>, fallback = '-') {
  const text = node.text
  if (typeof text === 'string') return text
  if (text && typeof text === 'object') {
    const value = (text as { value?: unknown; text?: unknown }).value || (text as { value?: unknown; text?: unknown }).text
    if (typeof value === 'string' && value.trim()) return value
  }
  return fallback
}

function graphNodeNumber(value: unknown, fallback: number) {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback
}

function numberProperty(value: unknown, fallback: number) {
  if (typeof value === 'number' && Number.isFinite(value)) return value
  if (typeof value === 'string' && value.trim() && Number.isFinite(Number(value))) return Number(value)
  return fallback
}

function shortElementId(id: string) {
  if (!id) return '-'
  return id.length > 18 ? `${id.slice(0, 8)}...${id.slice(-6)}` : id
}

function definitionStatusText(status?: string) {
  if (status === 'ENABLED') return '启用'
  if (status === 'DISABLED') return '停用'
  if (status === 'DRAFT') return '草稿'
  return status || '-'
}

function statusClass(status?: string) {
  if (status === 'ENABLED') return 'is-enabled'
  if (status === 'DRAFT') return 'is-draft'
  return 'is-disabled'
}


</script>

<style scoped>
.workflow-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 100%;
  gap: 14px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: stretch;
  border: 0;
  border-radius: 0;
  padding: 4px 2px 0;
  background: transparent;
}

.page-header-main {
  min-width: 300px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.header-kicker {
  width: fit-content;
  min-height: 24px;
  display: inline-flex;
  align-items: center;
  border: 1px solid #bfdbfe;
  border-radius: 999px;
  padding: 0 9px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 700;
}

.page-header h1 {
  margin: 8px 0 0;
  color: var(--ea-text);
  font-size: 20px;
  line-height: 1.25;
}

.page-header p {
  margin: 4px 0 0;
  color: var(--ea-muted);
  font-size: 12px;
  line-height: 1.45;
}

.page-header-side {
  min-width: 0;
  display: flex;
  align-items: flex-start;
  justify-content: flex-end;
  gap: 10px;
}

.page-actions,
.toolbar-actions,
.todo-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.workflow-board {
  flex: 1 1 auto;
  min-height: 0;
  border: 1px solid var(--ea-border);
  border-radius: 8px;
  background: #fff;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  overflow: hidden;
}

.workflow-board-toolbar {
  min-height: 50px;
  padding: 8px 10px;
  border-bottom: 1px solid var(--ea-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.toolbar-left {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
}

.workflow-list-search {
  width: min(320px, 36vw);
  flex: 0 1 320px;
}

.workflow-status-filter {
  width: 132px;
  flex: 0 0 auto;
}

.workflow-board-body {
  min-height: 0;
  display: grid;
  grid-template-columns: 1fr;
  overflow: visible;
}

.definition-table-panel {
  min-width: 0;
  min-height: 0;
  overflow: visible;
  display: flex;
  flex-direction: column;
}

.workflow-definition-table {
  flex: 1 1 auto;
  min-height: 0;
  --el-table-row-hover-bg-color: #f8fbff;
}

.workflow-definition-table :deep(.el-table__cell) {
  padding: 7px 0;
}

.workflow-name-cell strong,
.workflow-name-cell small,
.workflow-name-cell span {
  display: block;
}

.workflow-name-main {
  min-width: 0;
  display: flex !important;
  align-items: center;
  gap: 8px;
}

.workflow-dot {
  width: 8px;
  height: 8px;
  flex: 0 0 auto;
  border-radius: 999px;
  background: #94a3b8;
}

.workflow-dot.is-enabled {
  background: #16a34a;
}

.workflow-dot.is-draft {
  background: #64748b;
}

.workflow-dot.is-disabled {
  background: #dc2626;
}

.workflow-name-cell strong {
  min-width: 0;
  color: var(--ea-text);
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workflow-name-cell small {
  margin-top: 3px;
  color: #64748b;
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", monospace;
  font-size: 12px;
}

.workflow-remark {
  color: #475569;
}

.status-pill {
  min-width: 44px;
  min-height: 26px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--ea-border);
  background: #f8fafc;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

.status-pill.is-enabled {
  border-color: #86efac;
  background: #f0fdf4;
  color: #15803d;
}

.status-pill.is-draft {
  border-color: #cbd5e1;
  background: #f8fafc;
  color: #475569;
}

.status-pill.is-disabled {
  border-color: #fecaca;
  background: #fff1f2;
  color: #be123c;
}

.status-control {
  display: flex;
  align-items: center;
  gap: 10px;
}

.workflow-row-actions {
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  justify-content: center;
  gap: 4px;
  white-space: nowrap;
  overflow: hidden;
}

.workflow-row-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.workflow-row-actions :deep(.el-button) {
  padding-right: 5px;
  padding-left: 5px;
  font-weight: 600;
}

.workflow-row-actions :deep(.workflow-edit-button.el-button) {
  color: var(--ea-primary);
}

.workflow-row-actions :deep(.workflow-more-button.el-button) {
  --el-button-text-color: #64748b;
  --el-button-hover-text-color: var(--ea-primary);
  --el-button-active-text-color: #64748b;
  min-width: 48px;
  color: #64748b;
}

.workflow-row-actions :deep(.workflow-more-button.el-button .el-icon),
.workflow-row-actions :deep(.workflow-more-button.el-button span) {
  color: inherit;
}

.workflow-row-actions :deep(.workflow-more-button.el-button:hover) {
  color: var(--ea-primary);
}

.workflow-row-actions :deep(.workflow-more-button.el-button:focus:not(:hover)),
.workflow-row-actions :deep(.workflow-more-button.el-button:active:not(:hover)) {
  color: #64748b;
}

:global(.workflow-definition-row-action-menu .el-dropdown-menu__item) {
  min-width: 128px;
  font-weight: 600;
}

:global(.workflow-definition-row-action-menu .el-dropdown-menu__item.is-danger:not(.is-disabled)) {
  color: #dc2626;
}

.workflow-table-empty {
  min-height: 220px;
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 10px;
  padding: 24px;
  text-align: center;
}

.workflow-table-empty strong,
.workflow-table-empty span {
  display: block;
}

.workflow-table-empty strong {
  color: var(--ea-text);
  font-size: 16px;
}

.workflow-table-empty span {
  max-width: 420px;
  color: #64748b;
  line-height: 1.7;
}

.canvas-stat {
  display: inline-flex !important;
  align-items: center;
  min-height: 28px;
  border: 1px solid var(--ea-border);
  border-radius: 999px;
  padding: 0 9px;
  background: #f8fafc;
  color: #475569 !important;
  font-size: 12px !important;
  font-weight: 600;
}

.workflow-pagination {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: flex-end;
}

:global(.workflow-designer-dialog) {
  max-width: calc(100vw - 32px);
  height: min(820px, calc(100dvh - 32px));
  max-height: calc(100dvh - 32px);
  margin-top: 16px !important;
  border-radius: 10px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

:global(.workflow-designer-dialog .el-dialog__header) {
  flex: 0 0 auto;
  padding: 14px 16px;
  border-bottom: 1px solid var(--ea-border);
}

:global(.workflow-designer-dialog .el-dialog__body) {
  flex: 1 1 auto;
  min-height: 0;
  padding: 0;
  overflow: hidden;
}

:global(.workflow-designer-dialog .el-dialog__footer) {
  flex: 0 0 auto;
  padding: 10px 16px;
  border-top: 1px solid var(--ea-border);
}

.designer-dialog-title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-right: 32px;
}

.designer-dialog-title strong,
.designer-dialog-title span {
  display: block;
}

.designer-dialog-title strong {
  color: var(--ea-text);
  font-size: 18px;
}

.designer-dialog-title span {
  margin-top: 4px;
  color: var(--ea-muted);
  font-size: 13px;
  line-height: 1.5;
}

.workflow-designer {
  display: grid;
  grid-template-columns: minmax(240px, 270px) minmax(0, 1fr) minmax(320px, 360px);
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.workflow-designer.is-properties-empty {
  grid-template-columns: minmax(240px, 270px) minmax(0, 1fr) minmax(300px, 340px);
}

.designer-sidebar,
.designer-properties {
  min-width: 0;
  min-height: 0;
  overflow-y: auto;
  background: #f6f8fb;
}

.designer-sidebar {
  border-right: 1px solid var(--ea-border);
  padding: 14px;
  display: flex;
  flex-direction: column;
}

.designer-properties {
  border-left: 1px solid var(--ea-border);
  padding: 14px;
}

.definition-card,
.node-palette {
  border: 1px solid var(--ea-border);
  border-radius: 8px;
  background: #fff;
  padding: 14px;
}

.node-palette {
  order: 1;
}

.definition-card {
  order: 2;
  margin-top: 12px;
}

.definition-card h3,
.panel-title h3 {
  margin: 0;
  color: var(--ea-text);
  font-size: 15px;
}

.panel-title span {
  display: block;
  margin-top: 4px;
  color: var(--ea-muted);
  font-size: 12px;
  line-height: 1.5;
}

.palette-node {
  width: 100%;
  min-height: 62px;
  margin-top: 8px;
  border: 1px solid #d8e1ec;
  border-radius: 8px;
  background: #fff;
  padding: 9px 10px;
  display: flex;
  align-items: center;
  gap: 10px;
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.16s ease,
    background 0.16s ease,
    transform 0.16s ease;
}

.palette-node:hover {
  border-color: #93b6f5;
  background: #f8fbff;
  transform: translateY(-1px);
}

.palette-node.is-start {
  border-color: #cbd5e1;
  background: #fff;
}

.palette-node.is-submit {
  border-color: #cbd5e1;
  background: #fff;
}

.palette-node.is-approval {
  border-color: #bfdbfe;
  background: #f8fbff;
}

.palette-node.is-cc {
  border-color: #bbf7d0;
  background: #fbfefc;
}

.palette-node.is-condition {
  border-color: #fed7aa;
  background: #fffbeb;
}

.palette-node.is-end {
  border-color: #cbd5e1;
  background: #fff;
}

.palette-node strong,
.palette-node small {
  display: block;
}

.palette-node strong {
  color: var(--ea-text);
  font-size: 14px;
}

.palette-node small {
  margin-top: 3px;
  color: var(--ea-muted);
  font-size: 12px;
  line-height: 1.4;
}

.node-shape {
  width: 30px;
  height: 30px;
  flex: 0 0 auto;
  border: 2px solid #94a3b8;
  background: #fff;
}

.node-shape.is-start {
  border-color: #2563eb;
  background: #fff;
}

.node-shape.is-submit {
  border-color: #94a3b8;
  background: #fff;
}

.node-shape.is-approval {
  border-color: #2563eb;
  background: #f8fbff;
}

.node-shape.is-start,
.node-shape.is-end {
  border-radius: 50%;
}

.node-shape.is-approval,
.node-shape.is-submit,
.node-shape.is-cc {
  border-radius: 8px;
}

.node-shape.is-condition {
  width: 27px;
  height: 27px;
  margin: 1px;
  transform: rotate(45deg);
  border-width: 2px;
  border-color: #d97706;
  background: #fffbeb;
}

.node-shape.is-cc {
  border-color: #16a34a;
  border-style: dashed;
  background: #fbfefc;
}

.node-shape.is-end {
  border-color: #475569;
  background: #f8fafc;
}

.designer-workspace {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: #f8fafc;
}

.designer-actions-bar {
  flex: 0 0 auto;
  min-height: 52px;
  padding: 9px 14px;
  border-bottom: 1px solid var(--ea-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  background: #fff;
}

.designer-action-group {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.designer-canvas-shell {
  position: relative;
  flex: 1 1 auto;
  min-height: 0;
  background: var(--ea-bg);
}

.workflow-editor-canvas :deep(.lf-node .lf-node-shape) {
  filter: none;
  stroke-width: 1.4;
}

.workflow-editor-canvas :deep(.lf-edge path),
.workflow-editor-canvas :deep(.lf-edge polyline) {
  vector-effect: non-scaling-stroke;
}

.designer-canvas-shell :deep(.lf-node-selected .lf-node-shape),
.designer-canvas-shell :deep(.lf-node:hover .lf-node-shape) {
  filter: none;
  stroke: var(--ea-primary);
  stroke-width: 2;
}

.workflow-editor-canvas {
  width: 100%;
  height: 100%;
  min-height: 0;
}

.designer-empty {
  position: absolute;
  inset: 0;
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 10px;
  padding: 24px;
  text-align: center;
  pointer-events: none;
}

.designer-empty .el-button {
  pointer-events: auto;
}

.designer-empty-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
  pointer-events: auto;
}

.designer-empty strong,
.designer-empty span {
  display: block;
}

.designer-empty strong {
  color: var(--ea-text);
  font-size: 18px;
}

.designer-empty span {
  max-width: 460px;
  color: var(--ea-muted);
  line-height: 1.7;
}

.property-title {
  min-height: 58px;
  margin-bottom: 14px;
  border-bottom: 1px solid var(--ea-border);
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.property-title > div {
  min-width: 0;
  flex: 1 1 auto;
}

.property-title strong,
.property-title span {
  display: block;
}

.property-title strong {
  color: var(--ea-text);
  font-size: 16px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.property-title span {
  margin-top: 4px;
  color: var(--ea-muted);
  font-size: 12px;
  line-height: 1.45;
}

.property-title :deep(.el-tag) {
  max-width: 118px;
  flex: 0 0 auto;
}

.property-title :deep(.el-tag__content) {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}

.property-form {
  display: grid;
  gap: 2px;
}

.node-type-summary {
  margin: 0 0 14px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  padding: 10px 12px;
  background: #f8fbff;
}

.node-type-summary small {
  display: block;
  margin-top: 6px;
  color: var(--ea-muted);
  font-size: 12px;
  line-height: 1.55;
}

.node-type-badge {
  min-height: 26px;
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 0 10px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 800;
}

.node-type-badge.is-approval {
  background: #eff6ff;
  color: #1d4ed8;
}

.node-type-badge.is-condition {
  background: #fff7ed;
  color: #c2410c;
}

.node-type-badge.is-cc {
  background: #f0fdf4;
  color: #15803d;
}

.node-type-badge.is-start,
.node-type-badge.is-submit,
.node-type-badge.is-end {
  background: #f8fafc;
  color: #475569;
}

.preset-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: -4px 0 14px;
}

.preset-actions.is-inline {
  margin: 8px 0 0;
}

.preset-actions button {
  min-height: 28px;
  border: 1px solid #bfdbfe;
  border-radius: 999px;
  padding: 0 10px;
  background: #eff6ff;
  color: #1d4ed8;
  cursor: pointer;
  font-size: 12px;
  font-weight: 700;
}

.preset-actions button:hover {
  border-color: #93c5fd;
  background: #dbeafe;
}

.approver-rule-summary {
  margin: -2px 0 14px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  padding: 10px 12px;
  background: #f8fbff;
}

.approver-rule-summary span,
.approver-rule-summary strong,
.approver-rule-summary small {
  display: block;
}

.approver-rule-summary span {
  color: #64748b;
  font-size: 12px;
  line-height: 1.3;
}

.approver-rule-summary strong {
  margin-top: 4px;
  color: #0f172a;
  font-size: 14px;
  line-height: 1.35;
}

.approver-rule-summary small {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.55;
}

.edge-drag-hint {
  width: 100%;
  margin: 8px 0 0;
  color: var(--ea-muted);
  font-size: 12px;
  line-height: 1.45;
}

.workflow-editor-canvas :deep(.lf-edge-text),
.workflow-editor-canvas :deep(.lf-line-text) {
  pointer-events: auto;
  cursor: grab;
}

.workflow-editor-canvas :deep(.lf-line-text rect),
.workflow-editor-canvas :deep(.lf-line-text .lf-element-text),
.workflow-editor-canvas :deep(.lf-text-draggable) {
  pointer-events: all;
  cursor: grab !important;
}

.workflow-editor-canvas :deep(.lf-line-text:hover rect) {
  fill: #eff6ff;
  stroke: #60a5fa;
  stroke-width: 1.2;
  filter: none;
}

.workflow-editor-canvas :deep(.lf-line-text:hover .lf-element-text) {
  fill: #1d4ed8;
}

.workflow-editor-canvas :deep(.lf-dragging .lf-line-text),
.workflow-editor-canvas :deep(.lf-dragging .lf-line-text rect),
.workflow-editor-canvas :deep(.lf-dragging .lf-line-text .lf-element-text),
.workflow-editor-canvas :deep(.lf-dragging .lf-text-draggable) {
  cursor: grabbing !important;
}

.form-help {
  margin: 6px 0 0;
  color: var(--ea-muted);
  font-size: 12px;
  line-height: 1.5;
}

.property-actions {
  display: flex;
  gap: 10px;
  margin-top: 10px;
}

.workflow-inspector {
  display: grid;
  gap: 14px;
}

.inspector-score {
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #eff6ff;
  padding: 16px;
}

.inspector-score strong,
.inspector-score span,
.inspector-metrics strong,
.inspector-metrics span {
  display: block;
}

.inspector-score strong {
  color: #1e40af;
  font-size: 26px;
  line-height: 1;
}

.inspector-score span,
.inspector-metrics span,
.inspector-section p {
  margin-top: 6px;
  color: var(--ea-muted);
  font-size: 12px;
  line-height: 1.6;
}

.inspector-metrics {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.inspector-metrics div,
.inspector-section {
  border: 1px solid var(--ea-border);
  border-radius: 8px;
  background: #fff;
}

.inspector-metrics div {
  padding: 10px 12px;
}

.inspector-metrics strong {
  color: var(--ea-text);
  font-size: 18px;
}

.inspector-section {
  padding: 14px;
}

.inspector-section h4 {
  margin: 0 0 10px;
  color: var(--ea-text);
  font-size: 14px;
}

.inspector-checks {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 8px;
}

.inspector-checks li {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #92400e;
  font-size: 13px;
}

.inspector-checks i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #f59e0b;
}

.inspector-checks li.is-ok {
  color: #166534;
}

.inspector-checks li.is-ok i {
  background: #22c55e;
}

.designer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}

.designer-footer > span {
  flex: 1 1 auto;
}

@media (max-width: 1180px) {
  .page-header {
    flex-direction: column;
  }

  .page-header-side {
    align-items: stretch;
  }

}

@media (max-width: 1024px) {
  .workflow-page {
    height: auto;
    min-height: calc(100dvh - 108px);
    overflow: visible;
  }

  .workflow-board-body {
    grid-template-columns: 1fr;
  }

  .definition-table-panel {
    border-right: 0;
    border-bottom: 1px solid var(--ea-border);
  }

  .workflow-designer,
  .workflow-designer.is-properties-empty {
    grid-template-columns: minmax(210px, 230px) minmax(0, 1fr) minmax(230px, 260px);
  }

  .designer-properties {
    grid-column: auto;
    border-left: 1px solid var(--ea-border);
    border-top: 0;
  }
}

@media (max-width: 900px) {
  .page-header {
    flex-direction: column;
  }

  .page-actions {
    width: 100%;
    flex-wrap: wrap;
  }

  .workflow-board-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .toolbar-left {
    align-items: stretch;
    flex-direction: column;
  }

  .workflow-list-search,
  .workflow-status-filter {
    width: 100%;
    flex: 0 0 auto;
  }

  .workflow-designer,
  .workflow-designer.is-properties-empty {
    grid-template-columns: minmax(200px, 220px) minmax(0, 1fr) minmax(220px, 240px);
    min-height: 0;
    overflow: hidden;
  }

  .designer-sidebar,
  .designer-properties {
    max-height: none;
    overflow-y: auto;
  }

  .designer-sidebar {
    border-right: 1px solid var(--ea-border);
  }

  .designer-properties {
    border-left: 1px solid var(--ea-border);
    border-top: 0;
  }

  .designer-canvas-shell {
    min-height: 0;
  }

  .designer-actions-bar,
  .designer-footer {
    align-items: center;
  }

  .toolbar-actions {
    flex-wrap: wrap;
  }
}

@media (max-width: 640px) {
  .workflow-board {
    min-height: 0;
  }
}

@media (max-width: 760px) {
  .workflow-designer,
  .workflow-designer.is-properties-empty {
    grid-template-columns: 1fr;
    overflow: auto;
  }

  .designer-sidebar,
  .designer-properties {
    overflow: visible;
    border-right: 0;
    border-left: 0;
  }

  .designer-properties {
    border-top: 1px solid var(--ea-border);
  }

  .designer-canvas-shell {
    min-height: 420px;
  }

  .designer-actions-bar {
    align-items: flex-start;
  }
}
</style>

<template>
  <section class="resource-page workflow-instance-monitor-page">
    <template v-if="canManageInstances">
    <div class="resource-hero">
      <div>
        <h1>流程实例</h1>
        <p>具备流程实例管理权限的管理员按运行中和历史流程分别监控实例，进入详情查看申请内容、流程图和处理动态，并对运行中待办执行催办、转办或终止。</p>
      </div>
    </div>

    <div class="resource-metrics is-four">
      <div v-for="item in metrics" :key="item.label" class="resource-metric">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
        <small>{{ item.hint }}</small>
      </div>
    </div>

    <section ref="tablePanelRef" class="surface resource-panel workflow-instance-panel is-fluid-table">
      <el-tabs v-model="query.scope" class="workflow-scope-tabs" @tab-change="handleScopeChange">
        <el-tab-pane label="运行中流程" name="RUNTIME" />
        <el-tab-pane label="历史流程" name="HISTORY" />
      </el-tabs>
      <div class="table-control-row">
        <el-form :inline="true" class="filter-bar">
          <el-form-item label="关键词">
            <el-input v-model="query.keyword" placeholder="标题 / 单号" clearable @keyup.enter="handleSearch" />
          </el-form-item>
          <el-form-item v-if="query.scope === 'HISTORY'" label="状态">
            <el-select v-model="query.status" placeholder="全部历史状态" clearable style="width: 148px">
              <el-option label="已通过" value="APPROVED" />
              <el-option label="已驳回" value="REJECTED" />
              <el-option label="已撤回" value="REVOKED" />
              <el-option label="已终止" value="TERMINATED" />
            </el-select>
          </el-form-item>
          <el-form-item label="业务">
            <el-select v-model="query.businessType" placeholder="全部业务" clearable style="width: 132px">
              <el-option label="请假" value="leave" />
              <el-option label="采购" value="purchase" />
              <el-option label="报修" value="repair" />
              <el-option label="报销" value="expense" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" plain :icon="Search" :loading="loading" @click="handleSearch">查询</el-button>
            <el-button @click="resetFilters">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <el-table
        v-loading="loading"
        :data="instances"
        row-key="id"
        :height="tableHeight"
        class="admin-table instance-monitor-table"
        empty-text="暂无流程实例"
        @row-click="(row: WorkflowInstance) => openInstance(row.id)"
      >
        <el-table-column label="流程" min-width="300">
          <template #default="{ row }">
            <div class="instance-title-cell">
              <strong>{{ row.title }}</strong>
              <span>{{ row.processKey }} / {{ row.businessId || '无业务单号' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="业务" min-width="96">
          <template #default="{ row }">{{ businessTypeText(row.businessType) }}</template>
        </el-table-column>
        <el-table-column label="当前节点" min-width="160">
          <template #default="{ row }">
            <div class="current-node-cell">
              <strong>{{ currentNodeDisplayName(row) }}</strong>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="发起人" min-width="128">
          <template #default="{ row }">{{ assigneeName(row.initiatorId) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="instanceStatusType(row.status)" effect="plain">{{ instanceStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发起时间" min-width="166">
          <template #default="{ row }">{{ formatTime(row.startedAt) }}</template>
        </el-table-column>
        <el-table-column label="结束时间" min-width="166">
          <template #default="{ row }">{{ formatTime(row.endedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="156" fixed="right" align="center" header-align="center">
          <template #default="{ row }">
            <div class="table-actions" @click.stop>
              <el-button text type="primary" @click.stop="openInstance(row.id)">详情</el-button>
              <el-dropdown trigger="click" placement="bottom-end">
                <el-button class="workflow-more-button" text :icon="MoreFilled" title="更多操作" aria-label="更多操作" @click.stop>
                  更多
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu class="workflow-row-action-menu">
                    <el-dropdown-item
                      :disabled="row.status !== 'RUNNING' || !canUsePermission(PermissionCodes.workflow.taskRemind)"
                      @click.stop="remindInstance(row)"
                    >
                      催办当前节点
                    </el-dropdown-item>
                    <el-dropdown-item
                      :disabled="row.status !== 'RUNNING' || !canUsePermission(PermissionCodes.workflow.taskTransfer)"
                      @click.stop="openTransferDrawer(row)"
                    >
                      转办当前待办
                    </el-dropdown-item>
                    <el-dropdown-item
                      class="is-danger"
                      :disabled="row.status !== 'RUNNING' || !canUsePermission(PermissionCodes.workflow.instanceTerminate)"
                      @click.stop="terminateInstance(row)"
                    >
                      终止流程
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer is-split">
        <span>共 {{ total }} 条，当前页 {{ instances.length }} 条</span>
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.limit"
          background
          layout="sizes, prev, pager, next"
          :page-sizes="[10, 20, 50]"
          :total="total"
          @current-change="loadInstances"
          @size-change="handleSizeChange"
        />
      </div>
    </section>

    <el-drawer v-model="transferDrawerVisible" title="转办当前待办" size="min(420px, 92vw)" append-to-body>
      <div v-if="activeTransferTask" class="transfer-summary">
        <strong>{{ activeTransferInstance?.title || activeTransferTask.instanceTitle || '流程实例' }}</strong>
        <span>{{ activeTransferTask.nodeName }} / 当前处理人：{{ assigneeName(activeTransferTask.assigneeId) }}</span>
      </div>
      <el-form label-position="top" class="transfer-form">
        <el-form-item label="新处理人" required>
          <el-select v-model="transferForm.targetUserId" filterable placeholder="选择转办人员" class="full-width">
            <el-option v-for="item in assignees" :key="item.value" :label="item.name" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="转办原因" required>
          <el-input
            v-model="transferForm.comment"
            type="textarea"
            :rows="5"
            maxlength="500"
            show-word-limit
            placeholder="说明管理员转办原因，便于后续审计"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferDrawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="transferring" @click="submitTransfer">确认转办</el-button>
      </template>
    </el-drawer>

    <WorkflowInstanceDetailDrawer
      v-model="detailDrawerVisible"
      title="流程实例详情"
      :detail="detail"
      :assignees="assignees"
      :repair-attachments="detailRepairAttachments"
      :current-user-id="auth.user?.userId"
      :can-approve-task="canUsePermission(PermissionCodes.workflow.taskApprove)"
      :can-reject-task="canUsePermission(PermissionCodes.workflow.taskReject)"
      :action-submitting="detailActionSubmitting"
      @task-action="submitDetailTaskAction"
    />
    </template>

    <div v-else class="instance-permission-empty">
      <el-empty description="缺少流程实例管理权限" />
    </div>
  </section>
</template>

<script setup lang="ts">
import { MoreFilled, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { PermissionCodes } from '@/permissions/codes'
import {
  approveWorkflowTask,
  rejectWorkflowTask,
  remindWorkflowTask,
  transferWorkflowTask
} from '@/features/workflow/taskApi'
import {
  getWorkflowInstance,
  pageWorkflowInstances,
  terminateWorkflowInstance,
  type WorkflowInstanceScope
} from '@/features/workflow/instanceApi'
import { listWorkflowAssignees } from '@/features/workflow/definitionApi'
import { getRepairRequestByWorkflowInstance } from '@/features/workflow/repairApi'
import type {
  RepairAttachment,
  WorkflowAssigneeOption,
  WorkflowGraph,
  WorkflowInstance,
  WorkflowInstanceDetail,
  WorkflowTask,
  WorkflowTaskActionPayload
} from '@/features/workflow/types'
import type { EntityId } from '@/features/system/types'
import { useAuthStore } from '@/stores/auth'
import { useFluidTableHeight } from '@/components/table/useFluidTableHeight'
import WorkflowInstanceDetailDrawer from './components/WorkflowInstanceDetailDrawer.vue'

const auth = useAuthStore()
const loading = ref(false)
const instances = ref<WorkflowInstance[]>([])
const total = ref(0)
const tablePanelRef = ref<HTMLElement>()
const { tableHeight, updateTableHeight } = useFluidTableHeight(tablePanelRef)
const assignees = ref<WorkflowAssigneeOption[]>([])
const assigneesLoaded = ref(false)
const detail = ref<WorkflowInstanceDetail>()
const detailRepairAttachments = ref<RepairAttachment[]>([])
const detailDrawerVisible = ref(false)
const transferDrawerVisible = ref(false)
const transferring = ref(false)
const detailActionSubmitting = ref(false)
const activeTransferTask = ref<WorkflowTask>()
const activeTransferInstance = ref<WorkflowInstance>()
const transferForm = reactive<{
  targetUserId?: EntityId
  comment: string
}>({
  targetUserId: undefined,
  comment: ''
})
const query = reactive({
  page: 1,
  limit: 10,
  scope: 'RUNTIME' as WorkflowInstanceScope,
  keyword: '',
  status: '',
  businessType: ''
})
const canManageInstances = computed(() => auth.hasAnyPermission([PermissionCodes.workflow.instanceManage]))
type DetailTaskActionPayload = { type: 'approve' | 'reject'; task: WorkflowTask; comment: string }

const metrics = computed(() => {
  const running = instances.value.filter((item) => item.status === 'RUNNING').length
  const finished = instances.value.filter((item) => item.status !== 'RUNNING').length
  const needIntervention = instances.value.filter((item) => item.status === 'RUNNING' && !item.currentNodeKey).length
  return [
    { label: '实例总量', value: total.value.toLocaleString('zh-CN'), hint: '当前筛选条件下的流程数' },
    { label: '审批中', value: running.toLocaleString('zh-CN'), hint: '当前页运行中实例' },
    { label: '已结束', value: finished.toLocaleString('zh-CN'), hint: '当前页归档或终态实例' },
    { label: '待排查', value: needIntervention.toLocaleString('zh-CN'), hint: '运行中但无当前节点' }
  ]
})

onMounted(() => {
  if (!canManageInstances.value) {
    ElMessage.error('缺少流程实例管理权限')
    return
  }
  void Promise.all([loadInstances(), ensureAssignees()])
})

async function loadInstances() {
  if (!canManageInstances.value) {
    instances.value = []
    total.value = 0
    return
  }
  loading.value = true
  try {
    const result = await pageWorkflowInstances({
      page: query.page,
      limit: query.limit,
      scope: query.scope,
      keyword: query.keyword,
      status: query.scope === 'HISTORY' ? query.status : undefined,
      businessType: query.businessType,
      manage: true
    })
    instances.value = result.list
    total.value = result.total
  } finally {
    loading.value = false
    updateTableHeight()
  }
}

function handleSearch() {
  query.page = 1
  void loadInstances()
}

function resetFilters() {
  query.keyword = ''
  query.status = ''
  query.businessType = ''
  handleSearch()
}

function handleScopeChange() {
  query.page = 1
  query.status = ''
  void loadInstances()
}

function handleSizeChange() {
  query.page = 1
  void loadInstances()
}

async function openInstance(instanceId: EntityId) {
  const instanceDetail = await loadInstanceDetail(instanceId)
  detail.value = instanceDetail
  detailRepairAttachments.value = await loadRepairAttachments(instanceDetail)
  detailDrawerVisible.value = true
}

async function openTransferDrawer(instance: WorkflowInstance) {
  const instanceDetail = await loadInstanceDetail(instance.id)
  const pendingTasks = instanceDetail.tasks.filter((task) => task.status === 'PENDING')
  if (!pendingTasks.length) {
    ElMessage.warning('当前流程没有可转办的待办任务')
    return
  }
  activeTransferInstance.value = instanceDetail.instance
  activeTransferTask.value = pendingTasks[0]
  transferForm.targetUserId = undefined
  transferForm.comment = ''
  transferDrawerVisible.value = true
}

async function submitTransfer() {
  if (!activeTransferTask.value) return
  if (!transferForm.targetUserId) {
    ElMessage.warning('请选择新处理人')
    return
  }
  if (!transferForm.comment.trim()) {
    ElMessage.warning('请填写转办原因')
    return
  }
  transferring.value = true
  try {
    detail.value = await transferWorkflowTask(activeTransferTask.value.id, {
      targetUserId: transferForm.targetUserId,
      comment: transferForm.comment.trim()
    })
    ElMessage.success('已转办当前待办')
    transferDrawerVisible.value = false
    await loadInstances()
  } finally {
    transferring.value = false
  }
}

async function submitDetailTaskAction(payload: DetailTaskActionPayload) {
  const permission = payload.type === 'approve' ? PermissionCodes.workflow.taskApprove : PermissionCodes.workflow.taskReject
  if (!canUsePermission(permission)) {
    ElMessage.warning('没有该任务操作权限')
    return
  }
  if (payload.type === 'reject' && !payload.comment.trim()) {
    ElMessage.warning('请填写处理意见')
    return
  }
  detailActionSubmitting.value = true
  try {
    await performDetailTaskAction(payload.task, payload.type, { comment: payload.comment.trim() })
    ElMessage.success('处理成功')
  } finally {
    detailActionSubmitting.value = false
  }
}

async function performDetailTaskAction(task: WorkflowTask, type: 'approve' | 'reject', payload: WorkflowTaskActionPayload) {
  if (type === 'approve') await approveWorkflowTask(task.id, payload)
  if (type === 'reject') await rejectWorkflowTask(task.id, payload)
  const instanceDetail = await getWorkflowInstance(task.instanceId)
  detail.value = instanceDetail
  detailRepairAttachments.value = await loadRepairAttachments(instanceDetail)
  await loadInstances()
}

async function remindInstance(instance: WorkflowInstance) {
  const instanceDetail = await loadInstanceDetail(instance.id)
  const pendingTask = instanceDetail.tasks.find((task) => task.status === 'PENDING')
  if (!pendingTask) {
    ElMessage.warning('当前流程没有可催办的待办任务')
    return
  }
  await remindTask(pendingTask, instanceDetail)
}

async function remindTask(task: WorkflowTask, currentDetail?: WorkflowInstanceDetail) {
  await remindWorkflowTask(task.id, { comment: '管理员催办' })
  ElMessage.success('已记录催办')
  if (currentDetail) detail.value = await getWorkflowInstance(currentDetail.instance.id)
}

async function terminateInstance(instance: WorkflowInstance) {
  const confirmed = await ElMessageBox.confirm('终止后运行中待办会取消，流程将进入终态。', '终止流程实例', {
    type: 'warning',
    confirmButtonText: '终止流程',
    cancelButtonText: '取消'
  }).then(() => true).catch(() => false)
  if (!confirmed) return
  await terminateWorkflowInstance(instance.id, { comment: '管理员终止流程' })
  ElMessage.success('流程已终止')
  await loadInstances()
}

async function loadRepairAttachments(instanceDetail: WorkflowInstanceDetail) {
  if (instanceDetail.instance.businessType !== 'repair') return []
  try {
    const repairRequest = await getRepairRequestByWorkflowInstance(instanceDetail.instance.id)
    return repairRequest.attachments || []
  } catch {
    return []
  }
}

async function loadInstanceDetail(instanceId: EntityId) {
  const [instanceDetail] = await Promise.all([getWorkflowInstance(instanceId), ensureAssignees()])
  return instanceDetail
}

async function ensureAssignees() {
  if (assigneesLoaded.value) return
  assignees.value = await listWorkflowAssignees()
  assigneesLoaded.value = true
}

function canUsePermission(permission: string) {
  return auth.hasAnyPermission([permission])
}

function assigneeName(userId?: EntityId) {
  if (!userId) return '-'
  const option = assignees.value.find((item) => String(item.value) === String(userId))
  return option?.name || `用户 ${userId}`
}

function currentNodeDisplayName(instance: WorkflowInstance) {
  if (!instance.currentNodeKey) {
    return instance.status === 'RUNNING' ? '等待流转' : '已结束'
  }
  const graphNode = workflowGraphNodes(instance.definitionSnapshotJson)
    .find((node) => graphNodeId(node) === instance.currentNodeKey)
  return graphNode ? graphNodeText(graphNode, instance.currentNodeKey) : instance.currentNodeKey
}

function workflowGraphNodes(graphJson?: string) {
  if (!graphJson) return []
  try {
    const graph = JSON.parse(graphJson) as WorkflowGraph
    return Array.isArray(graph.nodes) ? graph.nodes : []
  } catch {
    return []
  }
}

function graphNodeId(node: Record<string, unknown>) {
  return stringValue(node.id) || stringValue(node.properties && typeof node.properties === 'object'
    ? (node.properties as Record<string, unknown>).id
    : undefined)
}

function graphNodeText(node: Record<string, unknown>, fallback: string) {
  const text = node.text
  if (typeof text === 'string' && text.trim()) return text.trim()
  if (text && typeof text === 'object') {
    const value = (text as Record<string, unknown>).value
    if (typeof value === 'string' && value.trim()) return value.trim()
  }
  return fallback
}

function stringValue(value: unknown) {
  return typeof value === 'string' ? value.trim() : ''
}

function businessTypeText(type: string) {
  const textMap: Record<string, string> = {
    LEAVE: '请假',
    leave: '请假',
    expense: '报销',
    purchase: '采购',
    repair: '报修',
    workflow: '流程'
  }
  return textMap[type] || type || '流程'
}

function instanceStatusText(status: string) {
  const textMap: Record<string, string> = {
    RUNNING: '审批中',
    APPROVED: '已通过',
    REJECTED: '已驳回',
    REVOKED: '已撤回',
    TERMINATED: '已终止'
  }
  return textMap[status] || status
}

function instanceStatusType(status: string) {
  if (status === 'RUNNING') return 'warning'
  if (status === 'APPROVED') return 'success'
  if (status === 'REJECTED' || status === 'TERMINATED') return 'danger'
  return 'info'
}

function formatTime(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}
</script>

<style scoped>
.workflow-instance-monitor-page {
  min-width: 0;
}

.instance-permission-empty {
  display: grid;
  min-height: 360px;
  place-items: center;
  border: 1px solid var(--ea-border);
  border-radius: 8px;
  background: #fff;
}

.workflow-instance-panel {
  min-height: 0;
}

.workflow-scope-tabs {
  margin-bottom: 12px;
}

.instance-monitor-table {
  --el-table-fixed-right-column: inset -8px 0 8px -8px rgba(15, 23, 42, 0.08);
}

.instance-title-cell {
  min-width: 0;
}

.instance-title-cell strong,
.instance-title-cell span {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.instance-title-cell span {
  margin-top: 3px;
  color: var(--ea-muted);
  font-size: 12px;
}

.current-node-cell {
  min-width: 0;
}

.current-node-cell strong {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--ea-text);
  font-weight: 600;
}

.table-actions {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
}

.workflow-more-button {
  padding-inline: 6px;
}

.transfer-summary {
  display: grid;
  gap: 4px;
  margin-bottom: 16px;
  border: 1px solid var(--ea-border);
  border-radius: 8px;
  padding: 12px 14px;
  background: #f8fbff;
}

.transfer-summary span {
  color: var(--ea-muted);
  font-size: 13px;
}

.transfer-form {
  display: grid;
  gap: 4px;
}

.full-width {
  width: 100%;
}

:global(.workflow-row-action-menu .el-dropdown-menu__item) {
  min-width: 132px;
}

:global(.workflow-row-action-menu .el-dropdown-menu__item.is-danger:not(.is-disabled)) {
  color: #b91c1c;
}

@media (max-width: 760px) {
  .workflow-instance-monitor-page .table-control-row {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>

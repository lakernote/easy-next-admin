<template>
  <section class="workflow-task-page">
    <div class="page-head">
      <div>
        <h1>我的流程</h1>
        <p>按待办、已办、我发起和抄送分区处理，进入详情可查看完整流转记录。</p>
      </div>
      <div class="head-actions">
        <el-dropdown v-if="canStartWorkflow" trigger="click" @command="goApplication">
          <el-button type="primary" :icon="Plus">发起申请<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-for="item in applicationEntries" :key="item.path" :command="item.path">{{ item.title }}</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <section ref="taskBoardRef" class="surface task-board">
      <el-tabs v-model="activeTab" class="workflow-tabs">
        <el-tab-pane name="pending">
          <template #label>
            <span class="tab-label">待办任务 <em>{{ formatTotal(pendingTotal) }}</em></span>
          </template>
          <div v-if="!pendingTasks.length" class="task-empty">
            <strong>当前没有待处理流程</strong>
            <span>可以从常用申请发起一个流程，或等待其他人提交到你这里。</span>
            <el-dropdown v-if="canStartWorkflow" trigger="click" @command="goApplication">
              <el-button type="primary" plain :icon="Plus">发起申请<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-for="item in applicationEntries" :key="item.path" :command="item.path">{{ item.title }}</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
          <div v-else class="pending-workbench">
            <div class="pending-workbench-toolbar">
              <div class="pending-kpis">
                <button
                  v-for="option in pendingFilterOptions"
                  :key="option.value"
                  type="button"
                  class="pending-kpi"
                  :class="{ active: pendingUrgencyFilter === option.value, warning: option.tone === 'warning', danger: option.tone === 'danger' }"
                  @click="pendingUrgencyFilter = option.value"
                >
                  <span>{{ option.label }}</span>
                  <strong>{{ option.count }}</strong>
                </button>
              </div>
              <span class="pending-hint">按时效优先处理，点击行可查看完整流转记录。</span>
            </div>
            <el-table
              :data="filteredPendingTasks"
              class="admin-table process-table pending-process-table"
              :height="taskTableHeight(filteredPendingTasks.length)"
              :empty-text="pendingFilterEmptyText"
              @row-click="openPendingTaskRow"
            >
            <el-table-column label="流程" min-width="260">
              <template #default="{ row }">
                <div class="process-title-cell">
                  <strong>{{ instanceTitle(row) }}</strong>
                  <div class="process-subline">
                    <span>{{ taskBusinessSummary(row) }}</span>
                    <div class="process-tags">
                      <el-tag effect="plain" size="small">{{ businessTypeText(row.businessType || '') }}</el-tag>
                      <el-tag :type="taskUrgencyTagType(row)" effect="plain" size="small">{{ taskUrgencyText(row) }}</el-tag>
                    </div>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="申请人" width="170">
              <template #default="{ row }">
                <div class="applicant-cell">
                  <strong>{{ applicantName(row) }}</strong>
                  <span v-if="applicantAccount(row)">{{ applicantAccount(row) }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="当前节点" min-width="190">
              <template #default="{ row }">
                <div class="node-cell">
                  <strong>{{ row.nodeName }}</strong>
                  <span>{{ nodeFlowHint(row) }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="等待时长" width="140">
              <template #default="{ row }">
                <div class="waiting-cell" :class="taskUrgencyLevel(row)">
                  <strong>{{ taskWaitingText(row.startedAt) }}</strong>
                  <span>{{ taskDueHint(row) }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="到达时间" width="170">
              <template #default="{ row }">{{ formatTime(row.startedAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="214" fixed="right" align="center" header-align="center">
              <template #default="{ row }">
                <div class="table-actions" @click.stop>
                  <el-button
                    v-permission:disable="PermissionCodes.workflow.taskApprove"
                    type="primary"
                    size="small"
                    @click.stop="openTaskAction('approveWithComment', row)"
                  >
                    处理
                  </el-button>
                  <el-button text type="primary" size="small" @click.stop="openInstance(row.instanceId)">详情</el-button>
                  <el-dropdown trigger="click" placement="bottom-end">
                    <el-button class="workflow-more-button" size="small" :icon="MoreFilled" @click.stop>更多</el-button>
                    <template #dropdown>
                      <el-dropdown-menu class="workflow-row-action-menu">
                        <template v-for="group in rowActionGroups" :key="group.label">
                          <el-dropdown-item disabled class="workflow-menu-section-title">{{ group.label }}</el-dropdown-item>
                          <el-dropdown-item
                            v-for="action in group.actions"
                            :key="action.type"
                            :class="{ 'is-danger': action.danger }"
                            :disabled="!canUsePermission(action.permission)"
                            @click.stop="handleMoreTaskAction(action, row)"
                          >
                            {{ action.label }}
                          </el-dropdown-item>
                        </template>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </template>
            </el-table-column>
            </el-table>
          </div>
          <div class="table-footer process-pagination">
            <el-pagination
              v-model:current-page="pendingPage"
              v-model:page-size="pendingPageSize"
              background
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              :total="pendingTotal ?? 0"
              @size-change="() => handleTaskPageSizeChange('pending')"
              @current-change="() => loadActiveTab('pending')"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane name="done">
          <template #label>
            <span class="tab-label">已办任务 <em>{{ formatTotal(doneTotal) }}</em></span>
          </template>
          <el-table class="admin-table process-table" :data="doneTasks" :height="taskTableHeight(doneTasks.length)" empty-text="暂无已办任务" @row-click="(row: WorkflowTask) => openInstance(row.instanceId)">
            <el-table-column label="流程标题" min-width="220">
              <template #default="{ row }">
                <div class="process-title-cell">
                  <strong>{{ instanceTitle(row) }}</strong>
                  <span>{{ instanceMeta(row) }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="nodeName" label="办理节点" min-width="150" />
            <el-table-column prop="status" label="处理结果" width="120">
              <template #default="{ row }">
                <el-tag :type="taskStatusType(row.status)" effect="plain">{{ taskStatusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="approveComment" label="意见" min-width="220" show-overflow-tooltip />
            <el-table-column label="完成时间" width="180">
              <template #default="{ row }">{{ formatTime(row.finishedAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="110" fixed="right" align="center" header-align="center">
              <template #default="{ row }">
                <el-button text type="primary" @click.stop="openInstance(row.instanceId)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="table-footer process-pagination">
            <el-pagination
              v-model:current-page="donePage"
              v-model:page-size="donePageSize"
              background
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              :total="doneTotal ?? 0"
              @size-change="() => handleTaskPageSizeChange('done')"
              @current-change="() => loadActiveTab('done')"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane name="started">
          <template #label>
            <span class="tab-label">我发起的 <em>{{ formatTotal(startedTotal) }}</em></span>
          </template>
          <div class="started-scope-row">
            <el-radio-group v-model="startedScope" size="small" @change="handleStartedScopeChange">
              <el-radio-button value="RUNTIME">进行中</el-radio-button>
              <el-radio-button value="HISTORY">历史流程</el-radio-button>
            </el-radio-group>
          </div>
          <el-table class="admin-table process-table" :data="startedInstances" :height="taskTableHeight(startedInstances.length)" empty-text="暂无发起记录" @row-click="(row: WorkflowInstance) => openInstance(row.id)">
            <el-table-column prop="title" label="标题" min-width="220" />
            <el-table-column label="业务" width="110">
              <template #default="{ row }">{{ businessTypeText(row.businessType) }}</template>
            </el-table-column>
            <el-table-column prop="businessId" label="单号" width="180" />
            <el-table-column prop="status" label="状态" width="120">
              <template #default="{ row }">
                <el-tag :type="instanceStatusType(row.status)" effect="plain">{{ instanceStatusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="发起时间" width="180">
              <template #default="{ row }">{{ formatTime(row.startedAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="168" fixed="right" align="center" header-align="center">
              <template #default="{ row }">
                <div class="table-actions" @click.stop>
                  <el-button text type="primary" @click.stop="openInstance(row.id)">详情</el-button>
                  <el-dropdown v-if="row.status === 'RUNNING'" trigger="click" placement="bottom-end">
                    <el-button class="workflow-more-button" text :icon="MoreFilled" title="更多操作" aria-label="更多操作" @click.stop>
                      更多
                    </el-button>
                    <template #dropdown>
                      <el-dropdown-menu class="workflow-row-action-menu">
                        <el-dropdown-item
                          :disabled="!canUsePermission(PermissionCodes.workflow.taskRemind) || remindingInstanceId === row.id"
                          @click.stop="remindStartedInstance(row)"
                        >
                          催办流程
                        </el-dropdown-item>
                        <el-dropdown-item
                          class="is-danger"
                          :disabled="!canUsePermission(PermissionCodes.workflow.instanceRevoke) || revokingInstanceId === row.id"
                          @click.stop="revokeStartedInstance(row)"
                        >
                          撤回流程
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </template>
            </el-table-column>
          </el-table>
          <div class="table-footer process-pagination">
            <el-pagination
              v-model:current-page="startedPage"
              v-model:page-size="startedPageSize"
              background
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              :total="startedTotal ?? 0"
              @size-change="() => handleTaskPageSizeChange('started')"
              @current-change="() => loadActiveTab('started')"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane name="cc">
          <template #label>
            <span class="tab-label">抄送我的 <em>{{ formatTotal(ccTotal) }}</em></span>
          </template>
          <el-table class="admin-table process-table" :data="ccList" :height="taskTableHeight(ccList.length)" empty-text="暂无抄送流程" @row-click="openCcRow">
            <el-table-column label="流程标题" min-width="220">
              <template #default="{ row }">
                <div class="process-title-cell">
                  <strong>{{ instanceTitle(row) }}</strong>
                  <span>{{ instanceMeta(row) }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="nodeName" label="抄送节点" width="160">
              <template #default="{ row }">{{ row.nodeName || row.nodeKey || '-' }}</template>
            </el-table-column>
            <el-table-column prop="readStatus" label="阅读状态" width="110">
              <template #default="{ row }">
                <el-tag :type="row.readStatus ? 'success' : 'info'" effect="plain">{{ row.readStatus ? '已读' : '未读' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="抄送时间" width="180">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
          </el-table>
          <div class="table-footer process-pagination">
            <el-pagination
              v-model:current-page="ccPage"
              v-model:page-size="ccPageSize"
              background
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              :total="ccTotal ?? 0"
              @size-change="() => handleTaskPageSizeChange('cc')"
              @current-change="() => loadActiveTab('cc')"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </section>

    <el-drawer v-model="actionDrawerVisible" :title="actionTitle" size="min(420px, 92vw)" append-to-body>
      <div v-if="activeTask" class="action-task-summary">
        <strong>{{ instanceTitle(activeTask) }}</strong>
        <span>{{ activeTask.nodeName }} / {{ instanceMeta(activeTask) }}</span>
        <div class="action-task-meta">
          <em>申请人 {{ applicantName(activeTask) }}</em>
          <em>等待 {{ taskWaitingText(activeTask.startedAt) }}</em>
        </div>
      </div>
      <el-form label-position="top">
        <el-form-item v-if="requiresUserTarget" label="处理人">
          <el-select v-model="actionForm.targetUserId" filterable placeholder="选择人员" class="full-width">
            <el-option v-for="item in assignees" :key="item.value" :label="item.name" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="actionForm.type === 'return'" label="退回位置">
          <el-select v-model="actionForm.returnNodeKey" class="full-width">
            <el-option label="退回发起人补充" value="submit" />
            <el-option label="退回上一审批节点" value="previous" />
          </el-select>
        </el-form-item>
        <el-form-item label="处理意见">
          <el-input v-model="actionForm.comment" type="textarea" :rows="5" maxlength="1000" show-word-limit :placeholder="actionCommentPlaceholder" />
          <p class="form-help">{{ actionHelperText }}</p>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="actionDrawerVisible = false">取消</el-button>
        <el-button :type="actionSubmitButtonType" :loading="submittingAction" @click="submitTaskAction">{{ actionSubmitText }}</el-button>
      </template>
    </el-drawer>

    <WorkflowInstanceDetailDrawer
      v-model="detailDrawerVisible"
      :detail="detail"
      :assignees="assignees"
      :repair-attachments="detailRepairAttachments"
      :current-user-id="auth.user?.userId"
      :can-approve-task="canUsePermission(PermissionCodes.workflow.taskApprove)"
      :can-reject-task="canUsePermission(PermissionCodes.workflow.taskReject)"
      :action-submitting="detailActionSubmitting"
      @task-action="submitDetailTaskAction"
    />
  </section>
</template>

<script setup lang="ts">
import { ArrowDown, MoreFilled, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { PermissionCodes } from '@/permissions/codes'
import { useAuthStore } from '@/stores/auth'
import { useFluidTableHeight } from '@/components/table/useFluidTableHeight'
import {
  addSignWorkflowTask,
  approveWorkflowTask,
  delegateWorkflowTask,
  getWorkflowTaskCenterSummary,
  markWorkflowCcRead,
  pageWorkflowCc,
  pageWorkflowTasks,
  rejectWorkflowTask,
  remindWorkflowTask,
  removeSignWorkflowTask,
  returnWorkflowTask,
  transferWorkflowTask
} from '@/features/workflow/taskApi'
import {
  getWorkflowInstance,
  pageWorkflowInstances,
  revokeWorkflowInstance,
  type WorkflowInstanceScope
} from '@/features/workflow/instanceApi'
import { listWorkflowAssignees } from '@/features/workflow/definitionApi'
import { getRepairRequestByWorkflowInstance } from '@/features/workflow/repairApi'
import { loadWorkflowTaskCenterTab, type WorkflowTaskCenterApi } from '@/features/workflow/taskCenterLoader'
import type {
  WorkflowAssigneeOption,
  WorkflowCc,
  WorkflowInstance,
  WorkflowInstanceDetail,
  RepairAttachment,
  WorkflowTask,
  WorkflowTaskActionPayload
} from '@/features/workflow/types'
import type { EntityId } from '@/features/system/types'
import { resolveWorkflowTaskCenterTab, type WorkflowTaskCenterTab } from '@/features/workflow/taskCenterTabs'
import { workflowTaskAction, type TaskActionType, type WorkflowTaskActionMeta } from '@/features/workflow/taskActions'
import WorkflowInstanceDetailDrawer from './components/WorkflowInstanceDetailDrawer.vue'

type PendingUrgencyFilter = 'all' | 'today' | 'aging' | 'overdue'
type PendingUrgencyLevel = 'normal' | 'today' | 'aging' | 'overdue'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const loading = ref(false)
const activeTab = ref<WorkflowTaskCenterTab>(resolveWorkflowTaskCenterTab(route.query.tab))
const pendingTasks = ref<WorkflowTask[]>([])
const doneTasks = ref<WorkflowTask[]>([])
const startedInstances = ref<WorkflowInstance[]>([])
const ccList = ref<WorkflowCc[]>([])
const assignees = ref<WorkflowAssigneeOption[]>([])
const assigneesLoaded = ref(false)
const detail = ref<WorkflowInstanceDetail>()
const detailRepairAttachments = ref<RepairAttachment[]>([])
const detailDrawerVisible = ref(false)
const actionDrawerVisible = ref(false)
const submittingAction = ref(false)
const detailActionSubmitting = ref(false)
const remindingInstanceId = ref<EntityId | ''>('')
const revokingInstanceId = ref<EntityId | ''>('')
const activeTask = ref<WorkflowTask>()
const taskBoardRef = ref<HTMLElement>()
const { tableHeight: taskTableHeightValue, updateTableHeight: updateTaskTableHeight } = useFluidTableHeight(taskBoardRef, {
  tableSelector: '.el-tab-pane:not([aria-hidden="true"]) .admin-table',
  footerSelector: '.el-tab-pane:not([aria-hidden="true"]) .process-pagination'
})
const pendingUrgencyFilter = ref<PendingUrgencyFilter>('all')
const pendingPage = ref(1)
const pendingPageSize = ref(10)
const pendingTotal = ref(0)
const donePage = ref(1)
const donePageSize = ref(10)
const doneTotal = ref(0)
const startedPage = ref(1)
const startedPageSize = ref(10)
const startedTotal = ref(0)
const startedScope = ref<WorkflowInstanceScope>('RUNTIME')
const ccPage = ref(1)
const ccPageSize = ref(10)
const ccTotal = ref(0)
const actionForm = reactive<{
  type: TaskActionType
  comment: string
  targetUserId?: EntityId
  returnNodeKey: string
}>({
  type: 'approve',
  comment: '',
  targetUserId: undefined,
  returnNodeKey: 'submit'
})

const canStartWorkflow = computed(() => auth.hasAnyPermission([PermissionCodes.workflow.instanceStart]))
const rowActionGroups: ReadonlyArray<{ label: string; actions: WorkflowTaskActionMeta[] }> = [
  { label: '审批处理', actions: [requireTaskAction('reject')] },
  { label: '协同处理', actions: [requireTaskAction('transfer'), requireTaskAction('delegate'), requireTaskAction('remind')] },
  { label: '节点调整', actions: [requireTaskAction('return'), requireTaskAction('addSign'), requireTaskAction('removeSign')] }
]
const filteredPendingTasks = computed(() => pendingTasks.value.filter((task) => pendingMatchesFilter(task, pendingUrgencyFilter.value)))
const pendingFilterOptions = computed(() => [
  { value: 'all' as const, label: '全部待办', count: pendingTasks.value.length, tone: 'default' },
  { value: 'today' as const, label: '今日到达', count: pendingTasks.value.filter((task) => taskUrgencyLevel(task) === 'today').length, tone: 'default' },
  { value: 'aging' as const, label: '需关注', count: pendingTasks.value.filter((task) => taskUrgencyLevel(task) === 'aging').length, tone: 'warning' },
  { value: 'overdue' as const, label: '已超时', count: pendingTasks.value.filter((task) => taskUrgencyLevel(task) === 'overdue').length, tone: 'danger' }
])
const pendingFilterEmptyText = computed(() => pendingUrgencyFilter.value === 'all' ? '暂无待办任务' : '当前筛选没有待办任务')
const activeActionMeta = computed(() => workflowTaskAction(actionForm.type))
const actionTitle = computed(() => {
  if (activeTask.value?.nodeKey === 'submit' && (actionForm.type === 'approve' || actionForm.type === 'approveWithComment')) {
    return '重新提交'
  }
  return activeActionMeta.value?.title || '处理任务'
})
const requiresUserTarget = computed(() => Boolean(activeActionMeta.value?.requiresUserTarget))
const actionHelperText = computed(() => {
  const helpers: Record<TaskActionType, string> = {
    approve: '同意后，流程会自动进入下一审批节点。',
    approveWithComment: activeTask.value?.nodeKey === 'submit' ? '补充意见后，流程会重新提交到下一审批节点。' : '同意后，流程会自动进入下一审批节点。',
    reject: '驳回会结束本次申请，请写清楚原因。',
    transfer: '转办后，当前待办将交给新的处理人。',
    delegate: '委派用于临时请他人协助处理，并保留委派记录。',
    return: '可退回发起人补充，也可退回上一审批节点。',
    addSign: '加签会在当前节点追加处理人，追加人员处理完后才继续流转。',
    removeSign: '减签用于移除尚未处理的加签待办。',
    remind: '催办只记录提醒，不改变流程状态。'
  }
  return helpers[actionForm.type]
})
const actionCommentRequired = computed(() => Boolean(activeActionMeta.value?.requiresComment))
const actionCommentPlaceholder = computed(() => (actionCommentRequired.value ? '请填写原因或处理意见' : '可补充处理意见'))
const actionSubmitButtonType = computed(() => activeActionMeta.value?.danger ? 'danger' : 'primary')
const actionSubmitText = computed(() => {
  if (activeTask.value?.nodeKey === 'submit' && (actionForm.type === 'approve' || actionForm.type === 'approveWithComment')) {
    return '确认提交'
  }
  return activeActionMeta.value?.submitText || '确认处理'
})

const workflowTaskCenterApi: WorkflowTaskCenterApi = {
  pageWorkflowTasks,
  pageWorkflowInstances,
  pageWorkflowCc
}
const applicationEntries = [
  { title: '请假申请', path: '/workflow/leave' },
  { title: '采购申请', path: '/workflow/purchase' },
  { title: '报修申请', path: '/workflow/repair' }
]
type DetailTaskActionPayload = { type: 'approve' | 'reject'; task: WorkflowTask; comment: string }

onMounted(() => {
  void Promise.all([loadSummary(), loadActiveTab(), ensureAssignees()]).then(openRouteInstanceFromQuery).finally(() => {
    updateTaskTableHeight()
  })
  updateTaskTableHeight()
})

watch(() => route.query.tab, syncActiveTabFromRoute, { immediate: true })

watch(() => route.query.instanceId, () => {
  void openRouteInstanceFromQuery()
})

watch(activeTab, (tab) => {
  if (route.query.tab !== tab) {
    void router.replace({ path: route.path, query: { ...route.query, tab } })
  }
  void loadActiveTab(tab)
  updateTaskTableHeight()
})

function syncActiveTabFromRoute(tab: unknown) {
  const nextTab = resolveWorkflowTaskCenterTab(tab)
  if (activeTab.value !== nextTab) {
    activeTab.value = nextTab
  }
}

async function loadActiveTab(tab: WorkflowTaskCenterTab = activeTab.value) {
  loading.value = true
  try {
    const result = await loadWorkflowTaskCenterTab({
      tab,
      pages: {
        pending: { page: pendingPage.value, pageSize: pendingPageSize.value },
        done: { page: donePage.value, pageSize: donePageSize.value },
        started: { page: startedPage.value, pageSize: startedPageSize.value },
        cc: { page: ccPage.value, pageSize: ccPageSize.value }
      },
      api: workflowTaskCenterApi,
      startedScope: startedScope.value
    })
    if (result.tab === 'pending') {
      pendingTasks.value = result.list
      pendingTotal.value = result.total
    }
    if (result.tab === 'done') {
      doneTasks.value = result.list
      doneTotal.value = result.total
    }
    if (result.tab === 'started') {
      startedInstances.value = result.list
      startedTotal.value = result.total
    }
    if (result.tab === 'cc') {
      ccList.value = result.list
      ccTotal.value = result.total
    }
  } finally {
    loading.value = false
  }
}

async function loadSummary() {
  const summary = await getWorkflowTaskCenterSummary()
  pendingTotal.value = summary.pendingTotal
  doneTotal.value = summary.doneTotal
  if (activeTab.value !== 'started') {
    startedTotal.value = summary.startedTotal
  }
  ccTotal.value = summary.ccTotal
}

async function refreshTaskCenter() {
  await Promise.all([loadSummary(), loadActiveTab()])
}

function handleTaskPageSizeChange(type: 'pending' | 'done' | 'started' | 'cc') {
  if (type === 'pending') pendingPage.value = 1
  if (type === 'done') donePage.value = 1
  if (type === 'started') startedPage.value = 1
  if (type === 'cc') ccPage.value = 1
  void loadActiveTab(type)
}

function handleStartedScopeChange() {
  startedPage.value = 1
  void loadActiveTab('started')
}

function goApplication(path: string) {
  void router.push(path)
}

function taskTableHeight(_count: number) {
  return taskTableHeightValue.value
}

function requireTaskAction(type: TaskActionType) {
  const action = workflowTaskAction(type)
  if (!action) throw new Error(`Unknown workflow task action: ${type}`)
  return action
}

function openTaskAction(type: TaskActionType, task: WorkflowTask) {
  activeTask.value = task
  actionForm.type = type
  actionForm.comment = ''
  actionForm.targetUserId = undefined
  actionForm.returnNodeKey = 'submit'
  actionDrawerVisible.value = true
  if (workflowTaskAction(type)?.requiresUserTarget) {
    void ensureAssignees()
  }
}

function canUsePermission(permission: string) {
  return auth.hasAnyPermission([permission])
}

function handleMoreTaskAction(action: WorkflowTaskActionMeta, task: WorkflowTask) {
  if (!canUsePermission(action.permission)) {
    ElMessage.warning('没有该任务操作权限')
    return
  }
  openTaskAction(action.type, task)
}

async function submitTaskAction() {
  const task = activeTask.value
  if (!task) return
  if (!activeActionMeta.value) {
    ElMessage.warning('未知任务操作')
    return
  }
  if (requiresUserTarget.value && !actionForm.targetUserId) {
    ElMessage.warning('请选择处理人')
    return
  }
  if (actionCommentRequired.value && !actionForm.comment.trim()) {
    ElMessage.warning('请填写处理意见')
    return
  }
  submittingAction.value = true
  try {
    const payload = {
      comment: actionForm.comment,
      targetUserId: actionForm.targetUserId,
      returnNodeKey: actionForm.returnNodeKey
    }
    await performTaskAction(task, actionForm.type, payload)
    ElMessage.success('处理成功')
    actionDrawerVisible.value = false
  } finally {
    submittingAction.value = false
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
    await performTaskAction(payload.task, payload.type, { comment: payload.comment.trim() })
    ElMessage.success('处理成功')
  } finally {
    detailActionSubmitting.value = false
  }
}

async function performTaskAction(task: WorkflowTask, type: TaskActionType, payload: WorkflowTaskActionPayload) {
  if (type === 'approve' || type === 'approveWithComment') await approveWorkflowTask(task.id, payload)
  if (type === 'reject') await rejectWorkflowTask(task.id, payload)
  if (type === 'transfer') await transferWorkflowTask(task.id, payload)
  if (type === 'delegate') await delegateWorkflowTask(task.id, payload)
  if (type === 'return') await returnWorkflowTask(task.id, payload)
  if (type === 'addSign') await addSignWorkflowTask(task.id, payload)
  if (type === 'removeSign') await removeSignWorkflowTask(task.id, payload)
  if (type === 'remind') await remindWorkflowTask(task.id, payload)
  await refreshTaskCenter()
  if (detailDrawerVisible.value && String(detail.value?.instance.id ?? '') === String(task.instanceId)) {
    const instanceDetail = await getWorkflowInstance(task.instanceId)
    detail.value = instanceDetail
    detailRepairAttachments.value = await loadRepairAttachments(instanceDetail)
  }
}

async function openInstance(instanceId: EntityId) {
  const [instanceDetail] = await Promise.all([getWorkflowInstance(instanceId), ensureAssignees()])
  detail.value = instanceDetail
  detailRepairAttachments.value = await loadRepairAttachments(instanceDetail)
  detailDrawerVisible.value = true
}

async function openRouteInstanceFromQuery() {
  const instanceId = route.query.instanceId
  if (typeof instanceId !== 'string' || !instanceId.trim()) return
  if (detailDrawerVisible.value && String(detail.value?.instance.id ?? '') === instanceId) return
  try {
    await openInstance(instanceId)
  } catch {
    ElMessage.error('流程详情加载失败')
  }
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

async function remindStartedInstance(instance: WorkflowInstance) {
  remindingInstanceId.value = instance.id
  try {
    const instanceDetail = await getWorkflowInstance(instance.id)
    const pendingTask = instanceDetail.tasks.find((task) => task.status === 'PENDING')
    if (!pendingTask) {
      ElMessage.warning('当前流程没有可催办的待办')
      return
    }
    await remindWorkflowTask(pendingTask.id, { comment: '发起人催办' })
    ElMessage.success('已记录催办')
    await refreshTaskCenter()
  } finally {
    remindingInstanceId.value = ''
  }
}

async function revokeStartedInstance(instance: WorkflowInstance) {
  const confirmed = await ElMessageBox.confirm('撤回后流程会结束，审批人待办会同步取消。', '撤回流程', {
    type: 'warning',
    confirmButtonText: '撤回',
    cancelButtonText: '取消'
  }).then(() => true).catch(() => false)
  if (!confirmed) return
  revokingInstanceId.value = instance.id
  try {
    await revokeWorkflowInstance(instance.id, { comment: '发起人撤回' })
    ElMessage.success('流程已撤回')
    await refreshTaskCenter()
  } finally {
    revokingInstanceId.value = ''
  }
}

async function openCcRow(row: WorkflowCc) {
  if (!row.readStatus) {
    await markWorkflowCcRead(row.id, Boolean(row.historic))
    row.readStatus = 1
    row.readAt = new Date().toISOString()
  }
  await openInstance(row.instanceId)
}

function openPendingTaskRow(row: WorkflowTask, _column: unknown, event?: Event) {
  const target = event?.target
  if (target instanceof Element && target.closest('.table-actions')) {
    return
  }
  void openInstance(row.instanceId)
}

async function ensureAssignees() {
  if (assigneesLoaded.value) return
  assignees.value = await listWorkflowAssignees()
  assigneesLoaded.value = true
}

function instanceTitle(row: Pick<WorkflowTask | WorkflowCc, 'instanceId' | 'instanceTitle'>) {
  return row.instanceTitle || `流程 ${row.instanceId}`
}

function instanceMeta(row: Pick<WorkflowTask | WorkflowCc, 'businessType' | 'businessId'>) {
  const segments = [row.businessType ? businessTypeText(row.businessType) : '', row.businessId].filter(Boolean)
  return segments.length ? segments.join(' / ') : '流程'
}

function taskBusinessSummary(row: WorkflowTask) {
  const typeText = businessTypeText(row.businessType || 'workflow')
  return row.businessId ? `${typeText} / ${row.businessId}` : typeText
}

function applicantOption(userId?: EntityId) {
  if (!userId) return undefined
  return assignees.value.find((item) => String(item.value) === String(userId))
}

function applicantUserId(row: Pick<WorkflowTask, 'instanceInitiatorId' | 'createdBy'>) {
  return row.instanceInitiatorId || row.createdBy
}

function applicantName(row: Pick<WorkflowTask, 'instanceInitiatorId' | 'createdBy'>) {
  const applicantId = applicantUserId(row)
  const applicant = applicantOption(applicantId)
  if (applicant) return applicant.name
  return applicantId ? `用户 ${applicantId}` : '未记录'
}

function applicantAccount(row: Pick<WorkflowTask, 'instanceInitiatorId' | 'createdBy'>) {
  const applicant = applicantOption(applicantUserId(row))
  return applicant?.userName ? `@${applicant.userName}` : ''
}

function nodeFlowHint(row: WorkflowTask) {
  if (row.nodeKey === 'submit') return '等待补充后重新提交'
  return '待你处理'
}

function pendingMatchesFilter(task: WorkflowTask, filter: PendingUrgencyFilter) {
  if (filter === 'all') return true
  return taskUrgencyLevel(task) === filter
}

function taskUrgencyLevel(row: Pick<WorkflowTask, 'startedAt'>): PendingUrgencyLevel {
  const hours = hoursSince(row.startedAt)
  if (hours === undefined) return 'normal'
  if (hours >= 24) return 'overdue'
  if (hours >= 8) return 'aging'
  if (isToday(row.startedAt)) return 'today'
  return 'normal'
}

function taskUrgencyText(row: Pick<WorkflowTask, 'startedAt'>) {
  const level = taskUrgencyLevel(row)
  if (level === 'overdue') return '已超时'
  if (level === 'aging') return '需关注'
  if (level === 'today') return '今日到达'
  return '正常'
}

function taskUrgencyTagType(row: Pick<WorkflowTask, 'startedAt'>) {
  const level = taskUrgencyLevel(row)
  if (level === 'overdue') return 'danger'
  if (level === 'aging') return 'warning'
  if (level === 'today') return 'primary'
  return 'info'
}

function taskDueHint(row: Pick<WorkflowTask, 'startedAt'>) {
  const level = taskUrgencyLevel(row)
  if (level === 'overdue') return '超过24小时'
  if (level === 'aging') return '超过8小时'
  if (level === 'today') return '今日到达'
  return '正常流转'
}

function taskWaitingText(value?: string) {
  const hours = hoursSince(value)
  if (hours === undefined) return '-'
  if (hours < 1) return '1小时内'
  if (hours < 24) return `${Math.floor(hours)}小时`
  const days = Math.floor(hours / 24)
  const remainHours = Math.floor(hours % 24)
  return remainHours > 0 ? `${days}天${remainHours}小时` : `${days}天`
}

function hoursSince(value?: string) {
  const time = parseTime(value)
  if (!time) return undefined
  return Math.max(0, (Date.now() - time.getTime()) / 3_600_000)
}

function isToday(value?: string) {
  const time = parseTime(value)
  if (!time) return false
  const now = new Date()
  return time.getFullYear() === now.getFullYear() && time.getMonth() === now.getMonth() && time.getDate() === now.getDate()
}

function parseTime(value?: string) {
  if (!value) return undefined
  const normalized = value.includes('T') ? value : value.replace(' ', 'T')
  const time = new Date(normalized)
  return Number.isNaN(time.getTime()) ? undefined : time
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
  return textMap[type] || type
}

function formatTotal(total?: number) {
  return total ?? 0
}

function formatTime(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}

function taskStatusText(status: string) {
  const textMap: Record<string, string> = {
    PENDING: '待处理',
    APPROVED: '已同意',
    REJECTED: '已驳回',
    TRANSFERRED: '已转办',
    DELEGATED: '已委派',
    CANCELED: '已取消'
  }
  return textMap[status] || status
}

function taskStatusType(status: string) {
  if (status === 'PENDING') return 'warning'
  if (status === 'APPROVED') return 'success'
  if (status === 'REJECTED') return 'danger'
  return 'info'
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
</script>

<style scoped>
.workflow-task-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100%;
  min-height: 100%;
}

.page-head,
.surface {
  border: 1px solid var(--ea-border, #d8e0ec);
  border-radius: 8px;
  background: #fff;
}

.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  padding: 4px 2px 0;
  border: 0;
  background: transparent;
}

.page-head > div:first-child {
  min-width: 0;
}

.page-head h1 {
  margin: 0;
  color: #172033;
}

.page-head p,
.process-title-cell span,
.applicant-cell span,
.node-cell span,
.waiting-cell span {
  margin: 6px 0 0;
  color: #66758f;
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 0 0 auto;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.surface {
  min-width: 0;
  padding: 10px 18px 18px;
}

.task-board {
  flex: 1 1 auto;
  min-height: 420px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.workflow-tabs {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.workflow-tabs :deep(.el-tabs__content) {
  flex: 1 1 auto;
  min-height: 0;
}

.workflow-tabs :deep(.el-tab-pane) {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.workflow-tabs :deep(.el-tabs__header) {
  flex: 0 0 auto;
  margin-bottom: 14px;
}

.started-scope-row {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 700;
}

.tab-label em {
  min-width: 22px;
  height: 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: #eff6ff;
  color: #2563eb;
  font-size: 12px;
  font-style: normal;
  font-variant-numeric: tabular-nums;
}

.task-empty {
  flex: 1 1 auto;
  min-height: 260px;
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 10px;
  text-align: center;
}

.task-empty strong,
.task-empty span {
  display: block;
}

.task-empty strong {
  color: #172033;
  font-size: 16px;
}

.task-empty span {
  max-width: 440px;
  color: #66758f;
  line-height: 1.7;
}

.pending-workbench {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.pending-workbench-toolbar {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
  padding: 10px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
}

.pending-kpis {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.pending-kpi {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 32px;
  padding: 0 12px;
  border: 1px solid #dbe3ef;
  border-radius: 999px;
  background: #fff;
  color: #3d4b63;
  font: inherit;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.pending-kpi strong {
  font-variant-numeric: tabular-nums;
  color: #172033;
}

.pending-kpi.active {
  border-color: #3b82f6;
  background: #eff6ff;
  color: #1d4ed8;
}

.pending-kpi.warning.active {
  border-color: #f59e0b;
  background: #fffbeb;
  color: #b45309;
}

.pending-kpi.danger.active {
  border-color: #ef4444;
  background: #fef2f2;
  color: #b91c1c;
}

.pending-hint {
  color: #66758f;
  font-size: 13px;
  white-space: nowrap;
}

.process-table :deep(.el-table__row) {
  cursor: pointer;
}

.workflow-tabs :deep(.el-table) {
  flex: 1 1 auto;
  min-height: 0;
}

.process-table :deep(.el-table__cell) {
  padding: 9px 0;
}

.process-table {
  --el-table-fixed-right-column: inset -8px 0 8px -8px rgba(15, 23, 42, 0.08);
}

.process-pagination {
  flex: 0 0 auto;
  display: flex;
  justify-content: flex-end;
}

.process-title-cell strong,
.process-title-cell span,
.applicant-cell strong,
.applicant-cell span,
.node-cell strong,
.node-cell span,
.waiting-cell strong,
.waiting-cell span {
  display: block;
  min-width: 0;
}

.process-title-cell {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.process-title-cell strong,
.applicant-cell strong,
.node-cell strong,
.waiting-cell strong {
  color: #172033;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.process-subline {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.process-subline > span {
  flex: 0 1 auto;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.process-tags {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 22px;
  flex-wrap: nowrap;
}

.process-tags :deep(.el-tag) {
  height: 22px;
  display: inline-flex;
  align-items: center;
  margin: 0;
  line-height: 20px;
  white-space: nowrap;
}

.applicant-cell,
.node-cell,
.waiting-cell {
  min-width: 0;
}

.waiting-cell strong {
  font-variant-numeric: tabular-nums;
}

.waiting-cell.aging strong {
  color: #b45309;
}

.waiting-cell.overdue strong {
  color: #b91c1c;
}

.table-actions {
  display: flex;
  flex-wrap: nowrap;
  gap: 5px;
  align-items: center;
  justify-content: center;
  white-space: nowrap;
}

.table-actions :deep(.el-button) {
  flex: none;
  margin-left: 0;
  font-weight: 600;
}

.table-actions :deep(.el-dropdown) {
  flex: none;
}

.table-actions :deep(.workflow-more-button.el-button) {
  --el-button-text-color: #64748b;
  --el-button-hover-text-color: var(--ea-primary);
  --el-button-active-text-color: #64748b;
  color: #64748b;
}

.table-actions :deep(.workflow-more-button.el-button .el-icon),
.table-actions :deep(.workflow-more-button.el-button span) {
  color: inherit;
}

.table-actions :deep(.workflow-more-button.el-button:hover) {
  color: var(--ea-primary);
}

.table-actions :deep(.workflow-more-button.el-button:focus:not(:hover)),
.table-actions :deep(.workflow-more-button.el-button:active:not(:hover)) {
  color: #64748b;
}

:global(.workflow-row-action-menu .el-dropdown-menu__item) {
  min-width: 128px;
  font-weight: 600;
}

:global(.workflow-row-action-menu .workflow-menu-section-title) {
  height: 28px;
  margin-top: 4px;
  color: #94a3b8;
  cursor: default;
  font-size: 12px;
  font-weight: 700;
}

:global(.workflow-row-action-menu .workflow-menu-section-title:first-child) {
  margin-top: 0;
}

:global(.workflow-row-action-menu .el-dropdown-menu__item.is-danger:not(.is-disabled)) {
  color: #dc2626;
}

.action-task-summary {
  margin-bottom: 16px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  padding: 12px 14px;
  background: #f8fbff;
}

.action-task-summary strong,
.action-task-summary span {
  display: block;
  min-width: 0;
}

.action-task-summary strong {
  color: #172033;
}

.action-task-summary span {
  margin-top: 4px;
  color: #66758f;
  font-size: 13px;
}

.action-task-meta {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 10px;
}

.action-task-meta em {
  padding: 3px 8px;
  border-radius: 999px;
  background: #eef4ff;
  color: #3d4b63;
  font-size: 12px;
  font-style: normal;
  font-weight: 700;
}

.full-width {
  width: 100%;
}

.form-help {
  margin: 8px 0 0;
  color: #66758f;
  font-size: 13px;
  line-height: 1.6;
}

@media (max-width: 900px) {
  .page-head {
    flex-direction: column;
    align-items: stretch;
  }

  .head-actions {
    justify-content: flex-start;
    flex-wrap: wrap;
  }

  .pending-workbench-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .pending-hint {
    white-space: normal;
  }
}
</style>

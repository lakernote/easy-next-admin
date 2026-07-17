<template>
  <el-drawer v-model="visible" class="workflow-instance-detail-drawer" :title="title" size="min(1120px, 92vw)" append-to-body>
    <div v-if="detail" class="detail-panel">
      <section v-if="businessFields.length" class="detail-flat-section detail-business-section">
        <div class="detail-section-head is-compact application-paper-section-head">
          <div>
            <strong>申请内容</strong>
            <span>按企业审批单格式归档展示，便于核对和打印留存。</span>
          </div>
        </div>
        <WorkflowApplicationPaper
          :title="businessSheetTitle"
          :meta="businessPaperMeta"
          :fields="businessFields"
          :approvals="businessPaperApprovals"
        />
      </section>

      <section v-if="repairAttachments.length" class="detail-flat-section detail-attachment-section">
        <div class="detail-section-head">
          <div>
            <strong>报修图片</strong>
            <span>查看申请人提交的故障现场图片。</span>
          </div>
        </div>
        <div class="repair-preview-grid">
          <div v-for="item in repairAttachments" :key="item.fileId" class="repair-preview-item">
            <el-image
              v-if="attachmentPreviewUrl(item)"
              :src="attachmentPreviewUrl(item)"
              :preview-src-list="repairPreviewUrls"
              :initial-index="repairPreviewIndex(item)"
              fit="cover"
              preview-teleported
              hide-on-click-modal
            />
            <div v-else class="repair-preview-placeholder">
              <el-icon><Picture /></el-icon>
              <span>{{ isAttachmentLoading(item) ? '加载中' : '无法预览' }}</span>
            </div>
            <div class="repair-preview-meta">
              <strong>{{ item.fileName }}</strong>
              <span>{{ formatFileSize(item.fileSize) }}</span>
            </div>
          </div>
        </div>
      </section>

      <section v-if="canHandleCurrentTask && actionableTask" class="detail-flat-section detail-task-action-section">
        <div class="detail-task-action-title">
          <strong class="detail-task-action-heading">审批处理</strong>
          <div class="detail-task-action-node">
            <span>当前节点</span>
            <strong>{{ actionableTask.nodeName }}</strong>
          </div>
        </div>
        <div class="detail-task-action-panel">
          <el-input
            v-model="taskActionComment"
            class="detail-task-action-input"
            type="textarea"
            :rows="3"
            maxlength="1000"
            show-word-limit
            placeholder="填写处理意见；拒绝时必须填写原因"
          />
          <p v-if="taskActionError" class="detail-task-action-error">{{ taskActionError }}</p>
          <div class="detail-task-action-buttons">
            <el-button
              v-if="canRejectTask"
              type="danger"
              plain
              :loading="actionSubmitting && pendingActionType === 'reject'"
              :disabled="actionSubmitting"
              @click="emitTaskAction('reject')"
            >
              拒绝
            </el-button>
            <el-button
              v-if="canApproveTask"
              type="primary"
              :loading="actionSubmitting && pendingActionType === 'approve'"
              :disabled="actionSubmitting"
              @click="emitTaskAction('approve')"
            >
              同意
            </el-button>
          </div>
        </div>
      </section>

      <section class="detail-flat-section detail-flow-section">
        <div class="detail-section-head">
          <div>
            <strong>流程运行图</strong>
            <span>蓝色为已走过，橙色为当前待办，灰色为尚未执行。</span>
          </div>
          <div class="flow-tools">
            <div class="flow-legend">
              <span><i class="is-done"></i>已完成</span>
              <span><i class="is-current"></i>当前</span>
              <span><i class="is-todo"></i>未执行</span>
            </div>
            <el-button size="small" @click="flowExpanded = !flowExpanded">
              {{ flowExpanded ? '收起视图' : '放大查看' }}
            </el-button>
          </div>
        </div>
        <div
          v-if="detailGraph.nodes.length"
          :class="['instance-flow-canvas', { 'is-expanded': flowExpanded, 'is-compact': isCompactFlowGraph }]"
          :style="flowCanvasStyle"
        >
          <svg :viewBox="detailGraph.viewBox" role="img" aria-label="流程实例流转图">
            <defs>
              <marker
                id="instance-arrow-default"
                markerWidth="18"
                markerHeight="14"
                refX="16"
                refY="7"
                orient="auto"
                markerUnits="userSpaceOnUse"
              >
                <path d="M 0 0 L 18 7 L 0 14 z" fill="#94a3b8" />
              </marker>
              <marker
                id="instance-arrow-active"
                markerWidth="18"
                markerHeight="14"
                refX="16"
                refY="7"
                orient="auto"
                markerUnits="userSpaceOnUse"
              >
                <path d="M 0 0 L 18 7 L 0 14 z" fill="#2563eb" />
              </marker>
              <marker
                id="instance-arrow-current"
                markerWidth="18"
                markerHeight="14"
                refX="16"
                refY="7"
                orient="auto"
                markerUnits="userSpaceOnUse"
              >
                <path d="M 0 0 L 18 7 L 0 14 z" fill="#d97706" />
              </marker>
            </defs>
            <path
              v-for="edge in detailGraph.edges.filter((item) => !isFlowAnimatedEdge(item))"
              :key="edge.id"
              :class="['flow-edge', `is-${edge.state}`]"
              :d="edgePath(edge)"
              :marker-end="edgeMarker(edge.state)"
            />
            <path
              v-for="edge in detailGraph.edges.filter(isFlowAnimatedEdge)"
              :key="`${edge.id}-flow`"
              :class="['flow-edge-flow', `is-${edge.state}`]"
              :d="edgePath(edge)"
              :marker-end="edgeMarker(edge.state)"
            />
            <text
              v-for="edge in detailGraph.edges.filter((item) => item.label)"
              :key="`${edge.id}-label`"
              :class="['flow-edge-label', `is-${edge.state}`]"
              :x="edgeLabelX(edge)"
              :y="edgeLabelY(edge)"
              text-anchor="middle"
            >
              {{ shortText(edge.label, 8) }}
            </text>
            <g
              v-for="node in detailGraph.nodes"
              :key="node.id"
              :class="['flow-node', `is-${node.state}`, `is-${node.nodeType.toLowerCase()}`]"
              :transform="`translate(${node.x}, ${node.y})`"
            >
              <circle v-if="node.nodeType === 'START' || node.nodeType === 'END'" class="flow-node-shape" r="32" />
              <polygon v-else-if="node.nodeType === 'CONDITION'" class="flow-node-shape" points="0,-52 52,0 0,52 -52,0" />
              <rect v-else class="flow-node-shape" x="-84" y="-32" width="168" height="64" rx="8" />
              <g
                :class="['flow-node-icon', `is-${nodeIconKind(node.nodeType)}`]"
                :transform="nodeIconTransform(node.nodeType)"
                aria-hidden="true"
              >
                <circle r="8.5" />
                <path v-if="nodeIconKind(node.nodeType) === 'play'" class="is-filled" d="M -2 -4 L 4 0 L -2 4 Z" />
                <g v-else-if="nodeIconKind(node.nodeType) === 'file'">
                  <path d="M -3.5 -5 H 1 L 4 -2 V 5 H -3.5 Z" />
                  <path d="M 1 -5 V -2 H 4" />
                  <path d="M -1.5 1 H 2" />
                </g>
                <g v-else-if="nodeIconKind(node.nodeType) === 'user'">
                  <circle cx="0" cy="-2.4" r="2.2" />
                  <path d="M -4.4 4.6 C -3.5 1.4, 3.5 1.4, 4.4 4.6" />
                </g>
                <g v-else-if="nodeIconKind(node.nodeType) === 'notify'">
                  <path d="M -4 1 C -4 -3, -2 -5, 1 -5 C 4 -5, 5 -3, 5 0 V 3 H -4 Z" />
                  <path d="M -1 4.5 C 1 5.5, 2.5 4.5, 2.5 4.5" />
                </g>
                <g v-else-if="nodeIconKind(node.nodeType) === 'branch'">
                  <circle class="is-filled" cx="-4" cy="0" r="1.2" />
                  <circle class="is-filled" cx="4" cy="-4" r="1.2" />
                  <circle class="is-filled" cx="4" cy="4" r="1.2" />
                  <path d="M -2.8 0 H 1 L 3 -3" />
                  <path d="M 1 0 L 3 3" />
                </g>
                <rect v-else class="is-filled" x="-3.5" y="-3.5" width="7" height="7" rx="1.4" />
              </g>
              <text class="flow-node-label" text-anchor="middle" dominant-baseline="middle">{{ shortText(node.label, 9) }}</text>
              <text v-if="node.state === 'current'" class="flow-node-status" x="0" y="50" text-anchor="middle">当前</text>
            </g>
          </svg>
        </div>
        <div v-else class="detail-empty-block">该流程实例没有可展示的流程图快照。</div>
      </section>

      <section class="detail-flat-section detail-dynamics-section">
        <div class="detail-section-head">
          <div>
            <strong>流程动态</strong>
            <span>按处理节点展示状态、处理人、时间和意见，便于审批跟踪。</span>
          </div>
        </div>
        <div v-if="workflowDynamics.length" class="workflow-dynamics">
          <div class="dynamic-table-head" aria-hidden="true">
            <span>状态</span>
            <span>节点</span>
            <span>处理人</span>
            <span>到达时间</span>
            <span>处理时间</span>
            <span>耗时</span>
            <span>处理意见</span>
          </div>
          <div v-for="item in workflowDynamics" :key="item.id" :class="['dynamic-row', `is-${item.tone}`]">
            <div class="dynamic-cell dynamic-state-cell">
              <span class="dynamic-mobile-label">状态</span>
              <span :class="['dynamic-status-dot', `is-${item.tone}`]"></span>
              <div>
                <strong>{{ item.statusText }}</strong>
                <span>{{ item.actionText }}</span>
              </div>
            </div>
            <div class="dynamic-cell dynamic-node-cell">
              <span class="dynamic-mobile-label">节点</span>
              <strong>{{ item.nodeName }}</strong>
            </div>
            <div class="dynamic-cell">
              <span class="dynamic-mobile-label">处理人</span>
              <span>{{ item.actor }}</span>
            </div>
            <div class="dynamic-cell">
              <span class="dynamic-mobile-label">到达时间</span>
              <span>{{ item.arrivedAtText }}</span>
            </div>
            <div class="dynamic-cell">
              <span class="dynamic-mobile-label">处理时间</span>
              <span>{{ item.processedAtText }}</span>
            </div>
            <div class="dynamic-cell">
              <span class="dynamic-mobile-label">耗时</span>
              <span>{{ item.durationText }}</span>
            </div>
            <div class="dynamic-cell dynamic-comment-cell">
              <span class="dynamic-mobile-label">处理意见</span>
              <span>{{ item.comment || '-' }}</span>
            </div>
          </div>
        </div>
        <div v-else class="detail-empty-block">暂无流程动态。</div>
      </section>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { Picture } from '@element-plus/icons-vue'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { downloadRepairAttachment } from '@/features/workflow/repairApi'
import { buildBusinessFields } from '@/features/workflow/businessFields'
import { buildWorkflowParticipantMap, workflowParticipantProfile } from '@/features/workflow/participants'
import { repairAttachmentsForDetail } from '@/features/workflow/repairAttachmentPreview'
import { buildRuntimeGraph, edgeLabelX, edgeLabelY, edgePath, type GraphEdgeState } from '@/features/workflow/runtimeGraph'
import type { RepairAttachment, WorkflowAssigneeOption, WorkflowInstanceDetail, WorkflowTask } from '@/features/workflow/types'
import { sortWorkflowDynamicsForDisplay } from '@/features/workflow/workflowDynamics'
import WorkflowApplicationPaper from './WorkflowApplicationPaper.vue'

const props = withDefaults(defineProps<{
  modelValue: boolean
  detail?: WorkflowInstanceDetail
  assignees?: WorkflowAssigneeOption[]
  repairAttachments?: RepairAttachment[]
  currentUserId?: WorkflowAssigneeOption['value']
  canApproveTask?: boolean
  canRejectTask?: boolean
  actionSubmitting?: boolean
  title?: string
}>(), {
  assignees: () => [],
  repairAttachments: () => [],
  canApproveTask: false,
  canRejectTask: false,
  actionSubmitting: false,
  title: '流程详情'
})

type DetailTaskActionType = 'approve' | 'reject'
type ApplicationStatusTone = 'running' | 'approved' | 'rejected' | 'ended' | 'draft'

interface WorkflowDynamicItem {
  id: string
  nodeName: string
  actor: string
  actionText: string
  statusText: string
  tone: 'done' | 'current' | 'danger' | 'todo'
  arrivedAtText: string
  processedAtText: string
  durationText: string
  comment: string
  sequenceTime: number
  sequenceRank: number
}

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'task-action', payload: { type: DetailTaskActionType; task: WorkflowTask; comment: string }): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

const assigneeMap = computed(() => buildWorkflowParticipantMap(props.assignees, props.detail?.participants))
const repairAttachments = computed(() => repairAttachmentsForDetail(props.detail, props.repairAttachments))
const businessVariables = computed(() => {
  const variables = props.detail?.variables || props.detail?.instance.variables
  if (isPlainRecord(variables)) return variables
  return parseVariablesJson(props.detail?.instance.variablesJson)
})
const businessFields = computed(() => buildBusinessFields(props.detail?.instance.businessType, businessVariables.value))
const businessSheetTitle = computed(() => {
  const title = props.detail?.instance.title
  if (title) return title
  return `${businessTypeText(props.detail?.instance.businessType)}申请单`
})
const businessSheetNo = computed(() => businessValueText(businessVariables.value.requestNo || props.detail?.instance.businessId))
const businessApplicantName = computed(() => {
  const applicantName = businessValueText(businessVariables.value.applicantName)
  if (applicantName !== '-') return applicantName
  return userProfile(props.detail?.instance.initiatorId).name
})
const businessSheetTone = computed(() => instanceStatusTone(props.detail?.instance.status))
const businessPaperMeta = computed(() => [
  { key: 'requestNo', label: '申请单号', value: businessSheetNo.value },
  { key: 'applicant', label: '申请人', value: businessApplicantName.value },
  { key: 'date', label: '申请日期', value: formatDate(props.detail?.instance.startedAt) },
  { key: 'status', label: '流程状态', value: instanceStatusText(props.detail?.instance.status), tone: businessSheetTone.value }
])
const businessPaperApprovals = computed(() => workflowDynamics.value.slice(0, 4).map((item) => ({
  id: item.id,
  nodeName: item.nodeName,
  actor: item.actor,
  statusText: item.statusText,
  timeText: item.processedAtText,
  tone: item.tone
})))
const repairAttachmentKey = computed(() => repairAttachments.value.map((item) => `${item.fileId}:${item.url}`).join('|'))
const attachmentObjectUrls = ref<Record<string, string>>({})
const attachmentLoadingMap = ref<Record<string, boolean>>({})
const flowExpanded = ref(false)
const taskActionComment = ref('')
const taskActionError = ref('')
const pendingActionType = ref<DetailTaskActionType | ''>('')
const repairPreviewUrls = computed(() => repairAttachments.value.map(attachmentPreviewUrl).filter(Boolean))
const detailGraph = computed(() => buildRuntimeGraph(props.detail))
const flowViewBoxMetrics = computed(() => {
  const [, , width, height] = detailGraph.value.viewBox.split(/\s+/).map(Number)
  return {
    width: Number.isFinite(width) && width > 0 ? width : 1160,
    height: Number.isFinite(height) && height > 0 ? height : 320
  }
})
const isCompactFlowGraph = computed(() => flowViewBoxMetrics.value.height <= 240)
const flowCanvasHeight = computed(() => {
  if (flowExpanded.value) return '520px'
  if (flowViewBoxMetrics.value.height <= 220) return '240px'
  if (flowViewBoxMetrics.value.height <= 300) return '300px'
  return '380px'
})
const flowCanvasStyle = computed(() => ({
  '--runtime-flow-height': flowCanvasHeight.value
}))
const actionableTask = computed(() => {
  if (!props.detail || !props.currentUserId) return undefined
  return props.detail.tasks.find((task) => task.status === 'PENDING' && sameEntityId(task.assigneeId, props.currentUserId))
})
const canHandleCurrentTask = computed(() => Boolean(actionableTask.value && (props.canApproveTask || props.canRejectTask)))
const workflowDynamics = computed<WorkflowDynamicItem[]>(() => {
  if (!props.detail) return []
  const submitEvents = props.detail.events
    .filter((event) => event.action === 'SUBMIT')
    .map((event) => {
      const actor = userProfile(event.operatorId).name
      return {
        id: `event-${event.id}`,
        nodeName: '流程开始',
        actor,
        actionText: actionText(event.action),
        statusText: '已完成',
        tone: 'done' as const,
        arrivedAtText: formatTime(event.createdAt),
        processedAtText: formatTime(event.createdAt),
        durationText: '即时',
        comment: event.comment || eventActionDescription(event.action),
        sequenceTime: timeValue(event.createdAt),
        sequenceRank: 0
      }
    })
  const taskDynamics = props.detail.tasks.map((task) => {
    const actor = userProfile(task.assigneeId).name
    return {
      id: `task-${task.id}`,
      nodeName: task.nodeName,
      actor,
      actionText: taskActionText(task.status),
      statusText: taskStatusText(task.status),
      tone: workflowDynamicTone(task.status),
      arrivedAtText: formatTime(task.startedAt),
      processedAtText: formatTime(task.finishedAt),
      durationText: durationText(task.startedAt, task.finishedAt),
      comment: task.approveComment || '',
      sequenceTime: timeValue(task.startedAt || task.finishedAt),
      sequenceRank: workflowDynamicSequenceRank(task.status)
    }
  })
  return sortWorkflowDynamicsForDisplay([...submitEvents, ...taskDynamics])
})

watch([visible, repairAttachmentKey], ([isVisible]) => {
  if (!isVisible) {
    flowExpanded.value = false
    clearAttachmentObjectUrls()
    resetTaskActionForm()
    return
  }
  void loadRepairAttachmentPreviews()
}, { immediate: true })

watch(() => actionableTask.value?.id, resetTaskActionForm)

watch(() => props.actionSubmitting, (submitting) => {
  if (!submitting) pendingActionType.value = ''
})

onBeforeUnmount(clearAttachmentObjectUrls)

function emitTaskAction(type: DetailTaskActionType) {
  const task = actionableTask.value
  if (!task) return
  if (type === 'approve' && !props.canApproveTask) return
  if (type === 'reject' && !props.canRejectTask) return
  const comment = taskActionComment.value.trim()
  if (type === 'reject' && !comment) {
    taskActionError.value = '拒绝时必须填写处理意见'
    return
  }
  taskActionError.value = ''
  pendingActionType.value = type
  emit('task-action', { type, task, comment })
}

function resetTaskActionForm() {
  taskActionComment.value = ''
  taskActionError.value = ''
  pendingActionType.value = ''
}

function sameEntityId(left?: WorkflowAssigneeOption['value'], right?: WorkflowAssigneeOption['value']) {
  return left !== undefined && right !== undefined && String(left) === String(right)
}

function edgeMarker(state: GraphEdgeState) {
  if (state === 'done') return 'url(#instance-arrow-active)'
  if (state === 'current') return 'url(#instance-arrow-current)'
  return 'url(#instance-arrow-default)'
}

function isFlowAnimatedEdge(edge: { state: GraphEdgeState }) {
  return edge.state === 'done' || edge.state === 'current'
}

function nodeIconKind(nodeType: string) {
  const map: Record<string, string> = {
    START: 'play',
    SUBMIT: 'file',
    APPROVAL: 'user',
    CC: 'notify',
    CONDITION: 'branch',
    END: 'stop'
  }
  return map[nodeType] || 'user'
}

function nodeIconTransform(nodeType: string) {
  if (nodeType === 'CONDITION') return 'translate(-34, -34)'
  if (nodeType === 'START' || nodeType === 'END') return 'translate(-17, -17)'
  return 'translate(-69, -17)'
}

function userProfile(userId?: WorkflowAssigneeOption['value']) {
  return workflowParticipantProfile(userId, assigneeMap.value)
}

function parseVariablesJson(value?: string) {
  if (!value) return {}
  try {
    const parsed = JSON.parse(value)
    return isPlainRecord(parsed) ? parsed : {}
  } catch {
    return {}
  }
}

function isPlainRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function formatTime(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}

function formatDate(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 10)
}

function formatFileSize(value?: number) {
  if (!value || value <= 0) return '-'
  if (value < 1024 * 1024) return `${Math.ceil(value / 1024)} KB`
  return `${(value / 1024 / 1024).toFixed(1)} MB`
}

function timeValue(value?: string) {
  return value ? new Date(value.replace(' ', 'T')).getTime() || 0 : 0
}

function durationText(startedAt?: string, finishedAt?: string) {
  const start = timeValue(startedAt)
  if (!start) return '-'
  if (!finishedAt) return '处理中'
  const end = timeValue(finishedAt)
  if (!end || end < start) return '-'
  const seconds = Math.max(1, Math.floor((end - start) / 1000))
  if (seconds < 60) return `${seconds} 秒`
  const minutes = Math.floor(seconds / 60)
  const remainSeconds = seconds % 60
  if (minutes < 60) return remainSeconds ? `${minutes} 分 ${remainSeconds} 秒` : `${minutes} 分钟`
  const hours = Math.floor(minutes / 60)
  const remainMinutes = minutes % 60
  return remainMinutes ? `${hours} 小时 ${remainMinutes} 分` : `${hours} 小时`
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

function workflowDynamicTone(status: string): WorkflowDynamicItem['tone'] {
  if (status === 'PENDING') return 'current'
  if (status === 'APPROVED') return 'done'
  if (status === 'REJECTED' || status === 'CANCELED') return 'danger'
  return 'todo'
}

function workflowDynamicSequenceRank(status: string) {
  if (status === 'PENDING') return 2
  return 1
}

function taskActionText(status: string) {
  const textMap: Record<string, string> = {
    PENDING: '等待处理',
    APPROVED: '审批通过',
    REJECTED: '审批驳回',
    TRANSFERRED: '已转办',
    DELEGATED: '已委派',
    CANCELED: '已取消'
  }
  return textMap[status] || status
}

function actionText(action: string) {
  const textMap: Record<string, string> = {
    SUBMIT: '提交',
    APPROVE: '同意',
    REJECT: '驳回',
    TRANSFER: '转办',
    DELEGATE: '委派',
    RETURN: '退回',
    ADD_SIGN: '加签',
    REMOVE_SIGN: '减签',
    REMIND: '催办',
    REVOKE: '撤回',
    TERMINATE: '终止'
  }
  return textMap[action] || action
}

function eventActionDescription(action: string) {
  const textMap: Record<string, string> = {
    SUBMIT: '发起人提交业务申请',
    APPROVE: '审批人处理通过',
    REJECT: '审批人驳回并结束流程',
    TRANSFER: '当前任务转交给其他处理人',
    DELEGATE: '当前任务委派给其他处理人协助',
    RETURN: '流程退回到前序节点',
    ADD_SIGN: '当前节点追加处理人',
    REMOVE_SIGN: '当前节点移除尚未处理的加签人',
    REMIND: '发起催办提醒',
    REVOKE: '发起人撤回流程',
    TERMINATE: '管理员终止流程'
  }
  return textMap[action] || '流程状态发生变化'
}

function businessTypeText(type?: string) {
  const textMap: Record<string, string> = {
    leave: '请假',
    purchase: '采购',
    repair: '报修',
    expense: '报销'
  }
  return textMap[type || ''] || '业务'
}

function instanceStatusText(status?: string) {
  const textMap: Record<string, string> = {
    RUNNING: '审批中',
    APPROVED: '已通过',
    REJECTED: '已驳回',
    REVOKED: '已撤回',
    TERMINATED: '已终止'
  }
  return textMap[status || ''] || status || '-'
}

function instanceStatusTone(status?: string): ApplicationStatusTone {
  if (status === 'RUNNING') return 'running'
  if (status === 'APPROVED') return 'approved'
  if (status === 'REJECTED') return 'rejected'
  return 'ended'
}

function businessValueText(value: unknown) {
  if (value === undefined || value === null || value === '') return '-'
  return String(value)
}

function shortText(value: string, maxLength: number) {
  if (!value) return '-'
  return value.length > maxLength ? `${value.slice(0, maxLength)}...` : value
}

async function loadRepairAttachmentPreviews() {
  const visibleIds = new Set(repairAttachments.value.map((item) => String(item.fileId)))
  Object.entries(attachmentObjectUrls.value).forEach(([id, objectUrl]) => {
    if (!visibleIds.has(id)) {
      URL.revokeObjectURL(objectUrl)
      const nextUrls = { ...attachmentObjectUrls.value }
      delete nextUrls[id]
      attachmentObjectUrls.value = nextUrls
    }
  })
  await Promise.all(repairAttachments.value.map(loadRepairAttachmentPreview))
}

async function loadRepairAttachmentPreview(item: RepairAttachment) {
  const id = String(item.fileId)
  if (attachmentObjectUrls.value[id] || attachmentLoadingMap.value[id]) return
  attachmentLoadingMap.value = { ...attachmentLoadingMap.value, [id]: true }
  try {
    const blob = await downloadRepairAttachment(item.fileId)
    if (!visible.value || !repairAttachments.value.some((attachment) => String(attachment.fileId) === id)) return
    const currentUrl = attachmentObjectUrls.value[id]
    if (currentUrl) URL.revokeObjectURL(currentUrl)
    attachmentObjectUrls.value = { ...attachmentObjectUrls.value, [id]: URL.createObjectURL(blob) }
  } catch {
    attachmentObjectUrls.value = { ...attachmentObjectUrls.value, [id]: '' }
  } finally {
    attachmentLoadingMap.value = { ...attachmentLoadingMap.value, [id]: false }
  }
}

function attachmentPreviewUrl(item: RepairAttachment) {
  return attachmentObjectUrls.value[String(item.fileId)] || ''
}

function repairPreviewIndex(item: RepairAttachment) {
  return Math.max(repairPreviewUrls.value.indexOf(attachmentPreviewUrl(item)), 0)
}

function isAttachmentLoading(item: RepairAttachment) {
  return Boolean(attachmentLoadingMap.value[String(item.fileId)])
}

function clearAttachmentObjectUrls() {
  Object.values(attachmentObjectUrls.value).forEach((objectUrl) => {
    if (objectUrl) URL.revokeObjectURL(objectUrl)
  })
  attachmentObjectUrls.value = {}
  attachmentLoadingMap.value = {}
}

</script>

<style scoped>
.detail-panel {
  display: flex;
  flex-direction: column;
  gap: 0;
  color: #172033;
}

:global(.workflow-instance-detail-drawer .el-drawer__body) {
  padding: 0 24px 24px;
  background: #fff;
}

.detail-flat-section {
  border-bottom: 1px solid #e5eaf3;
  padding: 20px 0;
  background: #fff;
}

.detail-flat-section:last-child {
  border-bottom: 0;
}

.repair-preview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(176px, 224px));
  gap: 12px;
  align-items: start;
}

.repair-preview-item {
  width: 100%;
  max-width: 224px;
  overflow: hidden;
  border: 1px solid #e1e8f2;
  border-radius: 8px;
  background: #fff;
}

.repair-preview-item :deep(.el-image) {
  display: block;
  width: 100%;
  aspect-ratio: 4 / 3;
  background: #f8fafc;
}

.repair-preview-item :deep(.el-image__inner) {
  width: 100%;
  height: 100%;
}

.repair-preview-placeholder {
  display: grid;
  width: 100%;
  aspect-ratio: 4 / 3;
  place-items: center;
  align-content: center;
  gap: 8px;
  background: #f8fafc;
  color: #66758f;
}

.repair-preview-placeholder .el-icon {
  font-size: 28px;
}

.repair-preview-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  border-top: 1px solid #edf2f7;
  padding: 10px 12px;
}

.repair-preview-meta strong,
.repair-preview-meta span {
  min-width: 0;
  font-size: 12px;
}

.repair-preview-meta strong {
  color: #172033;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.repair-preview-meta span {
  flex: none;
  color: #66758f;
}

.detail-task-action-section {
  background: #fff;
}

.detail-task-action-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid #e5eaf3;
}

.detail-task-action-heading {
  color: #172033;
  font-size: 15px;
  font-weight: 700;
  line-height: 1.4;
}

.detail-task-action-node {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #66758f;
  font-size: 13px;
  line-height: 1.4;
  white-space: nowrap;
}

.detail-task-action-node span {
  color: #8a98ad;
}

.detail-task-action-node strong {
  color: #33415c;
  font-weight: 600;
}

.detail-task-action-panel {
  padding-top: 0;
}

.detail-task-action-input {
  width: 100%;
}

.detail-task-action-buttons {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 10px;
}

.detail-task-action-error {
  margin: 6px 0 0;
  color: #b91c1c;
  font-size: 12px;
}

.detail-section-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.detail-section-head.is-compact {
  margin-bottom: 10px;
}

.detail-section-head strong,
.detail-section-head span {
  display: block;
}

.detail-section-head strong {
  color: #172033;
  font-size: 15px;
}

.detail-section-head span {
  margin-top: 4px;
  color: #66758f;
  font-size: 13px;
  line-height: 1.5;
}

.flow-tools {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.flow-legend {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
  color: #66758f;
  font-size: 12px;
}

.flow-legend span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin: 0;
}

.flow-legend i {
  width: 10px;
  height: 10px;
  border-radius: 999px;
}

.flow-legend .is-done {
  background: #2563eb;
}

.flow-legend .is-current {
  background: #f59e0b;
}

.flow-legend .is-todo {
  background: #94a3b8;
}

.instance-flow-canvas {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fbfdff;
  overflow: auto;
  scrollbar-width: thin;
  scrollbar-color: #cbd5e1 transparent;
}

.instance-flow-canvas::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.instance-flow-canvas::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: #cbd5e1;
}

.instance-flow-canvas svg {
  width: 100%;
  min-width: 920px;
  height: var(--runtime-flow-height, 380px);
  display: block;
}

.instance-flow-canvas.is-compact svg {
  min-height: 220px;
}

.instance-flow-canvas.is-expanded svg {
  min-width: 1280px;
  height: 520px;
}

.flow-edge {
  fill: none;
  stroke: #94a3b8;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-dasharray: 6 7;
}

.flow-edge.is-done {
  stroke: #2563eb;
  stroke-dasharray: none;
}

.flow-edge.is-current {
  stroke: #d97706;
  stroke-width: 2.1;
  stroke-dasharray: none;
}

.flow-edge-flow {
  fill: none;
  stroke: #60a5fa;
  stroke-width: 2.4;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-dasharray: 12 18;
  stroke-dashoffset: 0;
  opacity: 0.9;
  pointer-events: none;
  animation: workflow-edge-flow 1.1s linear infinite;
}

.flow-edge-flow.is-current {
  stroke: #f59e0b;
  stroke-width: 2.6;
  animation-duration: 0.9s;
}

.flow-edge-label {
  fill: #64748b;
  font-size: 12px;
  font-weight: 700;
  paint-order: stroke;
  stroke: #fff;
  stroke-width: 5px;
  stroke-linejoin: round;
}

.flow-edge-label.is-current {
  fill: #b45309;
}

.flow-edge-label.is-done {
  fill: #1d4ed8;
}

.flow-node-shape {
  fill: #f8fafc;
  stroke: #94a3b8;
  stroke-width: 2;
}

.flow-node.is-done .flow-node-shape {
  fill: #eff6ff;
  stroke: #2563eb;
  stroke-dasharray: 10 6;
  animation: workflow-node-flow 1.45s linear infinite;
}

.flow-node.is-current .flow-node-shape {
  fill: #fffbeb;
  stroke: #d97706;
  stroke-width: 2.5;
  stroke-dasharray: 10 6;
  animation: workflow-node-flow 1s linear infinite;
}

.flow-node.is-cc.is-done .flow-node-shape {
  fill: #f0fdf4;
  stroke: #16a34a;
  stroke-dasharray: 6 4;
}

.flow-node-icon {
  pointer-events: none;
}

.flow-node-icon > circle {
  fill: #fff;
  stroke: #cbd5e1;
  stroke-width: 1.2;
}

.flow-node-icon path,
.flow-node-icon rect,
.flow-node-icon g > circle {
  fill: none;
  stroke: #475569;
  stroke-width: 1.5;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.flow-node-icon .is-filled {
  fill: currentColor;
  stroke: none;
}

.flow-node-icon.is-play,
.flow-node-icon.is-user {
  color: #2563eb;
}

.flow-node-icon.is-play > circle,
.flow-node-icon.is-user > circle {
  stroke: #93c5fd;
}

.flow-node-icon.is-notify {
  color: #16a34a;
}

.flow-node-icon.is-notify > circle {
  stroke: #86efac;
}

.flow-node-icon.is-branch {
  color: #d97706;
}

.flow-node-icon.is-branch > circle {
  stroke: #fdba74;
}

.flow-node-icon.is-file,
.flow-node-icon.is-stop {
  color: #475569;
}

.flow-node-icon.is-file > circle,
.flow-node-icon.is-stop > circle {
  stroke: #cbd5e1;
}

.flow-node-icon.is-play path,
.flow-node-icon.is-user path,
.flow-node-icon.is-notify path,
.flow-node-icon.is-branch path,
.flow-node-icon.is-file path {
  stroke: currentColor;
}

.flow-node-icon.is-user g > circle {
  stroke: currentColor;
}

.flow-node-label {
  fill: #334155;
  font-size: 13px;
  font-weight: 700;
}

.flow-node.is-done .flow-node-label {
  fill: #1e3a8a;
}

.flow-node.is-current .flow-node-label {
  fill: #92400e;
}

.flow-node-status {
  fill: #b45309;
  font-size: 12px;
  font-weight: 700;
}

@keyframes workflow-edge-flow {
  to {
    stroke-dashoffset: -30;
  }
}

@keyframes workflow-node-flow {
  to {
    stroke-dashoffset: -16;
  }
}

@media (prefers-reduced-motion: reduce) {
  .flow-edge-flow,
  .flow-node.is-done .flow-node-shape,
  .flow-node.is-current .flow-node-shape {
    animation: none;
  }
}

.workflow-dynamics {
  border-top: 1px solid #e2e8f0;
  border-bottom: 1px solid #e2e8f0;
  background: #fff;
  overflow-x: auto;
  scrollbar-width: thin;
  scrollbar-color: #cbd5e1 transparent;
}

.workflow-dynamics::-webkit-scrollbar {
  height: 8px;
}

.workflow-dynamics::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: #cbd5e1;
}

.dynamic-table-head,
.dynamic-row {
  display: grid;
  grid-template-columns:
    minmax(112px, 0.9fr)
    minmax(142px, 1.1fr)
    minmax(116px, 0.8fr)
    minmax(142px, 1fr)
    minmax(142px, 1fr)
    minmax(84px, 0.65fr)
    minmax(180px, 1.4fr);
  min-width: 960px;
}

.dynamic-table-head {
  min-height: 40px;
  align-items: center;
  border-bottom: 1px solid #e5eaf3;
  background: #f8fafc;
  color: #66758f;
  font-size: 12px;
  font-weight: 600;
}

.dynamic-table-head span,
.dynamic-cell {
  min-width: 0;
  padding: 10px 12px;
}

.dynamic-row {
  min-height: 58px;
  border-bottom: 1px solid #edf2f7;
  background: #fff;
}

.dynamic-row:last-child {
  border-bottom: 0;
}

.dynamic-row.is-current {
  background: #fffdf6;
}

.dynamic-row.is-danger {
  background: #fffafa;
}

.dynamic-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #475569;
  font-size: 13px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.dynamic-cell strong,
.dynamic-cell span {
  min-width: 0;
}

.dynamic-cell strong {
  color: #172033;
  font-weight: 600;
}

.dynamic-state-cell > div {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.dynamic-state-cell strong {
  font-size: 13px;
  line-height: 1.3;
}

.dynamic-state-cell div span {
  color: #66758f;
  font-size: 12px;
}

.dynamic-node-cell strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dynamic-status-dot {
  width: 8px;
  height: 8px;
  flex: none;
  border-radius: 999px;
  background: #cbd5e1;
}

.dynamic-status-dot.is-current {
  background: #d97706;
}

.dynamic-status-dot.is-done {
  background: #2563eb;
}

.dynamic-status-dot.is-danger {
  background: #dc2626;
}

.dynamic-mobile-label {
  display: none;
  color: #8a99af;
  font-size: 12px;
  font-weight: 600;
}

.dynamic-comment-cell span:last-child {
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.detail-empty-block {
  display: grid;
  min-height: 120px;
  place-items: center;
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
  background: #f8fafc;
  color: #66758f;
}

@media (max-width: 900px) {
  .repair-preview-grid {
    grid-template-columns: repeat(auto-fill, minmax(144px, 1fr));
  }

  .repair-preview-item {
    max-width: none;
  }

  .detail-section-head {
    flex-direction: column;
  }

  .detail-task-action-buttons {
    justify-content: flex-start;
  }

  .detail-task-action-title {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .flow-tools,
  .flow-legend {
    justify-content: flex-start;
  }

  .dynamic-table-head {
    display: none;
  }

  .dynamic-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    min-width: 0;
    row-gap: 0;
    padding: 8px 0;
  }

  .dynamic-cell {
    align-items: flex-start;
    flex-direction: column;
    gap: 4px;
    padding: 8px 12px;
  }

  .dynamic-state-cell {
    flex-direction: row;
    align-items: center;
  }

  .dynamic-state-cell .dynamic-mobile-label {
    display: none;
  }

  .dynamic-mobile-label {
    display: block;
  }

  .dynamic-node-cell,
  .dynamic-comment-cell {
    grid-column: 1 / -1;
  }

  .dynamic-node-cell strong {
    white-space: normal;
  }
}

@media (max-width: 560px) {
  .dynamic-row {
    grid-template-columns: 1fr;
  }
}
</style>

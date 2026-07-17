import type { EntityId } from '@/features/system/types'

export interface WorkflowDefinition {
  id: EntityId
  processKey: string
  processName: string
  currentVersion: number
  status: 'DRAFT' | 'ENABLED' | 'DISABLED' | string
  remark?: string
  graphJson?: string
  updatedAt?: string
}

export interface WorkflowTask {
  id: EntityId
  instanceId: EntityId
  instanceTitle?: string
  businessType?: string
  businessId?: string
  instanceStatus?: string
  instanceInitiatorId?: EntityId
  nodeKey: string
  nodeName: string
  assigneeId?: EntityId
  assigneeDeptId?: EntityId
  assignmentRuleType?: string
  assignmentRuleName?: string
  assignmentResolvePath?: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'TRANSFERRED' | 'DELEGATED' | 'CANCELED' | string
  approveComment?: string
  startedAt?: string
  finishedAt?: string
  createdBy?: EntityId
}

export interface WorkflowInstance {
  id: EntityId
  definitionId: EntityId
  versionId: EntityId
  processKey: string
  businessType: string
  businessId: string
  title: string
  initiatorId: EntityId
  currentNodeKey?: string
  status: 'RUNNING' | 'APPROVED' | 'REJECTED' | 'REVOKED' | 'TERMINATED' | string
  variablesJson?: string
  variables?: Record<string, unknown>
  definitionSnapshotJson?: string
  startedAt?: string
  endedAt?: string
}

export interface WorkflowEvent {
  id: EntityId
  instanceId: EntityId
  taskId?: EntityId
  operatorId?: EntityId
  action: string
  fromNodeKey?: string
  toNodeKey?: string
  targetUserId?: EntityId
  comment?: string
  createdAt?: string
}

export interface WorkflowCc {
  id: EntityId
  instanceId: EntityId
  instanceTitle?: string
  businessType?: string
  businessId?: string
  instanceStatus?: string
  nodeKey?: string
  nodeName?: string
  receiverId: EntityId
  readStatus: 0 | 1 | number
  readAt?: string
  createdAt?: string
  historic?: boolean
}

export interface LeaveRequest {
  id: EntityId
  requestNo: string
  applicantId: EntityId
  applicantName?: string
  leaveType: string
  startTime: string
  endTime: string
  days: number | string
  reason: string
  status: 'DRAFT' | 'APPROVING' | 'APPROVED' | 'REJECTED' | 'REVOKED' | 'TERMINATED' | string
  workflowStatus?: string
  workflowInstanceId?: EntityId
  createdAt?: string
}

export interface LeaveApplyPayload {
  leaveType: string
  startTime: string
  endTime: string
  days: number
  reason: string
}

export interface PurchaseRequest {
  id: EntityId
  requestNo: string
  applicantId: EntityId
  applicantName?: string
  itemName: string
  category: string
  quantity: number
  estimatedAmount: number | string
  requiredDate: string
  reason: string
  status: 'DRAFT' | 'APPROVING' | 'APPROVED' | 'REJECTED' | 'REVOKED' | 'TERMINATED' | string
  workflowStatus?: string
  workflowInstanceId?: EntityId
  createdAt?: string
}

export interface PurchaseApplyPayload {
  itemName: string
  category: string
  quantity: number
  estimatedAmount: number
  requiredDate: string
  reason: string
}

export interface RepairRequest {
  id: EntityId
  requestNo: string
  applicantId: EntityId
  applicantName?: string
  repairType: string
  assetName: string
  urgency: string
  faultTime: string
  location: string
  description: string
  attachments?: RepairAttachment[]
  status: 'DRAFT' | 'APPROVING' | 'APPROVED' | 'REJECTED' | 'REVOKED' | 'TERMINATED' | string
  workflowStatus?: string
  workflowInstanceId?: EntityId
  createdAt?: string
}

export interface RepairApplyPayload {
  repairType: string
  assetName: string
  urgency: string
  faultTime: string
  location: string
  description: string
  attachments?: RepairAttachment[]
}

export interface RepairAttachment {
  fileId: EntityId
  fileName: string
  contentType?: string
  fileSize?: number
  url: string
}

export interface WorkflowAssigneeOption {
  name: string
  value: EntityId
  userName?: string
  avatar?: string
}

export interface WorkflowInstanceDetail {
  instance: WorkflowInstance
  definition?: WorkflowDefinition
  version?: {
    id: EntityId
    definitionId: EntityId
    version: number
    graphJson?: string
    status?: string
  }
  graphJson?: string
  variables?: Record<string, unknown>
  participants?: WorkflowAssigneeOption[]
  tasks: WorkflowTask[]
  events: WorkflowEvent[]
  ccList: WorkflowCc[]
}

export interface WorkflowGraph {
  nodes: Array<Record<string, unknown>>
  edges: Array<Record<string, unknown>>
}

export interface StartWorkflowPayload {
  definitionId?: EntityId
  processKey?: string
  businessType: string
  businessId: string
  title: string
  assigneeId?: EntityId
  ccUserIds?: EntityId[]
  variables?: Record<string, unknown>
  comment?: string
}

export interface WorkflowTaskActionPayload {
  comment?: string
  nextAssigneeId?: EntityId
  targetUserId?: EntityId
  returnNodeKey?: string
  returnAssigneeId?: EntityId
  ccUserIds?: EntityId[]
  addSignUserIds?: EntityId[]
  variables?: Record<string, unknown>
}

export interface WorkflowTaskCenterSummary {
  pendingTotal: number
  doneTotal: number
  startedTotal: number
  ccTotal: number
}

export interface WorkflowInstanceActionPayload {
  comment?: string
}

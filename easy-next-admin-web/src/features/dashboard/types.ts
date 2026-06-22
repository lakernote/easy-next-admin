import type { EntityId } from '@/features/system/types'

export interface DashboardWorkflowMetric {
  key: 'pending' | 'started' | 'cc' | 'applications'
  title: string
  value: string
  hint: string
  tone: 'primary' | 'success' | 'warning' | 'info'
  path: string
  permission: string
}

export interface DashboardWorkflowTaskBrief {
  id: EntityId
  instanceId: EntityId
  title: string
  nodeName: string
  businessType: string
  businessId: string
  status: string
  startedAt?: string
}

export interface DashboardWorkflowInstanceBrief {
  id: EntityId
  title: string
  businessType: string
  businessId: string
  status: string
  startedAt?: string
}

export interface DashboardWorkflowCcBrief {
  id: EntityId
  instanceId: EntityId
  title: string
  nodeKey?: string
  nodeName?: string
  readStatus: number
  createdAt?: string
}

export interface DashboardWorkflowOverview {
  metrics: DashboardWorkflowMetric[]
  pendingTasks: DashboardWorkflowTaskBrief[]
  startedInstances: DashboardWorkflowInstanceBrief[]
  ccItems: DashboardWorkflowCcBrief[]
}

export interface DashboardApplicationEntry {
  title: string
  description: string
  path: string
  permission: string
  icon: string
}

export interface DashboardOverview {
  workflow: DashboardWorkflowOverview
  applications: DashboardApplicationEntry[]
}

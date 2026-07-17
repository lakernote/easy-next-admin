import type { PageQuery } from '@/api/types'
import type { WorkflowInstanceScope } from './instanceApi'
import { DONE_TASK_STATUSES } from './taskFilters'
import type { WorkflowCc, WorkflowInstance, WorkflowTask } from './types'
import type { WorkflowTaskCenterTab } from './taskCenterTabs'

export interface WorkflowTaskCenterPageState {
  page: number
  pageSize: number
}

export interface WorkflowTaskCenterPages {
  pending: WorkflowTaskCenterPageState
  done: WorkflowTaskCenterPageState
  started: WorkflowTaskCenterPageState
  cc: WorkflowTaskCenterPageState
}

export interface WorkflowTaskCenterApi {
  pageWorkflowTasks(query: PageQuery & { status?: string; statuses?: readonly string[]; mine?: boolean }): Promise<{ list: WorkflowTask[]; total: number }>
  pageWorkflowInstances(query: PageQuery & { mine?: boolean; scope: WorkflowInstanceScope }): Promise<{ list: WorkflowInstance[]; total: number }>
  pageWorkflowCc(query: PageQuery & { mine?: boolean }): Promise<{ list: WorkflowCc[]; total: number }>
}

export type WorkflowTaskCenterLoadResult =
  | { tab: 'pending'; list: WorkflowTask[]; total: number }
  | { tab: 'done'; list: WorkflowTask[]; total: number }
  | { tab: 'started'; list: WorkflowInstance[]; total: number }
  | { tab: 'cc'; list: WorkflowCc[]; total: number }

export async function loadWorkflowTaskCenterTab({
  tab,
  pages,
  api,
  startedScope = 'RUNTIME'
}: {
  tab: WorkflowTaskCenterTab
  pages: WorkflowTaskCenterPages
  api: WorkflowTaskCenterApi
  startedScope?: WorkflowInstanceScope
}): Promise<WorkflowTaskCenterLoadResult> {
  if (tab === 'pending') {
    const result = await api.pageWorkflowTasks({ page: pages.pending.page, limit: pages.pending.pageSize, mine: true, status: 'PENDING' })
    return { tab, list: result.list, total: result.total }
  }

  if (tab === 'done') {
    const result = await api.pageWorkflowTasks({ page: pages.done.page, limit: pages.done.pageSize, mine: true, statuses: DONE_TASK_STATUSES })
    return { tab, list: result.list, total: result.total }
  }

  if (tab === 'started') {
    const result = await api.pageWorkflowInstances({ page: pages.started.page, limit: pages.started.pageSize, mine: true, scope: startedScope })
    return { tab, list: result.list, total: result.total }
  }

  const result = await api.pageWorkflowCc({ page: pages.cc.page, limit: pages.cc.pageSize, mine: true })
  return { tab, list: result.list, total: result.total }
}

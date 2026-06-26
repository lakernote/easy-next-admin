import type { EntityId } from '@/features/system/types'

export interface BatchTask {
  id: EntityId
  taskType: string
  taskName: string
  businessKey?: string
  triggerType: 'MANUAL' | 'API' | 'JOB' | 'MESSAGE' | 'SYSTEM' | string
  triggerRefId?: string
  status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'PARTIAL_SUCCESS' | 'FAILED' | 'CANCELING' | 'CANCELED' | string
  totalCount: number
  successCount: number
  failedCount: number
  skippedCount: number
  progressPercent: number
  cancelRequested: boolean
  startedAt?: string
  finishedAt?: string
  traceId?: string
  errorMessage?: string
  resultMessage?: string
  remark?: string
  createTime?: string
  updateTime?: string
}

export interface BatchTaskItem {
  id: EntityId
  taskId: EntityId
  itemKey: string
  itemName?: string
  status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'SKIPPED' | 'RETRYING' | string
  retryCount: number
  payload?: string
  errorMessage?: string
  resultMessage?: string
  startedAt?: string
  finishedAt?: string
  remark?: string
  createTime?: string
  updateTime?: string
}

export interface BatchTaskQuery {
  page?: number
  limit?: number
  keyword?: string
  taskType?: string
  triggerType?: string
  status?: string
}

export interface BatchTaskItemQuery {
  page?: number
  limit?: number
  status?: string
  keyword?: string
}

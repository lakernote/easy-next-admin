import request from '@/api/request'
import { toData, toPageResult, type ApiResponse, type PageApiResponse } from '@/api/types'
import type { BatchTask, BatchTaskItem, BatchTaskItemQuery, BatchTaskQuery } from './types'

export async function pageBatchTasks(query: BatchTaskQuery = {}) {
  const response = await request.get<PageApiResponse<BatchTask>>('/batch/tasks', {
    params: {
      page: query.page || 1,
      limit: query.limit || 10,
      keyword: query.keyword,
      taskType: query.taskType,
      triggerType: query.triggerType,
      status: query.status
    }
  })
  return toPageResult(response.data)
}

export async function getBatchTask(id: BatchTask['id']) {
  const response = await request.get<ApiResponse<BatchTask>>(`/batch/tasks/${id}`)
  return toData(response.data)
}

export async function pageBatchTaskItems(taskId: BatchTask['id'], query: BatchTaskItemQuery = {}) {
  const response = await request.get<PageApiResponse<BatchTaskItem>>(`/batch/tasks/${taskId}/items`, {
    params: {
      page: query.page || 1,
      limit: query.limit || 10,
      status: query.status,
      keyword: query.keyword
    }
  })
  return toPageResult(response.data)
}

export async function cancelBatchTask(id: BatchTask['id'], reason?: string) {
  const response = await request.put<ApiResponse<BatchTask>>(`/batch/tasks/${id}/cancel`, { reason })
  return toData(response.data)
}

export async function retryFailedBatchItems(id: BatchTask['id']) {
  const response = await request.put<ApiResponse<BatchTask>>(`/batch/tasks/${id}/retry-failed`)
  return toData(response.data)
}

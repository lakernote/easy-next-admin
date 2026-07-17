import request from '@/api/request'
import { toData, type ApiResponse } from '@/api/types'
import type { DashboardOverview } from './types'

export async function getDashboardOverview(): Promise<DashboardOverview> {
  const response = await request.get<ApiResponse<DashboardOverview>>('/system/workbench/overview')
  return toData(response.data)
}

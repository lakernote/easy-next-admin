import request from '@/api/request'
import { toData, type ApiResponse } from '@/api/types'
import type { EnterpriseReportOverview } from './types'

export async function getEnterprisePaperReport() {
  const response = await request.get<ApiResponse<EnterpriseReportOverview>>('/reports/enterprise-paper')
  return toData(response.data)
}

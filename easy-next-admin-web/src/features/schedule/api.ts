import request from '@/api/request'
import { toData, toPageResult, type ApiResponse, type PageApiResponse, type PageQuery } from '@/api/types'
import type { ScheduleJob, ScheduleJobLog } from './types'

export async function pageScheduleJobs(query: PageQuery = {}) {
  const response = await request.get<PageApiResponse<ScheduleJob>>('/schedule/jobs', {
    params: {
      page: query.page || 1,
      limit: query.limit || 10
    }
  })
  return toPageResult(response.data)
}

export async function saveScheduleJob(job: Partial<ScheduleJob>) {
  const response = await request.post<ApiResponse<void>>('/schedule/jobs', job)
  return toData(response.data)
}

export async function startScheduleJob(jobCode: string) {
  const response = await request.put<ApiResponse<void>>(`/schedule/jobs/${jobCode}/start`)
  return toData(response.data)
}

export async function stopScheduleJob(jobCode: string) {
  const response = await request.put<ApiResponse<void>>(`/schedule/jobs/${jobCode}/stop`)
  return toData(response.data)
}

export async function pageScheduleJobLogs(query: PageQuery & { jobCode?: string } = {}) {
  const response = await request.get<PageApiResponse<ScheduleJobLog>>('/schedule/job-logs', {
    params: {
      page: query.page || 1,
      limit: query.limit || 10,
      jobCode: query.jobCode
    }
  })
  return toPageResult(response.data)
}

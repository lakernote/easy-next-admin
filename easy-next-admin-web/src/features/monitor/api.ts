import request from '@/api/request'
import { toData, toPageResult, type ApiResponse, type PageApiResponse, type PageQuery } from '@/api/types'
import type {
  CacheEntryPage,
  CacheMonitorOverview,
  JobStatistics,
  MonitorStatisticsOverview,
  OnlineSession,
  RemoteCallStatistics,
  SystemStatusOverview,
  WebLogFileSnapshot
} from './types'

export async function getSystemStatusOverview() {
  const response = await request.get<ApiResponse<SystemStatusOverview>>('/monitor/system/overview')
  return toData(response.data)
}

export async function getMonitorStatisticsOverview() {
  const response = await request.get<ApiResponse<MonitorStatisticsOverview>>('/monitor/statistics/overview')
  return toData(response.data)
}

export async function pageOnlineUsers(query: PageQuery = {}) {
  const response = await request.get<PageApiResponse<OnlineSession>>('/monitor/statistics/online-users', {
    params: {
      page: query.page || 1,
      limit: query.limit || 10
    }
  })
  return toPageResult(response.data)
}

export async function revokeOnlineSession(sessionId: string | number) {
  const response = await request.delete<ApiResponse<void>>(`/auth/sessions/${sessionId}`)
  return toData(response.data)
}

export async function getCacheMonitorOverview() {
  const response = await request.get<ApiResponse<CacheMonitorOverview>>('/monitor/cache/overview')
  return toData(response.data)
}

export async function clearMonitorCache(cacheName: string) {
  const response = await request.delete<ApiResponse<void>>(`/monitor/cache/${encodeURIComponent(cacheName)}`)
  return toData(response.data)
}

export async function listCacheEntries(cacheName: string, params: { keyword?: string; selectedKey?: string; limit?: number } = {}) {
  const response = await request.get<ApiResponse<CacheEntryPage>>(`/monitor/cache/${encodeURIComponent(cacheName)}/entries`, { params })
  return toData(response.data)
}

export async function evictCacheEntry(cacheName: string, key: string) {
  const response = await request.delete<ApiResponse<void>>(`/monitor/cache/${encodeURIComponent(cacheName)}/entries`, {
    params: { key }
  })
  return toData(response.data)
}

export async function listRemoteCallStatistics() {
  const response = await request.get<ApiResponse<RemoteCallStatistics[]>>('/monitor/statistics/remote-calls')
  return toData(response.data)
}

export async function getJobStatistics(params: { limit?: number } = {}) {
  const response = await request.get<ApiResponse<JobStatistics>>('/monitor/statistics/jobs', { params })
  return toData(response.data)
}

export async function getWebLogFileSnapshot(params: { keyword?: string; level?: string; lines?: number } = {}) {
  const response = await request.get<ApiResponse<WebLogFileSnapshot>>('/monitor/weblog/file/snapshot', { params })
  return toData(response.data)
}

export async function configureWebLogLevel(params: { name: string; configuredLevel: string }) {
  const response = await request.post<ApiResponse<string>>('/monitor/weblog/level', null, { params })
  return toData(response.data)
}

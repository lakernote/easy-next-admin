import request from '@/api/request'
import { toData, toPageResult, type ApiResponse, type PageApiResponse, type PageQuery } from '@/api/types'
import type {
  CacheEntryPage,
  CacheMonitorOverview,
  ActiveOperation,
  DeadlockReport,
  DiagnosisSummary,
  DiagnosisEvent,
  DiagnosisMetricSample,
  ErrorSample,
  HistoryPage,
  HeapHistogram,
  HeapHistogramCapability,
  JfrFile,
  JfrAnalysis,
  JfrRecorderStatus,
  JobStatistics,
  MetricHistoryPoint,
  OperationSample,
  PoolDiagnostics,
  MonitorStatisticsOverview,
  OnlineSession,
  RemoteCallStatistics,
  RuntimeStatistics,
  SlowSample,
  SystemStatusOverview,
  TraceSample,
  TraceCorrelation,
  TraceJfrSelection,
  ThreadDump,
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

export async function getDiagnosisMetrics() {
  const response = await request.get<ApiResponse<DiagnosisMetricSample[]>>('/monitor/diagnosis/metrics', {
    params: { limit: 2000 }
  })
  return toData(response.data) ?? []
}

export interface DiagnosisHistoryQuery {
  hours?: number
  before?: string
  size?: number
  keyword?: string
  traceOnly?: boolean
}

interface DiagnosisHistoryCapabilities {
  keyword?: boolean
  traceOnly?: boolean
}

async function getDiagnosisHistoryPage<T>(
  path: string,
  query: DiagnosisHistoryQuery = {},
  capabilities: DiagnosisHistoryCapabilities = {}
) {
  const params: Record<string, string | number | boolean | undefined> = {
    hours: query.hours ?? 24,
    before: query.before,
    size: query.size ?? 100
  }
  if (capabilities.keyword) params.keyword = query.keyword?.trim() || undefined
  if (capabilities.traceOnly) params.traceOnly = query.traceOnly || undefined
  const response = await request.get<ApiResponse<HistoryPage<T>>>(path, {
    params
  })
  return toData(response.data) ?? { items: [], hasMore: false }
}

const searchableIncidentHistory = { keyword: true, traceOnly: true }
const searchableHistory = { keyword: true }

export const getDiagnosisEventsPage = (query?: DiagnosisHistoryQuery) => getDiagnosisHistoryPage<DiagnosisEvent>('/monitor/diagnosis/events/page', query, searchableIncidentHistory)
export const getSlowSamplesPage = (query?: DiagnosisHistoryQuery) => getDiagnosisHistoryPage<SlowSample>('/monitor/diagnosis/slow/page', query, searchableIncidentHistory)
export const getDiagnosisErrorsPage = (query?: DiagnosisHistoryQuery) => getDiagnosisHistoryPage<ErrorSample>('/monitor/diagnosis/errors/page', query, searchableIncidentHistory)
export const getRecentTracesPage = (query?: DiagnosisHistoryQuery) => getDiagnosisHistoryPage<TraceSample>('/monitor/diagnosis/traces/page', query, searchableHistory)
export const getOperationSamplesPage = (query?: DiagnosisHistoryQuery) => getDiagnosisHistoryPage<OperationSample>('/monitor/diagnosis/operations/page', query, searchableHistory)
export const getJfrFilesPage = (query?: DiagnosisHistoryQuery) => getDiagnosisHistoryPage<JfrFile>('/monitor/diagnosis/jfr/files/page', query)

export async function getJfrStatus() {
  const response = await request.get<ApiResponse<JfrRecorderStatus>>('/monitor/diagnosis/jfr/recording/status')
  return toData(response.data)
}

export async function startJfrRecording(durationMinutes = 60) {
  const response = await request.post<ApiResponse<JfrRecorderStatus>>('/monitor/diagnosis/jfr/recording/start', null, {
    params: { durationMinutes }
  })
  return toData(response.data)
}

export async function stopJfrRecording() {
  const response = await request.post<ApiResponse<JfrRecorderStatus>>('/monitor/diagnosis/jfr/recording/stop')
  return toData(response.data)
}

export async function getDiagnosisSummary() {
  const response = await request.get<ApiResponse<DiagnosisSummary>>('/monitor/diagnosis/summary')
  return toData(response.data)
}

export async function getActiveOperations() {
  const response = await request.get<ApiResponse<ActiveOperation[]>>('/monitor/diagnosis/active', { params: { limit: 200 } })
  return toData(response.data) ?? []
}

export async function getTraceCorrelation(traceId: string) {
  const response = await request.get<ApiResponse<TraceCorrelation>>(`/monitor/diagnosis/traces/${encodeURIComponent(traceId)}/correlation`)
  return toData(response.data)
}

export async function getTraceJfrSelection(traceId: string) {
  const response = await request.get<ApiResponse<TraceJfrSelection>>(
    `/monitor/diagnosis/jfr/files/for-trace/${encodeURIComponent(traceId)}`
  )
  return toData(response.data)
}

export interface JfrAnalysisQuery { fileName?: string; traceId?: string; from?: string; to?: string; limit?: number }

export async function analyzeJfr(query: JfrAnalysisQuery = {}) {
  const response = await request.get<ApiResponse<JfrAnalysis>>('/monitor/diagnosis/jfr/analysis', {
    params: { fileName: query.fileName, traceId: query.traceId, from: query.from, to: query.to, limit: query.limit ?? 20 }
  })
  return toData(response.data)
}

export interface JfrWindowAnalysisQuery extends Omit<JfrAnalysisQuery, 'fileName'> { fileNames: string[] }

export async function analyzeJfrWindow(query: JfrWindowAnalysisQuery) {
  const response = await request.get<ApiResponse<JfrAnalysis>>('/monitor/diagnosis/jfr/analysis/window', {
    params: {
      fileNames: query.fileNames.join(','),
      traceId: query.traceId,
      from: query.from,
      to: query.to,
      limit: query.limit ?? 20
    }
  })
  return toData(response.data)
}

export async function getJvmThreads() {
  const response = await request.get<ApiResponse<ThreadDump>>('/monitor/diagnosis/threads', { params: { limit: 200, stackDepth: 64 } })
  return toData(response.data)
}

export async function getJvmDeadlocks() {
  const response = await request.get<ApiResponse<DeadlockReport>>('/monitor/diagnosis/deadlocks', { params: { limit: 100, stackDepth: 64 } })
  return toData(response.data)
}

export async function getRuntimePools() {
  const response = await request.get<ApiResponse<PoolDiagnostics>>('/monitor/diagnosis/pools')
  return toData(response.data)
}

export async function getHeapHistogramCapability() {
  const response = await request.get<ApiResponse<HeapHistogramCapability>>('/monitor/diagnosis/heap-histogram/capability')
  return toData(response.data)
}

export async function captureHeapHistogram(limit = 100) {
  const response = await request.post<ApiResponse<HeapHistogram>>('/monitor/diagnosis/heap-histogram', null, {
    params: { limit }
  })
  return toData(response.data)
}

export async function getRuntimeStatistics(params: { type?: string; windowHours?: number; limit?: number; sort?: string } = {}) {
  const response = await request.get<ApiResponse<RuntimeStatistics>>('/monitor/diagnosis/statistics', {
    params: { type: params.type ?? 'ALL', windowHours: params.windowHours ?? 24, limit: params.limit ?? 100, sort: params.sort ?? 'totalDurationMs,desc' }
  })
  return toData(response.data)
}

export async function getMetricHistory(name: string, tags: Record<string, string>) {
  const response = await request.get<ApiResponse<MetricHistoryPoint[]>>('/monitor/diagnosis/metrics/history', { params: { name, tags: JSON.stringify(tags), limit: 50000 } })
  return toData(response.data) ?? []
}

export async function getTraceDetail(traceId: string) {
  const response = await request.get<ApiResponse<TraceSample>>(`/monitor/diagnosis/traces/${encodeURIComponent(traceId)}`)
  return toData(response.data)
}

export async function downloadJfrFile(fileName: string) {
  const response = await request.get<Blob>(`/monitor/diagnosis/jfr/files/${encodeURIComponent(fileName)}`, { responseType: 'blob' })
  const url = URL.createObjectURL(response.data)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  URL.revokeObjectURL(url)
}

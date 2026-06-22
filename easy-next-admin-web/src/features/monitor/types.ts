export interface SystemStatusOverview {
  status: string
  healthy: boolean
  applicationName: string
  activeProfiles: string
  startTime: string
  sampleTime: string
  uptimeSeconds: NumericStat
  uptime: string
  cpu: CpuMetrics
  memory: MemoryMetrics
  threads: ThreadMetrics
  server?: ServerInfo
  java?: JavaInfo
  disks: DiskMetrics[]
  garbageCollectors: GarbageCollectorMetrics[]
  runtime: RuntimeInfo[]
}

export type NumericStat = number | string

export interface CpuMetrics {
  processors: number
  systemLoadAverage: number
  systemCpuUsagePercent: number
  processCpuUsagePercent: number
  idleCpuUsagePercent?: number
}

export interface MemoryMetrics {
  heapUsedBytes: NumericStat
  heapCommittedBytes: NumericStat
  heapMaxBytes: NumericStat
  heapFreeBytes?: NumericStat
  heapUsagePercent: number
  nonHeapUsedBytes: NumericStat
  nonHeapCommittedBytes: NumericStat
  nonHeapUsagePercent?: number
  physicalTotalBytes: NumericStat
  physicalFreeBytes: NumericStat
  physicalUsedBytes?: NumericStat
  physicalUsagePercent: number
}

export interface ThreadMetrics {
  live: number
  daemon: number
  peak: number
  totalStarted: NumericStat
}

export interface DiskMetrics {
  name?: string
  path: string
  fileSystem?: string
  type?: string
  totalBytes: NumericStat
  freeBytes?: NumericStat
  usableBytes: NumericStat
  usedBytes: NumericStat
  usagePercent: number
}

export interface GarbageCollectorMetrics {
  name: string
  collectionCount: NumericStat
  collectionTimeMillis: NumericStat
}

export interface RuntimeInfo {
  label: string
  value: string
}

export interface ServerInfo {
  name: string
  ip: string
  osName: string
  osArch: string
  osVersion: string
  processors: number
}

export interface JavaInfo {
  name: string
  version: string
  vendor: string
  home: string
  projectDir: string
  startTime: string
  runTime: string
  inputArguments: string
}

export interface MonitorStatisticsOverview {
  api: ApiStatistics
  onlineUsers: OnlineUserStatistics
  remoteCalls: RemoteCallStatistics[]
  jobs: JobStatistics
}

export interface CacheMonitorOverview {
  provider: string
  scope?: 'LOCAL' | 'DISTRIBUTED' | string
  sampleTime?: string
  statisticsAvailable?: boolean
  cacheCount: number
  totalEstimatedSize: NumericStat
  totalMaximumSize?: NumericStat
  totalRequestCount: NumericStat
  totalHitCount: NumericStat
  totalMissCount: NumericStat
  totalEvictionCount: NumericStat
  hitRate: NumericStat
  missRate?: NumericStat
  usageRate?: NumericStat
  warningCount?: number
  busiestCacheName?: string
  largestCacheName?: string
  weakestCacheName?: string
  recommendations?: string[]
  caches: CacheMonitorItem[]
}

export interface CacheMonitorItem {
  name: string
  provider?: string
  nativeClass?: string
  estimatedSize?: NumericStat
  maximumSize?: NumericStat
  ttlSeconds?: NumericStat
  maxIdleSeconds?: NumericStat
  requestCount?: NumericStat
  hitCount?: NumericStat
  missCount?: NumericStat
  evictionCount?: NumericStat
  hitRate?: NumericStat
  missRate?: NumericStat
  usageRate?: NumericStat
  statisticsAvailable?: boolean
  healthStatus?: string
  healthLabel?: string
  riskLevel?: 'success' | 'warning' | 'danger' | 'info' | string
  description?: string
}

export interface CacheEntryPage {
  cacheName: string
  provider?: string
  nativeClass?: string
  scope?: 'LOCAL' | 'DISTRIBUTED' | string
  total: number
  limit: number
  truncated?: boolean
  selectedKey?: string
  selected?: CacheEntryItem
  entries: CacheEntryItem[]
}

export interface CacheEntryItem {
  key: string
  keyType?: string
  valueType?: string
  valuePreview?: string
  valueTruncated?: boolean
}

export interface ApiStatistics {
  totalCount: number
  successCount: number
  failureCount: number
  uniqueIpCount: number
  slowCount: number
  avgCostMs: number
  maxCostMs: number
  successRate: number
  endpoints: ApiEndpointStatistics[]
}

export interface ApiEndpointStatistics {
  uri: string
  method: string
  totalCount: number
  successCount: number
  failureCount: number
  avgCostMs: number
  maxCostMs: number
  successRate: number
  lastTime?: string
}

export interface OnlineUserStatistics {
  totalCount: number
  records: OnlineSession[]
}

export interface OnlineSession {
  sessionId: string | number
  userId: string | number
  userName?: string
  nickName?: string
  avatar?: string
  clientType?: string
  clientVersion?: string
  ip?: string
  userAgent?: string
  status?: string
  loginTime?: string
  lastActiveTime?: string
  accessExpireTime?: string
  current?: boolean
}

export interface RemoteCallStatistics {
  target: string
  method: string
  totalCount: number
  successCount: number
  failureCount: number
  avgCostMs: number
  maxCostMs: number
  successRate: number
}

export interface JobStatistics {
  totalJobs: number
  enabledJobs: number
  executionCount24h: number
  failureCount24h: number
  avgCostMs24h: number
  maxCostMs24h: number
  successRate24h: number
  executions: JobExecutionStatistics[]
}

export interface JobExecutionStatistics {
  jobCode: string
  totalCount: number
  successCount: number
  failureCount: number
  avgCostMs: number
  maxCostMs: number
  lastStatus?: number
  lastStartTime?: string
}

export interface WebLogFileSnapshot {
  source: string
  fileName: string
  filePath: string
  charset: string
  fileSizeBytes: NumericStat
  lastModifiedTime?: string
  sampleTime?: string
  requestedLines: number
  returnedLines: number
  truncated: boolean
  readable: boolean
  message?: string
  lines: string[]
}

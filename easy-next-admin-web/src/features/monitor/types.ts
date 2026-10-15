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

export interface DiagnosisMetricSample { name: string; type: string; baseUnit?: string; description?: string; tags: Record<string, string>; measurements: Record<string, number> }
export interface HistoryPage<T> { items: T[]; hasMore: boolean; nextBefore?: string }
export interface MetricHistoryPoint { capturedAt: string; tags: Record<string, string>; measurements: Record<string, number> }
export interface DiagnosisEvent { type: string; traceId?: string; name: string; method?: string; status: number; costMs: number; occurredAt: string; detail?: string; value?: number; threshold?: number; slow: boolean; error: boolean; reasons: string[] }
export interface SlowSample { type: string; traceId?: string; name: string; method?: string; costMs: number; occurredAt: string; reasons: string[] }
export interface ErrorSample { id: string | number; source?: string; traceId?: string; requestUri: string; requestMethod: string; errorType: string; errorMessage: string; stackTrace: string; createdAt: string }
export interface TraceSample { type?: string; traceId?: string; name: string; method: string; status: number; tree: string; costMs: number; finishedAt: string }
export interface ChildOperationSummary { count: number; errorCount: number; totalDurationMs: number; maxDurationMs: number }
export interface OperationSample { type: string; traceId?: string; name: string; threadName: string; outcome: string; durationMs: number; cpuTimeMs: number; allocatedBytes: number; blockedCount: number; blockedTimeMs: number; waitedCount: number; waitedTimeMs: number; detail?: string; lockInfo: string; stackTrace: string; childSummaries: Record<string, ChildOperationSummary>; finishedAt: string }
export interface JfrFile { fileName: string; sizeBytes: number; recordingStartedAt?: string; recordingEndedAt?: string; modifiedAt: string }
export interface TraceJfrSelection { traceId: string; from?: string; to?: string; files: JfrFile[] }

export interface JfrRecorderStatus {
  enabled: boolean
  available: boolean
  running: boolean
  lastError: string
  retentionDays: number
  maxTotalBytes: number
  completedFiles: number
  completedBytes: number
  activeBytes: number
  totalBytes: number
  oldestRecordingAt?: string
  coverageHours: number
  capacityPressure: boolean
  sessionStartedAt?: string
  autoStopAt?: string
  maxSessionMinutes?: number
}

export interface DiagnosisSummary {
  status: 'HEALTHY' | 'WARNING' | 'ERROR' | string
  sampledAt: string
  windowMinutes: number
  summaryTruncated: boolean
  summarySampleLimit: number
  activeTotal: number
  activeByType: Record<string, number>
  traces: number
  slow: number
  errors: number
  retentionDays: number
  historyBytes: number
  jfrFiles: number
  jfrBytes: number
  thresholdsMs: Record<string, number>
  jfr: JfrRecorderStatus
  storage: DiagnosisStorageStatus
  metricsStorage: DiagnosisMetricStorageStatus
}

export interface DiagnosisStorageStatus {
  oldestSegmentDate?: string
  coverageDays: number
  retentionDays: number
  historyBytes: number
  maxHistoryBytes: number
  pendingWrites: number
  queueCapacity: number
  pendingBytes: number
  pendingMaxBytes: number
  droppedWrites: number
  byteBudgetDroppedWrites: number
  capacityPressure: boolean
}

export interface DiagnosisMetricStorageStatus {
  enabled: boolean
  storageDirectory: string
  retentionDays: number
  maxStorageBytes: number
  maxSegmentBytes: number
  storageBytes: number
  oldestSegmentDate?: string
  coverageDays: number
  segmentCount: number
  droppedWrites: number
  rejectedSeries: number
  evictedSeries: number
}

export interface ActiveOperation {
  id: string
  type: string
  traceId?: string
  name: string
  detail?: string
  threadId: number
  threadName: string
  threadState: string
  durationMs: number
  cpuTimeMs: number
  allocatedBytes: number
  blockedCount: number
  blockedTimeMs: number
  waitedCount: number
  waitedTimeMs: number
  lockInfo: string
  stackTrace: string
  startedAt: string
  sampledAt?: string
}

export interface TransactionSample {
  traceId?: string
  name: string
  success: boolean
  costMs: number
  finishedAt: string
}

export interface TraceCorrelation {
  trace?: TraceSample
  transactions: TransactionSample[]
  operations: OperationSample[]
  errors: ErrorSample[]
  events: DiagnosisEvent[]
  slow: SlowSample[]
}

export interface JfrAggregate {
  key: string
  detail: string
  count: number
  estimatedBytes: number
  totalDurationNanos: number
  maxDurationNanos: number
}

export interface JfrAnalysis {
  status: string
  message: string
  fileName?: string
  fileSizeBytes: number
  modifiedAt?: string
  recordingStartedAt?: string
  recordingEndedAt?: string
  scannedEvents: number
  relevantEvents: number
  deduplicatedEvents?: number
  truncated: boolean
  executionHotspots: JfrAggregate[]
  allocations: JfrAggregate[]
  locks: JfrAggregate[]
  gcPauses: JfrAggregate[]
  ioActivities: JfrAggregate[]
  errorThrows: JfrAggregate[]
  operations: JfrRuntimeOperation[]
}

export interface JfrRuntimeOperation {
  type: string
  traceId?: string
  operation: string
  detail: string
  outcome: string
  errorType: string
  durationNanos: number
  cpuTimeMs: number
  allocatedBytes: number
  blockedCount: number
  waitedCount: number
  startedAt: string
  endedAt: string
}

export interface ThreadStackFrame { className: string; methodName: string; fileName?: string; lineNumber: number; nativeMethod: boolean }
export interface HeldThreadLock { type: string; className: string; identityHashCode: number; label: string; lockedAt?: string }
export interface ThreadSnapshot {
  id: number
  name: string
  state: string
  daemon: boolean
  priority: number
  cpuTimeNanos?: number
  allocatedBytes?: number
  blockedCount: number
  blockedTimeMs?: number
  waitedCount: number
  waitedTimeMs?: number
  waitingOn?: { label: string }
  lockOwner?: { id: number; name: string }
  lockedMonitors: HeldThreadLock[]
  lockedSynchronizers: HeldThreadLock[]
  stackTrace: ThreadStackFrame[]
  stackTruncated: boolean
  deadlocked: boolean
}
export interface ThreadDump { sampledAt: string; totalThreads: number; returnedThreads: number; truncated: boolean; stackDepth: number; cpuTimeEnabled: boolean; allocatedMemoryEnabled: boolean; contentionMonitoringEnabled: boolean; threads: ThreadSnapshot[] }
export interface DeadlockReport { sampledAt: string; detected: boolean; count: number; truncated: boolean; threads: ThreadSnapshot[] }
export interface PoolMetric { name: string; meterType: string; baseUnit?: string; description?: string; tags: Record<string, string>; measurements: Record<string, number> }
export interface PoolView { type: string; name: string; active?: number; idle?: number; pending?: number; current?: number; max?: number; completed?: number; utilizationPercent?: number; metrics: PoolMetric[] }
export interface PoolDiagnostics { sampledAt: string; pools: PoolView[] }
export interface HeapHistogramCapability { supported: boolean; status: 'AVAILABLE' | 'UNAVAILABLE'; impact: string; message: string }
export interface HeapClassHistogramRow { rank: number; instances: number; bytes: number; className: string; module?: string }
export interface HeapHistogram {
  sampledAt: string
  status: 'READY' | 'UNAVAILABLE' | 'BUSY' | 'FAILED'
  supported: boolean
  allObjects: boolean
  impact: string
  message: string
  limit: number
  totalClasses: number
  returnedClasses: number
  truncated: boolean
  totalInstances: number
  totalBytes: number
  classes: HeapClassHistogramRow[]
}

export interface RuntimeStatisticRow {
  type: string
  name: string
  detail: string
  sampled: boolean
  coverage: string
  count: number
  errorCount: number
  errorRate: number
  avgDurationMs: number
  maxDurationMs: number
  p50Ms: number
  p95Ms: number
  p99Ms: number
  totalDurationMs: number
  avgCpuMs?: number
  avgAllocatedBytes?: number
  cpuSamples: number
  allocatedSamples: number
}
export interface RuntimeStatistics {
  type: string
  windowHours: number
  from: string
  to: string
  sampled: boolean
  coverage: string
  matchedSamples: number
  scanLimitReached: boolean
  groupLimitReached: boolean
  totalGroups: number
  coverageByType: Array<{ type: string; sampled: boolean; coverage: string; readSamples: number; matchedSamples: number; scanLimitReached: boolean; note: string }>
  items: RuntimeStatisticRow[]
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

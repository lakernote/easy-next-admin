import type { ChildOperationSummary, DiagnosisMetricSample, MetricHistoryPoint } from './types'

export type DiagnosisMetricCategory =
  | 'application'
  | 'request'
  | 'database'
  | 'jvm'
  | 'system'
  | 'container'
  | 'executor'
  | 'other'

export interface DiagnosisMetricPresentation {
  category: DiagnosisMetricCategory
  categoryLabel: string
  label: string
}
export const DIAGNOSIS_METRIC_CATEGORIES: Array<{ value: DiagnosisMetricCategory; label: string }> = [
  { value: 'application', label: '业务应用' },
  { value: 'request', label: 'HTTP 请求' },
  { value: 'database', label: '数据库' },
  { value: 'jvm', label: 'JVM' },
  { value: 'system', label: '进程与系统' },
  { value: 'container', label: 'Web 容器' },
  { value: 'executor', label: '线程池' },
  { value: 'other', label: '其他' }
]

const METRIC_LABELS: Record<string, string> = {
  'easy.api.requests': '应用接口请求',
  'easy.remote.calls': '远程调用',
  'easy.rate_limit.blocked': '限流拦截',
  'easy.schedule.jobs': '定时任务执行',
  'easy.outbox.messages': 'Outbox 消息',
  'easy.transactions': 'Spring 事务',
  'easy.transactions.active': '当前活跃事务',
  'easy.sql.queries': 'SQL 执行',
  'http.server.requests': 'HTTP 服务请求',
  'jvm.memory.used': 'JVM 已用内存',
  'jvm.memory.committed': 'JVM 已提交内存',
  'jvm.memory.max': 'JVM 最大内存',
  'jvm.gc.pause': 'GC 暂停',
  'jvm.threads.live': 'JVM 活跃线程',
  'jvm.threads.daemon': 'JVM 守护线程',
  'jvm.threads.peak': 'JVM 峰值线程',
  'process.cpu.usage': '进程 CPU 使用率',
  'process.uptime': '进程运行时长',
  'process.start.time': '进程启动时间',
  'system.cpu.usage': '系统 CPU 使用率',
  'system.load.average.1m': '系统一分钟负载',
  'hikaricp.connections.active': '连接池活跃连接',
  'hikaricp.connections.idle': '连接池空闲连接',
  'hikaricp.connections.pending': '等待数据库连接',
  'hikaricp.connections.max': '连接池最大连接',
  'hikaricp.connections.min': '连接池最小连接',
  'hikaricp.connections.acquire': '获取数据库连接耗时',
  'hikaricp.connections.timeout': '获取连接超时',
  'hikaricp.connections.usage': '数据库连接占用时长',
  'jdbc.connections.active': 'JDBC 活跃连接',
  'jdbc.connections.idle': 'JDBC 空闲连接',
  'jdbc.connections.max': 'JDBC 最大连接',
  'jdbc.connections.min': 'JDBC 最小连接',
  'tomcat.threads.busy': 'Tomcat 忙碌线程',
  'tomcat.threads.current': 'Tomcat 当前线程',
  'tomcat.threads.config.max': 'Tomcat 最大线程'
}

const CATEGORY_LABELS = new Map(DIAGNOSIS_METRIC_CATEGORIES.map((item) => [item.value, item.label]))

export function metricPresentation(name: string): DiagnosisMetricPresentation {
  const category = metricCategory(name)
  return {
    category,
    categoryLabel: CATEGORY_LABELS.get(category) || '其他',
    label: METRIC_LABELS[name] || fallbackMetricLabel(name)
  }
}

export function metricCategory(name: string): DiagnosisMetricCategory {
  if (name === 'easy.sql.queries') return 'database'
  if (name.startsWith('easy.')) return 'application'
  if (name.startsWith('http.server.')) return 'request'
  if (name.startsWith('hikaricp.') || name.startsWith('jdbc.connections.')) return 'database'
  if (name.startsWith('jvm.')) return 'jvm'
  if (name.startsWith('process.') || name.startsWith('system.')) return 'system'
  if (name.startsWith('tomcat.')) return 'container'
  if (name.startsWith('executor.')) return 'executor'
  return 'other'
}

function fallbackMetricLabel(name: string) {
  if (name.startsWith('executor.')) return `线程池 ${name.slice('executor.'.length)}`
  if (name.startsWith('jvm.')) return `JVM ${name.slice('jvm.'.length)}`
  if (name.startsWith('process.')) return `进程 ${name.slice('process.'.length)}`
  if (name.startsWith('system.')) return `系统 ${name.slice('system.'.length)}`
  return name
}

export function operationTypeLabel(type: string) {
  return ({
    HTTP: '请求',
    JOB: 'Job',
    KAFKA: 'Kafka',
    SQL: 'SQL',
    TRANSACTION: '事务',
    REMOTE: '远程调用'
  } as Record<string, string>)[type] || type
}

export function formatChildOperationSummaries(summaries?: Record<string, ChildOperationSummary>) {
  if (!summaries) return '-'
  const order = ['SQL', 'TRANSACTION', 'REMOTE']
  const entries = Object.entries(summaries)
    .filter(([, summary]) => summary.count > 0)
    .sort(([left], [right]) => {
      const leftIndex = order.indexOf(left)
      const rightIndex = order.indexOf(right)
      return (leftIndex < 0 ? order.length : leftIndex) - (rightIndex < 0 ? order.length : rightIndex) || left.localeCompare(right)
    })
  if (!entries.length) return '-'
  return entries.map(([type, summary]) => `${operationTypeLabel(type)} ${summary.count.toLocaleString('zh-CN')} 次 · 总耗时 ${summary.totalDurationMs.toLocaleString('zh-CN')} ms · 最慢 ${summary.maxDurationMs.toLocaleString('zh-CN')} ms · 错误 ${summary.errorCount.toLocaleString('zh-CN')}`).join('；')
}

export function diagnosisEventLabel(type: string) {
  return ({
    ERROR: '请求错误',
    SLOW_REQUEST: '慢请求',
    SLOW_TRANSACTION: '长事务',
    TRANSACTION_ERROR: '事务失败',
    SLOW_SQL: '慢 SQL',
    SQL_ERROR: 'SQL 失败',
    SLOW_JOB: '慢 Job',
    JOB_ERROR: 'Job 失败',
    SLOW_KAFKA: '慢 Kafka',
    KAFKA_ERROR: 'Kafka 失败',
    SLOW_REMOTE: '慢远程调用',
    REMOTE_ERROR: '远程调用失败',
    METRIC_HIGH: '指标越线',
    METRIC_RECOVERED: '指标恢复'
  } as Record<string, string>)[type] || type
}

export function formatMetricValue(metric: Pick<DiagnosisMetricSample, 'name' | 'baseUnit'>, statistic: string, value: number) {
  if (!Number.isFinite(value)) return '-'
  if (['COUNT', 'ACTIVE_TASKS'].includes(statistic)) return formatCompactNumber(value)
  if (metric.name === 'process.start.time') return formatDiagnosisTime(new Date(value * 1_000).toISOString())

  const unit = (metric.baseUnit || '').trim().toLocaleLowerCase('en-US')
  if (unit.includes('byte')) return formatBinaryBytes(value)
  if (['seconds', 'second', 's'].includes(unit)) return formatSeconds(value)
  if (['milliseconds', 'millisecond', 'ms'].includes(unit)) return formatSeconds(value / 1_000)
  if (['nanoseconds', 'nanosecond', 'ns'].includes(unit)) return formatSeconds(value / 1_000_000_000)
  if (unit.includes('percent')) return `${(Math.abs(value) <= 1 ? value * 100 : value).toFixed(1)}%`
  if (/^(process|system)\.cpu\.usage$/.test(metric.name)) return `${(value * 100).toFixed(1)}%`
  return `${formatCompactNumber(value)}${metric.baseUnit ? ` ${metric.baseUnit}` : ''}`
}

/**
 * Reduces an ordered metric series without hiding a short peak between evenly spaced samples.
 * The first and last points are retained, while each interior time bucket contributes its
 * minimum and maximum value in their original order.
 */
export function sampleMetricHistory(
  items: MetricHistoryPoint[],
  measurement: string,
  maximum: number
) {
  const limit = Math.max(0, Math.floor(maximum))
  if (limit === 0 || items.length === 0) return []
  if (items.length <= limit) return items
  if (limit === 1) return [items[0]]
  if (limit === 2) return [items[0], items[items.length - 1]]

  const interiorCount = items.length - 2
  const bucketCount = Math.max(1, Math.floor((limit - 2) / 2))
  const selected: MetricHistoryPoint[] = [items[0]]

  for (let bucket = 0; bucket < bucketCount; bucket++) {
    const start = 1 + Math.floor(bucket * interiorCount / bucketCount)
    const end = 1 + Math.floor((bucket + 1) * interiorCount / bucketCount)
    let minIndex = -1
    let maxIndex = -1
    let minValue = Number.POSITIVE_INFINITY
    let maxValue = Number.NEGATIVE_INFINITY

    for (let index = start; index < end; index++) {
      const value = items[index]?.measurements?.[measurement]
      if (!Number.isFinite(value)) continue
      if (value < minValue) {
        minValue = value
        minIndex = index
      }
      // Keep the last equal maximum so a flat bucket still preserves both time edges.
      if (value >= maxValue) {
        maxValue = value
        maxIndex = index
      }
    }

    const bucketIndexes = minIndex < 0
      ? [start, end - 1]
      : [minIndex, maxIndex]
    for (const index of [...new Set(bucketIndexes)].sort((left, right) => left - right)) {
      if (selected.length >= limit - 1) break
      selected.push(items[index])
    }
  }

  selected.push(items[items.length - 1])
  return selected
}

function formatBinaryBytes(value: number) {
  const absolute = Math.abs(value)
  if (absolute < 1024) return `${formatCompactNumber(value)} B`
  if (absolute < 1024 ** 2) return `${(value / 1024).toFixed(1)} KB`
  if (absolute < 1024 ** 3) return `${(value / 1024 ** 2).toFixed(1)} MB`
  return `${(value / 1024 ** 3).toFixed(2)} GB`
}

function formatSeconds(value: number) {
  const absolute = Math.abs(value)
  if (absolute < 0.001) return `${(value * 1_000_000).toFixed(1)} μs`
  if (absolute < 1) return `${(value * 1_000).toFixed(1)} ms`
  if (absolute < 60) return `${formatCompactNumber(value)} s`
  if (absolute < 3600) return `${(value / 60).toFixed(1)} min`
  return `${(value / 3600).toFixed(2)} h`
}

function formatCompactNumber(value: number) {
  if (Math.abs(value) >= 1_000_000) return value.toExponential(3)
  return Number(value.toPrecision(6)).toLocaleString('zh-CN')
}

export function matchesDiagnosisKeyword(keyword: string, ...values: unknown[]) {
  const normalizedKeyword = keyword.trim().toLocaleLowerCase('zh-CN')
  if (!normalizedKeyword) return true
  return values.some((value) => stringifySearchValue(value).toLocaleLowerCase('zh-CN').includes(normalizedKeyword))
}

function stringifySearchValue(value: unknown): string {
  if (value == null) return ''
  if (typeof value === 'object') {
    return Object.entries(value as Record<string, unknown>)
      .map(([key, item]) => `${key} ${stringifySearchValue(item)}`)
      .join(' ')
  }
  return String(value)
}

export function isWithinDiagnosisWindow(value: string | undefined, hours: number, now = Date.now()) {
  if (!hours) return true
  if (!value) return false
  const timestamp = Date.parse(value)
  if (!Number.isFinite(timestamp)) return false
  return timestamp >= now - hours * 60 * 60 * 1000
}

export function formatDiagnosisTime(value: string | undefined) {
  if (!value) return '-'
  const timestamp = Date.parse(value)
  if (!Number.isFinite(timestamp)) return value
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  }).format(timestamp)
}

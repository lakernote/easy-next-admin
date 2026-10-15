import { describe, expect, it } from 'vitest'
import {
  diagnosisEventLabel,
  formatChildOperationSummaries,
  formatMetricValue,
  formatDiagnosisTime,
  isWithinDiagnosisWindow,
  matchesDiagnosisKeyword,
  metricPresentation,
  operationTypeLabel,
  sampleMetricHistory
} from './diagnosisPresentation'
import type { MetricHistoryPoint } from './types'

describe('runtime diagnosis presentation', () => {
  it('groups common Micrometer metrics and gives important metrics readable names', () => {
    expect(metricPresentation('easy.transactions')).toEqual({
      category: 'application',
      categoryLabel: '业务应用',
      label: 'Spring 事务'
    })
    expect(metricPresentation('hikaricp.connections.pending').categoryLabel).toBe('数据库')
    expect(metricPresentation('easy.sql.queries').categoryLabel).toBe('数据库')
    expect(metricPresentation('jvm.memory.used').label).toBe('JVM 已用内存')
    expect(metricPresentation('executor.active').label).toBe('线程池 active')
  })

  it('gives SQL, Job and Kafka slow/error events readable Chinese names', () => {
    expect(diagnosisEventLabel('SLOW_SQL')).toBe('慢 SQL')
    expect(diagnosisEventLabel('SQL_ERROR')).toBe('SQL 失败')
    expect(diagnosisEventLabel('SLOW_JOB')).toBe('慢 Job')
    expect(diagnosisEventLabel('JOB_ERROR')).toBe('Job 失败')
    expect(diagnosisEventLabel('SLOW_KAFKA')).toBe('慢 Kafka')
    expect(diagnosisEventLabel('KAFKA_ERROR')).toBe('Kafka 失败')
    expect(diagnosisEventLabel('SLOW_REMOTE')).toBe('慢远程调用')
    expect(diagnosisEventLabel('REMOTE_ERROR')).toBe('远程调用失败')
  })

  it('formats metric values with Micrometer base units without applying time units to counts', () => {
    expect(formatMetricValue({ name: 'jvm.memory.used', baseUnit: 'bytes' }, 'VALUE', 1_572_864)).toBe('1.5 MB')
    expect(formatMetricValue({ name: 'http.server.requests', baseUnit: 'seconds' }, 'TOTAL_TIME', 1.5)).toBe('1.5 s')
    expect(formatMetricValue({ name: 'http.server.requests', baseUnit: 'seconds' }, 'COUNT', 12)).toBe('12')
    expect(formatMetricValue({ name: 'process.cpu.usage' }, 'VALUE', 0.125)).toBe('12.5%')
    expect(formatMetricValue({ name: 'process.start.time', baseUnit: 'seconds' }, 'VALUE', 0))
      .not.toContain(' h')
  })

  it('recognizes operation types used by correlated diagnosis samples', () => {
    expect(operationTypeLabel('HTTP')).toBe('请求')
    expect(operationTypeLabel('SQL')).toBe('SQL')
    expect(operationTypeLabel('TRANSACTION')).toBe('事务')
    expect(operationTypeLabel('REMOTE')).toBe('远程调用')
  })

  it('formats root-operation child summaries with SQL diagnostics first', () => {
    expect(formatChildOperationSummaries({
      REMOTE: { count: 1, errorCount: 0, totalDurationMs: 20, maxDurationMs: 20 },
      SQL: { count: 2, errorCount: 1, totalDurationMs: 35, maxDurationMs: 24 }
    })).toBe('SQL 2 次 · 总耗时 35 ms · 最慢 24 ms · 错误 1；远程调用 1 次 · 总耗时 20 ms · 最慢 20 ms · 错误 0')
    expect(formatChildOperationSummaries({})).toBe('-')
  })

  it('matches nested tags and respects the selected time window', () => {
    expect(matchesDiagnosisKeyword('rollback', { outcome: 'ROLLBACK', tags: { method: 'POST' } })).toBe(true)
    expect(matchesDiagnosisKeyword('get', { outcome: 'ROLLBACK', tags: { method: 'POST' } })).toBe(false)
    const now = Date.parse('2026-10-14T10:00:00Z')
    expect(isWithinDiagnosisWindow('2026-10-14T09:30:00Z', 1, now)).toBe(true)
    expect(isWithinDiagnosisWindow('2026-10-14T08:30:00Z', 1, now)).toBe(false)
    expect(isWithinDiagnosisWindow('invalid', 1, now)).toBe(false)
  })

  it('keeps invalid server timestamps readable instead of throwing', () => {
    expect(formatDiagnosisTime(undefined)).toBe('-')
    expect(formatDiagnosisTime('not-a-time')).toBe('not-a-time')
  })

  it('keeps first, last, minimum and maximum metric points within the chart limit', () => {
    const points = Array.from({ length: 1_000 }, (_, index) => ({
      capturedAt: `point-${index}`,
      tags: {},
      measurements: { VALUE: index === 123 ? -10_000 : index === 124 ? 20_000 : index }
    }))

    const sampled = sampleMetricHistory(points, 'VALUE', 600)

    expect(sampled.length).toBeLessThanOrEqual(600)
    expect(sampled[0]).toBe(points[0])
    expect(sampled[sampled.length - 1]).toBe(points[points.length - 1])
    expect(sampled).toContain(points[123])
    expect(sampled).toContain(points[124])
    expect(sampled.map((point) => Number(point.capturedAt.slice('point-'.length))))
      .toEqual([...sampled.map((point) => Number(point.capturedAt.slice('point-'.length)))].sort((left, right) => left - right))
  })

  it('keeps the requested hard limit when a metric bucket has no finite values', () => {
    const points: MetricHistoryPoint[] = Array.from({ length: 20 }, (_, index) => ({
      capturedAt: `point-${index}`,
      tags: {},
      measurements: (index % 2 === 0 ? {} : { OTHER: index }) as Record<string, number>
    }))

    const sampled = sampleMetricHistory(points, 'VALUE', 6)

    expect(sampled.length).toBeLessThanOrEqual(6)
    expect(sampled[0]).toBe(points[0])
    expect(sampled[sampled.length - 1]).toBe(points[points.length - 1])
  })

})

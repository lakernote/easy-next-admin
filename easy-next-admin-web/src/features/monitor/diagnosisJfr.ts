import type { JfrAnalysis, JfrFile, TraceSample } from './types'

export function toJfrAggregateSections(analysis?: JfrAnalysis) {
  if (!analysis) return []
  return [
    { key: 'executionHotspots', label: 'CPU 热点', rows: analysis.executionHotspots },
    { key: 'allocations', label: '对象分配', rows: analysis.allocations },
    { key: 'locks', label: '锁 / Park', rows: analysis.locks },
    { key: 'gcPauses', label: 'GC Pause', rows: analysis.gcPauses },
    { key: 'ioActivities', label: '慢 I/O', rows: analysis.ioActivities },
    { key: 'errorThrows', label: 'Error 抛出', rows: analysis.errorThrows }
  ]
}

export function preferredJfrAnalysisTab(analysis: JfrAnalysis) {
  if (analysis.operations.length) return 'operations'
  return toJfrAggregateSections(analysis).find((section) => section.rows.length)?.key || 'operations'
}

export function traceJfrWindow(trace: Pick<TraceSample, 'finishedAt' | 'costMs'>) {
  const finishedAt = Date.parse(trace.finishedAt)
  if (!Number.isFinite(finishedAt)) return undefined
  return {
    from: new Date(finishedAt - Math.max(0, trace.costMs)).toISOString(),
    to: new Date(finishedAt).toISOString()
  }
}

export function findCoveringJfrFile(files: JfrFile[], trace: Pick<TraceSample, 'finishedAt'>) {
  const finishedAt = Date.parse(trace.finishedAt)
  if (!Number.isFinite(finishedAt)) return undefined
  return files.find((file) => {
    const startedAt = Date.parse(file.recordingStartedAt || '')
    const endedAt = Date.parse(file.recordingEndedAt || file.modifiedAt)
    return Number.isFinite(startedAt) && Number.isFinite(endedAt) && startedAt <= finishedAt && finishedAt <= endedAt
  })
}

import { describe, expect, it } from 'vitest'
import { findCoveringJfrFile, preferredJfrAnalysisTab, toJfrAggregateSections, traceJfrWindow } from './diagnosisJfr'

describe('trace JFR correlation', () => {
  it('derives the trace execution window from its finish time and duration', () => {
    expect(traceJfrWindow({ finishedAt: '2026-10-14T02:00:01.500Z', costMs: 1_500 })).toEqual({
      from: '2026-10-14T02:00:00.000Z',
      to: '2026-10-14T02:00:01.500Z'
    })
  })

  it('selects the JFR segment covering the trace finish time', () => {
    const files = [
      { fileName: 'new.jfr', sizeBytes: 1, recordingStartedAt: '2026-10-14T03:00:00Z', recordingEndedAt: '2026-10-14T04:00:00Z', modifiedAt: '2026-10-14T04:00:00Z' },
      { fileName: 'match.jfr', sizeBytes: 1, recordingStartedAt: '2026-10-14T01:00:00Z', recordingEndedAt: '2026-10-14T03:00:00Z', modifiedAt: '2026-10-14T03:00:00Z' }
    ]
    expect(findCoveringJfrFile(files, { finishedAt: '2026-10-14T02:00:00Z' })?.fileName).toBe('match.jfr')
    expect(findCoveringJfrFile(files, { finishedAt: 'invalid' })).toBeUndefined()
  })

  it('picks the first useful JFR analysis section', () => {
    const analysis = {
      status: 'READY', message: '', fileSizeBytes: 1, scannedEvents: 1, relevantEvents: 1, truncated: false,
      executionHotspots: [], allocations: [{ key: 'byte[]', detail: '', count: 1, estimatedBytes: 10, totalDurationNanos: 0, maxDurationNanos: 0 }],
      locks: [], gcPauses: [], ioActivities: [], errorThrows: [], operations: []
    }
    expect(toJfrAggregateSections(analysis).map((section) => section.key)).toEqual([
      'executionHotspots', 'allocations', 'locks', 'gcPauses', 'ioActivities', 'errorThrows'
    ])
    expect(preferredJfrAnalysisTab(analysis)).toBe('allocations')
  })
})

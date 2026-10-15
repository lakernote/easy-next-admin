import { beforeEach, describe, expect, it, vi } from 'vitest'
import request from '@/api/request'
import {
  analyzeJfr,
  analyzeJfrWindow,
  getDiagnosisErrorsPage,
  getDiagnosisEventsPage,
  getJfrFilesPage,
  getJfrStatus,
  getOperationSamplesPage,
  getRecentTracesPage,
  getSlowSamplesPage,
  getTraceJfrSelection,
  startJfrRecording,
  stopJfrRecording
} from './api'

vi.mock('@/api/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), delete: vi.fn() }
}))

describe('runtime diagnosis history api', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(request.get).mockResolvedValue({ data: { code: 200, message: 'ok', data: { items: [], hasMore: false } } })
  })

  it('uses server-side time windows and cursors for every history list', async () => {
    const query = { hours: 168, before: '2026-10-14T02:00:00', size: 50, keyword: 'trace-42', traceOnly: true }
    await getDiagnosisEventsPage(query)
    await getSlowSamplesPage(query)
    await getDiagnosisErrorsPage(query)
    await getRecentTracesPage(query)
    await getOperationSamplesPage(query)
    await getJfrFilesPage(query)

    for (const path of ['events', 'slow', 'errors']) {
      expect(request.get).toHaveBeenCalledWith(`/monitor/diagnosis/${path}/page`, { params: query })
    }
    for (const path of ['traces', 'operations']) {
      expect(request.get).toHaveBeenCalledWith(`/monitor/diagnosis/${path}/page`, {
        params: { hours: query.hours, before: query.before, size: query.size, keyword: query.keyword }
      })
    }
    expect(request.get).toHaveBeenCalledWith('/monitor/diagnosis/jfr/files/page', {
      params: { hours: query.hours, before: query.before, size: query.size }
    })
  })

  it('passes trace and time bounds into JFR analysis', async () => {
    vi.mocked(request.get).mockResolvedValueOnce({ data: { code: 200, message: 'ok', data: { operations: [] } } })
    await analyzeJfr({ fileName: 'runtime.jfr', traceId: 'trace-1', from: '2026-10-14T02:00:00Z', to: '2026-10-14T02:00:01Z' })

    expect(request.get).toHaveBeenCalledWith('/monitor/diagnosis/jfr/analysis', {
      params: {
        fileName: 'runtime.jfr',
        traceId: 'trace-1',
        from: '2026-10-14T02:00:00Z',
        to: '2026-10-14T02:00:01Z',
        limit: 20
      }
    })
  })

  it('sends every selected segment to the bounded server-side JFR window analysis', async () => {
    vi.mocked(request.get).mockResolvedValueOnce({ data: { code: 200, message: 'ok', data: { operations: [] } } })
    await analyzeJfrWindow({
      fileNames: ['runtime-1.jfr', 'runtime-2.jfr'],
      traceId: 'trace-1',
      from: '2026-10-14T02:00:00Z',
      to: '2026-10-14T02:00:01Z'
    })

    expect(request.get).toHaveBeenCalledWith('/monitor/diagnosis/jfr/analysis/window', {
      params: {
        fileNames: 'runtime-1.jfr,runtime-2.jfr',
        traceId: 'trace-1',
        from: '2026-10-14T02:00:00Z',
        to: '2026-10-14T02:00:01Z',
        limit: 20
      }
    })
  })

  it('asks the server to resolve retained JFR files for a trace', async () => {
    vi.mocked(request.get).mockResolvedValueOnce({ data: { code: 200, message: 'ok', data: { traceId: 'trace/1', files: [] } } })

    await getTraceJfrSelection('trace/1')

    expect(request.get).toHaveBeenCalledWith('/monitor/diagnosis/jfr/files/for-trace/trace%2F1')
  })

  it('controls a bounded JFR recording session', async () => {
    vi.mocked(request.get).mockResolvedValueOnce({ data: { code: 200, message: 'ok', data: { running: false } } })
    vi.mocked(request.post).mockResolvedValue({ data: { code: 200, message: 'ok', data: { running: true } } })

    await getJfrStatus()
    await startJfrRecording(60)
    await stopJfrRecording()

    expect(request.get).toHaveBeenCalledWith('/monitor/diagnosis/jfr/recording/status')
    expect(request.post).toHaveBeenCalledWith('/monitor/diagnosis/jfr/recording/start', null, { params: { durationMinutes: 60 } })
    expect(request.post).toHaveBeenCalledWith('/monitor/diagnosis/jfr/recording/stop')
  })
})

import { readFileSync } from 'node:fs'
import { fileURLToPath, URL } from 'node:url'
import { describe, expect, it } from 'vitest'

const viewSource = readFileSync(fileURLToPath(new URL('./RuntimeDiagnosisView.vue', import.meta.url)), 'utf-8')

describe('runtime diagnosis workbench', () => {
  it('keeps only the three daily diagnosis views', () => {
    for (const tab of ['Metrics', 'Slow', 'Errors']) {
      expect(viewSource).toContain(`label="${tab}"`)
    }
    expect(viewSource).not.toContain('<el-tab-pane name="events">')
    expect(viewSource).not.toContain('<el-tab-pane name="traces">')
    expect(viewSource).toContain('统一筛选')
    expect(viewSource).toContain('>刷新</el-button>')
  })

  it('offers explicit Table and Trend modes over the same Metrics surface', () => {
    expect(viewSource).toContain('v-model="metricView"')
    expect(viewSource).toContain('value="table">Table')
    expect(viewSource).toContain('value="trend">Trend')
    expect(viewSource).toContain("metricView === 'table'")
    expect(viewSource).toContain('指标历史趋势')
    expect(viewSource).toContain(':data="pagedMetrics"')
    expect(viewSource).toContain('formatMetricMeasurements(row.measurements, row)')
    expect(viewSource).not.toContain('慢错样本统计')
    expect(viewSource).not.toContain('getRuntimeStatistics')
    expect(viewSource).not.toContain('实时 Metrics')
    expect(viewSource).not.toContain('sampledStatisticsCoverage')
    expect(viewSource).toContain("if (activeTab.value === 'metrics' && metricView.value === 'trend')")
    expect(viewSource).toContain('metricHistoryRequestGuard.isCurrent(version)')
    expect(viewSource).not.toContain('关联 Events')
  })

  it('links slow and error samples into retained traces and operation resources', () => {
    expect(viewSource).toContain('查看 Trace')
    expect(viewSource).toContain('@click="openTrace(row.traceId)"')
    expect(viewSource).toContain('Trace 关联诊断数据')
    expect(viewSource).toContain('selectedTraceOperations')
    expect(viewSource).toContain('子调用汇总')
    expect(viewSource).toContain('事务与 SQL Span')
    expect(viewSource).toContain('用 JFR 深入分析')
    expect(viewSource).toContain("path: '/monitor/advanced-diagnosis'")
    expect(viewSource).toContain('query.from =')
    expect(viewSource).toContain('query.to =')
  })

  it('shows lock, contention and stack evidence in correlated trace operations', () => {
    const sectionStart = viewSource.indexOf('<template v-if="selectedTraceOperations.length">')
    const sectionEnd = viewSource.indexOf('<template v-if="selectedTraceErrors.length">', sectionStart)
    const operationSection = viewSource.slice(sectionStart, sectionEnd)

    expect(operationSection).toContain('<el-table-column type="expand">')
    expect(operationSection).toContain("row.lockInfo || '无锁等待或未触发慢/错误详情采集'")
    expect(operationSection).toContain("row.stackTrace || '仅对慢操作和错误操作保留堆栈'")
    expect(operationSection).toContain("known(row.blockedTimeMs, ' ms')")
    expect(operationSection).toContain("known(row.waitedTimeMs, ' ms')")
    expect(viewSource).toContain('父子操作资源为包含关系')
  })

  it('keeps advanced JVM and JFR controls off the daily diagnosis page', () => {
    expect(viewSource).not.toContain('JFR 近期深度取证')
    expect(viewSource).not.toContain('JVM 线程、死锁与运行池')
    expect(viewSource).not.toContain('captureHeapHistogram')
    expect(viewSource).not.toContain('downloadJfrFile')
  })

  it('uses server history windows, cursors and independent load-more controls', () => {
    expect(viewSource).toContain('reloadHistoryForActiveTab')
    expect(viewSource).toContain('keyword: query.keyword.trim() || undefined')
    expect(viewSource).toContain('scheduleHistorySearch')
    expect(viewSource).toContain("isHistoryFilterCurrent('slow') ? slow.value : []")
    expect(viewSource).toContain("isHistoryFilterCurrent('errors') ? errors.value : []")
    expect(viewSource).toContain('Paged history is already filtered by the server')
    expect(viewSource).toContain('historyRequestVersion++')
    for (const key of ['slow', 'errors']) {
      expect(viewSource).toContain(`loadMoreHistory('${key}')`)
    }
    expect(viewSource).not.toContain("loadMoreHistory('events')")
    expect(viewSource).not.toContain("loadMoreHistory('traces')")
    expect(viewSource).not.toContain("loadMoreHistory('operations')")
  })

  it('loads only the visible tab data during a full refresh', () => {
    expect(viewSource).toContain('const tab = activeTab.value')
    expect(viewSource).toContain('const historyKeys = historyKeysForTab(tab)')
    expect(viewSource).toContain("if (tab === 'metrics')")
    expect(viewSource).toContain('historyKeys.forEach((key) => {')
    expect(viewSource).not.toContain("if (tab === 'traces')")
  })

  it('removes the dashboard and persistent storage detail blocks', () => {
    expect(viewSource).not.toContain('aria-label="运行诊断概览"')
    expect(viewSource).not.toContain('aria-label="诊断本地存储状态"')
    expect(viewSource).not.toContain('落盘目录')
    expect(viewSource).toContain('storage.droppedWrites')
    expect(viewSource).toContain('storage.byteBudgetDroppedWrites')
    expect(viewSource).toContain('摘要为部分数据')
  })

  it('does not poll the removed standalone Trace view', () => {
    expect(viewSource).not.toContain('createRuntimeDiagnosisPolling')
    expect(viewSource).not.toContain('getActiveOperations')
    expect(viewSource).not.toContain("activeTab.value === 'traces'")
  })

  it('guards asynchronous metric history and trace selection from stale responses', () => {
    expect(viewSource).toContain('selectedMetricSeries.value !== seriesKey')
    expect(viewSource).toContain('traceSelectionRequestGuard.isCurrent(version)')
    expect(viewSource).toContain('selectedTrace.value = undefined')
    expect(viewSource).toContain('traceDrawer.value = false')
    expect(viewSource).toContain('traceLoading.value = true')
  })
})

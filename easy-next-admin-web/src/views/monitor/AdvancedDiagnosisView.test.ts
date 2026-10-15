import { readFileSync } from 'node:fs'
import { fileURLToPath, URL } from 'node:url'
import { describe, expect, it } from 'vitest'

const viewSource = readFileSync(fileURLToPath(new URL('./AdvancedDiagnosisView.vue', import.meta.url)), 'utf-8')

describe('advanced diagnosis view', () => {
  it('keeps JFR and JVM tools outside the daily diagnosis surfaces', () => {
    expect(viewSource).toContain('<h1>高级诊断</h1>')
    expect(viewSource).toContain('JFR 录制与分析')
    expect(viewSource).toContain('JVM 现场')
    for (const dailyView of ['Metrics', 'Events', 'Slow', 'Errors', 'Trace']) {
      expect(viewSource).not.toContain(`>${dailyView} <`)
    }
  })

  it('offers bounded JFR recording control with an explicit 24 hour maximum', () => {
    expect(viewSource).toContain('JFR 默认关闭')
    expect(viewSource).toContain('最长 24 小时')
    expect(viewSource).toContain('currentJfrStatus.value?.maxSessionMinutes ?? 1440')
    expect(viewSource).toContain('recordingDurationOptions')
    expect(viewSource).toContain('Math.min(recordingDurationMinutes.value, effectiveMaxSessionMinutes.value)')
    expect(viewSource).toContain('startJfrRecording(recordingDurationMinutes.value)')
    expect(viewSource).toContain('if (recorderStatus.value.running)')
    expect(viewSource).toContain('recorderStatus.value.autoStopAt')
    expect(viewSource).toContain("recorderStatus.value.lastError")
    expect(viewSource).toContain('stopJfrRecording()')
    expect(viewSource).toContain('PermissionCodes.monitor.diagnosisJfrControl')
    expect(viewSource).toContain('createRuntimeDiagnosisPolling')
    expect(viewSource).toContain('pollJfrStatus')
    expect(viewSource).toContain('wasRunning && !status.running')
    expect(viewSource).toContain('jfrFilesRefreshPending')
    expect(viewSource).toContain("document.addEventListener('visibilitychange'")
    expect(viewSource).toContain('jfrStatusPolling.stop()')
    expect(viewSource).toContain('保留与估算跨度')
    expect(viewSource).not.toContain('保留与覆盖')
  })

  it('reports a stop as successful only after the recorder actually stopped', () => {
    expect(viewSource).toContain('if (status.running)')
    expect(viewSource).toContain("status.lastError || 'JFR 录制仍在运行，停止失败'")
    expect(viewSource).toContain("ElMessage.success('JFR 录制已停止')")
  })

  it('explains JFR attribution and retains explicit high-impact capture controls', () => {
    expect(viewSource).toContain('GC Pause、I/O')
    expect(viewSource).toContain('JDK JFR 的 CPU、分配、锁/Park、GC 与 I/O 仅是同 JVM、同时间窗内的旁证')
    expect(viewSource).toContain('只有项目自定义 RuntimeOperation 事件可按 traceId 精确关联')
    expect(viewSource).toContain('PermissionCodes.monitor.diagnosisCapture')
    expect(viewSource).toContain('可能产生 JVM 安全点停顿')
    expect(viewSource).toContain('PermissionCodes.monitor.diagnosisJfrDownload')
  })

  it('resolves the route trace window and analyzes every covering JFR segment', () => {
    expect(viewSource).toContain('useRoute()')
    expect(viewSource).toContain('route.query.traceId')
    expect(viewSource).toContain('route.query.from')
    expect(viewSource).toContain('route.query.to')
    expect(viewSource).toContain('getTraceJfrSelection(traceId)')
    expect(viewSource).toContain('analyzeJfrWindow({ fileNames, traceId, from, to })')
    expect(viewSource).toContain('已按 traceId 联合分析')
    expect(viewSource).toContain('jfrAnalysisRequestGuard.isCurrent(version)')
  })
})

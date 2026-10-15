<template>
  <section class="resource-page advanced-diagnosis-page">
    <div class="resource-hero">
      <div>
        <h1>高级诊断</h1>
        <p>按需开启 JFR 深度取证，或抓取 JVM 线程、死锁、运行池和堆对象现场。</p>
      </div>
      <div class="advanced-hero-actions">
        <span>页面刷新 {{ refreshedTime }}</span>
        <el-button type="primary" :icon="Refresh" :loading="loading" @click="refreshAdvanced">刷新</el-button>
      </div>
    </div>

    <el-alert
      class="advanced-scope-alert"
      type="info"
      :closable="false"
      show-icon
      title="高级诊断用于短时问题取证。JFR 默认关闭，单次录制最长 24 小时；日常 Metrics、Slow、Errors 和关联 Trace 请在运行诊断查看。"
    />

    <div class="resource-metrics is-four" aria-label="高级诊断概览">
      <div class="resource-metric" :class="currentJfrStatus?.running ? 'is-warning' : ''">
        <span>JFR 录制</span>
        <strong>{{ jfrStatusLabel }}</strong>
        <small>{{ currentJfrStatus?.autoStopAt ? `自动停止 ${formatDiagnosisTime(currentJfrStatus.autoStopAt)}` : '需要时手动开启' }}</small>
      </div>
      <div class="resource-metric">
        <span>JFR 本地文件</span>
        <strong>{{ currentJfrStatus?.completedFiles ?? jfrFiles.length }}</strong>
        <small>{{ bytes(currentJfrStatus?.totalBytes ?? 0) }} / {{ bytes(currentJfrStatus?.maxTotalBytes ?? 0) }}</small>
      </div>
      <div class="resource-metric">
        <span>线程快照</span>
        <strong>{{ jvmThreads?.totalThreads ?? '-' }}</strong>
        <small>{{ jvmThreads ? `已返回 ${jvmThreads.returnedThreads} 个线程` : '尚未抓取' }}</small>
      </div>
      <div class="resource-metric" :class="jvmDeadlocks?.detected ? 'is-danger' : ''">
        <span>JVM 死锁</span>
        <strong>{{ jvmDeadlocks?.count ?? '-' }}</strong>
        <small>{{ jvmDeadlocks ? formatDiagnosisTime(jvmDeadlocks.sampledAt) : '尚未检测' }}</small>
      </div>
    </div>

    <div v-if="errorMessage" class="inline-error advanced-error">
      <span>{{ errorMessage }}</span>
      <el-button link type="primary" :loading="loading" @click="refreshAdvanced">重新加载</el-button>
    </div>

    <section class="surface resource-panel advanced-panel">
      <el-tabs v-model="activeTab" class="advanced-tabs">
        <el-tab-pane label="JFR 录制与分析" name="jfr" />
        <el-tab-pane label="JVM 现场" name="jvm" />
      </el-tabs>

      <div v-if="activeTab === 'jfr'" class="advanced-content">
        <section class="recording-console">
          <div>
            <h2>按需录制</h2>
            <p>录制 CPU、对象分配、锁与 Park、GC Pause、I/O，以及带 traceId 的自定义运行事件。</p>
          </div>
          <div class="recording-actions">
            <el-select v-model="recordingDurationMinutes" aria-label="JFR 录制时长" :disabled="!currentJfrStatus?.available || currentJfrStatus?.running" class="duration-select">
              <el-option v-for="option in recordingDurationOptions" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
            <el-button
              v-permission:disable="{ permissions: PermissionCodes.monitor.diagnosisJfrControl, reason: '缺少 JFR 录制控制权限' }"
              type="primary"
              :loading="recordingActionLoading"
              :disabled="!currentJfrStatus?.available || currentJfrStatus?.running"
              @click="startRecording"
            >开启录制</el-button>
            <el-button
              v-permission:disable="{ permissions: PermissionCodes.monitor.diagnosisJfrControl, reason: '缺少 JFR 录制控制权限' }"
              :loading="recordingActionLoading"
              :disabled="!currentJfrStatus?.running"
              @click="stopRecording"
            >停止录制</el-button>
          </div>
        </section>

        <div v-if="currentJfrStatus" class="jfr-recorder-status" aria-label="JFR 本地录制状态">
          <span><strong>录制状态</strong><el-tag :type="jfrStatusTag" effect="plain">{{ jfrStatusLabel }}</el-tag></span>
          <span><strong>本次会话</strong>{{ currentJfrStatus.sessionStartedAt ? formatDiagnosisTime(currentJfrStatus.sessionStartedAt) : '-' }}<template v-if="currentJfrStatus.autoStopAt"> → {{ formatDiagnosisTime(currentJfrStatus.autoStopAt) }}</template></span>
          <span><strong>保留与估算跨度</strong>上限 {{ currentJfrStatus.retentionDays }} 天 · 估算 {{ durationHours(currentJfrStatus.coverageHours) }}</span>
          <span><strong>本地容量</strong>{{ bytes(currentJfrStatus.totalBytes) }} / {{ bytes(currentJfrStatus.maxTotalBytes) }}</span>
        </div>
        <el-alert v-if="jfrWarning" class="advanced-warning" type="warning" :closable="false" show-icon :title="jfrWarning" />
        <el-alert
          v-if="traceJfrContext"
          v-loading="traceJfrLoading"
          class="advanced-warning trace-jfr-context"
          :type="traceJfrContext.type"
          :closable="false"
          show-icon
          :title="traceJfrContext.message"
        >
          <template #default>
            <span class="trace-jfr-window">Trace {{ traceJfrContext.traceId }}<template v-if="traceJfrContext.from && traceJfrContext.to"> · {{ formatDiagnosisTime(traceJfrContext.from) }} → {{ formatDiagnosisTime(traceJfrContext.to) }}</template></span>
          </template>
        </el-alert>

        <div class="table-control-row jfr-toolbar">
          <el-form :inline="true" class="filter-bar">
            <el-form-item label="录制范围">
              <el-select v-model="jfrWindowHours" class="window-select" :loading="jfrHistory.loading" @change="reloadJfrFiles">
                <el-option label="最近 1 小时" :value="1" />
                <el-option label="最近 24 小时" :value="24" />
                <el-option label="最近 7 天" :value="168" />
                <el-option label="最近 30 天" :value="720" />
              </el-select>
            </el-form-item>
          </el-form>
          <span>已加载 {{ jfrFiles.length }} 个分段</span>
        </div>

        <el-table v-loading="loading || jfrHistory.loading" :data="jfrFiles" row-key="fileName" class="admin-table" empty-text="暂无 JFR 录制分段">
          <el-table-column prop="fileName" label="文件" min-width="280" />
          <el-table-column label="录制时间" min-width="320"><template #default="{ row }">{{ formatDiagnosisTime(row.recordingStartedAt) }} → {{ formatDiagnosisTime(row.recordingEndedAt || row.modifiedAt) }}</template></el-table-column>
          <el-table-column label="大小" width="130"><template #default="{ row }">{{ bytes(row.sizeBytes) }}</template></el-table-column>
          <el-table-column label="操作" width="190"><template #default="{ row }"><el-button link type="primary" :loading="jfrAnalysisLoading" @click="analyzeJfrFile(row.fileName)">应用内分析</el-button><el-button v-permission:disable="{ permissions: PermissionCodes.monitor.diagnosisJfrDownload, reason: '缺少 JFR 原始文件下载权限' }" link @click="downloadJfr(row.fileName)">下载</el-button></template></el-table-column>
        </el-table>
        <div class="table-footer is-split">
          <span>JFR 文件受容量上限和保留时间共同约束</span>
          <el-button v-if="jfrHistory.hasMore" :loading="jfrHistory.loading" @click="loadMoreJfrFiles">加载更多</el-button>
          <span v-else>当前时间范围已全部加载</span>
        </div>

        <section v-if="jfrAnalysis" class="jfr-analysis-panel">
          <div class="subsection-head"><div><h2>JFR 应用内分析</h2><p>{{ jfrAnalysis.fileName || `${traceJfrContext?.fileNames.length || 0} 个关联分段` }} · 扫描 {{ jfrAnalysis.scannedEvents }} 个事件 · {{ jfrAnalysis.message }}</p></div><el-tag :type="jfrAnalysis.status === 'READY' ? 'success' : jfrAnalysis.status === 'PARTIAL' ? 'warning' : 'danger'">{{ jfrAnalysis.status }}</el-tag></div>
          <el-alert type="info" :closable="false" show-icon title="JDK JFR 的 CPU、分配、锁/Park、GC 与 I/O 仅是同 JVM、同时间窗内的旁证；只有项目自定义 RuntimeOperation 事件可按 traceId 精确关联。" />
          <el-tabs v-model="jfrAnalysisTab" class="analysis-tabs">
            <el-tab-pane name="operations" :label="`运行事件 ${jfrAnalysis.operations.length}`">
              <el-table :data="jfrAnalysis.operations" class="admin-table" empty-text="该分段没有自定义运行事件">
                <el-table-column label="类型" width="100"><template #default="{ row }"><el-tag effect="plain">{{ operationTypeLabel(row.type) }}</el-tag></template></el-table-column>
                <el-table-column prop="operation" label="操作" min-width="220" show-overflow-tooltip />
                <el-table-column prop="detail" label="详情" min-width="230" show-overflow-tooltip />
                <el-table-column label="结果" width="110"><template #default="{ row }"><el-tag :type="outcomeTag(row.outcome)">{{ row.outcome }}</el-tag></template></el-table-column>
                <el-table-column prop="errorType" label="异常类型" min-width="190" show-overflow-tooltip><template #default="{ row }">{{ row.errorType || '-' }}</template></el-table-column>
                <el-table-column label="耗时 / CPU" width="170"><template #default="{ row }">{{ nanos(row.durationNanos) }} / {{ known(row.cpuTimeMs) }} ms</template></el-table-column>
                <el-table-column label="线程分配" width="130"><template #default="{ row }">{{ bytes(row.allocatedBytes) }}</template></el-table-column>
                <el-table-column prop="traceId" label="Trace ID" min-width="190" show-overflow-tooltip />
              </el-table>
            </el-tab-pane>
            <el-tab-pane v-for="section in jfrAggregateSections" :key="section.key" :name="section.key" :label="`${section.label} ${section.rows.length}`">
              <el-table :data="section.rows" class="admin-table" empty-text="该 JFR 分段没有采集到对应事件">
                <el-table-column prop="key" label="热点 / 类 / 锁" min-width="300" show-overflow-tooltip />
                <el-table-column prop="detail" label="调用位置 / 原因" min-width="260" show-overflow-tooltip />
                <el-table-column prop="count" label="样本数" width="100" />
                <el-table-column label="估算分配" width="130"><template #default="{ row }">{{ row.estimatedBytes > 0 ? bytes(row.estimatedBytes) : '-' }}</template></el-table-column>
                <el-table-column label="累计 / 最大耗时" width="180"><template #default="{ row }">{{ row.totalDurationNanos > 0 ? `${nanos(row.totalDurationNanos)} / ${nanos(row.maxDurationNanos)}` : '-' }}</template></el-table-column>
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </section>
      </div>

      <div v-else class="advanced-content">
        <div class="subsection-head">
          <div><h2>JVM 线程、死锁与运行池</h2><p>抓取当前线程 CPU、线程分配、锁拥有者和连接池/线程池饱和状态。</p></div>
          <div class="runtime-actions">
            <el-button type="primary" :loading="jvmLoading" @click="loadJvmDiagnostics">抓取现场</el-button>
            <el-button v-permission:disable="{ permissions: PermissionCodes.monitor.diagnosisCapture, reason: '缺少 JVM 现场抓取权限' }" type="warning" plain :loading="heapHistogramLoading" :disabled="heapHistogramCapability?.supported === false" @click="loadHeapHistogram">生成类直方图</el-button>
          </div>
        </div>
        <el-alert type="warning" :closable="false" show-icon title="类直方图会检查堆对象，可能产生 JVM 安全点停顿；请避开业务高峰。" />

        <section v-if="runtimePools" class="runtime-block" aria-label="运行池现场">
          <div class="runtime-title"><strong>连接池与线程池</strong><span>抓取时间 {{ formatDiagnosisTime(runtimePools.sampledAt) }}</span></div>
          <el-table :data="runtimePools.pools" class="admin-table" empty-text="暂无连接池或线程池指标">
            <el-table-column prop="type" label="类型" width="110" /><el-table-column prop="name" label="资源池" min-width="180" />
            <el-table-column label="活跃 / 最大" width="150"><template #default="{ row }">{{ optionalNumber(row.active) }} / {{ optionalNumber(row.max) }}</template></el-table-column>
            <el-table-column label="空闲 / 等待" width="150"><template #default="{ row }">{{ optionalNumber(row.idle) }} / {{ optionalNumber(row.pending) }}</template></el-table-column>
            <el-table-column label="利用率" width="120"><template #default="{ row }"><el-tag :type="poolTag(row.utilizationPercent)">{{ optionalPercent(row.utilizationPercent) }}</el-tag></template></el-table-column>
          </el-table>
        </section>

        <section v-if="jvmDeadlocks" class="runtime-block" aria-label="死锁检测结果">
          <div class="runtime-title"><strong>死锁关系</strong><span>抓取时间 {{ formatDiagnosisTime(jvmDeadlocks.sampledAt) }}</span></div>
          <el-alert :type="jvmDeadlocks.detected ? 'error' : 'success'" :closable="false" show-icon :title="jvmDeadlocks.detected ? `发现 ${jvmDeadlocks.count} 个死锁线程` : '未发现 JVM 死锁'" />
          <el-table v-if="jvmDeadlocks.detected" :data="jvmDeadlocks.threads" row-key="id" class="admin-table">
            <el-table-column type="expand"><template #default="{ row }"><div class="thread-detail"><div><strong>当前持有的锁</strong><pre>{{ threadLocks(row) }}</pre></div><div><strong>线程堆栈</strong><pre>{{ threadStack(row) }}</pre></div></div></template></el-table-column>
            <el-table-column label="线程" min-width="220"><template #default="{ row }"><strong>#{{ row.id }} {{ row.name }}</strong></template></el-table-column>
            <el-table-column prop="state" label="状态" width="110" /><el-table-column label="正在等待" min-width="220"><template #default="{ row }">{{ row.waitingOn?.label || '-' }}</template></el-table-column>
            <el-table-column label="锁拥有者" min-width="220"><template #default="{ row }">{{ lockOwnerLabel(row) }}</template></el-table-column>
          </el-table>
        </section>

        <section v-if="jvmThreads" class="runtime-block" aria-label="JVM 线程快照">
          <div class="runtime-title"><strong>线程快照</strong><span>返回 {{ jvmThreads.returnedThreads }} / {{ jvmThreads.totalThreads }}</span></div>
          <el-table :data="jvmThreads.threads" row-key="id" class="admin-table" max-height="520">
            <el-table-column type="expand"><template #default="{ row }"><div class="thread-detail"><div><strong>当前持有的 Monitor / Synchronizer</strong><pre>{{ threadLocks(row) }}</pre></div><div><strong>线程堆栈</strong><pre>{{ threadStack(row) }}</pre></div></div></template></el-table-column>
            <el-table-column prop="name" label="线程" min-width="230" show-overflow-tooltip />
            <el-table-column label="状态" width="120"><template #default="{ row }"><el-tag :type="row.deadlocked ? 'danger' : row.state === 'BLOCKED' ? 'warning' : 'info'">{{ row.state }}</el-tag></template></el-table-column>
            <el-table-column label="CPU" width="120"><template #default="{ row }">{{ nanos(row.cpuTimeNanos) }}</template></el-table-column>
            <el-table-column label="线程分配" width="120"><template #default="{ row }">{{ bytes(row.allocatedBytes ?? -1) }}</template></el-table-column>
            <el-table-column label="阻塞 / 等待" width="140"><template #default="{ row }">{{ row.blockedCount }} / {{ row.waitedCount }}</template></el-table-column>
            <el-table-column label="等待锁" min-width="220"><template #default="{ row }">{{ row.waitingOn?.label || '-' }}</template></el-table-column>
            <el-table-column label="锁拥有者" min-width="190"><template #default="{ row }">{{ lockOwnerLabel(row) }}</template></el-table-column>
          </el-table>
        </section>

        <section v-if="heapHistogram || heapHistogramCapability?.supported === false" class="runtime-block" aria-label="JVM 类直方图">
          <div class="runtime-title"><strong>堆对象类直方图</strong><span v-if="heapHistogram">{{ heapHistogram.returnedClasses }} / {{ heapHistogram.totalClasses }} 类</span></div>
          <el-alert v-if="heapHistogram?.status !== 'READY'" :type="heapHistogram?.status === 'FAILED' ? 'error' : 'warning'" :closable="false" show-icon :title="heapHistogram?.message || heapHistogramCapability?.message || '当前 JVM 不支持类直方图'" />
          <el-table v-if="heapHistogram?.status === 'READY'" :data="heapHistogram.classes" row-key="rank" class="admin-table" max-height="520">
            <el-table-column prop="rank" label="#" width="70" /><el-table-column prop="className" label="类名" min-width="300" show-overflow-tooltip />
            <el-table-column prop="module" label="模块" min-width="180"><template #default="{ row }">{{ row.module || '-' }}</template></el-table-column>
            <el-table-column label="实例数" width="140" align="right"><template #default="{ row }">{{ formatNumber(row.instances) }}</template></el-table-column>
            <el-table-column label="浅堆大小" width="140" align="right"><template #default="{ row }">{{ bytes(row.bytes) }}</template></el-table-column>
          </el-table>
        </section>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { analyzeJfr, analyzeJfrWindow, captureHeapHistogram, downloadJfrFile, getHeapHistogramCapability, getJfrFilesPage, getJfrStatus, getJvmDeadlocks, getJvmThreads, getRuntimePools, getTraceJfrSelection, startJfrRecording, stopJfrRecording } from '@/features/monitor/api'
import { formatDiagnosisTime, operationTypeLabel } from '@/features/monitor/diagnosisPresentation'
import { preferredJfrAnalysisTab, toJfrAggregateSections } from '@/features/monitor/diagnosisJfr'
import { createLatestRequestGuard, createRuntimeDiagnosisPolling } from '@/features/monitor/runtimeDiagnosisPolling'
import type { DeadlockReport, HeapHistogram, HeapHistogramCapability, JfrAnalysis, JfrFile, JfrRecorderStatus, PoolDiagnostics, ThreadDump, ThreadSnapshot } from '@/features/monitor/types'
import { PermissionCodes } from '@/permissions/codes'

type AdvancedTab = 'jfr' | 'jvm'
interface TraceJfrContext {
  traceId: string
  from?: string
  to?: string
  fileNames: string[]
  type: 'success' | 'warning' | 'error' | 'info'
  message: string
}

const route = useRoute()
const activeTab = ref<AdvancedTab>('jfr')
const loading = ref(false)
const recordingActionLoading = ref(false)
const jfrAnalysisLoading = ref(false)
const jvmLoading = ref(false)
const heapHistogramLoading = ref(false)
const errorMessage = ref('')
const refreshedAt = ref('')
const recorderStatus = ref<JfrRecorderStatus>()
const recordingDurationMinutes = ref(60)
const jfrWindowHours = ref(24)
const jfrFiles = ref<JfrFile[]>([])
const jfrHistory = reactive({ loading: false, hasMore: false, nextBefore: undefined as string | undefined })
const jfrAnalysis = ref<JfrAnalysis>()
const jfrAnalysisTab = ref('operations')
const traceJfrLoading = ref(false)
const traceJfrContext = ref<TraceJfrContext>()
const jvmThreads = ref<ThreadDump>()
const jvmDeadlocks = ref<DeadlockReport>()
const runtimePools = ref<PoolDiagnostics>()
const heapHistogramCapability = ref<HeapHistogramCapability>()
const heapHistogram = ref<HeapHistogram>()
let disposed = false
const jfrStatusRequestGuard = createLatestRequestGuard()
const jfrFilesRequestGuard = createLatestRequestGuard()
const jfrAnalysisRequestGuard = createLatestRequestGuard()
const jvmSnapshotRequestGuard = createLatestRequestGuard()
const heapHistogramRequestGuard = createLatestRequestGuard()
let jfrFilesRefreshPending = false

const jfrStatusPolling = createRuntimeDiagnosisPolling({
  intervalMs: 10_000,
  shouldPoll: () => !disposed && activeTab.value === 'jfr' && !document.hidden && !loading.value && !recordingActionLoading.value,
  poll: pollJfrStatus
})

const baseRecordingDurationOptions = [15, 30, 60, 360, 720, 1440]

const currentJfrStatus = computed(() => recorderStatus.value)
const effectiveMaxSessionMinutes = computed(() => Math.max(1, Math.min(currentJfrStatus.value?.maxSessionMinutes ?? 1440, 1440)))
const recordingDurationOptions = computed(() => {
  const maximum = effectiveMaxSessionMinutes.value
  const values = baseRecordingDurationOptions.filter((value) => value <= maximum)
  if (!values.includes(maximum)) values.push(maximum)
  return values.sort((left, right) => left - right).map((value) => ({ value, label: durationMinutesLabel(value) }))
})
const refreshedTime = computed(() => refreshedAt.value ? formatDiagnosisTime(refreshedAt.value) : '-')
const jfrStatusLabel = computed(() => {
  const status = currentJfrStatus.value
  if (!status) return '加载中'
  if (!status.available) return '当前 JVM 不可用'
  return status.running ? '录制中' : '已关闭'
})
const jfrStatusTag = computed(() => currentJfrStatus.value?.running ? 'warning' : currentJfrStatus.value?.available ? 'info' : 'danger')
const jfrWarning = computed(() => {
  const status = currentJfrStatus.value
  if (!status) return ''
  if (!status.available) return status.lastError || '当前 JVM 不支持 JFR'
  if (status.capacityPressure) return `JFR 文件已用 ${bytes(status.totalBytes)} / ${bytes(status.maxTotalBytes)}，容量接近上限`
  if (status.lastError) return status.lastError
  return ''
})
const jfrAggregateSections = computed(() => toJfrAggregateSections(jfrAnalysis.value))
const routeTraceSignature = computed(() => [routeQueryText(route.query.traceId), routeQueryText(route.query.from), routeQueryText(route.query.to)].join('|'))

async function refreshAdvanced() {
  if (loading.value) return
  const statusVersion = jfrStatusRequestGuard.next()
  const filesVersion = jfrFilesRequestGuard.next()
  const hours = jfrWindowHours.value
  jfrHistory.loading = false
  loading.value = true
  errorMessage.value = ''
  const [statusResult, filesResult] = await Promise.allSettled([
    getJfrStatus(),
    getJfrFilesPage({ hours, size: 100 })
  ])
  if (disposed) return
  const failures: string[] = []
  if (jfrStatusRequestGuard.isCurrent(statusVersion)) {
    if (statusResult.status === 'fulfilled') {
      recorderStatus.value = statusResult.value
      recordingDurationMinutes.value = Math.min(recordingDurationMinutes.value, effectiveMaxSessionMinutes.value)
    }
    else failures.push('JFR 状态')
  }
  if (jfrFilesRequestGuard.isCurrent(filesVersion)) {
    if (filesResult.status === 'fulfilled') {
      applyJfrPage(filesResult.value, false)
      jfrFilesRefreshPending = false
    }
    else failures.push('JFR 文件')
  }
  refreshedAt.value = new Date().toISOString()
  errorMessage.value = failures.length ? `${failures.join('、')}加载失败` : ''
  loading.value = false
}

async function pollJfrStatus() {
  const version = jfrStatusRequestGuard.next()
  const wasRunning = recorderStatus.value?.running === true
  const status = await getJfrStatus()
  if (!jfrStatusRequestGuard.isCurrent(version) || disposed || document.hidden || activeTab.value !== 'jfr') return
  recorderStatus.value = status
  refreshedAt.value = new Date().toISOString()
  if (wasRunning && !status.running) jfrFilesRefreshPending = true
  if (jfrFilesRefreshPending) {
    await reloadJfrFiles(false)
  }
}

function syncJfrStatusPolling() {
  jfrStatusPolling.stop()
  if (!disposed && activeTab.value === 'jfr' && !document.hidden) jfrStatusPolling.start()
}

async function startRecording() {
  const version = jfrStatusRequestGuard.next()
  recordingActionLoading.value = true
  try {
    const status = await startJfrRecording(recordingDurationMinutes.value)
    if (!jfrStatusRequestGuard.isCurrent(version) || disposed) return
    recorderStatus.value = status
    if (recorderStatus.value.running) ElMessage.success(recorderStatus.value.autoStopAt
      ? `JFR 已开启，将于 ${formatDiagnosisTime(recorderStatus.value.autoStopAt)} 自动停止`
      : `JFR 已开启，最长录制 ${effectiveMaxSessionMinutes.value} 分钟`)
    else ElMessage.error(recorderStatus.value.lastError || (recorderStatus.value.available ? 'JFR 未进入录制状态' : '当前 JVM 不支持 JFR'))
  } catch {
    if (jfrStatusRequestGuard.isCurrent(version) && !disposed) ElMessage.error('JFR 录制启动失败')
  }
  finally { recordingActionLoading.value = false }
}

async function stopRecording() {
  const version = jfrStatusRequestGuard.next()
  recordingActionLoading.value = true
  try {
    const status = await stopJfrRecording()
    if (!jfrStatusRequestGuard.isCurrent(version) || disposed) return
    recorderStatus.value = status
    if (status.running) {
      ElMessage.error(status.lastError || 'JFR 录制仍在运行，停止失败')
      return
    }
    ElMessage.success('JFR 录制已停止')
    jfrFilesRefreshPending = true
    await reloadJfrFiles()
  } catch {
    if (jfrStatusRequestGuard.isCurrent(version) && !disposed) ElMessage.error('JFR 录制停止失败')
  }
  finally { recordingActionLoading.value = false }
}

function applyJfrPage(page: { items: JfrFile[]; hasMore: boolean; nextBefore?: string }, append: boolean) {
  jfrFiles.value = append ? [...jfrFiles.value, ...page.items] : page.items
  Object.assign(jfrHistory, { hasMore: page.hasMore, nextBefore: page.nextBefore, loading: false })
}

async function reloadJfrFiles(showError = true) {
  const version = jfrFilesRequestGuard.next()
  const hours = jfrWindowHours.value
  jfrHistory.loading = true
  try {
    const page = await getJfrFilesPage({ hours, size: 100 })
    if (jfrFilesRequestGuard.isCurrent(version) && !disposed) {
      applyJfrPage(page, false)
      jfrFilesRefreshPending = false
    }
  } catch {
    if (showError && jfrFilesRequestGuard.isCurrent(version) && !disposed) ElMessage.error('JFR 文件加载失败')
  } finally {
    if (jfrFilesRequestGuard.isCurrent(version)) jfrHistory.loading = false
  }
}

async function loadMoreJfrFiles() {
  if (!jfrHistory.hasMore || !jfrHistory.nextBefore || jfrHistory.loading) return
  const version = jfrFilesRequestGuard.next()
  const hours = jfrWindowHours.value
  const before = jfrHistory.nextBefore
  jfrHistory.loading = true
  try {
    const page = await getJfrFilesPage({ hours, before, size: 100 })
    if (jfrFilesRequestGuard.isCurrent(version) && !disposed) applyJfrPage(page, true)
  } catch {
    if (jfrFilesRequestGuard.isCurrent(version) && !disposed) ElMessage.error('JFR 历史加载失败')
  } finally {
    if (jfrFilesRequestGuard.isCurrent(version)) jfrHistory.loading = false
  }
}

async function analyzeJfrFile(fileName: string) {
  const version = jfrAnalysisRequestGuard.next()
  traceJfrLoading.value = false
  traceJfrContext.value = undefined
  jfrAnalysisLoading.value = true
  try {
    const analysis = await analyzeJfr({ fileName })
    if (!jfrAnalysisRequestGuard.isCurrent(version) || disposed) return
    jfrAnalysis.value = analysis
    jfrAnalysisTab.value = preferredJfrAnalysisTab(analysis)
    if (!['READY', 'PARTIAL'].includes(analysis.status)) ElMessage.warning(analysis.message)
  } catch {
    if (jfrAnalysisRequestGuard.isCurrent(version) && !disposed) ElMessage.error('JFR 应用内分析失败')
  } finally {
    if (jfrAnalysisRequestGuard.isCurrent(version)) jfrAnalysisLoading.value = false
  }
}

async function analyzeRouteTrace() {
  if (disposed) return
  const version = jfrAnalysisRequestGuard.next()
  const traceId = routeQueryText(route.query.traceId)
  if (!traceId) {
    jfrAnalysis.value = undefined
    jfrAnalysisLoading.value = false
    traceJfrContext.value = undefined
    traceJfrLoading.value = false
    return
  }
  const signature = routeTraceSignature.value
  activeTab.value = 'jfr'
  jfrAnalysisLoading.value = false
  traceJfrLoading.value = true
  jfrAnalysis.value = undefined
  traceJfrContext.value = {
    traceId,
    from: normalizedTimestamp(route.query.from),
    to: normalizedTimestamp(route.query.to),
    fileNames: [],
    type: 'info',
    message: '正在定位覆盖该 Trace 的 JFR 分段'
  }
  try {
    const selection = await getTraceJfrSelection(traceId)
    if (!jfrAnalysisRequestGuard.isCurrent(version) || disposed || signature !== routeTraceSignature.value) return
    const from = selection?.from || normalizedTimestamp(route.query.from)
    const to = selection?.to || normalizedTimestamp(route.query.to)
    const files = selection?.files ?? []
    const fileNames = [...new Set(files.map((file) => file.fileName).filter(Boolean))]
    jfrFiles.value = [...files, ...jfrFiles.value.filter((file) => !fileNames.includes(file.fileName))]
    if (!from || !to) {
      traceJfrContext.value = { traceId, from, to, fileNames, type: 'warning', message: '该 Trace 已超过本地明细保留期，无法确定 JFR 分析时间窗' }
      return
    }
    if (!fileNames.length) {
      traceJfrContext.value = { traceId, from, to, fileNames, type: 'warning', message: '该 Trace 执行期间没有可用 JFR 分段；请先开启录制后复现问题' }
      return
    }
    traceJfrContext.value = { traceId, from, to, fileNames, type: 'info', message: `正在联合分析 ${fileNames.length} 个 JFR 分段` }
    const analysis = await analyzeJfrWindow({ fileNames, traceId, from, to })
    if (!jfrAnalysisRequestGuard.isCurrent(version) || disposed || signature !== routeTraceSignature.value) return
    jfrAnalysis.value = analysis
    jfrAnalysisTab.value = preferredJfrAnalysisTab(analysis)
    const ready = ['READY', 'PARTIAL'].includes(analysis.status)
    traceJfrContext.value = {
      traceId,
      from,
      to,
      fileNames,
      type: analysis.status === 'READY' ? 'success' : analysis.status === 'PARTIAL' ? 'warning' : 'error',
      message: ready ? `已按 traceId 联合分析 ${fileNames.length} 个 JFR 分段` : analysis.message
    }
    if (!ready) ElMessage.warning(analysis.message)
  } catch {
    if (jfrAnalysisRequestGuard.isCurrent(version) && !disposed && signature === routeTraceSignature.value) traceJfrContext.value = { traceId, from: normalizedTimestamp(route.query.from), to: normalizedTimestamp(route.query.to), fileNames: [], type: 'error', message: 'Trace 与 JFR 关联分析失败' }
  } finally {
    if (jfrAnalysisRequestGuard.isCurrent(version) && signature === routeTraceSignature.value) traceJfrLoading.value = false
  }
}

async function loadJvmDiagnostics() {
  const version = jvmSnapshotRequestGuard.next()
  jvmLoading.value = true
  const [threadsResult, deadlocksResult, poolsResult, capabilityResult] = await Promise.allSettled([getJvmThreads(), getJvmDeadlocks(), getRuntimePools(), getHeapHistogramCapability()])
  if (!jvmSnapshotRequestGuard.isCurrent(version) || disposed) return
  if (threadsResult.status === 'fulfilled') jvmThreads.value = threadsResult.value
  if (deadlocksResult.status === 'fulfilled') jvmDeadlocks.value = deadlocksResult.value
  if (poolsResult.status === 'fulfilled') runtimePools.value = poolsResult.value
  if (capabilityResult.status === 'fulfilled') heapHistogramCapability.value = capabilityResult.value
  const failures = [threadsResult, deadlocksResult, poolsResult].filter((item) => item.status === 'rejected').length
  if (failures) ElMessage.error(`${failures} 项 JVM 现场数据抓取失败`)
  jvmLoading.value = false
}

async function loadHeapHistogram() {
  const version = heapHistogramRequestGuard.next()
  if (!heapHistogramCapability.value) {
    try {
      const capability = await getHeapHistogramCapability()
      if (!heapHistogramRequestGuard.isCurrent(version) || disposed) return
      heapHistogramCapability.value = capability
    }
    catch {
      if (heapHistogramRequestGuard.isCurrent(version) && !disposed) ElMessage.error('无法探测当前 JVM 的类直方图能力')
      return
    }
  }
  if (!heapHistogramCapability.value.supported) { ElMessage.warning(heapHistogramCapability.value.message); return }
  try {
    await ElMessageBox.confirm('类直方图需要检查当前堆对象，可能产生 JVM 安全点停顿。建议避开业务高峰，确认现在执行吗？', '高开销 JVM 诊断', { confirmButtonText: '确认抓取', cancelButtonText: '取消', type: 'warning' })
  } catch { return }
  if (!heapHistogramRequestGuard.isCurrent(version) || disposed) return
  heapHistogramLoading.value = true
  try {
    const histogram = await captureHeapHistogram(100)
    if (!heapHistogramRequestGuard.isCurrent(version) || disposed) return
    heapHistogram.value = histogram
    if (heapHistogram.value.status === 'READY') ElMessage.success('JVM 类直方图抓取完成')
    else ElMessage.warning(heapHistogram.value.message)
  } catch {
    if (heapHistogramRequestGuard.isCurrent(version) && !disposed) ElMessage.error('JVM 类直方图抓取失败')
  }
  finally { if (heapHistogramRequestGuard.isCurrent(version)) heapHistogramLoading.value = false }
}

function formatNumber(value: number) { if (!Number.isFinite(value)) return '-'; return Number(value.toPrecision(6)).toLocaleString('zh-CN') }
function bytes(value: number) { if (!Number.isFinite(value) || value < 0) return '不可用'; if (value < 1024) return `${value} B`; if (value < 1024 ** 2) return `${(value / 1024).toFixed(1)} KB`; if (value < 1024 ** 3) return `${(value / 1024 ** 2).toFixed(1)} MB`; return `${(value / 1024 ** 3).toFixed(2)} GB` }
function nanos(value: number | undefined) { if (value == null || value < 0) return '不可用'; if (value < 1_000_000) return `${(value / 1_000).toFixed(1)} μs`; if (value < 1_000_000_000) return `${(value / 1_000_000).toFixed(1)} ms`; return `${(value / 1_000_000_000).toFixed(2)} s` }
function known(value: number) { return value < 0 ? '不可用' : formatNumber(value) }
function optionalNumber(value: number | undefined) { return value == null ? '-' : formatNumber(value) }
function optionalPercent(value: number | undefined) { return value == null ? '-' : `${value.toFixed(1)}%` }
function durationHours(value: number) { if (!Number.isFinite(value) || value <= 0) return '0 小时'; return value < 24 ? `${formatNumber(value)} 小时` : `${formatNumber(value / 24)} 天` }
function durationMinutesLabel(value: number) { if (value < 60) return `${value} 分钟`; if (value % 60 === 0) return `${value / 60} 小时`; return `${Math.floor(value / 60)} 小时 ${value % 60} 分钟` }
function poolTag(value: number | undefined) { if (value == null) return 'info'; if (value >= 90) return 'danger'; if (value >= 75) return 'warning'; return 'success' }
function outcomeTag(outcome: string) { const value = (outcome || '').toUpperCase(); if (['ERROR', 'FAILED', 'FAILURE', 'ROLLBACK'].some((item) => value.includes(item))) return 'danger'; if (['SUCCESS', 'OK', 'COMMIT'].some((item) => value.includes(item))) return 'success'; return 'info' }
function lockOwnerLabel(thread: ThreadSnapshot) { return thread.lockOwner ? `#${thread.lockOwner.id} ${thread.lockOwner.name}` : '-' }
function threadLocks(thread: ThreadSnapshot) { return [...(thread.lockedMonitors || []).map((lock) => `Monitor ${lock.label}${lock.lockedAt ? ` @ ${lock.lockedAt}` : ''}`), ...(thread.lockedSynchronizers || []).map((lock) => `Synchronizer ${lock.label}`)].join('\n') || '当前未持有 Monitor 或 Synchronizer' }
function threadStack(thread: ThreadSnapshot) { return thread.stackTrace.map((frame) => `${frame.className}.${frame.methodName}(${frame.fileName || 'Unknown Source'}:${frame.lineNumber})`).join('\n') || '没有可用堆栈' }
async function downloadJfr(fileName: string) { try { await downloadJfrFile(fileName) } catch { ElMessage.error('JFR 文件下载失败') } }
function routeQueryText(value: unknown) { if (typeof value === 'string') return value; if (Array.isArray(value) && typeof value[0] === 'string') return value[0]; return '' }
function normalizedTimestamp(value: unknown) { const raw = routeQueryText(value); const parsed = Date.parse(raw); return raw && Number.isFinite(parsed) ? new Date(parsed).toISOString() : undefined }

watch(routeTraceSignature, () => { void analyzeRouteTrace() })
watch(activeTab, syncJfrStatusPolling)
function handleVisibilityChange() { syncJfrStatusPolling() }
onMounted(async () => {
  document.addEventListener('visibilitychange', handleVisibilityChange)
  await refreshAdvanced()
  if (disposed) return
  await analyzeRouteTrace()
  if (disposed) return
  syncJfrStatusPolling()
})
onBeforeUnmount(() => {
  disposed = true
  jfrStatusRequestGuard.invalidate()
  jfrFilesRequestGuard.invalidate()
  jfrAnalysisRequestGuard.invalidate()
  jvmSnapshotRequestGuard.invalidate()
  heapHistogramRequestGuard.invalidate()
  jfrStatusPolling.stop()
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})
</script>

<style scoped>
.advanced-diagnosis-page { min-height: 0; }
.advanced-hero-actions, .recording-actions, .runtime-actions { display: flex; align-items: center; gap: 10px; }
.advanced-hero-actions { color: var(--el-text-color-secondary); font-size: 13px; }
.advanced-scope-alert, .advanced-error, .advanced-warning { margin-bottom: 14px; }
.trace-jfr-window { overflow-wrap: anywhere; }
.advanced-panel { overflow: hidden; padding: 0; }
.advanced-tabs { padding: 0 20px; border-bottom: 1px solid var(--el-border-color-lighter); }
.advanced-tabs :deep(.el-tabs__header) { margin: 0; }
.advanced-tabs :deep(.el-tabs__content) { display: none; }
.advanced-content { padding: 20px; }
.recording-console, .subsection-head, .runtime-title, .jfr-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 18px; }
.recording-console { margin-bottom: 16px; padding: 16px; border: 1px solid var(--el-border-color-lighter); border-radius: 10px; background: var(--el-fill-color-extra-light); }
.recording-console h2, .subsection-head h2 { margin: 0; font-size: 17px; }
.recording-console p, .subsection-head p { margin: 5px 0 0; color: var(--el-text-color-secondary); font-size: 13px; }
.duration-select, .window-select { width: 150px; }
.jfr-recorder-status { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 1px; margin-bottom: 14px; overflow: hidden; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; background: var(--el-border-color-lighter); }
.jfr-recorder-status > span { display: flex; flex-direction: column; gap: 5px; min-width: 0; padding: 12px 14px; background: var(--el-bg-color); color: var(--el-text-color-regular); font-size: 12px; }
.jfr-recorder-status strong { color: var(--el-text-color-secondary); font-weight: 500; }
.jfr-toolbar { margin: 16px 0 10px; color: var(--el-text-color-secondary); font-size: 13px; }
.jfr-toolbar .filter-bar { margin: 0; }
.jfr-analysis-panel, .runtime-block { margin-top: 22px; padding-top: 18px; border-top: 1px solid var(--el-border-color-lighter); }
.subsection-head { margin-bottom: 12px; }
.runtime-title { margin-bottom: 10px; }
.runtime-title span { color: var(--el-text-color-secondary); font-size: 12px; }
.thread-detail { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.thread-detail pre { overflow: auto; max-height: 360px; margin: 8px 0 0; padding: 12px; border-radius: 8px; background: var(--el-fill-color-light); white-space: pre-wrap; }
.table-footer { margin-top: 12px; }
@media (max-width: 980px) {
  .advanced-hero-actions, .recording-console, .recording-actions, .subsection-head, .jfr-toolbar { align-items: stretch; flex-direction: column; }
  .jfr-recorder-status { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 640px) {
  .advanced-content { padding: 14px; }
  .jfr-recorder-status, .thread-detail { grid-template-columns: 1fr; }
  .duration-select, .window-select { width: 100%; }
}
</style>

<template>
  <section class="resource-page diagnosis-page">
    <div class="resource-hero">
      <h1>运行诊断</h1>
      <el-button :icon="Refresh" :loading="loading" @click="refreshAll">刷新</el-button>
    </div>

    <el-alert v-if="summary?.summaryTruncated" class="diagnosis-health-alert" type="warning" :closable="false" show-icon :title="`该小时超过 ${summary.summarySampleLimit.toLocaleString('zh-CN')} 条采样上限，摘要为部分数据`" />
    <el-alert v-if="storageWarning" class="diagnosis-health-alert" type="error" :closable="false" show-icon :title="storageWarning" />

    <div v-if="errorMessage" class="inline-error diagnosis-error">
      <span>{{ errorMessage }}</span>
      <el-button link type="primary" :loading="loading" @click="refreshAll">重新加载</el-button>
    </div>

    <section class="surface resource-panel diagnosis-panel">
      <div class="diagnosis-tabs-bar">
        <el-tabs v-model="activeTab" class="diagnosis-tabs">
          <el-tab-pane label="Metrics" name="metrics" />
          <el-tab-pane label="Slow" name="slow" />
          <el-tab-pane label="Errors" name="errors" />
        </el-tabs>
        <span class="diagnosis-retention">保留目标 {{ diagnosisRetentionDays }} 天 · 可追溯跨度 {{ diagnosisCoverageDays }} 天</span>
      </div>

      <div class="table-control-row diagnosis-control-row">
        <el-form :inline="true" class="filter-bar diagnosis-filter-bar">
          <el-form-item label="统一筛选">
            <el-input v-model="query.keyword" :prefix-icon="Search" :placeholder="filterPlaceholder" clearable class="keyword-input" />
          </el-form-item>
          <el-form-item v-if="activeTab === 'metrics'" label="指标分类">
            <el-select v-model="query.metricCategory" placeholder="全部分类" clearable class="category-select">
              <el-option v-for="item in DIAGNOSIS_METRIC_CATEGORIES" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item v-else label="时间范围">
            <el-select v-model="query.timeWindow" class="time-select" :loading="historyWindowLoading" @change="reloadHistoryForActiveTab">
              <el-option label="最近 1 小时" :value="1" />
              <el-option label="最近 24 小时" :value="24" />
              <el-option label="最近 7 天" :value="168" />
              <el-option label="最近 30 天" :value="720" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="supportsTraceOnly">
            <el-checkbox v-model="query.traceOnly">仅看可定位 Trace</el-checkbox>
          </el-form-item>
          <el-form-item><el-button @click="resetFilters">重置</el-button></el-form-item>
        </el-form>
        <span class="diagnosis-result-summary">{{ resultSummary }}</span>
      </div>

      <div v-if="activeTab === 'metrics'" class="diagnosis-content">
        <div class="metric-view-heading">
          <h2>Metrics</h2>
          <el-radio-group v-model="metricView" aria-label="Metrics 展示方式">
            <el-radio-button value="table">Table</el-radio-button>
            <el-radio-button value="trend">Trend</el-radio-button>
          </el-radio-group>
        </div>
        <div v-if="metricView === 'table'">
          <el-table v-loading="loading" :data="pagedMetrics" class="admin-table" empty-text="暂无匹配指标">
            <el-table-column label="分类" width="110"><template #default="{ row }"><el-tag effect="plain">{{ row.categoryLabel }}</el-tag></template></el-table-column>
            <el-table-column label="指标" min-width="260"><template #default="{ row }"><strong>{{ row.displayName }}</strong><span class="cell-note">{{ row.name }}</span></template></el-table-column>
            <el-table-column prop="type" label="类型" width="150" />
            <el-table-column label="标签" min-width="280" show-overflow-tooltip><template #default="{ row }">{{ formatTags(row.tags) }}</template></el-table-column>
            <el-table-column label="当前值" min-width="280"><template #default="{ row }">{{ formatMetricMeasurements(row.measurements, row) }}</template></el-table-column>
          </el-table>
          <div class="history-page-footer">
            <el-pagination v-model:current-page="metricPage" background layout="prev, pager, next" :page-size="METRIC_PAGE_SIZE" :total="filteredMetrics.length" />
          </div>
        </div>
        <section v-else class="metric-history-section">
          <div class="history-toolbar">
            <h2>指标历史趋势</h2>
            <div class="history-selectors">
              <el-select v-model="selectedMetricSeries" filterable placeholder="选择指标系列" @change="loadMetricHistory">
                <el-option v-for="item in metricSeries" :key="item.key" :label="item.label" :value="item.key" />
              </el-select>
              <el-select v-model="selectedMeasurement" placeholder="选择统计值" class="measurement-select">
                <el-option v-for="item in historyMeasurementNames" :key="item" :label="measurementLabel(item)" :value="item" />
              </el-select>
            </div>
          </div>
          <div v-if="metricHistory.length && selectedMeasurement" v-loading="historyLoading" class="metric-history-chart">
            <EasyChart :option="metricHistoryChartOption" :chart-label="`${selectedMetricLabel}历史趋势图`" />
          </div>
          <el-table v-loading="historyLoading" :data="pagedMetricHistory" class="admin-table history-table" empty-text="选择指标后展示已落盘的历史快照">
            <el-table-column label="采样时间" width="190"><template #default="{ row }">{{ formatDiagnosisTime(row.capturedAt) }}</template></el-table-column>
            <el-table-column label="标签" min-width="280" show-overflow-tooltip><template #default="{ row }">{{ formatTags(row.tags) }}</template></el-table-column>
            <el-table-column label="指标值" min-width="260"><template #default="{ row }">{{ formatMetricMeasurements(row.measurements, selectedMetricRow) }}</template></el-table-column>
          </el-table>
          <div class="table-footer is-split">
            <span>当前系列 {{ metricHistory.length.toLocaleString('zh-CN') }} 个本地样本</span>
            <el-pagination v-model:current-page="historyPage" background layout="prev, pager, next" :page-size="50" :total="metricHistory.length" />
          </div>
        </section>
      </div>

      <div v-else-if="activeTab === 'slow'" class="diagnosis-content">
        <el-table v-loading="loading || historyPages.slow.loading" :data="filteredSlow" :row-key="diagnosisRowKey" class="admin-table" empty-text="暂无匹配慢调用">
          <el-table-column label="类型" width="130"><template #default="{ row }"><el-tag type="warning" effect="plain">{{ slowTypeLabel(row.type) }}</el-tag></template></el-table-column>
          <el-table-column prop="name" label="操作" min-width="240" show-overflow-tooltip />
          <el-table-column prop="method" label="请求 / 方法" min-width="210"><template #default="{ row }">{{ row.method || '-' }}</template></el-table-column>
          <el-table-column label="耗时" width="130"><template #default="{ row }"><strong class="duration-risk">{{ row.costMs }} ms</strong></template></el-table-column>
          <el-table-column label="发生时间" width="190"><template #default="{ row }">{{ formatDiagnosisTime(row.occurredAt) }}</template></el-table-column>
          <el-table-column label="关联 Trace" width="120" fixed="right"><template #default="{ row }"><el-button v-if="row.traceId" link type="primary" @click="openTrace(row.traceId)">查看 Trace</el-button><span v-else class="muted">无 Trace</span></template></el-table-column>
        </el-table>
        <div class="history-page-footer"><span>已从服务端加载 {{ slow.length }} 条慢调用</span><el-button v-if="historyPages.slow.hasMore" :loading="historyPages.slow.loading" @click="loadMoreHistory('slow')">加载更多</el-button><span v-else>已到达当前时间范围末尾</span></div>
      </div>

      <div v-else-if="activeTab === 'errors'" class="diagnosis-content">
        <el-table v-loading="loading || historyPages.errors.loading" :data="filteredErrors" row-key="id" class="admin-table" empty-text="暂无匹配错误记录">
          <el-table-column type="expand"><template #default="{ row }"><div class="expanded-detail"><strong>异常堆栈</strong><pre class="trace-tree">{{ row.stackTrace || '没有保存堆栈信息' }}</pre></div></template></el-table-column>
          <el-table-column label="发生时间" width="190"><template #default="{ row }">{{ formatDiagnosisTime(row.createdAt) }}</template></el-table-column>
          <el-table-column label="来源" width="100"><template #default="{ row }"><el-tag effect="plain">{{ row.source === 'AUDIT' ? '审计' : row.requestMethod || '运行时' }}</el-tag></template></el-table-column>
          <el-table-column label="请求" min-width="260"><template #default="{ row }"><strong>{{ row.requestMethod }}</strong> {{ row.requestUri }}</template></el-table-column>
          <el-table-column prop="errorType" label="异常类型" min-width="190" show-overflow-tooltip />
          <el-table-column prop="errorMessage" label="错误摘要" min-width="260" show-overflow-tooltip />
          <el-table-column label="关联 Trace" width="120" fixed="right"><template #default="{ row }"><el-button v-if="row.traceId" link type="primary" @click="openTrace(row.traceId)">查看 Trace</el-button><span v-else class="muted">无 Trace</span></template></el-table-column>
        </el-table>
        <div class="history-page-footer"><span>已从服务端加载 {{ errors.length }} 条错误</span><el-button v-if="historyPages.errors.hasMore" :loading="historyPages.errors.loading" @click="loadMoreHistory('errors')">加载更多</el-button><span v-else>已到达当前时间范围末尾</span></div>
      </div>

    </section>

    <el-drawer v-model="traceDrawer" title="Trace 问题定位" size="min(820px, 94vw)" append-to-body>
      <template v-if="selectedTrace">
        <div v-loading="traceLoading">
        <div class="trace-summary">
          <div><strong>{{ selectedTrace.name }}</strong><el-tag effect="plain">{{ operationTypeLabel(selectedTrace.type || 'HTTP') }}</el-tag><el-tag :type="selectedTrace.status >= 400 ? 'danger' : 'success'">{{ selectedTrace.status }}</el-tag></div>
          <span>{{ selectedTrace.method }} · {{ selectedTrace.costMs }} ms</span>
          <code>{{ selectedTrace.traceId }}</code>
        </div>
        <div class="trace-correlation" aria-label="Trace 关联诊断数据">
          <span>关联数据</span>
          <el-tag v-if="selectedTraceSlow.length || selectedTraceErrors.length" effect="plain">问题事件 1</el-tag>
          <el-tag type="warning" effect="plain">Slow {{ selectedTraceSlow.length }}</el-tag>
          <el-tag type="danger" effect="plain">Errors {{ selectedTraceErrors.length }}</el-tag>
          <el-tag effect="plain">操作资源 {{ selectedTraceOperations.length }}</el-tag>
          <el-button
            v-if="selectedTrace.traceId"
            v-permission:disable="{ permissions: PermissionCodes.monitor.diagnosisAdvancedView, reason: '缺少高级诊断查看权限' }"
            link
            type="primary"
            @click="openAdvancedDiagnosis(selectedTrace.traceId, selectedTrace.finishedAt, selectedTrace.costMs)"
          >用 JFR 深入分析</el-button>
        </div>
        <div class="drawer-section-head"><h3>调用树</h3><span>事务与 SQL Span 会显示在对应父子层级</span></div>
        <pre class="trace-tree">{{ selectedTrace.tree || '该 Trace 没有保留调用树。' }}</pre>
        <template v-if="selectedTraceSlow.length">
          <div class="drawer-section-head"><h3>服务端关联 Slow</h3><span>同一 traceId 下的慢请求、长事务和慢 SQL</span></div>
          <el-table :data="selectedTraceSlow" :row-key="diagnosisRowKey" class="admin-table" max-height="220">
            <el-table-column label="类型" width="110"><template #default="{ row }">{{ slowTypeLabel(row.type) }}</template></el-table-column>
            <el-table-column prop="name" label="操作" min-width="260" show-overflow-tooltip />
            <el-table-column label="耗时" width="110"><template #default="{ row }">{{ row.costMs }} ms</template></el-table-column>
          </el-table>
        </template>
        <template v-if="selectedTraceTransactions.length">
          <div class="drawer-section-head"><h3>关联事务</h3><span>事务边界、结果和耗时</span></div>
          <el-table :data="selectedTraceTransactions" class="admin-table" max-height="240">
            <el-table-column prop="name" label="事务" min-width="250" show-overflow-tooltip />
            <el-table-column label="结果" width="100"><template #default="{ row }"><el-tag :type="row.success ? 'success' : 'danger'">{{ row.success ? '提交' : '失败/回滚' }}</el-tag></template></el-table-column>
            <el-table-column label="耗时" width="100"><template #default="{ row }">{{ row.costMs }} ms</template></el-table-column>
          </el-table>
        </template>
        <template v-if="selectedTraceOperations.length">
          <div class="drawer-section-head"><h3>关联操作资源</h3><span>同一 traceId 下的线程区间数据；父子操作资源为包含关系</span></div>
          <el-table :data="selectedTraceOperations" :row-key="diagnosisRowKey" class="admin-table" max-height="300">
            <el-table-column type="expand">
              <template #default="{ row }"><div class="operation-detail"><div><strong>锁信息</strong><pre>{{ row.lockInfo || '无锁等待或未触发慢/错误详情采集' }}</pre></div><div><strong>现场线程堆栈</strong><pre>{{ row.stackTrace || '仅对慢操作和错误操作保留堆栈' }}</pre></div></div></template>
            </el-table-column>
            <el-table-column label="类型" width="96"><template #default="{ row }">{{ operationTypeLabel(row.type) }}</template></el-table-column>
            <el-table-column prop="name" label="操作" min-width="210" show-overflow-tooltip />
            <el-table-column label="子调用汇总" min-width="330" show-overflow-tooltip><template #default="{ row }">{{ formatChildOperationSummaries(row.childSummaries) }}</template></el-table-column>
            <el-table-column label="耗时" width="100"><template #default="{ row }">{{ row.durationMs }} ms</template></el-table-column>
            <el-table-column label="CPU" width="96"><template #default="{ row }">{{ known(row.cpuTimeMs, ' ms') }}</template></el-table-column>
            <el-table-column label="线程分配" width="112"><template #default="{ row }">{{ bytes(row.allocatedBytes) }}</template></el-table-column>
            <el-table-column label="阻塞 / 等待" width="150"><template #default="{ row }">{{ known(row.blockedCount) }} / {{ known(row.waitedCount) }}<small class="cell-note">{{ known(row.blockedTimeMs, ' ms') }} / {{ known(row.waitedTimeMs, ' ms') }}</small></template></el-table-column>
          </el-table>
        </template>
        <template v-if="selectedTraceErrors.length">
          <div class="drawer-section-head"><h3>关联错误</h3><span>同一 traceId 的运行错误与异常审计</span></div>
          <el-table :data="selectedTraceErrors" row-key="id" class="admin-table" max-height="260">
            <el-table-column prop="errorType" label="类型" min-width="180" show-overflow-tooltip />
            <el-table-column prop="errorMessage" label="摘要" min-width="280" show-overflow-tooltip />
            <el-table-column label="时间" width="175"><template #default="{ row }">{{ formatDiagnosisTime(row.createdAt) }}</template></el-table-column>
          </el-table>
        </template>
        </div>
      </template>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { EChartsCoreOption } from 'echarts/core'
import EasyChart from '@/components/charts/EasyChart.vue'
import { getDiagnosisErrorsPage, getDiagnosisMetrics, getDiagnosisSummary, getMetricHistory, getSlowSamplesPage, getTraceCorrelation, getTraceDetail } from '@/features/monitor/api'
import { DIAGNOSIS_METRIC_CATEGORIES, formatChildOperationSummaries, formatDiagnosisTime, formatMetricValue, matchesDiagnosisKeyword, metricPresentation, operationTypeLabel, sampleMetricHistory, type DiagnosisMetricCategory } from '@/features/monitor/diagnosisPresentation'
import { createLatestRequestGuard } from '@/features/monitor/runtimeDiagnosisPolling'
import type { DiagnosisMetricSample, DiagnosisSummary, ErrorSample, HistoryPage, MetricHistoryPoint, SlowSample, TraceCorrelation, TraceSample } from '@/features/monitor/types'
import { PermissionCodes } from '@/permissions/codes'

type DiagnosisTab = 'metrics' | 'slow' | 'errors'
type HistoryListKey = 'slow' | 'errors'
type MetricView = 'table' | 'trend'
type MetricRow = DiagnosisMetricSample & { seriesKey: string; category: DiagnosisMetricCategory; categoryLabel: string; displayName: string }
interface HistoryPageState { hasMore: boolean; nextBefore?: string; loading: boolean; hours?: number; filterKey?: string }
const METRIC_PAGE_SIZE = 50

const router = useRouter()
const activeTab = ref<DiagnosisTab>('metrics')
const metricView = ref<MetricView>('table')
const loading = ref(false)
const historyLoading = ref(false)
const errorMessage = ref('')
const summary = ref<DiagnosisSummary>()
const metrics = ref<DiagnosisMetricSample[]>([])
const slow = ref<SlowSample[]>([])
const errors = ref<ErrorSample[]>([])
const metricHistory = ref<MetricHistoryPoint[]>([])
const selectedMetricSeries = ref('')
const selectedMeasurement = ref('')
const metricPage = ref(1)
const historyPage = ref(1)
const historyWindowLoading = ref(false)
const traceDrawer = ref(false)
const traceLoading = ref(false)
const selectedTrace = ref<TraceSample>()
const selectedCorrelation = ref<TraceCorrelation>()
const query = reactive({ keyword: '', timeWindow: 24, traceOnly: false, metricCategory: '' as '' | DiagnosisMetricCategory })
const historyPages = reactive<Record<HistoryListKey, HistoryPageState>>({
  slow: { hasMore: false, loading: false },
  errors: { hasMore: false, loading: false }
})
let disposed = false
let historyRequestVersion = 0
let historyFilterTimer: ReturnType<typeof setTimeout> | undefined
const metricHistoryRequestGuard = createLatestRequestGuard()
const traceSelectionRequestGuard = createLatestRequestGuard()
const metricRows = computed<MetricRow[]>(() => metrics.value.map((metric) => {
  const presentation = metricPresentation(metric.name)
  return { ...metric, seriesKey: JSON.stringify({ name: metric.name, tags: metric.tags }), category: presentation.category, categoryLabel: presentation.categoryLabel, displayName: presentation.label }
}))
const filteredMetrics = computed(() => metricRows.value.filter((metric) => (!query.metricCategory || metric.category === query.metricCategory) && matchesDiagnosisKeyword(query.keyword, metric.displayName, metric.name, metric.tags, metric.measurements)))
const pagedMetrics = computed(() => filteredMetrics.value.slice((metricPage.value - 1) * METRIC_PAGE_SIZE, metricPage.value * METRIC_PAGE_SIZE))
// Paged history is already filtered by the server across persisted details such as stacks, lock text and trace trees.
// Re-filtering the DTOs here would hide valid matches for fields that are intentionally not repeated in every row DTO.
const filteredSlow = computed(() => isHistoryFilterCurrent('slow') ? slow.value : [])
const filteredErrors = computed(() => isHistoryFilterCurrent('errors') ? errors.value : [])
const metricSeries = computed(() => filteredMetrics.value.map((metric) => ({ key: metric.seriesKey, label: `${metric.displayName} · ${formatTags(metric.tags)}` })))
const historyMeasurementNames = computed(() => [...new Set(metricHistory.value.flatMap((item) => Object.keys(item.measurements || {})))])
const pagedMetricHistory = computed(() => metricHistory.value.slice((historyPage.value - 1) * 50, historyPage.value * 50))
const selectedMetricLabel = computed(() => metricSeries.value.find((item) => item.key === selectedMetricSeries.value)?.label || '指标')
const selectedMetricRow = computed(() => metricRows.value.find((item) => item.seriesKey === selectedMetricSeries.value))
const diagnosisRetentionDays = computed(() => activeTab.value === 'metrics'
  ? summary.value?.metricsStorage?.retentionDays ?? 30
  : summary.value?.storage?.retentionDays ?? summary.value?.retentionDays ?? 30)
const diagnosisCoverageDays = computed(() => activeTab.value === 'metrics'
  ? summary.value?.metricsStorage?.coverageDays ?? 0
  : summary.value?.storage?.coverageDays ?? 0)
const metricHistoryChartOption = computed<EChartsCoreOption>(() => {
  const sampled = sampleMetricHistory(metricHistory.value.slice().reverse(), selectedMeasurement.value, 600)
  return {
    animation: false,
    grid: { left: 56, right: 24, top: 28, bottom: 58 },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', boundaryGap: false, data: sampled.map((item) => formatDiagnosisTime(item.capturedAt)), axisLabel: { hideOverlap: true } },
    yAxis: { type: 'value', scale: true },
    dataZoom: [{ type: 'inside' }, { type: 'slider', height: 18, bottom: 8 }],
    series: [{ name: measurementLabel(selectedMeasurement.value), type: 'line', showSymbol: false, smooth: true, data: sampled.map((item) => item.measurements?.[selectedMeasurement.value] ?? null), areaStyle: { opacity: 0.08 } }]
  }
})
const supportsTraceOnly = computed(() => activeTab.value !== 'metrics')
const filterPlaceholder = computed(() => ({ metrics: '指标名称、中文名或标签', slow: '类型、操作、方法或 traceId', errors: '请求、异常、摘要或 traceId' }[activeTab.value]))
const resultSummary = computed(() => ({ metrics: `显示 ${filteredMetrics.value.length} / ${metrics.value.length} 个指标系列`, slow: `显示 ${filteredSlow.value.length} / ${slow.value.length} 个慢调用`, errors: `显示 ${filteredErrors.value.length} / ${errors.value.length} 个错误` }[activeTab.value]))
const selectedTraceSlow = computed(() => selectedCorrelation.value?.slow ?? slow.value.filter((item) => item.traceId && item.traceId === selectedTrace.value?.traceId))
const selectedTraceErrors = computed(() => selectedCorrelation.value?.errors ?? errors.value.filter((item) => item.traceId && item.traceId === selectedTrace.value?.traceId))
const selectedTraceOperations = computed(() => selectedCorrelation.value?.operations ?? [])
const selectedTraceTransactions = computed(() => selectedCorrelation.value?.transactions ?? [])
const storageWarning = computed(() => {
  const storage = summary.value?.storage
  const messages: string[] = []
  if (storage?.capacityPressure) messages.push(`事件与 Trace 已用 ${bytes(storage.historyBytes)} / ${bytes(storage.maxHistoryBytes)}，容量接近上限`)
  if (storage?.droppedWrites) messages.push(`事件写入队列已丢弃 ${storage.droppedWrites.toLocaleString('zh-CN')} 条记录`)
  if (storage?.byteBudgetDroppedWrites) messages.push(`其中 ${storage.byteBudgetDroppedWrites.toLocaleString('zh-CN')} 条因待写内存达到 ${bytes(storage.pendingMaxBytes)} 上限被拒绝`)
  const metricsStorage = summary.value?.metricsStorage
  if (metricsStorage && metricsStorage.maxStorageBytes > 0 && metricsStorage.storageBytes / metricsStorage.maxStorageBytes >= 0.9) messages.push(`Metrics 时序已用 ${bytes(metricsStorage.storageBytes)} / ${bytes(metricsStorage.maxStorageBytes)}，容量接近上限`)
  if (metricsStorage?.droppedWrites) messages.push(`Metrics 时序已丢弃 ${metricsStorage.droppedWrites.toLocaleString('zh-CN')} 个快照`)
  if (metricsStorage?.rejectedSeries) messages.push(`Metrics 时序已拒绝 ${metricsStorage.rejectedSeries.toLocaleString('zh-CN')} 个新序列，请检查标签基数或容量配置`)
  return messages.join('；')
})

async function refreshAll() {
  if (loading.value) return
  loading.value = true
  errorMessage.value = ''
  const tab = activeTab.value
  const historyKeys = historyKeysForTab(tab)
  const historyVersion = ++historyRequestVersion
  const hours = query.timeWindow
  const historyFilterKeys = Object.fromEntries(historyKeys.map((key) => [key, historyFilterKey(key)])) as Partial<Record<HistoryListKey, string>>
  Object.values(historyPages).forEach((state) => { state.loading = false })
  historyKeys.forEach((key) => { historyPages[key].loading = true })
  historyWindowLoading.value = historyKeys.length > 0
  try {
    const requests: Array<Promise<string | undefined>> = [
      applyRefreshRequest('诊断摘要', getDiagnosisSummary(), (value) => { summary.value = value })
    ]
    if (tab === 'metrics') {
      requests.push(applyRefreshRequest('Metrics', getDiagnosisMetrics(), (value) => { metrics.value = value }))
    }
    historyKeys.forEach((key) => {
      requests.push(applyRefreshRequest(historyLabel(key), fetchHistoryPage(key, hours), (page) => {
        if (historyVersion === historyRequestVersion) {
          applyHistoryPage(key, page, false, hours, historyFilterKeys[key] || '')
        }
      }))
    })
    const failures = (await Promise.all(requests)).filter((failure): failure is string => Boolean(failure))
    if (disposed) return
    if (failures.length) errorMessage.value = `${failures.join('、')} 加载失败，其他诊断数据已保留`
  } finally {
    if (historyVersion === historyRequestVersion) historyWindowLoading.value = false
    loading.value = false
  }
}

async function applyRefreshRequest<T>(label: string, request: Promise<T>, apply: (value: T) => void) {
  try {
    const value = await request
    if (!disposed) apply(value)
    return undefined
  } catch {
    return label
  }
}

function historyLabel(key: HistoryListKey) {
  return ({ slow: 'Slow', errors: 'Errors' } as const)[key]
}

function fetchHistoryPage(key: HistoryListKey, hours: number, before?: string): Promise<HistoryPage<unknown>> {
  const params = {
    hours,
    before,
    size: 100,
    keyword: query.keyword.trim() || undefined,
    traceOnly: query.traceOnly
  }
  switch (key) {
    case 'slow': return getSlowSamplesPage(params) as Promise<HistoryPage<unknown>>
    case 'errors': return getDiagnosisErrorsPage(params) as Promise<HistoryPage<unknown>>
  }
}

function applyHistoryPage(key: HistoryListKey, page: HistoryPage<unknown>, append: boolean, hours: number, filterKey: string) {
  switch (key) {
    case 'slow': slow.value = append ? [...slow.value, ...(page.items as SlowSample[])] : page.items as SlowSample[]; break
    case 'errors': errors.value = append ? [...errors.value, ...(page.items as ErrorSample[])] : page.items as ErrorSample[]; break
  }
  Object.assign(historyPages[key], {
    hasMore: page.hasMore,
    nextBefore: page.nextBefore,
    loading: false,
    hours,
    filterKey
  })
}

function historyKeysForTab(tab: DiagnosisTab): HistoryListKey[] {
  if (tab === 'slow' || tab === 'errors') return [tab]
  return []
}

async function reloadHistoryForActiveTab(force = false) {
  if (historyFilterTimer) clearTimeout(historyFilterTimer)
  historyFilterTimer = undefined
  const keys = historyKeysForTab(activeTab.value)
  if (!keys.length || !force && keys.every((key) => historyPages[key].hours === query.timeWindow
    && historyPages[key].filterKey === historyFilterKey(key))) return
  const version = ++historyRequestVersion
  const hours = query.timeWindow
  const filterKeys = Object.fromEntries(keys.map((key) => [key, historyFilterKey(key)])) as Partial<Record<HistoryListKey, string>>
  historyWindowLoading.value = true
  keys.forEach((key) => { historyPages[key].loading = true })
  const results = await Promise.allSettled(keys.map((key) => fetchHistoryPage(key, hours)))
  if (version !== historyRequestVersion || disposed) return
  const failures: string[] = []
  results.forEach((result, index) => {
    const key = keys[index]
    if (result.status === 'fulfilled') applyHistoryPage(key, result.value, false, hours, filterKeys[key] || '')
    else { historyPages[key].loading = false; failures.push(key) }
  })
  historyWindowLoading.value = false
  if (failures.length) ElMessage.error(`${failures.join('、')} 时间范围加载失败`)
}

async function loadMoreHistory(key: HistoryListKey) {
  const state = historyPages[key]
  if (state.filterKey !== historyFilterKey(key)) {
    await reloadHistoryForActiveTab(true)
    return false
  }
  if (state.loading || !state.hasMore || !state.nextBefore) return false
  const version = historyRequestVersion
  const hours = query.timeWindow
  const filterKey = state.filterKey
  state.loading = true
  try {
    const page = await fetchHistoryPage(key, hours, state.nextBefore)
    if (version !== historyRequestVersion || disposed) return false
    applyHistoryPage(key, page, true, hours, filterKey || historyFilterKey(key))
    return true
  } catch {
    if (version === historyRequestVersion) ElMessage.error('加载更多诊断历史失败')
    return false
  } finally {
    if (version === historyRequestVersion) state.loading = false
  }
}

async function loadMetricHistory(showError = true) {
  const seriesKey = selectedMetricSeries.value
  const version = metricHistoryRequestGuard.next()
  if (!seriesKey) {
    metricHistory.value = []
    selectedMeasurement.value = ''
    historyLoading.value = false
    return
  }
  historyLoading.value = true
  try {
    const series = JSON.parse(seriesKey) as { name: string; tags: Record<string, string> }
    const history = await getMetricHistory(series.name, series.tags)
    if (!metricHistoryRequestGuard.isCurrent(version) || disposed || selectedMetricSeries.value !== seriesKey) return
    metricHistory.value = history
    const available = [...new Set(history.flatMap((item) => Object.keys(item.measurements || {})))]
    if (!available.includes(selectedMeasurement.value)) selectedMeasurement.value = available[0] || ''
    historyPage.value = 1
  } catch {
    if (metricHistoryRequestGuard.isCurrent(version) && !disposed) {
      metricHistory.value = []
      if (showError) ElMessage.error('Metrics 历史加载失败')
    }
  } finally {
    if (metricHistoryRequestGuard.isCurrent(version)) historyLoading.value = false
  }
}

function historyFilterKey(key: HistoryListKey) {
  return `${query.timeWindow}|${query.keyword.trim().toLocaleLowerCase('zh-CN')}|${query.traceOnly}`
}
function isHistoryFilterCurrent(key: HistoryListKey) { return historyPages[key].filterKey === historyFilterKey(key) }
function scheduleHistorySearch() {
  if (historyFilterTimer) clearTimeout(historyFilterTimer)
  historyFilterTimer = undefined
  if (activeTab.value === 'metrics') return
  // Invalidate any older response immediately; otherwise it could be stamped with the new filter during debounce.
  historyRequestVersion++
  historyWindowLoading.value = false
  historyKeysForTab(activeTab.value).forEach((key) => { historyPages[key].loading = false })
  historyFilterTimer = setTimeout(() => {
    historyFilterTimer = undefined
    void reloadHistoryForActiveTab(true)
  }, 300)
}
function handleTabChange() {
  if (!supportsTraceOnly.value) query.traceOnly = false
  if (historyFilterTimer) clearTimeout(historyFilterTimer)
  historyFilterTimer = undefined
  Object.values(historyPages).forEach((state) => { state.loading = false })
  historyWindowLoading.value = false
  void reloadHistoryForActiveTab()
}
function resetFilters() {
  const timeWindowChanged = query.timeWindow !== 24
  query.keyword = ''; query.timeWindow = 24; query.traceOnly = false; query.metricCategory = ''
  if (timeWindowChanged) void reloadHistoryForActiveTab(true)
}
function openAdvancedDiagnosis(traceId: string, finishedAt?: string, costMs = 0, paddingMs = 0) {
  const finishedAtMs = Date.parse(finishedAt || '')
  const query: Record<string, string> = { traceId }
  if (Number.isFinite(finishedAtMs)) {
    query.from = new Date(finishedAtMs - Math.max(0, costMs) - paddingMs).toISOString()
    query.to = new Date(finishedAtMs + paddingMs).toISOString()
  }
  void router.push({ path: '/monitor/advanced-diagnosis', query })
}
async function openTrace(traceId: string) {
  const version = traceSelectionRequestGuard.next()
  traceDrawer.value = false
  selectedTrace.value = undefined
  selectedCorrelation.value = undefined
  traceLoading.value = true
  const trace = await getTraceDetail(traceId).catch(() => null)
  if (!traceSelectionRequestGuard.isCurrent(version) || disposed) return
  if (trace) {
    await showTrace(trace, version)
  } else {
    traceLoading.value = false
    ElMessage.warning('该 Trace 树已超过本地保留期或当前实例未采集到')
  }
}
async function showTrace(trace: TraceSample, existingVersion?: number) {
  const version = existingVersion ?? traceSelectionRequestGuard.next()
  if (!traceSelectionRequestGuard.isCurrent(version) || disposed) return
  selectedTrace.value = trace
  selectedCorrelation.value = undefined
  traceDrawer.value = true
  traceLoading.value = false
  if (!trace.traceId) return
  traceLoading.value = true
  try {
    const correlation = await getTraceCorrelation(trace.traceId)
    if (traceSelectionRequestGuard.isCurrent(version) && !disposed && correlation) {
      selectedCorrelation.value = correlation
      if (correlation.trace) selectedTrace.value = correlation.trace
    }
  } catch {
    if (traceSelectionRequestGuard.isCurrent(version) && !disposed) ElMessage.warning('完整关联数据加载失败，已显示当前列表中的 Trace 数据')
  } finally {
    if (traceSelectionRequestGuard.isCurrent(version)) traceLoading.value = false
  }
}

function syncMetricSeriesSelection(series = metricSeries.value) {
  const nextSeries = series.some((item) => item.key === selectedMetricSeries.value)
    ? selectedMetricSeries.value
    : series[0]?.key || ''
  selectedMetricSeries.value = nextSeries
  if (!nextSeries) {
    metricHistoryRequestGuard.invalidate()
    metricHistory.value = []
    selectedMeasurement.value = ''
    historyLoading.value = false
    return
  }
  if (activeTab.value === 'metrics' && metricView.value === 'trend') void loadMetricHistory(false)
}

function formatTags(tags: Record<string, string>) { return Object.entries(tags || {}).map(([key, value]) => `${key}=${value}`).join(' · ') || '-' }
function formatMetricMeasurements(values: Record<string, number>, metric?: Pick<DiagnosisMetricSample, 'name' | 'baseUnit'>) { return Object.entries(values || {}).map(([key, value]) => `${measurementLabel(key)}: ${metric ? formatMetricValue(metric, key, value) : formatNumber(value)}`).join(' · ') || '-' }
function formatNumber(value: number) { if (!Number.isFinite(value)) return '-'; const normalized = Math.abs(value) >= 1000000 ? value.toExponential(3) : Number(value.toPrecision(6)); return typeof normalized === 'number' ? normalized.toLocaleString('zh-CN') : normalized }
function measurementLabel(name: string) { return ({ COUNT: '次数', TOTAL_TIME: '总耗时', MAX: '最大值', VALUE: '当前值', TOTAL: '总量', ACTIVE_TASKS: '活跃任务', DURATION: '持续时间' } as Record<string, string>)[name] || name }
function slowTypeLabel(type: string) { return ({ TRANSACTION: '长事务', HTTP: '慢请求', SQL: '慢 SQL', JOB: '慢 Job', KAFKA: '慢 Kafka', REMOTE: '慢远程调用' } as Record<string, string>)[type] || operationTypeLabel(type) }
function known(value: number, suffix = '') { return value < 0 ? '不可用' : `${formatNumber(value)}${suffix}` }
function bytes(value: number) { if (value < 0) return '不可用'; if (value < 1024) return `${value} B`; if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`; return `${(value / 1024 / 1024).toFixed(1)} MB` }
function diagnosisRowKey(row: Record<string, unknown>) { return String(row.id ?? `${row.traceId || ''}-${row.type || ''}-${row.name || ''}-${row.occurredAt || row.finishedAt || ''}`) }

watch(metricSeries, (series) => { syncMetricSeriesSelection(series) })
watch(metricView, (view) => { if (view === 'trend') syncMetricSeriesSelection() })
watch(() => [query.keyword, query.metricCategory], () => { metricPage.value = 1 }, { flush: 'sync' })
watch(filteredMetrics, (rows) => {
  const lastPage = Math.max(1, Math.ceil(rows.length / METRIC_PAGE_SIZE))
  if (metricPage.value > lastPage) metricPage.value = lastPage
})
watch(activeTab, () => {
  handleTabChange()
  if (activeTab.value === 'metrics') syncMetricSeriesSelection()
})
watch(() => [query.keyword, query.traceOnly], scheduleHistorySearch, { flush: 'sync' })
onMounted(() => { void refreshAll() })
onUnmounted(() => {
  disposed = true
  historyRequestVersion++
  metricHistoryRequestGuard.invalidate()
  traceSelectionRequestGuard.invalidate()
  if (historyFilterTimer) clearTimeout(historyFilterTimer)
})
</script>

<style scoped>
.diagnosis-page { min-height: 0; }
.diagnosis-health-alert { margin-top: 10px; }
.diagnosis-error { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.diagnosis-panel { min-height: 500px; }
.diagnosis-tabs-bar { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 0 16px; border-bottom: 1px solid var(--ea-border); }
.diagnosis-tabs { flex: 1 1 auto; min-width: 0; }
.diagnosis-tabs :deep(.el-tabs__header) { margin: 0; }
.diagnosis-tabs :deep(.el-tabs__nav-wrap::after), .diagnosis-tabs :deep(.el-tabs__content) { display: none; }
.diagnosis-retention { flex: 0 0 auto; color: var(--el-text-color-secondary); font-size: 12px; }
.diagnosis-control-row { align-items: flex-start; }
.diagnosis-filter-bar { row-gap: 8px; }
.keyword-input { width: min(340px, 34vw); }
.category-select, .time-select { width: 150px; }
.diagnosis-result-summary { flex: 0 0 auto; padding-top: 9px; color: var(--el-text-color-secondary); font-size: 12px; white-space: nowrap; }
.diagnosis-content { min-width: 0; }
.metric-view-heading { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 16px; border-bottom: 1px solid var(--ea-border); }
.metric-view-heading h2 { margin: 0; color: var(--el-text-color-primary); font-size: 18px; }
.metric-history-section { padding-top: 8px; border-top: 1px solid var(--ea-border); }
.history-toolbar, .drawer-section-head { display: flex; align-items: center; justify-content: space-between; gap: 20px; }
.history-toolbar { padding: 16px; }
.history-toolbar h2, .drawer-section-head h3 { margin: 0; color: var(--el-text-color-primary); }
.history-toolbar h2 { font-size: 16px; }
.history-selectors { display: flex; align-items: center; gap: 10px; }
.history-selectors > .el-select:first-child { width: min(430px, 40vw); }
.measurement-select { width: 140px; }
.metric-history-chart { min-height: 280px; margin: 0 16px 16px; border: 1px solid var(--ea-border); border-radius: 8px; background: var(--el-bg-color); }
.history-table { border-top: 1px solid var(--ea-border); }
.history-page-footer { display: flex; align-items: center; justify-content: flex-end; gap: 14px; min-height: 48px; padding: 8px 16px; border-top: 1px solid var(--ea-border); color: var(--el-text-color-secondary); font-size: 12px; }
.drawer-section-head > span { color: var(--el-text-color-secondary); font-size: 12px; }
.duration-risk { color: var(--el-color-warning-dark-2); }
.cell-note { display: block; margin-top: 3px; color: var(--el-text-color-secondary); font-size: 11px; }
.muted { color: var(--el-text-color-placeholder); font-size: 12px; }
.expanded-detail { padding: 4px 16px 16px; }
.operation-detail { display: grid; grid-template-columns: minmax(220px, 1fr) minmax(360px, 2fr); gap: 20px; padding: 8px 16px; }
.operation-detail > div { min-width: 0; }
.operation-detail pre { max-height: 320px; overflow: auto; white-space: pre-wrap; }
.trace-summary { display: grid; gap: 8px; margin-bottom: 16px; color: var(--el-text-color-secondary); }
.trace-summary > div { display: flex; align-items: center; gap: 10px; }
.trace-summary > div .el-button { margin-left: auto; }
.trace-summary strong { color: var(--el-text-color-primary); font-size: 17px; }
.trace-summary code { overflow-wrap: anywhere; color: var(--el-color-primary); }
.trace-correlation { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; padding: 12px; border-radius: 8px; background: var(--el-fill-color-light); }
.trace-correlation > span { margin-right: 4px; color: var(--el-text-color-secondary); font-size: 12px; }
.drawer-section-head { margin: 22px 0 10px; }
.drawer-section-head h3 { font-size: 15px; }
.trace-tree { overflow: auto; max-height: calc(100vh - 260px); padding: 16px; border-radius: 8px; background: var(--el-fill-color-light); color: var(--el-text-color-primary); font: 12px/1.65 ui-monospace, SFMono-Regular, Menlo, monospace; white-space: pre-wrap; }

@media (max-width: 980px) {
  .diagnosis-tabs-bar, .diagnosis-control-row, .history-toolbar, .metric-view-heading { align-items: stretch; flex-direction: column; }
  .diagnosis-retention { padding-bottom: 10px; }
  .diagnosis-result-summary { padding-top: 0; }
  .keyword-input, .history-selectors > .el-select:first-child { width: min(100%, 520px); }
  .operation-detail { grid-template-columns: 1fr; }
}

@media (max-width: 640px) {
  .diagnosis-tabs-bar { padding-inline: 12px; }
  .diagnosis-retention { display: none; }
  .history-selectors { align-items: stretch; flex-direction: column; }
  .history-selectors > .el-select:first-child, .measurement-select { width: 100%; }
  .history-page-footer { align-items: flex-end; flex-direction: column; }
}
</style>

<template>
  <section class="resource-page monitor-page">
    <div class="resource-hero">
      <div>
        <h1>服务监控</h1>
        <p>面向企业内网运维排障，聚焦主机资源、JVM 水位、线程、磁盘和 GC 状态。</p>
      </div>
    </div>

    <div class="resource-metrics is-four">
      <div v-for="item in metrics" :key="item.label" class="resource-metric">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
      </div>
    </div>

    <div v-if="errorMessage" class="inline-error">
      <span>{{ errorMessage }}</span>
      <el-button link type="primary" :loading="loading" @click="loadOverview">重试</el-button>
    </div>

    <div class="monitor-chart-grid">
      <section class="surface monitor-panel chart-panel">
        <div class="section-head">
          <div>
            <h2>资源水位</h2>
            <p>CPU、JVM、物理内存和磁盘最高水位。</p>
          </div>
        </div>
        <EasyChart :option="resourceUsageChartOption" chart-label="服务资源水位图" />
      </section>

      <section class="surface monitor-panel chart-panel">
        <div class="section-head">
          <div>
            <h2>JVM 内存结构</h2>
            <p>堆与非堆内存已用和剩余空间。</p>
          </div>
        </div>
        <EasyChart :option="memoryStructureChartOption" chart-label="JVM 内存结构图" />
      </section>

      <section class="surface monitor-panel chart-panel">
        <div class="section-head">
          <div>
            <h2>磁盘容量</h2>
            <p>按分区查看已用和可用容量。</p>
          </div>
        </div>
        <EasyChart :option="diskUsageChartOption" chart-label="磁盘容量水位图" />
      </section>

      <section class="surface monitor-panel chart-panel">
        <div class="section-head">
          <div>
            <h2>线程结构</h2>
            <p>活动线程中的业务线程和守护线程占比。</p>
          </div>
        </div>
        <EasyChart :option="threadStructureChartOption" chart-label="线程结构图" />
      </section>
    </div>

    <section class="surface monitor-panel monitor-focus-panel">
      <div class="section-head">
        <div>
          <h2>运维关注项</h2>
          <p>按资源水位和运行态风险给出当前页最需要先看的位置。</p>
        </div>
        <span class="sample-time">采样 {{ overview?.sampleTime || '-' }}</span>
      </div>
      <div v-loading="loading" class="attention-grid">
        <div v-for="item in attentionItems" :key="item.label" class="attention-item">
          <div>
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
          <el-tag :type="item.type" effect="light">{{ item.status }}</el-tag>
          <p>{{ item.description }}</p>
        </div>
      </div>
    </section>

    <div class="dashboard-layout monitor-topology">
      <section class="surface monitor-panel">
        <div class="section-head">
          <div>
            <h2>CPU 与线程</h2>
            <p>当前进程负载、系统负载和线程数量。</p>
          </div>
        </div>
        <div v-loading="loading" class="metric-table">
          <div v-for="item in cpuRows" :key="item.label" class="metric-row">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </div>
      </section>

      <section class="surface monitor-panel">
        <div class="section-head">
          <div>
            <h2>内存水位</h2>
            <p>JVM、非堆和物理内存使用情况。</p>
          </div>
        </div>
        <div v-loading="loading" class="memory-grid">
          <div v-for="item in memoryCards" :key="item.label" class="memory-card">
            <div class="memory-card-head">
              <span>{{ item.label }}</span>
              <strong>{{ formatPercent(item.percent) }}</strong>
            </div>
            <el-progress
              :percentage="safePercent(item.percent)"
              :status="progressStatus(item.percent)"
              :stroke-width="8"
            />
            <div class="memory-card-foot">
              <span>总量 {{ item.total }}</span>
              <span>已用 {{ item.used }}</span>
              <span>剩余 {{ item.free }}</span>
            </div>
          </div>
        </div>
      </section>
    </div>

    <div class="dashboard-layout monitor-topology">
      <section class="surface monitor-panel">
        <div class="section-head">
          <div>
            <h2>服务器信息</h2>
            <p>当前应用所在主机基础信息。</p>
          </div>
        </div>
        <div class="metric-table">
          <div v-for="item in serverRows" :key="item.label" class="metric-row">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </div>
      </section>

      <section class="surface monitor-panel">
        <div class="section-head">
          <div>
            <h2>Java 虚拟机信息</h2>
            <p>JVM、启动时间、运行时长和项目路径。</p>
          </div>
        </div>
        <div class="metric-table">
          <div v-for="item in javaRows" :key="item.label" class="metric-row">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </div>
      </section>
    </div>

    <section class="surface monitor-panel">
      <div class="section-head">
        <div>
          <h2>磁盘状态</h2>
          <p>展示服务所在主机可见磁盘分区。</p>
        </div>
      </div>
      <el-table v-loading="loading" :data="overview?.disks || []" class="admin-table" empty-text="暂无磁盘数据">
        <el-table-column prop="path" label="盘符路径" min-width="120" />
        <el-table-column prop="fileSystem" label="文件系统" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.fileSystem || '-' }}</template>
        </el-table-column>
        <el-table-column prop="type" label="类型" width="120">
          <template #default="{ row }">{{ row.type || '-' }}</template>
        </el-table-column>
        <el-table-column label="总大小" width="130">
          <template #default="{ row }">{{ formatBytes(row.totalBytes) }}</template>
        </el-table-column>
        <el-table-column label="可用大小" width="130">
          <template #default="{ row }">{{ formatBytes(row.usableBytes) }}</template>
        </el-table-column>
        <el-table-column label="已用大小" width="130">
          <template #default="{ row }">{{ formatBytes(row.usedBytes) }}</template>
        </el-table-column>
        <el-table-column label="已用百分比" min-width="180">
          <template #default="{ row }">
            <div class="table-meter">
              <el-progress
                :percentage="safePercent(row.usagePercent)"
                :status="progressStatus(row.usagePercent)"
                :stroke-width="7"
              />
              <span>{{ formatPercent(row.usagePercent) }}</span>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <div class="dashboard-layout dashboard-lower">
      <section class="surface monitor-panel">
        <div class="section-head">
          <div>
            <h2>GC 统计</h2>
            <p>垃圾回收次数和累计耗时。</p>
          </div>
        </div>
        <EasyChart :option="gcChartOption" chart-label="GC 统计图" />
        <el-table :data="overview?.garbageCollectors || []" class="admin-table compact-gc-table" height="180" empty-text="暂无 GC 数据">
          <el-table-column prop="name" label="收集器" min-width="160" show-overflow-tooltip />
          <el-table-column label="次数" width="110">
            <template #default="{ row }">{{ normalizeCount(row.collectionCount) }}</template>
          </el-table-column>
          <el-table-column label="耗时" width="120">
            <template #default="{ row }">{{ normalizeMillis(row.collectionTimeMillis) }}</template>
          </el-table-column>
        </el-table>
      </section>

      <section class="surface monitor-panel">
        <div class="section-head">
          <div>
            <h2>运行参数</h2>
            <p>Java 启动参数和运行路径，便于排查部署差异。</p>
          </div>
        </div>
        <div class="runtime-list">
          <div v-for="item in overview?.runtime || []" :key="item.label" class="runtime-item">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </div>
        <div class="jvm-args">{{ overview?.java?.inputArguments || '未配置额外启动参数' }}</div>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { EChartsCoreOption } from 'echarts/core'
import EasyChart from '@/components/charts/EasyChart.vue'
import { getSystemStatusOverview } from '@/features/monitor/api'
import type { DiskMetrics, GarbageCollectorMetrics, NumericStat, SystemStatusOverview } from '@/features/monitor/types'

const loading = ref(false)
const errorMessage = ref('')
const overview = ref<SystemStatusOverview>()

const metrics = computed(() => [
  { label: '健康状态', value: healthStatusText.value },
  { label: '运行时长', value: overview.value?.uptime || '-' },
  { label: 'CPU 使用率', value: formatPercent(overview.value?.cpu?.systemCpuUsagePercent) },
  { label: '磁盘最高水位', value: formatPercent(maxDiskUsagePercent.value) }
])

const healthStatusText = computed(() => {
  if (!overview.value) return '加载中'
  if (overview.value.healthy) return '健康'
  return overview.value.status || '异常'
})

const maxDiskUsagePercent = computed(() => {
  const values = (overview.value?.disks || [])
    .map((disk) => toNumber(disk.usagePercent))
    .filter((value): value is number => value !== undefined && value >= 0)
  return values.length ? Math.max(...values) : undefined
})

const highestDisk = computed(() => {
  return (overview.value?.disks || []).reduce<DiskMetrics | undefined>((selected, disk) => {
    if (!selected) return disk
    return safePercent(disk.usagePercent) > safePercent(selected.usagePercent) ? disk : selected
  }, undefined)
})

const totalGcCount = computed(() => sumNumbers(overview.value?.garbageCollectors || [], 'collectionCount'))
const totalGcTimeMillis = computed(() => sumNumbers(overview.value?.garbageCollectors || [], 'collectionTimeMillis'))

const attentionItems = computed(() => [
  {
    label: '服务健康',
    value: healthStatusText.value,
    status: overview.value?.healthy ? '正常' : '需关注',
    type: overview.value?.healthy ? 'success' : 'danger',
    description: overview.value?.healthy ? '基础运行状态正常。' : '健康状态非 UP，请优先查看 WebLog 和最近发布变更。'
  },
  {
    label: 'CPU 压力',
    value: formatPercent(overview.value?.cpu?.systemCpuUsagePercent),
    status: riskLabel(overview.value?.cpu?.systemCpuUsagePercent),
    type: riskTagType(overview.value?.cpu?.systemCpuUsagePercent),
    description: '持续高于 70% 建议检查慢接口、批处理任务或外部调用阻塞。'
  },
  {
    label: 'JVM 堆内存',
    value: formatPercent(overview.value?.memory?.heapUsagePercent),
    status: riskLabel(overview.value?.memory?.heapUsagePercent),
    type: riskTagType(overview.value?.memory?.heapUsagePercent),
    description: '接近 85% 时关注对象膨胀、缓存策略和 Full GC 风险。'
  },
  {
    label: '磁盘容量',
    value: highestDisk.value ? `${highestDisk.value.path} ${formatPercent(highestDisk.value.usagePercent)}` : '-',
    status: riskLabel(maxDiskUsagePercent.value),
    type: riskTagType(maxDiskUsagePercent.value),
    description: '日志、上传文件和临时导出目录会优先消耗磁盘空间。'
  },
  {
    label: '线程峰值',
    value: overview.value?.threads ? `${overview.value.threads.live}/${overview.value.threads.peak}` : '-',
    status: threadRiskType.value === 'success' ? '正常' : '需关注',
    type: threadRiskType.value,
    description: '活动线程持续接近峰值时，检查异步线程池、长事务和慢 IO。'
  },
  {
    label: 'GC 累计耗时',
    value: normalizeMillis(totalGcTimeMillis.value),
    status: totalGcTimeMillis.value > 30000 ? '需关注' : '正常',
    type: totalGcTimeMillis.value > 30000 ? 'warning' : 'success',
    description: '累计耗时过高时，结合堆内存水位和请求峰值判断是否需要调优。'
  }
])

const threadRiskType = computed(() => {
  const live = toNumber(overview.value?.threads?.live)
  const peak = toNumber(overview.value?.threads?.peak)
  if (!live || !peak || peak <= 0) return 'success'
  return live / peak >= 0.85 ? 'warning' : 'success'
})

const cpuRows = computed(() => {
  const cpu = overview.value?.cpu
  const threads = overview.value?.threads
  return [
    { label: 'CPU 核心数', value: cpu?.processors ?? '-' },
    { label: '系统使用率', value: formatPercent(cpu?.systemCpuUsagePercent) },
    { label: '进程使用率', value: formatPercent(cpu?.processCpuUsagePercent) },
    { label: '当前空闲率', value: formatPercent(cpu?.idleCpuUsagePercent) },
    { label: '系统负载', value: formatNumber(cpu?.systemLoadAverage) },
    { label: '活动线程', value: threads?.live ?? '-' },
    { label: '守护线程', value: threads?.daemon ?? '-' },
    { label: '峰值线程', value: threads?.peak ?? '-' }
  ]
})

const memoryCards = computed(() => {
  const memory = overview.value?.memory
  return [
    {
      label: 'JVM 堆内存',
      total: formatBytes(memory?.heapMaxBytes),
      used: formatBytes(memory?.heapUsedBytes),
      free: formatBytes(memory?.heapFreeBytes),
      percent: memory?.heapUsagePercent
    },
    {
      label: 'JVM 非堆内存',
      total: formatBytes(memory?.nonHeapCommittedBytes),
      used: formatBytes(memory?.nonHeapUsedBytes),
      free: formatBytes(minus(memory?.nonHeapCommittedBytes, memory?.nonHeapUsedBytes)),
      percent: memory?.nonHeapUsagePercent
    },
    {
      label: '物理内存',
      total: formatBytes(memory?.physicalTotalBytes),
      used: formatBytes(memory?.physicalUsedBytes),
      free: formatBytes(memory?.physicalFreeBytes),
      percent: memory?.physicalUsagePercent
    }
  ]
})

const serverRows = computed(() => {
  const server = overview.value?.server
  return [
    { label: '服务器名称', value: server?.name || '-' },
    { label: '服务器 IP', value: server?.ip || '-' },
    { label: '应用名称', value: overview.value?.applicationName || '-' },
    { label: '运行环境', value: overview.value?.activeProfiles || '-' },
    { label: '操作系统', value: server ? `${server.osName} ${server.osVersion}` : '-' },
    { label: '系统架构', value: server?.osArch || '-' },
    { label: 'CPU 核心', value: server?.processors ?? '-' }
  ]
})

const javaRows = computed(() => {
  const java = overview.value?.java
  return [
    { label: 'Java 名称', value: java?.name || '-' },
    { label: 'Java 版本', value: java ? `${java.version} / ${java.vendor}` : '-' },
    { label: '启动时间', value: java?.startTime || overview.value?.startTime || '-' },
    { label: '运行时长', value: java?.runTime || overview.value?.uptime || '-' },
    { label: '安装路径', value: java?.home || '-' },
    { label: '项目路径', value: java?.projectDir || '-' }
  ]
})

const resourceUsageChartOption = computed<EChartsCoreOption>(() => ({
  tooltip: { trigger: 'axis', valueFormatter: (value: number) => `${value}%` },
  grid: { left: 96, right: 22, top: 16, bottom: 20 },
  xAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
  yAxis: {
    type: 'category',
    data: ['CPU', 'JVM 堆', '物理内存', '磁盘最高'],
    axisTick: { show: false }
  },
  series: [
    {
      name: '使用率',
      type: 'bar',
      barWidth: 14,
      data: [
        safePercent(overview.value?.cpu?.systemCpuUsagePercent),
        safePercent(overview.value?.memory?.heapUsagePercent),
        safePercent(overview.value?.memory?.physicalUsagePercent),
        safePercent(maxDiskUsagePercent.value)
      ],
      itemStyle: { color: '#2563eb', borderRadius: [0, 6, 6, 0] }
    }
  ]
}))

const memoryStructureChartOption = computed<EChartsCoreOption>(() => {
  const memory = overview.value?.memory
  const heapUsed = bytesToMb(memory?.heapUsedBytes)
  const heapFree = bytesToMb(memory?.heapFreeBytes)
  const nonHeapUsed = bytesToMb(memory?.nonHeapUsedBytes)
  const nonHeapFree = bytesToMb(minus(memory?.nonHeapCommittedBytes, memory?.nonHeapUsedBytes))
  return {
    tooltip: { trigger: 'item', valueFormatter: (value: number) => `${value.toFixed(1)} MB` },
    legend: { bottom: 0, left: 'center' },
    series: [
      {
        name: 'JVM 内存',
        type: 'pie',
        radius: ['48%', '72%'],
        center: ['50%', '43%'],
        avoidLabelOverlap: true,
        data: [
          { name: '堆已用', value: heapUsed },
          { name: '堆剩余', value: heapFree },
          { name: '非堆已用', value: nonHeapUsed },
          { name: '非堆剩余', value: nonHeapFree }
        ].filter((item) => item.value > 0)
      }
    ]
  }
})

const diskUsageChartOption = computed<EChartsCoreOption>(() => {
  const disks = overview.value?.disks || []
  return {
    tooltip: { trigger: 'axis', valueFormatter: (value: number) => `${value.toFixed(1)} GB` },
    legend: { bottom: 0 },
    grid: { left: 92, right: 20, top: 18, bottom: 42 },
    xAxis: { type: 'value' },
    yAxis: {
      type: 'category',
      data: disks.map((disk) => disk.path),
      axisTick: { show: false }
    },
    series: [
      {
        name: '已用',
        type: 'bar',
        stack: 'disk',
        barWidth: 14,
        data: disks.map((disk) => bytesToGb(disk.usedBytes)),
        itemStyle: { color: '#0f766e', borderRadius: [0, 0, 0, 0] }
      },
      {
        name: '可用',
        type: 'bar',
        stack: 'disk',
        barWidth: 14,
        data: disks.map((disk) => bytesToGb(disk.usableBytes)),
        itemStyle: { color: '#dbeafe', borderRadius: [0, 6, 6, 0] }
      }
    ]
  }
})

const threadStructureChartOption = computed<EChartsCoreOption>(() => {
  const live = toNumber(overview.value?.threads?.live) || 0
  const daemon = toNumber(overview.value?.threads?.daemon) || 0
  const business = Math.max(live - daemon, 0)
  return {
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, left: 'center' },
    series: [
      {
        name: '线程结构',
        type: 'pie',
        radius: ['48%', '72%'],
        center: ['50%', '43%'],
        data: [
          { name: '业务线程', value: business },
          { name: '守护线程', value: daemon }
        ].filter((item) => item.value > 0)
      }
    ]
  }
})

const gcChartOption = computed<EChartsCoreOption>(() => {
  const collectors = overview.value?.garbageCollectors || []
  return {
    tooltip: { trigger: 'axis' },
    legend: { bottom: 0 },
    grid: { left: 92, right: 20, top: 18, bottom: 42 },
    xAxis: { type: 'value' },
    yAxis: {
      type: 'category',
      data: collectors.map((item) => item.name),
      axisTick: { show: false }
    },
    series: [
      {
        name: '次数',
        type: 'bar',
        barWidth: 12,
        data: collectors.map((item) => toNumber(item.collectionCount) || 0),
        itemStyle: { color: '#7c3aed', borderRadius: [0, 6, 6, 0] }
      },
      {
        name: '耗时秒',
        type: 'bar',
        barWidth: 12,
        data: collectors.map((item) => Math.round(((toNumber(item.collectionTimeMillis) || 0) / 1000) * 10) / 10),
        itemStyle: { color: '#f59e0b', borderRadius: [0, 6, 6, 0] }
      }
    ]
  }
})

async function loadOverview() {
  loading.value = true
  errorMessage.value = ''
  try {
    overview.value = await getSystemStatusOverview()
  } catch (error) {
    errorMessage.value = resolveError(error, '服务监控加载失败')
  } finally {
    loading.value = false
  }
}

function progressStatus(value?: NumericStat) {
  const percent = safePercent(value)
  if (percent >= 85) return 'exception'
  if (percent >= 70) return 'warning'
  return undefined
}

function safePercent(value?: NumericStat) {
  const numberValue = toNumber(value)
  if (numberValue === undefined || numberValue < 0) return 0
  return Math.min(100, Math.max(0, Math.round(numberValue)))
}

function formatPercent(value?: NumericStat) {
  const numberValue = toNumber(value)
  if (numberValue === undefined || numberValue < 0) return '-'
  return `${safePercent(numberValue)}%`
}

function riskLabel(value?: NumericStat) {
  const percent = safePercent(value)
  if (percent >= 85) return '高风险'
  if (percent >= 70) return '需关注'
  return '正常'
}

function riskTagType(value?: NumericStat) {
  const percent = safePercent(value)
  if (percent >= 85) return 'danger'
  if (percent >= 70) return 'warning'
  return 'success'
}

function formatBytes(value?: NumericStat) {
  const numberValue = toNumber(value)
  if (numberValue === undefined || numberValue < 0) return '-'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let size = numberValue
  let unitIndex = 0
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024
    unitIndex += 1
  }
  return `${size >= 10 || unitIndex === 0 ? size.toFixed(0) : size.toFixed(1)} ${units[unitIndex]}`
}

function normalizeCount(value: NumericStat) {
  const numberValue = toNumber(value)
  return numberValue === undefined || numberValue < 0 ? '-' : numberValue.toLocaleString('zh-CN')
}

function normalizeMillis(value?: NumericStat) {
  const numberValue = toNumber(value)
  return numberValue === undefined || numberValue < 0 ? '-' : `${numberValue.toLocaleString('zh-CN')}ms`
}

function formatNumber(value?: NumericStat) {
  const numberValue = toNumber(value)
  if (numberValue === undefined || numberValue < 0) return '-'
  return numberValue.toLocaleString('zh-CN')
}

function minus(left?: NumericStat, right?: NumericStat) {
  const leftValue = toNumber(left)
  const rightValue = toNumber(right)
  if (leftValue === undefined || rightValue === undefined || leftValue < 0 || rightValue < 0) return undefined
  return Math.max(leftValue - rightValue, 0)
}

function bytesToMb(value?: NumericStat) {
  const numberValue = toNumber(value)
  if (numberValue === undefined || numberValue <= 0) return 0
  return Math.round((numberValue / 1024 / 1024) * 10) / 10
}

function bytesToGb(value?: NumericStat) {
  const numberValue = toNumber(value)
  if (numberValue === undefined || numberValue <= 0) return 0
  return Math.round((numberValue / 1024 / 1024 / 1024) * 10) / 10
}

function sumNumbers<T extends GarbageCollectorMetrics>(items: T[], key: keyof T) {
  return items.reduce((sum, item) => sum + Math.max(toNumber(item[key] as NumericStat) || 0, 0), 0)
}

function toNumber(value?: NumericStat) {
  if (typeof value === 'number') return value
  if (typeof value !== 'string' || !value.trim()) return undefined
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : undefined
}

function resolveError(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

onMounted(loadOverview)
</script>

<style scoped>
.monitor-page {
  --monitor-soft: #f8fafc;
}

.inline-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
  padding: 10px 14px;
  border: 1px solid var(--el-color-danger-light-5);
  border-radius: 8px;
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}

.monitor-topology,
.dashboard-lower {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.monitor-panel {
  min-width: 0;
}

.monitor-focus-panel {
  margin-bottom: 16px;
}

.sample-time {
  color: var(--ea-muted);
  font-size: 13px;
}

.attention-grid,
.monitor-chart-grid {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.attention-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px 12px;
  align-items: start;
  padding: 14px;
  border: 1px solid var(--ea-border);
  border-radius: 8px;
  background: var(--monitor-soft);
}

.attention-item div {
  min-width: 0;
}

.attention-item span {
  display: block;
  color: var(--ea-muted);
  font-size: 13px;
}

.attention-item strong {
  display: block;
  margin-top: 4px;
  color: var(--ea-text);
  font-size: 18px;
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}

.attention-item p {
  grid-column: 1 / -1;
  margin: 0;
  color: var(--ea-muted);
  font-size: 13px;
  line-height: 1.6;
}

.chart-panel {
  min-height: 380px;
}

.metric-table {
  display: grid;
  border: 1px solid var(--ea-border);
  border-radius: 8px;
  overflow: hidden;
}

.metric-row {
  display: grid;
  grid-template-columns: 140px minmax(0, 1fr);
  min-height: 46px;
  border-bottom: 1px solid var(--ea-border);
}

.metric-row:last-child {
  border-bottom: 0;
}

.metric-row span,
.metric-row strong {
  display: flex;
  align-items: center;
  min-width: 0;
  padding: 11px 14px;
}

.metric-row span {
  background: var(--monitor-soft);
  color: var(--ea-muted);
}

.metric-row strong {
  color: var(--ea-text);
  font-variant-numeric: tabular-nums;
  word-break: break-word;
}

.memory-grid {
  display: grid;
  gap: 12px;
}

.memory-card {
  display: grid;
  gap: 10px;
  padding: 14px;
  border: 1px solid var(--ea-border);
  border-radius: 8px;
  background: #fbfdff;
}

.memory-card-head,
.memory-card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.memory-card-head span,
.memory-card-foot {
  color: var(--ea-muted);
}

.memory-card-head strong {
  color: var(--ea-text);
  font-size: 18px;
  font-variant-numeric: tabular-nums;
}

.memory-card-foot {
  flex-wrap: wrap;
  font-size: 12px;
}

.table-meter {
  display: grid;
  gap: 6px;
}

.table-meter span {
  color: var(--ea-muted);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.compact-gc-table {
  margin-top: 12px;
}

.runtime-list {
  display: grid;
  gap: 8px;
  margin-bottom: 12px;
}

.runtime-item {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr);
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--ea-border);
  border-radius: 8px;
  background: var(--monitor-soft);
}

.runtime-item span {
  color: var(--ea-muted);
}

.runtime-item strong {
  color: var(--ea-text);
  font-weight: 600;
  word-break: break-word;
}

.jvm-args {
  min-height: 138px;
  padding: 14px;
  border: 1px solid var(--ea-border);
  border-radius: 8px;
  background: var(--monitor-soft);
  color: var(--ea-text);
  line-height: 1.7;
  word-break: break-all;
}

@media (max-width: 1180px) {
  .monitor-topology,
  .dashboard-lower,
  .attention-grid,
  .monitor-chart-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .metric-row,
  .runtime-item {
    grid-template-columns: 1fr;
  }

  .metric-row span {
    padding-bottom: 4px;
  }

  .metric-row strong {
    padding-top: 4px;
  }
}
</style>

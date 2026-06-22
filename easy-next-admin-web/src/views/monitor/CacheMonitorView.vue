<template>
  <section class="resource-page cache-page">
    <div class="resource-hero">
      <div>
        <h1>缓存监控</h1>
        <p>查看缓存作用域、容量策略、访问命中、淘汰风险和治理建议，支持按缓存名称清理运行态缓存。</p>
      </div>
    </div>

    <section class="surface cache-overview-panel">
      <div class="cache-runtime-line">
        <div class="cache-runtime-title">
          <span>运行概览</span>
          <strong>{{ scopeLabel }}</strong>
          <small>{{ providerLabel(overview?.provider) }} · 采样 {{ overview?.sampleTime || '-' }}</small>
        </div>
        <div class="cache-runtime-facts">
          <span>统计 <strong>{{ overview?.statisticsAvailable ? '已开启' : '未暴露' }}</strong></span>
          <span>容量上限 <strong>{{ formatCount(overview?.totalMaximumSize) }}</strong></span>
          <span>风险 <strong>{{ formatCount(overview?.warningCount) }}</strong></span>
        </div>
      </div>

      <div class="cache-metrics">
        <div v-for="item in metricCards" :key="item.label" :class="['cache-metric', `is-${item.tone}`]">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
          <small>{{ item.hint }}</small>
        </div>
      </div>

      <div class="cache-ops-line">
        <div class="cache-spotlight">
          <span>重点关注</span>
          <strong>{{ cacheDisplayName(overview?.busiestCacheName) }}</strong>
          <small>访问最多</small>
          <strong>{{ cacheDisplayName(overview?.largestCacheName) }}</strong>
          <small>容量最大</small>
        </div>
        <div class="cache-recommendation-line">
          <span>治理建议</span>
          <p>{{ primaryRecommendation }}</p>
        </div>
      </div>
    </section>

    <section ref="tablePanelRef" class="surface resource-panel cache-table-panel is-fluid-table">
      <div class="section-head cache-section-head">
        <div class="cache-section-title">
          <h2>缓存列表</h2>
          <span>按名称、实现类、健康状态筛选，清理前确认作用域。</span>
        </div>
        <div class="cache-table-tools">
          <el-input
            v-model="keyword"
            class="cache-search"
            clearable
            :prefix-icon="Search"
            placeholder="搜索缓存名称 / 实现类"
          />
          <el-radio-group v-model="statusFilter" size="small">
            <el-radio-button v-for="item in statusOptions" :key="item.value" :value="item.value">
              {{ item.label }}
            </el-radio-button>
          </el-radio-group>
        </div>
      </div>

      <div class="cache-inline-alert" role="note">
        <el-icon><WarningFilled /></el-icon>
        <span>清理缓存会影响当前作用域命中；分布式缓存会影响共享缓存。</span>
      </div>

      <el-table v-loading="loading" :data="filteredCaches" row-key="name" :height="tableHeight" class="admin-table" empty-text="暂无缓存数据">
        <el-table-column label="缓存名称" min-width="230">
          <template #default="{ row }">
            <div class="entity-cell">
              <span class="entity-avatar is-role">{{ row.name.slice(0, 1).toUpperCase() }}</span>
              <span>
                <strong>{{ cacheDisplayName(row.name) }}</strong>
                <small>{{ row.name }} · {{ providerLabel(row.provider || overview?.provider) }}</small>
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="健康状态" width="118">
          <template #default="{ row }">
            <el-tag :type="riskTagType(row)" effect="plain">{{ row.healthLabel || '未评估' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="容量 / 策略" min-width="190">
          <template #default="{ row }">
            <div class="cache-policy-cell">
              <strong>{{ formatCapacity(row) }}</strong>
              <small>TTL {{ formatDuration(row.ttlSeconds) }} · MaxIdle {{ formatDuration(row.maxIdleSeconds) }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="容量使用率" min-width="170">
          <template #default="{ row }">
            <div class="table-meter">
              <el-progress :percentage="safePercent(row.usageRate)" :stroke-width="7" :show-text="false" />
              <span>{{ formatPercent(row.usageRate) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="请求 / 命中" min-width="160">
          <template #default="{ row }">
            <div class="cache-policy-cell">
              <strong>{{ formatCount(row.requestCount) }} / {{ formatCount(row.hitCount) }}</strong>
              <small>未命中 {{ formatCount(row.missCount) }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="命中率" min-width="170">
          <template #default="{ row }">
            <div class="table-meter">
              <el-progress :percentage="safePercent(row.hitRate)" :stroke-width="7" :show-text="false" />
              <span>{{ row.statisticsAvailable ? formatPercent(row.hitRate) : '未暴露统计' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="淘汰 / 风险" min-width="180">
          <template #default="{ row }">
            <div class="cache-risk-cell">
              <strong>{{ formatCount(row.evictionCount) }}</strong>
              <small>{{ row.description || '-' }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="158" fixed="right" align="center" header-align="center">
          <template #default="{ row }">
            <div class="cache-row-actions">
              <el-button class="cache-action-button" text type="primary" :icon="View" @click="openDetail(row)">
                详情
              </el-button>
              <el-dropdown class="cache-row-more" trigger="click" placement="bottom-end">
                <el-button class="cache-more-button" text :icon="MoreFilled" title="更多操作" aria-label="更多操作" @click.stop>
                  更多
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu class="cache-row-action-menu">
                    <el-dropdown-item
                      class="is-danger"
                      :disabled="!canClearCache || clearingName === row.name"
                      :title="canClearCache ? undefined : '缺少清理缓存权限'"
                      @click.stop="handleClear(row)"
                    >
                      <el-icon><Delete /></el-icon>
                      <span>清理缓存</span>
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-drawer v-model="detailVisible" title="缓存详情" size="min(540px, 92vw)">
      <template v-if="activeCache">
        <div class="cache-detail-head">
          <el-tag :type="riskTagType(activeCache)" effect="plain">{{ activeCache.healthLabel || '未评估' }}</el-tag>
          <strong>{{ cacheDisplayName(activeCache.name) }}</strong>
          <p>{{ activeCache.name }} · {{ activeCache.description || '暂无风险说明。' }}</p>
        </div>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="缓存实现">{{ providerLabel(activeCache.provider || overview?.provider) }}</el-descriptions-item>
          <el-descriptions-item label="底层类型">{{ activeCache.nativeClass || '-' }}</el-descriptions-item>
          <el-descriptions-item label="作用域">{{ scopeLabel }}</el-descriptions-item>
          <el-descriptions-item label="容量">{{ formatCapacity(activeCache) }}</el-descriptions-item>
          <el-descriptions-item label="TTL">{{ formatDuration(activeCache.ttlSeconds) }}</el-descriptions-item>
          <el-descriptions-item label="MaxIdle">{{ formatDuration(activeCache.maxIdleSeconds) }}</el-descriptions-item>
          <el-descriptions-item label="请求 / 命中 / 未命中">
            {{ formatCount(activeCache.requestCount) }} / {{ formatCount(activeCache.hitCount) }} / {{ formatCount(activeCache.missCount) }}
          </el-descriptions-item>
          <el-descriptions-item label="命中率 / 未命中率">
            {{ activeCache.statisticsAvailable ? formatPercent(activeCache.hitRate) : '未暴露统计' }} /
            {{ activeCache.statisticsAvailable ? formatPercent(activeCache.missRate) : '未暴露统计' }}
          </el-descriptions-item>
          <el-descriptions-item label="淘汰次数">{{ formatCount(activeCache.evictionCount) }}</el-descriptions-item>
        </el-descriptions>
      </template>
      <el-empty v-else description="未选择缓存" />
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Delete, MoreFilled, Search, View, WarningFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { clearMonitorCache, getCacheMonitorOverview } from '@/features/monitor/api'
import type { CacheMonitorItem, CacheMonitorOverview, NumericStat } from '@/features/monitor/types'
import { PermissionCodes } from '@/permissions/codes'
import { useAuthStore } from '@/stores/auth'
import { useFluidTableHeight } from '@/components/table/useFluidTableHeight'

type StatusFilter = 'ALL' | 'WARNING' | 'HEALTHY' | 'IDLE'

const loading = ref(false)
const clearingName = ref('')
const overview = ref<CacheMonitorOverview>()
const keyword = ref('')
const statusFilter = ref<StatusFilter>('ALL')
const activeCache = ref<CacheMonitorItem>()
const detailVisible = ref(false)
const tablePanelRef = ref<HTMLElement>()
const authStore = useAuthStore()
const { tableHeight, updateTableHeight } = useFluidTableHeight(tablePanelRef, { footerSelector: false, minHeight: 120 })

const caches = computed(() => overview.value?.caches || [])
const canClearCache = computed(() => authStore.hasAnyPermission([PermissionCodes.monitor.cacheClear]))
const warningCaches = computed(() => caches.value.filter((item) => isWarning(item)))
const filteredCaches = computed(() => {
  const term = keyword.value.trim().toLowerCase()
  return caches.value.filter((item) => {
    const matchesKeyword = !term
      || item.name.toLowerCase().includes(term)
      || (item.nativeClass || '').toLowerCase().includes(term)
      || (item.provider || '').toLowerCase().includes(term)
    const matchesStatus = statusFilter.value === 'ALL'
      || (statusFilter.value === 'WARNING' && isWarning(item))
      || (statusFilter.value === 'HEALTHY' && item.healthStatus === 'HEALTHY')
      || (statusFilter.value === 'IDLE' && ['IDLE', 'UNKNOWN'].includes(item.healthStatus || ''))
    return matchesKeyword && matchesStatus
  })
})

const scopeLabel = computed(() => overview.value?.scope === 'DISTRIBUTED' ? '分布式缓存' : '本地缓存')

const metricCards = computed(() => [
  {
    label: '缓存数量',
    value: formatCount(overview.value?.cacheCount),
    hint: `当前展示 ${formatCount(filteredCaches.value.length)} 个`,
    tone: 'info'
  },
  {
    label: '容量使用率',
    value: formatPercent(overview.value?.usageRate),
    hint: `${formatCount(overview.value?.totalEstimatedSize)} / ${formatCount(overview.value?.totalMaximumSize)}`,
    tone: (toNumber(overview.value?.usageRate) ?? 0) >= 90 ? 'danger' : 'info'
  },
  {
    label: '总请求',
    value: formatCount(overview.value?.totalRequestCount),
    hint: `未命中 ${formatCount(overview.value?.totalMissCount)}`,
    tone: 'info'
  },
  {
    label: '总命中率',
    value: overview.value?.statisticsAvailable ? formatPercent(overview.value?.hitRate) : '未暴露',
    hint: `请求 ${formatCount(overview.value?.totalRequestCount)} 次`,
    tone: (toNumber(overview.value?.hitRate) ?? 100) < 50 && (toNumber(overview.value?.totalRequestCount) ?? 0) > 0 ? 'warning' : 'success'
  },
  {
    label: '风险缓存',
    value: formatCount(overview.value?.warningCount),
    hint: warningCaches.value.map((item) => cacheDisplayName(item.name)).slice(0, 2).join('、') || '暂无风险',
    tone: warningCaches.value.length ? 'warning' : 'success'
  },
  {
    label: '淘汰次数',
    value: formatCount(overview.value?.totalEvictionCount),
    hint: '容量或过期触发的回收',
    tone: (toNumber(overview.value?.totalEvictionCount) ?? 0) > 0 ? 'warning' : 'info'
  }
])

const recommendations = computed(() => overview.value?.recommendations?.length
  ? overview.value.recommendations
  : ['暂无治理建议，刷新后再查看当前缓存状态。'])
const primaryRecommendation = computed(() => recommendations.value[0])

const statusOptions = computed(() => [
  { value: 'ALL', label: `全部 ${caches.value.length}` },
  { value: 'WARNING', label: `风险 ${warningCaches.value.length}` },
  { value: 'HEALTHY', label: `正常 ${caches.value.filter((item) => item.healthStatus === 'HEALTHY').length}` },
  { value: 'IDLE', label: `待预热 ${caches.value.filter((item) => ['IDLE', 'UNKNOWN'].includes(item.healthStatus || '')).length}` }
])

async function loadOverview() {
  loading.value = true
  try {
    overview.value = await getCacheMonitorOverview()
  } catch (error) {
    ElMessage.error(resolveError(error, '缓存监控加载失败'))
  } finally {
    loading.value = false
    updateTableHeight()
  }
}

function openDetail(cache: CacheMonitorItem) {
  activeCache.value = cache
  detailVisible.value = true
}

async function handleClear(cache: CacheMonitorItem) {
  const impact = overview.value?.scope === 'DISTRIBUTED'
    ? '这会清理分布式共享缓存，可能影响所有应用实例。'
    : '这只会清理当前节点的本地缓存，多实例环境下其他节点不受影响。'
  try {
    await ElMessageBox.confirm(`确认清理 ${cacheDisplayName(cache.name)}？${impact}`, '清理缓存', {
      type: 'warning',
      confirmButtonText: '确认清理',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  clearingName.value = cache.name
  try {
    await clearMonitorCache(cache.name)
    ElMessage.success('缓存已清理')
    await loadOverview()
  } catch (error) {
    ElMessage.error(resolveError(error, '清理缓存失败'))
  } finally {
    clearingName.value = ''
  }
}

function isWarning(cache: CacheMonitorItem) {
  return ['CAPACITY_RISK', 'EVICTING', 'LOW_HIT'].includes(cache.healthStatus || '')
}

function riskTagType(cache: CacheMonitorItem) {
  if (cache.riskLevel === 'danger') return 'danger'
  if (cache.riskLevel === 'warning') return 'warning'
  if (cache.riskLevel === 'success') return 'success'
  return 'info'
}

function safePercent(value?: NumericStat) {
  const numberValue = toNumber(value)
  if (numberValue === undefined || numberValue < 0) return 0
  return Math.min(100, Math.max(0, Math.round(numberValue)))
}

function formatPercent(value?: NumericStat) {
  const numberValue = toNumber(value)
  if (numberValue === undefined || numberValue < 0) return '-'
  return `${safePercent(value)}%`
}

function formatCount(value?: NumericStat) {
  const numberValue = toNumber(value)
  if (numberValue === undefined) return '-'
  return numberValue.toLocaleString('zh-CN')
}

function formatCapacity(cache: CacheMonitorItem) {
  const estimatedSize = formatCount(cache.estimatedSize)
  const maximumSize = formatCount(cache.maximumSize)
  return maximumSize === '-' ? estimatedSize : `${estimatedSize} / ${maximumSize}`
}

function formatDuration(value?: NumericStat) {
  const seconds = toNumber(value)
  if (seconds === undefined || seconds <= 0) return '-'
  if (seconds % 86400 === 0) return `${seconds / 86400} 天`
  if (seconds % 3600 === 0) return `${seconds / 3600} 小时`
  if (seconds % 60 === 0) return `${seconds / 60} 分钟`
  return `${seconds} 秒`
}

function cacheDisplayName(name?: string) {
  if (!name) return '-'
  const normalized = name.replace(/^CACHE_NAME_/i, '')
  if (/^\d+H$/i.test(normalized)) return `通用业务缓存 ${normalized.replace(/H/i, '小时')}`
  if (/^\d+D$/i.test(normalized)) return `通用业务缓存 ${normalized.replace(/D/i, '天')}`
  return name
}

function providerLabel(value?: string) {
  if (!value) return '-'
  if (value.includes('Redisson')) return '分布式缓存'
  if (value.includes('Caffeine')) return '本地缓存'
  if (value.includes('ConcurrentMap')) return '本地缓存'
  return value
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
.cache-page {
  gap: 10px;
}

.cache-page .resource-hero {
  padding: 0 2px;
}

.cache-page .resource-hero h1 {
  margin-bottom: 4px;
  font-size: 22px;
}

.cache-page .resource-hero p {
  font-size: 13px;
  line-height: 1.5;
}

.cache-overview-panel {
  display: grid;
  gap: 10px;
  padding: 12px 16px;
}

.cache-runtime-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-width: 0;
  padding-bottom: 8px;
  border-bottom: 1px solid #edf2f7;
}

.cache-runtime-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
  min-width: 0;
}

.cache-runtime-title span {
  flex: 0 0 auto;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

.cache-runtime-title strong {
  flex: 0 0 auto;
  color: var(--ea-text);
  font-size: 16px;
}

.cache-runtime-title small {
  min-width: 0;
  overflow: hidden;
  color: var(--ea-muted);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cache-runtime-facts {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px 14px;
  color: var(--ea-muted);
  font-size: 12px;
}

.cache-runtime-facts strong {
  margin-left: 4px;
  color: var(--ea-text);
  font-weight: 700;
}

.cache-metrics {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 0;
}

.cache-metric {
  min-width: 0;
  padding: 0 12px;
  border-left: 1px solid #e8eef7;
}

.cache-metric:first-child {
  padding-left: 0;
  border-left: 0;
}

.cache-metric span,
.cache-metric strong,
.cache-metric small {
  display: block;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cache-metric span {
  color: var(--ea-muted);
  font-size: 12px;
}

.cache-metric strong {
  margin-top: 4px;
  color: var(--ea-text);
  font-size: 18px;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}

.cache-metric small {
  margin-top: 3px;
  color: var(--ea-muted);
  font-size: 12px;
}

.cache-metric.is-success strong {
  color: #15803d;
}

.cache-metric.is-warning strong {
  color: #b45309;
}

.cache-metric.is-danger strong {
  color: #dc2626;
}

.cache-ops-line {
  display: grid;
  grid-template-columns: minmax(340px, 0.9fr) minmax(0, 1.5fr);
  gap: 10px;
}

.cache-spotlight,
.cache-recommendation-line {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  padding: 7px 10px;
  border: 1px solid #e6edf6;
  border-radius: 6px;
  background: #f8fafc;
  color: var(--ea-muted);
  font-size: 12px;
}

.cache-spotlight span,
.cache-recommendation-line span {
  flex: 0 0 auto;
  color: #475569;
  font-weight: 700;
}

.cache-spotlight strong {
  min-width: 0;
  max-width: 180px;
  overflow: hidden;
  color: var(--ea-text);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cache-spotlight small {
  flex: 0 0 auto;
}

.cache-recommendation-line p {
  min-width: 0;
  margin: 0;
  overflow: hidden;
  color: #475569;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cache-table-panel {
  min-height: 420px;
}

.cache-section-head {
  align-items: center;
  gap: 14px;
}

.cache-section-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
  min-width: 0;
}

.cache-section-title span {
  min-width: 0;
  overflow: hidden;
  color: var(--ea-muted);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cache-table-tools {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
  max-width: 720px;
}

.cache-search {
  width: 240px;
}

.cache-inline-alert {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 32px;
  margin: 0 16px 10px;
  padding: 6px 10px;
  border: 1px solid #fde7bd;
  border-radius: 6px;
  background: #fffbf3;
  color: #9a5800;
  font-size: 13px;
}

.cache-inline-alert .el-icon {
  flex: 0 0 auto;
  color: #d97706;
}

.cache-table-panel :deep(.el-table__header th.el-table__cell) {
  padding: 8px 0;
}

.cache-table-panel :deep(.el-table__body td.el-table__cell) {
  padding: 7px 0;
}

.cache-policy-cell,
.cache-risk-cell {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.cache-policy-cell strong,
.cache-risk-cell strong {
  color: var(--ea-text);
  font-variant-numeric: tabular-nums;
}

.cache-policy-cell small,
.cache-risk-cell small {
  overflow: hidden;
  color: var(--ea-muted);
  font-size: 12px;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
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

.cache-row-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  width: 100%;
  min-width: 0;
  white-space: nowrap;
}

.cache-action-button {
  padding-right: 5px;
  padding-left: 5px;
  font-weight: 600;
}

.cache-row-actions :deep(.cache-more-button.el-button) {
  --el-button-text-color: #64748b;
  --el-button-hover-text-color: var(--ea-primary);
  --el-button-active-text-color: #64748b;
  color: #64748b;
  font-weight: 600;
}

.cache-row-actions :deep(.cache-more-button.el-button .el-icon),
.cache-row-actions :deep(.cache-more-button.el-button span) {
  color: inherit;
}

.cache-row-actions :deep(.cache-more-button.el-button:hover) {
  color: var(--ea-primary);
}

.cache-row-actions :deep(.cache-more-button.el-button:focus:not(:hover)),
.cache-row-actions :deep(.cache-more-button.el-button:active:not(:hover)) {
  color: #64748b;
}

.cache-row-more {
  display: inline-flex;
}

:global(.cache-row-action-menu .el-dropdown-menu__item) {
  gap: 8px;
  min-width: 132px;
  font-weight: 600;
}

:global(.cache-row-action-menu .el-dropdown-menu__item .el-icon) {
  margin-right: 0;
}

:global(.cache-row-action-menu .el-dropdown-menu__item.is-danger:not(.is-disabled)) {
  color: #dc2626;
}

.cache-detail-head {
  display: grid;
  gap: 10px;
  margin-bottom: 16px;
}

.cache-detail-head strong {
  color: var(--ea-text);
  font-size: 18px;
}

.cache-detail-head p {
  margin: 0;
  color: var(--ea-muted);
  line-height: 1.6;
}

@media (max-width: 1380px) {
  .cache-metrics {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    row-gap: 10px;
  }

  .cache-metric:nth-child(3n + 1) {
    padding-left: 0;
    border-left: 0;
  }

  .cache-ops-line {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .cache-runtime-line {
    display: grid;
  }

  .cache-runtime-facts {
    justify-content: flex-start;
  }

  .cache-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .cache-metric:nth-child(3n + 1) {
    padding-left: 12px;
    border-left: 1px solid #e8eef7;
  }

  .cache-metric:nth-child(2n + 1) {
    padding-left: 0;
    border-left: 0;
  }

  .cache-spotlight {
    flex-wrap: wrap;
  }

  .cache-section-head,
  .cache-table-tools {
    display: grid;
    justify-content: stretch;
  }

  .cache-search {
    width: 100%;
  }
}

@media (max-width: 520px) {
  .cache-metrics {
    grid-template-columns: 1fr;
  }

  .cache-metric,
  .cache-metric:nth-child(3n + 1),
  .cache-metric:nth-child(2n + 1) {
    padding-left: 0;
    border-left: 0;
  }

  .cache-section-title,
  .cache-runtime-title {
    display: grid;
    gap: 4px;
  }
}
</style>

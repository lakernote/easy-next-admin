<template>
  <section class="resource-page cache-list-page">
    <div class="resource-hero">
      <div>
        <h1>缓存列表</h1>
        <p>按缓存名称查看运行态 key 和脱敏 value 预览，支持精确清理单个 key，避免误清整组缓存。</p>
      </div>
    </div>

    <div class="cache-browser">
      <section class="surface cache-panel cache-name-panel">
        <div class="cache-panel-head">
          <div>
            <h2>缓存名称</h2>
            <p>{{ providerLabel(overview?.provider) }} · {{ overview?.cacheCount || 0 }} 个缓存</p>
          </div>
          <el-button circle text :icon="Refresh" :loading="loadingOverview" @click="loadOverview" />
        </div>
        <el-input
          v-model="cacheKeyword"
          class="cache-filter-input"
          clearable
          :prefix-icon="Search"
          placeholder="搜索缓存名称"
        />
        <div v-loading="loadingOverview" class="cache-name-list">
          <button
            v-for="cache in filteredCaches"
            :key="cache.name"
            type="button"
            :class="['cache-name-item', { 'is-active': cache.name === selectedCacheName }]"
            @click="selectCache(cache.name)"
          >
            <span class="cache-name-icon">
              <el-icon><Coin /></el-icon>
            </span>
            <span>
              <strong>{{ cacheDisplayName(cache.name) }}</strong>
              <small>{{ cache.name }} · {{ formatCount(cache.estimatedSize) }} keys · TTL {{ formatDuration(cache.ttlSeconds) }}</small>
            </span>
            <el-tag :type="riskTagType(cache)" effect="plain" size="small">{{ cache.healthLabel || '未评估' }}</el-tag>
          </button>
          <el-empty v-if="!filteredCaches.length && !loadingOverview" description="暂无缓存" />
        </div>
      </section>

      <section class="surface cache-panel cache-key-panel">
        <div class="cache-panel-head">
          <div>
            <h2>键名列表</h2>
            <p>{{ selectedCacheName || '未选择缓存' }} · {{ entryPage?.total || 0 }} 个匹配 key</p>
          </div>
          <div class="cache-panel-actions">
            <el-select v-model="entryLimit" class="cache-limit-select" @change="loadEntries">
              <el-option :value="10" label="10" />
              <el-option :value="20" label="20" />
              <el-option :value="50" label="50" />
            </el-select>
            <el-button circle text :icon="Refresh" :loading="loadingEntries" @click="loadEntries" />
          </div>
        </div>
        <div class="cache-key-filter">
          <el-input
            v-model="entryKeyword"
            clearable
            :prefix-icon="Search"
            placeholder="搜索缓存 key"
            @keyup.enter="loadEntries"
          />
          <el-button type="primary" plain :icon="Search" :loading="loadingEntries" @click="loadEntries">查询</el-button>
        </div>
        <el-alert
          v-if="entryPage?.truncated"
          class="cache-key-alert"
          type="info"
          show-icon
          :closable="false"
          title="结果较多，仅展示当前限制内的 key；可继续输入关键字缩小范围。"
        />
        <div v-loading="loadingEntries" class="cache-key-list">
          <button
            v-for="entry in entryPage?.entries || []"
            :key="entry.key"
            type="button"
            :class="['cache-key-item', { 'is-active': entry.key === activeEntry?.key }]"
            @click="selectEntry(entry.key)"
          >
            <span class="cache-key-icon">
              <el-icon><Key /></el-icon>
            </span>
            <span>
              <strong>{{ entry.key }}</strong>
              <small>{{ entry.valueType || '-' }} · key {{ entry.keyType || '-' }}</small>
            </span>
            <el-button
              v-permission:disable="{ permissions: PermissionCodes.monitor.cacheClear, reason: '缺少清理缓存权限' }"
              circle
              text
              type="danger"
              :icon="Delete"
              :loading="evictingKey === entry.key"
              @click.stop="confirmEvict(entry.key)"
            />
          </button>
          <el-empty v-if="selectedCacheName && !(entryPage?.entries || []).length && !loadingEntries" description="暂无 key" />
          <el-empty v-if="!selectedCacheName && !loadingEntries" description="请选择左侧缓存" />
        </div>
      </section>

      <section class="surface cache-panel cache-value-panel">
        <div class="cache-panel-head">
          <div>
            <h2>缓存内容</h2>
            <p>{{ activeEntry?.key || '选择 key 后查看 value' }}</p>
          </div>
          <el-button :disabled="!activeEntry?.valuePreview" @click="copyValue">复制脱敏内容</el-button>
        </div>
        <dl class="cache-value-meta">
          <div>
            <dt>缓存名称</dt>
            <dd>{{ entryPage?.cacheName || '-' }}</dd>
          </div>
          <div>
            <dt>缓存键名</dt>
            <dd>{{ activeEntry?.key || '-' }}</dd>
          </div>
          <div>
            <dt>值类型</dt>
            <dd>{{ activeEntry?.valueType || '-' }}</dd>
          </div>
          <div>
            <dt>预览口径</dt>
            <dd>脱敏</dd>
          </div>
          <div>
            <dt>底层实现</dt>
            <dd>{{ providerLabel(entryPage?.nativeClass) }}</dd>
          </div>
        </dl>
        <pre v-if="activeEntry" class="cache-value-preview">{{ activeEntry.valuePreview }}</pre>
        <el-alert
          v-if="activeEntry?.valueTruncated"
          class="cache-value-alert"
          type="warning"
          show-icon
          :closable="false"
          title="内容较长，当前仅展示脱敏后的前 12000 个字符。"
        />
        <el-empty v-if="!activeEntry" description="未选择缓存内容" />
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Coin, Delete, Key, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { evictCacheEntry, getCacheMonitorOverview, listCacheEntries } from '@/features/monitor/api'
import type { CacheEntryPage, CacheMonitorItem, CacheMonitorOverview, NumericStat } from '@/features/monitor/types'
import { PermissionCodes } from '@/permissions/codes'

const loadingOverview = ref(false)
const loadingEntries = ref(false)
const evictingKey = ref('')
const overview = ref<CacheMonitorOverview>()
const entryPage = ref<CacheEntryPage>()
const selectedCacheName = ref('')
const selectedKey = ref('')
const cacheKeyword = ref('')
const entryKeyword = ref('')
const entryLimit = ref(10)

const caches = computed(() => overview.value?.caches || [])
const filteredCaches = computed(() => {
  const term = cacheKeyword.value.trim().toLowerCase()
  if (!term) return caches.value
  return caches.value.filter((item) => item.name.toLowerCase().includes(term))
})
const activeEntry = computed(() => entryPage.value?.selected || entryPage.value?.entries?.[0])
const scopeLabel = computed(() => overview.value?.scope === 'DISTRIBUTED' ? '分布式缓存' : '本地缓存')

async function loadOverview() {
  loadingOverview.value = true
  try {
    overview.value = await getCacheMonitorOverview()
    if (!selectedCacheName.value && caches.value.length) {
      selectedCacheName.value = caches.value[0].name
      await loadEntries()
    }
  } catch (error) {
    ElMessage.error(resolveError(error, '缓存列表加载失败'))
  } finally {
    loadingOverview.value = false
  }
}

async function loadEntries() {
  if (!selectedCacheName.value) return
  loadingEntries.value = true
  try {
    entryPage.value = await listCacheEntries(selectedCacheName.value, {
      keyword: entryKeyword.value.trim() || undefined,
      selectedKey: selectedKey.value || undefined,
      limit: entryLimit.value
    })
    selectedKey.value = entryPage.value.selectedKey || entryPage.value.entries[0]?.key || ''
  } catch (error) {
    ElMessage.error(resolveError(error, '缓存键值加载失败'))
  } finally {
    loadingEntries.value = false
  }
}

async function selectCache(cacheName: string) {
  if (selectedCacheName.value === cacheName) return
  selectedCacheName.value = cacheName
  selectedKey.value = ''
  entryKeyword.value = ''
  entryPage.value = undefined
  await loadEntries()
}

async function selectEntry(key: string) {
  selectedKey.value = key
  await loadEntries()
}

async function confirmEvict(key: string) {
  if (!selectedCacheName.value) return
  try {
    await ElMessageBox.confirm(`确认清理缓存 ${selectedCacheName.value} 下的 key：${key}？`, '清理缓存键', {
      type: 'warning',
      confirmButtonText: '确认清理',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  evictingKey.value = key
  try {
    await evictCacheEntry(selectedCacheName.value, key)
    ElMessage.success('缓存键已清理')
    selectedKey.value = ''
    await Promise.all([loadOverview(), loadEntries()])
  } catch (error) {
    ElMessage.error(resolveError(error, '清理缓存键失败'))
  } finally {
    evictingKey.value = ''
  }
}

async function copyValue() {
  if (!activeEntry.value?.valuePreview) return
  await navigator.clipboard.writeText(activeEntry.value.valuePreview)
  ElMessage.success('缓存内容已复制')
}

function riskTagType(cache: CacheMonitorItem) {
  if (cache.riskLevel === 'danger') return 'danger'
  if (cache.riskLevel === 'warning') return 'warning'
  if (cache.riskLevel === 'success') return 'success'
  return 'info'
}

function formatCount(value?: NumericStat) {
  const numberValue = toNumber(value)
  if (numberValue === undefined) return '-'
  return numberValue.toLocaleString('zh-CN')
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
.cache-list-page {
  gap: 14px;
}

.cache-browser {
  display: grid;
  grid-template-columns: minmax(260px, 0.9fr) minmax(360px, 1.05fr) minmax(420px, 1.2fr);
  gap: 14px;
  min-height: calc(100vh - 238px);
}

.cache-panel {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  padding: 16px;
}

.cache-panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.cache-panel-head h2 {
  margin: 0;
  color: var(--ea-text);
  font-size: 17px;
  line-height: 1.3;
}

.cache-panel-head p {
  margin: 5px 0 0;
  color: var(--ea-muted);
  font-size: 13px;
}

.cache-panel-actions,
.cache-key-filter {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cache-filter-input,
.cache-key-filter {
  margin-bottom: 12px;
}

.cache-limit-select {
  width: 82px;
}

.cache-name-list,
.cache-key-list {
  flex: 1;
  min-height: 220px;
  overflow: auto;
}

.cache-name-item,
.cache-key-item {
  display: grid;
  width: 100%;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  padding: 10px;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.cache-key-item {
  grid-template-columns: auto minmax(0, 1fr) auto;
}

.cache-name-item + .cache-name-item,
.cache-key-item + .cache-key-item {
  margin-top: 6px;
}

.cache-name-item:hover,
.cache-key-item:hover,
.cache-name-item.is-active,
.cache-key-item.is-active {
  border-color: #bfdbfe;
  background: #eff6ff;
}

.cache-name-icon,
.cache-key-icon {
  display: inline-flex;
  width: 36px;
  height: 36px;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: #eef6ff;
  color: #2563eb;
  font-size: 16px;
}

.cache-name-item strong,
.cache-key-item strong,
.cache-name-item small,
.cache-key-item small {
  display: block;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cache-name-item strong,
.cache-key-item strong {
  color: var(--ea-text);
  font-size: 14px;
}

.cache-name-item small,
.cache-key-item small {
  margin-top: 3px;
  color: var(--ea-muted);
  font-size: 12px;
}

.cache-key-alert,
.cache-value-alert {
  margin-bottom: 10px;
}

.cache-value-meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin: 0 0 12px;
}

.cache-value-meta div {
  min-width: 0;
  border: 1px solid var(--ea-border);
  border-radius: 8px;
  padding: 10px 12px;
  background: #f8fafc;
}

.cache-value-meta dt {
  color: var(--ea-muted);
  font-size: 12px;
}

.cache-value-meta dd {
  min-width: 0;
  margin: 6px 0 0;
  overflow: hidden;
  color: var(--ea-text);
  font-size: 13px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cache-value-preview {
  flex: 1;
  min-height: 240px;
  margin: 0;
  overflow: auto;
  border: 1px solid var(--ea-border);
  border-radius: 8px;
  background: #0f172a;
  color: #dbeafe;
  font-family: 'JetBrains Mono', 'SFMono-Regular', Consolas, monospace;
  font-size: 12px;
  line-height: 1.7;
  padding: 14px;
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 1280px) {
  .cache-browser {
    grid-template-columns: 1fr;
  }

  .cache-panel {
    min-height: 360px;
  }
}
</style>

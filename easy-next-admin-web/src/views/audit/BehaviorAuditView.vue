<template>
  <section class="resource-page audit-page">
    <div class="resource-hero">
      <div>
        <h1>审计中心</h1>
        <p>面向内控、合规和追责留痕，聚合登录、关键操作、数据变更、异常和接口访问记录。</p>
      </div>
    </div>

    <div v-if="errorMessage" class="inline-error">
      <span>{{ errorMessage }}</span>
      <el-button link type="primary" :loading="loading" @click="loadLogs">重试</el-button>
    </div>

    <section ref="auditPanelRef" class="surface resource-panel audit-panel is-fluid-table">
      <div class="audit-tabs-bar">
        <el-tabs v-model="activeTab" @tab-change="handleTabChange">
          <el-tab-pane v-for="tab in auditTabs" :key="tab.key" :label="tab.label" :name="tab.key" />
        </el-tabs>
        <div class="audit-snapshot" aria-label="当前审计概览">
          <span v-for="item in auditSnapshot" :key="item.label" :class="{ 'is-danger': item.danger }">
            <strong>{{ item.value }}</strong>
            {{ item.label }}
          </span>
        </div>
      </div>

      <div class="table-control-row audit-control-row">
        <el-form :inline="true" class="filter-bar audit-filter-bar">
          <el-form-item :label="activeMeta.keywordLabel">
            <el-input v-model="query.keyword" :placeholder="activeMeta.keywordPlaceholder" clearable @keyup.enter="searchLogs" />
          </el-form-item>
          <el-form-item v-if="activeMeta.stateKey && activeMeta.stateOptions?.length" :label="activeMeta.stateLabel">
            <el-select v-model="query.state" placeholder="全部" clearable>
              <el-option v-for="option in activeMeta.stateOptions" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
          </el-form-item>
          <el-form-item v-else-if="activeMeta.stateKey" :label="activeMeta.stateLabel">
            <el-input v-model="query.state" placeholder="输入类型" clearable @keyup.enter="searchLogs" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" plain :icon="Search" :loading="loading" @click="searchLogs">查询</el-button>
            <el-button @click="resetAuditFilters">重置</el-button>
          </el-form-item>
        </el-form>
        <TableToolbar v-model:columns="columns" class="table-toolbar-inline" />
      </div>

      <el-table v-loading="loading" :data="rows" row-key="id" :height="tableHeight" class="admin-table audit-table" :empty-text="activeMeta.emptyText">
        <el-table-column v-if="visibleColumns.time" prop="time" label="审计时间" width="170" />
        <el-table-column v-if="visibleColumns.actor" prop="actor" label="操作人" width="130" show-overflow-tooltip />
        <el-table-column v-if="visibleColumns.event" prop="event" label="事件" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="audit-event-cell">
              <strong>{{ row.event }}</strong>
              <small v-if="row.summary !== '-'">{{ row.summary }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="visibleColumns.target" prop="target" label="审计对象" min-width="220" show-overflow-tooltip />
        <el-table-column v-if="visibleColumns.source" prop="source" label="来源" width="150" show-overflow-tooltip />
        <el-table-column v-if="visibleColumns.result" prop="result" label="结果" width="92">
          <template #default="{ row }">
            <el-tag :type="row.resultType" effect="plain">{{ row.result }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="92" fixed="right" align="center" header-align="center">
          <template #default="{ row }">
            <el-button text type="primary" :icon="View" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer is-split audit-table-footer">
        <span>共 {{ total }} 条</span>
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.limit"
          background
          layout="sizes, prev, pager, next"
          :page-sizes="[10, 20, 50]"
          :total="total"
          @size-change="handlePageSizeChange"
          @current-change="loadLogs"
        />
      </div>
    </section>

    <el-drawer v-model="detailVisible" :title="detailTitle" size="min(560px, 92vw)" append-to-body>
      <el-descriptions :column="1" border class="audit-detail-list">
        <el-descriptions-item v-for="item in detailItems" :key="item.label" :label="item.label">
          <pre v-if="item.code" class="audit-detail-code">{{ item.value }}</pre>
          <span v-else>{{ item.value }}</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import type { TabsPaneContext } from 'element-plus'
import { Search, View } from '@element-plus/icons-vue'
import TableToolbar from '@/components/table/TableToolbar.vue'
import { createTableColumnState, visibleColumnMap } from '@/components/table/tableColumns'
import {
  pageApiAuditLogs,
  pageDataChangeAuditLogs,
  pageErrorAuditLogs,
  pageLoginAuditLogs,
  pageOperationAuditLogs
} from '@/features/audit/api'
import {
  AUDIT_TABS,
  auditDetailItems,
  toAuditRows,
  type AuditLogRecord,
  type AuditRow,
  type AuditTabKey
} from '@/features/audit/auditRows'
import type { AuditPageQuery } from '@/features/audit/types'
import { useFluidTableHeight } from '@/components/table/useFluidTableHeight'

const auditTabs = AUDIT_TABS
const loading = ref(false)
const activeTab = ref<AuditTabKey>('operation')
const query = reactive({
  page: 1,
  limit: 10,
  keyword: '',
  state: ''
})
const rows = ref<AuditRow[]>([])
const total = ref(0)
const errorMessage = ref('')
const detailVisible = ref(false)
const selectedRow = ref<AuditRow>()
const auditPanelRef = ref<HTMLElement>()
const { tableHeight, updateTableHeight } = useFluidTableHeight(auditPanelRef)
const columns = ref(createTableColumnState([
  { key: 'time', label: '审计时间' },
  { key: 'actor', label: '操作人', required: true },
  { key: 'event', label: '事件', required: true },
  { key: 'target', label: '审计对象' },
  { key: 'source', label: '来源' },
  { key: 'result', label: '结果' }
]))

const activeMeta = computed(() => auditTabs.find((tab) => tab.key === activeTab.value) || auditTabs[0])
const visibleColumns = computed(() => visibleColumnMap(columns.value))
const detailTitle = computed(() => (selectedRow.value ? `${activeMeta.value.label}详情` : '审计详情'))
const detailItems = computed(() => auditDetailItems(activeTab.value, selectedRow.value))

const riskyCount = computed(() => rows.value.filter((row) => row.resultType === 'danger').length)
const auditSnapshot = computed(() => {
  return [
    { label: '当前页 / 总数', value: `${formatCount(rows.value.length)} / ${formatCount(total.value)}` },
    { label: '异常 / 失败', value: formatCount(riskyCount.value), danger: riskyCount.value > 0 }
  ]
})

onMounted(() => {
  void loadLogs()
})

async function loadLogs() {
  loading.value = true
  errorMessage.value = ''
  try {
    const params = buildQuery()
    const result = await loadByTab(activeTab.value, params)
    rows.value = toAuditRows(activeTab.value, result.list as AuditLogRecord[])
    total.value = result.total
  } catch (error) {
    errorMessage.value = resolveError(error, '审计记录加载失败')
  } finally {
    loading.value = false
    updateTableHeight()
  }
}

function searchLogs() {
  query.page = 1
  loadLogs()
}

function resetAuditFilters() {
  query.keyword = ''
  query.state = ''
  query.page = 1
  loadLogs()
}

function handleTabChange(name: TabsPaneContext['paneName']) {
  activeTab.value = name as AuditTabKey
  query.page = 1
  query.state = ''
  selectedRow.value = undefined
  detailVisible.value = false
  loadLogs()
  updateTableHeight()
}

function handlePageSizeChange() {
  query.page = 1
  loadLogs()
  updateTableHeight()
}

function openDetail(row: AuditRow) {
  selectedRow.value = row
  detailVisible.value = true
}

function buildQuery(): AuditPageQuery {
  const params: AuditPageQuery = {
    page: query.page,
    limit: query.limit,
    keyWord: query.keyword
  }
  if (activeMeta.value.stateKey && query.state) {
    params[activeMeta.value.stateKey] = query.state
  }
  return params
}

async function loadByTab(tab: AuditTabKey, params: AuditPageQuery) {
  if (tab === 'login') return pageLoginAuditLogs(params)
  if (tab === 'dataChange') return pageDataChangeAuditLogs(params)
  if (tab === 'error') return pageErrorAuditLogs(params)
  if (tab === 'api') return pageApiAuditLogs(params)
  return pageOperationAuditLogs(params)
}

function formatCount(value?: number) {
  if (typeof value !== 'number') return '-'
  return value.toLocaleString('zh-CN')
}

function resolveError(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}
</script>

<style scoped>
.audit-page {
  gap: 12px;
  overflow: hidden;
}

.audit-page .resource-hero {
  padding-top: 0;
}

.audit-page .resource-hero p {
  line-height: 1.5;
}

.audit-panel {
  border-color: var(--ea-border-strong, var(--ea-border));
}

.audit-tabs-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex: 0 0 auto;
  padding: 0 16px;
  border-bottom: 1px solid var(--ea-border);
  background: #fff;
}

.audit-tabs-bar :deep(.el-tabs) {
  flex: 1 1 auto;
  min-width: 0;
}

.audit-tabs-bar :deep(.el-tabs__header) {
  margin: 0;
}

.audit-tabs-bar :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.audit-tabs-bar :deep(.el-tabs__item) {
  height: 50px;
  font-weight: 700;
  color: var(--ea-muted);
}

.audit-tabs-bar :deep(.el-tabs__item.is-active) {
  color: var(--ea-primary);
}

.audit-snapshot {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
  color: var(--ea-muted);
  font-size: 12px;
  white-space: nowrap;
}

.audit-snapshot span {
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
  min-height: 28px;
  padding: 0 10px;
  border: 1px solid #dbe4f0;
  border-radius: 999px;
  background: #f8fafc;
}

.audit-snapshot strong {
  color: var(--ea-text);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}

.audit-snapshot span.is-danger {
  border-color: var(--el-color-danger-light-5);
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}

.audit-snapshot span.is-danger strong {
  color: var(--el-color-danger);
}

.audit-control-row {
  padding: 10px 16px;
}

.audit-filter-bar {
  align-items: center;
}

.audit-filter-bar :deep(.el-form-item__label) {
  color: #475569;
  font-weight: 700;
}

.audit-filter-bar :deep(.el-input) {
  width: 300px;
}

.audit-filter-bar :deep(.el-select) {
  width: 160px;
}

.audit-event-cell {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.audit-event-cell strong {
  overflow: hidden;
  color: var(--ea-text);
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.audit-event-cell small {
  overflow: hidden;
  color: var(--ea-muted);
  font-size: 12px;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
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

.audit-detail-list :deep(.el-descriptions__label) {
  width: 110px;
  color: var(--ea-muted);
  font-weight: 700;
}

.audit-detail-code {
  max-height: 220px;
  margin: 0;
  overflow: auto;
  color: var(--ea-text);
  font-family: var(--ea-code-font, ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace);
  font-size: 12px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 768px) {
  .audit-tabs-bar {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
    padding: 0 12px 10px;
  }

  .audit-tabs-bar :deep(.el-tabs) {
    width: 100%;
  }

  .audit-snapshot {
    justify-content: flex-start;
  }

  .audit-filter-bar :deep(.el-input),
  .audit-filter-bar :deep(.el-select) {
    width: 100%;
  }
}
</style>

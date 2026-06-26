<template>
  <section class="resource-page batch-page">
    <div class="resource-hero">
      <div>
        <h1>批处理任务</h1>
        <p>统一查看批量导入、数据同步和报表生成等长任务的进度、失败明细和治理动作。</p>
      </div>
      <div class="resource-actions">
        <el-button :icon="Refresh" :loading="loading" @click="loadPage">刷新</el-button>
      </div>
    </div>

    <div class="resource-metrics is-four">
      <div class="resource-metric">
        <span>任务总数</span>
        <strong>{{ taskTotal }}</strong>
      </div>
      <div class="resource-metric">
        <span>本页执行中</span>
        <strong>{{ runningCount }}</strong>
      </div>
      <div class="resource-metric">
        <span>本页异常</span>
        <strong>{{ unhealthyCount }}</strong>
      </div>
      <div class="resource-metric">
        <span>本页取消中</span>
        <strong>{{ cancelingCount }}</strong>
      </div>
    </div>

    <section ref="tablePanelRef" class="surface resource-panel batch-task-list is-fluid-table">
      <div class="table-control-row">
        <el-form :inline="true" class="filter-bar batch-filter-bar" @submit.prevent>
          <el-form-item label="关键字">
            <el-input v-model.trim="query.keyword" clearable placeholder="任务名称 / 类型 / 幂等键" @keyup.enter="searchTasks" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" clearable placeholder="全部状态">
              <el-option v-for="item in taskStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="触发">
            <el-select v-model="query.triggerType" clearable placeholder="全部触发">
              <el-option v-for="item in triggerOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" plain :icon="Search" :loading="taskLoading" @click="searchTasks">查询</el-button>
            <el-button @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
        <TableToolbar v-model:columns="taskColumns" class="table-toolbar-inline" />
      </div>
      <el-table
        v-loading="taskLoading"
        :data="tasks"
        row-key="id"
        :height="tableHeight"
        class="admin-table batch-task-table"
        empty-text="暂无批处理任务"
      >
        <el-table-column v-if="visibleTaskColumns.task" label="任务" min-width="240">
          <template #default="{ row }">
            <div class="batch-main-cell">
              <strong>{{ row.taskName }}</strong>
              <small>{{ row.taskType }}<span v-if="row.businessKey"> · {{ row.businessKey }}</span></small>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="visibleTaskColumns.status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="taskStatusType(row.status)" effect="plain">{{ taskStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="visibleTaskColumns.progress" label="进度" min-width="170">
          <template #default="{ row }">
            <div class="batch-progress-cell">
              <el-progress :percentage="row.progressPercent || 0" :stroke-width="8" :show-text="false" />
              <span>{{ row.progressPercent || 0 }}%</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="visibleTaskColumns.counts" label="数量" min-width="170">
          <template #default="{ row }">
            <div class="batch-counts">
              <span>总 {{ row.totalCount || 0 }}</span>
              <span class="is-success">成 {{ row.successCount || 0 }}</span>
              <span class="is-danger">败 {{ row.failedCount || 0 }}</span>
              <span v-if="row.skippedCount" class="is-skipped">跳 {{ row.skippedCount }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="visibleTaskColumns.trigger" label="触发来源" min-width="150">
          <template #default="{ row }">
            <div class="batch-main-cell">
              <strong>{{ triggerText(row.triggerType) }}</strong>
              <small>{{ row.triggerRefId || '-' }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="visibleTaskColumns.time" label="时间" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="batch-main-cell">
              <strong>{{ row.startedAt || row.createTime || '-' }}</strong>
              <small>{{ row.finishedAt || '未结束' }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right" align="center" header-align="center">
          <template #default="{ row }">
            <div class="row-actions batch-row-actions">
              <el-button text :icon="View" @click="openDetail(row)">明细</el-button>
              <el-button
                v-if="canRetry(row)"
                v-permission:disable="PermissionCodes.batch.taskManage"
                text
                :icon="RefreshRight"
                :loading="actingTaskId === row.id"
                @click="retryFailed(row)"
              >
                重试
              </el-button>
              <el-button
                v-if="canCancel(row)"
                v-permission:disable="PermissionCodes.batch.taskManage"
                text
                type="danger"
                :icon="CloseBold"
                :loading="actingTaskId === row.id"
                @click="cancelTask(row)"
              >
                {{ cancelActionText(row) }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <div class="table-footer is-split batch-table-footer">
        <span>共 {{ taskTotal }} 个任务</span>
        <el-pagination
          v-model:current-page="taskPage"
          v-model:page-size="taskPageSize"
          background
          layout="sizes, prev, pager, next"
          :total="taskTotal"
          :page-sizes="[10, 20, 50]"
          @current-change="loadTasks"
          @size-change="handleTaskSizeChange"
        />
      </div>
    </section>

    <el-drawer v-model="detailVisible" :title="detailTitle" size="min(920px, 94vw)" class="batch-detail-drawer" @opened="updateItemTableHeight">
      <section class="batch-detail-meta">
        <dl>
          <div>
            <dt>状态</dt>
            <dd><el-tag :type="taskStatusType(selectedTask?.status)" effect="plain">{{ taskStatusText(selectedTask?.status) }}</el-tag></dd>
          </div>
          <div>
            <dt>进度</dt>
            <dd>{{ selectedTask?.progressPercent || 0 }}%</dd>
          </div>
          <div>
            <dt>traceId</dt>
            <dd>{{ selectedTask?.traceId || '-' }}</dd>
          </div>
          <div>
            <dt>异常/取消原因</dt>
            <dd>{{ selectedTask?.errorMessage || '-' }}</dd>
          </div>
        </dl>
      </section>

      <section ref="itemPanelRef" class="batch-item-panel">
        <div class="table-control-row batch-item-filter-row">
          <el-form :inline="true" class="filter-bar batch-item-filter-bar" @submit.prevent>
            <el-form-item label="关键字">
              <el-input v-model.trim="itemQuery.keyword" clearable placeholder="明细 key / 名称" @keyup.enter="searchItems" />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="itemQuery.status" clearable placeholder="全部明细状态">
                <el-option v-for="item in itemStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" plain :icon="Search" :loading="itemLoading" @click="searchItems">查询</el-button>
            </el-form-item>
          </el-form>
        </div>
        <el-table v-loading="itemLoading" :data="items" :height="itemTableHeight" class="admin-table" empty-text="暂无明细">
          <el-table-column label="明细" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="batch-main-cell">
                <strong>{{ row.itemName || row.itemKey }}</strong>
                <small>{{ row.itemKey }}</small>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="itemStatusType(row.status)" effect="plain">{{ itemStatusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="retryCount" label="重试" width="80" />
          <el-table-column label="结果 / 失败原因" min-width="240" show-overflow-tooltip>
            <template #default="{ row }">{{ row.errorMessage || row.resultMessage || '-' }}</template>
          </el-table-column>
          <el-table-column label="完成时间" width="168" show-overflow-tooltip>
            <template #default="{ row }">{{ row.finishedAt || '-' }}</template>
          </el-table-column>
        </el-table>
        <div class="table-footer is-split batch-item-footer">
          <span>共 {{ itemTotal }} 条明细</span>
          <el-pagination
            v-model:current-page="itemPage"
            v-model:page-size="itemPageSize"
            background
            layout="sizes, prev, pager, next"
            :total="itemTotal"
            :page-sizes="[10, 20, 50]"
            @current-change="loadItems"
            @size-change="handleItemSizeChange"
          />
        </div>
      </section>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { CloseBold, Refresh, RefreshRight, Search, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import TableToolbar from '@/components/table/TableToolbar.vue'
import { createTableColumnState, visibleColumnMap } from '@/components/table/tableColumns'
import { useFluidTableHeight } from '@/components/table/useFluidTableHeight'
import { PermissionCodes } from '@/permissions/codes'
import { cancelBatchTask, pageBatchTaskItems, pageBatchTasks, retryFailedBatchItems } from '@/features/batch/api'
import type { BatchTask, BatchTaskItem } from '@/features/batch/types'

const taskLoading = ref(false)
const itemLoading = ref(false)
const detailVisible = ref(false)
const actingTaskId = ref<BatchTask['id']>()
const tablePanelRef = ref<HTMLElement>()
const itemPanelRef = ref<HTMLElement>()
const { tableHeight, updateTableHeight } = useFluidTableHeight(tablePanelRef)
const { tableHeight: itemTableHeight, updateTableHeight: updateItemTableHeight } = useFluidTableHeight(itemPanelRef, {
  footerSelector: '.batch-item-footer',
  bottomGap: 0,
  minHeight: 300
})

const tasks = ref<BatchTask[]>([])
const items = ref<BatchTaskItem[]>([])
const selectedTask = ref<BatchTask>()
const taskTotal = ref(0)
const itemTotal = ref(0)
const taskPage = ref(1)
const taskPageSize = ref(10)
const itemPage = ref(1)
const itemPageSize = ref(10)
const query = reactive({ keyword: '', status: '', triggerType: '' })
const itemQuery = reactive({ keyword: '', status: '' })
const taskColumns = ref(createTableColumnState([
  { key: 'task', label: '任务', required: true },
  { key: 'status', label: '状态' },
  { key: 'progress', label: '进度' },
  { key: 'counts', label: '数量' },
  { key: 'trigger', label: '触发来源' },
  { key: 'time', label: '时间' }
]))

const taskStatusOptions = [
  { label: '等待中', value: 'PENDING' },
  { label: '执行中', value: 'RUNNING' },
  { label: '成功', value: 'SUCCESS' },
  { label: '部分成功', value: 'PARTIAL_SUCCESS' },
  { label: '失败', value: 'FAILED' },
  { label: '取消中', value: 'CANCELING' },
  { label: '已取消', value: 'CANCELED' }
]

const itemStatusOptions = [
  { label: '等待中', value: 'PENDING' },
  { label: '执行中', value: 'RUNNING' },
  { label: '成功', value: 'SUCCESS' },
  { label: '失败', value: 'FAILED' },
  { label: '跳过', value: 'SKIPPED' },
  { label: '重试中', value: 'RETRYING' }
]

const triggerOptions = [
  { label: '手动', value: 'MANUAL' },
  { label: 'API', value: 'API' },
  { label: '定时任务', value: 'JOB' },
  { label: '消息', value: 'MESSAGE' },
  { label: '系统', value: 'SYSTEM' }
]

const loading = computed(() => taskLoading.value || itemLoading.value)
const visibleTaskColumns = computed(() => visibleColumnMap(taskColumns.value))
const runningCount = computed(() => tasks.value.filter((item) => item.status === 'RUNNING').length)
const unhealthyCount = computed(() => tasks.value.filter((item) => ['FAILED', 'PARTIAL_SUCCESS'].includes(item.status)).length)
const cancelingCount = computed(() => tasks.value.filter((item) => item.status === 'CANCELING').length)
const detailTitle = computed(() => (selectedTask.value ? `${selectedTask.value.taskName} 明细` : '批处理明细'))

onMounted(loadPage)

async function loadPage() {
  await loadTasks()
}

async function loadTasks() {
  taskLoading.value = true
  try {
    const result = await pageBatchTasks({
      page: taskPage.value,
      limit: taskPageSize.value,
      keyword: query.keyword,
      status: query.status,
      triggerType: query.triggerType
    })
    tasks.value = result.list
    taskTotal.value = result.total
  } finally {
    taskLoading.value = false
    updateTableHeight()
  }
}

async function loadItems() {
  if (!selectedTask.value) {
    return
  }
  itemLoading.value = true
  try {
    const result = await pageBatchTaskItems(selectedTask.value.id, {
      page: itemPage.value,
      limit: itemPageSize.value,
      keyword: itemQuery.keyword,
      status: itemQuery.status
    })
    items.value = result.list
    itemTotal.value = result.total
  } finally {
    itemLoading.value = false
    updateItemTableHeight()
  }
}

function searchTasks() {
  taskPage.value = 1
  loadTasks()
}

function resetQuery() {
  query.keyword = ''
  query.status = ''
  query.triggerType = ''
  searchTasks()
}

function searchItems() {
  itemPage.value = 1
  loadItems()
}

function handleTaskSizeChange() {
  taskPage.value = 1
  loadTasks()
}

function handleItemSizeChange() {
  itemPage.value = 1
  loadItems()
}

async function openDetail(row: BatchTask) {
  selectedTask.value = row
  itemPage.value = 1
  itemQuery.keyword = ''
  itemQuery.status = ''
  detailVisible.value = true
  await loadItems()
}

async function cancelTask(row: BatchTask) {
  const closing = row.status === 'CANCELING'
  await ElMessageBox.confirm(cancelConfirmMessage(row, closing), closing ? '收口取消任务' : '取消批处理任务', {
    confirmButtonText: closing ? '确认收口' : '确认取消',
    cancelButtonText: '保留任务',
    type: 'warning'
  })
  actingTaskId.value = row.id
  try {
    const result = await cancelBatchTask(row.id, closing ? '人工确认取消' : '人工取消')
    ElMessage.success(result.status === 'CANCELED' ? '任务已取消' : '已请求取消')
    await loadTasks()
  } finally {
    actingTaskId.value = undefined
  }
}

async function retryFailed(row: BatchTask) {
  actingTaskId.value = row.id
  try {
    await retryFailedBatchItems(row.id)
    ElMessage.success('失败项已重置为待处理')
    await loadTasks()
    if (selectedTask.value?.id === row.id) {
      await loadItems()
    }
  } finally {
    actingTaskId.value = undefined
  }
}

function canCancel(row: BatchTask) {
  return !['SUCCESS', 'PARTIAL_SUCCESS', 'FAILED', 'CANCELED'].includes(row.status)
}

function cancelActionText(row: BatchTask) {
  return row.status === 'CANCELING' ? '收口' : '取消'
}

function cancelConfirmMessage(row: BatchTask, closing: boolean) {
  if (closing) {
    return `确认将「${row.taskName}」收口为已取消？未完成明细会标记为跳过。`
  }
  return `确认请求取消「${row.taskName}」？执行中的 worker 会在下一个检查点停止。`
}

function canRetry(row: BatchTask) {
  return (row.failedCount || 0) > 0
}

function taskStatusText(status?: string) {
  return taskStatusOptions.find((item) => item.value === status)?.label || status || '-'
}

function itemStatusText(status?: string) {
  return itemStatusOptions.find((item) => item.value === status)?.label || status || '-'
}

function triggerText(triggerType?: string) {
  return triggerOptions.find((item) => item.value === triggerType)?.label || triggerType || '-'
}

function taskStatusType(status?: string) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED' || status === 'CANCELED') return 'danger'
  if (status === 'PARTIAL_SUCCESS' || status === 'CANCELING') return 'warning'
  if (status === 'RUNNING') return 'primary'
  return 'info'
}

function itemStatusType(status?: string) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'SKIPPED' || status === 'RETRYING') return 'warning'
  if (status === 'RUNNING') return 'primary'
  return 'info'
}
</script>

<style scoped>
.batch-filter-bar :deep(.el-input),
.batch-item-filter-bar :deep(.el-input) {
  width: 260px;
}

.batch-filter-bar :deep(.el-select),
.batch-item-filter-bar :deep(.el-select) {
  width: 136px;
}

.batch-item-panel {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.batch-task-table {
  --el-table-fixed-right-column: inset -8px 0 8px -8px rgba(15, 23, 42, 0.08);
}

.batch-main-cell {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.batch-main-cell strong,
.batch-main-cell small {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.batch-main-cell small {
  color: var(--ea-muted);
}

.batch-progress-cell {
  display: grid;
  grid-template-columns: minmax(80px, 1fr) 44px;
  align-items: center;
  gap: 8px;
  font-variant-numeric: tabular-nums;
}

.batch-counts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  color: var(--ea-muted);
}

.batch-counts .is-success {
  color: var(--el-color-success);
}

.batch-counts .is-danger {
  color: var(--el-color-danger);
}

.batch-counts .is-skipped {
  color: var(--el-color-warning);
}

.batch-detail-meta {
  flex: 0 0 auto;
  border-bottom: 1px solid var(--ea-border);
  padding-bottom: 12px;
  margin-bottom: 12px;
}

.batch-detail-meta dl {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
}

.batch-detail-meta dt {
  color: var(--ea-muted);
  font-size: 12px;
  margin-bottom: 6px;
}

.batch-detail-meta dd {
  min-width: 0;
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.batch-item-filter-row {
  flex: 0 0 auto;
  margin-bottom: 10px;
}

.batch-item-filter-row :deep(.el-form-item) {
  margin-bottom: 0;
}

@media (max-width: 860px) {
  .batch-detail-meta dl {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .batch-filter-bar :deep(.el-input),
  .batch-filter-bar :deep(.el-select),
  .batch-item-filter-bar :deep(.el-input),
  .batch-item-filter-bar :deep(.el-select) {
    width: 100%;
  }
}
</style>

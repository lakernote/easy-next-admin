<template>
  <section class="resource-page schedule-page">
    <div class="resource-hero">
      <div>
        <h1>动态定时任务</h1>
        <p>在线维护任务定义、Cron、执行状态和任务日志，适合企业内部周期任务治理。</p>
      </div>
      <div class="resource-actions">
        <el-button :icon="Document" @click="openJobLogs()">全部日志</el-button>
        <el-button v-permission="PermissionCodes.schedule.jobEdit" type="primary" :icon="Plus" @click="openEditor()">
          新增任务
        </el-button>
      </div>
    </div>

    <div class="schedule-summary-strip">
      <div class="schedule-summary-item">
        <span>任务总数</span>
        <strong>{{ jobTotal }}</strong>
      </div>
      <div class="schedule-summary-item">
        <span>本页运行中</span>
        <strong>{{ runningCount }}</strong>
      </div>
      <div class="schedule-summary-item">
        <span>最近成功</span>
        <strong>{{ successCount }}</strong>
      </div>
      <div class="schedule-summary-item">
        <span>最近失败</span>
        <strong>{{ failedCount }}</strong>
      </div>
    </div>

    <section ref="tablePanelRef" class="surface resource-panel schedule-job-list is-fluid-table">
      <div class="table-control-row is-tools-only">
        <TableToolbar v-model:columns="jobColumns" class="table-toolbar-inline" />
      </div>
      <el-table v-loading="jobLoading" :data="jobs" :height="tableHeight" class="admin-table" empty-text="暂无定时任务">
        <el-table-column v-if="visibleJobColumns.name" label="任务" min-width="220">
          <template #default="{ row }">
            <div class="schedule-main-cell">
              <strong>{{ row.jobName }}</strong>
              <small>{{ row.jobCode }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="visibleJobColumns.cron" prop="cronExpression" label="Cron" min-width="150" show-overflow-tooltip />
        <el-table-column v-if="visibleJobColumns.handler" prop="jobClassName" label="处理器" min-width="260" show-overflow-tooltip />
        <el-table-column v-if="visibleJobColumns.status" prop="status" label="状态" width="110">
          <template #default="{ row }">
            <EnableStatusSwitch
              :model-value="isRunning(row)"
              :loading="changingJobCode === row.jobCode"
              :disabled="!canEditJob"
              disabled-reason="缺少定时任务维护权限"
              :target-name="row.jobName"
              active-label="运行中"
              inactive-label="暂停"
              active-action-label="启动"
              inactive-action-label="暂停"
              @toggle="toggleJob(row)"
            />
          </template>
        </el-table-column>
        <el-table-column v-if="visibleJobColumns.updatedAt" prop="updateTime" label="更新时间" width="168" />
        <el-table-column v-if="visibleJobColumns.remark" prop="remark" label="说明" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right" align="center" header-align="center">
          <template #default="{ row }">
            <div class="row-actions schedule-row-actions">
              <el-button text :icon="Document" @click="openJobLogs(row)">日志</el-button>
              <el-button v-permission:disable="PermissionCodes.schedule.jobEdit" text @click="openEditor(row)">编辑</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <div class="table-footer is-split schedule-table-footer">
        <span>共 {{ jobTotal }} 个任务</span>
        <el-pagination
          v-model:current-page="jobPage"
          v-model:page-size="jobPageSize"
          background
          layout="sizes, prev, pager, next"
          :total="jobTotal"
          :page-sizes="[10, 20, 50]"
          @current-change="loadJobs"
          @size-change="handleJobSizeChange"
        />
      </div>
    </section>

    <el-drawer v-model="logDrawerVisible" :title="logDrawerTitle" size="min(760px, 92vw)" class="schedule-log-drawer" @opened="updateLogTableHeight">
      <section ref="logPanelRef" class="schedule-log-drawer-body">
        <el-table v-loading="logLoading" :data="logs" :height="logTableHeight" class="admin-table" empty-text="暂无执行日志">
          <el-table-column label="任务 / 线程" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="schedule-main-cell">
                <strong>{{ row.jobCode }}</strong>
                <small>{{ row.threadName || '-' }}</small>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="时间" min-width="190" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="schedule-main-cell">
                <strong>{{ row.startTime || '-' }}</strong>
                <small>{{ row.endTime || '执行中' }}</small>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="cost" label="耗时" width="96">
            <template #default="{ row }">{{ row.cost ?? 0 }}ms</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="86">
            <template #default="{ row }">
              <el-tag :type="row.status === 2 ? 'danger' : 'success'" effect="plain">
                {{ row.status === 2 ? '异常' : '成功' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
        <div class="table-footer is-split schedule-log-footer">
          <span>共 {{ logTotal }} 条</span>
          <el-pagination
            v-model:current-page="logPage"
            v-model:page-size="logPageSize"
            background
            layout="sizes, prev, pager, next"
            :total="logTotal"
            :page-sizes="[10, 20, 50]"
            @current-change="loadLogs"
            @size-change="handleLogSizeChange"
          />
        </div>
      </section>
    </el-drawer>

    <el-drawer v-model="editorVisible" :title="editingJob?.jobId ? '编辑任务' : '新增任务'" size="min(520px, 92vw)">
      <el-form label-position="top">
        <el-form-item label="任务编码">
          <el-input v-model="jobForm.jobCode" placeholder="全局唯一，例如 infra_local_message_retry" />
        </el-form-item>
        <el-form-item label="任务名称">
          <el-input v-model="jobForm.jobName" placeholder="展示给运维人员的任务名称" />
        </el-form-item>
        <el-form-item label="处理器类名">
          <el-input v-model="jobForm.jobClassName" placeholder="Spring Bean 真实类名" />
        </el-form-item>
        <el-form-item label="Cron 表达式">
          <el-input v-model="jobForm.cronExpression" placeholder="0 0/5 * * * ?" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="jobForm.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveJob">保存</el-button>
      </template>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Document, Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { PermissionCodes } from '@/permissions/codes'
import TableToolbar from '@/components/table/TableToolbar.vue'
import EnableStatusSwitch from '@/components/table/EnableStatusSwitch.vue'
import { createTableColumnState, visibleColumnMap } from '@/components/table/tableColumns'
import { useFluidTableHeight } from '@/components/table/useFluidTableHeight'
import { pageScheduleJobLogs, pageScheduleJobs, saveScheduleJob, startScheduleJob, stopScheduleJob } from '@/features/schedule/api'
import type { ScheduleJob, ScheduleJobLog } from '@/features/schedule/types'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const jobLoading = ref(false)
const logLoading = ref(false)
const recentLogLoading = ref(false)
const saving = ref(false)
const editorVisible = ref(false)
const logDrawerVisible = ref(false)
const changingJobCode = ref('')
const tablePanelRef = ref<HTMLElement>()
const logPanelRef = ref<HTMLElement>()
const { tableHeight, updateTableHeight } = useFluidTableHeight(tablePanelRef)
const { tableHeight: logTableHeight, updateTableHeight: updateLogTableHeight } = useFluidTableHeight(logPanelRef, {
  footerSelector: '.schedule-log-footer',
  bottomGap: 0,
  minHeight: 260
})
const jobs = ref<ScheduleJob[]>([])
const logs = ref<ScheduleJobLog[]>([])
const recentLogs = ref<ScheduleJobLog[]>([])
const jobTotal = ref(0)
const logTotal = ref(0)
const jobPage = ref(1)
const jobPageSize = ref(10)
const logPage = ref(1)
const logPageSize = ref(10)
const editingJob = ref<ScheduleJob>()
const selectedLogJob = ref<ScheduleJob>()
const jobForm = reactive<Partial<ScheduleJob>>({})
const jobColumns = ref(createTableColumnState([
  { key: 'name', label: '任务名称', required: true },
  { key: 'cron', label: 'Cron' },
  { key: 'handler', label: '处理器' },
  { key: 'updatedAt', label: '更新时间' },
  { key: 'status', label: '状态' },
  { key: 'remark', label: '说明' }
]))

const loading = computed(() => jobLoading.value || recentLogLoading.value || logLoading.value)
const visibleJobColumns = computed(() => visibleColumnMap(jobColumns.value))
const canEditJob = computed(() => auth.hasAnyPermission([PermissionCodes.schedule.jobEdit]))
const runningCount = computed(() => jobs.value.filter(isRunning).length)
const successCount = computed(() => recentLogs.value.filter((item) => item.status !== 2).length)
const failedCount = computed(() => recentLogs.value.filter((item) => item.status === 2).length)
const logDrawerTitle = computed(() => (selectedLogJob.value ? `${selectedLogJob.value.jobName} 执行日志` : '全部执行日志'))

onMounted(loadPage)

async function loadPage() {
  await Promise.all([loadJobs(), loadRecentLogs()])
}

async function loadJobs() {
  jobLoading.value = true
  try {
    const jobResult = await pageScheduleJobs({ page: jobPage.value, limit: jobPageSize.value })
    jobs.value = jobResult.list
    jobTotal.value = jobResult.total
  } finally {
    jobLoading.value = false
    updateTableHeight()
  }
}

async function loadRecentLogs() {
  recentLogLoading.value = true
  try {
    const logResult = await pageScheduleJobLogs({ page: 1, limit: 20 })
    recentLogs.value = logResult.list
  } finally {
    recentLogLoading.value = false
  }
}

async function loadLogs() {
  logLoading.value = true
  try {
    const logResult = await pageScheduleJobLogs({
      page: logPage.value,
      limit: logPageSize.value,
      jobCode: selectedLogJob.value?.jobCode
    })
    logs.value = logResult.list
    logTotal.value = logResult.total
  } finally {
    logLoading.value = false
    updateLogTableHeight()
  }
}

function handleJobSizeChange() {
  jobPage.value = 1
  loadJobs()
}

function handleLogSizeChange() {
  logPage.value = 1
  loadLogs()
}

function isRunning(row: ScheduleJob) {
  return row.enable !== false && (row.jobState === 'START' || row.jobState === 1)
}

function openEditor(row?: ScheduleJob) {
  editingJob.value = row
  Object.keys(jobForm).forEach((key) => {
    delete jobForm[key as keyof ScheduleJob]
  })
  Object.assign(jobForm, row || { enable: true, jobState: 'STOP' })
  editorVisible.value = true
}

async function openJobLogs(row?: ScheduleJob) {
  selectedLogJob.value = row
  logPage.value = 1
  logDrawerVisible.value = true
  await loadLogs()
}

async function saveJob() {
  saving.value = true
  try {
    await saveScheduleJob(jobForm)
    ElMessage.success('任务已保存')
    editorVisible.value = false
    await loadPage()
  } finally {
    saving.value = false
  }
}

async function toggleJob(row: ScheduleJob) {
  changingJobCode.value = row.jobCode
  try {
    if (isRunning(row)) {
      await stopScheduleJob(row.jobCode)
      ElMessage.success('任务已暂停')
    } else {
      await startScheduleJob(row.jobCode)
      ElMessage.success('任务已启动')
    }
    const jobs = [loadJobs(), loadRecentLogs()]
    if (logDrawerVisible.value) {
      jobs.push(loadLogs())
    }
    await Promise.all(jobs)
  } finally {
    changingJobCode.value = ''
  }
}
</script>

<style scoped>
.schedule-page {
  gap: 12px;
}

.schedule-summary-strip {
  flex: 0 0 auto;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.schedule-summary-item {
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border: 1px solid var(--ea-border);
  border-radius: 8px;
  padding: 10px 14px;
  background: #fff;
}

.schedule-summary-item span {
  color: var(--ea-muted);
  font-size: 13px;
}

.schedule-summary-item strong {
  color: var(--ea-text);
  font-size: 22px;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.schedule-job-list {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.schedule-job-list .admin-table {
  flex: 1 1 auto;
  min-height: 0;
}

.schedule-log-drawer-body .admin-table {
  flex: 1 1 auto;
  min-height: 0;
}

:global(.schedule-log-drawer .el-drawer__body) {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.schedule-row-actions {
  justify-content: center;
  gap: 8px;
  white-space: nowrap;
}

.schedule-main-cell {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.schedule-main-cell strong,
.schedule-main-cell small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.schedule-main-cell strong {
  color: var(--ea-text);
}

.schedule-main-cell small {
  color: var(--ea-muted);
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", monospace;
  font-size: 12px;
}

.schedule-table-footer,
.schedule-log-footer {
  padding: 10px 12px;
}

.schedule-log-drawer-body {
  flex: 1 1 auto;
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

@media (max-width: 768px) {
  .schedule-summary-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 520px) {
  .schedule-summary-strip {
    grid-template-columns: 1fr;
  }
}
</style>

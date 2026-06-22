<template>
  <section class="workspace">
    <div v-if="initialLoading" class="surface workspace-loading">
      <el-skeleton :rows="12" animated />
    </div>

    <el-alert
      v-else-if="errorMessage && !overview"
      :title="errorMessage"
      type="error"
      show-icon
      :closable="false"
    >
      <template #default>
        <el-button type="primary" :icon="RefreshRight" :loading="loading" @click="loadOverview">重新加载</el-button>
      </template>
    </el-alert>

    <template v-else-if="overview">
      <section v-if="visibleApplications.length" class="surface quick-application-strip">
        <div class="quick-application-head">
          <h2>常用申请</h2>
        </div>
        <div class="quick-application-list">
          <button
            v-for="item in visibleApplications"
            :key="item.path"
            class="quick-application-item"
            type="button"
            @click="go(item.path)"
          >
            <el-icon><component :is="resolveIcon(item.icon)" /></el-icon>
            <span>
              <strong>{{ item.title }}</strong>
              <small>{{ item.description }}</small>
            </span>
            <el-icon class="shortcut-arrow"><ArrowRight /></el-icon>
          </button>
        </div>
      </section>

      <div class="workspace-main-grid">
        <section class="surface workbench-panel">
          <div class="section-head">
            <div>
              <h2>我的待办</h2>
              <p>需要当前账号处理的流程任务。</p>
            </div>
            <el-button text type="primary" @click="go(workflowTaskCenterPath('pending'))">全部待办</el-button>
          </div>
          <div v-if="overview.workflow.pendingTasks.length" class="work-list">
            <button
              v-for="item in overview.workflow.pendingTasks"
              :key="item.id"
              class="work-list-item"
              type="button"
              @click="go(workflowTaskCenterPath('pending'))"
            >
              <span class="work-list-main">
                <strong>{{ item.title }}</strong>
                <small>{{ businessTypeText(item.businessType) }} / {{ item.businessId || '无业务单号' }}</small>
              </span>
              <span class="work-list-side">
                <el-tag type="warning" effect="plain">{{ item.nodeName || '待处理' }}</el-tag>
                <small>{{ formatTime(item.startedAt) }}</small>
              </span>
            </button>
          </div>
          <div v-else class="work-empty">
            <strong>当前没有待办</strong>
            <span>新流程到达你这里后会出现在这里。</span>
          </div>
        </section>

        <section class="surface workbench-panel personal-flow-panel">
          <div class="section-head">
            <div>
              <h2>我的流程</h2>
              <p>只展示我发起和抄送给我的流程。</p>
            </div>
            <div class="section-actions workspace-flow-actions">
              <span class="flow-list-summary">{{ activeFlowSummary }}</span>
              <el-button text type="primary" @click="go(activeFlowTargetPath)">查看全部</el-button>
            </div>
          </div>
          <el-tabs v-model="activeFlowTab" class="workspace-flow-tabs" stretch>
            <el-tab-pane :label="startedTabLabel" name="started">
              <div v-if="overview.workflow.startedInstances.length" class="compact-list">
                <button
                  v-for="item in overview.workflow.startedInstances"
                  :key="item.id"
                  class="compact-list-item"
                  type="button"
                  @click="go(workflowTaskCenterPath('started'))"
                >
                  <span>
                    <strong>{{ item.title }}</strong>
                    <small>{{ businessTypeText(item.businessType) }} / {{ formatTime(item.startedAt) }}</small>
                  </span>
                  <el-tag :type="instanceStatusType(item.status)" effect="plain">{{ instanceStatusText(item.status) }}</el-tag>
                </button>
              </div>
              <div v-else class="work-empty is-compact">
                <strong>暂无发起记录</strong>
                <span>从上方常用申请发起后会展示在这里。</span>
              </div>
            </el-tab-pane>
            <el-tab-pane :label="ccTabLabel" name="cc">
              <div v-if="overview.workflow.ccItems.length" class="compact-list">
                <button
                  v-for="item in overview.workflow.ccItems"
                  :key="item.id"
                  class="compact-list-item"
                  type="button"
                  @click="go(workflowTaskCenterPath('cc'))"
                >
                  <span>
                    <strong>{{ item.title }}</strong>
                    <small>{{ item.nodeName || item.nodeKey || '抄送节点' }} / {{ formatTime(item.createdAt) }}</small>
                  </span>
                  <el-tag :type="item.readStatus ? 'success' : 'info'" effect="plain">{{ item.readStatus ? '已读' : '未读' }}</el-tag>
                </button>
              </div>
              <div v-else class="work-empty is-compact">
                <strong>暂无抄送</strong>
                <span>与你相关的抄送流程会展示在这里。</span>
              </div>
            </el-tab-pane>
          </el-tabs>
        </section>
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
import {
  ArrowRight,
  Connection,
  DataBoard,
  DataLine,
  Document,
  Finished,
  RefreshRight,
  ShoppingCart,
  Tools,
  User
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref, type Component } from 'vue'
import { useRouter } from 'vue-router'
import { getDashboardOverview } from '@/features/dashboard/api'
import type { DashboardOverview, DashboardWorkflowMetric } from '@/features/dashboard/types'
import { workflowTaskCenterPath } from '@/features/workflow/taskCenterTabs'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()
const overview = ref<DashboardOverview>()
const loading = ref(false)
const errorMessage = ref('')
const initialLoading = computed(() => loading.value && !overview.value)

const visibleWorkflowMetrics = computed(() => {
  return overview.value?.workflow.metrics.filter((item) => item.key !== 'applications' && auth.hasAnyPermission([item.permission])) || []
})
const visibleApplications = computed(() => {
  return overview.value?.applications.filter((item) => auth.hasAnyPermission([item.permission])) || []
})
const activeFlowTab = ref<'started' | 'cc'>('started')
const activeFlowTargetPath = computed(() => workflowTaskCenterPath(activeFlowTab.value))
const startedTabLabel = computed(() => `我发起的 ${workflowMetricValue('started', overview.value?.workflow.startedInstances.length || 0)}`)
const ccTabLabel = computed(() => `抄送我的 ${workflowMetricValue('cc', overview.value?.workflow.ccItems.length || 0)}`)
const activeFlowSummary = computed(() => {
  if (activeFlowTab.value === 'started') {
    const visible = overview.value?.workflow.startedInstances.length || 0
    const total = workflowMetricValue('started', visible)
    return `当前显示 ${visible} 条 / 共 ${total} 条`
  }
  const visible = overview.value?.workflow.ccItems.length || 0
  const total = workflowMetricValue('cc', visible)
  return `当前显示 ${visible} 条 / 共 ${total} 条`
})

const iconMap: Record<string, Component> = {
  Connection,
  DataBoard,
  DataLine,
  Document,
  Finished,
  ShoppingCart,
  Tools,
  User
}

function resolveIcon(name?: string): Component {
  return iconMap[name || ''] || Document
}

function workflowMetricValue(key: DashboardWorkflowMetric['key'], fallback: number) {
  return visibleWorkflowMetrics.value.find((item) => item.key === key)?.value || String(fallback)
}

function businessTypeText(type: string) {
  const textMap: Record<string, string> = {
    leave: '请假',
    expense: '报销',
    purchase: '采购',
    repair: '报修',
    workflow: '流程'
  }
  return textMap[type] || type || '流程'
}

function formatTime(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 16)
}

function instanceStatusText(status: string) {
  const textMap: Record<string, string> = {
    RUNNING: '审批中',
    APPROVED: '已通过',
    REJECTED: '已驳回',
    REVOKED: '已撤回',
    TERMINATED: '已终止'
  }
  return textMap[status] || status
}

function instanceStatusType(status: string) {
  if (status === 'RUNNING') return 'warning'
  if (status === 'APPROVED') return 'success'
  if (status === 'REJECTED' || status === 'TERMINATED') return 'danger'
  return 'info'
}

function go(path: string) {
  router.push(path)
}

async function loadOverview() {
  loading.value = true
  errorMessage.value = ''
  try {
    overview.value = await getDashboardOverview()
    activeFlowTab.value = overview.value.workflow.startedInstances.length || !overview.value.workflow.ccItems.length ? 'started' : 'cc'
  } catch {
    errorMessage.value = '工作台数据加载失败'
    ElMessage.error(errorMessage.value)
  } finally {
    loading.value = false
  }
}

onMounted(loadOverview)
</script>

<style scoped>
.workspace {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
  overflow: hidden;
}

.workspace-main-grid {
  flex: 1 1 auto;
  min-height: 0;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  min-width: 0;
}

.quick-application-item:hover,
.work-list-item:hover,
.compact-list-item:hover {
  background: #f8fbff;
}

.workbench-panel {
  min-height: 0;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 16px;
}

.workbench-panel .section-head {
  flex: 0 0 auto;
  margin-bottom: 12px;
}

.quick-application-strip {
  flex: 0 0 auto;
  display: grid;
  grid-template-columns: 110px minmax(0, 1fr);
  align-items: center;
  gap: 14px;
  padding: 12px 14px;
}

.quick-application-head h2 {
  margin: 0;
  font-size: 17px;
}

.quick-application-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  min-width: 0;
}

.personal-flow-panel .section-head {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 8px;
}

.workspace-flow-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-width: 0;
}

.flow-list-summary {
  border: 1px solid #dbe7f5;
  border-radius: 999px;
  padding: 4px 9px;
  background: #f8fbff;
  color: #64748b;
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
}

.work-list,
.compact-list {
  flex: 1 1 auto;
  min-height: 0;
  display: grid;
  align-content: start;
  gap: 8px;
  overflow: auto;
  padding-right: 4px;
  scrollbar-width: thin;
}

.workspace-flow-tabs {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.workspace-flow-tabs :deep(.el-tabs__header) {
  flex: 0 0 auto;
  margin: 0 0 10px;
}

.workspace-flow-tabs :deep(.el-tabs__content) {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
}

.workspace-flow-tabs :deep(.el-tab-pane) {
  flex: 1 1 auto;
  height: 100%;
  min-height: 0;
  flex-direction: column;
}

.work-list-item,
.compact-list-item,
.quick-application-item {
  width: 100%;
  display: grid;
  align-items: center;
  border: 1px solid var(--ea-border);
  border-radius: 8px;
  background: #fff;
  color: var(--ea-text);
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.16s ease,
    background 0.16s ease,
    color 0.16s ease;
}

.work-list-item {
  min-height: 58px;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  padding: 9px 12px;
}

.compact-list-item {
  min-height: 56px;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  padding: 9px 12px;
}

.quick-application-item {
  min-height: 64px;
  grid-template-columns: 34px minmax(0, 1fr) 18px;
  gap: 10px;
  padding: 10px 12px;
}

.quick-application-item > .el-icon:first-child {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: #eff6ff;
  color: var(--ea-primary);
}

.work-list-main,
.work-list-side,
.compact-list-item span,
.quick-application-item span {
  min-width: 0;
}

.work-list-main strong,
.work-list-main small,
.work-list-side small,
.compact-list-item strong,
.compact-list-item small,
.quick-application-item strong,
.quick-application-item small {
  display: block;
}

.work-list-main strong,
.compact-list-item strong,
.quick-application-item strong {
  overflow: hidden;
  color: var(--ea-text);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.work-list-main small,
.work-list-side small,
.compact-list-item small,
.quick-application-item small {
  margin-top: 2px;
  color: var(--ea-muted);
  font-size: 12px;
  line-height: 1.35;
}

.work-list-side {
  display: grid;
  justify-items: end;
  gap: 6px;
}

.work-empty {
  flex: 1 1 auto;
  min-height: 0;
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 8px;
  border: 1px dashed var(--ea-border-strong);
  border-radius: 8px;
  color: var(--ea-muted);
  text-align: center;
}

.work-empty.is-compact {
  flex: 1 1 auto;
  min-height: 100%;
}

.work-empty strong {
  color: var(--ea-text);
}

@media (max-width: 1100px) {
  .workspace {
    height: auto;
    min-height: 100%;
    overflow: visible;
  }

  .workspace-main-grid {
    flex: 0 0 auto;
    grid-template-columns: 1fr;
    min-height: auto;
    overflow: visible;
  }

  .quick-application-strip {
    grid-template-columns: 1fr;
  }

  .quick-application-list {
    grid-template-columns: 1fr;
  }

  .workbench-panel {
    min-height: 220px;
    overflow: visible;
  }

  .work-list,
  .compact-list {
    overflow: visible;
  }

  .workspace-flow-tabs,
  .workspace-flow-tabs :deep(.el-tabs__content),
  .workspace-flow-tabs :deep(.el-tab-pane) {
    min-height: auto;
  }

  .workspace-flow-tabs :deep(.el-tab-pane) {
    height: auto;
  }
}

@media (max-width: 640px) {
  .workspace-flow-actions {
    align-items: flex-start;
    flex-direction: column;
    gap: 4px;
    white-space: normal;
  }

  .work-list-item,
  .compact-list-item {
    grid-template-columns: 1fr;
  }

  .work-list-side {
    justify-items: start;
  }
}
</style>

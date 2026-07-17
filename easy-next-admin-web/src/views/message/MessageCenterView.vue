<template>
  <section class="resource-page">
    <div class="resource-hero">
      <div>
        <h1>消息中心</h1>
        <p>聚合流程待办、审批结果、审计提醒和任务消息，先处理未读和待办相关消息。</p>
      </div>
      <div class="resource-actions">
        <el-button
          v-permission:disable="PermissionCodes.message.read"
          :icon="Check"
          :loading="markingAll"
          @click="handleMarkAllRead"
        >
          全部已读
        </el-button>
      </div>
    </div>

    <div class="resource-metrics is-four">
      <div v-for="item in metrics" :key="item.label" class="resource-metric">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
      </div>
    </div>

    <section ref="tablePanelRef" class="surface resource-panel is-fluid-table">
      <div class="section-head message-section-head">
        <div>
          <h2>我的消息</h2>
          <p>仅展示当前账号接收的流程、审计和任务消息。</p>
        </div>
      </div>

      <div class="table-control-row">
        <el-form :inline="true" class="filter-bar">
          <el-form-item label="关键词">
            <el-input v-model="query.keyword" placeholder="标题 / 内容 / 业务单号" clearable @keyup.enter="handleSearch" />
          </el-form-item>
          <el-form-item label="类型">
            <el-select v-model="query.type" placeholder="全部类型" clearable style="width: 140px">
              <el-option label="流程消息" value="workflow" />
              <el-option label="审计提醒" value="audit" />
              <el-option label="任务消息" value="task" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.read" placeholder="全部状态" clearable style="width: 130px">
              <el-option label="未读" :value="false" />
              <el-option label="已读" :value="true" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" plain :icon="Search" :loading="loading" @click="handleSearch">查询</el-button>
            <el-button @click="resetFilters">重置</el-button>
          </el-form-item>
        </el-form>
        <TableToolbar v-model:columns="columns" class="table-toolbar-inline" />
      </div>

      <el-table v-loading="loading" :data="messages" row-key="id" :height="tableHeight" class="admin-table message-table" empty-text="暂无消息">
        <el-table-column v-if="visibleColumns.title" label="消息" min-width="260">
          <template #default="{ row }">
            <div class="message-cell">
              <span :class="['message-dot', { 'is-unread': !row.read }]"></span>
              <span>
                <strong>{{ row.title }}</strong>
                <small>{{ row.content || '-' }}</small>
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="visibleColumns.type" label="类型" width="110">
          <template #default="{ row }">
            <el-tag :type="messageTypeTag(row.type)" effect="plain">{{ messageTypeText(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="visibleColumns.biz" label="关联业务" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            {{ [row.relatedBizType, row.relatedBizId].filter(Boolean).join(' / ') || '-' }}
          </template>
        </el-table-column>
        <el-table-column v-if="visibleColumns.sender" label="发送人" width="120">
          <template #default="{ row }">{{ row.senderName || '系统' }}</template>
        </el-table-column>
        <el-table-column v-if="visibleColumns.createdAt" prop="createdAt" label="时间" width="170" />
        <el-table-column v-if="visibleColumns.read" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.read ? 'info' : 'primary'" effect="plain">{{ row.read ? '已读' : '未读' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="148" fixed="right" align="center" header-align="center">
          <template #default="{ row }">
            <div class="row-actions message-row-actions">
              <el-button
                v-if="row.link"
                class="message-primary-action"
                text
                type="primary"
                :icon="Position"
                @click="router.push(row.link)"
              >
                前往
              </el-button>
              <el-button
                v-if="!row.read"
                v-permission:disable="PermissionCodes.message.read"
                class="message-read-action"
                text
                :icon="Check"
                :loading="markingId === row.id"
                @click="handleMarkRead(row)"
              >
                标已读
              </el-button>
              <span v-if="!row.link && row.read" class="message-action-muted">-</span>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer is-split">
        <span>共 {{ total }} 条</span>
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.limit"
          background
          layout="sizes, prev, pager, next"
          :page-sizes="[10, 20, 50]"
          :total="total"
          @current-change="loadMessages"
          @size-change="handleSizeChange"
        />
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Check, Position, Search } from '@element-plus/icons-vue'
import { PermissionCodes } from '@/permissions/codes'
import TableToolbar from '@/components/table/TableToolbar.vue'
import { createTableColumnState, visibleColumnMap } from '@/components/table/tableColumns'
import { useFluidTableHeight } from '@/components/table/useFluidTableHeight'
import {
  getMessageUnreadCount,
  markAllMessagesRead,
  markMessageRead,
  pageMessages
} from '@/features/message/api'
import type {
  MessageItem,
  MessagePageQuery,
  MessageType,
  MessageUnreadCount
} from '@/features/message/types'

const router = useRouter()
const loading = ref(false)
const markingAll = ref(false)
const markingId = ref<MessageItem['id']>()
const messages = ref<MessageItem[]>([])
const total = ref(0)
const tablePanelRef = ref<HTMLElement>()
const unreadCount = ref<MessageUnreadCount>({ total: 0 })
const columns = ref(createTableColumnState([
  { key: 'title', label: '消息', required: true },
  { key: 'type', label: '类型' },
  { key: 'biz', label: '关联业务' },
  { key: 'sender', label: '发送人' },
  { key: 'createdAt', label: '时间' },
  { key: 'read', label: '状态' }
]))

const query = reactive<Required<Pick<MessagePageQuery, 'page' | 'limit'>> & Omit<MessagePageQuery, 'page' | 'limit'>>({
  page: 1,
  limit: 10,
  keyword: '',
  type: '',
  read: undefined
})
const visibleColumns = computed(() => visibleColumnMap(columns.value))
const { tableHeight, updateTableHeight } = useFluidTableHeight(tablePanelRef)
const metrics = computed(() => [
  { label: '未读消息', value: unreadCount.value.total.toLocaleString('zh-CN') },
  { label: '流程未读', value: (unreadCount.value.workflow || 0).toLocaleString('zh-CN') },
  { label: '审计提醒', value: (unreadCount.value.audit || 0).toLocaleString('zh-CN') },
  { label: '任务提醒', value: (unreadCount.value.task || 0).toLocaleString('zh-CN') }
])

onMounted(loadMessages)

async function loadMessages() {
  loading.value = true
  try {
    const [page, count] = await Promise.all([
      pageMessages(query),
      getMessageUnreadCount()
    ])
    messages.value = page.list
    total.value = page.total
    unreadCount.value = count
  } finally {
    loading.value = false
    updateTableHeight()
  }
}

function handleSearch() {
  query.page = 1
  loadMessages()
}

function resetFilters() {
  query.keyword = ''
  query.type = ''
  query.read = undefined
  handleSearch()
}

function handleSizeChange() {
  query.page = 1
  loadMessages()
}

async function handleMarkRead(row: MessageItem) {
  markingId.value = row.id
  try {
    await markMessageRead(row.id)
    ElMessage.success('消息已标记为已读')
    await loadMessages()
  } finally {
    markingId.value = undefined
  }
}

async function handleMarkAllRead() {
  markingAll.value = true
  try {
    await markAllMessagesRead()
    ElMessage.success('已全部标记为已读')
    await loadMessages()
  } finally {
    markingAll.value = false
  }
}

function messageTypeText(type: MessageType) {
  if (type === 'workflow') return '流程'
  if (type === 'audit') return '审计'
  if (type === 'task') return '任务'
  return type || '-'
}

function messageTypeTag(type: MessageType) {
  if (type === 'workflow') return 'primary'
  if (type === 'audit') return 'warning'
  if (type === 'task') return 'danger'
  return 'info'
}

</script>

<style scoped>
.message-cell {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  min-width: 0;
}

.message-cell strong,
.message-cell small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.message-cell small {
  margin-top: 3px;
  color: var(--ea-muted);
}

.message-dot {
  width: 8px;
  height: 8px;
  margin-top: 7px;
  border-radius: 999px;
  background: #cbd5e1;
}

.message-dot.is-unread {
  background: var(--ea-primary);
}

.message-section-head {
  margin-bottom: 12px;
}

.message-table {
  --el-table-fixed-right-column: inset -8px 0 8px -8px rgba(15, 23, 42, 0.08);
}

.message-row-actions {
  justify-content: center;
  gap: 4px;
}

.message-row-actions :deep(.el-button.is-text) {
  min-height: 28px;
  padding-right: 5px;
  padding-left: 5px;
  font-weight: 600;
}

.message-row-actions :deep(.message-primary-action.el-button) {
  color: var(--ea-primary);
}

.message-row-actions :deep(.message-read-action.el-button) {
  color: #64748b;
}

.message-row-actions :deep(.message-read-action.el-button:hover) {
  color: var(--ea-primary);
}

.message-action-muted {
  color: #94a3b8;
  font-size: 13px;
}

</style>

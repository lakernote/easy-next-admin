<template>
  <section class="resource-page">
    <div class="resource-hero">
      <div>
        <h1>在线用户</h1>
        <p>查看当前有效会话、登录客户端、来源 IP 和最近活跃时间，并对异常会话执行下线处理。</p>
      </div>
    </div>

    <div class="resource-metrics is-four">
      <div v-for="item in metrics" :key="item.label" class="resource-metric">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
      </div>
    </div>

    <section ref="tablePanelRef" class="surface resource-panel is-fluid-table">
      <div class="table-control-row">
        <el-input v-model="keyword" :prefix-icon="Search" placeholder="过滤当前页账号 / IP / 客户端" clearable class="session-filter" />
      </div>

      <el-table v-loading="loading" :data="filteredSessions" row-key="sessionId" :height="tableHeight" class="admin-table" empty-text="暂无在线用户">
        <el-table-column label="用户" min-width="180">
          <template #default="{ row }">
            <div class="entity-cell">
              <UserAvatar class="entity-avatar" :user="row" />
              <span>
                <strong>{{ userName(row) }}</strong>
                <small>{{ row.userName || row.userId }} <el-tag v-if="row.current" size="small" effect="plain">当前会话</el-tag></small>
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="客户端" width="150">
          <template #default="{ row }">{{ clientText(row) }}</template>
        </el-table-column>
        <el-table-column prop="ip" label="登录 IP" width="150">
          <template #default="{ row }">{{ row.ip || '-' }}</template>
        </el-table-column>
        <el-table-column prop="loginTime" label="登录时间" width="170" />
        <el-table-column prop="lastActiveTime" label="最近活跃" width="170">
          <template #default="{ row }">{{ row.lastActiveTime || '-' }}</template>
        </el-table-column>
        <el-table-column prop="accessExpireTime" label="会话到期" width="170">
          <template #default="{ row }">{{ row.accessExpireTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              v-permission:disable="{ permissions: PermissionCodes.auth.sessionRevoke, reason: '缺少下线会话权限' }"
              text
              type="danger"
              :icon="Delete"
              :disabled="row.current"
              :loading="revokingId === row.sessionId"
              @click="handleRevoke(row)"
            >
              下线
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer is-split">
        <span>共 {{ total }} 个在线会话</span>
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.limit"
          background
          layout="sizes, prev, pager, next"
          :page-sizes="[10, 20, 50]"
          :total="total"
          @current-change="loadSessions"
          @size-change="handleSizeChange"
        />
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Delete, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageOnlineUsers, revokeOnlineSession } from '@/features/monitor/api'
import type { OnlineSession } from '@/features/monitor/types'
import { PermissionCodes } from '@/permissions/codes'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import { useFluidTableHeight } from '@/components/table/useFluidTableHeight'

const loading = ref(false)
const revokingId = ref<string | number>()
const keyword = ref('')
const sessions = ref<OnlineSession[]>([])
const total = ref(0)
const tablePanelRef = ref<HTMLElement>()
const { tableHeight, updateTableHeight } = useFluidTableHeight(tablePanelRef)
const query = reactive({
  page: 1,
  limit: 10
})

const filteredSessions = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  if (!text) return sessions.value
  return sessions.value.filter((item) => [
    item.nickName,
    item.userName,
    item.userId,
    item.clientType,
    item.clientVersion,
    item.ip
  ].some((value) => String(value ?? '').toLowerCase().includes(text)))
})

const metrics = computed(() => {
  const browserCount = sessions.value.filter((item) => normalizeClientType(item.clientType) === 'web').length
  const currentCount = sessions.value.filter((item) => item.current).length
  const lastActive = compactTime(sessions.value[0]?.lastActiveTime)
  return [
    { label: '在线会话', value: total.value.toLocaleString('zh-CN') },
    { label: 'Web 客户端', value: browserCount.toLocaleString('zh-CN') },
    { label: '当前账号会话', value: currentCount.toLocaleString('zh-CN') },
    { label: '最近活跃', value: lastActive }
  ]
})

async function loadSessions() {
  loading.value = true
  try {
    const result = await pageOnlineUsers(query)
    sessions.value = result.list
    total.value = result.total
  } catch (error) {
    ElMessage.error(resolveError(error, '在线用户加载失败'))
  } finally {
    loading.value = false
    updateTableHeight()
  }
}

function handleSizeChange() {
  query.page = 1
  loadSessions()
}

async function handleRevoke(row: OnlineSession) {
  if (row.current) return
  try {
    await ElMessageBox.confirm(`确认下线 ${userName(row)} 的当前会话？`, '下线会话', {
      type: 'warning',
      confirmButtonText: '确认下线',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  revokingId.value = row.sessionId
  try {
    await revokeOnlineSession(row.sessionId)
    ElMessage.success('会话已下线')
    await loadSessions()
  } catch (error) {
    ElMessage.error(resolveError(error, '下线会话失败'))
  } finally {
    revokingId.value = undefined
  }
}

function userName(row: OnlineSession) {
  return row.nickName || row.userName || String(row.userId || '-')
}

function clientText(row: OnlineSession) {
  return [row.clientType || 'WEB', row.clientVersion].filter(Boolean).join(' / ')
}

function normalizeClientType(value?: string) {
  return (value || 'web').toLowerCase()
}

function compactTime(value?: string) {
  if (!value) return '-'
  const normalized = value.replace('T', ' ')
  const match = normalized.match(/^\d{4}-(\d{2}-\d{2})\s+(\d{2}:\d{2})/)
  return match ? `${match[1]} ${match[2]}` : normalized
}

function resolveError(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

onMounted(loadSessions)
</script>

<style scoped>
.session-filter {
  width: 320px;
}

@media (max-width: 768px) {
  .session-filter {
    width: 100%;
  }
}
</style>

<template>
  <section class="resource-page weblog-page">
    <div class="resource-hero">
      <div>
        <h1>实时日志</h1>
        <p>读取 logback 当前文件日志，按级别、关键词和行数查看尾部内容。</p>
      </div>
      <div class="resource-actions">
        <el-button type="primary" plain :icon="Warning" @click="showOnlyErrors">只看异常</el-button>
        <el-button
          v-permission="PermissionCodes.monitor.weblogLevel"
          type="warning"
          plain
          :icon="Operation"
          @click="openLevelDialog"
        >
          调整级别
        </el-button>
      </div>
    </div>

    <div class="weblog-file-bar">
      <div class="weblog-file-identity">
        <span class="weblog-file-icon">
          <el-icon><Document /></el-icon>
        </span>
        <div>
          <strong>{{ snapshot?.fileName || 'easy-next-admin.log' }}</strong>
          <span>{{ snapshot?.filePath || '等待读取当前 logback 文件' }}</span>
        </div>
      </div>
      <div class="weblog-file-facts">
        <span><small>大小</small>{{ formatBytes(snapshot?.fileSizeBytes) }}</span>
        <span><small>返回</small>{{ returnedLinesText }}</span>
        <span><small>更新</small>{{ snapshot?.lastModifiedTime || '-' }}</span>
        <span><small>采样</small>{{ snapshot?.sampleTime || '-' }}</span>
      </div>
    </div>

    <div v-if="errorMessage" class="inline-error">
      <span>{{ errorMessage }}</span>
      <el-button link type="primary" :loading="loading" @click="loadLogSnapshot">重试</el-button>
    </div>

    <section class="surface resource-panel weblog-panel">
      <div class="weblog-toolbar">
        <el-form :inline="true" class="filter-bar" @submit.prevent>
          <el-form-item label="关键词">
            <el-input
              v-model="query.keyword"
              placeholder="TraceId / logger / 文本"
              clearable
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item label="级别">
            <el-select v-model="query.level" placeholder="全部级别" clearable style="width: 130px">
              <el-option v-for="item in logFilterOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="行数">
            <el-select v-model="query.lines" style="width: 120px">
              <el-option v-for="item in lineOptions" :key="item" :label="`${item} 行`" :value="item" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" plain :icon="Search" :loading="loading" @click="handleSearch">查询</el-button>
            <el-button @click="resetFilters">重置</el-button>
          </el-form-item>
        </el-form>
        <div class="weblog-read-state" :class="{ 'is-warning': snapshot && !snapshot.readable }">
          {{ snapshot?.message || '读取当前日志文件尾部内容' }}
        </div>
      </div>

      <div v-loading="loading" class="weblog-console">
        <template v-if="logLines.length">
          <div
            v-for="(line, index) in logLines"
            :key="`${index}-${line}`"
            class="weblog-line"
            :class="levelClass(line)"
          >
            <span class="weblog-line-number">{{ formatLineNumber(index + 1) }}</span>
            <span class="weblog-line-text">{{ line }}</span>
          </div>
        </template>
        <div v-else class="weblog-empty">
          {{ snapshot && !snapshot.readable ? snapshot.message : '当前条件下暂无日志内容' }}
        </div>
      </div>
    </section>

    <el-dialog v-model="levelDialogVisible" title="调整日志级别" width="520px" append-to-body>
      <el-alert
        title="仅允许调整白名单 logger 或 Spring Boot logger group，操作会写入审计。"
        type="warning"
        :closable="false"
        show-icon
        class="level-alert"
      />
      <el-form label-width="96px" class="level-form">
        <el-form-item label="日志名称" required>
          <el-select v-model="levelForm.name" filterable allow-create default-first-option placeholder="选择或输入 logger">
            <el-option v-for="item in loggerNameOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="日志级别" required>
          <el-select v-model="levelForm.configuredLevel" placeholder="选择级别">
            <el-option v-for="item in logLevelOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="levelDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="levelSaving" @click="submitLogLevel">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Document, Operation, Search, Warning } from '@element-plus/icons-vue'
import { configureWebLogLevel, getWebLogFileSnapshot } from '@/features/monitor/api'
import type { NumericStat, WebLogFileSnapshot } from '@/features/monitor/types'
import { PermissionCodes } from '@/permissions/codes'

const loading = ref(false)
const query = reactive({
  keyword: '',
  level: '',
  lines: 300
})
const snapshot = ref<WebLogFileSnapshot | null>(null)
const errorMessage = ref('')
const levelDialogVisible = ref(false)
const levelSaving = ref(false)
const levelForm = reactive({
  name: 'com.laker.admin',
  configuredLevel: 'INFO'
})
const loggerNameOptions = [
  'ROOT',
  'com.laker.admin',
  'org.springframework',
  'org.hibernate',
  'com.zaxxer.hikari',
  'org.mybatis',
  'org.apache.ibatis'
]
const logLevelOptions = ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR', 'OFF']
const logFilterOptions = [
  { label: '全部级别', value: '' },
  { label: 'TRACE', value: 'TRACE' },
  { label: 'DEBUG', value: 'DEBUG' },
  { label: 'INFO', value: 'INFO' },
  { label: 'WARN', value: 'WARN' },
  { label: 'ERROR', value: 'ERROR' }
]
const lineOptions = [200, 300, 500, 1000, 2000]

const logLines = computed(() => snapshot.value?.lines || [])
const returnedLinesText = computed(() => {
  if (!snapshot.value) return '-'
  return `${snapshot.value.returnedLines}/${snapshot.value.requestedLines} 行`
})

onMounted(loadLogSnapshot)

async function loadLogSnapshot() {
  loading.value = true
  errorMessage.value = ''
  try {
    snapshot.value = await getWebLogFileSnapshot({
      keyword: query.keyword,
      level: query.level,
      lines: query.lines
    })
  } catch (error) {
    errorMessage.value = resolveError(error, '实时日志加载失败')
  } finally {
    loading.value = false
  }
}

function showOnlyErrors() {
  query.level = 'ERROR'
  handleSearch()
}

function resetFilters() {
  query.keyword = ''
  query.level = ''
  query.lines = 300
  loadLogSnapshot()
}

function openLevelDialog() {
  levelDialogVisible.value = true
}

async function submitLogLevel() {
  const name = levelForm.name.trim()
  if (!name) {
    ElMessage.warning('请输入日志名称')
    return
  }
  levelSaving.value = true
  try {
    await configureWebLogLevel({ name, configuredLevel: levelForm.configuredLevel })
    ElMessage.success('日志级别已调整')
    levelDialogVisible.value = false
    loadLogSnapshot()
  } catch {
    // 统一错误提示已由请求拦截器处理。
  } finally {
    levelSaving.value = false
  }
}

function handleSearch() {
  loadLogSnapshot()
}

function levelClass(line: string) {
  const level = line.match(/\s(TRACE|DEBUG|INFO|WARN|ERROR)\s/)?.[1]?.toLowerCase()
  return level ? `is-${level}` : ''
}

function formatLineNumber(value: number) {
  return String(value).padStart(4, '0')
}

function formatBytes(value?: NumericStat) {
  const bytes = typeof value === 'number' ? value : Number(value)
  if (!Number.isFinite(bytes) || bytes <= 0) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  return `${(bytes / 1024 / 1024 / 1024).toFixed(1)} GB`
}

function resolveError(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}
</script>

<style scoped>
.weblog-file-bar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  flex: 0 0 auto;
  gap: 18px;
  margin-bottom: 16px;
  padding: 14px 18px;
  border: 1px solid #d9e4f2;
  border-radius: 8px;
  background: #fff;
}

.weblog-file-identity {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 12px;
}

.weblog-file-identity > div {
  min-width: 0;
}

.weblog-file-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 38px;
  height: 38px;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  background: #eff6ff;
  color: #2563eb;
  font-size: 18px;
}

.weblog-file-identity strong,
.weblog-file-identity span {
  display: block;
}

.weblog-file-identity strong {
  color: #111827;
  font-size: 15px;
  font-weight: 700;
}

.weblog-file-identity div > span {
  overflow: hidden;
  color: #5f6f86;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.weblog-file-facts {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  max-width: 560px;
  gap: 8px 22px;
  color: #1f2937;
  white-space: nowrap;
}

.weblog-file-facts span {
  display: grid;
  gap: 2px;
  min-width: 76px;
  font-size: 13px;
  font-weight: 700;
}

.weblog-file-facts small {
  color: #75849a;
  font-size: 12px;
  font-weight: 500;
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

.weblog-page {
  overflow: hidden;
}

.weblog-panel {
  flex: 1 1 auto !important;
  min-height: 0;
  overflow: hidden;
}

.weblog-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 18px;
  border-bottom: 1px solid #dfe7f2;
  background: #f8fafc;
}

.weblog-toolbar .filter-bar {
  flex: 1;
  min-width: 0;
  padding: 0;
  border: 0;
  background: transparent;
}

.weblog-read-state {
  flex: 0 0 auto;
  max-width: 320px;
  color: #5f6f86;
  font-size: 13px;
  line-height: 32px;
  text-align: right;
}

.weblog-read-state.is-warning {
  color: #b45309;
}

.weblog-console {
  flex: 1 1 auto;
  min-height: 0;
  max-height: none;
  overflow: auto;
  padding: 16px 0;
  background: #0f172a;
  color: #cbd5e1;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 13px;
}

.weblog-line {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr);
  gap: 14px;
  min-height: 24px;
  padding: 1px 18px;
  line-height: 22px;
}

.weblog-line:hover {
  background: rgba(96, 165, 250, 0.12);
}

.weblog-line-number {
  color: #64748b;
  text-align: right;
  user-select: none;
}

.weblog-line-text {
  min-width: 0;
  white-space: pre-wrap;
  word-break: break-word;
}

.weblog-line.is-error .weblog-line-text {
  color: #fecaca;
}

.weblog-line.is-warn .weblog-line-text {
  color: #fde68a;
}

.weblog-line.is-info .weblog-line-text {
  color: #bfdbfe;
}

.weblog-line.is-debug .weblog-line-text,
.weblog-line.is-trace .weblog-line-text {
  color: #c4b5fd;
}

.weblog-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 360px;
  color: #94a3b8;
}

.level-alert {
  margin-bottom: 16px;
}

.level-form :deep(.el-select) {
  width: 100%;
}

@media (max-width: 1200px) {
  .weblog-file-bar {
    grid-template-columns: 1fr;
  }

  .weblog-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .weblog-file-facts {
    justify-content: flex-start;
  }

  .weblog-read-state {
    max-width: none;
    line-height: 20px;
    text-align: left;
  }
}

@media (max-width: 768px) {
  .weblog-file-facts span {
    min-width: calc(50% - 12px);
  }

  .weblog-console {
    min-height: 0;
    max-height: none;
  }

  .weblog-line {
    grid-template-columns: 48px minmax(0, 1fr);
    gap: 10px;
    padding: 1px 12px;
  }
}
</style>

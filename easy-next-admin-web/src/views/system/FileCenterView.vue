<template>
  <section class="resource-page">
    <div class="resource-hero">
      <div>
        <h1>文件中心</h1>
        <p>统一管理企业附件、下载权限和存储状态，所有下载都通过鉴权接口完成。</p>
      </div>
      <div class="resource-actions">
        <el-button v-permission="PermissionCodes.system.file.upload" type="primary" :icon="Upload" @click="openUploadDialog">
          上传文件
        </el-button>
      </div>
    </div>

    <section ref="tablePanelRef" class="surface resource-panel is-fluid-table">
      <div class="table-control-row">
        <el-form :inline="true" class="filter-bar file-filter-bar">
          <el-form-item label="文件名">
            <el-input v-model="query.keyWord" :prefix-icon="Search" placeholder="搜索文件名" clearable @keyup.enter="handleSearch" />
          </el-form-item>
          <el-form-item label="类型">
            <el-select v-model="typeFilter" placeholder="全部类型" clearable>
              <el-option label="图片" value="image" />
              <el-option label="办公文档" value="office" />
              <el-option label="压缩包" value="archive" />
              <el-option label="文本" value="text" />
              <el-option label="其他" value="other" />
            </el-select>
          </el-form-item>
          <el-form-item label="存储">
            <el-select v-model="storageFilter" placeholder="全部存储" clearable>
              <el-option label="本地" value="LOCAL" />
              <el-option label="OSS" value="OSS" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" plain :icon="Search" :loading="loading" @click="handleSearch">查询</el-button>
            <el-button @click="resetFilters">重置</el-button>
          </el-form-item>
        </el-form>
        <TableToolbar v-model:columns="columns" class="table-toolbar-inline" />
      </div>

      <el-table
        v-loading="loading"
        :data="filteredFiles"
        row-key="fileId"
        :height="tableHeight"
        class="admin-table file-table"
        empty-text="暂无文件数据"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="44" />
        <el-table-column v-if="visibleColumns.file" label="文件" min-width="260">
          <template #default="{ row }">
            <div class="file-name-cell">
              <span :class="['file-type-icon', `is-${fileCategory(row)}`]">
                <el-icon><Picture v-if="fileCategory(row) === 'image'" /><FilesIcon v-else /></el-icon>
              </span>
              <span class="file-name-main">
                <strong>{{ displayFileName(row) }}</strong>
                <small>{{ row.storageName || '-' }}</small>
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="visibleColumns.type" label="类型" width="120">
          <template #default="{ row }">
            <el-tag :type="fileCategoryMeta(row).tag" effect="plain">{{ fileCategoryMeta(row).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="visibleColumns.mime" prop="contentType" label="MIME" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.contentType || '-' }}</template>
        </el-table-column>
        <el-table-column v-if="visibleColumns.size" label="大小" width="110">
          <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column v-if="visibleColumns.owner" label="上传人" width="130">
          <template #default="{ row }">{{ row.nickName || row.userId || '-' }}</template>
        </el-table-column>
        <el-table-column v-if="visibleColumns.storage" label="存储" width="100">
          <template #default="{ row }">{{ row.storageType || 'LOCAL' }}</template>
        </el-table-column>
        <el-table-column v-if="visibleColumns.createdAt" prop="createTime" label="上传时间" width="170">
          <template #default="{ row }">{{ row.createTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="168" fixed="right" align="center" header-align="center">
          <template #default="{ row }">
            <div class="row-actions file-row-actions">
              <el-button
                v-permission:disable="{ permissions: PermissionCodes.system.file.list, reason: '缺少文件下载权限' }"
                class="file-download-button"
                text
                type="primary"
                :icon="Download"
                :loading="sameId(downloadingId, row.fileId)"
                @click="handleDownload(row)"
              >
                下载
              </el-button>
              <el-dropdown class="file-row-more" trigger="click" placement="bottom-end">
                <el-button class="file-more-button" text :icon="MoreFilled" title="更多操作" aria-label="更多操作" @click.stop>
                  更多
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu class="file-row-action-menu">
                    <el-dropdown-item
                      v-if="canPreviewFile(row)"
                      :disabled="!canPreviewSystemFile || sameId(previewingId, row.fileId)"
                      :title="canPreviewSystemFile ? undefined : '缺少文件预览权限'"
                      :data-testid="`file-preview-menu-${row.fileId}`"
                      @click.stop="handlePreview(row)"
                    >
                      <el-icon><View /></el-icon>
                      <span>预览文件</span>
                    </el-dropdown-item>
                    <el-dropdown-item
                      class="is-danger"
                      :disabled="!canDeleteSystemFile || sameId(deletingId, row.fileId)"
                      :title="canDeleteSystemFile ? undefined : '缺少文件删除权限'"
                      :data-testid="`file-delete-menu-${row.fileId}`"
                      @click.stop="handleDelete(row)"
                    >
                      <el-icon><Delete /></el-icon>
                      <span>删除文件</span>
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer file-table-footer">
        <el-button
          v-permission:disable="{ permissions: PermissionCodes.system.file.delete, reason: '缺少文件删除权限' }"
          :disabled="selectedFiles.length === 0"
          :loading="batchDeleting"
          :icon="Delete"
          @click="handleBatchDelete"
        >
          批量删除
        </el-button>
        <el-pagination
          v-model:current-page="page.page"
          v-model:page-size="page.limit"
          :page-sizes="[10, 20, 50]"
          :total="page.total"
          layout="total, sizes, prev, pager, next"
          @current-change="loadFiles"
          @size-change="handleSizeChange"
        />
      </div>
    </section>

    <el-dialog v-model="uploadVisible" title="上传企业文件" width="min(560px, 92vw)" destroy-on-close>
      <el-upload
        class="file-upload"
        drag
        action=""
        :auto-upload="false"
        :limit="1"
        :accept="uploadAccept"
        :file-list="uploadFiles"
        :on-change="handleUploadChange"
        :on-remove="handleUploadRemove"
        :on-exceed="handleUploadExceed"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽文件到此处或 <em>点击选择</em></div>
        <template #tip>
          <div class="file-upload-tip">
            <span>20MB 以内</span>
            <span>图片 / Office / PDF / 文本 / ZIP</span>
          </div>
        </template>
      </el-upload>
      <template #footer>
        <div class="drawer-footer">
          <el-button @click="uploadVisible = false">取消</el-button>
          <el-button type="primary" :loading="uploading" :disabled="!selectedUploadFile" @click="submitUpload">上传</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="previewVisible"
      :title="previewState?.title || '文件预览'"
      width="min(960px, 92vw)"
      class="file-preview-dialog"
      destroy-on-close
      @closed="clearPreview"
    >
      <div v-if="previewState" class="file-preview">
        <el-alert
          v-if="previewState.truncated"
          type="warning"
          :closable="false"
          title="文本内容较大，仅预览前 1MB。"
        />
        <el-image
          v-if="previewState.kind === 'image'"
          class="file-preview-image"
          :src="previewState.url || ''"
          fit="contain"
          :preview-src-list="previewState.url ? [previewState.url] : []"
          preview-teleported
        />
        <iframe
          v-else-if="previewState.kind === 'pdf'"
          class="file-preview-frame"
          :src="previewState.url || ''"
          title="PDF 文件预览"
        />
        <pre v-else class="file-preview-text">{{ previewState.text }}</pre>
      </div>
      <el-empty v-else description="暂无可预览内容" />
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import type { UploadFile, UploadFiles, UploadRawFile, UploadUserFile } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Download, Files as FilesIcon, MoreFilled, Picture, Search, Upload, UploadFilled, View } from '@element-plus/icons-vue'
import { PermissionCodes } from '@/permissions/codes'
import TableToolbar from '@/components/table/TableToolbar.vue'
import { createTableColumnState, visibleColumnMap } from '@/components/table/tableColumns'
import { useFluidTableHeight } from '@/components/table/useFluidTableHeight'
import { batchDeleteSystemFiles, deleteSystemFile, downloadSystemFile, pageSystemFiles, uploadSystemFile } from '@/features/system/fileApi'
import {
  canPreviewFile,
  displayFileName,
  fileExtension,
  resolveFileCategory,
  resolveFilePreviewKind,
  type FileCategory,
  type FilePreviewKind
} from '@/features/file/preview'
import type { FileCenterItem, FileEntityId } from '@/features/file/types'
import { downloadBlob } from '@/utils/download'
import { useAuthStore } from '@/stores/auth'

type TagType = 'success' | 'warning' | 'info' | 'primary'

interface FilePreviewState {
  title: string
  kind: FilePreviewKind
  url?: string
  text?: string
  truncated?: boolean
}

const allowedExtensions = [
  'jpg',
  'jpeg',
  'png',
  'gif',
  'webp',
  'pdf',
  'txt',
  'csv',
  'doc',
  'docx',
  'xls',
  'xlsx',
  'ppt',
  'pptx',
  'zip'
]
const maxUploadSize = 20 * 1024 * 1024
const uploadAccept = allowedExtensions.map((item) => `.${item}`).join(',')

const query = reactive({ keyWord: '' })
const page = reactive({ page: 1, limit: 10, total: 0 })
const files = ref<FileCenterItem[]>([])
const selectedFiles = ref<FileCenterItem[]>([])
const loading = ref(false)
const tablePanelRef = ref<HTMLElement>()
const { tableHeight, updateTableHeight } = useFluidTableHeight(tablePanelRef)
const uploading = ref(false)
const batchDeleting = ref(false)
const uploadVisible = ref(false)
const typeFilter = ref<FileCategory | ''>('')
const storageFilter = ref('')
const downloadingId = ref<FileEntityId>()
const previewingId = ref<FileEntityId>()
const deletingId = ref<FileEntityId>()
const selectedUploadFile = ref<File>()
const uploadFiles = ref<UploadUserFile[]>([])
const previewVisible = ref(false)
const previewState = ref<FilePreviewState>()
const authStore = useAuthStore()
const columns = ref(createTableColumnState([
  { key: 'file', label: '文件', required: true },
  { key: 'type', label: '类型' },
  { key: 'mime', label: 'MIME' },
  { key: 'size', label: '大小' },
  { key: 'owner', label: '上传人' },
  { key: 'storage', label: '存储' },
  { key: 'createdAt', label: '上传时间' }
]))

const filteredFiles = computed(() =>
  files.value.filter((item) => {
    const typeMatched = !typeFilter.value || fileCategory(item) === typeFilter.value
    const storageMatched = !storageFilter.value || (item.storageType || 'LOCAL') === storageFilter.value
    return typeMatched && storageMatched
  })
)
const visibleColumns = computed(() => visibleColumnMap(columns.value))
const canPreviewSystemFile = computed(() => authStore.hasAnyPermission([PermissionCodes.system.file.list]))
const canDeleteSystemFile = computed(() => authStore.hasAnyPermission([PermissionCodes.system.file.delete]))

onMounted(loadFiles)
onBeforeUnmount(clearPreview)

async function loadFiles() {
  loading.value = true
  try {
    const result = await pageSystemFiles({
      page: page.page,
      limit: page.limit,
      keyWord: query.keyWord.trim() || undefined
    })
    files.value = result.list
    page.total = result.total
    selectedFiles.value = []
  } finally {
    loading.value = false
    updateTableHeight()
  }
}

function handleSearch() {
  page.page = 1
  loadFiles()
}

function resetFilters() {
  query.keyWord = ''
  typeFilter.value = ''
  storageFilter.value = ''
  handleSearch()
}

function handleSizeChange(size: number) {
  page.limit = size
  page.page = 1
  loadFiles()
}

function handleSelectionChange(selection: FileCenterItem[]) {
  selectedFiles.value = selection
}

function openUploadDialog() {
  selectedUploadFile.value = undefined
  uploadFiles.value = []
  uploadVisible.value = true
}

function handleUploadChange(uploadFile: UploadFile, uploadFileList: UploadFiles) {
  const rawFile = uploadFile.raw
  if (!rawFile || !validateUploadFile(rawFile)) {
    selectedUploadFile.value = undefined
    uploadFiles.value = []
    return
  }
  selectedUploadFile.value = rawFile
  uploadFiles.value = uploadFileList.slice(-1).map(toUploadUserFile)
}

function handleUploadRemove() {
  selectedUploadFile.value = undefined
  uploadFiles.value = []
}

function handleUploadExceed(files: File[]) {
  const nextFile = files[0]
  if (!nextFile || !validateUploadFile(nextFile)) {
    return
  }
  const rawFile = nextFile as UploadRawFile
  rawFile.uid = Date.now()
  selectedUploadFile.value = nextFile
  uploadFiles.value = [
    {
      name: rawFile.name,
      size: rawFile.size,
      raw: rawFile,
      uid: rawFile.uid,
      status: 'ready'
    }
  ]
}

async function submitUpload() {
  if (!selectedUploadFile.value) {
    ElMessage.warning('请选择要上传的文件')
    return
  }
  uploading.value = true
  try {
    await uploadSystemFile(selectedUploadFile.value)
    ElMessage.success('文件已上传')
    uploadVisible.value = false
    selectedUploadFile.value = undefined
    uploadFiles.value = []
    await loadFiles()
  } finally {
    uploading.value = false
  }
}

async function handleDownload(row: FileCenterItem) {
  downloadingId.value = row.fileId
  try {
    const payload = await downloadSystemFile(row)
    downloadBlob(payload.blob, payload.filename)
    ElMessage.success('下载请求已提交')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '文件下载失败')
  } finally {
    downloadingId.value = undefined
  }
}

async function handlePreview(row: FileCenterItem) {
  const kind = resolveFilePreviewKind(row)
  if (!kind) {
    ElMessage.warning('当前文件类型暂不支持预览')
    return
  }
  previewingId.value = row.fileId
  try {
    const payload = await downloadSystemFile(row)
    clearPreview()
    const title = displayFileName(row)
    if (kind === 'text') {
      const maxPreviewSize = 1024 * 1024
      const textBlob = payload.blob.size > maxPreviewSize ? payload.blob.slice(0, maxPreviewSize) : payload.blob
      previewState.value = {
        title,
        kind,
        text: await textBlob.text(),
        truncated: payload.blob.size > maxPreviewSize
      }
    } else {
      const typedBlob = payload.blob.type ? payload.blob : new Blob([payload.blob], { type: payload.contentType })
      previewState.value = {
        title,
        kind,
        url: URL.createObjectURL(typedBlob)
      }
    }
    previewVisible.value = true
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '文件预览失败')
  } finally {
    previewingId.value = undefined
  }
}

async function handleDelete(row: FileCenterItem) {
  try {
    await ElMessageBox.confirm(`确定删除文件「${displayFileName(row)}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  deletingId.value = row.fileId
  try {
    await deleteSystemFile(row.fileId)
    ElMessage.success('文件已删除')
    await loadFiles()
  } finally {
    deletingId.value = undefined
  }
}

async function handleBatchDelete() {
  if (selectedFiles.value.length === 0) {
    return
  }
  try {
    await ElMessageBox.confirm(`确定删除已选 ${selectedFiles.value.length} 个文件吗？`, '批量删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  batchDeleting.value = true
  try {
    await batchDeleteSystemFiles(selectedFiles.value.map((item) => item.fileId))
    ElMessage.success('文件已删除')
    await loadFiles()
  } finally {
    batchDeleting.value = false
  }
}

function validateUploadFile(file: File) {
  const extension = fileExtension(file.name)
  if (!extension || !allowedExtensions.includes(extension)) {
    ElMessage.warning('不支持的文件类型')
    return false
  }
  if (file.size <= 0) {
    ElMessage.warning('文件不能为空')
    return false
  }
  if (file.size > maxUploadSize) {
    ElMessage.warning('文件大小不能超过 20MB')
    return false
  }
  return true
}

function toUploadUserFile(file: UploadFile): UploadUserFile {
  return {
    name: file.name,
    size: file.size,
    percentage: file.percentage,
    status: file.status,
    uid: file.uid,
    raw: file.raw
  }
}

function fileCategoryMeta(row: FileCenterItem): { label: string; tag: TagType } {
  const category = fileCategory(row)
  if (category === 'image') return { label: '图片', tag: 'success' }
  if (category === 'office') return { label: '办公文档', tag: 'primary' }
  if (category === 'archive') return { label: '压缩包', tag: 'warning' }
  if (category === 'text') return { label: '文本', tag: 'info' }
  return { label: '其他', tag: 'info' }
}

function fileCategory(row: FileCenterItem): FileCategory {
  return resolveFileCategory(row)
}

function formatFileSize(size?: number) {
  const value = Number(size || 0)
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  if (value < 1024 * 1024 * 1024) return `${(value / 1024 / 1024).toFixed(1)} MB`
  return `${(value / 1024 / 1024 / 1024).toFixed(1)} GB`
}

function sameId(left?: FileEntityId, right?: FileEntityId) {
  return String(left ?? '') === String(right ?? '')
}

function clearPreview() {
  if (previewState.value?.url) {
    URL.revokeObjectURL(previewState.value.url)
  }
  previewState.value = undefined
}
</script>

<style scoped>
.file-filter-bar .el-input {
  width: 240px;
}

.file-filter-bar .el-select {
  width: 132px;
}

.file-table {
  --el-table-fixed-right-column: inset -8px 0 8px -8px rgba(15, 23, 42, 0.08);
}

.file-name-cell {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
}

.file-type-icon {
  flex: 0 0 auto;
  width: 34px;
  height: 34px;
  display: inline-grid;
  place-items: center;
  border-radius: 8px;
  background: #f1f5f9;
  color: #475569;
}

.file-type-icon.is-image {
  background: #ecfdf5;
  color: #0f766e;
}

.file-type-icon.is-office {
  background: #eff6ff;
  color: #2563eb;
}

.file-type-icon.is-archive {
  background: #fffbeb;
  color: #b45309;
}

.file-name-main,
.file-name-main strong,
.file-name-main small {
  display: block;
  min-width: 0;
}

.file-name-main strong,
.file-name-main small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-name-main small {
  margin-top: 2px;
  color: var(--ea-muted);
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", monospace;
  font-size: 12px;
}

.file-table-footer {
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.file-row-actions {
  justify-content: center;
  gap: 4px;
}

.file-row-actions :deep(.el-button.is-text) {
  min-height: 28px;
  padding-right: 5px;
  padding-left: 5px;
  font-weight: 600;
}

.file-row-actions :deep(.file-download-button.el-button) {
  color: var(--ea-primary);
}

.file-row-actions :deep(.file-more-button.el-button) {
  --el-button-text-color: #64748b;
  --el-button-hover-text-color: var(--ea-primary);
  --el-button-active-text-color: #64748b;
  color: #64748b;
}

.file-row-actions :deep(.file-more-button.el-button .el-icon),
.file-row-actions :deep(.file-more-button.el-button span) {
  color: inherit;
}

.file-row-actions :deep(.file-more-button.el-button:hover) {
  color: var(--ea-primary);
}

.file-row-actions :deep(.file-more-button.el-button:focus:not(:hover)),
.file-row-actions :deep(.file-more-button.el-button:active:not(:hover)) {
  color: #64748b;
}

.file-row-more {
  display: inline-flex;
}

.file-upload-tip {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
  color: var(--ea-muted);
  font-size: 12px;
}

.file-preview {
  display: grid;
  gap: 12px;
  min-height: 360px;
}

.file-preview-image {
  width: 100%;
  height: min(64vh, 620px);
  border: 1px solid var(--ea-border);
  border-radius: 8px;
  background: #0f172a;
}

.file-preview-frame {
  width: 100%;
  height: min(66vh, 680px);
  border: 1px solid var(--ea-border);
  border-radius: 8px;
  background: #fff;
}

.file-preview-text {
  min-height: 360px;
  max-height: min(66vh, 680px);
  margin: 0;
  overflow: auto;
  border: 1px solid var(--ea-border);
  border-radius: 8px;
  padding: 14px;
  background: #0f172a;
  color: #e2e8f0;
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", monospace;
  font-size: 13px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

.file-upload-tip span {
  border: 1px solid var(--ea-border);
  border-radius: 999px;
  padding: 3px 8px;
  background: #f8fafc;
}

:global(.file-row-action-menu .el-dropdown-menu__item) {
  gap: 8px;
  min-width: 132px;
  font-weight: 600;
}

:global(.file-row-action-menu .el-dropdown-menu__item .el-icon) {
  margin-right: 0;
}

:global(.file-row-action-menu .el-dropdown-menu__item.is-danger:not(.is-disabled)) {
  color: #dc2626;
}

@media (max-width: 760px) {
  .file-filter-bar .el-input,
  .file-filter-bar .el-select {
    width: 100%;
  }

  .file-table-footer {
    display: grid;
    justify-content: stretch;
  }
}
</style>

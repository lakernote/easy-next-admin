<template>
  <section class="resource-page business-number-page">
    <div class="resource-hero">
      <div>
        <h1>编号规则</h1>
        <p>维护申请单号、工单号、合同号等业务可读编号规则，业务代码按规则编码取号。</p>
      </div>
      <div class="resource-actions">
        <el-button :icon="RefreshRight" :loading="loading" @click="loadRules">刷新</el-button>
        <el-button v-permission="PermissionCodes.businessNumber.edit" type="primary" :icon="Plus" @click="openEditor()">
          新增规则
        </el-button>
      </div>
    </div>

    <div class="resource-metrics is-four">
      <div class="resource-metric">
        <span>规则总数</span>
        <strong>{{ ruleTotal }}</strong>
      </div>
      <div class="resource-metric">
        <span>本页启用</span>
        <strong>{{ enabledCount }}</strong>
      </div>
      <div class="resource-metric">
        <span>按日流水</span>
        <strong>{{ dailyCount }}</strong>
      </div>
      <div class="resource-metric">
        <span>最近生成</span>
        <strong class="last-number">{{ lastGeneratedNumber || '-' }}</strong>
      </div>
    </div>

    <section ref="tablePanelRef" class="surface resource-panel is-fluid-table">
      <div class="table-control-row">
        <el-form :inline="true" class="filter-bar number-filter-bar" @submit.prevent>
          <el-form-item label="关键词">
            <el-input
              v-model="query.keyword"
              clearable
              placeholder="规则编码 / 名称 / 前缀"
              :prefix-icon="Search"
              @keyup.enter="handleSearch"
              @clear="handleSearch"
            />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.enable" clearable placeholder="全部" @change="handleSearch">
              <el-option label="启用" :value="true" />
              <el-option label="停用" :value="false" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" plain :icon="Search" :loading="loading" @click="handleSearch">查询</el-button>
          </el-form-item>
        </el-form>
        <TableToolbar v-model:columns="ruleColumns" class="table-toolbar-inline" />
      </div>

      <el-table
        v-loading="loading"
        :data="rules"
        row-key="id"
        :height="tableHeight"
        class="admin-table number-rule-table"
        empty-text="暂无编号规则"
      >
        <el-table-column v-if="visibleRuleColumns.rule" label="规则" min-width="220">
          <template #default="{ row }">
            <div class="number-main-cell">
              <strong>{{ row.ruleName }}</strong>
              <small>{{ row.ruleCode }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="visibleRuleColumns.preview" label="格式预览" min-width="230" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="number-main-cell">
              <strong class="mono-value">{{ row.sampleNumber }}</strong>
              <small>{{ row.datePatternName }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="visibleRuleColumns.pattern" label="规则参数" min-width="190">
          <template #default="{ row }">
            <div class="number-rule-tags">
              <el-tag effect="plain">{{ row.prefix }}</el-tag>
              <el-tag effect="plain" type="info">{{ row.sequenceWidth }} 位</el-tag>
              <el-tag v-if="row.sequenceStep !== 1" effect="plain" type="warning">步长 {{ row.sequenceStep }}</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="visibleRuleColumns.status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enable ? 'success' : 'info'" effect="plain">
              {{ row.enable ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="visibleRuleColumns.updatedAt" prop="updateTime" label="更新时间" width="168" show-overflow-tooltip />
        <el-table-column v-if="visibleRuleColumns.remark" prop="remark" label="说明" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="210" fixed="right" align="center" header-align="center">
          <template #default="{ row }">
            <div class="row-actions number-row-actions">
              <el-button
                v-permission:disable="PermissionCodes.businessNumber.generate"
                text
                :loading="generatingRuleCode === row.ruleCode"
                @click="handleGenerate(row)"
              >
                生成
              </el-button>
              <el-button v-permission:disable="PermissionCodes.businessNumber.edit" text :icon="EditPen" @click="openEditor(row)">
                编辑
              </el-button>
              <el-button
                v-permission:disable="PermissionCodes.businessNumber.edit"
                text
                :icon="Delete"
                class="danger-text-button"
                @click="handleDelete(row)"
              >
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer is-split">
        <span>共 {{ ruleTotal }} 条规则</span>
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.limit"
          background
          layout="sizes, prev, pager, next"
          :total="ruleTotal"
          :page-sizes="[10, 20, 50]"
          @current-change="loadRules"
          @size-change="handleSizeChange"
        />
      </div>
    </section>

    <el-drawer v-model="editorVisible" :title="ruleForm.id ? '编辑编号规则' : '新增编号规则'" size="min(560px, 92vw)">
      <el-form label-position="top">
        <el-form-item label="规则编码">
          <el-input v-model="ruleForm.ruleCode" :disabled="Boolean(ruleForm.id)" placeholder="PURCHASE_REQUEST" />
        </el-form-item>
        <el-form-item label="规则名称">
          <el-input v-model="ruleForm.ruleName" placeholder="采购申请单号" />
        </el-form-item>
        <div class="number-form-grid">
          <el-form-item label="编号前缀">
            <el-input v-model="ruleForm.prefix" placeholder="PR" />
          </el-form-item>
          <el-form-item label="日期规则">
            <el-select v-model="ruleForm.datePattern">
              <el-option v-for="option in datePatternOptions" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
          </el-form-item>
        </div>
        <div class="number-form-grid">
          <el-form-item label="分隔符">
            <el-select v-model="ruleForm.separator">
              <el-option v-for="option in separatorOptions" :key="option.label" :label="option.label" :value="option.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="启用状态">
            <el-switch v-model="ruleForm.enable" active-text="启用" inactive-text="停用" />
          </el-form-item>
        </div>
        <div class="number-form-grid">
          <el-form-item label="流水位数">
            <el-input-number v-model="ruleForm.sequenceWidth" :min="1" :max="12" controls-position="right" />
          </el-form-item>
          <el-form-item label="递增步长">
            <el-input-number v-model="ruleForm.sequenceStep" :min="1" :max="1000" controls-position="right" />
          </el-form-item>
        </div>
        <el-form-item label="初始当前值">
          <el-input-number v-model="ruleForm.initialValue" :min="0" :max="999999999999" controls-position="right" />
        </el-form-item>
        <el-form-item label="格式预览">
          <div class="number-preview">
            <span>{{ formPreview }}</span>
          </div>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="ruleForm.remark" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button
          v-permission:disable="PermissionCodes.businessNumber.edit"
          type="primary"
          :loading="saving"
          @click="saveRule"
        >
          保存
        </el-button>
      </template>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Delete, EditPen, Plus, RefreshRight, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { PermissionCodes } from '@/permissions/codes'
import TableToolbar from '@/components/table/TableToolbar.vue'
import { createTableColumnState, visibleColumnMap } from '@/components/table/tableColumns'
import { useFluidTableHeight } from '@/components/table/useFluidTableHeight'
import { deleteBusinessNumberRule, generateBusinessNumber, pageBusinessNumberRules, saveBusinessNumberRule } from '@/features/business-number/api'
import type { BusinessNumberDatePattern, BusinessNumberRule, BusinessNumberRulePayload, BusinessNumberRuleQuery } from '@/features/business-number/types'

type RuleForm = BusinessNumberRulePayload

const defaultForm = (): RuleForm => ({
  id: undefined,
  ruleCode: '',
  ruleName: '',
  prefix: '',
  datePattern: 'yyyyMMdd',
  separator: '-',
  sequenceWidth: 6,
  sequenceStep: 1,
  initialValue: 0,
  enable: true,
  remark: ''
})

const datePatternOptions: Array<{ label: string; value: BusinessNumberDatePattern }> = [
  { label: '按日重置 yyyyMMdd', value: 'yyyyMMdd' },
  { label: '按月重置 yyyyMM', value: 'yyyyMM' },
  { label: '按年重置 yyyy', value: 'yyyy' },
  { label: '永续流水', value: 'NONE' }
]

const separatorOptions = [
  { label: '短横线 -', value: '-' },
  { label: '下划线 _', value: '_' },
  { label: '斜杠 /', value: '/' },
  { label: '无分隔符', value: '' }
]

const tablePanelRef = ref<HTMLElement>()
const { tableHeight, updateTableHeight } = useFluidTableHeight(tablePanelRef)
const loading = ref(false)
const saving = ref(false)
const editorVisible = ref(false)
const generatingRuleCode = ref('')
const lastGeneratedNumber = ref('')
const rules = ref<BusinessNumberRule[]>([])
const ruleTotal = ref(0)
const query = reactive<BusinessNumberRuleQuery>({
  page: 1,
  limit: 10,
  keyword: undefined,
  enable: undefined
})
const ruleForm = reactive<RuleForm>(defaultForm())
const ruleColumns = ref(createTableColumnState([
  { key: 'rule', label: '规则', required: true },
  { key: 'preview', label: '格式预览', required: true },
  { key: 'pattern', label: '规则参数' },
  { key: 'status', label: '状态' },
  { key: 'updatedAt', label: '更新时间' },
  { key: 'remark', label: '说明' }
]))

const visibleRuleColumns = computed(() => visibleColumnMap(ruleColumns.value))
const enabledCount = computed(() => rules.value.filter((item) => item.enable).length)
const dailyCount = computed(() => rules.value.filter((item) => item.datePattern === 'yyyyMMdd').length)
const formPreview = computed(() => previewRule(ruleForm))

onMounted(loadRules)

async function loadRules() {
  loading.value = true
  try {
    const result = await pageBusinessNumberRules(query)
    rules.value = result.list
    ruleTotal.value = result.total
  } finally {
    loading.value = false
    updateTableHeight()
  }
}

function handleSearch() {
  query.page = 1
  loadRules()
}

function handleSizeChange() {
  query.page = 1
  loadRules()
}

function openEditor(row?: BusinessNumberRule) {
  Object.assign(ruleForm, defaultForm(), row ? toForm(row) : {})
  editorVisible.value = true
}

async function saveRule() {
  if (!validateForm()) return
  saving.value = true
  try {
    await saveBusinessNumberRule(toPayload(ruleForm))
    ElMessage.success('编号规则已保存')
    editorVisible.value = false
    await loadRules()
  } finally {
    saving.value = false
  }
}

async function handleGenerate(row: BusinessNumberRule) {
  generatingRuleCode.value = row.ruleCode
  try {
    const result = await generateBusinessNumber(row.ruleCode)
    lastGeneratedNumber.value = result.number
    ElMessage.success(`已生成 ${result.number}`)
  } finally {
    generatingRuleCode.value = ''
  }
}

async function handleDelete(row: BusinessNumberRule) {
  try {
    await ElMessageBox.confirm(`确认删除“${row.ruleName}”？业务代码继续使用该规则时会取号失败。`, '删除编号规则', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
      confirmButtonClass: 'el-button--danger'
    })
  } catch {
    return
  }
  await deleteBusinessNumberRule(row.id)
  ElMessage.success('编号规则已删除')
  await loadRules()
}

function validateForm() {
  if (!ruleForm.ruleCode.trim()) {
    ElMessage.warning('规则编码不能为空')
    return false
  }
  if (!ruleForm.ruleName.trim()) {
    ElMessage.warning('规则名称不能为空')
    return false
  }
  if (!ruleForm.prefix.trim()) {
    ElMessage.warning('编号前缀不能为空')
    return false
  }
  return true
}

function toPayload(form: RuleForm): BusinessNumberRulePayload {
  return {
    id: form.id,
    ruleCode: form.ruleCode.trim().toUpperCase(),
    ruleName: form.ruleName.trim(),
    prefix: form.prefix.trim().toUpperCase(),
    datePattern: form.datePattern,
    separator: form.separator ?? '-',
    sequenceWidth: Number(form.sequenceWidth || 6),
    sequenceStep: Number(form.sequenceStep || 1),
    initialValue: Number(form.initialValue || 0),
    enable: form.enable !== false,
    remark: form.remark?.trim()
  }
}

function toForm(row: BusinessNumberRule): RuleForm {
  return {
    id: row.id,
    ruleCode: row.ruleCode,
    ruleName: row.ruleName,
    prefix: row.prefix,
    datePattern: row.datePattern,
    separator: row.separator,
    sequenceWidth: row.sequenceWidth,
    sequenceStep: row.sequenceStep,
    initialValue: row.initialValue,
    enable: row.enable,
    remark: row.remark || ''
  }
}

function previewRule(form: RuleForm) {
  const prefix = form.prefix.trim().toUpperCase() || 'PR'
  const dateSegment = dateSegmentFor(form.datePattern)
  const nextValue = Number(form.initialValue || 0) + Number(form.sequenceStep || 1)
  const sequence = String(nextValue).padStart(Number(form.sequenceWidth || 6), '0')
  const separator = form.separator ?? '-'
  const parts = dateSegment ? [prefix, dateSegment, sequence] : [prefix, sequence]
  return separator ? parts.join(separator) : parts.join('')
}

function dateSegmentFor(pattern: string) {
  const date = new Date()
  const year = String(date.getFullYear())
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  if (pattern === 'NONE') return ''
  if (pattern === 'yyyy') return year
  if (pattern === 'yyyyMM') return `${year}${month}`
  return `${year}${month}${day}`
}
</script>

<style scoped>
.business-number-page .resource-metric .last-number {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", monospace;
  font-size: 15px;
}

.number-filter-bar :deep(.el-input) {
  width: 260px;
}

.number-filter-bar :deep(.el-select) {
  width: 130px;
}

.number-rule-table {
  --el-table-fixed-right-column: inset -8px 0 8px -8px rgba(15, 23, 42, 0.08);
}

.number-main-cell {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.number-main-cell strong,
.number-main-cell small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.number-main-cell strong {
  color: var(--ea-text);
}

.number-main-cell small {
  color: var(--ea-muted);
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", monospace;
  font-size: 12px;
}

.mono-value {
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", monospace;
}

.number-rule-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.number-row-actions {
  justify-content: center;
  gap: 8px;
  white-space: nowrap;
}

.danger-text-button {
  color: var(--el-color-danger);
}

.number-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.number-form-grid :deep(.el-input-number) {
  width: 100%;
}

.number-preview {
  width: 100%;
  min-height: 34px;
  display: flex;
  align-items: center;
  border: 1px solid var(--ea-border);
  border-radius: 8px;
  padding: 0 12px;
  background: #f8fafc;
}

.number-preview span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", monospace;
  color: var(--ea-text);
}

@media (max-width: 768px) {
  .number-filter-bar :deep(.el-input),
  .number-filter-bar :deep(.el-select) {
    width: 100%;
  }

  .number-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>

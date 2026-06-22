<template>
  <section class="request-page">
    <div class="request-head">
      <div>
        <h1>采购申请</h1>
        <p>填写采购物品、预算和用途，提交后按金额自动进入负责人或总经办审批。</p>
      </div>
      <div class="head-actions">
        <el-button :icon="Finished" @click="goMyStarted">我的流程</el-button>
      </div>
    </div>

    <section class="surface route-summary">
      <div class="section-head">
        <div>
          <h2>采购流程</h2>
          <p>5000 元以内走部门负责人审批，超过 5000 元直接进入总经办审批。</p>
        </div>
      </div>
      <div class="approval-rules" aria-label="采购审批规则">
        <div class="approval-rule">
          <span>1</span>
          <strong>填写采购单</strong>
        </div>
        <div class="approval-rule">
          <span>2</span>
          <strong>金额分流</strong>
        </div>
        <div class="approval-rule">
          <span>3</span>
          <strong>负责人审批</strong>
        </div>
        <div class="approval-rule is-branch">
          <span>4</span>
          <strong>大额审批</strong>
        </div>
      </div>
    </section>

    <section class="paper-form-area">
      <el-form ref="applyFormRef" :model="applyForm" :rules="applyRules" label-position="top" class="apply-form paper-apply-form">
        <WorkflowApplicationPaper
          :title="purchaseApplyPaperTitle"
          :meta="applyPaperMeta"
          :fields="purchaseApplyPaperFields"
          :approvals="purchaseApplyPaperApprovals"
        >
          <template #field-itemName>
            <el-form-item prop="itemName" class="paper-form-item">
              <el-input v-model="applyForm.itemName" maxlength="120" placeholder="例如：办公显示器、云服务资源" />
            </el-form-item>
          </template>
          <template #field-category>
            <el-form-item prop="category" class="paper-form-item">
              <el-select v-model="applyForm.category" class="full-width" placeholder="请选择采购类别">
                <el-option label="办公用品" value="OFFICE_SUPPLIES" />
                <el-option label="IT 设备" value="IT_EQUIPMENT" />
                <el-option label="软件服务" value="SOFTWARE_SERVICE" />
                <el-option label="行政服务" value="ADMIN_SERVICE" />
              </el-select>
            </el-form-item>
          </template>
          <template #field-quantity>
            <el-form-item prop="quantity" class="paper-form-item">
              <el-input-number v-model="applyForm.quantity" :min="1" :step="1" :precision="0" :controls="false" class="full-width" />
            </el-form-item>
          </template>
          <template #field-estimatedAmount>
            <el-form-item prop="estimatedAmount" class="paper-form-item">
              <el-input-number v-model="applyForm.estimatedAmount" :min="0.01" :step="100" :precision="2" :controls="false" class="full-width" />
              <p class="paper-field-help">超过 5000 元直接进入总经办审批。</p>
            </el-form-item>
          </template>
          <template #field-requiredDate>
            <el-form-item prop="requiredDate" class="paper-form-item">
              <el-date-picker v-model="applyForm.requiredDate" value-format="YYYY-MM-DD" type="date" class="full-width" />
            </el-form-item>
          </template>
          <template #field-reason>
            <el-form-item prop="reason" class="paper-form-item">
              <el-input v-model="applyForm.reason" type="textarea" :rows="4" maxlength="500" placeholder="请说明用途、预算依据和期望时间" />
            </el-form-item>
          </template>
        </WorkflowApplicationPaper>
      </el-form>

      <div class="form-submit-bar">
        <span>提交后进入我的流程，可在“我发起的”查看进度。</span>
        <div>
          <el-button :icon="RefreshRight" @click="resetApplyForm">重置</el-button>
          <el-button v-permission:disable="PermissionCodes.workflow.instanceStart" type="primary" :loading="submittingApply" @click="submitPurchase">提交申请</el-button>
        </div>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { Finished, RefreshRight } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { PermissionCodes } from '@/permissions/codes'
import { applyPurchaseRequest } from '@/features/workflow/applicationApi'
import { workflowTaskCenterPath } from '@/features/workflow/taskCenterTabs'
import { useAuthStore } from '@/stores/auth'
import WorkflowApplicationPaper from './components/WorkflowApplicationPaper.vue'

const auth = useAuthStore()
const router = useRouter()
const submittingApply = ref(false)
const applyFormRef = ref<FormInstance>()
const canStartWorkflow = computed(() => auth.hasAnyPermission([PermissionCodes.workflow.instanceStart]))

const applyForm = reactive({
  itemName: '',
  category: 'OFFICE_SUPPLIES',
  quantity: 1,
  estimatedAmount: 3000,
  requiredDate: '',
  reason: ''
})

const applyRules: FormRules<typeof applyForm> = {
  itemName: [
    { required: true, message: '请填写采购物品', trigger: 'blur' },
    { min: 2, message: '采购物品至少 2 个字', trigger: 'blur' }
  ],
  category: [{ required: true, message: '请选择采购类别', trigger: 'change' }],
  quantity: [{ required: true, type: 'number', min: 1, message: '请填写采购数量', trigger: 'change' }],
  estimatedAmount: [{ required: true, type: 'number', min: 0.01, message: '请填写预算金额', trigger: 'change' }],
  requiredDate: [{ required: true, message: '请选择期望到货日期', trigger: 'change' }],
  reason: [
    { required: true, message: '请填写采购事由', trigger: 'blur' },
    { min: 2, message: '采购事由至少 2 个字', trigger: 'blur' }
  ]
}

const applicantName = computed(() => currentApplicantName())
const applyDateText = computed(() => formatDate(new Date()))
const purchaseApplyPaperTitle = computed(() => '采购申请单')
const applyPaperMeta = computed(() => [
  { key: 'requestNo', label: '申请单号', value: '提交后生成' },
  { key: 'applicant', label: '申请人', value: applicantName.value },
  { key: 'date', label: '申请日期', value: applyDateText.value },
  { key: 'status', label: '流程状态', value: '待提交', tone: 'draft' as const }
])
const purchaseApplyPaperFields = [
  { key: 'itemName', label: '采购物品' },
  { key: 'category', label: '采购类别' },
  { key: 'quantity', label: '采购数量' },
  { key: 'estimatedAmount', label: '预算金额' },
  { key: 'requiredDate', label: '期望到货日期', wide: true },
  { key: 'reason', label: '采购事由', wide: true }
]
const purchaseApplyPaperApprovals = computed(() => {
  const approvals: Array<{ id: string; nodeName: string; actor: string; statusText: string; timeText: string; tone: 'current' | 'todo' }> = [
    { id: 'start', nodeName: '流程开始', actor: applicantName.value, statusText: '待提交', timeText: '-', tone: 'current' as const }
  ]
  if (Number(applyForm.estimatedAmount) > 5000) {
    approvals.push({ id: 'office', nodeName: '总经办审批', actor: '提交后自动分配', statusText: '未执行', timeText: '-', tone: 'todo' as const })
    return approvals
  }
  approvals.push({ id: 'dept', nodeName: '部门负责人审批', actor: '提交后自动分配', statusText: '未执行', timeText: '-', tone: 'todo' as const })
  return approvals
})

onMounted(resetApplyForm)

function resetApplyForm() {
  applyForm.itemName = ''
  applyForm.category = 'OFFICE_SUPPLIES'
  applyForm.quantity = 1
  applyForm.estimatedAmount = 3000
  applyForm.requiredDate = localDateAfter(7)
  applyForm.reason = ''
  applyFormRef.value?.clearValidate()
}

async function submitPurchase() {
  if (!canStartWorkflow.value) {
    ElMessage.warning('没有发起流程权限')
    return
  }
  const valid = await applyFormRef.value?.validate().catch(() => false)
  if (!valid) return
  submittingApply.value = true
  try {
    await applyPurchaseRequest({ ...applyForm })
    ElMessage.success('采购申请已提交')
    await router.push(workflowTaskCenterPath('started'))
  } finally {
    submittingApply.value = false
  }
}

function goMyStarted() {
  void router.push(workflowTaskCenterPath('started'))
}

function localDateAfter(days: number) {
  const date = new Date(Date.now() + days * 24 * 60 * 60 * 1000)
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function formatDate(value: Date) {
  const pad = (item: number) => String(item).padStart(2, '0')
  return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}`
}

function currentApplicantName() {
  const user = auth.user
  if (!user) return '当前用户'
  return user.realName || user.nickName || user.userName || '当前用户'
}
</script>

<style scoped>
.request-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 100%;
}

.surface {
  min-width: 0;
  border: 1px solid #d8e0ec;
  border-radius: 8px;
  background: #fff;
}

.request-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  padding: 4px 2px 0;
}

.request-head > div:first-child {
  min-width: 0;
}

.request-head h1,
.section-head h2 {
  margin: 0;
  color: #172033;
}

.request-head p,
.section-head p {
  margin: 6px 0 0;
  color: #66758f;
}

.head-actions,
.section-head {
  display: flex;
  align-items: center;
}

.head-actions {
  gap: 10px;
  flex: 0 0 auto;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.route-summary {
  padding: 14px 18px;
}

.section-head {
  justify-content: space-between;
  gap: 14px;
}

.approval-rules {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.approval-rule {
  display: inline-flex;
  min-height: 0;
  align-items: center;
  gap: 8px;
  border: 1px solid #d8e0ec;
  border-radius: 999px;
  padding: 6px 12px 6px 8px;
  background: #f8fafc;
  color: #172033;
  font-size: 13px;
}

.approval-rule span {
  width: 22px;
  height: 22px;
  display: inline-grid;
  place-items: center;
  border-radius: 999px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

.approval-rule strong {
  display: block;
  min-width: 0;
  white-space: nowrap;
}

.approval-rule.is-branch {
  border-color: #fed7aa;
  background: #fff7ed;
}

.approval-rule.is-branch span {
  background: #ffedd5;
  color: #c2410c;
}

.paper-form-area {
  min-width: 0;
  display: grid;
  gap: 12px;
}

.apply-form {
  min-width: 0;
}

.paper-apply-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.paper-apply-form :deep(.el-input-number) {
  width: 100%;
}

.paper-apply-form :deep(.el-textarea__inner) {
  min-height: 108px;
}

.form-submit-bar {
  width: min(980px, 100%);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 14px;
  margin: 0 auto;
  color: #66758f;
  font-size: 13px;
}

.form-submit-bar > div {
  display: flex;
  flex: 0 0 auto;
  gap: 10px;
}

.full-width {
  width: 100%;
}

@media (max-width: 760px) {
  .request-head,
  .section-head,
  .form-submit-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .approval-rules {
    gap: 8px;
  }

  .form-submit-bar > div {
    justify-content: flex-end;
  }
}
</style>

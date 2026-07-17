<template>
  <section class="leave-page">
    <div class="leave-head">
      <div>
        <h1>请假申请</h1>
        <p>填写请假时间、类型和事由，提交后进入审批流并在我的流程中跟踪。</p>
      </div>
      <div class="head-actions">
        <el-button :icon="Finished" @click="goMyStarted">我的流程</el-button>
      </div>
    </div>

    <section class="surface route-summary">
      <div class="section-head">
        <div>
          <h2>请假流程</h2>
          <p>3 天内走部门审批，超过 3 天追加总经办复核，流程通过后行政备案。</p>
        </div>
      </div>
      <div class="approval-rules" aria-label="请假审批规则">
        <div class="approval-rule">
          <span>1</span>
          <strong>填写申请</strong>
        </div>
        <div class="approval-rule">
          <span>2</span>
          <strong>部门审批</strong>
        </div>
        <div class="approval-rule is-branch">
          <span>3</span>
          <strong>超 3 天复核</strong>
        </div>
        <div class="approval-rule">
          <span>4</span>
          <strong>行政备案</strong>
        </div>
      </div>
    </section>

    <section class="paper-form-area">
      <el-form ref="applyFormRef" :model="applyForm" :rules="applyRules" label-position="top" class="leave-apply-form paper-apply-form">
        <WorkflowApplicationPaper
          :title="leaveApplyPaperTitle"
          :meta="applyPaperMeta"
          :fields="leaveApplyPaperFields"
          :approvals="leaveApplyPaperApprovals"
        >
          <template #field-leaveType>
            <el-form-item prop="leaveType" class="paper-form-item">
              <el-select v-model="applyForm.leaveType" class="full-width paper-select" placeholder="请选择请假类型">
                <el-option label="年假" value="ANNUAL" />
                <el-option label="事假" value="PERSONAL" />
                <el-option label="病假" value="SICK" />
                <el-option label="调休" value="COMPENSATORY" />
              </el-select>
            </el-form-item>
          </template>
          <template #field-leavePeriod>
            <div class="leave-period-field">
              <div class="leave-period-line">
                <span>自</span>
                <el-form-item prop="startTime" class="paper-form-item leave-period-date">
                  <el-date-picker
                    v-model="applyForm.startTime"
                    value-format="YYYY-MM-DD HH:mm:ss"
                    format="YYYY-MM-DD HH:mm"
                    type="datetime"
                    class="paper-line-control"
                    placeholder="选择开始时间"
                    @change="handleStartTimeChange"
                  />
                </el-form-item>
                <span>起，至</span>
                <el-form-item prop="endTime" class="paper-form-item leave-period-date">
                  <el-date-picker
                    v-model="applyForm.endTime"
                    value-format="YYYY-MM-DD HH:mm:ss"
                    format="YYYY-MM-DD HH:mm"
                    type="datetime"
                    class="paper-line-control"
                    placeholder="选择结束时间"
                    @change="handleEndTimeChange"
                  />
                </el-form-item>
                <span>止，共</span>
                <el-form-item prop="days" class="paper-form-item leave-period-days">
                  <el-input-number
                    v-model="applyForm.days"
                    :min="0.5"
                    :step="0.5"
                    :controls="false"
                    class="paper-day-control"
                    @change="handleDaysChange"
                  />
                </el-form-item>
                <span>天。</span>
              </div>
              <div class="duration-shortcuts" aria-label="常用请假时长">
                <button
                  v-for="preset in durationPresets"
                  :key="preset.value"
                  type="button"
                  :class="{ 'is-active': Number(applyForm.days) === preset.value }"
                  @click="applyDurationShortcut(preset.value)"
                >
                  {{ preset.label }}
                </button>
              </div>
              <p :class="['paper-field-help', Number(applyForm.days) > 3 ? 'is-warning' : '']">{{ leaveRouteHint }}</p>
            </div>
          </template>
          <template #field-reason>
            <el-form-item prop="reason" class="paper-form-item">
              <el-input v-model="applyForm.reason" class="paper-reason-input" type="textarea" :rows="4" maxlength="500" placeholder="请说明请假原因，便于审批人判断" />
            </el-form-item>
            <div class="reason-shortcuts" aria-label="常用请假事由">
              <button v-for="preset in reasonPresets" :key="preset" type="button" @click="applyReasonPreset(preset)">{{ preset }}</button>
            </div>
          </template>
        </WorkflowApplicationPaper>
      </el-form>

      <div class="form-submit-bar">
        <span>提交后进入我的流程，可在“我发起的”查看进度。</span>
        <div>
          <el-button :icon="RefreshRight" @click="resetApplyForm">重置</el-button>
          <el-button v-permission:disable="PermissionCodes.workflow.instanceStart" type="primary" :loading="submittingApply" @click="submitLeave">提交申请</el-button>
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
import { applyLeaveRequest } from '@/features/workflow/applicationApi'
import {
  calculateLeaveDaysFromRange,
  calculateLeaveEndTimeFromDays,
  formatLeaveDateTime,
  normalizeLeaveDays
} from '@/features/workflow/leavePeriod'
import { workflowTaskCenterPath } from '@/features/workflow/taskCenterTabs'
import { useAuthStore } from '@/stores/auth'
import WorkflowApplicationPaper from './components/WorkflowApplicationPaper.vue'

const auth = useAuthStore()
const router = useRouter()
const submittingApply = ref(false)
const applyFormRef = ref<FormInstance>()
const canStartWorkflow = computed(() => auth.hasAnyPermission([PermissionCodes.workflow.instanceStart]))
const durationPresets = [
  { label: '半天', value: 0.5 },
  { label: '1 天', value: 1 },
  { label: '2 天', value: 2 },
  { label: '3 天', value: 3 },
  { label: '5 天', value: 5 },
  { label: '7 天', value: 7 },
  { label: '10 天', value: 10 }
]
const reasonPresets = ['家庭事务', '身体不适', '调休补休', '其他事由']

const applyForm = reactive({
  leaveType: 'ANNUAL',
  startTime: '',
  endTime: '',
  days: 1,
  reason: ''
})

const validateEndTime = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (!value) {
    callback(new Error('请选择结束时间'))
    return
  }
  if (applyForm.startTime && calculateLeaveDaysFromRange(applyForm.startTime, value) === null) {
    callback(new Error('结束时间必须晚于开始时间'))
    return
  }
  callback()
}

const applyRules: FormRules<typeof applyForm> = {
  leaveType: [{ required: true, message: '请选择请假类型', trigger: 'change' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, validator: validateEndTime, trigger: 'change' }],
  days: [{ required: true, type: 'number', min: 0.5, message: '请填写请假天数', trigger: 'change' }],
  reason: [
    { required: true, message: '请填写请假事由', trigger: 'blur' },
    { min: 2, message: '请假事由至少 2 个字', trigger: 'blur' }
  ]
}

const applicantName = computed(() => currentApplicantName())
const applyDateText = computed(() => formatDate(new Date()))
const leaveApplyPaperTitle = computed(() => '请假申请单')
const applyPaperMeta = computed(() => [
  { key: 'requestNo', label: '申请单号', value: '提交后生成' },
  { key: 'applicant', label: '申请人', value: applicantName.value },
  { key: 'date', label: '申请日期', value: applyDateText.value },
  { key: 'status', label: '流程状态', value: '待提交', tone: 'draft' as const }
])
const leaveApplyPaperFields = [
  { key: 'leaveType', label: '请假类型' },
  { key: 'leavePeriod', label: '请假时间', span: 3 as const },
  { key: 'reason', label: '请假事由', wide: true }
]
const leaveRouteHint = computed(() => {
  if (Number(applyForm.days) > 3) return '已超过 3 天，提交后将追加总经办复核。'
  return '3 天内由部门负责人审批；可直接点常用时长自动带出结束时间。'
})
const leaveApplyPaperApprovals = computed(() => {
  const approvals = [
    { id: 'start', nodeName: '流程开始', actor: applicantName.value, statusText: '待提交', timeText: '-', tone: 'current' as const },
    { id: 'dept', nodeName: '部门负责人审批', actor: '提交后自动分配', statusText: '未执行', timeText: '-', tone: 'todo' as const }
  ]
  if (Number(applyForm.days) > 3) {
    approvals.push({ id: 'office', nodeName: '总经办复核', actor: '提交后自动分配', statusText: '未执行', timeText: '-', tone: 'todo' as const })
  }
  approvals.push({ id: 'archive', nodeName: '行政备案', actor: '系统归档', statusText: '未执行', timeText: '-', tone: 'todo' as const })
  return approvals
})

onMounted(resetApplyForm)

function resetApplyForm() {
  const now = new Date()
  const tomorrow = new Date(now.getTime() + 24 * 60 * 60 * 1000)
  applyForm.leaveType = 'ANNUAL'
  applyForm.startTime = formatLeaveDateTime(tomorrow, 9)
  applyForm.days = 1
  applyForm.endTime = calculateLeaveEndTimeFromDays(applyForm.startTime, applyForm.days)
  applyForm.reason = ''
  applyFormRef.value?.clearValidate()
}

function applyDurationShortcut(days: number) {
  applyForm.days = days
  syncEndTimeFromPeriod()
}

function applyReasonPreset(reason: string) {
  applyForm.reason = reason
}

function handleStartTimeChange() {
  syncEndTimeFromPeriod()
}

function handleDaysChange() {
  syncEndTimeFromPeriod()
}

function handleEndTimeChange() {
  syncDaysFromPeriod()
}

function syncEndTimeFromPeriod() {
  if (!applyForm.startTime || !Number.isFinite(Number(applyForm.days))) return
  applyForm.days = normalizeLeaveDays(Number(applyForm.days))
  const endTime = calculateLeaveEndTimeFromDays(applyForm.startTime, applyForm.days)
  if (endTime) applyForm.endTime = endTime
  applyFormRef.value?.clearValidate(['days', 'endTime'])
}

function syncDaysFromPeriod() {
  const days = calculateLeaveDaysFromRange(applyForm.startTime, applyForm.endTime)
  if (days === null) {
    applyFormRef.value?.validateField('endTime').catch(() => undefined)
    return
  }
  applyForm.days = days
  applyFormRef.value?.clearValidate(['days', 'endTime'])
}

async function submitLeave() {
  if (!canStartWorkflow.value) {
    ElMessage.warning('没有发起流程权限')
    return
  }
  const valid = await applyFormRef.value?.validate().catch(() => false)
  if (!valid) return
  submittingApply.value = true
  try {
    await applyLeaveRequest({ ...applyForm })
    ElMessage.success('请假申请已提交')
    await router.push(workflowTaskCenterPath('started'))
  } finally {
    submittingApply.value = false
  }
}

function goMyStarted() {
  void router.push(workflowTaskCenterPath('started'))
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
.leave-page {
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

.leave-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  padding: 4px 2px 0;
}

.leave-head > div:first-child {
  min-width: 0;
}

.leave-head h1,
.section-head h2 {
  margin: 0;
  color: #172033;
}

.leave-head p,
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
  border-top: 1px solid #e2e8f0;
  border-bottom: 1px solid #e2e8f0;
  padding: 22px 0 24px;
  background: #f8fafc;
}

.leave-apply-form {
  min-width: 0;
}

.paper-apply-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.paper-apply-form :deep(.el-textarea__inner) {
  min-height: 108px;
}

:deep(.paper-select .el-select__wrapper),
:deep(.paper-line-control .el-input__wrapper),
:deep(.paper-day-control .el-input__wrapper) {
  min-height: 30px;
  border-radius: 0 !important;
  border-bottom: 1px solid rgba(96, 65, 31, 0.48);
  padding: 0 2px;
  background: transparent !important;
  --el-input-border-color: transparent;
  --el-input-hover-border-color: transparent;
  --el-input-focus-border-color: transparent;
}

:deep(.paper-select .el-select__wrapper) {
  padding-inline: 4px;
}

:deep(.paper-select .el-select__placeholder),
:deep(.paper-line-control .el-input__inner),
:deep(.paper-day-control .el-input__inner) {
  color: #24160d;
  font-size: 14px;
  font-weight: 800;
}

.paper-line-control {
  width: 100%;
}

:deep(.paper-line-control .el-input__prefix),
:deep(.paper-line-control .el-input__suffix) {
  display: none;
}

:deep(.paper-line-control .el-input__inner) {
  text-align: center;
  font-variant-numeric: tabular-nums;
}

.paper-day-control {
  width: 48px;
}

:deep(.paper-day-control .el-input__inner) {
  text-align: center;
}

:deep(.paper-reason-input .el-textarea__inner) {
  border-radius: 0;
  border-color: rgba(139, 111, 84, 0.42);
  background-color: #fffaf0;
  color: #24160d;
  font-weight: 700;
  line-height: 32px;
  --el-input-border-color: rgba(139, 111, 84, 0.42);
  --el-input-hover-border-color: #8b6f54;
  --el-input-focus-border-color: #9b1c1f;
}

.paper-apply-form :deep(.el-form-item.is-error .paper-reason-input .el-textarea__inner) {
  border-color: rgba(155, 28, 31, 0.5);
}

.leave-period-field {
  display: grid;
  gap: 10px;
  margin-top: 6px;
}

.leave-period-line {
  display: grid;
  min-width: 0;
  width: 100%;
  max-width: 100%;
  grid-template-columns: auto minmax(118px, 1fr) auto minmax(118px, 1fr) auto 48px auto;
  align-items: center;
  gap: 6px;
  color: #24160d;
  font-size: 14px;
  font-weight: 800;
  line-height: 30px;
}

.leave-period-line > span {
  white-space: nowrap;
}

.leave-period-date,
.leave-period-days {
  flex: 0 0 auto;
}

.duration-shortcuts,
.reason-shortcuts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.duration-shortcuts button,
.reason-shortcuts button {
  border: 1px solid #b7c2d3;
  border-radius: 2px;
  padding: 5px 10px;
  background: #fffaf0;
  color: #60411f;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.2;
  cursor: pointer;
}

.duration-shortcuts button:hover,
.reason-shortcuts button:hover,
.duration-shortcuts button.is-active {
  border-color: #9b1c1f;
  background: rgba(155, 28, 31, 0.07);
  color: #7a1b1d;
}

.reason-shortcuts {
  margin-top: 9px;
}

.paper-field-help.is-warning {
  color: #b45309;
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
  .leave-head,
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

  .paper-line-control {
    width: 100%;
  }

  .leave-period-line {
    width: 100%;
    grid-template-columns: auto minmax(0, 1fr);
  }

  .leave-period-date {
    flex: 1 1 210px;
  }
}
</style>

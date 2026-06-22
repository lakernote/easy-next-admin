<template>
  <section class="request-page">
    <div class="request-head">
      <div>
        <h1>报修申请</h1>
        <p>填写报修对象、位置、故障描述和现场图片，提交后由运维受理并留痕。</p>
      </div>
      <div class="head-actions">
        <el-button :icon="Finished" @click="goMyStarted">我的流程</el-button>
      </div>
    </div>

    <section class="surface route-summary">
      <div class="section-head">
        <div>
          <h2>报修流程</h2>
          <p>员工提交后由运维受理，处理结果进入流程历史并抄送审计备案。</p>
        </div>
      </div>
      <div class="approval-rules" aria-label="报修审批规则">
        <div class="approval-rule">
          <span>1</span>
          <strong>填写报修</strong>
        </div>
        <div class="approval-rule">
          <span>2</span>
          <strong>运维受理</strong>
        </div>
        <div class="approval-rule">
          <span>3</span>
          <strong>结果归档</strong>
        </div>
        <div class="approval-rule is-branch">
          <span>4</span>
          <strong>审计备案</strong>
        </div>
      </div>
    </section>

    <section class="paper-form-area">
      <el-form ref="applyFormRef" :model="applyForm" :rules="applyRules" label-position="top" class="apply-form paper-apply-form">
        <WorkflowApplicationPaper
          :title="repairApplyPaperTitle"
          :meta="applyPaperMeta"
          :fields="repairApplyPaperFields"
          :approvals="repairApplyPaperApprovals"
        >
          <template #field-repairType>
            <el-form-item prop="repairType" class="paper-form-item">
              <el-select v-model="applyForm.repairType" class="full-width" placeholder="请选择报修类型">
                <el-option label="办公设备" value="DEVICE" />
                <el-option label="网络故障" value="NETWORK" />
                <el-option label="软件系统" value="SOFTWARE" />
                <el-option label="办公设施" value="FACILITY" />
              </el-select>
            </el-form-item>
          </template>
          <template #field-urgency>
            <el-form-item prop="urgency" class="paper-form-item">
              <el-segmented v-model="applyForm.urgency" :options="urgencyOptions" />
            </el-form-item>
          </template>
          <template #field-assetName>
            <el-form-item prop="assetName" class="paper-form-item">
              <el-input v-model="applyForm.assetName" maxlength="120" placeholder="例如：会议室投影仪、工位网络、门禁设备" />
            </el-form-item>
          </template>
          <template #field-faultTime>
            <el-form-item prop="faultTime" class="paper-form-item">
              <el-date-picker v-model="applyForm.faultTime" value-format="YYYY-MM-DD HH:mm:ss" type="datetime" class="full-width" />
            </el-form-item>
          </template>
          <template #field-location>
            <el-form-item prop="location" class="paper-form-item">
              <el-input v-model="applyForm.location" maxlength="120" placeholder="例如：深圳总部 12F 会议室" />
            </el-form-item>
          </template>
          <template #field-description>
            <el-form-item prop="description" class="paper-form-item">
              <el-input v-model="applyForm.description" type="textarea" :rows="4" maxlength="500" placeholder="请说明故障现象、影响范围和已尝试的处理方式" />
            </el-form-item>
          </template>
          <template #field-attachments>
            <el-form-item class="paper-form-item">
              <el-upload
                class="repair-image-upload"
                drag
                multiple
                :limit="3"
                :auto-upload="false"
                :accept="repairImageAccept"
                :disabled="uploadingAttachment || submittingApply"
                :file-list="repairUploadFiles"
                :on-change="handleRepairImageChange"
                :on-remove="handleRepairImageRemove"
                :on-exceed="handleRepairImageExceed"
              >
                <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
                <div class="el-upload__text">拖拽现场图片到此处或 <em>点击选择</em></div>
                <template #tip>
                  <div class="repair-upload-tip">最多 3 张，支持 JPG / PNG / WEBP，单张不超过 5MB。</div>
                </template>
              </el-upload>
              <div v-if="applyForm.attachments.length" class="repair-attachments">
                <div v-for="item in applyForm.attachments" :key="item.fileId" class="repair-attachment">
                  <el-icon><Picture /></el-icon>
                  <span>{{ item.fileName }}</span>
                  <small>{{ formatFileSize(item.fileSize) }}</small>
                </div>
              </div>
            </el-form-item>
          </template>
        </WorkflowApplicationPaper>
      </el-form>

      <div class="form-submit-bar">
        <span>提交后进入我的流程，可在“我发起的”查看进度。</span>
        <div>
          <el-button :icon="RefreshRight" @click="resetApplyForm">重置</el-button>
          <el-button v-permission:disable="PermissionCodes.workflow.instanceStart" type="primary" :loading="submittingApply" @click="submitRepair">提交申请</el-button>
        </div>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { Finished, Picture, RefreshRight, UploadFilled } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules, type UploadFile, type UploadFiles, type UploadUserFile } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { PermissionCodes } from '@/permissions/codes'
import { applyRepairRequest, uploadRepairAttachment } from '@/features/workflow/api'
import { repairImageAccept, validateRepairImageFile } from '@/features/workflow/repairAttachments'
import { workflowTaskCenterPath } from '@/features/workflow/taskCenterTabs'
import { useAuthStore } from '@/stores/auth'
import type { RepairAttachment } from '@/features/workflow/types'
import WorkflowApplicationPaper from './components/WorkflowApplicationPaper.vue'

const auth = useAuthStore()
const router = useRouter()
const submittingApply = ref(false)
const uploadingAttachment = ref(false)
const applyFormRef = ref<FormInstance>()
const repairUploadFiles = ref<UploadUserFile[]>([])
const canStartWorkflow = computed(() => auth.hasAnyPermission([PermissionCodes.workflow.instanceStart]))
const urgencyOptions = [
  { label: '普通', value: 'NORMAL' },
  { label: '较急', value: 'HIGH' },
  { label: '紧急', value: 'URGENT' }
]
const applicantName = computed(() => currentApplicantName())
const applyDateText = computed(() => formatDate(new Date()))
const repairApplyPaperTitle = computed(() => '报修申请单')
const applyPaperMeta = computed(() => [
  { key: 'requestNo', label: '申请单号', value: '提交后生成' },
  { key: 'applicant', label: '申请人', value: applicantName.value },
  { key: 'date', label: '申请日期', value: applyDateText.value },
  { key: 'status', label: '流程状态', value: '待提交', tone: 'draft' as const }
])
const repairApplyPaperFields = [
  { key: 'repairType', label: '报修类型' },
  { key: 'urgency', label: '紧急程度' },
  { key: 'assetName', label: '报修对象' },
  { key: 'faultTime', label: '故障时间' },
  { key: 'location', label: '所在位置', wide: true },
  { key: 'description', label: '问题描述', wide: true },
  { key: 'attachments', label: '故障图片', wide: true }
]
const repairApplyPaperApprovals = computed(() => [
  { id: 'start', nodeName: '流程开始', actor: applicantName.value, statusText: '待提交', timeText: '-', tone: 'current' as const },
  { id: 'ops', nodeName: '运维受理', actor: '提交后自动分配', statusText: '未执行', timeText: '-', tone: 'todo' as const },
  { id: 'audit', nodeName: '审计备案', actor: '系统抄送', statusText: '未执行', timeText: '-', tone: 'todo' as const }
])

const applyForm = reactive({
  repairType: 'DEVICE',
  assetName: '',
  urgency: 'NORMAL',
  faultTime: '',
  location: '',
  description: '',
  attachments: [] as RepairAttachment[]
})

const applyRules: FormRules<typeof applyForm> = {
  repairType: [{ required: true, message: '请选择报修类型', trigger: 'change' }],
  assetName: [
    { required: true, message: '请填写报修对象', trigger: 'blur' },
    { min: 2, message: '报修对象至少 2 个字', trigger: 'blur' }
  ],
  urgency: [{ required: true, message: '请选择紧急程度', trigger: 'change' }],
  faultTime: [{ required: true, message: '请选择故障时间', trigger: 'change' }],
  location: [
    { required: true, message: '请填写所在位置', trigger: 'blur' },
    { min: 2, message: '所在位置至少 2 个字', trigger: 'blur' }
  ],
  description: [
    { required: true, message: '请填写问题描述', trigger: 'blur' },
    { min: 2, message: '问题描述至少 2 个字', trigger: 'blur' }
  ]
}

onMounted(resetApplyForm)

function resetApplyForm() {
  applyForm.repairType = 'DEVICE'
  applyForm.assetName = ''
  applyForm.urgency = 'NORMAL'
  applyForm.faultTime = localDateTime(new Date())
  applyForm.location = ''
  applyForm.description = ''
  applyForm.attachments = []
  repairUploadFiles.value = []
  applyFormRef.value?.clearValidate()
}

async function submitRepair() {
  if (!canStartWorkflow.value) {
    ElMessage.warning('没有发起流程权限')
    return
  }
  const valid = await applyFormRef.value?.validate().catch(() => false)
  if (!valid) return
  submittingApply.value = true
  try {
    await applyRepairRequest({ ...applyForm })
    ElMessage.success('报修申请已提交')
    await router.push(workflowTaskCenterPath('started'))
  } finally {
    submittingApply.value = false
  }
}

async function handleRepairImageChange(uploadFile: UploadFile) {
  const rawFile = uploadFile.raw
  if (!rawFile) {
    repairUploadFiles.value = attachmentUploadFiles()
    return
  }
  const error = validateRepairImageFile(rawFile)
  if (error) {
    ElMessage.warning(error)
    repairUploadFiles.value = attachmentUploadFiles()
    return
  }
  if (applyForm.attachments.length >= 3) {
    ElMessage.warning('最多上传 3 张故障图片')
    repairUploadFiles.value = attachmentUploadFiles()
    return
  }
  uploadingAttachment.value = true
  try {
    const attachment = await uploadRepairAttachment(rawFile)
    applyForm.attachments = [...applyForm.attachments, attachment]
    repairUploadFiles.value = attachmentUploadFiles()
    ElMessage.success('故障图片已上传')
  } catch (error) {
    void error
    repairUploadFiles.value = attachmentUploadFiles()
  } finally {
    uploadingAttachment.value = false
  }
}

function handleRepairImageRemove(uploadFile: UploadFile) {
  const fileId = String(uploadFile.uid)
  applyForm.attachments = applyForm.attachments.filter((item) => String(item.fileId) !== fileId)
  repairUploadFiles.value = attachmentUploadFiles()
}

function handleRepairImageExceed(files: File[], uploadFiles: UploadFiles) {
  void files
  void uploadFiles
  ElMessage.warning('最多上传 3 张故障图片')
}

function goMyStarted() {
  void router.push(workflowTaskCenterPath('started'))
}

function localDateTime(date: Date) {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:00`
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

function attachmentUploadFiles(): UploadUserFile[] {
  return applyForm.attachments.map((item) => ({
    uid: Number(item.fileId),
    name: item.fileName,
    status: 'success'
  }))
}

function formatFileSize(value?: number) {
  if (!value || value <= 0) return '-'
  if (value < 1024 * 1024) return `${Math.ceil(value / 1024)} KB`
  return `${(value / 1024 / 1024).toFixed(1)} MB`
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

.paper-apply-form :deep(.el-segmented) {
  width: 100%;
  border-radius: 2px;
  background: rgba(139, 111, 84, 0.12);
  --el-segmented-item-selected-bg-color: #fffaf0;
  --el-segmented-item-selected-color: #7a1b1d;
  --el-segmented-item-hover-color: #7a1b1d;
}

.paper-apply-form :deep(.el-segmented__item) {
  flex: 1 1 0;
  color: #60411f;
  font-weight: 800;
}

.paper-apply-form :deep(.el-textarea__inner) {
  min-height: 108px;
}

.repair-image-upload {
  width: 100%;
}

.repair-image-upload :deep(.el-upload) {
  width: 100%;
}

.repair-image-upload :deep(.el-upload-dragger) {
  width: 100%;
  border-color: #c7ad91;
  border-radius: 2px;
  background: #fffaf0;
}

.repair-image-upload :deep(.el-upload-dragger:hover) {
  border-color: #9b1c1f;
}

.repair-image-upload :deep(.el-upload__text) {
  color: #60411f;
  font-weight: 700;
}

.repair-image-upload :deep(.el-upload__text em) {
  color: #7a1b1d;
}

.repair-upload-tip {
  margin-top: 6px;
  color: #6a4526;
  font-size: 12px;
  line-height: 1.5;
}

.repair-attachments {
  display: grid;
  width: 100%;
  gap: 8px;
  margin-top: 10px;
}

.repair-attachment {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  border: 1px solid #c7ad91;
  border-radius: 2px;
  padding: 8px 10px;
  background: #fffaf0;
  color: #24160d;
}

.repair-attachment span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.repair-attachment small {
  color: #6a4526;
  font-variant-numeric: tabular-nums;
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

<template>
  <section class="resource-page profile-page">
    <div class="resource-hero">
      <div>
        <h1>个人中心</h1>
        <p>维护个人资料、头像、修改密码、查看登录历史和管理本人会话。</p>
      </div>
    </div>

    <div class="profile-layout">
      <section class="surface profile-sidebar-card">
        <div class="profile-identity">
          <button
            class="profile-avatar-upload"
            type="button"
            :aria-label="profileForm.avatar ? '更换头像' : '上传头像'"
            :disabled="avatarUploading"
            @click="openAvatarFilePicker"
          >
            <el-avatar class="profile-avatar" :size="112" :src="profileAvatarUrl || undefined">
              <span class="profile-avatar-initial">{{ profileInitial }}</span>
            </el-avatar>
            <span class="profile-avatar-overlay">
              <el-icon><Upload /></el-icon>
              <span>{{ profileForm.avatar ? '更换头像' : '上传头像' }}</span>
            </span>
          </button>
          <strong>{{ auth.displayName }}</strong>
          <span>{{ auth.user?.deptName || '未分配部门' }} / {{ auth.user?.positionName || '未配置职务' }}</span>
          <el-button v-if="profileForm.avatar" class="profile-avatar-remove" size="small" link type="danger" @click="clearAvatar">移除当前头像</el-button>
          <input ref="avatarFileInputRef" class="avatar-file-input" type="file" accept="image/jpeg,image/png,image/webp" @change="handleAvatarFileChange" />
        </div>

        <dl class="profile-info-list">
          <div v-for="item in profileInfoRows" :key="item.label">
            <dt>{{ item.label }}</dt>
            <dd>{{ item.value }}</dd>
          </div>
        </dl>
      </section>

      <section class="surface profile-workspace-card">
        <div class="section-head">
          <div>
            <h2>基本信息</h2>
            <p>资料会用于系统展示、消息通知和审计留痕；必填项保存前会进行校验。</p>
          </div>
        </div>
        <el-tabs v-model="activeProfileTab" class="profile-tabs">
          <el-tab-pane label="基本资料" name="profile">
            <el-form ref="profileFormRef" :model="profileForm" :rules="profileFormRules" label-position="top" class="drawer-form profile-form">
              <div class="form-grid">
                <el-form-item label="用户名">
                  <el-input :model-value="auth.user?.userName" disabled />
                </el-form-item>
                <el-form-item label="姓名/昵称" prop="nickName">
                  <el-input v-model.trim="profileForm.nickName" placeholder="请输入姓名或系统展示名称" />
                </el-form-item>
              </div>
              <div class="form-grid">
                <el-form-item label="手机号" prop="phone">
                  <el-input v-model.trim="profileForm.phone" placeholder="请输入手机号" />
                </el-form-item>
                <el-form-item label="邮箱" prop="email">
                  <el-input v-model.trim="profileForm.email" placeholder="name@company.com" />
                </el-form-item>
              </div>
              <el-form-item label="部门 / 职务">
                <el-input :model-value="`${auth.user?.deptName || '-'} / ${auth.user?.positionName || '-'}`" disabled />
              </el-form-item>
              <el-button type="primary" :loading="savingProfile" @click="handleSaveProfile">保存资料</el-button>
            </el-form>
          </el-tab-pane>
          <el-tab-pane label="修改密码" name="password">
            <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordFormRules" label-position="top" class="drawer-form password-form">
              <el-form-item label="当前密码" prop="oldPassword">
                <el-input v-model="passwordForm.oldPassword" type="password" show-password autocomplete="current-password" />
              </el-form-item>
              <el-form-item label="新密码" prop="newPassword">
                <el-input v-model="passwordForm.newPassword" type="password" show-password autocomplete="new-password" />
              </el-form-item>
              <el-form-item label="确认新密码" prop="confirmPassword">
                <el-input v-model="passwordForm.confirmPassword" type="password" show-password autocomplete="new-password" />
              </el-form-item>
              <el-button type="primary" :loading="changingPassword" @click="handleChangePassword">更新密码</el-button>
            </el-form>
          </el-tab-pane>
          <el-tab-pane label="登录历史" name="history">
            <div class="profile-tab-panel">
              <div class="table-control-row">
                <el-form :inline="true" class="filter-bar profile-filter-bar">
                  <el-form-item label="关键词">
                    <el-input v-model="historyQuery.keyword" placeholder="IP / 客户端 / User-Agent" clearable @keyup.enter="searchHistory" />
                  </el-form-item>
                  <el-form-item>
                    <el-button type="primary" plain :icon="Search" @click="searchHistory">查询</el-button>
                    <el-button @click="resetHistory">重置</el-button>
                  </el-form-item>
                </el-form>
                <TableToolbar v-model:columns="historyColumns" class="table-toolbar-inline" />
              </div>
              <el-table v-loading="historyLoading" :data="loginHistory" class="admin-table" empty-text="暂无登录历史">
                <el-table-column v-if="visibleHistoryColumns.loginTime" prop="loginTime" label="登录时间" width="170" />
                <el-table-column v-if="visibleHistoryColumns.ip" prop="ip" label="IP" width="140" />
                <el-table-column v-if="visibleHistoryColumns.clientType" prop="clientType" label="客户端" width="120" />
                <el-table-column v-if="visibleHistoryColumns.result" label="结果" width="90">
                  <template #default="{ row }">
                    <el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'" effect="plain">
                      {{ row.result === 'SUCCESS' ? '成功' : '失败' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column v-if="visibleHistoryColumns.failReason" prop="failReason" label="失败原因" min-width="160" show-overflow-tooltip>
                  <template #default="{ row }">{{ row.failReason || '-' }}</template>
                </el-table-column>
                <el-table-column v-if="visibleHistoryColumns.userAgent" prop="userAgent" label="User-Agent" min-width="220" show-overflow-tooltip />
              </el-table>
              <div class="table-footer is-split profile-table-footer">
                <span>共 {{ historyTotal }} 条</span>
                <el-pagination
                  v-model:current-page="historyQuery.page"
                  v-model:page-size="historyQuery.limit"
                  background
                  layout="sizes, prev, pager, next"
                  :page-sizes="[10, 20, 50]"
                  :total="historyTotal"
                  @current-change="loadLoginHistory"
                  @size-change="handleHistorySizeChange"
                />
              </div>
            </div>
          </el-tab-pane>
          <el-tab-pane label="在线会话" name="sessions">
            <div class="profile-tab-panel">
              <el-table v-loading="sessionLoading" :data="sessions" class="admin-table" empty-text="暂无在线会话">
                <el-table-column label="客户端" min-width="180">
                  <template #default="{ row }">
                    <strong>{{ row.clientType || 'Web' }}</strong>
                    <small class="muted-inline">{{ row.current ? '当前会话' : row.userAgent || '-' }}</small>
                  </template>
                </el-table-column>
                <el-table-column prop="ip" label="IP" width="140" />
                <el-table-column prop="loginTime" label="登录时间" width="170" />
                <el-table-column prop="lastAccessTime" label="最后访问" width="170" />
                <el-table-column label="操作" width="120" fixed="right">
                  <template #default="{ row }">
                    <el-button
                      text
                      type="danger"
                      :disabled="row.current"
                      :loading="revokingSessionId === row.id"
                      @click="handleRevokeSession(row)"
                    >
                      下线
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </el-tab-pane>
        </el-tabs>
      </section>
    </div>

    <el-dialog v-model="avatarCropVisible" title="裁剪头像" width="min(560px, 92vw)" append-to-body destroy-on-close @closed="resetAvatarCrop">
      <div class="avatar-crop-workbench">
        <div
          class="avatar-crop-frame"
          :class="{ 'is-dragging': avatarDrag.active }"
          aria-label="头像裁剪预览"
          @pointerdown="startAvatarDrag"
          @pointermove="moveAvatarDrag"
          @pointerup="finishAvatarDrag"
          @pointercancel="finishAvatarDrag"
        >
          <img v-if="avatarCrop.imageUrl" :src="avatarCrop.imageUrl" :style="avatarCropImageStyle" alt="头像裁剪预览" draggable="false" />
          <span class="avatar-crop-mask"></span>
        </div>
        <div class="avatar-crop-controls">
          <el-form label-position="top">
            <el-form-item label="缩放">
              <el-slider v-model="avatarCrop.zoom" :min="1" :max="3" :step="0.01" />
            </el-form-item>
            <el-form-item label="水平位置">
              <el-slider v-model="avatarCrop.offsetX" :min="-120" :max="120" :step="1" />
            </el-form-item>
            <el-form-item label="垂直位置">
              <el-slider v-model="avatarCrop.offsetY" :min="-120" :max="120" :step="1" />
            </el-form-item>
          </el-form>
        </div>
      </div>
      <template #footer>
        <el-button @click="avatarCropVisible = false">取消</el-button>
        <el-button type="primary" :loading="avatarUploading" @click="confirmAvatarCrop">上传并使用</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Search, Upload } from '@element-plus/icons-vue'
import TableToolbar from '@/components/table/TableToolbar.vue'
import { createTableColumnState, visibleColumnMap } from '@/components/table/tableColumns'
import { useAuthStore } from '@/stores/auth'
import { clampAvatarOffset, moveAvatarCrop, profileInitial as resolveProfileInitial } from '@/features/profile/avatar'
import {
  changePassword,
  listProfileSessions,
  pageProfileLoginHistory,
  revokeProfileSession,
  updateProfile,
  uploadProfileAvatar
} from '@/features/profile/api'
import type { ProfileLoginHistory, ProfileSession } from '@/features/profile/types'

const auth = useAuthStore()
const savingProfile = ref(false)
const changingPassword = ref(false)
const avatarUploading = ref(false)
const historyLoading = ref(false)
const sessionLoading = ref(false)
const revokingSessionId = ref<ProfileSession['id']>()
const loginHistory = ref<ProfileLoginHistory[]>([])
const historyTotal = ref(0)
const sessions = ref<ProfileSession[]>([])
const avatarFileInputRef = ref<HTMLInputElement>()
const avatarCropVisible = ref(false)
const profileFormRef = ref<FormInstance>()
const passwordFormRef = ref<FormInstance>()
const activeProfileTab = ref('profile')
const historyColumns = ref(createTableColumnState([
  { key: 'loginTime', label: '登录时间', required: true },
  { key: 'ip', label: 'IP' },
  { key: 'clientType', label: '客户端' },
  { key: 'result', label: '结果' },
  { key: 'failReason', label: '失败原因' },
  { key: 'userAgent', label: 'User-Agent' }
]))

const profileForm = reactive({
  nickName: '',
  phone: '',
  email: '',
  avatar: ''
})
const avatarCrop = reactive({
  imageUrl: '',
  fileName: 'avatar.png',
  naturalWidth: 0,
  naturalHeight: 0,
  zoom: 1,
  offsetX: 0,
  offsetY: 0
})
const avatarDrag = reactive({
  active: false,
  pointerId: 0,
  startX: 0,
  startY: 0,
  startOffsetX: 0,
  startOffsetY: 0
})
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})
const profileFormRules: FormRules = {
  nickName: [
    { required: true, message: '请输入姓名/昵称', trigger: 'blur' },
    { min: 2, max: 30, message: '姓名/昵称长度应为 2-30 个字符', trigger: 'blur' }
  ],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入有效的手机号', trigger: 'blur' }
  ],
  email: [{ type: 'email', message: '请输入有效的邮箱', trigger: 'blur' }]
}
const passwordFormRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { validator: validatePasswordStrength, trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}
const historyQuery = reactive({
  page: 1,
  limit: 10,
  keyword: ''
})

const loading = computed(() => historyLoading.value || sessionLoading.value)
const visibleHistoryColumns = computed(() => visibleColumnMap(historyColumns.value))
const profileInitial = computed(() => resolveProfileInitial(auth.displayName))
const profileAvatarUrl = computed(() => profileForm.avatar || auth.user?.avatar || '')
const profileInfoRows = computed(() => [
  { label: '用户名', value: auth.user?.userName || '-' },
  { label: '姓名', value: profileForm.nickName || '-' },
  { label: '手机号码', value: profileForm.phone || '-' },
  { label: '用户邮箱', value: profileForm.email || '-' },
  { label: '所属机构', value: auth.user?.deptName || '-' },
  { label: '所属岗位', value: auth.user?.positionName || '-' }
])
const avatarCropSize = 260
const avatarOutputSize = 512
const avatarDragLimit = 120
const avatarCropImageStyle = computed(() => {
  const base = avatarCropBaseSize()
  return {
    width: `${base.width * avatarCrop.zoom}px`,
    height: `${base.height * avatarCrop.zoom}px`,
    left: `calc(50% + ${avatarCrop.offsetX}px)`,
    top: `calc(50% + ${avatarCrop.offsetY}px)`
  }
})

watch(
  () => auth.user,
  (user) => {
    profileForm.nickName = user?.nickName || ''
    profileForm.phone = user?.phone || ''
    profileForm.email = user?.email || ''
    profileForm.avatar = user?.avatar || ''
  },
  { immediate: true }
)

onMounted(loadSecurityData)

async function loadSecurityData() {
  await Promise.all([loadLoginHistory(), loadSessions()])
}

async function handleSaveProfile() {
  const valid = await profileFormRef.value?.validate().catch(() => false)
  if (!valid) return
  savingProfile.value = true
  try {
    await updateProfile({ ...profileForm, realName: profileForm.nickName })
    await auth.loadProfile()
    ElMessage.success('个人资料已保存')
  } finally {
    savingProfile.value = false
  }
}

function openAvatarFilePicker() {
  avatarFileInputRef.value?.click()
}

async function handleAvatarFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || !validateAvatarFile(file)) {
    return
  }
  const imageUrl = URL.createObjectURL(file)
  try {
    const imageSize = await loadAvatarImageSize(imageUrl)
    resetAvatarCrop()
    avatarCrop.imageUrl = imageUrl
    avatarCrop.fileName = file.name
    avatarCrop.naturalWidth = imageSize.width
    avatarCrop.naturalHeight = imageSize.height
    avatarCrop.zoom = 1
    avatarCrop.offsetX = 0
    avatarCrop.offsetY = 0
    avatarCropVisible.value = true
  } catch {
    URL.revokeObjectURL(imageUrl)
    ElMessage.error('无法读取头像图片')
  }
}

function startAvatarDrag(event: PointerEvent) {
  if (!avatarCrop.imageUrl) return
  avatarDrag.active = true
  avatarDrag.pointerId = event.pointerId
  avatarDrag.startX = event.clientX
  avatarDrag.startY = event.clientY
  avatarDrag.startOffsetX = avatarCrop.offsetX
  avatarDrag.startOffsetY = avatarCrop.offsetY
  ;(event.currentTarget as HTMLElement).setPointerCapture(event.pointerId)
  event.preventDefault()
}

function moveAvatarDrag(event: PointerEvent) {
  if (!avatarDrag.active || event.pointerId !== avatarDrag.pointerId) return
  const moved = moveAvatarCrop(
    { offsetX: avatarDrag.startOffsetX, offsetY: avatarDrag.startOffsetY },
    {
      deltaX: event.clientX - avatarDrag.startX,
      deltaY: event.clientY - avatarDrag.startY,
      limit: avatarDragLimit
    }
  )
  avatarCrop.offsetX = moved.offsetX
  avatarCrop.offsetY = moved.offsetY
}

function finishAvatarDrag(event: PointerEvent) {
  if (!avatarDrag.active || event.pointerId !== avatarDrag.pointerId) return
  avatarDrag.active = false
  ;(event.currentTarget as HTMLElement).releasePointerCapture(event.pointerId)
}

function validateAvatarFile(file: File) {
  const allowedTypes = ['image/jpeg', 'image/png', 'image/webp']
  if (!allowedTypes.includes(file.type)) {
    ElMessage.warning('头像仅支持 JPG、PNG 或 WEBP 图片')
    return false
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('头像图片不能超过 5MB')
    return false
  }
  return true
}

function loadAvatarImageSize(url: string) {
  return new Promise<{ width: number; height: number }>((resolve, reject) => {
    const image = new Image()
    image.onload = () => resolve({ width: image.naturalWidth, height: image.naturalHeight })
    image.onerror = () => reject(new Error('image load failed'))
    image.src = url
  })
}

function avatarCropBaseSize() {
  if (!avatarCrop.naturalWidth || !avatarCrop.naturalHeight) {
    return { width: avatarCropSize, height: avatarCropSize }
  }
  const scale = Math.max(avatarCropSize / avatarCrop.naturalWidth, avatarCropSize / avatarCrop.naturalHeight)
  return {
    width: avatarCrop.naturalWidth * scale,
    height: avatarCrop.naturalHeight * scale
  }
}

async function confirmAvatarCrop() {
  if (!avatarCrop.imageUrl) return
  avatarUploading.value = true
  try {
    const blob = await renderCroppedAvatar()
    const avatarFile = new File([blob], 'avatar.png', { type: 'image/png' })
    const user = await uploadProfileAvatar(avatarFile)
    profileForm.avatar = user.avatar || ''
    await auth.loadProfile()
    avatarCropVisible.value = false
    ElMessage.success('头像已更新')
  } finally {
    avatarUploading.value = false
  }
}

function renderCroppedAvatar() {
  return new Promise<Blob>((resolve, reject) => {
    const image = new Image()
    image.onload = () => {
      const canvas = document.createElement('canvas')
      canvas.width = avatarOutputSize
      canvas.height = avatarOutputSize
      const context = canvas.getContext('2d')
      if (!context) {
        reject(new Error('canvas context unavailable'))
        return
      }
      const base = avatarCropBaseSize()
      const outputScale = avatarOutputSize / avatarCropSize
      const drawWidth = base.width * avatarCrop.zoom * outputScale
      const drawHeight = base.height * avatarCrop.zoom * outputScale
      const drawLeft = (avatarCropSize / 2 + avatarCrop.offsetX) * outputScale - drawWidth / 2
      const drawTop = (avatarCropSize / 2 + avatarCrop.offsetY) * outputScale - drawHeight / 2
      context.clearRect(0, 0, avatarOutputSize, avatarOutputSize)
      context.imageSmoothingEnabled = true
      context.imageSmoothingQuality = 'high'
      context.drawImage(image, drawLeft, drawTop, drawWidth, drawHeight)
      canvas.toBlob((blob) => {
        if (blob) {
          resolve(blob)
        } else {
          reject(new Error('avatar crop failed'))
        }
      }, 'image/png')
    }
    image.onerror = () => reject(new Error('image load failed'))
    image.src = avatarCrop.imageUrl
  })
}

function resetAvatarCrop() {
  if (avatarCrop.imageUrl) {
    URL.revokeObjectURL(avatarCrop.imageUrl)
  }
  avatarCrop.imageUrl = ''
  avatarCrop.fileName = 'avatar.png'
  avatarCrop.naturalWidth = 0
  avatarCrop.naturalHeight = 0
  avatarCrop.zoom = 1
  avatarCrop.offsetX = clampAvatarOffset(0, avatarDragLimit)
  avatarCrop.offsetY = clampAvatarOffset(0, avatarDragLimit)
  avatarDrag.active = false
}

async function clearAvatar() {
  profileForm.avatar = ''
  await handleSaveProfile()
}

async function handleChangePassword() {
  const valid = await passwordFormRef.value?.validate().catch(() => false)
  if (!valid) return
  changingPassword.value = true
  try {
    await changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
      confirmPassword: passwordForm.confirmPassword
    })
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
    passwordFormRef.value?.clearValidate()
    ElMessage.success('密码已更新')
  } finally {
    changingPassword.value = false
  }
}

function validatePasswordStrength(_: unknown, value: string, callback: (error?: Error) => void) {
  if (!value || value.length < 8) {
    callback(new Error('新密码至少 8 位'))
    return
  }
  if (!/[A-Za-z]/.test(value) || !/\d/.test(value)) {
    callback(new Error('新密码需同时包含字母和数字'))
    return
  }
  callback()
}

function validateConfirmPassword(_: unknown, value: string, callback: (error?: Error) => void) {
  if (value !== passwordForm.newPassword) {
    callback(new Error('两次输入的新密码不一致'))
    return
  }
  callback()
}

async function loadLoginHistory() {
  historyLoading.value = true
  try {
    const result = await pageProfileLoginHistory(historyQuery)
    loginHistory.value = result.list
    historyTotal.value = result.total
  } finally {
    historyLoading.value = false
  }
}

function searchHistory() {
  historyQuery.page = 1
  loadLoginHistory()
}

function resetHistory() {
  historyQuery.keyword = ''
  searchHistory()
}

function handleHistorySizeChange() {
  historyQuery.page = 1
  loadLoginHistory()
}

async function loadSessions() {
  sessionLoading.value = true
  try {
    sessions.value = await listProfileSessions()
  } finally {
    sessionLoading.value = false
  }
}

async function handleRevokeSession(row: ProfileSession) {
  await ElMessageBox.confirm('确认下线该会话？', '会话下线', { type: 'warning' })
  revokingSessionId.value = row.id
  try {
    await revokeProfileSession(row.id)
    ElMessage.success('会话已下线')
    await loadSessions()
  } finally {
    revokingSessionId.value = undefined
  }
}
</script>

<style scoped>
.profile-page {
  overflow: hidden;
}

.profile-layout {
  flex: 1 1 auto;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(280px, 360px) minmax(0, 1fr);
  gap: 16px;
  align-items: stretch;
}

.profile-sidebar-card,
.profile-workspace-card {
  min-height: 0;
  overflow: hidden;
}

.profile-workspace-card {
  display: flex;
  flex-direction: column;
}

.profile-workspace-card > .section-head {
  flex: 0 0 auto;
  margin-bottom: 12px;
}

.profile-identity {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 10px 0 18px;
  text-align: center;
}

.profile-avatar-upload {
  position: relative;
  width: 112px;
  height: 112px;
  display: inline-grid;
  place-items: center;
  border: 0;
  border-radius: 999px;
  padding: 0;
  background: transparent;
  color: #fff;
  cursor: pointer;
  overflow: hidden;
}

.profile-avatar-upload:disabled {
  cursor: wait;
}

.profile-identity :deep(.profile-avatar) {
  flex: 0 0 auto;
  background: #eff6ff;
  color: var(--ea-primary);
  font-weight: 800;
  font-size: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

.profile-avatar-overlay {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 6px;
  background: rgba(15, 23, 42, 0.58);
  font-size: 13px;
  font-weight: 700;
  opacity: 0;
  transition: opacity 0.18s ease;
}

.profile-avatar-overlay .el-icon {
  font-size: 18px;
}

.profile-avatar-upload:hover .profile-avatar-overlay,
.profile-avatar-upload:focus-visible .profile-avatar-overlay {
  opacity: 1;
}

.profile-avatar-initial {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  line-height: 1;
}

.profile-identity strong,
.profile-identity > span {
  display: block;
  max-width: 100%;
  overflow-wrap: anywhere;
}

.profile-identity strong {
  color: var(--ea-text);
  font-size: 20px;
}

.profile-identity > span {
  color: var(--ea-muted);
  line-height: 1.5;
}

.profile-avatar-remove {
  min-height: auto;
  padding: 0;
}

.profile-info-list {
  margin: 0;
  border-top: 1px solid var(--ea-border);
}

.profile-info-list div {
  display: grid;
  grid-template-columns: 92px minmax(0, 1fr);
  gap: 12px;
  padding: 13px 0;
  border-bottom: 1px solid var(--ea-border);
}

.profile-info-list dt,
.profile-info-list dd {
  min-width: 0;
  margin: 0;
}

.profile-info-list dt {
  color: var(--ea-muted);
}

.profile-info-list dd {
  color: var(--ea-text);
  text-align: right;
  overflow-wrap: anywhere;
}

.profile-tabs :deep(.el-tabs__header) {
  flex: 0 0 auto;
  margin-bottom: 14px;
}

.profile-tabs {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.profile-tabs :deep(.el-tabs__content) {
  flex: 1 1 auto;
  min-height: 0;
}

.profile-tabs :deep(.el-tab-pane) {
  height: 100%;
  min-height: 0;
}

.profile-form,
.password-form {
  max-width: 840px;
}

.profile-tab-panel {
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--ea-border);
  border-radius: 8px;
  background: #fff;
}

.profile-tab-panel .table-control-row,
.profile-tab-panel .profile-filter-bar,
.profile-tab-panel .profile-table-footer {
  flex: 0 0 auto;
}

.profile-tab-panel .admin-table {
  flex: 0 0 auto;
  min-height: 0;
}

.profile-filter-bar {
  padding: 0;
}

.profile-filter-bar .el-form-item {
  margin-bottom: 10px;
}

.profile-table-footer {
  padding: 10px 14px 12px;
}

.avatar-file-input {
  display: none;
}

.avatar-crop-workbench {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 20px;
  align-items: center;
}

.avatar-crop-frame {
  position: relative;
  width: 260px;
  height: 260px;
  overflow: hidden;
  border: 1px solid var(--ea-border);
  border-radius: 999px;
  background: #f8fafc;
  cursor: grab;
  touch-action: none;
}

.avatar-crop-frame.is-dragging {
  cursor: grabbing;
}

.avatar-crop-frame img {
  position: absolute;
  max-width: none;
  transform: translate(-50%, -50%);
  user-select: none;
  pointer-events: none;
}

.avatar-crop-mask {
  position: absolute;
  inset: 0;
  border: 2px solid rgba(37, 99, 235, 0.38);
  border-radius: inherit;
  pointer-events: none;
}

.avatar-crop-controls {
  min-width: 0;
}

@media (max-width: 1180px) {
  .profile-layout {
    grid-template-columns: 1fr;
    overflow: auto;
  }
}

@media (max-width: 720px) {
  .avatar-crop-workbench {
    grid-template-columns: 1fr;
    justify-items: center;
  }
}
</style>

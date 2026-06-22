<template>
  <main class="login-page">
    <div class="login-background" aria-hidden="true">
      <span class="login-grid"></span>
      <span class="login-ribbon"></span>
      <span class="login-plane is-left"></span>
      <span class="login-plane is-right"></span>
    </div>
    <section class="login-stage" aria-label="EasyNextAdmin 企业开发中枢">
      <div class="login-brand-row">
        <div class="login-brand-mark">
          <img src="/brand/easy-next-admin-mark.svg?v=20260512-hub" alt="" aria-hidden="true" draggable="false" />
        </div>
        <div>
          <strong>EasyNextAdmin</strong>
          <span>企业管理基座</span>
        </div>
      </div>

      <div class="login-hero-copy">
        <span>企业级管理脚手架</span>
        <h1>欢迎登录 EasyNextAdmin</h1>
        <p>登录后按角色加载菜单、按钮权限和数据范围，进入工作台处理用户、流程、审计和监控任务。</p>
      </div>
    </section>

    <section class="login-access" aria-label="登录区域">
      <div class="login-card">
        <div class="login-card-head">
          <span>安全入口</span>
          <h2>账号登录</h2>
          <p>{{ loginAccessText }}</p>
        </div>

        <el-form ref="formRef" :model="form" :rules="rules" class="login-form" label-position="top" @submit.prevent>
          <el-form-item label="用户名" prop="username">
            <el-input
              v-model="form.username"
              size="large"
              autocomplete="username"
              placeholder="请输入用户名"
              :prefix-icon="User"
              @keyup.enter="submit"
            />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              size="large"
              type="password"
              autocomplete="current-password"
              show-password
              placeholder="请输入密码"
              :prefix-icon="Key"
              @keyup.enter="submit"
            />
          </el-form-item>
          <el-form-item v-if="captchaVisible" label="验证码" prop="captchaCode">
            <div class="login-captcha-row">
              <el-input
                v-model="form.captchaCode"
                size="large"
                autocomplete="off"
                placeholder="请输入验证码"
                maxlength="4"
                @keyup.enter="submit"
              />
              <button type="button" class="login-captcha-image" :disabled="captchaLoading" @click="loadCaptcha">
                <img v-if="captchaImage" :src="captchaImage" alt="登录验证码" />
                <span v-else>刷新</span>
              </button>
            </div>
          </el-form-item>
          <div class="login-options">
            <el-checkbox v-model="form.rememberMe">记住我</el-checkbox>
            <span>仅保存登录状态和账号，不保存密码</span>
          </div>
          <el-button type="primary" size="large" class="login-submit" :loading="loading" @click="submit">
            <span>进入工作台</span>
            <el-icon><ArrowRight /></el-icon>
          </el-button>
        </el-form>

        <div v-if="demoAccounts.length" class="login-demo-accounts" aria-label="默认测试账号">
          <div class="login-demo-title">
            <el-icon><Check /></el-icon>
            <span>测试账号</span>
          </div>
          <button
            v-for="account in demoAccounts"
            :key="account.username"
            type="button"
            :class="['login-demo-account', { 'is-active': form.username === account.username }]"
            @click="useDemoAccount(account)"
          >
            <strong>{{ account.roleName }}</strong>
            <span>{{ account.username }} / {{ account.password }}</span>
            <small>{{ account.description }}</small>
          </button>
        </div>
      </div>
    </section>
    <footer class="login-footer">Copyright © 2026 EasyNextAdmin. All Rights Reserved.</footer>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { ArrowRight, Check, Key, User } from '@element-plus/icons-vue'
import { captchaApi, demoAccountsApi } from '@/features/auth/api'
import type { DemoAccount } from '@/features/auth/types'
import { useAuthStore } from '@/stores/auth'
import { firstAuthorizedPath, syncDynamicRoutes } from '@/router/dynamicRoutes'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const LOGIN_PREFERENCE_KEY = 'easy-next-admin-web-login-preference'
const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({
  username: '',
  password: '',
  captchaId: '',
  captchaCode: '',
  rememberMe: true
})
const captchaImage = ref('')
const captchaLoading = ref(false)
const captchaVisible = ref(false)
const demoAccounts = ref<DemoAccount[]>([])
const loginAccessText = computed(() => demoAccounts.value.length
  ? '使用测试账号快速体验权限、流程、审计和监控能力。'
  : '请输入企业账号登录系统。')

onMounted(() => {
  loadLoginPreference()
  loadDemoAccounts()
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captchaCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}

function useDemoAccount(account: DemoAccount) {
  form.username = account.username
  form.password = account.password
  nextTick(() => formRef.value?.clearValidate(['username', 'password']))
}

async function loadDemoAccounts() {
  try {
    const response = await demoAccountsApi()
    demoAccounts.value = response.data || []
    fillDemoPasswordFromCurrentUser()
  } catch {
    demoAccounts.value = []
  }
}

function fillDemoPasswordFromCurrentUser() {
  if (form.password || !demoAccounts.value.length) {
    return
  }
  const currentUsername = form.username.trim()
  const matchedAccount = currentUsername
    ? demoAccounts.value.find((account) => account.username === currentUsername)
    : demoAccounts.value[0]
  if (matchedAccount) {
    useDemoAccount(matchedAccount)
  }
}

async function submit() {
  const valid = await formRef.value?.validate()
  if (!valid) return

  loading.value = true
  try {
    await auth.login({
      username: form.username,
      password: form.password,
      captchaId: form.captchaId,
      captchaCode: form.captchaCode
    }, { rememberMe: form.rememberMe })
    persistLoginPreference()
    ElMessage.success({ message: '登录成功', offset: 76 })
    syncDynamicRoutes(router, auth.menus)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    await router.replace(redirect || firstAuthorizedPath(auth.menus))
  } catch (error) {
    if (shouldRevealCaptcha(error)) {
      captchaVisible.value = true
      await loadCaptcha()
    }
  } finally {
    loading.value = false
  }
}

async function loadCaptcha() {
  captchaLoading.value = true
  try {
    const response = await captchaApi()
    form.captchaId = response.data.captchaId
    form.captchaCode = ''
    captchaImage.value = response.data.imageBase64
  } finally {
    captchaLoading.value = false
  }
}

function shouldRevealCaptcha(error: unknown) {
  const response = (error as { response?: { status?: number; data?: { code?: string | number; message?: string } } }).response
  const code = Number(response?.data?.code)
  const codeGroup = Number.isFinite(code) ? Math.trunc(code / 1000) : undefined
  const message = response?.data?.message || (error instanceof Error ? error.message : '')
  return (response?.status === 401 || codeGroup === 401) && (message.includes('用户名或密码') || message.includes('验证码'))
}

function loadLoginPreference() {
  const raw = localStorage.getItem(LOGIN_PREFERENCE_KEY)
  if (!raw) return
  try {
    const preference = JSON.parse(raw) as { username?: string; rememberMe?: boolean }
    if (preference.username) {
      form.username = preference.username
    }
    if (typeof preference.rememberMe === 'boolean') {
      form.rememberMe = preference.rememberMe
    }
  } catch {
    localStorage.removeItem(LOGIN_PREFERENCE_KEY)
  }
}

function persistLoginPreference() {
  if (!form.rememberMe) {
    localStorage.removeItem(LOGIN_PREFERENCE_KEY)
    return
  }
  localStorage.setItem(LOGIN_PREFERENCE_KEY, JSON.stringify({
    username: form.username,
    rememberMe: form.rememberMe
  }))
}
</script>

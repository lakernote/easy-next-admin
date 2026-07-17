<template>
  <section class="state-page menu-component-missing-page">
    <div class="state-panel">
      <div class="state-icon">
        <el-icon><Warning /></el-icon>
      </div>
      <el-tag effect="plain" type="warning">菜单配置</el-tag>
      <h1>菜单组件未找到</h1>
      <p>当前菜单已经授权，但前端没有找到对应的页面组件。请检查菜单资源里的组件路径是否与 src/views 下的文件一致。</p>
      <div class="state-meta">
        <span>组件路径</span>
        <strong>{{ componentPath || '未配置' }}</strong>
      </div>
      <div v-if="routePath" class="state-meta">
        <span>访问地址</span>
        <strong>{{ routePath }}</strong>
      </div>
      <div class="state-actions">
        <el-button :icon="RefreshLeft" @click="goBack">返回上一页</el-button>
        <el-button type="primary" :icon="House" @click="goHome">回到工作台</el-button>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { House, RefreshLeft, Warning } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { firstAuthorizedPath } from '@/router/dynamicRoutes'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const componentPath = computed(() => typeof route.meta.componentPath === 'string' ? route.meta.componentPath : '')
const routePath = computed(() => route.fullPath || route.path)

function goBack() {
  if (window.history.length > 1) {
    router.back()
    return
  }
  goHome()
}

function goHome() {
  router.replace(firstAuthorizedPath(auth.menus))
}
</script>

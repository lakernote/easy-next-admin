<template>
  <a class="skip-link" href="#main-content">跳到主内容</a>

  <el-container class="app-shell" :class="{ 'is-sidebar-collapsed': isCollapsed }">
    <el-aside :width="sidebarWidth" class="app-sidebar">
      <div class="brand">
        <div class="brand-mark">
          <img src="/brand/easy-next-admin-mark.svg?v=20260512-hub" alt="" aria-hidden="true" draggable="false" />
        </div>
        <div class="brand-copy">
          <strong>EasyNextAdmin</strong>
          <span>企业开发脚手架</span>
        </div>
      </div>

      <div class="side-section-label">核心导航</div>

      <el-menu
        router
        :collapse="isCollapsed"
        :collapse-transition="false"
        :default-active="$route.path"
        :default-openeds="defaultOpeneds"
        class="side-menu"
      >
        <SidebarMenuNode v-for="item in menuItems" :key="sidebarMenuIndex(item)" :item="item" :resolve-icon="resolveIcon" />
      </el-menu>
    </el-aside>

    <el-drawer
      v-model="mobileMenuVisible"
      class="mobile-menu-drawer"
      direction="ltr"
      size="min(320px, 86vw)"
      :with-header="false"
      append-to-body
    >
      <div class="mobile-menu-shell">
        <div class="mobile-menu-head">
          <div class="brand-mark">
            <img src="/brand/easy-next-admin-mark.svg?v=20260512-hub" alt="" aria-hidden="true" draggable="false" />
          </div>
          <div>
            <strong>EasyNextAdmin</strong>
            <span>企业开发脚手架</span>
          </div>
        </div>
        <div class="mobile-menu-search">
          <el-icon class="header-search-icon"><Search /></el-icon>
          <el-select
            v-model="quickPath"
            filterable
            clearable
            placeholder="搜菜单"
            aria-label="搜索菜单"
            @change="handleQuickNavigate"
          >
            <el-option v-for="item in quickMenuItems" :key="item.path" :label="item.label" :value="item.path">
              <div class="quick-option">
                <span>{{ item.title }}</span>
                <small>{{ item.parentTitle || '顶层导航' }}</small>
              </div>
            </el-option>
          </el-select>
        </div>
        <el-menu
          router
          :collapse="false"
          :collapse-transition="false"
          :default-active="$route.path"
          :default-openeds="defaultOpeneds"
          class="side-menu mobile-menu"
          @select="mobileMenuVisible = false"
        >
          <SidebarMenuNode v-for="item in menuItems" :key="`mobile-${sidebarMenuIndex(item)}`" :item="item" :resolve-icon="resolveIcon" />
        </el-menu>
      </div>
    </el-drawer>

    <el-container>
      <el-header class="app-header">
        <div class="header-primary">
          <button
            class="icon-button"
            type="button"
            :aria-label="sidebarToggleAriaLabel"
            @click="toggleSidebar"
          >
            <el-icon><component :is="isCollapsed ? Expand : Fold" /></el-icon>
          </button>
          <div>
            <div class="page-title">{{ pageTitle }}</div>
            <el-breadcrumb separator="/" class="page-breadcrumb">
              <el-breadcrumb-item v-for="item in breadcrumbs" :key="item">{{ item }}</el-breadcrumb-item>
            </el-breadcrumb>
          </div>
        </div>

        <div class="header-search">
          <el-icon class="header-search-icon"><Search /></el-icon>
          <el-select
            v-model="quickPath"
            filterable
            clearable
            placeholder="搜菜单"
            aria-label="搜索菜单"
            @change="handleQuickNavigate"
          >
            <el-option v-for="item in quickMenuItems" :key="item.path" :label="item.label" :value="item.path">
              <div class="quick-option">
                <span>{{ item.title }}</span>
                <small>{{ item.parentTitle || '顶层导航' }}</small>
              </div>
            </el-option>
          </el-select>
        </div>

        <div class="header-actions">
          <el-popover
            v-model:visible="noticePopoverVisible"
            trigger="click"
            placement="bottom-end"
            width="320"
            @show="refreshHeaderMessages"
          >
            <template #reference>
              <button class="icon-button" type="button" :aria-label="noticeAriaLabel" title="消息中心">
                <el-badge :value="noticeBadgeText" :hidden="!hasUnreadNotice" class="header-notice-badge">
                  <el-icon><Bell /></el-icon>
                </el-badge>
              </button>
            </template>
            <div class="notice-popover">
              <div class="notice-popover-head">
                <strong>消息中心</strong>
                <span>{{ noticeSummary }}</span>
              </div>
              <div v-if="noticeLoading" class="notice-empty">加载中...</div>
              <template v-else>
                <button
                  v-for="item in headerMessages"
                  :key="item.id"
                  class="notice-item"
                  type="button"
                  @click="openHeaderMessage(item)"
                >
                  <strong>{{ item.title }}</strong>
                  <span>{{ item.content || '无消息内容' }}</span>
                  <small>{{ item.createdAt }}</small>
                </button>
                <div v-if="!headerMessages.length" class="notice-empty">暂无未读消息</div>
              </template>
              <div class="notice-popover-actions">
                <el-button link type="primary" @click="goMessageCenter">查看全部消息</el-button>
              </div>
            </div>
          </el-popover>
          <el-dropdown trigger="click">
            <button class="user-button">
              <UserAvatar class="header-avatar" :size="28" :user="auth.user || undefined" />
              <span class="user-button-name">{{ auth.displayName }}</span>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="router.push(profilePath)">
                  个人中心
                </el-dropdown-item>
                <el-dropdown-item :disabled="loggingOut" @click="handleLogout">
                  {{ loggingOut ? '退出中...' : '退出登录' }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <TagsView />

      <el-main id="main-content" class="app-main" tabindex="-1">
        <router-view v-slot="{ Component, route: viewRoute }">
          <component :is="Component" :key="`${viewRoute.fullPath}-${tagsView.refreshKey}`" />
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import {
  Bell,
  Expand,
  Fold,
  Search
} from '@element-plus/icons-vue'
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useTagsViewStore, type ViewTag } from '@/stores/tagsView'
import { getMessageUnreadCount, markMessageRead, pageMessages } from '@/features/message/api'
import { formatHeaderUnreadBadge, hasHeaderUnread, headerNoticeSummary } from '@/features/message/headerMessages'
import type { MessageItem, MessageUnreadCount } from '@/features/message/types'
import type { AuthMenu } from '@/features/auth/types'
import { PermissionCodes } from '@/permissions/codes'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import SidebarMenuNode from './SidebarMenuNode.vue'
import TagsView from './TagsView.vue'
import { resolveSidebarIcon } from './sidebarIconResolver'
import { buildSidebarMenus, sidebarMenuIndex, type SidebarMenuItem } from './sidebarMenus'
import { firstAuthorizedPath, firstAuthorizedRoute } from '@/router/dynamicRoutes'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const tagsView = useTagsViewStore()
const isCollapsed = ref(false)
const isMobileViewport = ref(false)
const mobileMenuVisible = ref(false)
const quickPath = ref('')
const loggingOut = ref(false)
const noticePopoverVisible = ref(false)
const noticeLoading = ref(false)
const headerUnreadCount = ref<MessageUnreadCount>({ total: 0 })
const headerMessages = ref<MessageItem[]>([])
const emptyOpenIndexes: string[] = []
const openIndexCache = new Map<string, string[]>()
const HEADER_MESSAGES_STALE_MS = 12_000
let headerMessagesRequest: Promise<void> | undefined
let headerMessagesLoadedAt = 0
let firstRouteSync = true

interface HeaderMessagesLoadOptions {
  force?: boolean
  showLoading?: boolean
}

interface QuickMenuItem {
  title: string
  label: string
  path: string
  parentTitle?: string
}

const menuItems = computed(() => {
  return buildSidebarMenus(auth.menus)
})

const sidebarWidth = computed(() => (isCollapsed.value ? '72px' : '240px'))
const sidebarToggleAriaLabel = computed(() => {
  if (isMobileViewport.value) return '打开菜单'
  return isCollapsed.value ? '展开菜单' : '收起菜单'
})
const pageTitle = computed(() => String(route.meta.title || '工作台'))
const defaultOpeneds = computed(() => {
  const matched = findMenuPath(menuItems.value, route.path)
  return matched.length > 1 ? stableOpenIndexes(matched.slice(0, -1).map(sidebarMenuIndex)) : emptyOpenIndexes
})
const breadcrumbs = computed(() => {
  const matched = findMenuPath(menuItems.value, route.path)
  if (matched.length === 0) return [pageTitle.value]
  return matched.map((item) => item.title)
})
const quickMenuItems = computed(() => flattenMenus(menuItems.value))
const authorizedTagPaths = computed(() => quickMenuItems.value.map((item) => item.path))
const homeTag = computed<ViewTag | undefined>(() => {
  const homeRoute = firstAuthorizedRoute(auth.menus)
  if (!homeRoute) return undefined
  return {
    title: homeRoute.title,
    path: homeRoute.path,
    fullPath: homeRoute.path,
    fixed: true
  }
})
const profilePath = computed(() => findMenuPathByPermission(auth.menus, PermissionCodes.profile.view) || firstAuthorizedPath(auth.menus))
const messageCenterPath = computed(() => findMenuPathByPermission(auth.menus, PermissionCodes.message.view) || firstAuthorizedPath(auth.menus))
const hasUnreadNotice = computed(() => hasHeaderUnread(headerUnreadCount.value.total))
const noticeBadgeText = computed(() => formatHeaderUnreadBadge(headerUnreadCount.value.total))
const noticeSummary = computed(() => headerNoticeSummary(headerUnreadCount.value))
const noticeAriaLabel = computed(() => hasUnreadNotice.value ? `消息中心，${noticeSummary.value}` : '消息中心，暂无未读消息')

function resolveIcon(name?: string) {
  return resolveSidebarIcon(name)
}

function findMenuPath(items: SidebarMenuItem[], path: string, parents: SidebarMenuItem[] = []): SidebarMenuItem[] {
  for (const item of items) {
    const current = [...parents, item]
    if (item.path === path) return current
    if (item.children?.length) {
      const matched = findMenuPath(item.children, path, current)
      if (matched.length) return matched
    }
  }
  return []
}

function findMenuPathByPermission(items: AuthMenu[], permissionCode: string): string {
  for (const item of items) {
    if (item.permissionCode === permissionCode && item.href?.startsWith('/')) {
      return item.href
    }
    const childPath = findMenuPathByPermission(item.children || [], permissionCode)
    if (childPath) return childPath
  }
  return ''
}

function flattenMenus(items: SidebarMenuItem[], parentTitle?: string): QuickMenuItem[] {
  return items.flatMap((item) => {
    const current = item.path
      ? [
          {
            title: item.title,
            label: parentTitle ? `${parentTitle} / ${item.title}` : item.title,
            path: item.path,
            parentTitle
          }
        ]
      : []
    const children = item.children?.length ? flattenMenus(item.children, item.title) : []
    return [...current, ...children]
  })
}

function stableOpenIndexes(indexes: string[]) {
  const key = indexes.join('/')
  if (!openIndexCache.has(key)) {
    openIndexCache.set(key, indexes)
  }
  return openIndexCache.get(key)!
}

function toggleSidebar() {
  if (isMobileViewport.value) {
    mobileMenuVisible.value = true
    return
  }
  isCollapsed.value = !isCollapsed.value
}

function handleQuickNavigate(path: string) {
  if (path && path !== route.path) {
    router.push(path)
  }
  quickPath.value = ''
  mobileMenuVisible.value = false
}

async function loadHeaderMessages(options: HeaderMessagesLoadOptions = {}) {
  const { force = false, showLoading = false } = options
  if (!auth.accessToken) {
    headerUnreadCount.value = { total: 0 }
    headerMessages.value = []
    headerMessagesLoadedAt = 0
    return
  }
  if (!force && headerMessagesLoadedAt && Date.now() - headerMessagesLoadedAt < HEADER_MESSAGES_STALE_MS) {
    return
  }
  if (headerMessagesRequest) {
    return headerMessagesRequest
  }
  const shouldShowLoading = showLoading && headerMessagesLoadedAt === 0 && headerMessages.value.length === 0
  if (shouldShowLoading) {
    noticeLoading.value = true
  }
  headerMessagesRequest = fetchHeaderMessages()
  try {
    await headerMessagesRequest
  } finally {
    headerMessagesRequest = undefined
    if (shouldShowLoading) {
      noticeLoading.value = false
    }
  }
}

async function fetchHeaderMessages() {
  const count = await getMessageUnreadCount()
  headerUnreadCount.value = count
  if (hasHeaderUnread(count.total)) {
    const page = await pageMessages({ page: 1, limit: 3, read: false })
    headerMessages.value = page.list
  } else {
    headerMessages.value = []
  }
  headerMessagesLoadedAt = Date.now()
}

function refreshHeaderMessages() {
  void loadHeaderMessages({ force: true })
}

async function openHeaderMessage(item: MessageItem) {
  if (!item.read) {
    await markMessageRead(item.id)
    await loadHeaderMessages()
  }
  noticePopoverVisible.value = false
  await router.push(item.link || messageCenterPath.value)
}

function goMessageCenter() {
  noticePopoverVisible.value = false
  void router.push(messageCenterPath.value)
}

async function handleLogout() {
  if (loggingOut.value) return
  loggingOut.value = true
  try {
    await auth.logoutCurrentSession()
  } finally {
    loggingOut.value = false
    router.replace('/login')
  }
}

function syncSidebarForViewport() {
  isMobileViewport.value = window.innerWidth <= 720
  isCollapsed.value = isMobileViewport.value
  if (!isMobileViewport.value) {
    mobileMenuVisible.value = false
  }
}

onMounted(() => {
  syncSidebarForViewport()
  window.addEventListener('resize', syncSidebarForViewport)
})

watch(
  () => route.fullPath,
  () => {
    mobileMenuVisible.value = false
    tagsView.visit(route)
    void loadHeaderMessages({ showLoading: firstRouteSync })
    firstRouteSync = false
  },
  { immediate: true }
)

watch([homeTag, authorizedTagPaths], ([tag, paths]) => {
  tagsView.setHomeTag(tag)
  tagsView.syncAuthorizedTags(paths, tag?.path || firstAuthorizedPath(auth.menus))
}, { immediate: true })

onUnmounted(() => {
  window.removeEventListener('resize', syncSidebarForViewport)
})
</script>

<template>
  <nav class="tags-view" aria-label="已打开页面">
    <button
      v-if="hasHorizontalOverflow"
      class="tags-nav"
      type="button"
      aria-label="向左滚动页签"
      title="向左滚动页签"
      :disabled="!canScrollLeft"
      @click="scrollTags('left')"
    >
      <el-icon><ArrowLeft /></el-icon>
    </button>
    <div ref="tagsScrollRef" class="tags-scroll" @scroll="syncScrollState">
      <div
        v-for="tag in tagsView.tags"
        :key="tag.path"
        :class="['tag-item', { 'is-active': tag.path === tagsView.activePath, 'is-fixed': tag.fixed }]"
        @contextmenu.prevent="openContextMenu($event, tag)"
      >
        <button class="tag-link" type="button" @click="goTag(tag)">
          <span>{{ tag.title }}</span>
        </button>
        <button
          v-if="!tag.fixed"
          class="tag-close"
          type="button"
          :aria-label="`关闭${tag.title}`"
          :title="`关闭${tag.title}`"
          @click="closeTagItem(tag)"
        >
          <el-icon><Close /></el-icon>
        </button>
      </div>
    </div>
    <button
      v-if="hasHorizontalOverflow"
      class="tags-nav"
      type="button"
      aria-label="向右滚动页签"
      title="向右滚动页签"
      :disabled="!canScrollRight"
      @click="scrollTags('right')"
    >
      <el-icon><ArrowRight /></el-icon>
    </button>
    <button
      class="tags-refresh"
      type="button"
      aria-label="刷新当前页面"
      title="刷新当前页面"
      :disabled="!currentTag"
      @click="refreshCurrentTag"
    >
      <el-icon><Refresh /></el-icon>
    </button>
    <el-dropdown trigger="click" @command="handleCommand">
      <button class="tags-more" type="button" aria-label="页签操作" title="页签操作">
        <el-icon><MoreFilled /></el-icon>
      </button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="close-current" :disabled="currentTag?.fixed">关闭当前</el-dropdown-item>
          <el-dropdown-item command="close-other">关闭其他</el-dropdown-item>
          <el-dropdown-item command="close-left">关闭左侧</el-dropdown-item>
          <el-dropdown-item command="close-right">关闭右侧</el-dropdown-item>
          <el-dropdown-item command="close-all">关闭全部</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
    <Teleport to="body">
      <div
        v-if="contextMenu.visible"
        class="tags-context-menu"
        role="menu"
        aria-label="页签右键菜单"
        :style="{ left: `${contextMenu.x}px`, top: `${contextMenu.y}px` }"
        @click.stop
        @contextmenu.prevent
      >
        <button
          v-for="item in contextMenuItems"
          :key="item.command"
          type="button"
          role="menuitem"
          class="tags-context-menu-item"
          :disabled="isCommandDisabled(item.command, contextMenu.tag)"
          @click="handleContextCommand(item.command)"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </button>
      </div>
    </Teleport>
  </nav>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight, Back, CircleClose, Close, MoreFilled, Refresh, Right } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useTagsViewStore, type ViewTag } from '@/stores/tagsView'
import { firstAuthorizedPath } from '@/router/dynamicRoutes'

const router = useRouter()
const auth = useAuthStore()
const tagsView = useTagsViewStore()
const currentTag = computed(() => tagsView.tags.find((tag) => tag.path === tagsView.activePath))
const fallbackPath = computed(() => firstAuthorizedPath(auth.menus))
const tagsScrollRef = ref<HTMLElement>()
const hasHorizontalOverflow = ref(false)
const canScrollLeft = ref(false)
const canScrollRight = ref(false)
let tagsResizeObserver: ResizeObserver | undefined
const contextMenu = reactive({
  visible: false,
  x: 0,
  y: 0,
  tag: undefined as ViewTag | undefined
})
const contextMenuItems = [
  { command: 'refresh', label: '刷新页面', icon: Refresh },
  { command: 'close-current', label: '关闭当前', icon: Close },
  { command: 'close-other', label: '关闭其他', icon: CircleClose },
  { command: 'close-left', label: '关闭左侧', icon: Back },
  { command: 'close-right', label: '关闭右侧', icon: Right },
  { command: 'close-all', label: '全部关闭', icon: CircleClose }
] as const

function syncScrollState() {
  const scrollEl = tagsScrollRef.value
  if (!scrollEl) {
    hasHorizontalOverflow.value = false
    canScrollLeft.value = false
    canScrollRight.value = false
    return
  }
  hasHorizontalOverflow.value = scrollEl.scrollWidth > scrollEl.clientWidth + 2
  canScrollLeft.value = hasHorizontalOverflow.value && scrollEl.scrollLeft > 2
  canScrollRight.value = hasHorizontalOverflow.value && scrollEl.scrollLeft + scrollEl.clientWidth < scrollEl.scrollWidth - 2
}

function scrollTags(direction: 'left' | 'right') {
  const scrollEl = tagsScrollRef.value
  if (!scrollEl) return
  const offset = Math.max(180, Math.floor(scrollEl.clientWidth * 0.6))
  scrollEl.scrollBy({
    left: direction === 'left' ? -offset : offset,
    behavior: 'smooth'
  })
  window.setTimeout(syncScrollState, 260)
}

async function scrollActiveTagIntoView() {
  await nextTick()
  const scrollEl = tagsScrollRef.value
  if (!scrollEl) return
  const activeTag = scrollEl.querySelector<HTMLElement>('.tag-item.is-active')
  activeTag?.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: 'smooth' })
  syncScrollState()
}

function goTag(tag: ViewTag) {
  if (tag.path !== tagsView.activePath) {
    router.push(tag.fullPath)
  }
}

function closeTagItem(tag: ViewTag) {
  const wasActive = tag.path === tagsView.activePath
  const next = tagsView.close(tag.path)
  if (wasActive) {
    router.push(next || fallbackPath.value)
  }
}

function isCommandDisabled(command: string, targetTag?: ViewTag) {
  if (!targetTag) return true
  const targetIndex = tagsView.tags.findIndex((tag) => tag.path === targetTag.path)
  if (command === 'close-current') return Boolean(targetTag.fixed)
  if (command === 'close-other') {
    return !tagsView.tags.some((tag) => !tag.fixed && tag.path !== targetTag.path)
  }
  if (command === 'close-left') {
    return !tagsView.tags.some((tag, index) => !tag.fixed && index < targetIndex)
  }
  if (command === 'close-right') {
    return !tagsView.tags.some((tag, index) => !tag.fixed && index > targetIndex)
  }
  if (command === 'close-all') {
    return !tagsView.tags.some((tag) => !tag.fixed)
  }
  return false
}

function closeContextMenu() {
  contextMenu.visible = false
  contextMenu.tag = undefined
}

function openContextMenu(event: MouseEvent, tag: ViewTag) {
  const menuWidth = 152
  const menuHeight = 240
  contextMenu.tag = tag
  contextMenu.x = Math.max(8, Math.min(event.clientX, window.innerWidth - menuWidth - 8))
  contextMenu.y = Math.max(8, Math.min(event.clientY, window.innerHeight - menuHeight - 8))
  contextMenu.visible = true
}

async function handleCommand(command: string, targetTag = currentTag.value) {
  if (!targetTag || isCommandDisabled(command, targetTag)) {
    closeContextMenu()
    return
  }
  if (command === 'refresh') {
    if (targetTag.path !== tagsView.activePath) {
      await router.push(targetTag.fullPath)
    }
    tagsView.refreshCurrent()
    closeContextMenu()
    return
  }
  if (command === 'close-current') {
    const wasActive = targetTag.path === tagsView.activePath
    const next = tagsView.close(targetTag.path)
    if (wasActive) {
      await router.push(next || fallbackPath.value)
    }
    closeContextMenu()
    return
  }
  if (command === 'close-other') {
    tagsView.closeOthers(targetTag.path)
    await router.push(targetTag.fullPath)
    closeContextMenu()
    return
  }
  if (command === 'close-left') {
    const previousActivePath = tagsView.activePath
    tagsView.closeLeft(targetTag.path)
    if (!tagsView.tags.some((tag) => tag.path === previousActivePath)) {
      await router.push(targetTag.fullPath)
    }
    closeContextMenu()
    return
  }
  if (command === 'close-right') {
    const previousActivePath = tagsView.activePath
    tagsView.closeRight(targetTag.path)
    if (!tagsView.tags.some((tag) => tag.path === previousActivePath)) {
      await router.push(targetTag.fullPath)
    }
    closeContextMenu()
    return
  }
  if (command === 'close-all') {
    tagsView.closeAll()
    await router.push(fallbackPath.value)
    closeContextMenu()
  }
}

function handleContextCommand(command: string) {
  void handleCommand(command, contextMenu.tag)
}

function refreshCurrentTag() {
  void handleCommand('refresh')
}

function handleDocumentPointer() {
  closeContextMenu()
}

function handleDocumentKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    closeContextMenu()
  }
}

watch(() => [tagsView.tags.length, tagsView.activePath], () => {
  void scrollActiveTagIntoView()
}, { flush: 'post' })

onMounted(() => {
  document.addEventListener('click', handleDocumentPointer)
  document.addEventListener('scroll', handleDocumentPointer, true)
  document.addEventListener('keydown', handleDocumentKeydown)
  window.addEventListener('resize', syncScrollState)
  if (tagsScrollRef.value) {
    tagsResizeObserver = new ResizeObserver(syncScrollState)
    tagsResizeObserver.observe(tagsScrollRef.value)
  }
  void scrollActiveTagIntoView()
})

onUnmounted(() => {
  document.removeEventListener('click', handleDocumentPointer)
  document.removeEventListener('scroll', handleDocumentPointer, true)
  document.removeEventListener('keydown', handleDocumentKeydown)
  window.removeEventListener('resize', syncScrollState)
  tagsResizeObserver?.disconnect()
})
</script>

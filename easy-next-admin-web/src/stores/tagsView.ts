import { defineStore } from 'pinia'
import type { RouteLocationNormalizedLoaded } from 'vue-router'

export interface ViewTag {
  title: string
  path: string
  fullPath: string
  name?: string
  fixed?: boolean
}

export function tagFromRoute(route: Pick<RouteLocationNormalizedLoaded, 'path' | 'fullPath' | 'name' | 'meta'>): ViewTag | undefined {
  if (route.meta.hidden || route.path === '/403') return undefined
  return {
    title: String(route.meta.title || route.name || route.path),
    path: route.path,
    fullPath: route.fullPath || route.path,
    name: typeof route.name === 'string' ? route.name : undefined,
    fixed: route.meta.fixed === true
  }
}

export function upsertTag(tags: ViewTag[], tag: ViewTag): ViewTag[] {
  const existingIndex = tags.findIndex((item) => item.path === tag.path)
  if (existingIndex < 0) return [...tags, tag]
  return tags.map((item, index) => (index === existingIndex ? { ...item, ...tag, fixed: item.fixed || tag.fixed } : item))
}

export function ensureHomeTag(tags: ViewTag[], homeTag?: ViewTag): ViewTag[] {
  if (!homeTag?.path) return tags
  const fixedHomeTag = {
    ...homeTag,
    fullPath: homeTag.fullPath || homeTag.path,
    fixed: true
  }
  return [fixedHomeTag, ...tags.filter((tag) => tag.path !== fixedHomeTag.path)]
}

export function closeTag(tags: ViewTag[], path: string): ViewTag[] {
  return tags.filter((tag) => tag.fixed || tag.path !== path)
}

export function closeOtherTags(tags: ViewTag[], path: string): ViewTag[] {
  return tags.filter((tag) => tag.fixed || tag.path === path)
}

export function closeLeftTags(tags: ViewTag[], path: string): ViewTag[] {
  const index = tags.findIndex((tag) => tag.path === path)
  if (index <= 0) return tags
  return tags.filter((tag, currentIndex) => tag.fixed || currentIndex >= index)
}

export function closeRightTags(tags: ViewTag[], path: string): ViewTag[] {
  const index = tags.findIndex((tag) => tag.path === path)
  if (index < 0) return tags
  return tags.filter((tag, currentIndex) => tag.fixed || currentIndex <= index)
}

export function closeAllTags(tags: ViewTag[] = []) {
  return tags.filter((tag) => tag.fixed)
}

export function filterAuthorizedTags(tags: ViewTag[], authorizedPaths: string[]) {
  const allowedPaths = new Set(authorizedPaths)
  if (allowedPaths.size === 0) return []
  return tags.filter((tag) => allowedPaths.has(tag.path))
}

export function nextActivePath(tags: ViewTag[], closingPath: string, activePath: string, fallbackPath = '') {
  if (closingPath !== activePath) return activePath
  const index = tags.findIndex((tag) => tag.path === closingPath)
  return tags[index + 1]?.fullPath || tags[index - 1]?.fullPath || fallbackPath
}

export function resolveActivePathAfterTagChange(tags: ViewTag[], activePath: string, fallbackPath: string) {
  if (tags.some((tag) => tag.path === activePath)) return activePath
  return tags.find((tag) => tag.path === fallbackPath)?.path || tags[0]?.path || fallbackPath
}

export const useTagsViewStore = defineStore('tagsView', {
  state: () => ({
    tags: [] as ViewTag[],
    homeTag: undefined as ViewTag | undefined,
    activePath: '',
    refreshKey: 0
  }),
  actions: {
    setHomeTag(tag?: ViewTag) {
      const previousHomePath = this.homeTag?.path
      this.homeTag = tag
        ? {
            ...tag,
            fullPath: tag.fullPath || tag.path,
            fixed: true
          }
        : undefined
      const nextTags = previousHomePath && previousHomePath !== this.homeTag?.path
        ? this.tags.filter((item) => item.path !== previousHomePath)
        : this.tags
      this.tags = ensureHomeTag(nextTags, this.homeTag)
    },
    visit(route: RouteLocationNormalizedLoaded) {
      const routeTag = tagFromRoute(route)
      const tag = routeTag && this.homeTag?.path === routeTag.path
        ? { ...routeTag, fullPath: this.homeTag.fullPath || routeTag.fullPath, fixed: true }
        : routeTag
      if (!tag) return
      this.tags = ensureHomeTag(upsertTag(this.tags, tag), this.homeTag)
      this.activePath = tag.path
    },
    close(path: string) {
      const target = this.tags.find((tag) => tag.path === path)
      if (target?.fixed) return target.fullPath
      const next = nextActivePath(this.tags, path, this.activePath)
      this.tags = closeTag(this.tags, path)
      if (this.activePath === path) {
        this.activePath = this.tags.find((tag) => tag.fullPath === next)?.path || this.tags[0]?.path || ''
      }
      return next
    },
    closeOthers(path: string) {
      this.tags = closeOtherTags(this.tags, path)
      this.activePath = path
    },
    closeLeft(path: string) {
      this.tags = closeLeftTags(this.tags, path)
      this.activePath = resolveActivePathAfterTagChange(this.tags, this.activePath, path)
    },
    closeRight(path: string) {
      this.tags = closeRightTags(this.tags, path)
      this.activePath = resolveActivePathAfterTagChange(this.tags, this.activePath, path)
    },
    closeAll() {
      this.tags = closeAllTags(this.tags)
      this.activePath = this.tags[0]?.path || ''
    },
    syncAuthorizedTags(authorizedPaths: string[], fallbackPath = '') {
      this.tags = filterAuthorizedTags(this.tags, authorizedPaths)
      this.activePath = resolveActivePathAfterTagChange(this.tags, this.activePath, fallbackPath)
    },
    refreshCurrent() {
      this.refreshKey += 1
    }
  }
})

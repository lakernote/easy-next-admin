import type { Router, RouteComponent } from 'vue-router'
import type { AuthMenu } from '@/features/auth/types'

const viewModules = import.meta.glob('/src/views/**/*.vue')
const dynamicRouteNames = new Set<string>()
let routeSignature = ''

const MAX_MENU_TREE_DEPTH = 12

interface DynamicRouteMenu {
  path: string
  title: string
  icon?: string
  powerCode?: string
  componentPath: string
  visible: boolean
}

interface ViewComponentResolution {
  component: RouteComponent
  missing: boolean
}

interface MenuVisitNode {
  menu: AuthMenu
  depth: number
  visitingIds: Set<string>
}

export function syncDynamicRoutes(router: Router, menus: AuthMenu[]) {
  const routeMenus = collectRouteMenus(menus)
  const homeRoute = firstAuthorizedRouteFrom(routeMenus)
  const nextSignature = JSON.stringify(routeMenus.map((item) => [
    item.path,
    item.title,
    item.icon || '',
    item.powerCode || '',
    item.componentPath,
    item.visible ? '1' : '0'
  ]))
  if (nextSignature === routeSignature) {
    return false
  }

  resetDynamicRoutes(router)
  routeMenus.forEach((menu) => {
    const routeName = dynamicRouteName(menu.path)
    const viewComponent = resolveViewComponent(menu.componentPath)
    router.addRoute('AppShell', {
      path: trimLeadingSlash(menu.path),
      name: routeName,
      component: viewComponent.component,
      meta: {
        title: menu.title,
        icon: menu.icon,
        requiresAuth: true,
        permissionCode: menu.powerCode,
        dynamic: true,
        fixed: menu.path === homeRoute?.path,
        componentPath: menu.componentPath,
        componentMissing: viewComponent.missing
      }
    })
    dynamicRouteNames.add(routeName)
  })
  routeSignature = nextSignature
  return true
}

export function resetDynamicRoutes(router: Router) {
  dynamicRouteNames.forEach((name) => {
    if (router.hasRoute(name)) {
      router.removeRoute(name)
    }
  })
  dynamicRouteNames.clear()
  routeSignature = ''
}

export function firstAuthorizedPath(menus: AuthMenu[]) {
  return firstAuthorizedRoute(menus)?.path || '/403'
}

export function firstAuthorizedRoute(menus: AuthMenu[]) {
  return firstAuthorizedRouteFrom(collectRouteMenus(menus))
}

function collectRouteMenus(menus: AuthMenu[]) {
  const routeMenus: DynamicRouteMenu[] = []
  const seenPaths = new Set<string>()
  const stack: MenuVisitNode[] = normalizeMenus(menus)
    .reverse()
    .map((menu) => ({ menu, depth: 0, visitingIds: new Set<string>() }))

  while (stack.length > 0) {
    const item = stack.pop()
    if (!item || item.depth > MAX_MENU_TREE_DEPTH) {
      continue
    }

    const { menu, depth, visitingIds } = item
    const menuId = String(menu.id ?? '')
    if (menuId && visitingIds.has(menuId)) {
      continue
    }
    if (menu.enable === false) {
      continue
    }

    const nextVisitingIds = new Set(visitingIds)
    if (menuId) {
      nextVisitingIds.add(menuId)
    }

    const path = normalizeRoutePath(menu.href)
    if (menu.type === 1 && path && menu.componentPath && !seenPaths.has(path)) {
      seenPaths.add(path)
      routeMenus.push({
        path,
        title: menu.title,
        icon: menu.icon,
        powerCode: menu.powerCode,
        componentPath: menu.componentPath,
        visible: menu.visible !== false
      })
    }

    normalizeMenus(menu.children).reverse().forEach((child) => {
      stack.push({ menu: child, depth: depth + 1, visitingIds: nextVisitingIds })
    })
  }

  return routeMenus
}

function firstAuthorizedRouteFrom(routeMenus: DynamicRouteMenu[]) {
  return routeMenus.find((item) => item.visible) || routeMenus[0]
}

function normalizeMenus(menus: AuthMenu[] | undefined) {
  if (!Array.isArray(menus)) {
    return []
  }
  return menus.filter(isAuthMenu).sort(compareAuthMenus)
}

function isAuthMenu(menu: unknown): menu is AuthMenu {
  return Boolean(menu && typeof menu === 'object' && 'title' in menu)
}

function resolveViewComponent(componentPath: string): ViewComponentResolution {
  const key = normalizeComponentPath(componentPath)
  const loader = viewModules[key]
  if (!loader) {
    console.warn(`[EasyNextAdmin] 未找到菜单组件: ${componentPath}`)
    return {
      component: () => import('@/views/error/MenuComponentMissingView.vue'),
      missing: true
    }
  }
  return {
    component: loader as RouteComponent,
    missing: false
  }
}

function normalizeComponentPath(componentPath: string) {
  return componentPath.replace(/^@\//, '/src/').replace(/^src\//, '/src/')
}

function normalizeRoutePath(path?: string) {
  if (!path || !path.startsWith('/')) {
    return ''
  }
  return path
}

function trimLeadingSlash(path: string) {
  return path.replace(/^\/+/, '')
}

function dynamicRouteName(path: string) {
  return `Dynamic${path.split('/').filter(Boolean).map(capitalize).join('') || 'Home'}`
}

function capitalize(value: string) {
  return value.slice(0, 1).toUpperCase() + value.slice(1).replace(/[-_](\w)/g, (_, char: string) => char.toUpperCase())
}

function compareAuthMenus(left: AuthMenu, right: AuthMenu) {
  return orderValue(left.sort) - orderValue(right.sort) || entityIdOrder(left.id) - entityIdOrder(right.id)
}

function orderValue(value?: number) {
  return typeof value === 'number' ? value : 0
}

function entityIdOrder(value?: string | number) {
  const numeric = Number(value)
  return Number.isFinite(numeric) ? numeric : 0
}

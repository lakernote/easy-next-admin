import type { AuthEntityId, AuthMenu } from '@/features/auth/types'

export interface SidebarMenuItem {
  title: string
  path?: string
  icon?: string
  children?: SidebarMenuItem[]
}

export function sidebarMenuIndex(item: SidebarMenuItem) {
  return item.path || item.title
}

export function buildSidebarMenus(items: AuthMenu[]): SidebarMenuItem[] {
  return [...items].sort(compareAuthMenus).flatMap((item) => {
    if (item.enable === false || item.visible === false) return []

    const children = buildSidebarMenus(item.children || [])
    if (item.type === 0) {
      return children.length ? [{ title: item.title, icon: item.icon, children }] : []
    }
    if (item.type === 1 && item.href?.startsWith('/')) {
      const sidebarItem: SidebarMenuItem = {
        title: item.title,
        path: item.href,
        icon: item.icon
      }
      if (children.length) {
        sidebarItem.children = children
      }
      return [sidebarItem]
    }
    return children
  })
}

function compareAuthMenus(left: AuthMenu, right: AuthMenu) {
  return orderValue(left.sort) - orderValue(right.sort) || entityIdOrder(left.id) - entityIdOrder(right.id)
}

function orderValue(value?: number) {
  return typeof value === 'number' ? value : 0
}

function entityIdOrder(value?: AuthEntityId) {
  const numeric = Number(value)
  return Number.isFinite(numeric) ? numeric : 0
}

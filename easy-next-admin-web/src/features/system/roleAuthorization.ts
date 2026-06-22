import type { SystemMenu } from './types'

export type RolePermissionType = 'menu' | 'button'

export interface RolePermission {
  code: string
  name: string
  group: string
  type: RolePermissionType
  description?: string
  featureId: string
  featureName: string
}

export interface RolePermissionMenuNode {
  key: string
  featureId: string
  featureName: string
  groupName: string
  menu?: RolePermission
  actions: RolePermission[]
  actionCodes: string[]
}

export interface RolePermissionMenuSection {
  key: string
  featureId: string
  featureName: string
  nodes: RolePermissionMenuNode[]
  menuCodes: string[]
}

export function buildRolePermissionMenuSections(menuTree: SystemMenu[]): RolePermissionMenuSection[] {
  return menuTree
    .map((menu) => {
      const nodes = collectMenuPermissionNodes(menu, menu)
      if (nodes.length === 0) return undefined
      return {
        key: `section:${menu.menuId}`,
        featureId: String(menu.menuId),
        featureName: menu.title,
        nodes,
        menuCodes: uniqueCodes(nodes.flatMap((node) => (node.menu ? [node.menu.code] : [])))
      }
    })
    .filter(Boolean) as RolePermissionMenuSection[]
}

export function flattenRolePermissionMenuNodes(sections: RolePermissionMenuSection[]) {
  return sections.flatMap((section) => section.nodes)
}

function collectMenuPermissionNodes(section: SystemMenu, current: SystemMenu): RolePermissionMenuNode[] {
  const nodes: RolePermissionMenuNode[] = []
  if (isVisiblePageMenu(current)) {
    const menu = toMenuPermission(section, current)
    const actions = collectButtonPermissions(section, current, menu.code)
    nodes.push({
      key: `menu:${current.menuId}`,
      featureId: menu.featureId,
      featureName: menu.featureName,
      groupName: menu.group,
      menu,
      actions,
      actionCodes: actions.map((permission) => permission.code)
    })
  }
  const childMenus = current.children || []
  childMenus
    .filter((child) => child.type !== 2)
    .forEach((child) => {
      nodes.push(...collectMenuPermissionNodes(section, child))
    })
  return nodes
}

function collectButtonPermissions(section: SystemMenu, menu: SystemMenu, menuCode: string) {
  const actions = new Map<string, RolePermission>()
  const childResources = menu.children || []
  childResources
    .filter((child) => child.type === 2 && child.permissionCode)
    .forEach((child) => {
      if (!child.permissionCode || child.permissionCode === menuCode) return
      actions.set(child.permissionCode, {
        code: child.permissionCode,
        name: child.title,
        group: menu.title,
        type: 'button',
        description: child.remark,
        featureId: String(section.menuId),
        featureName: section.title
      })
    })
  return Array.from(actions.values())
}

function toMenuPermission(section: SystemMenu, menu: SystemMenu): RolePermission {
  return {
    code: menu.permissionCode || '',
    name: menu.title,
    group: menu.title,
    type: 'menu',
    description: menu.remark,
    featureId: String(section.menuId),
    featureName: section.title
  }
}

function isVisiblePageMenu(menu: SystemMenu) {
  return menu.type === 1 && Boolean(menu.permissionCode) && menu.visible !== false
}

function uniqueCodes(codes: string[]) {
  return Array.from(new Set(codes.filter(Boolean)))
}

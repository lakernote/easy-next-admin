import type { EntityId, SystemMenu } from './types'

export type MenuResourceKind = 'directory' | 'menu' | 'button'

export interface NavigationNode {
  id: string
  menuId?: EntityId
  parentMenuId?: EntityId
  name: string
  code: string
  icon?: string
  resource?: string
  componentPath?: string
  visible?: boolean
  description?: string
  sort?: number
  enable: boolean
  type: MenuResourceKind
  children: NavigationNode[]
  permissionChildren: NavigationNode[]
}

export function buildNavigationTree(menus: SystemMenu[]) {
  const flatMenus = flattenSystemMenus(menus)
  const nodeMap = new Map<string, NavigationNode>()
  const roots: NavigationNode[] = []

  flatMenus.forEach((menu) => {
    nodeMap.set(String(menu.menuId), toNavigationNode(menu))
  })

  flatMenus.forEach((menu) => {
    const node = nodeMap.get(String(menu.menuId))
    if (!node) return
    const parent = node.parentMenuId ? nodeMap.get(String(node.parentMenuId)) : undefined
    if (isPermissionNode(node)) {
      parent?.permissionChildren.push(node)
      return
    }
    if (parent && !isPermissionNode(parent)) {
      parent.children.push(node)
      return
    }
    roots.push(node)
  })

  sortNavigationTree(roots)
  nodeMap.forEach((node) => {
    node.permissionChildren.sort(compareNavigationNodes)
  })
  return roots
}

export function filterNavigationTree(nodes: NavigationNode[], keyword: string): NavigationNode[] {
  const text = keyword.trim().toLowerCase()
  if (!text) return nodes
  return nodes
    .map((node) => {
      const children = filterNavigationTree(node.children, keyword)
      if (matchesNavigationNode(node, text) || children.length || node.permissionChildren.some((item) => matchesNavigationNode(item, text))) {
        return { ...node, children }
      }
      return undefined
    })
    .filter(Boolean) as NavigationNode[]
}

export function flattenNavigationNodes(nodes: NavigationNode[]): NavigationNode[] {
  return nodes.flatMap((node) => [node, ...flattenNavigationNodes(node.children)])
}

export function flattenPermissionNodes(nodes: NavigationNode[]): NavigationNode[] {
  return flattenNavigationNodes(nodes).flatMap((node) => node.permissionChildren)
}

export function compareNavigationNodes(left: NavigationNode, right: NavigationNode) {
  return orderValue(left.sort) - orderValue(right.sort) || entityIdOrder(left.menuId ?? left.id) - entityIdOrder(right.menuId ?? right.id)
}

export function sameEntityId(left?: EntityId, right?: EntityId) {
  return left !== undefined && right !== undefined && String(left) === String(right)
}

function flattenSystemMenus(items: SystemMenu[]): SystemMenu[] {
  return items.flatMap((item) => [item, ...flattenSystemMenus(item.children || [])])
}

function toNavigationNode(menu: SystemMenu): NavigationNode {
  return {
    id: `${resourceKind(menu.type)}:${menu.menuId}`,
    menuId: menu.menuId,
    parentMenuId: menu.pid,
    name: menu.title,
    code: menu.permissionCode || '',
    icon: menu.icon,
    resource: menu.type === 1 ? menu.href : undefined,
    componentPath: menu.type === 1 ? menu.componentPath : undefined,
    visible: menu.visible,
    description: menu.remark,
    sort: menu.sort,
    enable: menu.enable ?? true,
    type: resourceKind(menu.type),
    children: [],
    permissionChildren: []
  }
}

function sortNavigationTree(nodes: NavigationNode[]) {
  nodes.sort(compareNavigationNodes)
  nodes.forEach((node) => sortNavigationTree(node.children))
}

function matchesNavigationNode(node: NavigationNode, text: string) {
  return [node.name, node.code, node.resource, node.description]
    .filter(Boolean)
    .some((value) => String(value).toLowerCase().includes(text))
}

function isPermissionNode(node: NavigationNode) {
  return node.type === 'button'
}

function resourceKind(type: SystemMenu['type']): MenuResourceKind {
  if (type === 0) return 'directory'
  if (type === 1) return 'menu'
  return 'button'
}

function orderValue(value?: number) {
  return typeof value === 'number' ? value : 0
}

function entityIdOrder(value?: EntityId | string) {
  const numeric = Number(value)
  return Number.isFinite(numeric) ? numeric : 0
}

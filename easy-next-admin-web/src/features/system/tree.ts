import type { EntityId, SystemDepartment, SystemMenu } from './types'

export function toSystemMenuTree(list: SystemMenu[]) {
  return toTree([...list].sort(compareSystemMenus), 'menuId', 'pid')
}

export function toDepartmentTree(list: SystemDepartment[]) {
  return toTree(list, 'deptId', 'pid')
}

// 后端返回扁平列表，前端页面统一转换为 Element Plus Tree 可用结构。
function toTree<T extends object>(list: T[], idKey: keyof T, parentKey: keyof T) {
  const nodeMap = new Map<string, T & { children?: Array<T & { children?: T[] }> }>()
  const roots: Array<T & { children?: Array<T & { children?: T[] }> }> = []

  list.forEach((item) => {
    nodeMap.set(String(item[idKey] ?? ''), { ...item, children: [] })
  })

  nodeMap.forEach((node) => {
    const parentId = String(node[parentKey] ?? '')
    const parent = parentId && parentId !== '0' ? nodeMap.get(parentId) : undefined
    if (parent) {
      parent.children?.push(node)
    } else {
      roots.push(node)
    }
  })

  return roots.map((node) => {
    if (!node.children?.length) {
      delete node.children
    }
    return node
  })
}

function compareSystemMenus(left: SystemMenu, right: SystemMenu) {
  return (
    entityIdOrder(left.pid) - entityIdOrder(right.pid) ||
    orderValue(left.sort) - orderValue(right.sort) ||
    entityIdOrder(left.menuId) - entityIdOrder(right.menuId)
  )
}

function orderValue(value?: number) {
  return typeof value === 'number' ? value : 0
}

function entityIdOrder(value?: EntityId) {
  const numeric = Number(value)
  return Number.isFinite(numeric) ? numeric : 0
}

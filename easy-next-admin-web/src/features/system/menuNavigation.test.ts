import { describe, expect, it } from 'vitest'
import type { SystemMenu } from './types'
import { buildNavigationTree, filterNavigationTree, flattenNavigationNodes } from './menuNavigation'

const menus: SystemMenu[] = [
  {
    menuId: 20,
    pid: 0,
    title: '系统管理',
    type: 0,
    sort: 20,
    enable: true,
    children: [
      { menuId: 22, pid: 20, title: '角色权限', href: '/system/roles', type: 1, powerCode: 'sys:role:list', sort: 20, enable: true },
      {
        menuId: 21,
        pid: 20,
        title: '用户管理',
        href: '/system/users',
        type: 1,
        powerCode: 'sys:user:list',
        sort: 10,
        enable: true,
        children: [
          { menuId: 211, pid: 21, title: '新增用户', type: 2, powerCode: 'sys:user:add', sort: 11, enable: true }
        ]
      }
    ]
  },
  { menuId: 10, pid: 0, title: '工作台', href: '/dashboard', type: 1, powerCode: 'dashboard:view', sort: 10, enable: true },
  {
    menuId: 30,
    pid: 0,
    title: '运行监控',
    type: 0,
    sort: 30,
    enable: true,
    children: [
      { menuId: 40, pid: 30, title: '定时任务', href: '/schedule/jobs', type: 1, powerCode: 'schedule:job:list', sort: 50, enable: true }
    ]
  }
]

describe('menuNavigation', () => {
  it('builds the same navigation hierarchy that the sidebar uses', () => {
    const tree = buildNavigationTree(menus)

    expect(tree.map((node) => node.name)).toEqual(['工作台', '系统管理', '运行监控'])
    expect(tree[1].children.map((node) => node.name)).toEqual(['用户管理', '角色权限'])
    expect(tree[1].children[0].permissionChildren.map((node) => node.name)).toEqual(['新增用户'])
    expect(tree[1].children[0].permissionChildren[0].resource).toBeUndefined()
    expect(tree[2].children.map((node) => node.name)).toEqual(['定时任务'])
  })

  it('filters by navigation and permission fields while keeping ancestors', () => {
    const filtered = filterNavigationTree(buildNavigationTree(menus), 'sys:user:add')

    expect(flattenNavigationNodes(filtered).map((node) => node.name)).toEqual(['系统管理', '用户管理'])
  })

  it('does not expose unused stable resource keys in the navigation model', () => {
    const menuWithRemovedField = {
      menuId: 1,
      pid: 0,
      title: '用户管理',
      href: '/system/users',
      type: 1,
      powerCode: 'sys:user:list',
      resourceKey: 'system.user.list',
      enable: true
    } as SystemMenu & { resourceKey: string }

    const tree = buildNavigationTree([menuWithRemovedField])

    expect(tree[0]).not.toHaveProperty('resourceKey')
  })
})

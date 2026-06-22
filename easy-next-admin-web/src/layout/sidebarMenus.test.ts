import { describe, expect, it } from 'vitest'
import type { AuthMenu } from '@/features/auth/types'
import { buildSidebarMenus } from './sidebarMenus'

describe('buildSidebarMenus', () => {
  it('keeps sidebar names and order aligned with enabled visible auth menus', () => {
    const menus: AuthMenu[] = [
      {
        id: 30,
        title: '空目录',
        type: 0,
        sort: 30,
        children: []
      },
      {
        id: 20,
        title: '系统管理',
        type: 0,
        sort: 20,
        children: [
          { id: 24, title: '按钮权限', type: 2, sort: 4, powerCode: 'sys:user:add' },
          { id: 23, title: '隐藏菜单', type: 1, href: '/system/hidden', sort: 3, visible: false },
          { id: 22, title: '停用菜单', type: 1, href: '/system/disabled', sort: 1, enable: false },
          { id: 21, title: '用户管理', type: 1, href: '/system/users', sort: 2 },
          {
            id: 25,
            title: '权限配置',
            type: 0,
            sort: 5,
            children: [{ id: 26, title: '菜单配置', type: 1, href: '/system/menus', sort: 1 }]
          }
        ]
      },
      {
        id: 10,
        title: '工作台',
        type: 1,
        href: '/dashboard',
        sort: 10
      }
    ]

    const sidebarMenus = buildSidebarMenus(menus)

    expect(sidebarMenus.map((item) => item.title)).toEqual(['工作台', '系统管理'])
    expect(sidebarMenus[1].children?.map((item) => item.title)).toEqual(['用户管理', '权限配置'])
    expect(sidebarMenus[1].children?.[1].children?.map((item) => item.title)).toEqual(['菜单配置'])
  })
})

import { describe, expect, it } from 'vitest'
import type { AuthMenu } from '@/features/auth/types'
import type { Router, RouteRecordRaw } from 'vue-router'
import { firstAuthorizedRoute, syncDynamicRoutes } from './dynamicRoutes'

describe('dynamic route menu helpers', () => {
  it('resolves the first visible authorized page as the home route', () => {
    const menus: AuthMenu[] = [
      {
        id: 20,
        title: '系统管理',
        type: 0,
        sort: 20,
        children: [
          {
            id: 21,
            title: '用户管理',
            type: 1,
            sort: 10,
            href: '/system/users',
            componentPath: '@/views/system/UserView.vue'
          }
        ]
      },
      {
        id: 10,
        title: '工作台',
        type: 1,
        sort: 10,
        href: '/dashboard',
        componentPath: '@/views/dashboard/WorkspaceView.vue'
      }
    ]

    expect(firstAuthorizedRoute(menus)).toMatchObject({ title: '工作台', path: '/dashboard' })
  })

  it('falls back to a hidden page only when no visible page exists', () => {
    const menus: AuthMenu[] = [
      {
        id: 10,
        title: '个人中心',
        type: 1,
        sort: 10,
        href: '/profile/security',
        visible: false,
        componentPath: '@/views/profile/ProfileSecurityView.vue'
      }
    ]

    expect(firstAuthorizedRoute(menus)).toMatchObject({ title: '个人中心', path: '/profile/security' })
  })

  it('marks routes with missing menu components as configuration errors', () => {
    const addedRoutes: RouteRecordRaw[] = []
    const router = {
      addRoute: (_parent: string, route: RouteRecordRaw) => addedRoutes.push(route),
      removeRoute: () => undefined,
      hasRoute: () => false
    } as unknown as Router
    const menus: AuthMenu[] = [
      {
        id: 10,
        title: '实施配置页',
        type: 1,
        sort: 10,
        href: '/implementation/missing',
        permissionCode: 'sys:menu:list',
        componentPath: '@/views/system/MissingView.vue'
      }
    ]

    syncDynamicRoutes(router, menus)

    expect(addedRoutes[0]?.meta).toMatchObject({
      componentMissing: true,
      componentPath: '@/views/system/MissingView.vue'
    })
  })
})

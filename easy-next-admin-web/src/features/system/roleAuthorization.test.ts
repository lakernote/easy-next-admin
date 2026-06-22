import { describe, expect, it } from 'vitest'
import type { SystemMenu } from './types'
import { buildRolePermissionMenuSections, flattenRolePermissionMenuNodes } from './roleAuthorization'

describe('role authorization menu tree', () => {
  it('builds authorization groups from database menu resources', () => {
    const menus: SystemMenu[] = [
      {
        menuId: 1,
        pid: 0,
        title: '流程中心',
        type: 0,
        enable: true,
        visible: true,
        children: [
          {
            menuId: 11,
            pid: 1,
            title: '发起流程',
            type: 1,
            permissionCode: 'workflow:instance:start',
            href: '/workflow/start',
            enable: true,
            visible: true,
            children: [
              {
                menuId: 111,
                pid: 11,
                title: '提交流程申请',
                type: 2,
                permissionCode: 'workflow:instance:start',
                enable: true,
                visible: false
              }
            ]
          },
          {
            menuId: 12,
            pid: 1,
            title: '请假申请',
            type: 1,
            permissionCode: 'workflow:instance:start',
            href: '/workflow/leave',
            enable: true,
            visible: false
          },
          {
            menuId: 13,
            pid: 1,
            title: '我的流程',
            type: 1,
            permissionCode: 'workflow:view',
            href: '/workflow/tasks',
            enable: true,
            visible: true,
            children: [
              {
                menuId: 131,
                pid: 13,
                title: '同意任务',
                type: 2,
                permissionCode: 'workflow:task:approve',
                enable: true,
                visible: false
              }
            ]
          }
        ]
      },
      {
        menuId: 2,
        pid: 0,
        title: '接口文档',
        type: 1,
        permissionCode: 'developer:api-docs:view',
        href: '/developer/api-docs',
        enable: true,
        visible: true
      }
    ]

    const sections = buildRolePermissionMenuSections(menus)
    const nodes = flattenRolePermissionMenuNodes(sections)

    expect(sections.map((section) => section.featureName)).toEqual(['流程中心', '接口文档'])
    expect(nodes.map((node) => node.groupName)).toEqual(['发起流程', '我的流程', '接口文档'])
    expect(nodes.find((node) => node.groupName === '发起流程')?.actionCodes).toEqual([])
    expect(nodes.find((node) => node.groupName === '我的流程')?.actionCodes).toEqual(['workflow:task:approve'])
  })
})

import { describe, expect, it } from 'vitest'
import {
  allResourceTypeOptions,
  menuIconOptions,
  menuIconSections,
  navigationResourceTypeOptions,
  navigationStateBadges,
  permissionResourceTypeOptions,
  resourceFormHelp,
  resourceNameLabel,
  resourceTypeIcon,
  resourceTypeText
} from './menuResourcePresentation'

describe('menuResourcePresentation', () => {
  it('keeps top-level navigation creation limited to groups and pages', () => {
    expect(navigationResourceTypeOptions.map((item) => item.value)).toEqual(['directory', 'menu'])
    expect(permissionResourceTypeOptions.map((item) => item.value)).toEqual(['button'])
  })

  it('keeps data scope out of menu resource maintenance', () => {
    expect(allResourceTypeOptions.map((item) => item.value)).toEqual(['directory', 'menu', 'button'])
    expect(resourceTypeText('button')).toBe('按钮权限')
    expect(resourceNameLabel('button')).toBe('按钮名称')
    expect(resourceFormHelp('button')).toContain('按钮授权')
  })

  it('provides local visual icon choices for navigation nodes', () => {
    expect(menuIconOptions.map((item) => item.value)).toEqual(expect.arrayContaining(['Setting', 'User', 'Connection', 'Tools']))
    expect(menuIconOptions.length).toBeGreaterThanOrEqual(48)
    expect(menuIconSections.map((section) => section.title)).toEqual(expect.arrayContaining(['系统基础', '流程业务', '监控运维']))
    expect(menuIconOptions.every((item) => !item.value.includes('://'))).toBe(true)
  })

  it('uses configured navigation icons and stable defaults by resource type', () => {
    expect(resourceTypeIcon('directory', 'Setting')).toBe('Setting')
    expect(resourceTypeIcon('directory')).toBe('FolderOpened')
    expect(resourceTypeIcon('menu')).toBe('Document')
    expect(resourceTypeIcon('button', 'Delete')).toBe('Operation')
  })

  it('separates enable state from sidebar visibility badges', () => {
    expect(navigationStateBadges({ type: 'menu', enable: true, visible: false }).map((item) => item.label)).toEqual(['启用', '隐藏路由'])
    expect(navigationStateBadges({ type: 'directory', enable: false, visible: true }).map((item) => item.label)).toEqual(['停用', '侧栏显示'])
    expect(navigationStateBadges({ type: 'button', enable: true, visible: false }).map((item) => item.label)).toEqual(['启用'])
  })

  it('can suppress normal states for dense navigation tree rows', () => {
    expect(navigationStateBadges({ type: 'menu', enable: true, visible: true }, { showNormal: false })).toEqual([])
    expect(navigationStateBadges({ type: 'menu', enable: true, visible: false }, { showNormal: false }).map((item) => item.label)).toEqual(['隐藏路由'])
    expect(navigationStateBadges({ type: 'menu', enable: false, visible: false }, { showNormal: false }).map((item) => item.label)).toEqual(['停用', '隐藏路由'])
    expect(navigationStateBadges({ type: 'directory', enable: true, visible: false }, { showNormal: false }).map((item) => item.label)).toEqual(['侧栏隐藏'])
    expect(navigationStateBadges({ type: 'button', enable: true, visible: false }, { showNormal: false })).toEqual([])
  })
})

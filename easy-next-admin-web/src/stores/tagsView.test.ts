import { describe, expect, it } from 'vitest'
import {
  closeAllTags,
  closeLeftTags,
  closeOtherTags,
  closeRightTags,
  closeTag,
  filterAuthorizedTags,
  nextActivePath,
  resolveActivePathAfterTagChange,
  tagFromRoute,
  ensureHomeTag,
  upsertTag,
  type ViewTag
} from './tagsView'

function tag(title: string, path: string): ViewTag {
  return { title, path, fullPath: path }
}

describe('tags view helpers', () => {
  it('creates tags from visible routes only', () => {
    const route = {
      path: '/system/users',
      fullPath: '/system/users',
      name: 'SystemUsers',
      meta: { title: '用户管理' }
    }

    expect(tagFromRoute(route as never)).toMatchObject({ title: '用户管理', path: '/system/users' })
    expect(tagFromRoute({ ...route, meta: { hidden: true } } as never)).toBeUndefined()
  })

  it('upserts tags by path and closes non-fixed tags', () => {
    const tags = upsertTag([], tag('用户管理', '/system/users'))
    const updated = upsertTag(tags, { ...tag('用户', '/system/users'), fullPath: '/system/users?keyword=a' })

    expect(updated).toHaveLength(1)
    expect(updated[0]).toMatchObject({ title: '用户', fullPath: '/system/users?keyword=a' })
    expect(closeTag(updated, '/system/users')).toEqual([])
  })

  it('keeps the authorized home tag fixed at the first position', () => {
    const tags = [
      tag('用户', '/system/users'),
      tag('角色', '/system/roles'),
      { ...tag('工作台', '/dashboard'), fixed: false }
    ]
    const result = ensureHomeTag(tags, { ...tag('工作台', '/dashboard'), fixed: true })

    expect(result.map((item) => item.path)).toEqual(['/dashboard', '/system/users', '/system/roles'])
    expect(result[0]).toMatchObject({ title: '工作台', fixed: true })
    expect(closeTag(result, '/dashboard')).toEqual(result)
  })

  it('supports close ranges and calculates next active path', () => {
    const tags = [
      tag('工作台', '/dashboard'),
      tag('用户', '/system/users'),
      tag('角色', '/system/roles'),
      tag('我的流程', '/workflow/tasks')
    ]

    expect(closeOtherTags(tags, '/system/roles').map((item) => item.path)).toEqual(['/system/roles'])
    expect(closeLeftTags(tags, '/workflow/tasks').map((item) => item.path)).toEqual(['/workflow/tasks'])
    expect(closeRightTags(tags, '/system/users').map((item) => item.path)).toEqual(['/dashboard', '/system/users'])
    expect(closeAllTags(tags)).toEqual([])
    expect(nextActivePath(tags, '/system/roles', '/system/roles')).toBe('/workflow/tasks')
  })

  it('moves active path to the context tag when range closing removes the current page', () => {
    const tags = [
      tag('工作台', '/dashboard'),
      tag('用户', '/system/users'),
      tag('角色', '/system/roles'),
      tag('我的流程', '/workflow/tasks')
    ]
    const afterCloseLeft = closeLeftTags(tags, '/workflow/tasks')
    const afterCloseRight = closeRightTags(tags, '/system/users')

    expect(resolveActivePathAfterTagChange(afterCloseLeft, '/system/users', '/workflow/tasks')).toBe('/workflow/tasks')
    expect(resolveActivePathAfterTagChange(afterCloseRight, '/workflow/tasks', '/system/users')).toBe('/system/users')
  })

  it('drops cached tabs that are not authorized for the current account', () => {
    const tags = [
      { ...tag('工作台', '/dashboard'), fixed: true },
      tag('用户管理', '/system/users'),
      tag('我的流程', '/workflow/tasks')
    ]

    expect(filterAuthorizedTags(tags, ['/dashboard', '/workflow/tasks']).map((item) => item.path)).toEqual([
      '/dashboard',
      '/workflow/tasks'
    ])
  })
})

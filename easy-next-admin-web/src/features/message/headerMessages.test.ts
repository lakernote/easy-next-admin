import { describe, expect, it } from 'vitest'
import { formatHeaderUnreadBadge, hasHeaderUnread, headerNoticeSummary } from './headerMessages'

describe('header message presentation', () => {
  it('formats unread badge for the top bell', () => {
    expect(hasHeaderUnread(0)).toBe(false)
    expect(formatHeaderUnreadBadge(0)).toBe('')
    expect(formatHeaderUnreadBadge(8)).toBe('8')
    expect(formatHeaderUnreadBadge(120)).toBe('99+')
  })

  it('summarizes unread message categories', () => {
    expect(headerNoticeSummary({ total: 0 })).toBe('暂无未读消息')
    expect(headerNoticeSummary({ total: 5, workflow: 2, audit: 1, task: 2 })).toBe('5 条未读，流程 2 条，审计 1 条，任务 2 条')
  })
})

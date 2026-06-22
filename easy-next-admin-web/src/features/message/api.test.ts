import { describe, expect, it } from 'vitest'
import { normalizeMessageType, normalizeUnreadCount } from './api'

describe('message api presentation', () => {
  it('maps backend categories to stable message center filters', () => {
    expect(normalizeMessageType('WORKFLOW_CC')).toBe('workflow')
    expect(normalizeMessageType('EXPORT')).toBe('task')
    expect(normalizeMessageType('SECURITY')).toBe('audit')
  })

  it('normalizes numeric string unread totals from the backend', () => {
    expect(normalizeUnreadCount('3')).toEqual({ total: 3 })
    expect(normalizeUnreadCount({ total: '5', workflow: '2' })).toEqual({ total: 5, workflow: 2 })
  })
})

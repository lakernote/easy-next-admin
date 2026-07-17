import { describe, expect, it } from 'vitest'
import { DONE_TASK_STATUSES, isDoneTaskStatus } from './taskFilters'

describe('workflow task filters', () => {
  it('treats only terminal task states as done', () => {
    expect(DONE_TASK_STATUSES).toEqual(['APPROVED', 'REJECTED', 'TRANSFERRED', 'DELEGATED', 'CANCELED'])
    expect(isDoneTaskStatus('APPROVED')).toBe(true)
    expect(isDoneTaskStatus('PENDING')).toBe(false)
  })
})

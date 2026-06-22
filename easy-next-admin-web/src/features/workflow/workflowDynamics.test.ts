import { describe, expect, it } from 'vitest'
import { sortWorkflowDynamicsForDisplay } from './workflowDynamics'

describe('workflowDynamics', () => {
  it('orders workflow dynamics by process arrival sequence instead of latest update time', () => {
    const ordered = sortWorkflowDynamicsForDisplay([
      { id: 'current-office', sequenceTime: 200, sequenceRank: 2 },
      { id: 'submit', sequenceTime: 100, sequenceRank: 0 },
      { id: 'dept-approved', sequenceTime: 100, sequenceRank: 1 }
    ])

    expect(ordered.map((item) => item.id)).toEqual(['submit', 'dept-approved', 'current-office'])
  })
})

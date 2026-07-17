import { describe, expect, it } from 'vitest'
import { resolveWorkflowTaskCenterTab, workflowTaskCenterPath } from './taskCenterTabs'

describe('workflow task center tabs', () => {
  it('builds task center paths for workbench entries', () => {
    expect(workflowTaskCenterPath('pending')).toBe('/workflow/tasks?tab=pending')
    expect(workflowTaskCenterPath('started')).toBe('/workflow/tasks?tab=started')
    expect(workflowTaskCenterPath('cc')).toBe('/workflow/tasks?tab=cc')
  })

  it('accepts only supported tab keys', () => {
    expect(resolveWorkflowTaskCenterTab('cc')).toBe('cc')
    expect(resolveWorkflowTaskCenterTab('unknown')).toBe('pending')
  })
})

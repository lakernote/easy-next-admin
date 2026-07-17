import { describe, expect, it, vi } from 'vitest'
import { loadWorkflowTaskCenterTab, type WorkflowTaskCenterApi } from './taskCenterLoader'
import type { WorkflowTask } from './types'

const pages = {
  pending: { page: 2, pageSize: 20 },
  done: { page: 1, pageSize: 10 },
  started: { page: 1, pageSize: 10 },
  cc: { page: 1, pageSize: 10 }
}

describe('workflow task center loader', () => {
  it('loads only the active pending task page', async () => {
    const pendingTask: WorkflowTask = {
      id: 1,
      instanceId: 100,
      nodeKey: 'dept_approval',
      nodeName: '部门审批',
      status: 'PENDING',
      instanceTitle: '请假申请 L001',
      businessType: 'LEAVE',
      businessId: 'L001'
    }
    const api = workflowTaskCenterApi({
      pageWorkflowTasks: vi.fn().mockResolvedValue({ list: [pendingTask], total: 1 })
    })

    const result = await loadWorkflowTaskCenterTab({ tab: 'pending', pages, api })

    expect(api.pageWorkflowTasks).toHaveBeenCalledOnce()
    expect(api.pageWorkflowTasks).toHaveBeenCalledWith({ page: 2, limit: 20, mine: true, status: 'PENDING' })
    expect(api.pageWorkflowInstances).not.toHaveBeenCalled()
    expect(api.pageWorkflowCc).not.toHaveBeenCalled()
    expect(result).toEqual({ tab: 'pending', list: [pendingTask], total: 1 })
  })

  it('loads started instances without fetching task or cc pages', async () => {
    const api = workflowTaskCenterApi({
      pageWorkflowInstances: vi.fn().mockResolvedValue({ list: [], total: 0 })
    })

    const result = await loadWorkflowTaskCenterTab({ tab: 'started', pages, api })

    expect(api.pageWorkflowInstances).toHaveBeenCalledOnce()
    expect(api.pageWorkflowInstances).toHaveBeenCalledWith({ page: 1, limit: 10, mine: true, scope: 'RUNTIME' })
    expect(api.pageWorkflowTasks).not.toHaveBeenCalled()
    expect(api.pageWorkflowCc).not.toHaveBeenCalled()
    expect(result).toEqual({ tab: 'started', list: [], total: 0 })
  })

  it('loads started history instances when the started scope is history', async () => {
    const api = workflowTaskCenterApi({
      pageWorkflowInstances: vi.fn().mockResolvedValue({ list: [], total: 0 })
    })

    await loadWorkflowTaskCenterTab({ tab: 'started', pages, api, startedScope: 'HISTORY' })

    expect(api.pageWorkflowInstances).toHaveBeenCalledWith({ page: 1, limit: 10, mine: true, scope: 'HISTORY' })
  })
})

function workflowTaskCenterApi(overrides: Partial<WorkflowTaskCenterApi>): WorkflowTaskCenterApi {
  return {
    pageWorkflowTasks: vi.fn().mockResolvedValue({ list: [], total: 0 }),
    pageWorkflowInstances: vi.fn().mockResolvedValue({ list: [], total: 0 }),
    pageWorkflowCc: vi.fn().mockResolvedValue({ list: [], total: 0 }),
    ...overrides
  }
}

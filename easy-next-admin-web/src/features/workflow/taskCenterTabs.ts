export const workflowTaskCenterTabs = ['pending', 'done', 'started', 'cc'] as const

export type WorkflowTaskCenterTab = (typeof workflowTaskCenterTabs)[number]

export function resolveWorkflowTaskCenterTab(value: unknown): WorkflowTaskCenterTab {
  return typeof value === 'string' && workflowTaskCenterTabs.includes(value as WorkflowTaskCenterTab)
    ? (value as WorkflowTaskCenterTab)
    : 'pending'
}

export function workflowTaskCenterPath(tab: WorkflowTaskCenterTab) {
  return `/workflow/tasks?tab=${tab}`
}

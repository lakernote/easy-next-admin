export const DONE_TASK_STATUSES = ['APPROVED', 'REJECTED', 'TRANSFERRED', 'DELEGATED', 'CANCELED'] as const

export function isDoneTaskStatus(status: string) {
  return DONE_TASK_STATUSES.includes(status as (typeof DONE_TASK_STATUSES)[number])
}

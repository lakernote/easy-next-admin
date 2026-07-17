import type { EntityId } from '@/features/system/types'

export interface ScheduleJob {
  jobId: EntityId
  jobCode: string
  jobName: string
  jobClassName: string
  cronExpression: string
  lockLeaseSeconds?: number
  executionMode: 'SINGLETON' | 'BROADCAST'
  enable: boolean
  jobState: 'START' | 'STOP' | number | string
  remark?: string
  createTime?: string
  updateTime?: string
}

export interface ScheduleJobLog {
  jobLogId: EntityId
  jobCode: string
  runId?: string
  instanceId?: string
  lockToken?: string
  triggerTime?: string
  traceId?: string
  startTime?: string
  endTime?: string
  status: 1 | 2 | number
  cost?: number
  threadName?: string
  errorMessage?: string
}

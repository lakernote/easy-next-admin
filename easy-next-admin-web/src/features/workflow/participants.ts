import type { EntityId } from '@/features/system/types'
import type { WorkflowAssigneeOption } from './types'

export interface WorkflowParticipantProfile {
  name: string
  userName?: string
  avatar?: string
}

export type WorkflowAssigneeMap = Map<string, WorkflowParticipantProfile>

export function buildWorkflowAssigneeMap(assignees: WorkflowAssigneeOption[]): WorkflowAssigneeMap {
  return buildWorkflowParticipantMap(assignees)
}

export function buildWorkflowParticipantMap(...participantGroups: Array<WorkflowAssigneeOption[] | undefined>): WorkflowAssigneeMap {
  return new Map(participantGroups.flatMap((items) => items || []).map((item) => [String(item.value), {
    name: item.name,
    userName: item.userName || userNameFromAssigneeName(item.name),
    avatar: item.avatar
  }]))
}

export function workflowParticipantName(userId: EntityId | undefined, assignees: WorkflowAssigneeMap) {
  return workflowParticipantProfile(userId, assignees).name
}

export function workflowParticipantProfile(userId: EntityId | undefined, assignees: WorkflowAssigneeMap): WorkflowParticipantProfile {
  if (userId === undefined || userId === null || userId === '') return { name: '-' }
  return assignees.get(String(userId)) || { name: '未知人员' }
}

export function uniqueWorkflowText(items: string[]) {
  return Array.from(new Set(items.filter(Boolean)))
}

function userNameFromAssigneeName(name: string) {
  const matched = name.match(/（(.+)）$/)
  return matched?.[1]
}

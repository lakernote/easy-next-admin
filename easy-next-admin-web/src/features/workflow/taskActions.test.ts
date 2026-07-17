import { describe, expect, it } from 'vitest'
import { PermissionCodes } from '@/permissions/codes'
import type { TaskActionType } from './taskActions'
import { workflowTaskAction, workflowTaskActions } from './taskActions'

describe('workflow task action metadata', () => {
  it('keeps master primary actions first', () => {
    expect(workflowTaskActions.map((action) => action.type)).toEqual([
      'approve',
      'approveWithComment',
      'reject',
      'transfer',
      'delegate',
      'return',
      'addSign',
      'removeSign',
      'remind'
    ])
  })

  it('marks only approve as direct submit', () => {
    const directActions = workflowTaskActions.filter((action) => action.direct).map((action) => action.type)

    expect(directActions).toEqual(['approve'])
  })

  it('requires comments for rejection and enhanced reassignment actions', () => {
    const required = workflowTaskActions.filter((action) => action.requiresComment).map((action) => action.type)

    expect(required).toEqual(['reject', 'transfer', 'delegate', 'return', 'addSign', 'removeSign'])
  })

  it('requires target user for transfer delegate add sign and remove sign', () => {
    const targetActions = workflowTaskActions.filter((action) => action.requiresUserTarget).map((action) => action.type)

    expect(targetActions).toEqual(['transfer', 'delegate', 'addSign', 'removeSign'])
  })

  it('returns undefined for unknown action lookup', () => {
    expect(workflowTaskAction('unknown' as TaskActionType)).toBeUndefined()
  })

  it('maps every action to its permission code', () => {
    expect(workflowTaskActions.map((action) => [action.type, action.permission])).toEqual([
      ['approve', PermissionCodes.workflow.taskApprove],
      ['approveWithComment', PermissionCodes.workflow.taskApprove],
      ['reject', PermissionCodes.workflow.taskReject],
      ['transfer', PermissionCodes.workflow.taskTransfer],
      ['delegate', PermissionCodes.workflow.taskDelegate],
      ['return', PermissionCodes.workflow.taskReturn],
      ['addSign', PermissionCodes.workflow.taskAddSign],
      ['removeSign', PermissionCodes.workflow.taskRemoveSign],
      ['remind', PermissionCodes.workflow.taskRemind]
    ])
  })
})

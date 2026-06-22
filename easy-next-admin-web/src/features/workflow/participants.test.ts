import { describe, expect, it } from 'vitest'
import { buildWorkflowAssigneeMap, buildWorkflowParticipantMap, uniqueWorkflowText, workflowParticipantName, workflowParticipantProfile } from './participants'

describe('workflow participants', () => {
  it('resolves participant names from assignee options', () => {
    const assignees = buildWorkflowAssigneeMap([
      { value: 1, name: '陈经理' },
      { value: '202604280101000017', name: '王审计（auditor）', avatar: '/storage/auditor.png' }
    ])

    expect(workflowParticipantName(1, assignees)).toBe('陈经理')
    expect(workflowParticipantName('202604280101000017', assignees)).toBe('王审计（auditor）')
    expect(workflowParticipantProfile('202604280101000017', assignees)).toEqual({
      name: '王审计（auditor）',
      userName: 'auditor',
      avatar: '/storage/auditor.png'
    })
  })

  it('does not expose raw user ids when assignee dictionary is incomplete', () => {
    expect(workflowParticipantName('202604280101000017', new Map())).toBe('未知人员')
    expect(workflowParticipantName(undefined, new Map())).toBe('-')
  })

  it('uses instance detail participants to fill data-scoped assignee gaps', () => {
    const participants = buildWorkflowParticipantMap(
      [{ value: 1, name: '陈经理（manager）' }],
      [{ value: '202604280101000024', name: '周运维（ops）', userName: 'ops' }]
    )

    expect(workflowParticipantName('202604280101000024', participants)).toBe('周运维（ops）')
    expect(workflowParticipantProfile('202604280101000024', participants)).toEqual({
      name: '周运维（ops）',
      userName: 'ops',
      avatar: undefined
    })
  })

  it('deduplicates readable participant text', () => {
    expect(uniqueWorkflowText(['陈经理', '', '王审计', '陈经理'])).toEqual(['陈经理', '王审计'])
  })
})

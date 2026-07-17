import { describe, expect, it } from 'vitest'
import { assistantSampleGroups, DEFAULT_ASSISTANT_SAMPLE } from './samples'

describe('assistantSampleGroups', () => {
  it('keeps the real regression scenarios available to both debug pages', () => {
    const messages = assistantSampleGroups.flatMap(group => group.samples.map(sample => sample.message))

    expect(assistantSampleGroups.map(group => group.key)).toEqual([
      'direct-replies',
      'read-only-tools',
      'leave-actions',
      'workflow-approval',
      'knowledge-boundaries',
      'compound-reasoning'
    ])
    expect(messages).toEqual(expect.arrayContaining([
      DEFAULT_ASSISTANT_SAMPLE,
      '帮我补一张上周五 13:30 到 18:00 的病假，原因是身体不舒服',
      '确认提交',
      '核对刚刚提交的请假单，告诉我申请人、请假时间、原因和当前状态',
      '帮我看看前两个待办的流程详情',
      '审批第一个',
      '确认审批',
      '驳回第一个',
      '确认驳回',
      '年假和调休有什么区别',
      '我们公司的年假能累计到明年吗？具体有效期多久？',
      '查一下我最近发起的一笔申请单号',
      '刚刚的申请单号是多少？',
      '年假和调休有什么区别？顺便查一下我现在有几个待办。'
    ]))
    expect(new Set(messages).size).toBe(messages.length)
    assistantSampleGroups.flatMap(group => group.samples).forEach(sample => {
      expect(sample.expected).not.toBe('')
      expect(['NEW', 'CURRENT']).toContain(sample.session)
    })
  })
})

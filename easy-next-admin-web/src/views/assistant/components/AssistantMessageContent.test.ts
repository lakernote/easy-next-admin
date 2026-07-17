// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AssistantMessageContent from './AssistantMessageContent.vue'

describe('AssistantMessageContent', () => {
  it('renders common model markdown as Vue text without raw html', () => {
    const wrapper = mount(AssistantMessageContent, {
      props: {
        content: '计算结果为 **260**。\n\n- 来自计算 Tool\n- <script>不能执行</script>'
      }
    })

    expect(wrapper.get('strong').text()).toBe('260')
    expect(wrapper.findAll('li')).toHaveLength(2)
    expect(wrapper.find('script').exists()).toBe(false)
    expect(wrapper.text()).toContain('<script>不能执行</script>')
  })

  it('renders model markdown tables as safe accessible tables', () => {
    const wrapper = mount(AssistantMessageContent, {
      props: {
        content: '**待办详情**\n\n| 字段 | 内容 |\n|------|------|\n| **任务标题** | 超级管理员请假申请 |\n| 状态 | <script>不能执行</script> |'
      }
    })

    expect(wrapper.get('[role="region"]').attributes('aria-label')).toBe('助手回复表格')
    expect(wrapper.findAll('table')).toHaveLength(1)
    expect(wrapper.findAll('th').map(cell => cell.text())).toEqual(['字段', '内容'])
    expect(wrapper.findAll('td').map(cell => cell.text())).toEqual([
      '任务标题',
      '超级管理员请假申请',
      '状态',
      '<script>不能执行</script>'
    ])
    expect(wrapper.find('script').exists()).toBe(false)
    expect(wrapper.findAll('strong').map(element => element.text())).toEqual(['待办详情', '任务标题'])
  })
})

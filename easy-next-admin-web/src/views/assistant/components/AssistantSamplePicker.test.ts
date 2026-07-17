// @vitest-environment jsdom

import ElementPlus from 'element-plus'
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { assistantSampleGroups } from '@/features/assistant/samples'
import AssistantSamplePicker from './AssistantSamplePicker.vue'

describe('AssistantSamplePicker', () => {
  it('renders every scenario and emits the complete message when clicked', async () => {
    const wrapper = mount(AssistantSamplePicker, {
      global: {
        plugins: [ElementPlus]
      }
    })
    const expectedCount = assistantSampleGroups.reduce((sum, group) => sum + group.samples.length, 0)
    const targetMessage = '年假和调休有什么区别？顺便查一下我现在有几个待办。'
    const targetSample = assistantSampleGroups
      .flatMap(group => group.samples)
      .find(sample => sample.message === targetMessage)

    expect(wrapper.findAll('.assistant-sample-group')).toHaveLength(6)
    expect(wrapper.findAll('.assistant-sample-card')).toHaveLength(expectedCount)
    expect(wrapper.text()).toContain('“新会话”会自动清空上一轮状态')
    expect(wrapper.text()).toContain('预期：')

    await wrapper.get(`button[title="${targetMessage}"]`).trigger('click')

    expect(wrapper.emitted('select')).toEqual([[targetSample]])
  })
})

import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const viewPath = resolve(__dirname, 'AssistantDebugView.vue')

describe('AssistantDebugView', () => {
  it('uses the assistant feature api and permission constant for debugging requests', () => {
    const source = readFileSync(viewPath, 'utf-8')

    expect(source).toContain('runAssistantDebugTurn')
    expect(source).toContain('PermissionCodes.assistant.debug')
    expect(source).toContain('AssistantSamplePicker')
    expect(source).toContain('AssistantRunInspector')
    expect(source).toContain('学习模式')
    expect(source).toContain('DEFAULT_ASSISTANT_SAMPLE')
    expect(source).toContain('相同 conversationId')
    expect(source).not.toContain('@/api/request')
  })
})

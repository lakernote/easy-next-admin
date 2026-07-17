import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const viewPath = resolve(__dirname, 'AssistantChatView.vue')

describe('AssistantChatView', () => {
  it('uses the safe streaming chat api and keeps debug details out of the user page', () => {
    const source = readFileSync(viewPath, 'utf-8')

    expect(source).toContain('streamAssistantChatTurn')
    expect(source).toContain('实时处理中')
    expect(source).toContain('AssistantChatProgress')
    expect(source).toContain('activeSteps')
    expect(source).toContain('PermissionCodes.assistant.chat')
    expect(source).toContain('conversationId')
    expect(source).toContain('AWAITING_CONFIRMATION')
    expect(source).toContain('INDETERMINATE')
    expect(source).toContain('结果待核对')
    expect(source).not.toContain('AssistantRunInspector')
    expect(source).not.toContain('requestPayload')
    expect(source).not.toContain('@/api/request')
  })
})

import { describe, expect, it } from 'vitest'
import { inspectAssistantRun } from './inspector'
import type { AssistantEntryResponse } from './types'

describe('inspectAssistantRun', () => {
  it('explains tool selection and provider latency without hiding the trace', () => {
    const inspection = inspectAssistantRun(result())

    expect(inspection.title).toBe('本轮执行完成')
    expect(inspection.modelCalls).toHaveLength(2)
    expect(inspection.toolCalls).toHaveLength(1)
    expect(inspection.checks).toEqual(expect.arrayContaining([
      expect.objectContaining({ label: 'Tool 选择', value: 'workflow.task.list_my_pending' })
    ]))
    expect(inspection.observations.join('')).toContain('主要延迟来自模型网关往返')
    expect(inspection.latencySegments.find(segment => segment.key === 'provider')?.percentage).toBe(80)
  })

  it('treats missing parameters as a normal clarification state', () => {
    const source = result()
    source.plan.status = 'INPUT_REQUIRED'
    source.terminationReason = 'WAITING_FOR_INPUT'
    source.plan.missingParameters = [{
      key: 'reason',
      label: '请假原因',
      type: 'string',
      format: '',
      description: '请假原因',
      required: true,
      options: []
    }]

    const inspection = inspectAssistantRun(source)

    expect(inspection.title).toBe('等待用户补充信息')
    expect(inspection.tone).toBe('warning')
    expect(inspection.observations.join('')).toContain('这是正常澄清，不是模型故障')
  })

  it('separates agent termination from a preserved tool result', () => {
    const source = result()
    source.terminationReason = 'MODEL_ERROR'

    const inspection = inspectAssistantRun(source)

    expect(inspection.title).toBe('已保留真实结果并安全停止')
    expect(inspection.tone).toBe('warning')
    expect(inspection.checks).toEqual(expect.arrayContaining([
      expect.objectContaining({ label: '终止原因', value: '模型调用异常' })
    ]))
  })

})

function result(): AssistantEntryResponse {
  const tokenUsage = {
    promptTokens: 100,
    completionTokens: 20,
    totalTokens: 120,
    cachedPromptTokens: 0,
    reasoningTokens: 0
  }
  return {
    phase: 'TURN_READY',
    runId: 'run-1',
    traceId: 'trace-1',
    conversationId: 1,
    normalizedMessage: '我有哪些待办',
    reply: '找到 1 个待办',
    terminationReason: 'FINAL_RESPONSE',
    tools: {
      toolNames: ['workflow.task.list_my_pending'],
      toolCount: 1,
      reason: '权限过滤完成'
    },
    plan: {
      status: 'READY',
      toolName: 'workflow.task.list_my_pending',
      readOnly: true,
      filledArguments: [],
      missingParameters: [],
      reason: '计划完成',
      missingEvidence: [],
      steps: []
    },
    execution: {
      status: 'EXECUTED',
      toolName: 'workflow.task.list_my_pending',
      artifactType: 'TASK_LIST',
      summary: '找到 1 个待办',
      data: { total: 1 }
    },
    trace: {
      totalDurationMs: 1000,
      latency: {
        totalDurationMs: 1000,
        modelDurationMs: 850,
        modelRequestPreparationDurationMs: 10,
        modelProviderRoundTripDurationMs: 800,
        modelResponseProcessingDurationMs: 40,
        toolDurationMs: 20,
        runtimeDurationMs: 130,
        modelCallCount: 2,
        toolCallCount: 1
      },
      tokenUsage,
      steps: [{
        sequence: 1,
        name: 'agent_run',
        component: 'AgentLoop',
        title: '原生 Tool Calling',
        purpose: '选择并执行 Tool',
        status: 'OK',
        durationMs: 870,
        tokenUsage,
        summary: '完成',
        modelCalls: [
          modelCall('agent_step', tokenUsage, 500),
          modelCall('final_response', tokenUsage, 350)
        ],
        toolCalls: [{ toolCallId: 'call-1', name: 'workflow.task.list_my_pending', phase: 'READ', status: 'EXECUTED', durationMs: 20 }]
      }]
    },
    time: {
      zoneId: 'Asia/Shanghai',
      requestInstant: '2026-07-14T00:00:00Z',
      today: '2026-07-14',
      now: '2026-07-14 12:00:00'
    }
  }
}

function modelCall(name: string, tokenUsage: AssistantEntryResponse['trace']['tokenUsage'], durationMs: number) {
  return {
    name,
    model: 'test-model',
    status: 'OK',
    timing: {
      mode: 'SYNCHRONOUS' as const,
      totalDurationMs: durationMs,
      requestPreparationDurationMs: 0,
      providerRoundTripDurationMs: durationMs,
      responseProcessingDurationMs: 0
    },
    tokenUsage
  }
}

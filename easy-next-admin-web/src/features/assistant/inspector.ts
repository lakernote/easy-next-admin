import type {
  AssistantEntryResponse,
  AssistantModelCallSnapshot,
  AssistantToolCallSnapshot,
  AssistantTraceStepSnapshot
} from './types'

export type AssistantInspectionTone = 'success' | 'warning' | 'danger' | 'info'

export interface AssistantInspectionCheck {
  label: string
  value: string
  detail: string
  tone: AssistantInspectionTone
}

export interface AssistantLatencySegment {
  key: string
  label: string
  valueMs: number
  percentage: number
  tone: 'primary' | 'warning' | 'success' | 'info'
}

export interface AssistantRunInspection {
  title: string
  summary: string
  tone: AssistantInspectionTone
  checks: AssistantInspectionCheck[]
  observations: string[]
  latencySegments: AssistantLatencySegment[]
  steps: AssistantTraceStepSnapshot[]
  modelCalls: AssistantModelCallSnapshot[]
  toolCalls: AssistantToolCallSnapshot[]
}

/**
 * 把完整 Trace 收敛为调试人员先看得懂的结论；原始 JSON 仍保留在页面最后一层。
 */
export function inspectAssistantRun(result: AssistantEntryResponse): AssistantRunInspection {
  const steps = result.trace.steps || []
  const modelCalls = steps.flatMap(step => step.modelCalls || [])
  const toolCalls = steps.flatMap(step => step.toolCalls || [])
  const missingLabels = result.plan.missingParameters.map(parameter => parameter.label)
  const failedSteps = steps.filter(step => step.status === 'FAILED')
  const failedToolCalls = toolCalls.filter(call => call.status === 'FAILED')
  const state = resolveRunState(result, missingLabels)
  const latencySegments = buildLatencySegments(result)
  const observations: string[] = []

  if (missingLabels.length) {
    observations.push(`还需要用户补充：${missingLabels.join('、')}。这是正常澄清，不是模型故障。`)
  }
  if (failedSteps.length || failedToolCalls.length) {
    observations.push(`发现 ${failedSteps.length} 个失败步骤、${failedToolCalls.length} 个失败 Tool 调用，请从执行链定位第一个失败点。`)
  }
  const providerSegment = latencySegments.find(segment => segment.key === 'provider')
  if (providerSegment && providerSegment.percentage >= 60) {
    observations.push(`主要延迟来自模型网关往返（${providerSegment.percentage}%），Java Runtime 和 Tool 不是当前瓶颈。`)
  }
  if (result.trace.latency.modelCallCount >= 4) {
    observations.push(`本轮发生 ${result.trace.latency.modelCallCount} 次模型调用，通常意味着依赖 Tool 链、参数修复或最终合成。`)
  }
  if (!result.plan.toolName) {
    observations.push('本轮未选择企业 Tool；如果问题需要企业数据，应检查可见 Tool 和 description 是否覆盖用户目标。')
  }
  if (!['FINAL_RESPONSE', 'WAITING_FOR_INPUT', 'WAITING_FOR_CONFIRMATION'].includes(result.terminationReason)) {
    observations.push(`Agent 因“${terminationLabel(result.terminationReason)}”停止；业务计划状态和 Tool 结果需分开判断。`)
  }
  if (observations.length === 0) {
    observations.push('未发现明显异常；Tool 选择、执行结果和最终回复已经形成闭环。')
  }

  return {
    ...state,
    checks: [
      {
        label: '终止原因',
        value: terminationLabel(result.terminationReason),
        detail: '表示 Agent Loop 为什么停止，不代表 Tool 业务执行是否成功。',
        tone: terminationTone(result.terminationReason)
      },
      {
        label: '模型决策',
        value: decisionLabel(result.plan.status),
        detail: result.plan.reason || '模型已完成本轮决策',
        tone: result.plan.status === 'BLOCKED' ? 'danger' : 'success'
      },
      {
        label: 'Tool 选择',
        value: result.plan.toolName || '未调用 Tool',
        detail: result.plan.toolName
          ? `本轮可见 ${result.tools.toolCount} 个 Tool，最终选择 1 个或进入多 Tool 链。`
          : `本轮可见 ${result.tools.toolCount} 个 Tool，模型判断无需调用。`,
        tone: result.plan.toolName ? 'success' : 'info'
      },
      {
        label: '参数与计划',
        value: missingLabels.length ? `缺少 ${missingLabels.length} 项` : '合同已通过',
        detail: missingLabels.length ? missingLabels.join('、') : `已填充 ${result.plan.filledArguments.length} 个参数`,
        tone: missingLabels.length ? 'warning' : 'success'
      }
    ],
    observations,
    latencySegments,
    steps,
    modelCalls,
    toolCalls
  }
}

function resolveRunState(result: AssistantEntryResponse, missingLabels: string[]) {
  const abnormalTermination = ![
    'FINAL_RESPONSE',
    'WAITING_FOR_INPUT',
    'WAITING_FOR_CONFIRMATION'
  ].includes(result.terminationReason)
  if (abnormalTermination) {
    const hasPreservedResult = result.execution.status === 'EXECUTED'
      || result.execution.status === 'PARTIAL_SUCCESS'
    return {
      title: hasPreservedResult ? '已保留真实结果并安全停止' : '本轮提前终止',
      summary: hasPreservedResult
        ? `Agent 因“${terminationLabel(result.terminationReason)}”停止，已执行的 Tool 结果仍然保留。`
        : result.execution.summary || result.plan.reason || '请查看终止原因和第一个失败步骤。',
      tone: hasPreservedResult ? 'warning' as const : 'danger' as const
    }
  }
  if (result.execution.status === 'FAILED' || result.plan.status === 'BLOCKED') {
    return {
      title: '本轮未完成',
      summary: result.execution.summary || result.plan.reason || '请查看失败步骤和原始响应。',
      tone: 'danger' as const
    }
  }
  if (missingLabels.length || result.plan.status === 'INPUT_REQUIRED') {
    return {
      title: '等待用户补充信息',
      summary: `Agent 已保存已知信息，等待补充 ${missingLabels.join('、') || '必要字段'}。`,
      tone: 'warning' as const
    }
  }
  if (result.plan.status === 'APPROVAL_REQUIRED') {
    return {
      title: '动作草稿已准备，尚未执行',
      summary: '企业写操作正在等待用户明确确认，这是预期的安全边界。',
      tone: 'warning' as const
    }
  }
  return {
    title: '本轮执行完成',
    summary: result.execution.summary || 'Agent 已完成回复并返回可追踪结果。',
    tone: 'success' as const
  }
}

function buildLatencySegments(result: AssistantEntryResponse): AssistantLatencySegment[] {
  const latency = result.trace.latency
  const total = Math.max(1, latency.totalDurationMs)
  const values = [
    {
      key: 'provider',
      label: '模型网关往返',
      valueMs: latency.modelProviderRoundTripDurationMs,
      tone: 'primary' as const
    },
    {
      key: 'model-runtime',
      label: '模型请求准备与响应处理',
      valueMs: Math.max(0, latency.modelDurationMs - latency.modelProviderRoundTripDurationMs),
      tone: 'warning' as const
    },
    { key: 'tool', label: 'ToolRunner', valueMs: latency.toolDurationMs, tone: 'success' as const },
    { key: 'runtime', label: '其他 Java Runtime', valueMs: latency.runtimeDurationMs, tone: 'info' as const }
  ]
  return values.map(segment => ({
    ...segment,
    percentage: Math.round((segment.valueMs / total) * 100)
  }))
}

export function decisionLabel(value: string) {
  const labels: Record<string, string> = {
    DIRECT_RESPONSE: '直接回复',
    READY: 'Tool 调用完成',
    INPUT_REQUIRED: '等待补充信息',
    APPROVAL_REQUIRED: '动作草稿待确认',
    APPROVED: '动作已确认执行',
    CANCELED: '动作已取消',
    BLOCKED: '无法安全规划'
  }
  return labels[value] || value
}

export function terminationLabel(value: string) {
  const labels: Record<string, string> = {
    FINAL_RESPONSE: '已生成最终回复',
    WAITING_FOR_INPUT: '等待用户补充',
    WAITING_FOR_CONFIRMATION: '等待用户确认',
    BLOCKED: '安全策略阻止',
    TOOL_CALL_LIMIT: 'Tool 调用达到预算',
    DUPLICATE_TOOL_CALL: '检测到重复 Tool 调用',
    MODEL_CALL_LIMIT: '模型调用达到预算',
    MODEL_ERROR: '模型调用异常',
    MODEL_MAX_TOKENS: '模型输出被截断',
    MODEL_CONTENT_FILTERED: '模型输出被过滤',
    MODEL_REFUSED: '模型拒绝请求',
    MODEL_PAUSED: '模型要求暂停',
    MODEL_PROTOCOL_ERROR: '模型协议响应异常'
  }
  return labels[value] || value
}

function terminationTone(value: string): AssistantInspectionTone {
  if (value === 'FINAL_RESPONSE') return 'success'
  if (value === 'WAITING_FOR_INPUT' || value === 'WAITING_FOR_CONFIRMATION') return 'warning'
  return 'danger'
}

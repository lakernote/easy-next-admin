export interface AssistantMessageRequest {
  message: string
  conversationId?: string | number | null
}

export type AssistantChatStatus =
  | 'COMPLETED'
  | 'AWAITING_INPUT'
  | 'AWAITING_CONFIRMATION'
  | 'PARTIALLY_COMPLETED'
  | 'FAILED'
  | 'INDETERMINATE'
  | 'BLOCKED'

export interface AssistantChatResponse {
  runId: string
  conversationId: string | number
  reply: string
  status: AssistantChatStatus
}

export interface AssistantVisibleToolSnapshot {
  toolNames: string[]
  toolCount: number
  reason?: string | null
}

export interface ToolInputFieldSnapshot {
  key: string
  label: string
  type: string
  format: string
  description: string
  required: boolean
  options: string[]
}

export interface AssistantToolArgumentSnapshot {
  key: string
  label: string
  value: string
  displayValue: string
}

export interface ToolExecutionPlanSnapshot {
  status: string
  toolName?: string | null
  readOnly: boolean
  filledArguments: AssistantToolArgumentSnapshot[]
  missingParameters: ToolInputFieldSnapshot[]
  reason?: string | null
  missingEvidence: string[]
  actionId?: string | null
  toolCallId?: string | null
  steps: ToolExecutionStepSnapshot[]
}

export interface ToolExecutionStepSnapshot {
  sequence: number
  type: string
  status: string
  toolName?: string | null
  description?: string | null
  inputKeys: string[]
  outputKeys: string[]
  observation?: string | null
}

export interface AssistantToolExecutionSnapshot {
  status: string
  toolName?: string | null
  artifactType?: string | null
  summary?: string | null
  data?: unknown
}

export interface AssistantTokenUsageSnapshot {
  promptTokens: number
  completionTokens: number
  totalTokens: number
  cachedPromptTokens: number
  reasoningTokens: number
}

export interface AssistantModelCallTimingSnapshot {
  mode: 'SYNCHRONOUS' | 'STREAMING'
  totalDurationMs: number
  requestPreparationDurationMs: number
  providerRoundTripDurationMs: number
  responseProcessingDurationMs: number
  timeToFirstTokenDurationMs?: number | null
  generationDurationMs?: number | null
}

export interface AssistantModelCallSnapshot {
  name: string
  model: string
  status: string
  timing: AssistantModelCallTimingSnapshot
  tokenUsage: AssistantTokenUsageSnapshot
  requestPayload?: unknown
  responsePayload?: unknown
  errorMessage?: string | null
}

export interface AssistantToolCallSnapshot {
  toolCallId: string
  name: string
  phase: 'READ' | 'PREPARE' | 'EXECUTE'
  status: string
  durationMs: number
}

export interface AssistantTraceStepSnapshot {
  sequence: number
  name: string
  component: string
  title: string
  purpose: string
  status: string
  durationMs: number
  tokenUsage: AssistantTokenUsageSnapshot
  summary?: string | null
  inputPayload?: unknown
  outputPayload?: unknown
  modelCalls: AssistantModelCallSnapshot[]
  toolCalls: AssistantToolCallSnapshot[]
}

export interface AssistantLatencyBreakdownSnapshot {
  totalDurationMs: number
  modelDurationMs: number
  modelRequestPreparationDurationMs: number
  modelProviderRoundTripDurationMs: number
  modelResponseProcessingDurationMs: number
  toolDurationMs: number
  runtimeDurationMs: number
  modelCallCount: number
  toolCallCount: number
}

export interface AssistantTraceSnapshot {
  totalDurationMs: number
  latency: AssistantLatencyBreakdownSnapshot
  tokenUsage: AssistantTokenUsageSnapshot
  steps: AssistantTraceStepSnapshot[]
}

export interface AssistantTimeSnapshot {
  zoneId: string
  requestInstant: string
  today: string
  now: string
}

export type AgentTerminationReason =
  | 'FINAL_RESPONSE'
  | 'WAITING_FOR_INPUT'
  | 'WAITING_FOR_CONFIRMATION'
  | 'BLOCKED'
  | 'TOOL_CALL_LIMIT'
  | 'DUPLICATE_TOOL_CALL'
  | 'MODEL_CALL_LIMIT'
  | 'MODEL_ERROR'
  | 'MODEL_MAX_TOKENS'
  | 'MODEL_CONTENT_FILTERED'
  | 'MODEL_REFUSED'
  | 'MODEL_PAUSED'
  | 'MODEL_PROTOCOL_ERROR'

export interface AssistantEntryResponse {
  phase: string
  runId: string
  traceId: string
  conversationId?: string | number | null
  normalizedMessage: string
  reply?: string | null
  terminationReason: AgentTerminationReason
  tools: AssistantVisibleToolSnapshot
  plan: ToolExecutionPlanSnapshot
  execution: AssistantToolExecutionSnapshot
  trace: AssistantTraceSnapshot
  time: AssistantTimeSnapshot
}

export type AssistantChatPhase =
  | 'PREPARING'
  | 'THINKING'
  | 'USING_TOOL'
  | 'SAVING'
  | 'COMPLETED'
  | 'FAILED'

export interface AssistantChatStreamEvent {
  event: 'progress' | 'done' | 'error'
  step: string
  sequence: number
  phase: AssistantChatPhase
  message: string
  response?: AssistantChatResponse | null
}

export interface AssistantChatProgressItem {
  step: string
  sequence: number
  phase: AssistantChatPhase
  message: string
  state: 'running' | 'completed'
}

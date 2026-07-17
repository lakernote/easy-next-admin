import request from '@/api/request'
import { createTraceHeaders } from '@/api/trace'
import { toData, type ApiResponse } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import type {
  AssistantChatStreamEvent,
  AssistantEntryResponse,
  AssistantMessageRequest
} from './types'

const ASSISTANT_MESSAGE_TIMEOUT_MS = 180_000
const API_BASE_URL = normalizeApiBaseUrl(import.meta.env.VITE_API_BASE_URL || '/api')

/** 同步调试 / eval 入口：返回完整 Agent 流程和 Trace。 */
export async function runAssistantDebugTurn(payload: AssistantMessageRequest) {
  const response = await request.post<ApiResponse<AssistantEntryResponse>>('/assistant/messages', payload, {
    // 助手入口会运行 Tool Calling 循环和工具执行，真实 LLM 网关抖动时不能沿用普通 CRUD 的 15s 超时。
    timeout: ASSISTANT_MESSAGE_TIMEOUT_MS
  })
  return toData(response.data)
}

/** 普通用户入口：只接收安全进度和精简最终回复。 */
export async function streamAssistantChatTurn(
  payload: AssistantMessageRequest,
  onEvent: (event: AssistantChatStreamEvent) => void,
  signal?: AbortSignal
) {
  const auth = useAuthStore()
  if (!auth.accessToken) {
    throw new Error('请先登录后再使用智能助手')
  }
  const response = await fetch(`${API_BASE_URL}/assistant/chat/messages/stream`, {
    method: 'POST',
    headers: {
      Accept: 'text/event-stream',
      'Content-Type': 'application/json',
      ...createTraceHeaders(),
      Authorization: `Bearer ${auth.accessToken}`
    },
    body: JSON.stringify(payload),
    signal
  })

  if (!response.ok || !response.body) {
    throw new Error(await responseErrorMessage(response))
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let terminalEventReceived = false
  const emitEvent = (event: AssistantChatStreamEvent) => {
    onEvent(event)
    if (event.event === 'done' || event.event === 'error') {
      terminalEventReceived = true
    }
  }
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    buffer = consumeSseBuffer(buffer, emitEvent)
    if (terminalEventReceived) {
      await reader.cancel().catch(() => undefined)
      break
    }
  }
  buffer += decoder.decode()
  if (!terminalEventReceived) {
    consumeSseBuffer(buffer, emitEvent, true)
  }
}

function consumeSseBuffer(
  buffer: string,
  onEvent: (event: AssistantChatStreamEvent) => void,
  flush = false
) {
  const normalized = buffer.replace(/\r\n/g, '\n')
  const blocks = normalized.split('\n\n')
  const pending = flush ? '' : blocks.pop() || ''
  blocks.forEach((block) => {
    const event = parseSseBlock(block)
    if (event) onEvent(event)
  })
  return pending
}

function parseSseBlock(block: string): AssistantChatStreamEvent | null {
  const dataLines: string[] = []
  let eventName = ''
  block.split('\n').forEach((line) => {
    if (line.startsWith('event:')) {
      eventName = line.slice('event:'.length).trim()
    }
    if (line.startsWith('data:')) {
      dataLines.push(line.slice('data:'.length).trimStart())
    }
  })
  if (!dataLines.length) return null
  const parsed = JSON.parse(dataLines.join('\n')) as AssistantChatStreamEvent
  return {
    ...parsed,
    event: parsed.event || eventName || 'message'
  }
}

async function responseErrorMessage(response: Response) {
  try {
    const body = await response.json() as ApiResponse
    return body.message || `助手请求失败：${response.status}`
  } catch {
    return `助手请求失败：${response.status}`
  }
}

function normalizeApiBaseUrl(value: string) {
  return value.replace(/\/+$/, '')
}

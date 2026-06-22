export const TRACE_ID_HEADER = 'X-Trace-Id'

export function createTraceHeaders() {
  return {
    [TRACE_ID_HEADER]: createTraceId()
  }
}

function createTraceId() {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

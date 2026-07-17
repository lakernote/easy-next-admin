import { beforeEach, describe, expect, it } from 'vitest'
import {
  clearFrontendObservabilityEvents,
  listFrontendObservabilityEvents,
  MAX_FRONTEND_EVENT_BUFFER,
  recordFrontendEvent,
  sanitizeLocationPath
} from './events'

describe('frontend observability events', () => {
  beforeEach(() => {
    clearFrontendObservabilityEvents()
  })

  it('records sanitized frontend events without query strings', () => {
    const event = recordFrontendEvent({
      type: 'frontend.api_failure',
      level: 'error',
      message: '请求失败',
      path: '/api/users?page=1&token=secret#list',
      status: 500,
      traceId: 'trace-1'
    })

    expect(event.path).toBe('/api/users#list')
    expect(event.traceId).toBe('trace-1')
    expect(listFrontendObservabilityEvents()).toHaveLength(1)
  })

  it('keeps the local event buffer bounded', () => {
    for (let i = 0; i < MAX_FRONTEND_EVENT_BUFFER + 3; i += 1) {
      recordFrontendEvent({
        type: 'frontend.route_error',
        level: 'error',
        message: `event-${i}`,
        path: `/route/${i}`
      })
    }

    const events = listFrontendObservabilityEvents()
    expect(events).toHaveLength(MAX_FRONTEND_EVENT_BUFFER)
    expect(events[0].message).toBe('event-3')
  })

  it('normalizes malformed paths without leaking query values', () => {
    expect(sanitizeLocationPath('http://[bad?token=secret')).toBe('http://[bad')
  })
})

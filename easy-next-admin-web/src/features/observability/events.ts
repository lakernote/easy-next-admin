import type { App } from 'vue'
import type { Router } from 'vue-router'

export const MAX_FRONTEND_EVENT_BUFFER = 100

export type FrontendObservabilityEventType =
  | 'frontend.api_failure'
  | 'frontend.vue_error'
  | 'frontend.window_error'
  | 'frontend.unhandled_rejection'
  | 'frontend.route_error'

export type FrontendObservabilityEventLevel = 'info' | 'warn' | 'error'

export interface FrontendObservabilityEventInput {
  type: FrontendObservabilityEventType
  level: FrontendObservabilityEventLevel
  message?: string
  path?: string
  status?: number
  traceId?: string
  detail?: Record<string, string | number | boolean | undefined>
}

export interface FrontendObservabilityEvent extends FrontendObservabilityEventInput {
  id: string
  time: string
  path: string
}

const frontendEvents: FrontendObservabilityEvent[] = []
let windowListenersInstalled = false

export function installFrontendObservability(app: App, router: Router) {
  const previousErrorHandler = app.config.errorHandler
  app.config.errorHandler = (error, instance, info) => {
    recordFrontendEvent({
      type: 'frontend.vue_error',
      level: 'error',
      message: errorMessage(error),
      detail: {
        component: instance?.$options.name,
        info
      }
    })
    previousErrorHandler?.(error, instance, info)
  }

  router.onError((error, to) => {
    recordFrontendEvent({
      type: 'frontend.route_error',
      level: 'error',
      message: errorMessage(error),
      path: to.fullPath
    })
  })

  if (typeof window === 'undefined' || windowListenersInstalled) {
    return
  }
  windowListenersInstalled = true
  window.addEventListener('error', (event) => {
    recordFrontendEvent({
      type: 'frontend.window_error',
      level: 'error',
      message: event.message || errorMessage(event.error),
      path: event.filename || currentLocationPath()
    })
  }, true)
  window.addEventListener('unhandledrejection', (event) => {
    recordFrontendEvent({
      type: 'frontend.unhandled_rejection',
      level: 'error',
      message: errorMessage(event.reason)
    })
  })
}

export function recordFrontendEvent(input: FrontendObservabilityEventInput) {
  const event: FrontendObservabilityEvent = {
    ...input,
    id: createEventId(),
    time: new Date().toISOString(),
    path: sanitizeLocationPath(input.path)
  }
  frontendEvents.push(event)
  if (frontendEvents.length > MAX_FRONTEND_EVENT_BUFFER) {
    frontendEvents.splice(0, frontendEvents.length - MAX_FRONTEND_EVENT_BUFFER)
  }
  return event
}

export function listFrontendObservabilityEvents() {
  return [...frontendEvents]
}

export function clearFrontendObservabilityEvents() {
  frontendEvents.splice(0, frontendEvents.length)
}

export function sanitizeLocationPath(value?: string) {
  const path = value || currentLocationPath()
  try {
    const base = typeof window === 'undefined' ? 'http://localhost' : window.location.origin
    const url = new URL(path, base)
    return `${url.pathname}${url.hash}` || '/'
  } catch {
    const withoutQuery = path.split('?')[0]
    const hashIndex = withoutQuery.indexOf('#')
    return hashIndex >= 0 ? withoutQuery.slice(0, hashIndex) : withoutQuery || '/'
  }
}

function currentLocationPath() {
  if (typeof window === 'undefined') {
    return '/'
  }
  return `${window.location.pathname}${window.location.hash}` || '/'
}

function createEventId() {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID()
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function errorMessage(error: unknown) {
  if (error instanceof Error) {
    return error.message
  }
  if (typeof error === 'string') {
    return error
  }
  return '未知前端异常'
}

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createLatestRequestGuard, createRuntimeDiagnosisPolling } from './runtimeDiagnosisPolling'

describe('latest diagnosis request guard', () => {
  it('allows only the latest request generation to publish state', () => {
    const guard = createLatestRequestGuard()
    const first = guard.next()
    const second = guard.next()

    expect(guard.isCurrent(first)).toBe(false)
    expect(guard.isCurrent(second)).toBe(true)

    guard.invalidate()
    expect(guard.isCurrent(second)).toBe(false)
  })
})

describe('runtime diagnosis live polling', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('polls immediately and then every five seconds while visible', async () => {
    const poll = vi.fn().mockResolvedValue(undefined)
    const controller = createRuntimeDiagnosisPolling({ shouldPoll: () => true, poll })

    controller.start()
    await vi.runAllTicks()
    expect(poll).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(10_000)
    expect(poll).toHaveBeenCalledTimes(3)
    controller.stop()
  })

  it('stops cleanly and never overlaps an in-flight request', async () => {
    let resolveRequest: (() => void) | undefined
    const poll = vi.fn(() => new Promise<void>((resolve) => { resolveRequest = resolve }))
    const controller = createRuntimeDiagnosisPolling({ shouldPoll: () => true, poll })

    controller.start()
    await vi.runAllTicks()
    await vi.advanceTimersByTimeAsync(15_000)
    expect(poll).toHaveBeenCalledTimes(1)

    controller.stop()
    resolveRequest?.()
    await controller.awaitIdle()
    await vi.advanceTimersByTimeAsync(10_000)
    expect(poll).toHaveBeenCalledTimes(1)
  })

  it('does not start when the page is hidden', () => {
    const poll = vi.fn().mockResolvedValue(undefined)
    const controller = createRuntimeDiagnosisPolling({ shouldPoll: () => false, poll })

    controller.start()
    vi.advanceTimersByTime(10_000)
    expect(poll).not.toHaveBeenCalled()
  })
})

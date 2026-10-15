export interface RuntimeDiagnosisPollingOptions {
  intervalMs?: number
  shouldPoll: () => boolean
  poll: () => Promise<void>
}

/**
 * Creates a small generation guard for views that can issue overlapping requests.
 * Only the newest generation may publish its response into reactive state.
 */
export function createLatestRequestGuard() {
  let generation = 0

  return {
    next: () => ++generation,
    isCurrent: (candidate: number) => candidate === generation,
    invalidate: () => { generation++ }
  }
}

export function createRuntimeDiagnosisPolling(options: RuntimeDiagnosisPollingOptions) {
  const intervalMs = options.intervalMs ?? 5_000
  let timer: ReturnType<typeof setInterval> | undefined
  let inFlight: Promise<void> | undefined

  function run() {
    if (!options.shouldPoll() || inFlight) return inFlight ?? Promise.resolve()
    const request = Promise.resolve()
      .then(options.poll)
      .catch(() => undefined)
      .finally(() => {
        if (inFlight === request) inFlight = undefined
      })
    inFlight = request
    return request
  }

  function start() {
    if (timer !== undefined || !options.shouldPoll()) return
    void run()
    timer = setInterval(() => { void run() }, intervalMs)
  }

  function stop() {
    if (timer !== undefined) clearInterval(timer)
    timer = undefined
  }

  async function awaitIdle() {
    if (inFlight) await inFlight
  }

  return { start, stop, run, awaitIdle }
}

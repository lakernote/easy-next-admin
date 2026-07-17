import { describe, expect, it } from 'vitest'
import { TRACE_ID_HEADER, createTraceHeaders } from './trace'

describe('trace headers', () => {
  it('creates only the unified trace header', () => {
    const headers = createTraceHeaders()

    expect(Object.keys(headers)).toEqual([TRACE_ID_HEADER])
    expect(headers[TRACE_ID_HEADER]).toBeTruthy()
  })
})

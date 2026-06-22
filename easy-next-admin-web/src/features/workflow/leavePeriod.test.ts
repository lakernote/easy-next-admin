import { describe, expect, it } from 'vitest'
import {
  calculateLeaveDaysFromRange,
  calculateLeaveEndTimeFromDays,
  normalizeLeaveDays
} from './leavePeriod'

describe('leave period calculations', () => {
  it('calculates end time from natural-day duration with half-day precision', () => {
    expect(calculateLeaveEndTimeFromDays('2026-05-27 09:00:00', 5)).toBe('2026-05-31 18:00:00')
    expect(calculateLeaveEndTimeFromDays('2026-05-27 09:00:00', 6.5)).toBe('2026-06-02 12:00:00')
    expect(calculateLeaveEndTimeFromDays('2026-05-27 13:00:00', 1)).toBe('2026-05-28 12:00:00')
  })

  it('recalculates leave days when end time is moved beyond the selected shortcut', () => {
    expect(calculateLeaveDaysFromRange('2026-05-27 09:00:00', '2026-06-03 18:00:00')).toBe(8)
    expect(calculateLeaveDaysFromRange('2026-05-27 09:00:00', '2026-06-03 12:00:00')).toBe(7.5)
  })

  it('normalizes manual day input to a half-day value', () => {
    expect(normalizeLeaveDays(0.1)).toBe(0.5)
    expect(normalizeLeaveDays(6.7)).toBe(6.5)
  })

  it('rejects invalid time ranges', () => {
    expect(calculateLeaveDaysFromRange('2026-05-27 09:00:00', '2026-05-27 09:00:00')).toBeNull()
    expect(calculateLeaveDaysFromRange('bad', '2026-05-27 18:00:00')).toBeNull()
  })
})

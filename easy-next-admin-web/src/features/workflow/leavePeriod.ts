const HALF_DAY_STEP = 0.5

export function normalizeLeaveDays(value: number) {
  if (!Number.isFinite(value)) return HALF_DAY_STEP
  return Math.max(HALF_DAY_STEP, Math.round(value / HALF_DAY_STEP) * HALF_DAY_STEP)
}

export function calculateLeaveEndTimeFromDays(startTime: string, days: number) {
  const start = parseLeaveDateTime(startTime)
  if (!start) return ''

  const halfDayCount = Math.round(normalizeLeaveDays(days) / HALF_DAY_STEP)
  const startHalf = isAfternoonStart(start) ? 1 : 0
  const endHalfOffset = startHalf + halfDayCount - 1
  const end = new Date(start)
  end.setDate(start.getDate() + Math.floor(endHalfOffset / 2))
  end.setHours(endHalfOffset % 2 === 0 ? 12 : 18, 0, 0, 0)
  return formatLeaveDateTime(end)
}

export function calculateLeaveDaysFromRange(startTime: string, endTime: string) {
  const start = parseLeaveDateTime(startTime)
  const end = parseLeaveDateTime(endTime)
  if (!start || !end || end.getTime() <= start.getTime()) return null

  const dayOffset = calendarDayOffset(start, end)
  const startHalf = isAfternoonStart(start) ? 1 : 0
  const endHalf = isMorningEnd(end) ? 0 : 1
  const halfDayCount = dayOffset * 2 + endHalf - startHalf + 1
  if (halfDayCount <= 0) return null
  return normalizeLeaveDays(halfDayCount * HALF_DAY_STEP)
}

export function formatLeaveDateTime(value: Date, hour?: number) {
  const next = new Date(value)
  if (hour !== undefined) next.setHours(hour, 0, 0, 0)
  const pad = (item: number) => String(item).padStart(2, '0')
  return `${next.getFullYear()}-${pad(next.getMonth() + 1)}-${pad(next.getDate())} ${pad(next.getHours())}:${pad(next.getMinutes())}:00`
}

function parseLeaveDateTime(value: string) {
  const match = /^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})(?::(\d{2}))?$/.exec(value)
  if (!match) return null
  const [, year, month, day, hour, minute, second = '0'] = match
  const parsed = new Date(Number(year), Number(month) - 1, Number(day), Number(hour), Number(minute), Number(second), 0)
  if (Number.isNaN(parsed.getTime())) return null
  return parsed
}

function calendarDayOffset(start: Date, end: Date) {
  const startDay = Date.UTC(start.getFullYear(), start.getMonth(), start.getDate())
  const endDay = Date.UTC(end.getFullYear(), end.getMonth(), end.getDate())
  return Math.round((endDay - startDay) / 86_400_000)
}

function isAfternoonStart(value: Date) {
  return value.getHours() >= 12
}

function isMorningEnd(value: Date) {
  return value.getHours() < 12 || (value.getHours() === 12 && value.getMinutes() === 0 && value.getSeconds() === 0)
}

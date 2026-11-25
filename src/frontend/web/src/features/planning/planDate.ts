declare const planDateBrand: unique symbol

/**
 * A delivery date as URLs and the API carry it, `YYYY-MM-DD`, known to be a real calendar date.
 * Only {@link isPlanDate} and {@link tomorrowInIsrael} make one.
 */
export type PlanDate = string & { readonly [planDateBrand]: true }

/** The zone every date and time of DispatchFlow is in (ADR-088). */
export const ISRAEL_TIME_ZONE = 'Asia/Jerusalem'

const PLAN_DATE_FORMAT = /^(\d{4})-(\d{2})-(\d{2})$/

/** Reads an instant's calendar date in Israel, whatever the zone of the computer it runs on. */
const israelCalendarDate = new Intl.DateTimeFormat('en-CA', {
  timeZone: ISRAEL_TIME_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})

/**
 * Tells whether a text is a plan date: `YYYY-MM-DD`, and a day the calendar has, so `2026-02-30`
 * and `2027-02-29` are not.
 */
export function isPlanDate(text: string): text is PlanDate {
  const match = PLAN_DATE_FORMAT.exec(text)
  if (match === null) {
    return false
  }
  const [year, month, day] = match.slice(1).map(Number)
  // Date.UTC rolls an impossible day over into the next month, so a real date round-trips.
  const date = new Date(Date.UTC(year, month - 1, day))
  return (
    date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day
  )
}

/**
 * The day after an instant's date in Israel: the date a dispatcher plans. At 00:30 on 26 November
 * in Israel, which is 22:30 UTC on the 25th, it is 27 November.
 */
export function tomorrowInIsrael(now: Date): PlanDate {
  const parts = new Map(
    israelCalendarDate.formatToParts(now).map((part) => [part.type, part.value]),
  )
  const tomorrow = new Date(
    Date.UTC(
      Number(parts.get('year')),
      Number(parts.get('month')) - 1,
      Number(parts.get('day')) + 1,
    ),
  )
  // The one place a PlanDate is made without isPlanDate: a UTC midnight's ISO date is always real.
  return tomorrow.toISOString().slice(0, 10) as PlanDate
}

/**
 * Writes a plan date as DD/MM/YYYY, the same in both languages (NFR-68, ADR-089). #173's
 * `@dispatchflow/formatters` takes this over for every date on every screen.
 */
export function formatPlanDate(date: PlanDate): string {
  const [year, month, day] = date.split('-')
  return `${day}/${month}/${year}`
}

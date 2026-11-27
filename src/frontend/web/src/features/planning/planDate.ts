import { isLocalDate, israelDateOf } from '@dispatchflow/formatters'

declare const planDateBrand: unique symbol

/**
 * A delivery date as URLs and the API carry it, `YYYY-MM-DD`, known to be a real calendar date.
 * Only {@link isPlanDate} and {@link tomorrowInIsrael} make one.
 */
export type PlanDate = string & { readonly [planDateBrand]: true }

/**
 * Tells whether a text is a plan date: `YYYY-MM-DD`, and a day the calendar has, so `2026-02-30`
 * and `2027-02-29` are not.
 */
export function isPlanDate(text: string): text is PlanDate {
  return isLocalDate(text)
}

/**
 * The day after an instant's date in Israel: the date a dispatcher plans. At 00:30 on 26 November
 * in Israel, which is 22:30 UTC on the 25th, it is 27 November.
 */
export function tomorrowInIsrael(now: Date): PlanDate {
  const [year, month, day] = israelDateOf(now).split('-').map(Number)
  const tomorrow = new Date(Date.UTC(year, month - 1, day + 1))
  // The one place a PlanDate is made without isPlanDate: a UTC midnight's ISO date is always real.
  return tomorrow.toISOString().slice(0, 10) as PlanDate
}

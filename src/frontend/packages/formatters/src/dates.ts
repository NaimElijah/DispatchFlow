/**
 * The zone every date and time of DispatchFlow is in, whatever the zone of the device showing it
 * (ADR-088).
 */
export const ISRAEL_TIME_ZONE = 'Asia/Jerusalem'

const LOCAL_DATE = /^(\d{4})-(\d{2})-(\d{2})$/
const LOCAL_TIME = /^([01]\d|2[0-3]):([0-5]\d)(?::[0-5]\d)?$/

/**
 * An instant's calendar date and clock time in Israel. `en-GB` only fixes the digits; the parts are
 * assembled by hand below, so the output is the same in both interface languages (NFR-68).
 */
const israelClock = new Intl.DateTimeFormat('en-GB', {
  timeZone: ISRAEL_TIME_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})

interface IsraelParts {
  readonly year: string
  readonly month: string
  readonly day: string
  readonly hour: string
  readonly minute: string
}

/** @throws RangeError when the instant is an invalid `Date` */
function israelPartsOf(instant: Date): IsraelParts {
  if (Number.isNaN(instant.getTime())) {
    throw new RangeError('An invalid Date has no date or time in Israel')
  }
  const parts = new Map(israelClock.formatToParts(instant).map((part) => [part.type, part.value]))
  const part = (type: Intl.DateTimeFormatPartTypes): string => {
    const value = parts.get(type)
    if (value === undefined) {
      throw new Error(`Intl gave no ${type} for an instant in ${ISRAEL_TIME_ZONE}`)
    }
    return value
  }
  return {
    year: part('year'),
    month: part('month'),
    day: part('day'),
    hour: part('hour'),
    minute: part('minute'),
  }
}

/**
 * Tells whether a text is a calendar date as the API and the app's addresses carry it,
 * `YYYY-MM-DD`, and a day the calendar has: `2026-02-30` and `2027-02-29` are not.
 */
export function isLocalDate(text: string): boolean {
  const match = LOCAL_DATE.exec(text)
  if (match === null) {
    return false
  }
  const [year, month, day] = match.slice(1).map(Number)
  // Date.UTC rolls an impossible day over into the next month, so only a real date round-trips.
  const date = new Date(Date.UTC(year, month - 1, day))
  return (
    date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day
  )
}

/** Tells whether a text is a time of day as the API carries it: `HH:mm` or `HH:mm:ss`, 00 to 23. */
export function isLocalTime(text: string): boolean {
  return LOCAL_TIME.test(text)
}

/**
 * Writes an Israel-local calendar date, `YYYY-MM-DD` as the API sends it, as DD/MM/YYYY, the same
 * in both languages (NFR-68, ADR-089).
 *
 * @throws RangeError when the text is not a real `YYYY-MM-DD` date
 */
export function formatLocalDate(date: string): string {
  if (!isLocalDate(date)) {
    throw new RangeError(`"${date}" is not a YYYY-MM-DD calendar date`)
  }
  const [year, month, day] = date.split('-')
  return `${day}/${month}/${year}`
}

/**
 * Writes an Israel-local time of day, `HH:mm` or `HH:mm:ss` as the API sends it, as `HH:mm` on a
 * 24-hour clock (NFR-68).
 *
 * @throws RangeError when the text is not such a time
 */
export function formatLocalTime(time: string): string {
  if (!isLocalTime(time)) {
    throw new RangeError(`"${time}" is not an HH:mm or HH:mm:ss time of day`)
  }
  return time.slice(0, 5)
}

/**
 * Writes a time window's two ends as `08:00–12:00`.
 *
 * @throws RangeError when either end is not an `HH:mm` or `HH:mm:ss` time of day
 */
export function formatTimeRange(start: string, end: string): string {
  return `${formatLocalTime(start)}–${formatLocalTime(end)}`
}

/**
 * The calendar date an instant falls on in Israel, as `YYYY-MM-DD`: at 00:30 on 26 November in
 * Israel, which is 22:30 UTC on the 25th, it is `2026-11-26`.
 *
 * @throws RangeError when the instant is an invalid `Date`
 */
export function israelDateOf(instant: Date): string {
  const { year, month, day } = israelPartsOf(instant)
  return `${year}-${month}-${day}`
}

/**
 * Writes the date an instant falls on in Israel as DD/MM/YYYY.
 *
 * @throws RangeError when the instant is an invalid `Date`
 */
export function formatInstantDate(instant: Date): string {
  const { year, month, day } = israelPartsOf(instant)
  return `${day}/${month}/${year}`
}

/**
 * Writes an instant's time in Israel as `HH:mm` on a 24-hour clock.
 *
 * @throws RangeError when the instant is an invalid `Date`
 */
export function formatInstantTime(instant: Date): string {
  const { hour, minute } = israelPartsOf(instant)
  return `${hour}:${minute}`
}

/**
 * Writes an instant in Israel as `DD/MM/YYYY HH:mm`.
 *
 * @throws RangeError when the instant is an invalid `Date`
 */
export function formatInstantDateTime(instant: Date): string {
  return `${formatInstantDate(instant)} ${formatInstantTime(instant)}`
}

/** An instant as a delivery date's screens show it: its time, and its date only when it differs. */
export interface TimeOnDate {
  /** The time in Israel, `HH:mm`. */
  readonly time: string
  /** The instant's own date in Israel, DD/MM/YYYY, when it is not the delivery date. */
  readonly date: string | undefined
}

/**
 * Writes an instant beside a delivery date's other times: a planned arrival can fall past the
 * date's end, and its time alone would then read as the morning of the delivery date. The list and
 * the map both show times through this, so they agree. A screen joins the two parts with its own
 * translated words.
 *
 * @param instant the instant to show
 * @param deliveryDate the delivery date the screen is about, `YYYY-MM-DD`
 * @throws RangeError when the instant is an invalid `Date` or the delivery date is not a date
 */
export function formatInstantTimeOn(instant: Date, deliveryDate: string): TimeOnDate {
  if (!isLocalDate(deliveryDate)) {
    throw new RangeError(`"${deliveryDate}" is not a YYYY-MM-DD calendar date`)
  }
  const time = formatInstantTime(instant)
  return israelDateOf(instant) === deliveryDate
    ? { time, date: undefined }
    : { time, date: formatInstantDate(instant) }
}

import { isLocalDate, isLocalTime } from '@dispatchflow/formatters'
import { isValidPhoneNumber } from 'libphonenumber-js/min'
import { z } from './zod.ts'
import { refusal } from './refusals.ts'

// A check that would refuse again what an earlier one refused, such as a format check after an
// empty value, sits behind an `abort`, so a field shows one refusal at a time.

/** Phones are entered as Israeli numbers: a local number reads with Israel's country code. */
const PHONE_REGION = 'IL'

/** Weights and volumes carry at most two decimals (DL §3.3). */
const HUNDREDTHS = 100

/** The tolerance for reading a float's hundredths, below any amount a person can enter. */
const FLOAT_TOLERANCE = 1e-9

const isFilled = (text: string) => text !== ''

/**
 * A text's length as the server counts it: in code points, after NFC (ADR-106), so an emoji counts
 * once, as the server's `PlainText` counts it, and the app never refuses what the server takes.
 */
const lengthOf = (text: string) => [...text.normalize('NFC')].length

/** An empty text means none: the request body leaves the field out. */
const noneWhenEmpty = (text: string) => (isFilled(text) ? text : undefined)

/**
 * A required text, trimmed, of at most `maxLength` characters after trimming.
 *
 * @param maxLength the most characters the server's row allows, such as 100 for a name
 */
export function requiredText(maxLength: number) {
  return z
    .string()
    .trim()
    .refine(isFilled, { ...refusal('REQUIRED'), abort: true })
    .refine((text) => lengthOf(text) <= maxLength, refusal('TEXT_TOO_LONG', { maxLength }))
}

/**
 * An optional text, trimmed, of at most `maxLength` characters. Empty means none, and leaves the
 * field out of the request body.
 *
 * @param maxLength the most characters the server's row allows, such as 500 for a note
 */
export function optionalText(maxLength: number) {
  return z
    .string()
    .trim()
    .refine((text) => lengthOf(text) <= maxLength, refusal('TEXT_TOO_LONG', { maxLength }))
    .transform(noneWhenEmpty)
}

/** A required choice from a select field: the chosen option's value. */
export const choice = z.string().refine(isFilled, refusal('REQUIRED'))

/** A calendar date, `YYYY-MM-DD`, as the date field gives it and the API takes it. */
export const localDate = z
  .string()
  .refine(isFilled, { ...refusal('REQUIRED'), abort: true })
  .refine(isLocalDate, refusal('WRONG_FORMAT'))

/**
 * A time of day on a 24-hour clock: `HH:mm` as the time field gives it, or `HH:mm:ss` as a record
 * read from the API may hold it until the user changes it.
 */
export const timeOfDay = z
  .string()
  .refine(isFilled, { ...refusal('REQUIRED'), abort: true })
  .refine(isLocalTime, refusal('WRONG_FORMAT'))

/** A time window's two ends, each `HH:mm` or `HH:mm:ss`. */
interface WindowEnds {
  readonly start: string
  readonly end: string
}

const SECONDS_PER_HOUR = 3600
const SECONDS_PER_MINUTE = 60

/** A checked time of day's seconds since midnight, with or without its seconds written. */
function secondsOf(time: string): number {
  const [hours, minutes, seconds = '0'] = time.split(':')
  return Number(hours) * SECONDS_PER_HOUR + Number(minutes) * SECONDS_PER_MINUTE + Number(seconds)
}

// Compared as times, never as texts: `09:30:00` sorts after `09:30`, though it is the same time.
const endsAfterStart = ({ start, end }: WindowEnds) => secondsOf(end) > secondsOf(start)

/**
 * A required time window: both ends, and the end after the start (BR-35). The order check's refusal
 * sits on the window itself, so the field shows it once for the pair.
 */
export const timeWindow = z
  .object({ start: timeOfDay, end: timeOfDay })
  .refine(endsAfterStart, refusal('INVALID_TIME_WINDOW'))

/**
 * An optional time window: both ends or neither, where neither is two empty texts and means none,
 * such as an order's "the customer's default", and leaves the window out of the request body.
 */
export const optionalTimeWindow = z
  .object({ start: z.string(), end: z.string() })
  .refine(({ start, end }) => isFilled(start) === isFilled(end), {
    ...refusal('INCOMPLETE_TIME_WINDOW'),
    abort: true,
  })
  .refine(({ start, end }) => !isFilled(start) || (isLocalTime(start) && isLocalTime(end)), {
    ...refusal('WRONG_FORMAT'),
    abort: true,
  })
  .refine((window) => !isFilled(window.start) || endsAfterStart(window), {
    ...refusal('INVALID_TIME_WINDOW'),
  })
  .transform((window) => (isFilled(window.start) ? window : undefined))

function hasAtMostTwoDecimals(value: number): boolean {
  const hundredths = value * HUNDREDTHS
  return Math.abs(hundredths - Math.round(hundredths)) < FLOAT_TOLERANCE
}

/** A weight or volume: a positive number with at most two decimals (BR-50). */
function quantity() {
  return z
    .number()
    .refine((value) => value > 0, { ...refusal('NON_POSITIVE_QUANTITY'), abort: true })
    .refine(hasAtMostTwoDecimals, refusal('WRONG_FORMAT'))
}

/** A weight in kilograms. */
export const weightKg = quantity()

/** A volume in litres. */
export const volumeLitres = quantity()

/**
 * A whole number from `min` to `max`, both included, such as an order's priority, 1 to 5.
 *
 * @param min the smallest value allowed
 * @param max the largest value allowed
 */
export function wholeNumber(min: number, max: number) {
  return z
    .number()
    .refine(Number.isInteger, { ...refusal('WRONG_FORMAT'), abort: true })
    .refine((value) => value >= min, { ...refusal('TOO_SMALL', { min }), abort: true })
    .refine((value) => value <= max, refusal('TOO_LARGE', { max }))
}

/** A duration in whole minutes, more than zero, such as a stop's service time. */
export const durationMinutes = z
  .number()
  .refine(Number.isInteger, { ...refusal('WRONG_FORMAT'), abort: true })
  .refine((value) => value > 0, refusal('TOO_SMALL', { min: 1 }))

const isPhoneNumber = (text: string) => isValidPhoneNumber(text, PHONE_REGION)

/**
 * A required phone number, as typed: a local Israeli number or an international one. The server
 * parses it again and stores it as E.164 (FR-377).
 */
export const phoneNumber = z
  .string()
  .trim()
  .refine(isFilled, { ...refusal('REQUIRED'), abort: true })
  .refine(isPhoneNumber, refusal('INVALID_PHONE_NUMBER'))

/** An optional phone number. Empty means none, and leaves the field out of the request body. */
export const optionalPhoneNumber = z
  .string()
  .trim()
  .refine((text) => !isFilled(text) || isPhoneNumber(text), refusal('INVALID_PHONE_NUMBER'))
  .transform(noneWhenEmpty)

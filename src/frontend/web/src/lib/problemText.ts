import {
  formatInstantDateTime,
  formatLocalDate,
  formatLocalTime,
  isLocalDate,
  isLocalTime,
} from '@dispatchflow/formatters'
import { resources } from '@dispatchflow/i18n'
import type { TFunction } from 'i18next'

/** A refusal code with its text and next step in `@dispatchflow/i18n`'s `problems` namespace. */
export type ProblemCode = keyof (typeof resources)['en']['problems']['codes']

const PROBLEM_CODES: ReadonlySet<string> = new Set(Object.keys(resources.en.problems.codes))

/** Tells whether a code has its own text; any other is shown with the general one (NFR-143). */
export function isProblemCode(code: string): code is ProblemCode {
  return PROBLEM_CODES.has(code)
}

/** An instant as the API writes one, with its offset; a date or time without one is Israel's. */
const INSTANT = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d+)?)?(?:Z|[+-]\d{2}:\d{2})$/

/** A value as a code's text shows it: Israel's dates and times as every screen writes them. */
function shownValue(value: unknown): string | number | undefined {
  if (typeof value === 'number') {
    return value
  }
  if (typeof value !== 'string') {
    // An ID list, a nested refusal or a variant has no place in a sentence; the code's own text
    // names only plain values (contracts/client-api/v1, Problem.params).
    return undefined
  }
  if (isLocalDate(value)) {
    return formatLocalDate(value)
  }
  if (isLocalTime(value)) {
    return formatLocalTime(value)
  }
  if (INSTANT.test(value)) {
    // The pattern lets an impossible instant through, such as month 13; it shows as sent.
    const instant = new Date(value)
    return Number.isNaN(instant.getTime()) ? value : formatInstantDateTime(instant)
  }
  return value
}

function shownParams(params: Readonly<Record<string, unknown>>): Record<string, string | number> {
  const shown: Record<string, string | number> = {}
  for (const [name, value] of Object.entries(params)) {
    const text = shownValue(value)
    if (text !== undefined) {
      shown[name] = text
    }
  }
  return shown
}

/** What a refusal says: what happened, and what the user can do next (NFR-69). */
export interface ProblemText {
  readonly text: string
  readonly nextStep: string
}

/**
 * The text and next step of a refusal code, its `params` filled in, in the interface language. A
 * code with no text of its own gets the general text, and never shows itself (NFR-143).
 *
 * @param t the `problems` namespace's translator
 * @param code the refusal's code
 * @param params the values its text needs, as the server or a schema gave them
 */
export function problemText(
  t: TFunction<'problems'>,
  code: string,
  params: Readonly<Record<string, unknown>> = {},
): ProblemText {
  if (!isProblemCode(code)) {
    return { text: t('unknown.text'), nextStep: t('unknown.nextStep') }
  }
  // The values go under `replace`, never as the options themselves: a param named like one of
  // i18next's options, such as `lng` or `returnDetails`, would otherwise change the lookup.
  const replace = shownParams(params)
  return {
    text: t(`codes.${code}.text`, { replace }),
    nextStep: t(`codes.${code}.nextStep`, { replace }),
  }
}

/** A refusal in one line, as a field shows it under its input: its text, then its next step. */
export function fieldErrorText(
  t: TFunction<'problems'>,
  code: string,
  params: Readonly<Record<string, unknown>> = {},
): string {
  const { text, nextStep } = problemText(t, code, params)
  return `${text} ${nextStep}`
}

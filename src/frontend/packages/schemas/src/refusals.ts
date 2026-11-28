import type { z } from 'zod'

/**
 * The codes a schema's checks raise, the one list of them: each is also a code the server answers,
 * or one of the app's own, and each has its text and next step in `@dispatchflow/i18n`'s `problems`
 * namespace, so a field shows the same words whether the app or the server refused the value. The
 * web app's `problemText.test.ts` fails a code that has no text there.
 */
export const schemaCodes = [
  'REQUIRED',
  'WRONG_FORMAT',
  'TOO_SMALL',
  'TOO_LARGE',
  'INVALID_VALUE',
  'TEXT_TOO_LONG',
  'INVALID_TIME_WINDOW',
  'INCOMPLETE_TIME_WINDOW',
  'INVALID_PHONE_NUMBER',
  'NON_POSITIVE_QUANTITY',
] as const

/** One of {@link schemaCodes}. */
export type SchemaCode = (typeof schemaCodes)[number]

/** A value a schema refused: its code, and the values the code's text needs. */
export interface SchemaRefusal {
  readonly code: SchemaCode
  readonly params: Readonly<Record<string, unknown>>
}

/**
 * The `params` of a check that names its refusal: what `.refine(check, refusal(...))` takes. A
 * check never sets a message: the app translates the code (see `packages/schemas/CLAUDE.md`).
 */
export function refusal(code: SchemaCode, params: Record<string, unknown> = {}) {
  return { params: { ...params, code } }
}

const SCHEMA_CODES: ReadonlySet<string> = new Set<string>(schemaCodes)

function isSchemaCode(value: unknown): value is SchemaCode {
  return typeof value === 'string' && SCHEMA_CODES.has(value)
}

/**
 * Reads the refusal an issue stands for, for an error map to translate. A check of this package
 * names its code in its `params`; Zod's own checks, which a feature's schema may also use, map onto
 * the same codes: a missing value, a string under one character and an empty choice are `REQUIRED`.
 *
 * @param issue an issue as Zod hands it to an error map
 * @throws Error when a custom check names no {@link SchemaCode}: it was written without `refusal()`
 */
export function refusalOf(issue: z.core.$ZodRawIssue): SchemaRefusal {
  switch (issue.code) {
    case 'custom': {
      const { code, ...params } = issue.params ?? {}
      if (!isSchemaCode(code)) {
        throw new Error(
          `A custom check raised no code of @dispatchflow/schemas (${String(code)}): use refusal()`,
        )
      }
      return { code, params }
    }
    case 'invalid_type':
      return {
        code: issue.input === undefined || issue.input === null ? 'REQUIRED' : 'WRONG_FORMAT',
        params: {},
      }
    case 'too_small':
      if (issue.origin === 'string' && Number(issue.minimum) <= 1) {
        return { code: 'REQUIRED', params: {} }
      }
      return { code: 'TOO_SMALL', params: { min: issue.minimum } }
    case 'too_big':
      return issue.origin === 'string'
        ? { code: 'TEXT_TOO_LONG', params: { maxLength: issue.maximum } }
        : { code: 'TOO_LARGE', params: { max: issue.maximum } }
    case 'invalid_value':
      return { code: 'INVALID_VALUE', params: {} }
    // Zod's other checks read as a wrong format. They are named, not left to a default, so a code a
    // later Zod adds fails the lint until it is mapped (switch-exhaustiveness-check).
    case 'invalid_format':
    case 'invalid_element':
    case 'invalid_key':
    case 'invalid_union':
    case 'not_multiple_of':
    case 'unrecognized_keys':
      return { code: 'WRONG_FORMAT', params: {} }
  }
}

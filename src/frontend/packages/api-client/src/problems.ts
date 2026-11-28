import type { components } from './generated/client-api.ts'

/**
 * A refusal as the core sends it: the contract's `Problem`, RFC 9457 problem details with a stable
 * `code`, the `params` its text needs and the `traceId` (ADR-043).
 */
export type Problem = components['schemas']['Problem']

/** One failing item of a problem: a field, a parameter, or the request as a whole. */
export type ProblemItem = components['schemas']['ProblemItem']

/** The parameter a failing item names, and where the request carried it. */
export type ProblemParameter = components['schemas']['ProblemParameter']

/**
 * The form of a problem's `code`, which the contract states as a pattern. The generated types
 * carry no pattern, so it lives here, and `problems.contract.test.ts` holds it to the contract.
 */
export const PROBLEM_CODE_PATTERN = /^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$/

/** The form of a problem's `traceId`, a W3C trace-id, held to the contract as the code's is. */
export const TRACE_ID_PATTERN = /^[0-9a-f]{32}$/

/**
 * Every place a failing parameter can be in, as a value a guard can test. The generated union
 * types it, and `problems.contract.test.ts` checks it lists each.
 */
export const PARAMETER_PLACES = [
  'path',
  'query',
  'header',
  'cookie',
] as const satisfies readonly ProblemParameter['in'][]

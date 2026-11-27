/**
 * A refusal as the core sends it: the contract's `Problem` (contracts/client-api/v1, ADR-043), RFC
 * 9457 problem details with a stable `code`, the `params` its text needs and the `traceId`. These
 * types are written by hand until `@dispatchflow/api-client` generates them (#148, ADR-153);
 * `problems.contract.test.ts` holds them to the contract meanwhile.
 */
export interface Problem {
  readonly type: string
  readonly title: string
  readonly status: number
  readonly code: string
  readonly params: Readonly<Record<string, unknown>>
  readonly traceId: string
  readonly instance?: string
  /** A 400's failing items, or each refusal of a 422 that lists several. */
  readonly errors?: readonly ProblemItem[]
}

/** One failing item of a problem: a field, a parameter, or the request as a whole. */
export interface ProblemItem {
  readonly code: string
  /** An RFC 6901 JSON Pointer into the request body, on a 400 only; empty for the whole body. */
  readonly pointer?: string
  readonly parameter?: ProblemParameter
  readonly params?: Readonly<Record<string, unknown>>
}

/**
 * The places a failing parameter can be in (`ProblemParameter.in`), which
 * `problems.contract.test.ts` pins.
 */
export const PARAMETER_PLACES = ['path', 'query', 'header', 'cookie'] as const

/** A failing parameter of a request: a header such as `If-Match`, a path or query parameter. */
export interface ProblemParameter {
  readonly name: string
  readonly in: (typeof PARAMETER_PLACES)[number]
}

/** The contract's pattern for a code (`Problem.code`), which `problems.contract.test.ts` pins. */
export const PROBLEM_CODE_PATTERN = /^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$/

/** The contract's pattern for a trace ID (`Problem.traceId`), which `problems.contract.test.ts` pins. */
export const TRACE_ID_PATTERN = /^[0-9a-f]{32}$/
const PARAMETER_PLACE_SET: ReadonlySet<unknown> = new Set(PARAMETER_PLACES)

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function isProblemItem(value: unknown): value is ProblemItem {
  if (
    !isRecord(value) ||
    typeof value.code !== 'string' ||
    !PROBLEM_CODE_PATTERN.test(value.code)
  ) {
    return false
  }
  const { pointer, parameter, params } = value
  return (
    (pointer === undefined || typeof pointer === 'string') &&
    (params === undefined || isRecord(params)) &&
    (parameter === undefined ||
      (isRecord(parameter) &&
        typeof parameter.name === 'string' &&
        PARAMETER_PLACE_SET.has(parameter.in)))
  )
}

/**
 * Tells whether an answer's body is a problem. What reaches the app is not always one: a proxy in
 * front of the core answers a 502 with a page of its own, and only a problem has a code to show.
 */
export function isProblem(value: unknown): value is Problem {
  if (!isRecord(value)) {
    return false
  }
  const { type, title, status, code, params, traceId, errors } = value
  return (
    typeof type === 'string' &&
    typeof title === 'string' &&
    typeof status === 'number' &&
    typeof code === 'string' &&
    PROBLEM_CODE_PATTERN.test(code) &&
    isRecord(params) &&
    typeof traceId === 'string' &&
    TRACE_ID_PATTERN.test(traceId) &&
    (errors === undefined || (Array.isArray(errors) && errors.every(isProblemItem)))
  )
}

/**
 * How a call to the core failed, as a screen acts on it:
 *
 * - `stale`: `STALE_VERSION`, someone else changed the record since it was read (ADR-057). The
 *   user's input is kept, and the screen offers a reload or refetches.
 * - `busy`: `TRY_AGAIN_LATER`, the core lost a race or a lock four times (ADR-121 decision 4).
 * - `refused`: the core refused the request, for a rule or a malformed request.
 * - `unexpected`: the core failed, something in front of it did, or its answer broke off. A
 *   write may be stored, so the screen reloads before the user sends it again; a read changed
 *   nothing, so the user reads again.
 * - `network`: no answer arrived, or the request could not be sent.
 */
export type RequestFailure =
  | { readonly kind: 'stale'; readonly problem: Problem }
  | { readonly kind: 'busy'; readonly problem: Problem | undefined }
  | { readonly kind: 'refused'; readonly problem: Problem }
  | { readonly kind: 'unexpected'; readonly problem: Problem | undefined }
  | { readonly kind: 'network' }

/** A call that failed, as a promise rejects with it: TanStack Query hands it to the screen. */
export class RequestError extends Error {
  readonly failure: RequestFailure

  /**
   * @param failure how the call failed
   * @param options the error that caused it, when one did, such as the network error
   */
  constructor(failure: RequestFailure, options?: ErrorOptions) {
    super(`The call to the core failed: ${failure.kind}`, options)
    this.name = 'RequestError'
    this.failure = failure
  }
}

const STALE_VERSION = 'STALE_VERSION'
const TRY_AGAIN_LATER = 'TRY_AGAIN_LATER'
const SERVICE_UNAVAILABLE = 503
const FIRST_SERVER_ERROR = 500

/**
 * Says how a call failed from its answer. The code decides before the status: `STALE_VERSION`
 * answers 412 for a stale `If-Match` and 409 for a version sent in the body, and both mean the same
 * to the user (ADR-140 decision 7).
 *
 * @param response the answer, whose status was not a success
 * @param body the answer's parsed body, which may be no problem at all
 */
export function toRequestFailure(response: Response, body: unknown): RequestFailure {
  if (!isProblem(body)) {
    return response.status === SERVICE_UNAVAILABLE
      ? { kind: 'busy', problem: undefined }
      : { kind: 'unexpected', problem: undefined }
  }
  if (body.code === STALE_VERSION) {
    return { kind: 'stale', problem: body }
  }
  if (body.code === TRY_AGAIN_LATER) {
    return { kind: 'busy', problem: body }
  }
  if (response.status >= FIRST_SERVER_ERROR) {
    return { kind: 'unexpected', problem: body }
  }
  return { kind: 'refused', problem: body }
}

import { RequestError, toRequestFailure } from './problems.ts'

/**
 * Fetches from the core as `fetch` does, except in how it fails:
 *
 * - A request that got no answer rejects with a {@link RequestError} whose failure is `network`.
 *   That covers any request fetch could not complete, one the Content Security Policy blocked
 *   included (ADR-069): fetch reports them all with the same `TypeError`.
 * - An answer that broke off while its body came in rejects as `unexpected`: the request arrived,
 *   so a write may be stored, and the user reloads rather than resends. fetch resolves once the
 *   headers are in, so this reads a copy of the body to its end before handing the answer on.
 * - A success whose body is in a media type the request's `Accept` doesn't name, such as a page
 *   that something in front of the core serves, rejects as `unexpected` too: the generated client
 *   would otherwise fail parsing it with a bare `SyntaxError`. A request that names no `Accept`
 *   takes any answer.
 * - Anything thrown before the request, such as a `TypeError` from a screen reading a missing
 *   record's ID, stays a bug and fails as itself, and an abort stays an abort.
 *
 * `coreClient.ts` hands it to the generated client as its `fetch` (ADR-161); a shared package never
 * imports it. It reads a body to its end, so never use it for the event stream.
 *
 * @throws RequestError when no answer arrived, the answer broke off, or a success came in a type
 *     the request didn't accept
 * @throws DOMException when the request was aborted, as fetch does
 */
export async function fetchFromCore(
  input: RequestInfo | URL,
  init?: RequestInit,
): Promise<Response> {
  let response: Response
  try {
    response = await fetch(input, init)
  } catch (error) {
    if (error instanceof TypeError) {
      throw new RequestError({ kind: 'network' }, { cause: error })
    }
    throw error
  }
  const body = await bodyOf(response)
  if (
    response.ok &&
    body.byteLength > 0 &&
    !isAccepted(acceptOf(input, init), response.headers.get('Content-Type'))
  ) {
    throw new RequestError({ kind: 'unexpected', problem: undefined })
  }
  return response
}

/** Reads a copy of the answer's body to its end, failing as `unexpected` if it breaks off. */
async function bodyOf(response: Response): Promise<ArrayBuffer> {
  try {
    return await response.clone().arrayBuffer()
  } catch (error) {
    if (error instanceof TypeError) {
      throw new RequestError({ kind: 'unexpected', problem: undefined }, { cause: error })
    }
    throw error
  }
}

/** The `Accept` a request sends: an init's headers replace the request's own, as in fetch. */
function acceptOf(input: RequestInfo | URL, init?: RequestInit): string | null {
  if (init?.headers !== undefined) {
    return new Headers(init.headers).get('Accept')
  }
  return input instanceof Request ? input.headers.get('Accept') : null
}

/** Whether an answer's media type is one the `Accept` names; a request that names none takes any. */
function isAccepted(accept: string | null, contentType: string | null): boolean {
  if (accept === null) {
    return true
  }
  const type = mediaTypeOf(contentType)
  return accept
    .split(',')
    .map(mediaTypeOf)
    .some(
      (range) =>
        range === '*/*' ||
        range === type ||
        (range.endsWith('/*') && type.startsWith(range.slice(0, -1))),
    )
}

/** A media type without its parameters, such as `application/json` for `application/json; charset=utf-8`. */
function mediaTypeOf(value: string | null): string {
  return (value ?? '').split(';')[0].trim().toLowerCase()
}

/**
 * What a call sends back, in openapi-fetch's shape, so `coreClient`'s call (ADR-161) is read as it
 * stands: `data` on a success, the parsed `error` body otherwise, and the answer.
 */
export interface SendResult<TData> {
  readonly response: Response
  readonly data?: TData
  readonly error?: unknown
}

/** A call's successful answer: what the core sent, and the record's `ETag`, for the next write. */
export interface Answer<TData> {
  readonly data: TData | undefined
  readonly etag: string | null
}

/**
 * Reads a call's answer, the one way every screen does, a read or a write: a success gives its data
 * and `ETag`, and anything else throws how the call failed, for TanStack Query to hand the screen.
 * A query keeps the whole answer, `queryFn: async () => answerOf(await client.GET(...))`, so the
 * screen has the view's `ETag` for its next save.
 *
 * @param result what the call sent back, as openapi-fetch gives it
 * @throws RequestError when the answer was no success
 */
export function answerOf<TData>({ response, data, error }: SendResult<TData>): Answer<TData> {
  if (!response.ok) {
    throw new RequestError(toRequestFailure(response, error))
  }
  return { data, etag: response.headers.get('ETag') }
}

/** The most times a read is tried again: TanStack Query's own default. */
const READ_RETRIES = 3

/**
 * Whether a failed read is tried again, as TanStack Query's `retry` asks: only when it failed on the
 * way, and at most three times. A read changes nothing, so trying it again is safe, and a failure on
 * the way passes: no answer arrived, the core was busy, or something in front of the core answered
 * or the answer broke off (`unexpected` with no problem). The core's own refusal or failure would
 * come back the same, and so would a bug, so the screen shows why at once rather than after seven
 * seconds of retries. `createQueryClient` uses it for every query; a write is never tried again.
 *
 * @param failureCount how many times the read has failed so far, less one
 * @param error how it failed this time
 */
export function retryRead(failureCount: number, error: Error): boolean {
  if (failureCount >= READ_RETRIES || !(error instanceof RequestError)) {
    return false
  }
  const { failure } = error
  return (
    failure.kind === 'network' ||
    failure.kind === 'busy' ||
    (failure.kind === 'unexpected' && failure.problem === undefined)
  )
}

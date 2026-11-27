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
 * - Anything thrown before the request, such as a `TypeError` from a screen reading a missing
 *   record's ID, stays a bug and fails as itself, and an abort stays an abort.
 *
 * The web app hands it to openapi-fetch as `fetch` when it builds its client (#148); a shared
 * package never imports it. It reads a body to its end, so never use it for the event stream.
 *
 * @throws RequestError when no answer arrived, or the answer broke off
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
  try {
    await response.clone().arrayBuffer()
  } catch (error) {
    if (error instanceof TypeError) {
      throw new RequestError({ kind: 'unexpected', problem: undefined }, { cause: error })
    }
    throw error
  }
  return response
}

/**
 * What a call sends back, in openapi-fetch's shape, so the generated client's call (#148) is read
 * as it stands: `data` on a success, the parsed `error` body otherwise, and the answer.
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

import { RequestError, toRequestFailure } from './problems.ts'

/**
 * Fetches from the core as `fetch` does, except that a request which got no answer rejects with a
 * {@link RequestError} whose failure is `network`. Only `fetch` itself can tell that no answer
 * arrived, so this is the one place that turns its rejection into one: a `TypeError` thrown before
 * the request, such as a screen reading a missing record's ID, stays a bug and fails as itself.
 * `@dispatchflow/api-client` passes it as openapi-fetch's `fetch` (#148), so every call, a read or
 * a write, reports a lost connection the same way.
 *
 * @throws RequestError when no answer arrived, which fetch reports with a `TypeError`
 * @throws DOMException when the request was aborted, as fetch does
 */
export async function fetchFromCore(
  input: RequestInfo | URL,
  init?: RequestInit,
): Promise<Response> {
  try {
    return await fetch(input, init)
  } catch (error) {
    if (error instanceof TypeError) {
      throw new RequestError({ kind: 'network' }, { cause: error })
    }
    throw error
  }
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
 * and `ETag`, and anything else throws how the call failed, for TanStack Query to hand the screen,
 * such as a query's `queryFn: async () => answerOf(await client.GET(...)).data`.
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

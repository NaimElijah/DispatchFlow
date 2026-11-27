import { useMutation, type UseMutationResult } from '@tanstack/react-query'
import { answerOf, type Answer, type SendResult } from './coreCalls.ts'

/**
 * The precondition a versioned write sends: the version the view was read at, as its `ETag`
 * (ADR-057), or none yet.
 *
 * `null` is what `response.headers.get('ETag')` gives for a view that has no version, such as the
 * fleet register before its first depot, and such a view's first change sends `If-None-Match: *`
 * (ADR-121 P31, as ADR-126 amends it). A read that forgot to pass its `ETag` fails safe: the core
 * answers `STALE_VERSION` when the record exists (ADR-140), and never overwrites it.
 *
 * @param etag the `ETag` the view was read with, sent back exactly as it arrived, quotes included
 */
export function preconditionHeaders(etag: string | null): Readonly<Record<string, string>> {
  return etag === null ? { 'If-None-Match': '*' } : { 'If-Match': etag }
}

/** One versioned write: the body, and the `ETag` of the view it changes, or `null` for none yet. */
export interface SaveRequest<TBody> {
  readonly body: TBody
  readonly etag: string | null
}

/** How {@link useVersionedSave} sends a write, and what it does once one is stored. */
export interface VersionedSaveOptions<TBody, TData> {
  /**
   * Sends the body with the precondition headers. It is the only part that knows the operation:
   * once the generated client exists, it is that client's call (#148, TS §10). It fetches through
   * `fetchFromCore`, as the web app's client will, so a call that failed rejects with a
   * `RequestError`; anything else it throws is a bug, and the save fails with it as it is.
   */
  readonly send: (
    body: TBody,
    headers: Readonly<Record<string, string>>,
  ) => Promise<SendResult<TData>>
  /**
   * Called once a write is stored, with the core's answer and the record's new `ETag`, such as to
   * keep the version and show a notification. It must not throw: the write is stored, and the
   * mutation would report the throw as the save's failure.
   */
  readonly onSaved?: (saved: Answer<TData>, request: SaveRequest<TBody>) => void
}

/**
 * A versioned write as a TanStack Query mutation: `mutate({ body, etag })` sends the body with the
 * view's version (BR-76), and resolves to the core's answer and the record's new `ETag`.
 *
 * A failed call rejects with a `RequestError` whose `failure` says what the screen shows, and a
 * bug in `send` rejects as itself, so the error is typed as any `Error`. Nothing is ever sent twice
 * on its own: a stale or refused write needs the user, and after an unexpected one the change may
 * already be stored. The mutation runs whether or not the browser thinks it is online, so a lost
 * connection fails at once and says so, rather than leaving the save pending.
 */
export function useVersionedSave<TBody, TData = unknown>({
  send,
  onSaved,
}: VersionedSaveOptions<TBody, TData>): UseMutationResult<
  Answer<TData>,
  Error,
  SaveRequest<TBody>
> {
  return useMutation<Answer<TData>, Error, SaveRequest<TBody>>({
    mutationFn: async ({ body, etag }) => answerOf(await send(body, preconditionHeaders(etag))),
    onSuccess: onSaved,
    retry: false,
    networkMode: 'always',
  })
}

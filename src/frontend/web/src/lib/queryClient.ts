import { QueryClient } from '@tanstack/react-query'
import { retryRead } from './coreCalls.ts'

/** What a test changes in the app's query cache. */
export interface QueryClientOptions {
  /**
   * The wait between a read's tries, in milliseconds. Tests wait none, since they wait for no
   * clock; the app keeps TanStack Query's own backoff, of one, two and four seconds.
   */
  readonly retryDelay?: number
}

/**
 * The app's one server-state cache, in memory only and never persisted (NFR-269, ADR-076), made
 * the same way by `main.tsx` and by the tests' helpers, so the two cannot drift apart. A read is
 * tried again only as `retryRead` allows; a write is never tried again.
 *
 * @param options what a test changes, such as no wait between a read's tries
 */
export function createQueryClient({ retryDelay }: QueryClientOptions = {}): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: retryRead, ...(retryDelay === undefined ? {} : { retryDelay }) },
    },
  })
}

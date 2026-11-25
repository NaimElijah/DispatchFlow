import type { QueryClient } from '@tanstack/react-query'
import { createRootRouteWithContext } from '@tanstack/react-router'
import type { LanguageSetting } from '../lib/languageSetting.ts'

/** What every route and page reaches through the router's context. */
export interface RouterContext {
  /**
   * The one server-state cache, in memory only (NFR-269). Sign-in clears it when a refresh is
   * refused or the session ends.
   */
  queryClient: QueryClient
  /** The current instant, injected so that a date such as tomorrow's plan is testable. */
  now: () => Date
  /** Where the interface language is kept until FR-124 stores it on the account. */
  languageSetting: LanguageSetting
}

/**
 * The root of every route. It draws nothing of its own: the shell is `shellRoute`'s, so a page that
 * must stand outside it, such as sign-in, sits here beside it (ADR-144).
 *
 * Its error and not-found pages are the router's defaults (`router.ts`): an error in the shell shows
 * the error page in the shell's place, from the shell route's own boundary. A `notFoundComponent`
 * here would catch a page's `notFound()` in place of the whole shell.
 */
export const rootRoute = createRootRouteWithContext<RouterContext>()({})

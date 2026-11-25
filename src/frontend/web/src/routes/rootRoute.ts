import type { QueryClient } from '@tanstack/react-query'
import { createRootRouteWithContext } from '@tanstack/react-router'
import type { LanguageSetting } from '../lib/languageSetting.ts'
import { ErrorPage } from './ErrorPage.tsx'
import { NotFoundPage } from './NotFoundPage.tsx'

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
 * must stand outside it, such as sign-in, sits here beside it. An error in the shell itself shows
 * {@link ErrorPage} here, in the shell's place.
 */
export const rootRoute = createRootRouteWithContext<RouterContext>()({
  notFoundComponent: NotFoundPage,
  errorComponent: ErrorPage,
})

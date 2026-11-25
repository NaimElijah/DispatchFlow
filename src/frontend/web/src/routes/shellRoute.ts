import { createRoute } from '@tanstack/react-router'
import { AppLayout } from './AppLayout.tsx'
import { NotFoundPage } from './NotFoundPage.tsx'
import { rootRoute } from './rootRoute.ts'

/**
 * The shell's layout route, with no path of its own (`_shell` is its ID, not a URL): every area's
 * routes are its children, so each page renders inside `AppLayout`'s header and navigation
 * (ADR-143), and sign-in guards this one route to guard every page. A page that finds its subject
 * missing throws the router's `notFound()`, which shows {@link NotFoundPage} inside the shell; an
 * address no route matches reaches `notFoundRoute`.
 */
export const shellRoute = createRoute({
  getParentRoute: () => rootRoute,
  id: '_shell',
  component: AppLayout,
  notFoundComponent: NotFoundPage,
})

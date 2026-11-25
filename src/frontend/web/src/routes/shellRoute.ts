import { createRoute } from '@tanstack/react-router'
import { AppLayout } from './AppLayout.tsx'
import { rootRoute } from './rootRoute.ts'

/**
 * The shell's layout route, with no path of its own (`_shell` is its ID, not a URL): every area's
 * routes are its children, so each page renders inside `AppLayout`'s header and navigation
 * (ADR-143), and sign-in guards this one route to guard every page (ADR-144).
 *
 * It sets no `notFoundComponent`: the router would show it in place of the whole layout. A loader
 * that throws `notFound()` gets the router's default not-found page in its own route's place, so
 * inside the shell; an address no route matches reaches `notFoundRoute`.
 */
export const shellRoute = createRoute({
  getParentRoute: () => rootRoute,
  id: '_shell',
  component: AppLayout,
})

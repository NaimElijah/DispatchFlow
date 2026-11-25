import { createRoute } from '@tanstack/react-router'
import { NotFoundPage } from './NotFoundPage.tsx'
import { shellRoute } from './shellRoute.ts'

/**
 * Every address no other route matches, shown inside the shell. The router tries a splat route
 * last, so this takes an unknown path, and also a plan date its route refused, such as
 * `/plans/2026-02-30`.
 */
export const notFoundRoute = createRoute({
  getParentRoute: () => shellRoute,
  path: '$',
  component: NotFoundPage,
})

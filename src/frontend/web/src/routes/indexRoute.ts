import { createRoute, redirect } from '@tanstack/react-router'
import { shellRoute } from './shellRoute.ts'

/**
 * `/` opens the planning screen, the dispatcher's main one, at `/plans`, which picks the date
 * (ADR-143). It replaces `/` in the history, so Back does not return to a redirect.
 */
export const indexRoute = createRoute({
  getParentRoute: () => shellRoute,
  path: '/',
  beforeLoad: () => {
    throw redirect({ to: '/plans', replace: true })
  },
})

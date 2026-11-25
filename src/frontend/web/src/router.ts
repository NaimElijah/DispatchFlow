import { createRouter, type RouterHistory } from '@tanstack/react-router'
import { ErrorPage } from './routes/ErrorPage.tsx'
import { NotFoundPage } from './routes/NotFoundPage.tsx'
import type { RouterContext } from './routes/rootRoute.ts'
import { routeTree } from './routeTree.ts'

/** What a router is made from: its context, and a history other than the browser's, for tests. */
export interface AppRouterOptions extends RouterContext {
  /** The history to navigate in; the browser's address bar when left out. */
  history?: RouterHistory
}

/**
 * Creates the web app's router over every route in `routeTree.ts`. A page that throws shows
 * {@link ErrorPage} in its own place, inside the shell, and a loader that throws the router's
 * `notFound()` shows {@link NotFoundPage} the same way. A page that throws `notFound()` while it
 * renders needs `notFoundComponent: NotFoundPage` on its own route: the router catches that only
 * there.
 *
 * The router's own global error boundary is off: its fallback offers to show the error's message
 * (NFR-143). `AppErrorBoundary`, around the whole app, takes its place.
 */
export function createAppRouter({ history, ...context }: AppRouterOptions) {
  return createRouter({
    routeTree,
    context,
    history,
    defaultErrorComponent: ErrorPage,
    defaultNotFoundComponent: NotFoundPage,
    disableGlobalCatchBoundary: true,
  })
}

/** The web app's router, whose type gives every `Link`, `redirect` and route hook its checks. */
export type AppRouter = ReturnType<typeof createAppRouter>

declare module '@tanstack/react-router' {
  interface Register {
    router: AppRouter
  }
}

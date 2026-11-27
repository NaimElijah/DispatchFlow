import type { QueryClient } from '@tanstack/react-query'
import { RouterProvider } from '@tanstack/react-router'
import type { i18n } from 'i18next'
import { AppErrorBoundary } from './AppErrorBoundary.tsx'
import { AppProviders } from './AppProviders.tsx'
import type { AppRouter } from './router.ts'

/** What the app runs on: `main.tsx` makes the browser's, and each test makes its own. */
export interface AppProps {
  /** The router, holding the routes and their context. */
  router: AppRouter
  /** The app's i18next instance, already set to the language to open in. */
  i18n: i18n
  /** The one server-state cache, in memory only and never persisted (NFR-269). */
  queryClient: QueryClient
}

/**
 * The web app's root: its providers, then the router, which renders the shell and every page,
 * all inside the last error boundary.
 */
export function App({ router, i18n, queryClient }: AppProps) {
  return (
    <AppErrorBoundary i18n={i18n}>
      <AppProviders i18n={i18n} queryClient={queryClient}>
        <RouterProvider router={router} />
      </AppProviders>
    </AppErrorBoundary>
  )
}

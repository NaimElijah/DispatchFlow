import { DirectionProvider, MantineProvider } from '@mantine/core'
import { Notifications } from '@mantine/notifications'
import { QueryClientProvider, type QueryClient } from '@tanstack/react-query'
import type { i18n } from 'i18next'
import type { ReactNode } from 'react'
import { I18nextProvider } from 'react-i18next'
import { DatesLanguage } from './DatesLanguage.tsx'
import { DocumentLanguage } from './DocumentLanguage.tsx'

/** What every screen of the app runs inside. */
export interface AppProvidersProps {
  /** The app's i18next instance, already set to the language to open in. */
  i18n: i18n
  /** The one server-state cache, in memory only and never persisted (NFR-269). */
  queryClient: QueryClient
  /** The router, or the one component a test renders. */
  children: ReactNode
}

/**
 * The providers every screen runs inside: the strings, the server-state cache, the direction, the
 * UI kit, its date pickers' language, and the save notifications. The app renders its router in
 * them, and a component's test renders that component in them, so the two can't drift apart.
 *
 * Mantine's run-time `<style>` elements are off, because ADR-069's Content Security Policy blocks
 * every one; `src/styles/theme.css` carries what they would hold (ADR-143). Forcing the light colour
 * scheme also keeps Mantine from storing a colour-scheme choice in the browser.
 */
export function AppProviders({ i18n, queryClient, children }: AppProvidersProps) {
  return (
    <I18nextProvider i18n={i18n}>
      <QueryClientProvider client={queryClient}>
        <DirectionProvider initialDirection={i18n.dir()}>
          <MantineProvider
            withCssVariables={false}
            withGlobalClasses={false}
            forceColorScheme="light"
          >
            <DatesLanguage>
              <DocumentLanguage />
              <Notifications />
              {children}
            </DatesLanguage>
          </MantineProvider>
        </DirectionProvider>
      </QueryClientProvider>
    </I18nextProvider>
  )
}

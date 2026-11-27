import type { Language } from '@dispatchflow/i18n'
import { QueryClient } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactElement } from 'react'
import { AppProviders } from '../AppProviders.tsx'
import { createI18n } from '../i18n.ts'

/** The language a component's test renders it in. */
export interface RenderComponentOptions {
  /** The interface language, English when left out. */
  language?: Language
}

/**
 * Renders one component inside the app's own providers, without the router, as a screen would
 * show it: in the given language and its direction, with a fresh query cache and i18next instance.
 *
 * @returns Testing Library's render result, with the i18next instance and the query cache
 */
export function renderComponent(
  ui: ReactElement,
  { language = 'en' }: RenderComponentOptions = {},
) {
  const i18n = createI18n(language)
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const result = render(
    <AppProviders i18n={i18n} queryClient={queryClient}>
      {ui}
    </AppProviders>,
  )
  return { ...result, i18n, queryClient }
}

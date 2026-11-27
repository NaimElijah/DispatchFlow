import { createMemoryHistory } from '@tanstack/react-router'
import { createQueryClient } from '../lib/queryClient.ts'
import { render } from '@testing-library/react'
import { App } from '../App.tsx'
import { createLanguageSetting } from '../lib/languageSetting.ts'
import { createI18n } from '../i18n.ts'
import { createAppRouter } from '../router.ts'

/** 10:00 UTC on 25 November 2026, 12:00 in Israel: tomorrow's plan there is 26 November. */
export const TEST_NOW = new Date('2026-11-25T10:00:00Z')

/** Where and when a test opens the app. */
export interface RenderAppOptions {
  /** The address to open, `/` when left out. */
  path?: string
  /** The instant the app takes as now, {@link TEST_NOW} when left out. */
  now?: Date
}

/**
 * Opens the whole web app at an address, as the browser would, over jsdom's `localStorage`, with a
 * fresh router, query cache and i18next instance, and a clock fixed at `now`. The router loads its
 * first route asynchronously, so query the page with `findBy…`.
 *
 * @returns Testing Library's render result, with the router and the i18next instance
 */
export function renderApp({ path = '/', now = TEST_NOW }: RenderAppOptions = {}) {
  const languageSetting = createLanguageSetting(localStorage)
  const i18n = createI18n(languageSetting.read())
  // The app's own cache, with no wait between a read's tries.
  const queryClient = createQueryClient({ retryDelay: 0 })
  const router = createAppRouter({
    queryClient,
    now: () => now,
    languageSetting,
    history: createMemoryHistory({ initialEntries: [path] }),
  })
  const result = render(<App router={router} i18n={i18n} queryClient={queryClient} />)
  return { ...result, router, i18n }
}

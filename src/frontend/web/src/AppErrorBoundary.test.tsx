import { screen } from '@testing-library/react'
import { expect, onTestFinished, test, vi } from 'vitest'
import { LANGUAGE_STORAGE_KEY } from './lib/languageSetting.ts'
import { renderApp } from './test/renderApp.tsx'

const MARKER = 'internal-detail-51d0'

// A page throws, and so does the error page that would show it: the last boundary must catch both.
vi.mock('./features/fleet/FleetPage.tsx', () => ({
  FleetPage: () => {
    throw new Error(`${MARKER} in the page`)
  },
}))
vi.mock('./routes/ErrorPage.tsx', () => ({
  ErrorPage: () => {
    throw new Error(`${MARKER} in the error page`)
  },
}))

/** Keeps React's and the router's own reports of the caught errors out of the test output. */
function silenceTheErrorReport(): void {
  const errors = vi.spyOn(console, 'error').mockImplementation(() => undefined)
  const warnings = vi.spyOn(console, 'warn').mockImplementation(() => undefined)
  onTestFinished(() => {
    errors.mockRestore()
    warnings.mockRestore()
  })
}

test('GivenTheErrorPageItselfThrows_WhenRendered_ThenTheLastBoundaryShowsATranslatedMessageAndNoError', async () => {
  silenceTheErrorReport()

  renderApp({ path: '/fleet' })

  expect(
    await screen.findByRole('heading', { level: 1, name: 'Something went wrong' }),
  ).toBeDefined()
  expect(document.body.textContent).not.toContain(MARKER)
  // The router's own fallback offered a "Show Error" button that revealed the message.
  expect(screen.queryByRole('button')).toBeNull()
})

test('GivenHebrewAndTheErrorPageThrows_WhenRendered_ThenTheLastBoundaryShowsTheHebrewMessage', async () => {
  silenceTheErrorReport()
  localStorage.setItem(LANGUAGE_STORAGE_KEY, 'he')

  renderApp({ path: '/fleet' })

  expect(await screen.findByRole('heading', { level: 1, name: 'משהו השתבש' })).toBeDefined()
  expect(document.body.textContent).not.toContain(MARKER)
})

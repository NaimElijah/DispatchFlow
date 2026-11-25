import { screen } from '@testing-library/react'
import { expect, onTestFinished, test, vi } from 'vitest'
import { LANGUAGE_STORAGE_KEY } from './lib/languageSetting.ts'
import { expectNoErrorDetail } from './test/errorDetail.ts'
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
  expectNoErrorDetail(MARKER)
  // The router's own fallback offered a "Show Error" button that revealed the message; this one
  // offers nothing to press, so it asks for a reload.
  expect(screen.queryByRole('button')).toBeNull()
  expect(screen.getByText('The app could not be shown. Reload the page.')).toBeDefined()
})

test('GivenHebrewAndTheErrorPageThrows_WhenRendered_ThenTheLastBoundaryShowsTheHebrewMessageRightToLeft', async () => {
  silenceTheErrorReport()
  localStorage.setItem(LANGUAGE_STORAGE_KEY, 'he')

  renderApp({ path: '/fleet' })

  expect(await screen.findByRole('heading', { level: 1, name: 'משהו השתבש' })).toBeDefined()
  // Set on the message itself, for when the first render fails before <html> gets them.
  const message = screen.getByRole('main')
  expect(message.getAttribute('lang')).toBe('he')
  expect(message.getAttribute('dir')).toBe('rtl')
  expectNoErrorDetail(MARKER)
})

import { fireEvent, screen } from '@testing-library/react'
import { beforeEach, expect, onTestFinished, test, vi } from 'vitest'
import { LANGUAGE_STORAGE_KEY } from '../lib/languageSetting.ts'
import { expectNoErrorDetail } from '../test/errorDetail.ts'
import { renderApp } from '../test/renderApp.tsx'

const MARKER = 'internal-detail-7f3a'

// Whether our own fleet page throws, with the marker in its message and so in its stack.
const fleetPage = vi.hoisted(() => ({ fails: true }))

vi.mock('../features/fleet/FleetPage.tsx', () => ({
  FleetPage: () => {
    if (fleetPage.fails) {
      throw new Error(`${MARKER} at FleetPage (FleetPage.tsx:12:7)`)
    }
    return <h1>Fleet</h1>
  },
}))

beforeEach(() => {
  fleetPage.fails = true
})

/** Keeps React's and the router's own reports of the caught error out of the test output. */
function silenceTheErrorReport(): void {
  const errors = vi.spyOn(console, 'error').mockImplementation(() => undefined)
  const warnings = vi.spyOn(console, 'warn').mockImplementation(() => undefined)
  onTestFinished(() => {
    errors.mockRestore()
    warnings.mockRestore()
  })
}

test('GivenAComponentThatThrows_WhenRendered_ThenATranslatedMessageShowsAndNoStackTrace', async () => {
  silenceTheErrorReport()

  renderApp({ path: '/fleet' })

  expect(
    await screen.findByRole('heading', { level: 1, name: 'Something went wrong' }),
  ).toBeDefined()
  expect(screen.getByRole('button', { name: 'Try again' })).toBeDefined()
  expect(screen.getByRole('navigation', { name: 'Main' })).toBeDefined()
  expectNoErrorDetail(MARKER)
  expect(document.body.textContent).not.toMatch(/\.tsx:\d+/)
})

test('GivenHebrewAndAComponentThatThrows_WhenRendered_ThenTheMessageIsInHebrew', async () => {
  silenceTheErrorReport()
  localStorage.setItem(LANGUAGE_STORAGE_KEY, 'he')

  renderApp({ path: '/fleet' })

  expect(await screen.findByRole('heading', { level: 1, name: 'משהו השתבש' })).toBeDefined()
  expectNoErrorDetail(MARKER)
})

test('GivenAnErrorThatHasCleared_WhenTryAgainIsPressed_ThenThePageShows', async () => {
  silenceTheErrorReport()
  renderApp({ path: '/fleet' })
  const tryAgain = await screen.findByRole('button', { name: 'Try again' })

  fleetPage.fails = false
  fireEvent.click(tryAgain)

  expect(await screen.findByRole('heading', { level: 1, name: 'Fleet' })).toBeDefined()
})

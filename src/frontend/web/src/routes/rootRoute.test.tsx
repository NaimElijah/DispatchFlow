import { screen } from '@testing-library/react'
import { expect, onTestFinished, test, vi } from 'vitest'
import { expectNoErrorDetail } from '../test/errorDetail.ts'
import { renderApp } from '../test/renderApp.tsx'

const MARKER = 'internal-detail-9c41'

// The shell's own layout throws, so no page and no navigation can render.
vi.mock('./AppLayout.tsx', () => ({
  AppLayout: () => {
    throw new Error(`${MARKER} at AppLayout (AppLayout.tsx:30:9)`)
  },
}))

test('GivenTheShellItselfThrows_WhenRendered_ThenATranslatedMessageShowsAndNoStackTrace', async () => {
  const errors = vi.spyOn(console, 'error').mockImplementation(() => undefined)
  const warnings = vi.spyOn(console, 'warn').mockImplementation(() => undefined)
  onTestFinished(() => {
    errors.mockRestore()
    warnings.mockRestore()
  })

  renderApp({ path: '/fleet' })

  expect(
    await screen.findByRole('heading', { level: 1, name: 'Something went wrong' }),
  ).toBeDefined()
  expect(screen.queryByRole('navigation')).toBeNull()
  // The router's error page, not the last boundary's plain message, which offers no retry.
  expect(screen.getByRole('button', { name: 'Try again' })).toBeDefined()
  expectNoErrorDetail(MARKER)
})

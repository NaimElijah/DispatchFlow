import { screen } from '@testing-library/react'
import { expect, test } from 'vitest'
import { LANGUAGE_STORAGE_KEY } from '../../lib/languageSetting.ts'
import { renderApp } from '../../test/renderApp.tsx'

test('GivenAPlanningUrlWithADate_WhenOpened_ThenThePlanningRouteReceivesThatDate', async () => {
  renderApp({ path: '/plans/2026-12-03' })

  const heading = await screen.findByRole('heading', { level: 1, name: 'Plan for 03/12/2026' })
  // The date is isolated for direction (NFR-136) and machine-readable.
  expect(heading.querySelector('bdi > time')?.getAttribute('datetime')).toBe('2026-12-03')
  expect(document.title).toBe('Plan for 03/12/2026 · DispatchFlow')
})

test('GivenHebrew_WhenAPlanIsOpened_ThenItsHeadingKeepsTheDateIsolatedInHebrewText', async () => {
  localStorage.setItem(LANGUAGE_STORAGE_KEY, 'he')

  renderApp({ path: '/plans/2026-12-03' })

  const heading = await screen.findByRole('heading', {
    level: 1,
    name: 'תוכנית ליום 03/12/2026',
  })
  expect(heading.querySelector('bdi > time')?.textContent).toBe('03/12/2026')
})

test('GivenAPlanningUrlWithAnImpossibleDate_WhenOpened_ThenTheNotFoundPageShows', async () => {
  for (const date of ['2026-02-30', '2026-13-01', '2027-02-29', '26-11-2026', 'tomorrow']) {
    const { unmount } = renderApp({ path: `/plans/${date}` })

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Page not found' }),
      date,
    ).toBeDefined()
    expect(screen.getByRole('navigation', { name: 'Main' }), date).toBeDefined()
    unmount()
  }
})

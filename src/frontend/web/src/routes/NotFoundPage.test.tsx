import { fireEvent, screen, within } from '@testing-library/react'
import { expect, test } from 'vitest'
import { renderApp } from '../test/renderApp.tsx'

test('GivenAnUnknownPath_WhenOpened_ThenTheNotFoundPageShows', async () => {
  renderApp({ path: '/no-such-page' })

  expect(await screen.findByRole('heading', { level: 1, name: 'Page not found' })).toBeDefined()
  expect(screen.getByRole('navigation', { name: 'Main' })).toBeDefined()
  expect(document.title).toBe('Page not found · DispatchFlow')
})

test('GivenAnUnknownAddressUnderAnArea_WhenOpened_ThenNoNavigationItemIsCurrent', async () => {
  for (const path of ['/plans/2026-02-30', '/settings/x', '/fleet/x']) {
    const { unmount } = renderApp({ path })
    await screen.findByRole('heading', { level: 1, name: 'Page not found' })

    const navigation = screen.getByRole('navigation', { name: 'Main' })
    expect(within(navigation).queryAllByRole('link', { current: 'page' }), path).toEqual([])
    unmount()
  }
})

test('GivenTheNotFoundPage_WhenItsLinkIsFollowed_ThenTomorrowsPlanOpens', async () => {
  renderApp({ path: '/orders/no-such-page' })

  fireEvent.click(await screen.findByRole('link', { name: 'Go to the planning screen' }))

  expect(
    await screen.findByRole('heading', { level: 1, name: 'Plan for 26/11/2026' }),
  ).toBeDefined()
})

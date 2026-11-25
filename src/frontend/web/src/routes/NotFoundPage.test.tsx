import { fireEvent, screen } from '@testing-library/react'
import { expect, test } from 'vitest'
import { renderApp } from '../test/renderApp.tsx'

test('GivenAnUnknownPath_WhenOpened_ThenTheNotFoundPageShows', async () => {
  renderApp({ path: '/no-such-page' })

  expect(await screen.findByRole('heading', { level: 1, name: 'Page not found' })).toBeDefined()
  expect(screen.getByRole('navigation', { name: 'Main' })).toBeDefined()
  expect(document.title).toBe('Page not found · DispatchFlow')
})

test('GivenTheNotFoundPage_WhenItsLinkIsFollowed_ThenTomorrowsPlanOpens', async () => {
  renderApp({ path: '/orders/no-such-page' })

  fireEvent.click(await screen.findByRole('link', { name: 'Go to the planning screen' }))

  expect(
    await screen.findByRole('heading', { level: 1, name: 'Plan for 26/11/2026' }),
  ).toBeDefined()
})

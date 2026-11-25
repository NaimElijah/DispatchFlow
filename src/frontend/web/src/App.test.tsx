import { fireEvent, screen, within } from '@testing-library/react'
import { expect, test } from 'vitest'
import { LANGUAGE_STORAGE_KEY } from './lib/languageSetting.ts'
import { renderApp } from './test/renderApp.tsx'

/** The shell's navigation, once the page has rendered. */
function navigation(name = 'Main'): Promise<HTMLElement> {
  return screen.findByRole('navigation', { name })
}

test('GivenNoLanguageSetting_WhenTheShellRenders_ThenTheDocumentIsLeftToRightAndInEnglish', async () => {
  renderApp({ path: '/fleet' })

  expect(await screen.findByRole('heading', { level: 1, name: 'Fleet' })).toBeDefined()
  expect(await navigation()).toBeDefined()
  expect(document.documentElement.lang).toBe('en')
  expect(document.documentElement.dir).toBe('ltr')
})

test('GivenHebrewInTheSettings_WhenTheShellRenders_ThenTheDocumentIsRightToLeftAndInHebrew', async () => {
  localStorage.setItem(LANGUAGE_STORAGE_KEY, 'he')

  renderApp({ path: '/fleet' })

  expect(await screen.findByRole('heading', { level: 1, name: 'צי רכב' })).toBeDefined()
  expect(await navigation('ראשי')).toBeDefined()
  expect(document.documentElement.lang).toBe('he')
  expect(document.documentElement.dir).toBe('rtl')
})

test('GivenTheShell_WhenRendered_ThenItsHeaderNamesTheProductAndLeadsToThePlanningScreen', async () => {
  renderApp({ path: '/fleet' })
  await screen.findByRole('heading', { level: 1, name: 'Fleet' })

  fireEvent.click(within(screen.getByRole('banner')).getByRole('link', { name: 'DispatchFlow' }))

  expect(
    await screen.findByRole('heading', { level: 1, name: 'Plan for 26/11/2026' }),
  ).toBeDefined()
})

test('GivenTheShell_WhenRendered_ThenEveryLinkAndButtonHasAnAccessibleName', async () => {
  for (const language of ['en', 'he']) {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, language)
    const { unmount } = renderApp({ path: '/plans/2026-11-26' })
    await screen.findByRole('heading', { level: 1 })

    for (const role of ['link', 'button'] as const) {
      const all = screen.getAllByRole(role)
      expect(all.length, `${language} ${role}`).toBeGreaterThan(0)
      expect(screen.getAllByRole(role, { name: /\S/ }), `${language} ${role}`).toHaveLength(
        all.length,
      )
    }
    unmount()
  }
})

test('GivenTheRootPath_WhenOpened_ThenTomorrowsPlanInIsraelOpensInPlaceOfIt', async () => {
  // 22:30 UTC on 25 November is 00:30 on 26 November in Israel, so tomorrow there is the 27th.
  const { router } = renderApp({ path: '/', now: new Date('2026-11-25T22:30:00Z') })

  expect(
    await screen.findByRole('heading', { level: 1, name: 'Plan for 27/11/2026' }),
  ).toBeDefined()
  expect(router.state.location.pathname).toBe('/plans/2026-11-27')
  // Both redirects replace the entry, so Back leaves the app instead of redirecting again.
  expect(router.history.length).toBe(1)
})

test('GivenTheNavigation_WhenALinkIsFollowed_ThenItsPageShowsAndOnlyItsItemIsCurrent', async () => {
  renderApp({ path: '/settings' })
  const items = await navigation()

  for (const [link, heading] of [
    ['Orders', 'Orders'],
    ['Import', 'Import'],
    ['Fleet', 'Fleet'],
    ['Planning', 'Plan for 26/11/2026'],
    ['Settings', 'Settings'],
  ]) {
    fireEvent.click(within(items).getByRole('link', { name: link }))

    expect(await screen.findByRole('heading', { level: 1, name: heading }), link).toBeDefined()
    expect(within(items).getAllByRole('link', { current: 'page' }), link).toEqual([
      within(items).getByRole('link', { name: link }),
    ])
  }
})

test('GivenAPlanForAnotherDate_WhenOpened_ThenThePlanningItemIsCurrent', async () => {
  renderApp({ path: '/plans/2026-12-03' })

  const planning = within(await navigation()).getByRole('link', { name: 'Planning' })

  expect(planning.getAttribute('aria-current')).toBe('page')
})

test('GivenAnImpossiblePlanDate_WhenThePlanningItemIsFollowed_ThenTomorrowsPlanOpens', async () => {
  renderApp({ path: '/plans/2026-02-30' })
  await screen.findByRole('heading', { level: 1, name: 'Page not found' })

  fireEvent.click(within(await navigation()).getByRole('link', { name: 'Planning' }))

  expect(
    await screen.findByRole('heading', { level: 1, name: 'Plan for 26/11/2026' }),
  ).toBeDefined()
})

test('GivenAPage_WhenOpened_ThenTheBrowserTabNamesIt', async () => {
  renderApp({ path: '/orders' })
  await screen.findByRole('heading', { level: 1, name: 'Orders' })

  expect(document.title).toBe('Orders · DispatchFlow')
})

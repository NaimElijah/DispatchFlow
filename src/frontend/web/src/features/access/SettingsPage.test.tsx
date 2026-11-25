import { fireEvent, screen } from '@testing-library/react'
import { expect, test } from 'vitest'
import { LANGUAGE_STORAGE_KEY } from '../../lib/languageSetting.ts'
import { renderApp } from '../../test/renderApp.tsx'

/** Opens the Settings page and chooses a language by its own name. */
async function chooseLanguage(name: 'English' | 'עברית'): Promise<void> {
  fireEvent.click(await screen.findByRole('radio', { name }))
}

test('GivenTheShellInEnglish_WhenHebrewIsChosenInTheSettings_ThenItIsRightToLeftAndInHebrew', async () => {
  renderApp({ path: '/settings' })
  expect(await screen.findByRole('heading', { level: 1, name: 'Settings' })).toBeDefined()

  await chooseLanguage('עברית')

  expect(await screen.findByRole('heading', { level: 1, name: 'הגדרות' })).toBeDefined()
  expect(screen.getByRole('radiogroup', { name: 'שפת הממשק' })).toBeDefined()
  expect(screen.getByRole('radio', { name: 'עברית' })).toHaveProperty('checked', true)
  expect(document.documentElement.lang).toBe('he')
  expect(document.documentElement.dir).toBe('rtl')
  expect(document.title).toBe('הגדרות · DispatchFlow')
})

test('GivenALanguageChosenInTheSettings_WhenStorageIsRead_ThenTheLanguageIsTheOnlyThingKept', async () => {
  renderApp({ path: '/settings' })

  await chooseLanguage('עברית')
  await screen.findByRole('heading', { level: 1, name: 'הגדרות' })

  // ADR-076 and ADR-143: nothing else the app does reaches browser storage.
  expect(Object.keys(localStorage)).toEqual([LANGUAGE_STORAGE_KEY])
  expect(Object.keys(sessionStorage)).toEqual([])
})

test('GivenHebrewChosenInTheSettings_WhenTheAppIsOpenedAgain_ThenItOpensInHebrew', async () => {
  const first = renderApp({ path: '/settings' })
  await chooseLanguage('עברית')
  await screen.findByRole('heading', { level: 1, name: 'הגדרות' })
  first.unmount()

  renderApp({ path: '/orders' })

  expect(await screen.findByRole('heading', { level: 1, name: 'הזמנות' })).toBeDefined()
  expect(document.documentElement.dir).toBe('rtl')
})

test('GivenHebrew_WhenEnglishIsChosenInTheSettings_ThenItIsLeftToRightAndInEnglish', async () => {
  renderApp({ path: '/settings' })
  await chooseLanguage('עברית')
  await screen.findByRole('heading', { level: 1, name: 'הגדרות' })

  await chooseLanguage('English')

  expect(await screen.findByRole('heading', { level: 1, name: 'Settings' })).toBeDefined()
  expect(document.documentElement.lang).toBe('en')
  expect(document.documentElement.dir).toBe('ltr')
})

test('GivenTheSettingsInEitherLanguage_WhenRendered_ThenEachLanguageIsNamedInItself', async () => {
  for (const [language, title] of [
    ['en', 'Settings'],
    ['he', 'הגדרות'],
  ]) {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, language)
    const { unmount } = renderApp({ path: '/settings' })
    await screen.findByRole('heading', { level: 1, name: title })

    expect(screen.getByText('עברית').getAttribute('lang'), language).toBe('he')
    expect(screen.getByText('English').getAttribute('lang'), language).toBe('en')
    unmount()
  }
})

import { languages, resources, type Language } from '@dispatchflow/i18n'
import i18next, { type i18n } from 'i18next'
import { initReactI18next } from 'react-i18next'

/**
 * Creates the app's own i18next instance, translating in the given language from the strings of
 * `@dispatchflow/i18n` (ADR-089). Each call makes a new instance, so no two apps, and no two tests,
 * share a language.
 *
 * @throws Error when i18next did not finish initialising synchronously, which the first render
 *   relies on to have its strings without suspending
 */
export function createI18n(language: Language): i18n {
  const instance = i18next.createInstance()
  // The strings are bundled and there is no backend, so with initAsync off the init completes
  // before it returns, and its promise has nothing left that can fail; the check below proves it.
  void instance.use(initReactI18next).init({
    resources,
    lng: language,
    supportedLngs: [...languages],
    // Never reached: both languages hold every key, which tsc and the package's tests check.
    fallbackLng: false,
    defaultNS: 'shell',
    initAsync: false,
    // React escapes what it renders; escaping here too would show `&amp;` on screen.
    interpolation: { escapeValue: false },
  })
  if (!instance.isInitialized) {
    throw new Error('i18next did not initialise synchronously from the bundled strings')
  }
  return instance
}

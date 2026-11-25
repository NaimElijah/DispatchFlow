import { isLanguage, type Language } from '@dispatchflow/i18n'

/**
 * The language before any setting chooses one (ADR-143). Until FR-124 stores a user's own language
 * on the account and FR-182 starts a new user with the company's, only the Settings page changes it.
 */
export const defaultLanguage: Language = 'en'

/**
 * The one key the web app writes to browser storage (ADR-143). It holds a choice made on this
 * device, not data the server sent (ADR-076), and it goes once FR-124 stores the language on the
 * account. Sign-out's `Clear-Site-Data` wipes it with the rest of the site's storage.
 */
export const LANGUAGE_STORAGE_KEY = 'dispatchflow.language'

/**
 * Where the interface language is kept between visits: the stand-in for the user's own language
 * setting (FR-124), until the account stores it.
 */
export interface LanguageSetting {
  /** The language last saved, or {@link defaultLanguage} when none was, or the stored one is not a language. */
  read(): Language
  /** Keeps the language for later visits, or for this visit only when the browser refuses storage. */
  save(language: Language): void
}

/** The errors with which a browser refuses its storage: blocked site data, and a full quota. */
const STORAGE_REFUSALS = new Set(['SecurityError', 'QuotaExceededError'])

/**
 * Returns the browser's `localStorage`, or undefined when the browser refuses access to it, as it
 * does when the user blocks site data: reading the property then throws a `SecurityError`.
 *
 * @throws whatever else reading the property throws, which no browser is known to
 */
export function browserStorage(): Storage | undefined {
  return refusalAsUndefined(() => window.localStorage)
}

/**
 * Creates the language setting over a storage, or over none, keeping the language for the visit
 * only. A storage that refuses a read or a write, with a `SecurityError` or a full quota's
 * `QuotaExceededError`, counts as none: the choice still holds until the page reloads.
 *
 * @param storage where the choice is kept between visits, usually {@link browserStorage}'s answer
 */
export function createLanguageSetting(storage: Storage | undefined): LanguageSetting {
  let chosenThisVisit: Language | undefined
  return {
    read() {
      if (chosenThisVisit !== undefined) {
        return chosenThisVisit
      }
      // The stored value is outside input: anything but a language's code opens the default.
      const stored = refusalAsUndefined(() => storage?.getItem(LANGUAGE_STORAGE_KEY))
      return isLanguage(stored) ? stored : defaultLanguage
    },
    save(language) {
      chosenThisVisit = language
      refusalAsUndefined(() => storage?.setItem(LANGUAGE_STORAGE_KEY, language))
    },
  }
}

/**
 * Runs a storage call, answering undefined when the browser refuses it with one of
 * {@link STORAGE_REFUSALS}, and rethrowing anything else.
 */
function refusalAsUndefined<T>(call: () => T): T | undefined {
  try {
    return call()
  } catch (error) {
    if (error instanceof DOMException && STORAGE_REFUSALS.has(error.name)) {
      return undefined
    }
    throw error
  }
}

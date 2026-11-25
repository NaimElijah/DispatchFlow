import { expect, test, vi } from 'vitest'
import { browserStorage, createLanguageSetting, LANGUAGE_STORAGE_KEY } from './languageSetting.ts'

/** A storage that refuses every call with the given error, as a browser does that blocks site data. */
function failingStorage(error: () => Error = () => new DOMException('insecure', 'SecurityError')) {
  const fail = () => {
    throw error()
  }
  return {
    length: 0,
    clear: fail,
    getItem: fail,
    key: fail,
    removeItem: fail,
    setItem: fail,
  } satisfies Storage
}

/**
 * Runs a check while reading `window.localStorage` throws, as it does in a browser that blocks site
 * data. The getter is restored before the check returns: setup.ts clears `localStorage` after each
 * test, before `onTestFinished` callbacks run.
 */
function withLocalStorageThrowing(error: Error, check: () => void): void {
  const spy = vi.spyOn(window, 'localStorage', 'get').mockImplementation(() => {
    throw error
  })
  try {
    check()
  } finally {
    spy.mockRestore()
  }
}

test('GivenNothingStored_WhenRead_ThenTheLanguageIsEnglish', () => {
  expect(createLanguageSetting(localStorage).read()).toBe('en')
})

test('GivenAStoredValueThatIsNotALanguage_WhenRead_ThenTheLanguageIsEnglish', () => {
  for (const stored of ['fr', 'HE', ' he', '<script>', '']) {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, stored)

    expect(createLanguageSetting(localStorage).read(), stored).toBe('en')
  }
})

test('GivenHebrewSaved_WhenANewVisitReadsIt_ThenTheLanguageIsHebrew', () => {
  createLanguageSetting(localStorage).save('he')

  expect(localStorage.getItem(LANGUAGE_STORAGE_KEY)).toBe('he')
  expect(createLanguageSetting(localStorage).read()).toBe('he')
})

test('GivenStorageThatRefusesAccess_WhenTheLanguageIsSaved_ThenItIsKeptForThisVisit', () => {
  const setting = createLanguageSetting(failingStorage())
  expect(setting.read()).toBe('en')

  setting.save('he')

  expect(setting.read()).toBe('he')
  expect(createLanguageSetting(failingStorage()).read()).toBe('en')
})

test('GivenAFullStorage_WhenTheLanguageIsSaved_ThenItIsKeptForThisVisit', () => {
  const setting = createLanguageSetting(
    failingStorage(() => new DOMException('full', 'QuotaExceededError')),
  )

  setting.save('he')

  expect(setting.read()).toBe('he')
})

test('GivenNoStorage_WhenTheLanguageIsSaved_ThenItIsKeptForThisVisit', () => {
  const setting = createLanguageSetting(undefined)

  setting.save('he')

  expect(setting.read()).toBe('he')
})

test('GivenStorageThatFailsWithAnythingButARefusal_WhenReadOrSaved_ThenTheErrorIsNotHidden', () => {
  for (const error of [() => new TypeError('broken'), () => new DOMException('x', 'DataError')]) {
    const setting = createLanguageSetting(failingStorage(error))

    expect(() => setting.read()).toThrow(error().message)
    expect(() => setting.save('he')).toThrow(error().message)
  }
})

test('GivenABrowserThatRefusesSiteData_WhenStorageIsAsked_ThenThereIsNone', () => {
  withLocalStorageThrowing(new DOMException('The operation is insecure.', 'SecurityError'), () => {
    expect(browserStorage()).toBeUndefined()
  })
})

test('GivenLocalStorageFailingWithAnythingButARefusal_WhenStorageIsAsked_ThenTheErrorIsNotHidden', () => {
  withLocalStorageThrowing(new TypeError('broken window'), () => {
    expect(browserStorage).toThrow('broken window')
  })
})

test('GivenABrowserWithStorage_WhenStorageIsAsked_ThenItIsLocalStorage', () => {
  expect(browserStorage()).toBe(localStorage)
})

import { expect, test } from 'vitest'
import { languages, resources } from './index.ts'

/** Lists every string under a node as its dotted key and its text, sorted by key. */
function stringsOf(node: object, prefix = ''): [string, string][] {
  return Object.entries(node)
    .flatMap(([key, value]): [string, string][] =>
      typeof value === 'string' ? [[prefix + key, value]] : stringsOf(value, `${prefix}${key}.`),
    )
    .sort(([a], [b]) => a.localeCompare(b))
}

/** The `{{value}}` interpolations and `<element>` markers in a string, sorted. */
function placeholdersOf(text: string): string[] {
  return [...text.matchAll(/\{\{\s*(\w+)\s*\}\}|<\/?(\w+)\s*\/?>/g)].map((match) => match[0]).sort()
}

const [english, hebrew] = languages.map((language) => stringsOf(resources[language]))

// The Hebrew files are typed against the English ones, so tsc already refuses a missing key; this
// also catches a cast that silenced it, and the empty strings no type can refuse.
test('GivenBothLanguages_WhenTheirStringsAreListed_ThenTheKeysMatchAndNoneIsEmpty', () => {
  expect(hebrew.map(([key]) => key)).toEqual(english.map(([key]) => key))
  for (const [key, text] of [...english, ...hebrew]) {
    expect(text, key).toMatch(/\S/)
  }
})

// A translation that drops `{{date}}` or `<date>` would show the screen without its value.
test('GivenAStringWithPlaceholders_WhenBothLanguagesAreCompared_ThenEachHasTheSamePlaceholders', () => {
  const hebrewByKey = new Map(hebrew)
  for (const [key, text] of english) {
    expect(placeholdersOf(hebrewByKey.get(key) ?? ''), key).toEqual(placeholdersOf(text))
  }
})

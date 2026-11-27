import { expect, test } from 'vitest'
import { languages, resources } from './index.ts'

/** The contract's pattern for a `code` (contracts/client-api/v1, Problem.code). */
const CODE = /^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$/

// NFR-144: every error code has Hebrew and English text and a next step. The Hebrew file's type
// already refuses a missing key, but the English file is the shape's source, so only this catches an
// English entry that left out its next step.
test('GivenEveryRefusalCode_WhenItsTextsAreRead_ThenEachLanguageHasTextAndANextStep', () => {
  for (const language of languages) {
    for (const [code, entry] of Object.entries(resources[language].problems.codes)) {
      expect(code, code).toMatch(CODE)
      expect(Object.keys(entry).sort(), `${language} ${code}`).toEqual(['nextStep', 'text'])
      expect(entry.text, `${language} ${code}`).toMatch(/\S/)
      expect(entry.nextStep, `${language} ${code}`).toMatch(/\S/)
    }
  }
})

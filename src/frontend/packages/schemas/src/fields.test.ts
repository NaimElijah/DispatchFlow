import { describe, expect, test } from 'vitest'
import { z } from './zod.ts'
import {
  choice,
  durationMinutes,
  localDate,
  optionalNumber,
  optionalPhoneNumber,
  optionalText,
  optionalTimeWindow,
  phoneNumber,
  requiredText,
  timeOfDay,
  timeWindow,
  weightKg,
  wholeNumber,
} from './fields.ts'
import { refusalOf } from './refusals.ts'

/** Parses a value and lists each refusal by its path, as an error map built on refusalOf reads it. */
function refusalsOf(schema: z.ZodType, value: unknown) {
  const result = schema.safeParse(value, { error: (issue) => JSON.stringify(refusalOf(issue)) })
  return result.success
    ? []
    : result.error.issues.map((issue) => ({
        path: issue.path.join('.'),
        ...(JSON.parse(issue.message) as object),
      }))
}

describe('texts', () => {
  test('GivenAnEmptyOrBlankRequiredText_WhenChecked_ThenItIsRequired', () => {
    for (const value of ['', '   ']) {
      expect(refusalsOf(requiredText(100), value)).toEqual([
        { path: '', code: 'REQUIRED', params: {} },
      ])
    }
  })

  test('GivenATextLongerThanItsLimit_WhenChecked_ThenItIsTooLongWithTheLimit', () => {
    expect(refusalsOf(requiredText(3), 'abcd')).toEqual([
      { path: '', code: 'TEXT_TOO_LONG', params: { maxLength: 3 } },
    ])
  })

  test('GivenATextWithSpacesAround_WhenParsed_ThenItIsTrimmed', () => {
    expect(requiredText(3).parse('  Ada ')).toBe('Ada')
  })

  test('GivenAnEmptyOptionalText_WhenParsed_ThenItIsAcceptedAndLeftOutOfTheBody', () => {
    expect(refusalsOf(optionalText(500), '')).toEqual([])
    expect(optionalText(500).parse('  ')).toBeUndefined()
  })

  test('GivenATextOfEmoji_WhenChecked_ThenItsLengthIsCountedAsTheServerCountsIt', () => {
    // Four emoji are eight UTF-16 units but four code points, the server's count (ADR-106).
    const trucks = '\u{1F69A}'.repeat(4)
    expect(refusalsOf(requiredText(4), trucks)).toEqual([])
    expect(refusalsOf(requiredText(3), trucks)).toEqual([
      { path: '', code: 'TEXT_TOO_LONG', params: { maxLength: 3 } },
    ])
  })

  test('GivenAnOptionalTextLongerThanItsLimit_WhenChecked_ThenItIsTooLongWithTheLimit', () => {
    expect(refusalsOf(optionalText(3), 'abcd')).toEqual([
      { path: '', code: 'TEXT_TOO_LONG', params: { maxLength: 3 } },
    ])
  })

  test('GivenNoChoice_WhenChecked_ThenAChoiceIsRequired', () => {
    expect(refusalsOf(choice, '')).toEqual([{ path: '', code: 'REQUIRED', params: {} }])
  })
})

describe('dates and times', () => {
  test('GivenTimesOfDay_WhenChecked_ThenOnlyAFilledHHmmIsAccepted', () => {
    expect(refusalsOf(timeOfDay, '')).toEqual([{ path: '', code: 'REQUIRED', params: {} }])
    expect(refusalsOf(timeOfDay, '8:30')).toEqual([{ path: '', code: 'WRONG_FORMAT', params: {} }])
    expect(refusalsOf(timeOfDay, '08:30')).toEqual([])
  })

  test('GivenDates_WhenChecked_ThenOnlyARealYYYYMMDDDateIsAccepted', () => {
    expect(refusalsOf(localDate, '')).toEqual([{ path: '', code: 'REQUIRED', params: {} }])
    expect(refusalsOf(localDate, '2026-02-30')).toEqual([
      { path: '', code: 'WRONG_FORMAT', params: {} },
    ])
    expect(refusalsOf(localDate, '2026-11-26')).toEqual([])
  })

  test('GivenAWindowThatEndsBeforeItStarts_WhenChecked_ThenTheWindowItselfIsRefused', () => {
    expect(refusalsOf(timeWindow, { start: '12:00', end: '08:00' })).toEqual([
      { path: '', code: 'INVALID_TIME_WINDOW', params: {} },
    ])
  })

  test('GivenAWindowThatEndsWhenItStarts_WhenChecked_ThenTheWindowItselfIsRefused', () => {
    expect(refusalsOf(timeWindow, { start: '08:00', end: '08:00' })).toEqual([
      { path: '', code: 'INVALID_TIME_WINDOW', params: {} },
    ])
  })

  test('GivenAWindowWithOneEndReadWithSeconds_WhenChecked_ThenItsEndsCompareAsTimes', () => {
    // A record read from the API may carry seconds, which the time field keeps until it changes.
    const endsWhenItStarts = [{ path: '', code: 'INVALID_TIME_WINDOW', params: {} }]
    expect(refusalsOf(timeWindow, { start: '09:30', end: '09:30:00' })).toEqual(endsWhenItStarts)
    expect(refusalsOf(optionalTimeWindow, { start: '09:30', end: '09:30:00' })).toEqual(
      endsWhenItStarts,
    )
    expect(refusalsOf(timeWindow, { start: '09:30:00', end: '09:31' })).toEqual([])
  })

  test('GivenAWindowWithoutItsStart_WhenChecked_ThenTheStartIsRequired', () => {
    expect(refusalsOf(timeWindow, { start: '', end: '08:00' })).toEqual([
      { path: 'start', code: 'REQUIRED', params: {} },
    ])
  })

  test('GivenAnOptionalWindowWithBothOrNeitherEnd_WhenChecked_ThenItIsAccepted', () => {
    expect(refusalsOf(optionalTimeWindow, { start: '', end: '' })).toEqual([])
    expect(optionalTimeWindow.parse({ start: '', end: '' })).toBeUndefined()
    expect(refusalsOf(optionalTimeWindow, { start: '08:00', end: '12:00' })).toEqual([])
  })

  test('GivenAnOptionalWindowWithOneEnd_WhenChecked_ThenItSaysBothOrNeither', () => {
    expect(refusalsOf(optionalTimeWindow, { start: '08:00', end: '' })).toEqual([
      { path: '', code: 'INCOMPLETE_TIME_WINDOW', params: {} },
    ])
  })

  test('GivenAnOptionalWindowThatEndsBeforeItStarts_WhenChecked_ThenItIsRefused', () => {
    expect(refusalsOf(optionalTimeWindow, { start: '12:00', end: '08:00' })).toEqual([
      { path: '', code: 'INVALID_TIME_WINDOW', params: {} },
    ])
  })

  test('GivenAnOptionalWindowWithAMalformedEnd_WhenChecked_ThenItIsInTheWrongFormat', () => {
    expect(refusalsOf(optionalTimeWindow, { start: '08:00', end: '25:00' })).toEqual([
      { path: '', code: 'WRONG_FORMAT', params: {} },
    ])
  })
})

describe('numbers', () => {
  test('GivenAWeightOfZero_WhenChecked_ThenItMustBePositive', () => {
    expect(refusalsOf(weightKg, 0)).toEqual([
      { path: '', code: 'NON_POSITIVE_QUANTITY', params: {} },
    ])
  })

  test('GivenAWeightWithThreeDecimals_WhenChecked_ThenItIsInTheWrongFormat', () => {
    expect(refusalsOf(weightKg, 1.234)).toEqual([{ path: '', code: 'WRONG_FORMAT', params: {} }])
    expect(refusalsOf(weightKg, 12.35)).toEqual([])
  })

  test('GivenNoWeight_WhenChecked_ThenItIsRequired', () => {
    expect(refusalsOf(weightKg, undefined)).toEqual([{ path: '', code: 'REQUIRED', params: {} }])
  })

  test('GivenWholeNumbersOutsideTheirRange_WhenChecked_ThenEachNamesItsLimit', () => {
    expect(refusalsOf(wholeNumber(1, 5), 0)).toEqual([
      { path: '', code: 'TOO_SMALL', params: { min: 1 } },
    ])
    expect(refusalsOf(wholeNumber(1, 5), 6)).toEqual([
      { path: '', code: 'TOO_LARGE', params: { max: 5 } },
    ])
    expect(refusalsOf(wholeNumber(1, 5), 2.5)).toEqual([
      { path: '', code: 'WRONG_FORMAT', params: {} },
    ])
  })

  test('GivenWholeNumbersAtEitherEndOfTheirRange_WhenChecked_ThenBothAreAccepted', () => {
    expect(refusalsOf(wholeNumber(1, 5), 1)).toEqual([])
    expect(refusalsOf(wholeNumber(1, 5), 5)).toEqual([])
  })

  test('GivenAnOptionalNumberLeftEmpty_WhenParsed_ThenItIsAcceptedAndLeftOutOfTheBody', () => {
    // `null` is what the number field holds once its box is emptied; `undefined`, before any typing.
    for (const empty of [null, undefined]) {
      expect(refusalsOf(optionalNumber(durationMinutes), empty), String(empty)).toEqual([])
      expect(optionalNumber(durationMinutes).parse(empty), String(empty)).toBeUndefined()
    }
  })

  test('GivenAnOptionalNumberFilledIn_WhenChecked_ThenItsBlocksChecksStillApply', () => {
    expect(refusalsOf(optionalNumber(durationMinutes), 0)).toEqual([
      { path: '', code: 'TOO_SMALL', params: { min: 1 } },
    ])
    expect(optionalNumber(durationMinutes).parse(15)).toBe(15)
  })

  test('GivenADurationOfNoMinutes_WhenChecked_ThenItIsTooSmall', () => {
    expect(refusalsOf(durationMinutes, 0)).toEqual([
      { path: '', code: 'TOO_SMALL', params: { min: 1 } },
    ])
    expect(refusalsOf(durationMinutes, 15)).toEqual([])
  })
})

describe('phones', () => {
  test('GivenIsraeliNumbersInTheirUsualForms_WhenChecked_ThenEachIsAccepted', () => {
    for (const value of ['050-123-4567', '0501234567', '+972501234567', '02-6234567']) {
      expect(refusalsOf(phoneNumber, value), value).toEqual([])
    }
  })

  test('GivenTextsThatAreNotPhoneNumbers_WhenChecked_ThenEachIsRefusedAsAPhoneNumber', () => {
    for (const value of ['12345', '050123456', 'phone']) {
      expect(refusalsOf(phoneNumber, value), value).toEqual([
        { path: '', code: 'INVALID_PHONE_NUMBER', params: {} },
      ])
    }
  })

  test('GivenAnEmptyPhone_WhenChecked_ThenARequiredOneIsRequiredAndAnOptionalOneIsAccepted', () => {
    expect(refusalsOf(phoneNumber, '')).toEqual([{ path: '', code: 'REQUIRED', params: {} }])
    expect(refusalsOf(optionalPhoneNumber, '')).toEqual([])
    expect(optionalPhoneNumber.parse('')).toBeUndefined()
  })

  test('GivenAnOptionalPhoneThatIsNoPhoneNumber_WhenChecked_ThenItIsRefusedAsAPhoneNumber', () => {
    expect(refusalsOf(optionalPhoneNumber, '12345')).toEqual([
      { path: '', code: 'INVALID_PHONE_NUMBER', params: {} },
    ])
  })
})

import { describe, expect, test } from 'vitest'
import { formatQuantity, unitSymbol, type Unit } from './quantities.ts'

// The Hebrew symbols are CLDR's, as the ICU in Node and in the browsers carries them: a change in a
// new ICU shows here as a failure of these tests, not of the formatter.
describe('formatQuantity', () => {
  test.each<[Unit, number, string, string]>([
    ['kg', 1234.5, '1,234.5 kg', '1,234.5 ק״ג'],
    ['litres', 0.25, '0.25 L', '0.25 ל׳'],
    ['minutes', 45, '45 min', '45 דק׳'],
    ['seconds', 30, '30 sec', '30 שנ׳'],
  ])(
    'GivenAQuantityIn_%s_WhenFormatted_ThenBothLanguagesShowItsUnit',
    (unit, value, english, hebrew) => {
      expect(formatQuantity(value, unit, 'en')).toBe(english)
      expect(formatQuantity(value, unit, 'he')).toBe(hebrew)
    },
  )

  test('GivenMoreThanTwoDecimals_WhenFormatted_ThenItIsRoundedToTwo', () => {
    expect(formatQuantity(12.3456, 'kg', 'en')).toBe('12.35 kg')
  })

  test('GivenAValueThatIsNotAFiniteNumber_WhenFormatted_ThenItIsRefused', () => {
    for (const value of [Number.NaN, Number.POSITIVE_INFINITY]) {
      expect(() => formatQuantity(value, 'kg', 'en')).toThrow(RangeError)
    }
  })
})

test('GivenEachUnit_WhenItsSymbolIsAsked_ThenItIsTheOneItsQuantitiesShow', () => {
  expect(unitSymbol('kg', 'en')).toBe('kg')
  expect(unitSymbol('kg', 'he')).toBe('ק״ג')
  expect(unitSymbol('litres', 'he')).toBe('ל׳')
  expect(unitSymbol('minutes', 'en')).toBe('min')
})

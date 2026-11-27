import { describe, expect, test } from 'vitest'
import {
  formatInstantDate,
  formatInstantDateTime,
  formatInstantTime,
  formatInstantTimeOn,
  formatLocalDate,
  formatLocalTime,
  formatTimeRange,
  isLocalDate,
  isLocalTime,
  israelDateOf,
} from './dates.ts'

// vitest.config.ts runs these tests in New York's zone, so a formatter that read the computer's
// zone in place of Israel's would fail them on every computer, an Israeli one included (ADR-088).
test('GivenTheTestsOwnZone_WhenRead_ThenItIsNotIsraels', () => {
  expect(new Intl.DateTimeFormat().resolvedOptions().timeZone).not.toBe('Asia/Jerusalem')
})

describe('instants', () => {
  test('GivenAnInstantAt2230UtcOn31March2026_WhenFormatted_ThenItShows01042026At0130', () => {
    // Israel moved to summer time (UTC+3) on 27 March 2026, so 22:30 UTC is 01:30 the next day.
    const instant = new Date('2026-03-31T22:30:00Z')

    expect(formatInstantDateTime(instant)).toBe('01/04/2026 01:30')
    expect(formatInstantDate(instant)).toBe('01/04/2026')
    expect(formatInstantTime(instant)).toBe('01:30')
    expect(israelDateOf(instant)).toBe('2026-04-01')
  })

  test('GivenAnInstantInWinter_WhenFormatted_ThenItIsTwoHoursAheadOfUtc', () => {
    expect(formatInstantDateTime(new Date('2026-01-15T22:30:00Z'))).toBe('16/01/2026 00:30')
  })

  test('GivenAnAfternoonInstant_WhenItsTimeIsFormatted_ThenTheClockHas24Hours', () => {
    expect(formatInstantTime(new Date('2026-11-25T13:05:00Z'))).toBe('15:05')
  })

  test('GivenAnInvalidDate_WhenFormatted_ThenItIsRefused', () => {
    const invalid = new Date('not a date')

    expect(() => formatInstantDateTime(invalid)).toThrow(RangeError)
    expect(() => israelDateOf(invalid)).toThrow(RangeError)
  })
})

describe('formatInstantTimeOn', () => {
  test('GivenAnInstantOnTheDeliveryDate_WhenFormatted_ThenOnlyItsTimeIsShown', () => {
    expect(formatInstantTimeOn(new Date('2026-11-26T08:15:00Z'), '2026-11-26')).toEqual({
      time: '10:15',
      date: undefined,
    })
  })

  test('GivenAnInstantPastTheDeliveryDatesEndInIsrael_WhenFormatted_ThenItsDateIsShownToo', () => {
    // 22:10 UTC on 26 November is 00:10 on 27 November in Israel.
    expect(formatInstantTimeOn(new Date('2026-11-26T22:10:00Z'), '2026-11-26')).toEqual({
      time: '00:10',
      date: '27/11/2026',
    })
  })

  test('GivenAnInstantOnTheDayBeforeTheDeliveryDateInIsrael_WhenFormatted_ThenItsDateIsShownToo', () => {
    // 21:30 UTC on 25 November is 23:30 on 25 November in Israel.
    expect(formatInstantTimeOn(new Date('2026-11-25T21:30:00Z'), '2026-11-26')).toEqual({
      time: '23:30',
      date: '25/11/2026',
    })
  })

  test('GivenADeliveryDateThatIsNotADate_WhenFormatted_ThenItIsRefused', () => {
    expect(() => formatInstantTimeOn(new Date('2026-11-26T08:15:00Z'), '26/11/2026')).toThrow(
      RangeError,
    )
  })
})

describe('local dates and times', () => {
  test('GivenAnIsraelLocalDate_WhenFormatted_ThenItIsDayMonthYear', () => {
    expect(formatLocalDate('2026-03-07')).toBe('07/03/2026')
  })

  test('GivenTextsThatAreNotRealDates_WhenFormatted_ThenEachIsRefused', () => {
    for (const text of ['2027-02-29', '2026-02-30', '2026-13-01', '2026-1-5', '26/11/2026', '']) {
      expect(isLocalDate(text), text).toBe(false)
      expect(() => formatLocalDate(text), text).toThrow(RangeError)
    }
  })

  test('GivenALeapDay_WhenChecked_ThenItIsALocalDate', () => {
    expect(isLocalDate('2028-02-29')).toBe(true)
  })

  test('GivenALocalTimeWithOrWithoutSeconds_WhenFormatted_ThenItIsHoursAndMinutes', () => {
    expect(formatLocalTime('08:30')).toBe('08:30')
    expect(formatLocalTime('23:59:59')).toBe('23:59')
  })

  test('GivenTextsThatAreNotTimesOfDay_WhenFormatted_ThenEachIsRefused', () => {
    for (const text of ['24:00', '8:30', '08:60', '08:30:60', '0830', '08:30 ', '']) {
      expect(isLocalTime(text), text).toBe(false)
      expect(() => formatLocalTime(text), text).toThrow(RangeError)
    }
  })

  test('GivenAWindowsStartAndEnd_WhenFormatted_ThenTheyAreJoinedByAnEnDash', () => {
    expect(formatTimeRange('08:00:00', '12:30')).toBe('08:00–12:30')
  })

  test('GivenAWindowWithAMalformedEnd_WhenFormatted_ThenItIsRefused', () => {
    expect(() => formatTimeRange('08:00', '25:00')).toThrow(RangeError)
  })
})

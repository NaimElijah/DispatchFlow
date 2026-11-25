import { describe, expect, test } from 'vitest'
import { formatPlanDate, isPlanDate, tomorrowInIsrael, type PlanDate } from './planDate.ts'

describe('tomorrowInIsrael', () => {
  test('GivenMiddayInIsrael_WhenTomorrowIsAsked_ThenItIsTheNextDay', () => {
    expect(tomorrowInIsrael(new Date('2026-11-25T10:00:00Z'))).toBe('2026-11-26')
  })

  test('GivenJustAfterMidnightInIsraelButNotInUtc_WhenTomorrowIsAsked_ThenItCountsFromIsraelsDate', () => {
    // 22:30 UTC on 25 November is 00:30 on 26 November in Israel (UTC+2 in winter).
    expect(tomorrowInIsrael(new Date('2026-11-25T22:30:00Z'))).toBe('2026-11-27')
  })

  test('GivenJustBeforeMidnightInIsraelInSummer_WhenTomorrowIsAsked_ThenItCountsFromIsraelsDate', () => {
    // 20:59 UTC on 1 July is 23:59 on 1 July in Israel (UTC+3 in summer).
    expect(tomorrowInIsrael(new Date('2026-07-01T20:59:00Z'))).toBe('2026-07-02')
  })

  test('GivenJustAfterMidnightInIsraelInSummer_WhenTomorrowIsAsked_ThenItCountsFromIsraelsDate', () => {
    // 21:30 UTC on 1 July is 00:30 on 2 July in Israel (UTC+3), but 23:30 on 1 July at UTC+2: a
    // fixed winter offset would answer 2 July.
    expect(tomorrowInIsrael(new Date('2026-07-01T21:30:00Z'))).toBe('2026-07-03')
  })

  test('GivenTheLastDayOfAMonthAndOfAYear_WhenTomorrowIsAsked_ThenItRollsOver', () => {
    expect(tomorrowInIsrael(new Date('2026-04-30T09:00:00Z'))).toBe('2026-05-01')
    expect(tomorrowInIsrael(new Date('2026-12-31T09:00:00Z'))).toBe('2027-01-01')
    expect(tomorrowInIsrael(new Date('2028-02-28T09:00:00Z'))).toBe('2028-02-29')
  })
})

describe('isPlanDate', () => {
  test('GivenRealDates_WhenChecked_ThenEachIsAPlanDate', () => {
    for (const text of ['2026-11-26', '2028-02-29', '2026-12-31', '2027-01-01']) {
      expect(isPlanDate(text), text).toBe(true)
    }
  })

  test('GivenTextsThatAreNotRealDates_WhenChecked_ThenNoneIsAPlanDate', () => {
    for (const text of [
      '2027-02-29',
      '2026-02-30',
      '2026-04-31',
      '2026-13-01',
      '2026-00-10',
      '2026-11-00',
      '2026-1-5',
      '26/11/2026',
      '2026-11-26T00:00',
      ' 2026-11-26',
      '',
    ]) {
      expect(isPlanDate(text), text).toBe(false)
    }
  })
})

test('GivenAPlanDate_WhenFormatted_ThenItIsDayMonthYear', () => {
  expect(formatPlanDate('2026-03-07' as PlanDate)).toBe('07/03/2026')
})

import { schemaCodes } from '@dispatchflow/schemas'
import type { TFunction } from 'i18next'
import { describe, expect, test } from 'vitest'
import { createI18n } from '../i18n.ts'
import { isProblemCode, problemText } from './problemText.ts'

/** A translator that answers with what it was asked, so a test can read the values it was given. */
const echo = ((key: string, options: { replace: Record<string, unknown> }) =>
  JSON.stringify({ key, replace: options.replace })) as unknown as TFunction<'problems'>

function replaceOf(params: Record<string, unknown>): unknown {
  return JSON.parse(problemText(echo, 'TEXT_TOO_LONG', params).text).replace
}

describe('problemText', () => {
  // NFR-144: a refusal a schema raises shows its own text, never the general one.
  test('GivenEveryCodeASchemaRaises_WhenLookedUp_ThenEachHasItsOwnText', () => {
    for (const code of schemaCodes) {
      expect(isProblemCode(code), code).toBe(true)
    }
  })

  test('GivenParamsWithAnIsraelDateATimeAndAnInstant_WhenFilledIn_ThenEachIsShownAsTheScreensShowIt', () => {
    expect(
      replaceOf({
        date: '2026-11-26',
        time: '08:30:00',
        at: '2026-03-31T22:30:00Z',
        limit: 3,
        name: 'NAME',
        ids: ['a', 'b'],
        reason: { code: 'DATE_IN_PAST', params: {} },
      }),
    ).toEqual({ date: '26/11/2026', time: '08:30', at: '01/04/2026 01:30', limit: 3, name: 'NAME' })
  })

  test('GivenAnInstantParamNoCalendarHas_WhenFilledIn_ThenItIsShownAsSentRatherThanFailing', () => {
    expect(replaceOf({ at: '2026-13-01T10:00Z' })).toEqual({ at: '2026-13-01T10:00Z' })
  })

  test('GivenAParamNamedLikeAnI18nextOption_WhenFilledIn_ThenTheCodesOwnTextIsShown', () => {
    const t = createI18n('en').getFixedT('en', 'problems')

    expect(
      problemText(t, 'TEXT_TOO_LONG', { maxLength: 5, lng: 'he', returnDetails: true, ns: 'x' }),
    ).toEqual({ text: 'This text is too long.', nextStep: 'Shorten it to 5 characters or fewer.' })
  })

  test('GivenACodeWithNoTextOfItsOwn_WhenLookedUp_ThenTheGeneralTextIsShown', () => {
    const t = createI18n('he').getFixedT('he', 'problems')

    expect(problemText(t, 'BRAND_NEW_RULE')).toEqual({
      text: 'משהו השתבש.',
      nextStep: 'יש לנסות שוב. אם זה חוזר, יש לפנות למנהל המערכת.',
    })
  })
})

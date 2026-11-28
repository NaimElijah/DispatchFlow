import { describe, expect, test } from 'vitest'
import { z } from 'zod'
import type { Problem } from '@dispatchflow/api-client'
import { planProblem } from './planProblem.ts'

const schema = z.object({
  name: z.string(),
  'a/b~c': z.string(),
  window: z.object({ start: z.string(), end: z.string() }),
  stops: z.array(z.object({ name: z.string() })),
  note: z.string().optional(),
})

type Values = z.input<typeof schema>

function invalidRequest(errors: Problem['errors']): Problem {
  return {
    type: 'about:blank',
    title: 'Bad Request',
    status: 400,
    code: 'INVALID_REQUEST',
    params: {},
    traceId: '0af7651916cd43dd8448eb211c80319c',
    errors,
  }
}

describe('planProblem', () => {
  test('GivenPointersToTheSchemasFields_WhenPlanned_ThenEachLandsOnItsField', () => {
    const plan = planProblem<Values>(
      invalidRequest([
        { code: 'WRONG_SIZE', pointer: '/name', params: { max: 100 } },
        { code: 'REQUIRED', pointer: '/window/start' },
        { code: 'REQUIRED', pointer: '/stops/0/name' },
        { code: 'WRONG_FORMAT', pointer: '/note' },
      ]),
      schema,
    )

    expect(plan.onFields.map(({ path }) => path)).toEqual([
      'name',
      'window.start',
      'stops.0.name',
      'note',
    ])
    expect(plan.onFields[0]).toEqual({ path: 'name', code: 'WRONG_SIZE', params: { max: 100 } })
    expect(plan.items).toEqual([])
    expect(plan.onField).toBe(false)
  })

  test('GivenAPointerToAListAsAWhole_WhenPlanned_ThenItStaysForTheSummary', () => {
    const tooMany = { code: 'WRONG_SIZE', pointer: '/stops', params: { max: 500 } }

    const plan = planProblem<Values>(invalidRequest([tooMany]), schema)

    expect(plan.onFields).toEqual([])
    expect(plan.items).toEqual([tooMany])
  })

  test('GivenAPointerWithEscapedCharacters_WhenPlanned_ThenItIsUnescapedPerRfc6901', () => {
    const plan = planProblem<Values>(
      invalidRequest([{ code: 'REQUIRED', pointer: '/a~1b~0c' }]),
      schema,
    )

    expect(plan.onFields.map(({ path }) => path)).toEqual(['a/b~c'])
  })

  test('GivenItemsTheFormHasNoFieldFor_WhenPlanned_ThenTheyStayForTheSummary', () => {
    const items = [
      { code: 'REQUIRED', pointer: '/address' },
      { code: 'UNREADABLE_BODY', pointer: '' },
      { code: 'REQUIRED', pointer: '/stops/first/name' },
      { code: 'MISSING_PARAMETER', parameter: { name: 'If-Match', in: 'header' as const } },
      { code: 'DUPLICATE_PROPERTY' },
    ]

    const plan = planProblem<Values>(invalidRequest(items), schema, () => undefined)

    expect(plan.onFields).toEqual([])
    expect(plan.items).toEqual(items)
  })

  test('GivenItemsWithoutAPointer_WhenTheFormMapsTheirCodes_ThenOnlyTheOneNamingNoParameterLandsOnAField', () => {
    const parameterItem = {
      code: 'MISSING_PARAMETER',
      parameter: { name: 'If-Match', in: 'header' as const },
    }

    const plan = planProblem<Values>(
      invalidRequest([{ code: 'DUPLICATE_PROPERTY' }, parameterItem]),
      schema,
      () => 'name',
    )

    expect(plan.onFields).toEqual([{ path: 'name', code: 'DUPLICATE_PROPERTY', params: {} }])
    expect(plan.items).toEqual([parameterItem])
  })

  test('GivenARuleRefusalTheFormMapsToAField_WhenPlanned_ThenItLandsThereAndTheSummaryNeedNotRepeatIt', () => {
    const plan = planProblem<Values>(
      {
        ...invalidRequest(undefined),
        status: 422,
        code: 'TEXT_TOO_LONG',
        params: { maxLength: 9 },
      },
      schema,
      (code) => (code === 'TEXT_TOO_LONG' ? 'name' : undefined),
    )

    expect(plan).toEqual({
      onFields: [{ path: 'name', code: 'TEXT_TOO_LONG', params: { maxLength: 9 } }],
      items: [],
      onField: true,
    })
  })

  test('GivenARuleRefusalTheFormDoesNotMap_WhenPlanned_ThenTheSummaryShowsItAlone', () => {
    const plan = planProblem<Values>(
      { ...invalidRequest(undefined), status: 422, code: 'DATE_FULL' },
      schema,
    )

    expect(plan).toEqual({ onFields: [], items: [], onField: false })
  })
})

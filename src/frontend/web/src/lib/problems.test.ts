import { describe, expect, expectTypeOf, test } from 'vitest'
import {
  PARAMETER_PLACES,
  type Problem,
  type ProblemItem,
  type ProblemParameter,
} from '@dispatchflow/api-client'
import { isProblem, toRequestFailure } from './problems.ts'

const TRACE_ID = '0af7651916cd43dd8448eb211c80319c'

/** The fields a type requires: those an object of it can't leave out. */
type RequiredKeys<T> = { [K in keyof T]-?: object extends Pick<T, K> ? never : K }[keyof T]

/** The fields the contract requires of a problem, held to the generated type below. */
const REQUIRED_FIELDS = ['type', 'title', 'status', 'code', 'params', 'traceId'] as const

function problem(status: number, code: string, extra: Partial<Problem> = {}): Problem {
  return {
    type: 'about:blank',
    title: 'Refused',
    status,
    code,
    params: {},
    traceId: TRACE_ID,
    ...extra,
  }
}

function answer(status: number, headers: Record<string, string> = {}): Response {
  return new Response(null, { status, headers })
}

describe('isProblem', () => {
  test('GivenTheContractsProblemWithItsItems_WhenChecked_ThenItIsAProblem', () => {
    expect(
      isProblem(
        problem(400, 'INVALID_REQUEST', {
          errors: [
            { code: 'WRONG_SIZE', pointer: '/name', params: { max: 4 } },
            { code: 'MISSING_PARAMETER', parameter: { name: 'If-Match', in: 'header' } },
          ],
        }),
      ),
    ).toBe(true)
  })

  test('GivenBodiesThatAreNotProblems_WhenChecked_ThenNoneIsAProblem', () => {
    for (const body of [
      '<html>Bad Gateway</html>',
      null,
      { ...problem(400, 'INVALID_REQUEST'), code: 'not a code' },
      { ...problem(400, 'INVALID_REQUEST'), type: 'https://example.com/probs/out-of-credit' },
      { ...problem(400, 'INVALID_REQUEST'), instance: 7 },
      { ...problem(400, 'INVALID_REQUEST'), detail: { text: 'not a string' } },
      { ...problem(400, 'INVALID_REQUEST'), params: undefined },
      { ...problem(400, 'INVALID_REQUEST'), traceId: '<b>not a trace id</b>' },
      { ...problem(400, 'INVALID_REQUEST'), errors: [{ pointer: '/name' }] },
      {
        ...problem(400, 'INVALID_REQUEST'),
        errors: [{ code: 'X', parameter: { name: 'q', in: 'body' } }],
      },
    ]) {
      expect(isProblem(body), JSON.stringify(body)).toBe(false)
    }
  })
})

describe('isProblem against the contract', () => {
  test('GivenAParameterInEachPlaceTheContractNames_WhenChecked_ThenEachIsAProblem', () => {
    for (const place of PARAMETER_PLACES) {
      const body = problem(400, 'INVALID_REQUEST', {
        errors: [{ code: 'MISSING_PARAMETER', parameter: { name: 'x', in: place } }],
      })
      expect(isProblem(body), place).toBe(true)
    }
  })

  test('GivenAProblemWithoutAFieldTheContractRequires_WhenChecked_ThenItIsNoProblem', () => {
    // `tsc -b`, the web app's build, checks the type assertions; `vitest run` doesn't.
    expectTypeOf<(typeof REQUIRED_FIELDS)[number]>().toEqualTypeOf<RequiredKeys<Problem>>()
    for (const field of REQUIRED_FIELDS) {
      const body: Record<string, unknown> = { ...problem(400, 'INVALID_REQUEST') }
      delete body[field]
      expect(isProblem(body), field).toBe(false)
    }
  })
})

describe('isProblem against the generated types', () => {
  test('GivenTheGeneratedTypes_WhenTheGuardsFieldsAreCompared_ThenTheyAreTheContracts', () => {
    // `tsc -b`, the web app's build, checks these; `vitest run` doesn't.
    expectTypeOf<RequiredKeys<ProblemItem>>().toEqualTypeOf<'code'>()
    expectTypeOf<RequiredKeys<ProblemParameter>>().toEqualTypeOf<'name' | 'in'>()
    expectTypeOf<Problem['type']>().toEqualTypeOf<'about:blank'>()
  })

  test('GivenAFailingParameterWithoutAFieldTheContractRequires_WhenChecked_ThenItIsNoProblem', () => {
    for (const parameter of [{ in: 'header' }, { name: 'If-Match' }]) {
      const item = { code: 'MISSING_PARAMETER', parameter } as unknown as ProblemItem
      expect(
        isProblem(problem(400, 'INVALID_REQUEST', { errors: [item] })),
        JSON.stringify(parameter),
      ).toBe(false)
    }
  })
})

describe('toRequestFailure', () => {
  test('GivenStaleVersionAs412OrAs409_WhenRead_ThenBothAreStale', () => {
    for (const status of [412, 409]) {
      const body = problem(status, 'STALE_VERSION', { params: { kind: 'Depot', id: 'd1' } })
      expect(toRequestFailure(answer(status), body)).toEqual({ kind: 'stale', problem: body })
    }
  })

  test('GivenTryAgainLaterWithRetryAfter_WhenRead_ThenItIsBusy', () => {
    const body = problem(503, 'TRY_AGAIN_LATER')
    expect(toRequestFailure(answer(503, { 'Retry-After': '2' }), body)).toEqual({
      kind: 'busy',
      problem: body,
    })
  })

  test('GivenTheUnexpectedError_WhenRead_ThenItIsUnexpected', () => {
    const body = problem(500, 'UNEXPECTED_ERROR')
    expect(toRequestFailure(answer(500), body)).toEqual({ kind: 'unexpected', problem: body })
  })

  test('GivenAProxysPageInsteadOfAProblem_WhenRead_ThenItIsUnexpectedWithNoProblem', () => {
    expect(toRequestFailure(answer(502), '<html>Bad Gateway</html>')).toEqual({
      kind: 'unexpected',
      problem: undefined,
    })
  })

  test('GivenA503ThatIsNoProblem_WhenRead_ThenItIsBusy', () => {
    expect(toRequestFailure(answer(503, { 'Retry-After': '5' }), '')).toEqual({
      kind: 'busy',
      problem: undefined,
    })
  })

  test('GivenARefusedRuleOrAMalformedRequest_WhenRead_ThenItIsRefused', () => {
    for (const body of [problem(422, 'DATE_FULL'), problem(400, 'INVALID_REQUEST')]) {
      expect(toRequestFailure(answer(body.status), body)).toEqual({
        kind: 'refused',
        problem: body,
      })
    }
  })
})

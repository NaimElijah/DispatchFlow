import { describe, expect, test } from 'vitest'
import { isProblem, toRequestFailure, type Problem } from './problems.ts'

const TRACE_ID = '0af7651916cd43dd8448eb211c80319c'

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
      { ...problem(400, 'INVALID_REQUEST'), params: undefined },
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

describe('toRequestFailure', () => {
  test('GivenStaleVersionAs412OrAs409_WhenRead_ThenBothAreStale', () => {
    for (const status of [412, 409]) {
      const body = problem(status, 'STALE_VERSION', { params: { kind: 'Depot', id: 'd1' } })
      expect(toRequestFailure(answer(status), body)).toEqual({ kind: 'stale', problem: body })
    }
  })

  test('GivenTryAgainLaterWithRetryAfter_WhenRead_ThenItIsBusyForThoseSeconds', () => {
    const body = problem(503, 'TRY_AGAIN_LATER')
    expect(toRequestFailure(answer(503, { 'Retry-After': '2' }), body)).toEqual({
      kind: 'busy',
      problem: body,
      retryAfterSeconds: 2,
    })
  })

  test('GivenARetryAfterThatIsADate_WhenRead_ThenItIsBusyWithNoDelayRead', () => {
    const failure = toRequestFailure(
      answer(503, { 'Retry-After': 'Fri, 27 Nov 2026 10:00:00 GMT' }),
      problem(503, 'TRY_AGAIN_LATER'),
    )
    expect(failure).toMatchObject({ kind: 'busy', retryAfterSeconds: undefined })
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
      retryAfterSeconds: 5,
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

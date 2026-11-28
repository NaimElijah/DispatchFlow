import type { Problem } from '@dispatchflow/api-client'
import { http, HttpResponse } from 'msw/http'
import { expect, test } from 'vitest'
import { server } from '../test/server.ts'
import { answerOf } from './coreCalls.ts'
import { coreClient } from './coreClient.ts'
import { RequestError } from './problems.ts'

const PLANS = '/api/v1/plans'
const PLAN_ID = '0194d6f0-0000-7000-8000-000000000001'

function openPlan() {
  return coreClient.POST('/plans', { body: { date: '2030-01-01' } })
}

test('GivenACall_WhenTheWebAppsClientSendsIt_ThenItNamesTheWebAppAndItsRelease', async () => {
  const sent: Headers[] = []
  server.use(
    http.post(PLANS, ({ request }) => {
      sent.push(request.headers)
      return HttpResponse.json({ planId: PLAN_ID }, { status: 201, headers: { ETag: '"0"' } })
    }),
  )

  const answer = answerOf(await openPlan())

  expect(answer).toEqual({ data: { planId: PLAN_ID }, etag: '"0"' })
  expect(sent[0].get('X-App')).toBe('web')
  expect(sent[0].get('X-App-Version')).toBe(__APP_VERSION__)
  expect(__APP_VERSION__).toMatch(/^[0-9]+\.[0-9]+\.[0-9]+$/)
})

test('GivenARefusal_WhenItsAnswerIsRead_ThenItIsARefusedRequestErrorWithTheProblem', async () => {
  const problem: Problem = {
    type: 'about:blank',
    title: 'Unprocessable Content',
    status: 422,
    code: 'PLAN_ALREADY_EXISTS',
    params: { date: '2030-01-01', existing: PLAN_ID },
    traceId: '4bf92f3577b34da6a3ce929d0e0e4736',
  }
  server.use(
    http.post(PLANS, () =>
      HttpResponse.json(problem, {
        status: 422,
        headers: { 'Content-Type': 'application/problem+json' },
      }),
    ),
  )

  const result = await openPlan()

  expect(() => answerOf(result)).toThrow(RequestError)
  expect(() => answerOf(result)).toThrow(
    expect.objectContaining({ failure: { kind: 'refused', problem } }),
  )
})

test('GivenANetworkFailure_WhenTheWebAppsClientCalls_ThenItFailsAsNetworkNotATypeError', async () => {
  server.use(http.post(PLANS, () => HttpResponse.error()))

  await expect(openPlan()).rejects.toEqual(
    expect.objectContaining({ name: 'RequestError', failure: { kind: 'network' } }),
  )
})

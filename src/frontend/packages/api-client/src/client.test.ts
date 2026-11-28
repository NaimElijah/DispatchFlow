import { http, HttpResponse } from 'msw/http'
import { setupServer } from 'msw/node'
import { afterAll, afterEach, beforeAll, expect, test } from 'vitest'
import { ACCEPT, createCoreClient, type CoreClientOptions } from './client.ts'
import type { Problem } from './problems.ts'

const ORIGIN = 'http://core.test'
const PLANS = `${ORIGIN}/api/v1/plans`
const PLAN_ID = '0194d6f0-0000-7000-8000-000000000001'

const server = setupServer()

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

function client(overrides: Partial<CoreClientOptions> = {}) {
  return createCoreClient({
    origin: ORIGIN,
    fetch: (request, init) => fetch(request, init),
    app: 'web',
    version: '1.4.0',
    ...overrides,
  })
}

function openPlan(options: Partial<CoreClientOptions> = {}) {
  return client(options).POST('/plans', { body: { date: '2030-01-01' } })
}

test('GivenAnyCall_WhenTheClientSendsIt_ThenItCarriesXAppWebAndXAppVersion', async () => {
  const sent: Headers[] = []
  server.use(
    http.post(PLANS, ({ request }) => {
      sent.push(request.headers)
      return HttpResponse.json({ planId: PLAN_ID }, { status: 201 })
    }),
  )

  const { data } = await openPlan()

  expect(data).toEqual({ planId: PLAN_ID })
  expect(sent).toHaveLength(1)
  expect(sent[0].get('X-App')).toBe('web')
  expect(sent[0].get('X-App-Version')).toBe('1.4.0')
  expect(sent[0].get('Accept')).toBe(ACCEPT)
})

test('GivenThePhoneApp_WhenItsClientSends_ThenXAppNamesThePhone', async () => {
  const apps: (string | null)[] = []
  server.use(
    http.post(PLANS, ({ request }) => {
      apps.push(request.headers.get('X-App'))
      return HttpResponse.json({ planId: PLAN_ID }, { status: 201 })
    }),
  )

  await openPlan({ app: 'phone', version: '2.0.1' })

  expect(apps).toEqual(['phone'])
})

test('GivenAProblemAnswer_WhenTheClientReadsIt_ThenTheErrorIsTheProblemBody', async () => {
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

  const { data, error, response } = await openPlan()

  expect(data).toBeUndefined()
  expect(error).toEqual(problem)
  expect(response.status).toBe(422)
})

test('GivenANetworkFailure_WhenTheClientCalls_ThenTheErrorReachesTheCaller', async () => {
  const lost = new Error('the connection was lost')

  // The app's fetch decides what a lost connection is, and the client passes it on unchanged.
  await expect(openPlan({ fetch: () => Promise.reject(lost) })).rejects.toBe(lost)
})

test('GivenAReleaseTheCoreWouldNotRead_WhenTheClientIsMade_ThenItIsRefused', () => {
  for (const version of ['1.4', 'v1.4.0', '1.4.0-beta', '', '01.4.0', '1.4.2147483648', '1x4x0']) {
    expect(() => client({ version }), version).toThrow(/three numbers/)
  }
})

test('GivenAReleaseAtTheEdgesTheCoreReads_WhenTheClientIsMade_ThenItIsAccepted', () => {
  for (const version of ['0.0.0', '2147483647.0.0', '10.20.30']) {
    expect(() => client({ version }), version).not.toThrow()
  }
})

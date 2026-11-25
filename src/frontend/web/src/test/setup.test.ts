import { http, HttpResponse } from 'msw/http'
import { expect, test } from 'vitest'
import { server, takeUnhandledRequests } from './server.ts'

/** Sends a request and returns what it was rejected with, or undefined when it was answered. */
async function rejectionOf(path: string): Promise<unknown> {
  return fetch(path).then(
    () => undefined,
    (error: unknown) => error,
  )
}

// Each test that sends an unanswered request takes the record itself; setup.ts fails any test that
// leaves one.
test('GivenNoHandlerForARequest_WhenATestSendsIt_ThenItFailsAsANetworkErrorAndIsRecorded', async () => {
  const rejection = await rejectionOf('/api/v1/no-handler?token=kept-out')

  expect(rejection).toBeInstanceOf(TypeError)
  expect(takeUnhandledRequests()).toEqual(['GET /api/v1/no-handler'])
})

test('GivenNoHandlerForAnAssetLikePath_WhenATestSendsIt_ThenItIsRefusedToo', async () => {
  const rejection = await rejectionOf('/api/v1/orders/export.csv')

  expect(rejection).toBeInstanceOf(TypeError)
  expect(takeUnhandledRequests()).toEqual(['GET /api/v1/orders/export.csv'])
})

// The next two run in order: the first adds an override, the second checks that setup.ts reset it.
test('GivenAnOverride_WhenItsTestSendsTheRequest_ThenTheOverrideAnswers', async () => {
  server.use(http.get('/api/v1/override', () => HttpResponse.json({ answered: true })))

  const response = await fetch('/api/v1/override')

  expect(await response.json()).toEqual({ answered: true })
})

test('GivenAnOverrideFromTheTestBefore_WhenThisTestSendsTheRequest_ThenNoHandlerAnswers', async () => {
  const rejection = await rejectionOf('/api/v1/override')

  expect(rejection).toBeInstanceOf(TypeError)
  expect(takeUnhandledRequests()).toEqual(['GET /api/v1/override'])
})

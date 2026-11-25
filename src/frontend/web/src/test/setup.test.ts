import { render } from '@testing-library/react'
import { http, HttpResponse } from 'msw/http'
import { createElement, useEffect } from 'react'
import { expect, onTestFinished, test, vi } from 'vitest'
import { endTest, server, takeUnhandledRequests } from './server.ts'
import { checkNoStyleElements, removeStyleElements } from './styleCheck.ts'

/** Renders nothing, and sends a request no handler answers when it unmounts. */
function SendsOnUnmount() {
  useEffect(
    () => () => {
      fetch('/api/v1/sent-on-unmount').catch(() => undefined)
    },
    [],
  )
  return null
}

/** Sends a request and returns what it was rejected with, or undefined when it was answered. */
async function rejectionOf(path: string): Promise<unknown> {
  return fetch(path).then(
    () => undefined,
    (error: unknown) => error,
  )
}

// MSW's refusal: a TypeError whose cause is the network-error Response that server.ts throws. A
// request that reached the real network would be rejected with a connection error instead.
const refusedByMsw = { cause: expect.objectContaining({ type: 'error' }) }

// Each test that sends an unanswered request takes the record itself, or ends the test itself:
// setup.ts ends every test, and a record left behind fails it.
test('GivenNoHandlerForARequest_WhenATestSendsIt_ThenItFailsAsANetworkErrorAndIsRecorded', async () => {
  const rejection = await rejectionOf('/api/v1/no-handler?token=kept-out')

  expect(rejection).toBeInstanceOf(TypeError)
  expect(rejection).toMatchObject(refusedByMsw)
  expect(takeUnhandledRequests()).toEqual(['GET /api/v1/no-handler'])
})

test('GivenNoHandlerForAnAssetLikePath_WhenATestSendsIt_ThenItIsRefusedToo', async () => {
  const rejection = await rejectionOf('/api/v1/orders/export.csv')

  expect(rejection).toMatchObject(refusedByMsw)
  expect(takeUnhandledRequests()).toEqual(['GET /api/v1/orders/export.csv'])
})

test('GivenTwoRequestsNoHandlerAnswered_WhenEndTestRuns_ThenItThrowsNamingBoth', async () => {
  await rejectionOf('/api/v1/first')
  await rejectionOf('/api/v1/second')

  expect(endTest).toThrow('No MSW handler answered: GET /api/v1/first, GET /api/v1/second')
})

test('GivenAnOverride_WhenEndTestRuns_ThenTheOverrideIsDropped', async () => {
  server.use(http.get('/api/v1/override', () => HttpResponse.json({ answered: true })))
  expect(await (await fetch('/api/v1/override')).json()).toEqual({ answered: true })

  endTest()

  expect(await rejectionOf('/api/v1/override')).toMatchObject(refusedByMsw)
  expect(takeUnhandledRequests()).toEqual(['GET /api/v1/override'])
})

test('GivenAnOverrideAndAnUnansweredRequest_WhenEndTestRuns_ThenItThrowsAndStillDropsTheOverride', async () => {
  server.use(http.get('/api/v1/override', () => HttpResponse.json({ answered: true })))
  await rejectionOf('/api/v1/unanswered')

  expect(endTest).toThrow('No MSW handler answered: GET /api/v1/unanswered')

  expect(await rejectionOf('/api/v1/override')).toMatchObject(refusedByMsw)
  expect(takeUnhandledRequests()).toEqual(['GET /api/v1/override'])
})

// The rest check setup.ts's own afterEach, each within its own test: onTestFinished runs after the
// afterEach hooks, and test.fails passes only when its test, hooks included, fails.
test('GivenAnOverride_WhenItsTestEnds_ThenSetupDropsItBeforeTheNextTest', () => {
  server.use(http.get('/api/v1/override', () => HttpResponse.json({ answered: true })))

  onTestFinished(async () => {
    expect(await rejectionOf('/api/v1/override')).toMatchObject(refusedByMsw)
    expect(takeUnhandledRequests()).toEqual(['GET /api/v1/override'])
  })
})

test('GivenFakeTimersStillOn_WhenItsTestEnds_ThenSetupStillDropsItsOverride', () => {
  vi.useFakeTimers()
  server.use(http.get('/api/v1/override', () => HttpResponse.json({ answered: true })))

  onTestFinished(async () => {
    vi.useRealTimers()
    expect(await rejectionOf('/api/v1/override')).toMatchObject(refusedByMsw)
    expect(takeUnhandledRequests()).toEqual(['GET /api/v1/override'])
  })
})

test.fails(
  'GivenAnUnansweredRequestLeftBehind_WhenItsTestEnds_ThenSetupFailsThatTest',
  async () => {
    await rejectionOf('/api/v1/left-behind')
  },
)

test.fails('GivenARequestTheTestDidNotAwait_WhenItsTestEnds_ThenSetupFailsThatTest', () => {
  fetch('/api/v1/not-awaited').catch(() => undefined)
})

test.fails('GivenARequestSentOnUnmount_WhenItsTestEnds_ThenSetupFailsThatTest', () => {
  render(createElement(SendsOnUnmount))
})

test('GivenAStyleElementInTheDocument_WhenTheStyleCheckRuns_ThenItThrowsNamingIt', () => {
  const style = document.createElement('style')
  style.dataset.mantineStyles = 'inline'
  document.head.append(style)

  expect(checkNoStyleElements).toThrow("1 <style> element(s) (inline), which ADR-069's")

  removeStyleElements()
  expect(document.querySelector('style')).toBeNull()
})

test.fails('GivenAComponentThatRendersAStyleElement_WhenItsTestEnds_ThenSetupFailsThatTest', () => {
  render(createElement('style', null, 'p { color: red }'))
})

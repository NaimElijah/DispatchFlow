import { http, HttpResponse } from 'msw/http'
import { describe, expect, test, vi } from 'vitest'
import { server } from '../test/server.ts'
import { answerOf, fetchFromCore, retryRead } from './coreCalls.ts'
import { RequestError, type Problem } from './problems.ts'

const PATH = '/api/v1/examples/e1'
const address = () => new URL(PATH, window.location.origin)

const refusal: Problem = {
  type: 'about:blank',
  title: 'Refused',
  status: 422,
  code: 'INVALID_PHONE_NUMBER',
  params: {},
  traceId: '4bf92f3577b34da6a3ce929d0e0e4736',
}

/** What a call threw, or a failed expectation when it threw nothing. */
async function thrownBy(call: () => unknown): Promise<unknown> {
  try {
    await call()
  } catch (error) {
    return error
  }
  throw new Error('The call threw nothing')
}

describe('calls to the core', () => {
  test('GivenARequestThatGetsNoAnswer_WhenFetched_ThenItRejectsAsUnreachable', async () => {
    server.use(http.get(PATH, () => HttpResponse.error()))

    const thrown = await thrownBy(() => fetchFromCore(address()))

    expect(thrown).toBeInstanceOf(RequestError)
    expect((thrown as RequestError).failure).toEqual({ kind: 'network' })
  })

  test('GivenAnAnswerThatBreaksOffWhileItsBodyComesIn_WhenFetched_ThenItRejectsAsUnexpected', async () => {
    // A browser's fetch resolves once the headers are in, and rejects the body's read with a
    // TypeError when the connection drops mid-answer. MSW in Node reads a mocked body whole before
    // fetch resolves, so this one test hands over such an answer itself.
    let chunks = 0
    const brokenOff = new ReadableStream<Uint8Array>({
      pull(controller) {
        chunks += 1
        if (chunks === 1) {
          controller.enqueue(new TextEncoder().encode('{"id":'))
        } else {
          controller.error(new TypeError('network error'))
        }
      },
    })
    vi.stubGlobal('fetch', () => Promise.resolve(new Response(brokenOff, { status: 200 })))

    let thrown: unknown
    try {
      thrown = await thrownBy(() => fetchFromCore(address()))
    } finally {
      vi.unstubAllGlobals()
    }

    // The request arrived, so a write may be stored: the user reloads rather than resends.
    expect(thrown).toBeInstanceOf(RequestError)
    expect((thrown as RequestError).failure).toEqual({ kind: 'unexpected', problem: undefined })
  })

  test('GivenAnAbortedRequest_WhenFetched_ThenItRejectsAsTheAbortAndNotAsUnreachable', async () => {
    server.use(http.get(PATH, () => HttpResponse.json({ id: 'e1' })))
    const controller = new AbortController()
    controller.abort()

    const thrown = await thrownBy(() => fetchFromCore(address(), { signal: controller.signal }))

    expect(thrown).not.toBeInstanceOf(RequestError)
    expect((thrown as Error).name).toBe('AbortError')
  })

  test('GivenAnAnswerThatIsAFailure_WhenFetched_ThenItIsReturnedForTheCallerToRead', async () => {
    server.use(http.get(PATH, () => new HttpResponse(null, { status: 500 })))

    const response = await fetchFromCore(address())

    expect(response.status).toBe(500)
  })

  test('GivenASuccess_WhenItsAnswerIsRead_ThenItGivesTheDataAndTheETag', () => {
    const response = new Response(null, { status: 200, headers: { ETag: '"7"' } })

    expect(answerOf({ response, data: { id: 'e1' } })).toEqual({ data: { id: 'e1' }, etag: '"7"' })
  })

  test('GivenASuccessWithNoETag_WhenItsAnswerIsRead_ThenItsVersionIsNone', () => {
    const response = new Response(null, { status: 204 })

    expect(answerOf({ response })).toEqual({ data: undefined, etag: null })
  })

  test('GivenARefusal_WhenItsAnswerIsRead_ThenItThrowsHowTheCallFailed', async () => {
    const response = new Response(null, { status: 422 })

    const thrown = await thrownBy(() => answerOf({ response, error: refusal }))

    expect(thrown).toBeInstanceOf(RequestError)
    expect((thrown as RequestError).failure).toEqual({ kind: 'refused', problem: refusal })
  })

  test('GivenAReadThatFailedOnTheWay_WhenItFails_ThenItIsTriedAgainUpToThreeTimes', () => {
    // No answer, a busy core, or a proxy's page or a broken-off answer: each passes, and a read
    // changes nothing.
    for (const error of [
      new RequestError({ kind: 'network' }),
      new RequestError({ kind: 'busy', problem: undefined }),
      new RequestError({ kind: 'unexpected', problem: undefined }),
    ]) {
      const kind = error.failure.kind
      expect(
        [0, 1, 2].map((failures) => retryRead(failures, error)),
        kind,
      ).toEqual([true, true, true])
      expect(retryRead(3, error), kind).toBe(false)
    }
  })

  test('GivenAReadTheCoreRefusedOrFailedOn_WhenItFails_ThenItIsNotTriedAgain', () => {
    // It would fail the same way again, so the screen shows why at once.
    const coreFailure = { ...refusal, status: 500, code: 'UNEXPECTED_ERROR' }
    for (const error of [
      new RequestError({ kind: 'refused', problem: refusal }),
      new RequestError({ kind: 'stale', problem: refusal }),
      new RequestError({ kind: 'unexpected', problem: coreFailure }),
      new Error('A bug in the screen'),
    ]) {
      expect(retryRead(0, error), error.message).toBe(false)
    }
  })
})

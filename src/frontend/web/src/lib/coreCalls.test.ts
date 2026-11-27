import { http, HttpResponse } from 'msw/http'
import { describe, expect, test } from 'vitest'
import { server } from '../test/server.ts'
import { answerOf, fetchFromCore } from './coreCalls.ts'
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

  test('GivenAnAnswerThatIsAFailure_WhenFetched_ThenItIsReturnedForTheCallerToRead', async () => {
    server.use(http.get(PATH, () => new HttpResponse(null, { status: 500 })))

    const response = await fetchFromCore(address())

    expect(response.status).toBe(500)
  })

  test('GivenASuccess_WhenItsAnswerIsRead_ThenItGivesTheDataAndTheETag', () => {
    const response = new Response(null, { status: 200, headers: { ETag: '"7"' } })

    expect(answerOf({ response, data: { id: 'e1' } })).toEqual({ data: { id: 'e1' }, etag: '"7"' })
  })

  test('GivenARefusal_WhenItsAnswerIsRead_ThenItThrowsHowTheCallFailed', async () => {
    const response = new Response(null, { status: 422 })

    const thrown = await thrownBy(() => answerOf({ response, error: refusal }))

    expect(thrown).toBeInstanceOf(RequestError)
    expect((thrown as RequestError).failure).toEqual({ kind: 'refused', problem: refusal })
  })
})

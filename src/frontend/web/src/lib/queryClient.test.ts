import { describe, expect, test } from 'vitest'
import type { Problem } from '@dispatchflow/api-client'
import { RequestError } from './problems.ts'
import { createQueryClient } from './queryClient.ts'

const coreFailure: Problem = {
  type: 'about:blank',
  title: 'Failed',
  status: 500,
  code: 'UNEXPECTED_ERROR',
  params: {},
  traceId: '4bf92f3577b34da6a3ce929d0e0e4736',
}

/** Reads through the app's own cache, failing every time as given, and counts the reads. */
async function readsUntilItGivesUp(failure: RequestError): Promise<number> {
  const queryClient = createQueryClient({ retryDelay: 0 })
  let reads = 0
  await expect(
    queryClient.fetchQuery({
      queryKey: ['a list'],
      queryFn: () => {
        reads += 1
        throw failure
      },
    }),
  ).rejects.toBe(failure)
  return reads
}

describe('the app query cache', () => {
  test('GivenAReadThatGotNoAnswer_WhenTheAppsCacheReadsIt_ThenItTriesFourTimesInAll', async () => {
    expect(await readsUntilItGivesUp(new RequestError({ kind: 'network' }))).toBe(4)
  })

  test('GivenAReadTheCoreRefused_WhenTheAppsCacheReadsIt_ThenItReadsOnceAndShowsWhyAtOnce', async () => {
    const refusal = { ...coreFailure, status: 403, code: 'FORBIDDEN' }
    expect(await readsUntilItGivesUp(new RequestError({ kind: 'refused', problem: refusal }))).toBe(
      1,
    )
  })
})

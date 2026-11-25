import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { endTest, listen, server } from './server.ts'

// Taken before any test can install fake timers, which would leave the yield below pending forever.
const realSetTimeout = globalThis.setTimeout

beforeAll(() => {
  listen()
})

/** Resolves after one turn of the event loop, on the real timer whatever a test faked. */
function oneEventLoopTurn(): Promise<void> {
  return new Promise((resolve) => realSetTimeout(resolve, 0))
}

// Testing Library only unmounts between tests on its own when test globals are enabled; they are
// not. Both steps run, and both their errors are reported: a crash on unmount can't hide a missing
// handler, nor the reverse.
afterEach(async () => {
  const errors: unknown[] = []
  try {
    cleanup()
  } catch (error) {
    errors.push(error)
  }
  // MSW records an unanswered request a few process.nextTick turns after it is sent, and those
  // run only once this hook yields to the event loop. Without the yield, a request sent on unmount,
  // or one the test didn't await, would fail the next test, or none. One timer turn, not a sleep.
  await oneEventLoopTurn()
  try {
    endTest()
  } catch (error) {
    errors.push(error)
  }
  if (errors.length === 1) {
    throw errors[0]
  }
  if (errors.length > 1) {
    throw new AggregateError(errors, 'Unmounting and ending the test both failed')
  }
})

// Fails the file for a request that the file's own afterAll sent; one still in flight when the file
// ends is missed.
afterAll(() => {
  try {
    endTest()
  } finally {
    server.close()
  }
})

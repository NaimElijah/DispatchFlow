import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { listen, server, takeUnhandledRequests } from './server.ts'

beforeAll(() => {
  listen()
})

// Testing Library only unmounts between tests on its own when test globals are enabled; they are not.
afterEach(() => {
  cleanup()
  server.resetHandlers()
  // A request no handler answered fails its test, even when the code under test caught the
  // refusal, so a missing or mistyped handler can't let a test pass for the wrong reason.
  const unhandled = takeUnhandledRequests()
  if (unhandled.length > 0) {
    throw new Error(`No MSW handler answered: ${unhandled.join(', ')}`)
  }
})

afterAll(() => {
  server.close()
})

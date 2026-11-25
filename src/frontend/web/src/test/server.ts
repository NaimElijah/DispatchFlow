import { HttpResponse } from 'msw/http'
import { setupServer } from 'msw/node'
import { handlers } from './handlers.ts'

const unhandledRequests: string[] = []

/**
 * The one MSW server of the web app's tests. A test overrides an answer with `server.use()`, and
 * nothing else calls `setupServer`.
 */
export const server = setupServer(...handlers)

/**
 * Starts the server so that every request no handler answers fails with a network error and is
 * recorded, for `endTest` to fail the test that sent it. A function rather than MSW's `'error'`
 * strategy, which lets requests to asset-like paths (`.json`, `.csv`, `.pdf` and the like) through
 * to the network.
 */
export function listen(): void {
  server.listen({
    onUnhandledFrame: ({ frame }) => {
      unhandledRequests.push(nameOf(frame))
      // MSW answers a thrown Response with that response, and HttpResponse.error() is a network
      // error: a thrown Error would come back as a 500, which looks like the server's own answer.
      throw HttpResponse.error()
    },
  })
}

/** Returns the requests no handler answered since the last call, and forgets them. */
export function takeUnhandledRequests(): string[] {
  return unhandledRequests.splice(0)
}

/**
 * Ends a test: drops the handlers it added with `server.use()`, then fails it if a request went
 * unanswered, even one the code under test caught, so a missing or mistyped handler can't let a
 * test pass for the wrong reason. `setup.ts` calls it after every test and after a file's last.
 *
 * @throws Error naming each request no handler answered since the last `endTest` or
 *   `takeUnhandledRequests`, an HTTP one by its method and path
 */
export function endTest(): void {
  server.resetHandlers()
  const unhandled = takeUnhandledRequests()
  if (unhandled.length > 0) {
    throw new Error(`No MSW handler answered: ${unhandled.join(', ')}`)
  }
}

/** Names a frame by its method and path, never its query or body, which can carry values. */
function nameOf(frame: { protocol: string; data: unknown }): string {
  const { data } = frame
  if (data instanceof Object && 'request' in data && data.request instanceof Request) {
    return `${data.request.method} ${new URL(data.request.url).pathname}`
  }
  return `a ${frame.protocol} frame`
}

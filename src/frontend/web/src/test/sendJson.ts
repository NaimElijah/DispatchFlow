import type { SendResult } from '../lib/versionedSave.ts'

/**
 * Sends a JSON body as `@dispatchflow/api-client`'s openapi-fetch call will (#148), and answers in
 * its shape: the parsed `data` on a success, the parsed `error` body otherwise, as text when it is
 * no JSON. Tests use it as a versioned save's `send` until the generated client exists.
 *
 * @param method the HTTP method, such as `PUT`
 * @param path the path under the app's origin, such as `/api/v1/examples/e1`
 * @param body the body, sent as JSON
 * @param headers the precondition headers the save adds
 */
export async function sendJson(
  method: string,
  path: string,
  body: unknown,
  headers: Readonly<Record<string, string>>,
): Promise<SendResult<unknown>> {
  const response = await fetch(new URL(path, window.location.origin), {
    method,
    headers: { 'Content-Type': 'application/json', ...headers },
    body: JSON.stringify(body),
  })
  const text = await response.text()
  const parsed = parseJson(text)
  return response.ok ? { response, data: parsed } : { response, error: parsed }
}

function parseJson(text: string): unknown {
  if (text === '') {
    return undefined
  }
  try {
    return JSON.parse(text)
  } catch (error) {
    // A proxy's own page is HTML: the caller reads it as an answer that is no problem.
    if (error instanceof SyntaxError) {
      return text
    }
    throw error
  }
}

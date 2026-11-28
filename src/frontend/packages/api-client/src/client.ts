import createClient, { type Client, type Middleware } from 'openapi-fetch'
import type { paths } from './generated/client-api.ts'

/** The path every operation of the client API's major version 1 sits under (ADR-160). */
export const API_BASE_PATH = '/api/v1'

/**
 * What every call accepts: the success's JSON and the refusal's problem. An operation whose only
 * body is the error body declares `application/problem+json` alone, so a call that named only
 * `application/json` would be refused 406 (ADR-160 decision 4).
 */
export const ACCEPT = 'application/json, application/problem+json'

/**
 * One number of a release as the core's `AppVersion` reads it into the audit record: no leading
 * zero, and at most Java's largest `int` (ADR-082, ADR-110).
 */
const RELEASE_PART = '(0|[1-9][0-9]{0,9})'
const RELEASE = new RegExp(`^${RELEASE_PART}[.]${RELEASE_PART}[.]${RELEASE_PART}$`)
const LARGEST_PART = 2_147_483_647

function isRelease(version: string): boolean {
  const parts = RELEASE.exec(version)
  return parts !== null && parts.slice(1).every((part) => Number(part) <= LARGEST_PART)
}

/** The apps the core tells apart, exactly as `X-App` names them (ADR-082). */
export type AppName = 'web' | 'phone'

/** A client of the client API, typed by the contract's operations. */
export type CoreClient = Client<paths>

/** What {@link createCoreClient} needs from the app that calls the core. */
export interface CoreClientOptions {
  /** The core's origin, such as the page's own `window.location.origin`. */
  readonly origin: string
  /**
   * The fetch every call goes through, the app's own: the web app's `fetchFromCore`, which tells a
   * lost connection from a broken answer (ADR-154). No shared package imports an app, so the app
   * hands it in.
   */
  readonly fetch: (input: Request, init?: RequestInit) => Promise<Response>
  /** The app that calls, for `X-App`. */
  readonly app: AppName
  /** The app's release, three numbers such as `1.4.0`, for `X-App-Version`. */
  readonly version: string
}

/**
 * Makes the client every call of an app goes through (TS §10, ADR-161): openapi-fetch over the
 * types generated from `contracts/client-api/v1`, under `/api/v1` at the app's origin. Each call
 * carries `X-App`, `X-App-Version` and the `Accept` both answers match.
 *
 * @param options the app's origin, fetch, name and release
 * @returns the client
 * @throws Error when the release isn't three numbers as the core reads them, which it would drop
 *     from every record without saying so
 */
export function createCoreClient(options: CoreClientOptions): CoreClient {
  if (!isRelease(options.version)) {
    throw new Error(`An app's release is three numbers, such as 1.4.0, not ${options.version}`)
  }
  const client = createClient<paths>({
    baseUrl: new URL(API_BASE_PATH, options.origin).href,
    fetch: options.fetch,
  })
  client.use(clientHeaders(options.app, options.version))
  return client
}

/** Sets the headers every call carries, on the request openapi-fetch built. */
function clientHeaders(app: AppName, version: string): Middleware {
  return {
    onRequest({ request }) {
      request.headers.set('X-App', app)
      request.headers.set('X-App-Version', version)
      request.headers.set('Accept', ACCEPT)
      return request
    },
  }
}

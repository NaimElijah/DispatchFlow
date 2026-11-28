import createClient, {
  defaultPathSerializer,
  type Client,
  type Middleware,
  type PathSerializer,
} from 'openapi-fetch'
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
 * One number of a release as the core keeps it in the audit record: `Caller` takes one to nine
 * digits, and `AppVersion` then refuses a leading zero (ADR-082, ADR-110).
 */
const RELEASE_PART = '(0|[1-9][0-9]{0,8})'
const RELEASE = new RegExp(`^${RELEASE_PART}[.]${RELEASE_PART}[.]${RELEASE_PART}$`)

/**
 * The path values a URL resolves away: `..` and `.` move the call to another path, and an empty
 * value joins two segments.
 */
const DOT_SEGMENTS: ReadonlySet<string> = new Set(['', '.', '..'])

/** The apps the core tells apart, exactly as `X-App` names them (ADR-082). */
export type AppName = 'web' | 'phone'

/**
 * A client of the client API, typed by the contract's operations: openapi-fetch's own client, so
 * a call reads `{ data, error, response }`. The type gives `error` as the contract's `Problem`, but
 * nothing checks it: a proxy's page arrives as text, an empty answer as `undefined`. Read every
 * answer through the app's own guard, the web app's `answerOf`, never `error` itself.
 */
export type CoreClient = Client<paths>

/** What {@link createCoreClient} needs from the app that calls the core. */
export interface CoreClientOptions {
  /** The core's origin, alone, such as the page's own `window.location.origin`. */
  readonly origin: string
  /**
   * The fetch every call goes through, the app's own: the web app's `fetchFromCore`, which tells a
   * lost connection from a broken answer (ADR-154). No shared package imports an app, so the app
   * hands it in. openapi-fetch calls it with the `Request` it built.
   */
  readonly fetch: (input: Request) => Promise<Response>
  /** The app that calls, for `X-App`. */
  readonly app: AppName
  /** The app's release, three numbers such as `1.4.0`, for `X-App-Version`. */
  readonly version: string
}

/**
 * Makes the client every call of an app goes through (TS §12, ADR-161): openapi-fetch over the
 * types generated from `contracts/client-api/v1`, under `/api/v1` at the app's origin. Each call
 * carries `X-App`, `X-App-Version` and the `Accept` both answers match.
 *
 * A call whose path value is `..`, `.` or empty throws before anything is sent: a screen passes
 * route parameters, which a link can set, and the URL would resolve such a value to another path.
 *
 * @param options the app's origin, fetch, name and release
 * @returns the client
 * @throws Error when the origin carries more than an origin, whose path the client would drop,
 *     or when the release isn't three numbers as the core reads them, which it would drop from
 *     every record without saying so
 */
export function createCoreClient(options: CoreClientOptions): CoreClient {
  if (!isOrigin(options.origin)) {
    throw new Error(`The core's origin is a scheme, a host and a port alone, not ${options.origin}`)
  }
  if (!RELEASE.test(options.version)) {
    throw new Error(`An app's release is three numbers, such as 1.4.0, not ${options.version}`)
  }
  const client = createClient<paths>({
    baseUrl: new URL(API_BASE_PATH, options.origin).href,
    fetch: options.fetch,
    pathSerializer: refusingDotSegments,
  })
  client.use(clientHeaders(options.app, options.version))
  return client
}

function isOrigin(text: string): boolean {
  return URL.canParse(text) && new URL(text).origin === text
}

/** openapi-fetch's own path serializer, after refusing a value that would move the call. */
const refusingDotSegments: PathSerializer = (pathname, pathParams) => {
  for (const [name, value] of Object.entries(pathParams)) {
    if (DOT_SEGMENTS.has(String(value))) {
      throw new Error(`The path value ${name} may not be '${String(value)}'`)
    }
  }
  return defaultPathSerializer(pathname, pathParams)
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

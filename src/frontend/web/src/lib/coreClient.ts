import { createCoreClient } from '@dispatchflow/api-client'
import { fetchFromCore } from './coreCalls.ts'

/**
 * The web app's one client of the core (TS §10, ADR-161): the client generated from the contract,
 * at the page's own origin, sending through {@link fetchFromCore}, so a lost connection fails as a
 * `RequestError` rather than a bare `TypeError` (ADR-154), and naming the web app and its release on
 * every call (ADR-082). A screen reads each answer with `answerOf`, for example
 * `answerOf(await coreClient.POST('/plans', { body }))`.
 */
export const coreClient = createCoreClient({
  origin: window.location.origin,
  fetch: fetchFromCore,
  app: 'web',
  version: __APP_VERSION__,
})

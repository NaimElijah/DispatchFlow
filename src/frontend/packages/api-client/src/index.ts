/**
 * The one way both apps reach the core (TS §10, ADR-161): the types generated from
 * `contracts/client-api/v1` and a client over them. The types are generated at setup and before
 * every check, never committed.
 *
 * @module @dispatchflow/api-client
 */
export {
  ACCEPT,
  API_BASE_PATH,
  createCoreClient,
  type AppName,
  type CoreClient,
  type CoreClientOptions,
} from './client.ts'
export type { components, operations, paths } from './generated/client-api.ts'
export {
  PARAMETER_PLACES,
  PROBLEM_CODE_PATTERN,
  TRACE_ID_PATTERN,
  type Problem,
  type ProblemItem,
  type ProblemParameter,
} from './problems.ts'

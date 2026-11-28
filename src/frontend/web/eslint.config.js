import { readdirSync } from 'node:fs'
import { reactVite, typeChecked } from '@dispatchflow/eslint-config'
import { defineConfig, globalIgnores } from 'eslint/config'

/** The modules directly under `src/` that assemble the app: nothing below them imports them. */
const APP_MODULES = [
  'main',
  'App',
  'AppErrorBoundary',
  'AppProviders',
  'DatesLanguage',
  'DocumentLanguage',
  'i18n',
  'router',
  'routeTree',
]

/** The feature folders, one per bounded context (ADR-130), read from `src/features/`. */
const FEATURES = readdirSync(new URL('./src/features/', import.meta.url), { withFileTypes: true })
  .filter((entry) => entry.isDirectory())
  .map((entry) => entry.name)

/** Tests may import anything: they assemble the app through `src/test/`. */
const TEST_FILES = ['**/*.test.{ts,tsx}']

const toAppModules = {
  regex: `^(\\.\\./)+(${APP_MODULES.join('|')})(\\.tsx?)?$`,
  message:
    'Only src/ itself, the router and the tests assemble the app: import what you need from below, not from src/ (ADR-143, ADR-144).',
}

/** The one file that loads Day.js's Hebrew names, for Mantine's date pickers (ADR-153). */
const DAYJS_LOCALE_FILE = 'src/DatesLanguage.tsx'

const toDayjs = {
  regex: '^dayjs(/|$)',
  message:
    "Day.js is only @mantine/dates' own helper: show a date through @dispatchflow/formatters and Intl, and do its arithmetic with date-fns (TS §10, ADR-153).",
}

/**
 * Zod, whose setting that keeps it from compiling code at run time (ADR-069's policy) is set where
 * @dispatchflow/schemas takes it, before any schema is built (ADR-154). Its types may still come
 * from Zod itself.
 */
const toZod = {
  regex: '^zod(/|$)',
  allowTypeImports: true,
  message:
    "Take z from @dispatchflow/schemas, which turns off Zod's run-time compiling before any schema is built (ADR-154).",
}

/**
 * `fetchFromCore`, the fetch the one client sends through: a call made with it alone names no app
 * and is checked against no type (ADR-082, ADR-161).
 */
const toFetchFromCore = {
  regex: '(^|/)coreCalls(\\.ts)?$',
  importNames: ['fetchFromCore'],
  message:
    "Call the core through lib/coreClient.ts's coreClient: fetchFromCore alone names no app and checks no type (ADR-161).",
}

/** The one client of the core, the one file that sends through `fetchFromCore`. */
const CORE_CLIENT_FILE = 'src/lib/coreClient.ts'

const SERVER_MESSAGE =
  "Call the core through lib/coreClient.ts's coreClient, which @dispatchflow/api-client builds (ADR-161)."

const toTestCode = {
  regex: '(^|/)test(/|$)',
  message: 'Test code stays out of the app: only a test file imports src/test/ (ADR-144).',
}

/**
 * The pattern that refuses an import into any of the named folders, a bare `../features` included.
 *
 * @param {string[]} folders the folders the files may not import from
 */
function toFolders(folders) {
  return {
    regex: `(^|/)(${folders.join('|')})(/|$)`,
    message: `Imports point from src/ to features/, routes/, components/ and lib/, never back, and no feature imports another: this file may not import from ${folders.join(', ')} (ADR-143, ADR-144).`,
  }
}

/**
 * Refuses the given import patterns in the given non-test files. ESLint checks static imports and
 * re-exports, type-only ones included, and not `import()` expressions.
 *
 * @param {string[]} files the files the patterns apply to
 * @param {object[]} patterns `no-restricted-imports` patterns; a later block replaces, not merges
 * @param {string[]} [ignores] files among them the patterns spare, beside the tests
 */
function restrict(files, patterns, ignores = []) {
  return {
    files,
    ignores: [...TEST_FILES, ...ignores],
    rules: { 'no-restricted-imports': ['error', { patterns }] },
  }
}

export default defineConfig([
  globalIgnores(['dist']),
  reactVite,
  typeChecked(import.meta.dirname),
  // Only @dispatchflow/api-client talks to the server (TS §10, ADR-161): the app's one client sends
  // through coreCalls.ts's fetchFromCore, and the tests call fetch to try MSW itself.
  {
    files: ['src/**/*.{ts,tsx}'],
    ignores: ['src/lib/coreCalls.ts', 'src/test/**', '**/*.test.{ts,tsx}'],
    rules: {
      'no-restricted-globals': [
        'error',
        { name: 'fetch', message: SERVER_MESSAGE },
        { name: 'XMLHttpRequest', message: SERVER_MESSAGE },
      ],
      'no-restricted-properties': [
        'error',
        ...['window', 'globalThis', 'self'].map((object) => ({
          object,
          property: 'fetch',
          message: SERVER_MESSAGE,
        })),
        { object: 'navigator', property: 'sendBeacon', message: SERVER_MESSAGE },
      ],
    },
  },
  ...FEATURES.map((feature) =>
    restrict(
      [`src/features/${feature}/**`],
      [
        toAppModules,
        toTestCode,
        toDayjs,
        toZod,
        toFetchFromCore,
        toFolders(FEATURES.filter((other) => other !== feature)),
      ],
    ),
  ),
  restrict(
    ['src/routes/**'],
    [toAppModules, toTestCode, toDayjs, toZod, toFetchFromCore, toFolders(['features'])],
  ),
  restrict(
    ['src/components/**'],
    [toAppModules, toTestCode, toDayjs, toZod, toFetchFromCore, toFolders(['features', 'routes'])],
  ),
  restrict(
    ['src/lib/**'],
    [
      toAppModules,
      toTestCode,
      toDayjs,
      toZod,
      toFetchFromCore,
      toFolders(['features', 'routes', 'components']),
    ],
    [CORE_CLIENT_FILE],
  ),
  // The one client sends through fetchFromCore.
  restrict(
    [CORE_CLIENT_FILE],
    [toAppModules, toTestCode, toDayjs, toZod, toFolders(['features', 'routes', 'components'])],
  ),
  restrict(['src/*.{ts,tsx}'], [toDayjs, toZod, toFetchFromCore], [DAYJS_LOCALE_FILE]),
  // The one file that may load Day.js still takes Zod only from @dispatchflow/schemas.
  restrict([DAYJS_LOCALE_FILE], [toZod, toFetchFromCore]),
  // The tests' own helpers, which no other block covers; the test files themselves stay free.
  restrict(['src/test/**'], [toDayjs, toZod]),
])

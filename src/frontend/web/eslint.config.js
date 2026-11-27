import { readdirSync } from 'node:fs'
import { reactVite } from '@dispatchflow/eslint-config'
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
  ...FEATURES.map((feature) =>
    restrict(
      [`src/features/${feature}/**`],
      [toAppModules, toTestCode, toDayjs, toFolders(FEATURES.filter((other) => other !== feature))],
    ),
  ),
  restrict(['src/routes/**'], [toAppModules, toTestCode, toDayjs, toFolders(['features'])]),
  restrict(
    ['src/components/**'],
    [toAppModules, toTestCode, toDayjs, toFolders(['features', 'routes'])],
  ),
  restrict(
    ['src/lib/**'],
    [toAppModules, toTestCode, toDayjs, toFolders(['features', 'routes', 'components'])],
  ),
  restrict(['src/*.{ts,tsx}'], [toDayjs], [DAYJS_LOCALE_FILE]),
])

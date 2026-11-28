import { expect, test } from 'vitest'
import { LINT_TIMEOUT, lintAs } from './lintAs.ts'

// Only @dispatchflow/api-client talks to the server (TS §10, ADR-161): each way below that a file
// of the app could send a request is refused, but in the one fetch the client sends through and in
// tests, which call fetch to try MSW itself. ESLint doesn't check import() expressions (ADR-144).

const SERVER_RULES = new Set(['no-restricted-globals', 'no-restricted-properties'])

/** Lints the code as if it sat in the given file, and says whether a rule refused it as a call. */
async function isRefused(code: string, filePath: string): Promise<boolean> {
  const messages = await lintAs(code, filePath)
  return messages.some((message) => message.ruleId !== null && SERVER_RULES.has(message.ruleId))
}

const SENDS = {
  fetch: "export const answer = fetch('/api/v1/plans')\n",
  'window.fetch': "export const answer = window.fetch('/api/v1/plans')\n",
  'globalThis.fetch': "export const answer = globalThis.fetch('/api/v1/plans')\n",
  'self.fetch': "export const answer = self.fetch('/api/v1/plans')\n",
  'a fetch taken from window':
    "const { fetch: send } = window\nexport const answer = send('/api/v1/plans')\n",
  XMLHttpRequest: 'export const request = new XMLHttpRequest()\n',
  'window.XMLHttpRequest': 'export const request = new window.XMLHttpRequest()\n',
  EventSource: "export const stream = new EventSource('/api/v1/plans')\n",
  WebSocket: "export const socket = new WebSocket('/api/v1/plans')\n",
  'navigator.sendBeacon': "export const sent = navigator.sendBeacon('/api/v1/plans')\n",
  'window.navigator.sendBeacon':
    "export const sent = window.navigator.sendBeacon('/api/v1/plans')\n",
}

test.each(
  Object.entries(SENDS).flatMap(([form, code]) =>
    [
      'src/features/planning/PlanPage.tsx',
      'src/components/form/useSchemaForm.ts',
      'src/lib/coreClient.ts',
      'src/main.tsx',
    ].map((filePath) => [form, filePath, code] as const),
  ),
)(
  'GivenACallThrough_%s_InAFileAt_%s_WhenLinted_ThenLintRefusesIt',
  async (_form, filePath, code) => {
    expect(await isRefused(code, filePath)).toBe(true)
  },
  LINT_TIMEOUT,
)

test.each(['src/lib/coreCalls.ts', 'src/test/setup.test.ts', 'src/lib/coreCalls.test.ts'])(
  'GivenAFileAt_%s_WhenItCallsFetch_ThenLintAllowsIt',
  async (filePath) => {
    expect(await isRefused(SENDS.fetch, filePath)).toBe(false)
  },
  LINT_TIMEOUT,
)

/** Lints an import of fetchFromCore as if it sat in the given file, and says whether it was refused. */
async function isImportRefused(filePath: string, specifier: string): Promise<boolean> {
  const messages = await lintAs(
    `import { fetchFromCore } from '${specifier}'\nexport { fetchFromCore }\n`,
    filePath,
  )
  return messages.some((message) => message.ruleId === 'no-restricted-imports')
}

test.each([
  ['src/features/planning/PlanPage.tsx', '../../lib/coreCalls.ts'],
  ['src/features/planning/PlanPage.tsx', '../../lib/coreCalls.js'],
  ['src/lib/versionedSave.ts', './coreCalls'],
  ['src/routes/AppLayout.tsx', '../lib/coreCalls.ts'],
  ['src/components/form/useSchemaForm.ts', '../../lib/coreCalls.ts'],
  ['src/lib/versionedSave.ts', './coreCalls.ts'],
  ['src/main.tsx', './lib/coreCalls.ts'],
])(
  'GivenAFileAt_%s_WhenItImportsFetchFromCoreFrom_%s_ThenLintRefusesTheImport',
  async (filePath, specifier) => {
    expect(await isImportRefused(filePath, specifier)).toBe(true)
  },
  LINT_TIMEOUT,
)

test.each([
  ['src/lib/coreClient.ts', './coreCalls.ts'],
  ['src/lib/coreCalls.test.ts', './coreCalls.ts'],
  ['src/test/sendJson.ts', '../lib/coreCalls.ts'],
])(
  'GivenAFileAt_%s_WhenItImportsFetchFromCoreFrom_%s_ThenLintAllowsTheImport',
  async (filePath, specifier) => {
    expect(await isImportRefused(filePath, specifier)).toBe(false)
  },
  LINT_TIMEOUT,
)

import { expect, test } from 'vitest'
import { LINT_TIMEOUT, lintAs } from './lintAs.ts'

/** Lints a call of the global fetch as if it sat in the given file, and says whether it was refused. */
async function isRefused(filePath: string): Promise<boolean> {
  const messages = await lintAs("export const answer = fetch('/api/v1/plans')\n", filePath)
  return messages.some((message) => message.ruleId === 'no-restricted-globals')
}

// Only @dispatchflow/api-client talks to the server (TS §10, ADR-161).
test.each([
  'src/features/planning/PlanPage.tsx',
  'src/components/form/useSchemaForm.ts',
  'src/lib/coreClient.ts',
  'src/main.tsx',
])(
  'GivenAFileAt_%s_WhenItCallsFetch_ThenLintRefusesIt',
  async (filePath) => {
    expect(await isRefused(filePath)).toBe(true)
  },
  LINT_TIMEOUT,
)

// fetchFromCore is the one fetch the client sends through, and tests call fetch to try MSW itself.
test.each(['src/lib/coreCalls.ts', 'src/test/setup.test.ts', 'src/lib/coreCalls.test.ts'])(
  'GivenAFileAt_%s_WhenItCallsFetch_ThenLintAllowsIt',
  async (filePath) => {
    expect(await isRefused(filePath)).toBe(false)
  },
  LINT_TIMEOUT,
)

import { ESLint } from 'eslint'
import { expect, test } from 'vitest'

// The package's own eslint.config.js, its type-reading layer included, which reads only the files a
// tsconfig holds, so the code is linted as the text of a file that exists. The web dev image never
// generates, so only types may come from the generated file (ADR-161 decision 6).
const eslint = new ESLint()
const A_FILE_OF_THE_PACKAGE = 'src/problems.ts'

// Loading the configuration and building the typed program take a few seconds the first time.
const LINT_TIMEOUT = 30_000

/** Lints the code as a file of the package, and says whether lint refused it. */
async function isRefused(code: string): Promise<boolean> {
  const [result] = await eslint.lintText(code, { filePath: A_FILE_OF_THE_PACKAGE })
  expect(result.fatalErrorCount, result.messages[0]?.message).toBe(0)
  return result.errorCount > 0
}

test.each([
  ["import { paths } from './generated/client-api.ts'\nexport type P = paths\n"],
  ["import { type paths } from './generated/client-api.ts'\nexport type P = paths\n"],
  ["export { components } from './generated/client-api.ts'\n"],
])(
  'GivenAnImportFromTheGeneratedFileThatRunsAtRunTime_WhenLinted_ThenItIsRefused: %s',
  async (code) => {
    expect(await isRefused(code)).toBe(true)
  },
  LINT_TIMEOUT,
)

test.each([
  ["import type { paths } from './generated/client-api.ts'\nexport type P = paths\n"],
  ["export type { components } from './generated/client-api.ts'\n"],
])(
  'GivenATypeOnlyImportFromTheGeneratedFile_WhenLinted_ThenItIsAllowed: %s',
  async (code) => {
    expect(await isRefused(code)).toBe(false)
  },
  LINT_TIMEOUT,
)

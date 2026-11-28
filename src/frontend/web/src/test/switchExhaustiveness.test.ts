import { ESLint } from 'eslint'
import { expect, test } from 'vitest'
import { LINT_TIMEOUT } from './lintAs.ts'

// The project's own eslint.config.js with its type-reading layer, which reads only the files a
// tsconfig holds, so the code is linted as the text of a file that exists (ADR-161).
const eslint = new ESLint()
const A_FILE_OF_THE_APP = 'src/lib/problems.ts'
const RULE = '@typescript-eslint/switch-exhaustiveness-check'

/** Lints a switch over a two-member union, and says whether the rule refused it. */
async function isRefused(cases: string): Promise<boolean> {
  const code = [
    "type Kind = 'network' | 'refused'",
    'export function f(kind: Kind): number {',
    '  switch (kind) {',
    cases,
    '  }',
    '}',
    '',
  ].join('\n')
  const [result] = await eslint.lintText(code, { filePath: A_FILE_OF_THE_APP })
  expect(result.fatalErrorCount, result.messages[0]?.message).toBe(0)
  return result.messages.some((message) => message.ruleId === RULE)
}

test(
  'GivenASwitchThatMissesAMemberOfItsUnion_WhenLinted_ThenItIsRefused',
  async () => {
    expect(await isRefused("    case 'network':\n      return 1")).toBe(true)
  },
  LINT_TIMEOUT,
)

test(
  'GivenASwitchThatLeavesAMemberToItsDefault_WhenLinted_ThenItIsRefused',
  async () => {
    expect(
      await isRefused("    case 'network':\n      return 1\n    default:\n      return 0"),
    ).toBe(true)
  },
  LINT_TIMEOUT,
)

test(
  'GivenASwitchThatNamesEveryMember_WhenLinted_ThenItIsAllowed',
  async () => {
    expect(
      await isRefused("    case 'network':\n      return 1\n    case 'refused':\n      return 2"),
    ).toBe(false)
  },
  LINT_TIMEOUT,
)

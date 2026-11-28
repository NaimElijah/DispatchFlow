import { ESLint } from 'eslint'

/**
 * The web app's own `eslint.config.js` without its type-reading layer (`typeChecked`, ADR-161),
 * which reads only the files a tsconfig holds on disk and fails any other path as a parsing error.
 * The lint tests name paths no file has yet, such as a component in a folder that is still empty,
 * and the rules they pin read no types, so turning the layer off leaves those rules as they are.
 */
const linter = new ESLint({
  overrideConfig: {
    files: ['**/*.{ts,tsx}'],
    languageOptions: { parserOptions: { projectService: false } },
    // A rule of the layer fails every file it can't read the types of, so each one goes off here.
    rules: { '@typescript-eslint/switch-exhaustiveness-check': 'off' },
  },
})

/**
 * Lints code as if it sat at a path of the web app, and returns what the lint said.
 *
 * @param code the file's text
 * @param filePath the path under the web app's folder, which need not exist
 * @returns the lint's messages
 * @throws Error when the code didn't parse: a test that looked only for a rule's message would
 *     otherwise pass on a file that was never linted
 */
export async function lintAs(
  code: string,
  filePath: string,
): Promise<ESLint.LintResult['messages']> {
  const [result] = await linter.lintText(code, { filePath })
  if (result.fatalErrorCount > 0) {
    throw new Error(`${filePath} did not parse: ${result.messages[0]?.message ?? 'no message'}`)
  }
  return result.messages
}

/** How long a lint test may take: loading the configuration and its plugins takes a few seconds. */
export const LINT_TIMEOUT = 30_000

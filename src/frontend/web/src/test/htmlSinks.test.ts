import { ESLint } from 'eslint'
import { expect, test } from 'vitest'

// The project's own eslint.config.js, read from the web app's folder, where Vitest runs.
const eslint = new ESLint()

/** Lints a snippet as if it sat in a feature's page, and says whether it was refused as HTML. */
async function isRefusedAsHtml(code: string, filePath = 'src/features/fleet/Probe.tsx') {
  const [result] = await eslint.lintText(code, { filePath })
  return result.messages.some((message) => message.ruleId === 'no-restricted-syntax')
}

// Loading the configuration and its plugins takes a few seconds the first time.
const LINT_TIMEOUT = 30_000

test.each([
  [
    'the JSX attribute',
    'export const A = (h: string) => <div dangerouslySetInnerHTML={{ __html: h }} />',
  ],
  [
    'a spread object',
    'export const A = (h: string) => <div {...{ dangerouslySetInnerHTML: { __html: h } }} />',
  ],
  [
    'a quoted key',
    "export const p = (h: string) => ({ 'dangerouslySetInnerHTML': { __html: h } })",
  ],
  ['innerHTML', 'export function f(e: Element, h: string) { e.innerHTML = h }'],
  ['outerHTML', 'export function f(e: Element, h: string) { e.outerHTML = h }'],
  [
    'insertAdjacentHTML',
    "export function f(e: Element, h: string) { e.insertAdjacentHTML('beforeend', h) }",
  ],
  ['document.write', 'export function f(h: string) { document.write(h) }'],
])(
  'GivenHtmlWrittenThrough_%s_WhenLinted_ThenItIsRefused',
  async (_form, code) => {
    expect(await isRefusedAsHtml(code)).toBe(true)
  },
  LINT_TIMEOUT,
)

test(
  'GivenAJsxFile_WhenItWritesHtml_ThenItIsLintedAndRefusedToo',
  async () => {
    const code = 'export const A = (h) => <div dangerouslySetInnerHTML={{ __html: h }} />'
    expect(await isRefusedAsHtml(code, 'src/features/fleet/Probe.jsx')).toBe(true)
  },
  LINT_TIMEOUT,
)

test(
  'GivenTextRenderedAsText_WhenLinted_ThenItIsAllowed',
  async () => {
    const code = 'export const A = (text: string) => <p title={text}>{text}</p>'
    expect(await isRefusedAsHtml(code)).toBe(false)
  },
  LINT_TIMEOUT,
)

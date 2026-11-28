import { expect, test } from 'vitest'
import { LINT_TIMEOUT, lintAs } from './lintAs.ts'

/** Lints a snippet as if it sat in a feature's page, and says whether it was refused as HTML. */
async function isRefusedAsHtml(code: string, filePath = 'src/features/fleet/Probe.tsx') {
  const messages = await lintAs(code, filePath)
  return messages.some((message) => message.ruleId === 'no-restricted-syntax')
}

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

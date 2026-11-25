import { expect } from 'vitest'

/**
 * Asserts that nothing of a thrown error reached the page (NFR-143): not its marker and not a source
 * location, in the markup, attributes included, or in the browser tab's title.
 *
 * @param marker a string the test put in the error's message, and so in its stack
 */
export function expectNoErrorDetail(marker: string): void {
  for (const [where, content] of [
    ['the page', document.documentElement.outerHTML],
    ['the tab title', document.title],
  ]) {
    expect(content, where).not.toContain(marker)
    expect(content, where).not.toMatch(/\.tsx?:\d+/)
  }
}

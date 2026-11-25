/**
 * The `<style>` elements the document has held since the last check: ADR-069's Content Security
 * Policy refuses every one in the browser, so a component that renders one, such as Mantine's
 * `AppShell`, `Grid`, a responsive style prop or a modal's scroll lock, would lose its styles there
 * (ADR-143).
 */
const seen = new Set<HTMLStyleElement>()

/** Records each `<style>` element a mutation added, at any depth of the nodes it added. */
function record(mutations: MutationRecord[]): void {
  for (const mutation of mutations) {
    for (const node of mutation.addedNodes) {
      if (node instanceof HTMLStyleElement) {
        seen.add(node)
      } else if (node instanceof Element) {
        node.querySelectorAll('style').forEach((style) => seen.add(style))
      }
    }
  }
}

const observer = new MutationObserver(record)

/**
 * Starts recording every `<style>` element added to the document, so one a test adds and removes
 * again, such as a modal's scroll lock that goes when the modal closes, is still caught.
 * `setup.ts` calls it before every test.
 */
export function watchForStyleElements(): void {
  observer.disconnect()
  seen.clear()
  observer.observe(document, { childList: true, subtree: true })
}

/**
 * Fails when the document holds a `<style>` element, or held one since the last check, and forgets
 * them. `setup.ts` runs it after every test, before unmounting.
 *
 * @throws Error naming each `<style>` element by its `data-mantine-styles` value, when it has one
 */
export function checkNoStyleElements(): void {
  record(observer.takeRecords())
  document.querySelectorAll('style').forEach((style) => seen.add(style))
  const found = [...seen]
  seen.clear()
  if (found.length > 0) {
    const names = found.map((style) => style.dataset.mantineStyles ?? 'no data-mantine-styles')
    throw new Error(
      `The page rendered ${found.length} <style> element(s) (${names.join(', ')}), which ` +
        "ADR-069's Content Security Policy blocks in the browser (ADR-143)",
    )
  }
}

/** Removes every `<style>` element left in the document, so one test's can't fail the next. */
export function removeStyleElements(): void {
  for (const style of document.querySelectorAll('style')) {
    style.remove()
  }
}

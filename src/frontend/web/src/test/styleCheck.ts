/**
 * Fails when the document holds a `<style>` element. ADR-069's Content Security Policy refuses every
 * one in the browser, so a component that renders one, such as Mantine's `AppShell`, `Grid` or a
 * responsive style prop, would lose its styles there (ADR-143). `setup.ts` runs it after every test,
 * before unmounting.
 *
 * @throws Error naming each `<style>` element by its `data-mantine-styles` value, when it has one
 */
export function checkNoStyleElements(): void {
  const found = [...document.querySelectorAll('style')]
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

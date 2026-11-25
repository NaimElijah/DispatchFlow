import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll, beforeEach } from 'vitest'
import { endTest, listen, server } from './server.ts'
import { checkNoStyleElements, removeStyleElements, watchForStyleElements } from './styleCheck.ts'

// Taken before any test can install fake timers, which would leave the yield below pending forever.
const realSetTimeout = globalThis.setTimeout

// jsdom has no matchMedia, and MantineProvider calls it for the colour scheme. This one, as
// Mantine's Vitest guide gives it, matches no media query: the desktop layout, light scheme.
window.matchMedia = (query: string): MediaQueryList => ({
  matches: false,
  media: query,
  onchange: null,
  addListener: () => undefined,
  removeListener: () => undefined,
  addEventListener: () => undefined,
  removeEventListener: () => undefined,
  dispatchEvent: () => false,
})

// jsdom does not scroll, and says so on the console each time the router scrolls a new page to
// its top. Nothing in a test depends on the scroll position.
window.scrollTo = () => undefined

beforeAll(() => {
  listen()
})

// From the test's first render, so a <style> element the test adds and removes is caught too.
beforeEach(() => {
  watchForStyleElements()
})

/** Resolves after one turn of the event loop, on the real timer whatever a test faked. */
function oneEventLoopTurn(): Promise<void> {
  return new Promise((resolve) => realSetTimeout(resolve, 0))
}

/** Runs a step and records what it threw, so that every step runs and every failure is reported. */
function recordingFailure(errors: unknown[], step: () => void): void {
  try {
    step()
  } catch (error) {
    errors.push(error)
  }
}

// Testing Library only unmounts between tests on its own when test globals are enabled; they are
// not. Every step runs, and every step's error is reported: a crash on unmount can't hide a missing
// handler or a <style> element, nor the reverse.
afterEach(async () => {
  const errors: unknown[] = []
  // Before unmounting: what the test left in the document, and what it added and took away.
  recordingFailure(errors, checkNoStyleElements)
  recordingFailure(errors, cleanup)
  removeStyleElements()
  // What the app writes outside its React tree, which unmounting leaves behind: the language
  // setting, the one thing it keeps in browser storage (ADR-143), and <html>'s language, direction
  // and the tab's title, so no test passes on what an earlier one set.
  localStorage.clear()
  document.documentElement.removeAttribute('lang')
  document.documentElement.removeAttribute('dir')
  document.title = ''
  // MSW records an unanswered request a few process.nextTick turns after it is sent, and those
  // run only once this hook yields to the event loop. Without the yield, a request sent on unmount,
  // or one the test didn't await, would fail the next test, or none. One timer turn, not a sleep.
  await oneEventLoopTurn()
  recordingFailure(errors, endTest)
  if (errors.length === 1) {
    throw errors[0]
  }
  if (errors.length > 1) {
    throw new AggregateError(errors, 'More than one step of ending the test failed')
  }
})

// Fails the file for a request that the file's own afterAll sent; one still in flight when the file
// ends is missed.
afterAll(() => {
  try {
    endTest()
  } finally {
    server.close()
  }
})

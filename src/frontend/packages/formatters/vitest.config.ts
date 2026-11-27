import { defineConfig } from 'vitest/config'

/**
 * The formatters' tests run in a zone far from Israel's, so one that took the computer's zone in
 * place of `Asia/Jerusalem` fails on every computer, an Israeli one included (ADR-088).
 */
export default defineConfig({
  test: {
    env: { TZ: 'America/New_York' },
  },
})

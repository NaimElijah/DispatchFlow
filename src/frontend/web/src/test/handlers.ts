import type { AnyHandler } from 'msw'

/**
 * The API answers every test starts with, one happy path per operation. The ticket that builds a
 * screen adds the handlers its operations need.
 */
export const handlers: readonly AnyHandler[] = []

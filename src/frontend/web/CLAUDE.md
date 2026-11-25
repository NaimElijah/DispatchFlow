# Web app

The web app for admins, dispatchers and drivers ([ADR-035]). Drivers work in it online only.
React 19, TypeScript 6.0, Vite 8, pnpm.

## Layout

- `src/main.tsx` mounts `src/App.tsx`.
- `src/test/setup.ts` configures Vitest; `vite.config.ts` sets the jsdom environment.
- `src/test/server.ts` is the tests' one MSW server, over the handlers in `src/test/handlers.ts`.

## Rules

- Mixed-direction text sits in `<bdi>` (NFR-136), and fonts come through Fontsource ([TS §10]).
- Lay it out for a desktop screen, except the driver screens, which must also work in a phone's
  browser (NFR-15, NFR-219).
- While a route is in progress and the page is open and visible, the app reports the driver's
  location every 30 seconds. A hidden page reports nothing (FR-243, [TS §10]), and reporting stops at a hand-over, when the driver is
  marked unavailable, and 1 hour after shift end on a route nobody finished (NFR-30, FR-328). It learns of route
  changes from its event stream (FR-347, [ADR-026]), and takes proof photos with the camera, never
  from files (NFR-204).
- Read the event stream with `fetch` and eventsource-parser, not the browser's `EventSource`, which
  cannot send an `Authorization` header ([TS §10]).
- Server data lives only in TanStack Query's in-memory cache, never persisted, and a refused refresh
  or an ended session drops it (NFR-269). Sign-out's answer sends `Clear-Site-Data` and deletes the
  refresh cookie, which is HttpOnly, so the app never reads it (NFR-233, [ADR-076]).

## Tests

- Vitest with Testing Library, in jsdom. MSW answers API calls in tests ([TS §10]).
- A request no handler answers, asset-like paths included, fails with a network error, and the
  test that sent it fails (`server.ts`, `setup.ts`). Happy paths go in `handlers.ts`; a test
  overrides an answer with `server.use()`, and nothing else calls `setupServer`. Import `http` and
  `HttpResponse` from `msw/http`.
- Vitest globals are off: import every Vitest API you use (`test`, `expect`, `vi`, …) from
  `vitest`, as `App.test.tsx` does.

## Commands

Run `just` recipes from the repository root, and `pnpm` from this folder.

| Command                                 | What it does                                                   |
| --------------------------------------- | -------------------------------------------------------------- |
| `just web-check`                        | ESLint, Prettier check, Vitest, then `tsc -b` and `vite build` |
| `just web-format`                       | Prettier                                                       |
| `just web-run`                          | Vite dev server                                                |
| `pnpm exec vitest run src/App.test.tsx` | One test file                                                  |

`web-check` and `web-format` run each script in this app and in every shared package that has it.

[ADR-026]: ../../../docs/ADRs/ADR-026-server-sent-events-and-push-for-live-updates.md
[ADR-035]: ../../../docs/ADRs/ADR-035-both-apps-serve-every-role.md
[ADR-076]: ../../../docs/ADRs/ADR-076-admin-and-dispatcher-data-stays-in-memory-on-devices.md
[TS §10]: ../../../docs/architecture/tech_stack.md#10-web-app

# Web app

The web app for admins, dispatchers and drivers ([ADR-035]). Drivers work in it online only.
React 19, TypeScript 6.0, Vite 8, pnpm; TanStack Router and Query, Mantine 9, i18next.

## Layout

- `src/main.tsx` builds the language setting, i18next, the query cache and the router, and mounts
  `src/App.tsx`, which holds the providers. `src/routeTree.ts` gathers every area's routes.
- `src/routes/` is the shell: the root route, `shellRoute` and its layout, `/`, the not-found and
  error pages.
- `src/features/<context>/` holds an area's pages and its `routes.ts`. `src/components/` holds the
  UI no area owns, the map's in `components/map/`, and `src/lib/` the code no layer owns.
- `src/styles/theme.css` holds what Mantine would otherwise inject as `<style>` elements.
- `src/test/setup.ts` configures Vitest; `vite.config.ts` sets the jsdom environment.
- `src/test/server.ts` is the tests' one MSW server, over the handlers in `src/test/handlers.ts`.

## Rules

- **Routes** ([ADR-143]). An area declares its routes in its own `routes.ts`, exports them as
  `<area>Routes = [...] as const`, and a new area adds its list to `src/routeTree.ts`:
  `createRoute({ getParentRoute: () => shellRoute, path: 'fleet/depots', component: DepotsPage })`.
  A route file is `.ts`, with no JSX. A page reaches its route by ID,
  `getRouteApi('/_shell/plans/$date')`, never by importing the route, which imports the page.
- A path parameter is checked in `params.parse`, which returns `false` for a bad value, so the
  address shows the not-found page, as `/plans/$date` does. A loader that finds its subject
  missing throws `notFound()`; a page that does so while rendering sets
  `notFoundComponent: NotFoundPage` on its route, or the shell is lost ([ADR-144]).
- Imports point from `src/` to `features/`, `routes/`, `components/` and `lib/`, never back; no
  feature imports another; only tests import `src/test/` (`eslint.config.js`, pinned by
  `src/test/importDirection.test.ts`; `import()` is not checked). What two features need goes in
  `lib/`, `components/` or a package ([ADR-144]).
- **Sign-in** stands outside the shell: its route is parented on `rootRoute` and listed beside
  `shellRoute` in `routeTree.ts`, the session sits in `lib/` and reaches routes through the router
  context, and `shellRoute`'s `beforeLoad` guards every page ([ADR-144]).
- The app calls the core on its own origin, through `vite.config.ts`'s `/api/` proxy. Never turn on
  `changeOrigin`, rewrite `Host` or `Sec-Fetch-Site`, or turn `server.cors` back on: the stand-in
  signs in only what the browser sent (ADR-148). `just infra-check` proves all three (ADR-152).
- Writing HTML into the page fails ESLint, `dangerouslySetInnerHTML` and `innerHTML` included:
  translations are not HTML-escaped. Render text as text, or markup through `Trans`.
- **No `<style>` element** (`setup.ts` fails the test that adds one; ADR-069's policy refuses it):
  no `AppShell`, `Grid`, `SimpleGrid` or responsive style props; use a CSS module. A theme change
  is a CSS variable in `src/styles/theme.css`: `createTheme` reaches no CSS ([ADR-143]).
- A page title, path or search parameter carries IDs and dates only, never personal data: the
  browser keeps them in its history, which sign-out cannot clear ([ADR-076]).
- **Text on screen** comes from `@dispatchflow/i18n` through `useTranslation('<namespace>')`, never
  as a literal, and a page names its browser tab with `usePageTitle`.
- **Language.** The app opens in English, and only the Settings page changes it. The choice is kept
  under `dispatchflow.language`, the one key the app writes to browser storage, until FR-124 keeps
  it on the account ([ADR-143]).
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
- Open the whole app at an address with `renderApp({ path, now })` from `src/test/renderApp.tsx`,
  and query it with `findBy…`: the router renders its first route asynchronously. `setup.ts`
  clears `localStorage` after each test, and stubs `matchMedia` and `scrollTo`, which jsdom lacks.
- A request no handler answers, asset-like paths included, fails with a network error, and the
  test that sent it fails, one sent on unmount included (`endTest` in `server.ts`, called by
  `setup.ts`). Happy paths go in `handlers.ts`; a test overrides an answer with `server.use()`,
  and nothing else calls `setupServer`. Import `http` and `HttpResponse` from `msw/http`.
- Vitest globals are off: import every Vitest API you use (`test`, `expect`, `vi`, …) from
  `vitest`, as `App.test.tsx` does.

## Commands

Run `just` recipes from the repository root, and `pnpm` from this folder.

| Command                                 | What it does                                                    |
| --------------------------------------- | --------------------------------------------------------------- |
| `just web-check`                        | ESLint, Prettier check, Vitest, then `tsc -b` and `vite build`  |
| `just web-format`                       | Prettier                                                        |
| `just web-run`                          | Vite dev server, which sends no Content Security Policy         |
| `pnpm run build && pnpm run preview`    | The production build, served under ADR-069's policy, on `:4173` |
| `pnpm exec vitest run src/App.test.tsx` | One test file                                                   |

`web-check` and `web-format` run each script in this app and in every shared package that has it,
and fail on a package without the two `src/frontend/CLAUDE.md` requires of every package.

[ADR-026]: ../../../docs/ADRs/ADR-026-server-sent-events-and-push-for-live-updates.md
[ADR-035]: ../../../docs/ADRs/ADR-035-both-apps-serve-every-role.md
[ADR-076]: ../../../docs/ADRs/ADR-076-admin-and-dispatcher-data-stays-in-memory-on-devices.md
[ADR-143]: ../../../docs/ADRs/ADR-143-the-web-app-shell-declares-routes-per-area-opens-in-english-and-renders-no-style-element.md
[ADR-144]: ../../../docs/ADRs/ADR-144-the-web-apps-features-stay-apart-and-its-lower-folders-never-import-upward.md
[TS §10]: ../../../docs/architecture/tech_stack.md#10-web-app

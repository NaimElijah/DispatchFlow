# Phone app

The phone app for admins, dispatchers and drivers, with every capability of each role
([ADR-035]). It is built from one codebase for iOS and Android.
Expo SDK 58, React Native 0.88, React 19, TypeScript 6.0, npm.

## Layout

- `src/index.ts`, which `package.json`'s `main` names, registers `src/App.tsx`.
- `app.json` holds the Expo configuration (name "DispatchFlow", slug `dispatchflow-mobile`).
  `assets/` holds the icons and the splash image.

## Rules

- Direction switches with `I18nManager.forceRTL` ([TS §11]).
- Create no route folder before `expo-router` is installed at M10: the folder becomes the route
  root once it is ([ADR-130]).
- From the Pilot this app also sends its over-the-air update ID ([ADR-081], [TS §11]).
- Add a dependency with `npx expo install <package>`, so its version matches SDK 58. Put
  tooling under `devDependencies`.
- The web build (`expo export --platform web`) is a development aid and an end-to-end test target,
  not a client for users: drivers in a browser use the web app ([ADR-035]). The use cases of NFR-83
  must run in it online, and it reads its route every 30 seconds while open ([ADR-026]).
- Admin and Dispatcher screens are native here too, not a web view ([ADR-035], FR-120; the web view
  is rejected in [TS §23]). The driver-screen rules of `src/frontend/CLAUDE.md` bind only the driver
  screens.
- Data received through the Admin or Dispatcher role stays in memory only and is wiped at sign-out, at
  revocation and on a refused refresh ([ADR-076]).
- Never use AsyncStorage or a persisted query cache. The route cache and the report outbox live in
  the encrypted SQLCipher database (NFR-39, [TS §11]).
- The report outbox is a `ReportQueue` port over a SQLCipher adapter. Each report carries an
  idempotency key scoped to the signed-in user and a per-route sequence number, and stays queued
  until the server acknowledges it ([TS §11]).
- Proof photos come only from `expo-camera`. The bytes move into the encrypted database and the
  temporary file is deleted at once, and nothing comes from or reaches the gallery (NFR-204,
  [TS §11]).
- Background location uses `expo-location` with `expo-task-manager`, one position every 30 seconds
  while a route is in progress. It stops at sign-out, at a hand-over, when the driver is marked
  unavailable, and 1 hour after shift end on a route nobody finished (FR-31, NFR-30, FR-328,
  [TS §11]).
- A refused refresh wipes the route data and keeps the outbox. 14 days offline wipes the route data
  and the queued reports and proofs (NFR-234, NFR-235).
- A push carries no personal data, only that something changed. Push tokens are removed at sign-out,
  at revocation and when Expo reports the device gone (NFR-218, NFR-263).
- MapLibre needs a development build (`expo-dev-client`): Expo Go cannot load it ([TS §11]).
- `tsconfig.json` sets `"types": ["jest"]`, because TypeScript 6 no longer loads every
  installed `@types` package by default. A new global type package goes into that list.
- `osv-scanner.toml` lists the vulnerabilities the audit ignores, each with a reason and an
  `ignoreUntil` date ([ADR-016], [ADR-023]). The zod and deepmerge entries were accepted in
  [ADR-095]. Add an entry only with both, and fix or re-justify an entry when it expires.

## Tests

- Jest through `jest-expo`, with React Native Testing Library.
- In React Native Testing Library 14, `render`, `rerender`, `unmount`, `renderHook`, `act` and
  every `fireEvent` call return a promise: `await` each, as `App.test.tsx` does with `render`.

## Commands

Run `just` recipes from the repository root, and `npm` or `npx` from this folder.

| Command                 | What it does                                             |
| ----------------------- | -------------------------------------------------------- |
| `just mobile-check`     | ESLint, Prettier check, `tsc --noEmit`, Jest, web export |
| `just mobile-format`    | Prettier                                                 |
| `just mobile-run`       | Expo dev server for phones and the browser               |
| `npx jest App.test.tsx` | One test file                                            |

[ADR-016]: ../../../docs/ADRs/ADR-016-dependency-audit-with-osv-scanner.md
[ADR-023]: ../../../docs/ADRs/ADR-023-accept-minimatch-and-glob-advisories.md
[ADR-026]: ../../../docs/ADRs/ADR-026-server-sent-events-and-push-for-live-updates.md
[ADR-035]: ../../../docs/ADRs/ADR-035-both-apps-serve-every-role.md
[ADR-076]: ../../../docs/ADRs/ADR-076-admin-and-dispatcher-data-stays-in-memory-on-devices.md
[ADR-081]: ../../../docs/ADRs/ADR-081-eas-update-for-javascript-only-fixes-during-the-pilot.md
[ADR-095]: ../../../docs/ADRs/ADR-095-accept-the-zod-and-deepmerge-advisories-until-2027-01-31.md
[ADR-130]: ../../../docs/ADRs/ADR-130-every-folder-is-declared-ahead-empty-and-documented.md
[TS §11]: ../../../docs/architecture/tech_stack.md#11-phone-app
[TS §23]: ../../../docs/architecture/tech_stack.md#23-alternatives-rejected-across-the-stack

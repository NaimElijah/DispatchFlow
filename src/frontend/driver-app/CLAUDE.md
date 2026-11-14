# Driver app: the phone app

The phone app for admins, dispatchers and drivers, with every capability of each role
([ADR-035]). It is built from one codebase for iOS and Android.
Expo SDK 58, React Native 0.88, React 19, TypeScript 6.0, npm.

## Layout

- `index.ts` registers `App.tsx`, which today renders only a heading.
- `app.json` holds the Expo configuration (name "DispatchFlow Driver", slug
  `dispatchflow-driver`). `assets/` holds the icons and the splash image.
- Tests sit next to the code they test, as `*.test.tsx`.

## Rules

- Add a dependency with `npx expo install <package>`, so its version matches SDK 58. Put
  tooling under `devDependencies`.
- The web build (`expo export --platform web`) is a development aid and an end-to-end test target,
  not a client for users: drivers in a browser use the web app ([ADR-035]). The use cases of NFR-83
  must run in it online, and it reads its route every 30 seconds while open ([ADR-026]).
- Admin and Dispatcher screens are native here too, not a web view ([ADR-035], FR-120; the web view
  is rejected in [TS §23]). Driver-only rules, such as no action while moving (NFR-45) and no prompt
  hiding the route (NFR-139), bind the driver screens only.
- Data received through the Admin or Dispatcher role stays in memory only and is wiped at sign-out, at
  revocation and on a refused refresh ([ADR-076]).
- Never use AsyncStorage or a persisted query cache. The route cache and the report outbox live in
  the encrypted SQLCipher database (NFR-39, [TS §11]).
- The report outbox is a `ReportQueue` port over a SQLCipher adapter. Each report carries an
  idempotency key scoped to the signed-in user and a per-route sequence number, and stays queued
  until the server acknowledges it ([TS §11]).
- `tsconfig.json` sets `"types": ["jest"]`, because TypeScript 6 no longer loads every
  installed `@types` package by default. A new global type package goes into that list.
- `osv-scanner.toml` lists the vulnerabilities the audit ignores, each with a reason and an
  `ignoreUntil` date ([ADR-016], [ADR-023]). The zod and deepmerge entries await the owner's
  review. Add an entry only with both, and fix or re-justify an entry when it expires.

## Tests

- Jest through `jest-expo`, with React Native Testing Library.
- Query the screen by role, label or text, never by component internals.
- End-to-end tests will run the same use cases on this app's web build as on the web app (NFR-83).
  Native behaviour is checked by hand on real phones ([ADR-028], NFR-140).

## Commands

Run `just` recipes from the repository root, and `npm` or `npx` from this folder.

| Command                  | What it does                                             |
| ------------------------ | -------------------------------------------------------- |
| `just driver-app-check`  | ESLint, Prettier check, `tsc --noEmit`, Jest, web export |
| `just driver-app-format` | Prettier                                                 |
| `just driver-app-run`    | Expo dev server for phones and the browser               |
| `npx jest App.test.tsx`  | One test file                                            |

[ADR-026]: ../../../docs/ADRs/ADR-026-server-sent-events-and-push-for-live-updates.md
[ADR-016]: ../../../docs/ADRs/ADR-016-dependency-audit-with-osv-scanner.md
[ADR-023]: ../../../docs/ADRs/ADR-023-accept-minimatch-and-glob-advisories.md
[ADR-028]: ../../../docs/ADRs/ADR-028-driver-app-through-test-tracks-with-a-device-checklist.md
[ADR-035]: ../../../docs/ADRs/ADR-035-both-apps-serve-every-role.md
[ADR-076]: ../../../docs/ADRs/ADR-076-admin-and-dispatcher-data-stays-in-memory-on-devices.md
[TS §11]: ../../../docs/architecture/tech_stack.md#11-phone-app
[TS §23]: ../../../docs/architecture/tech_stack.md#23-alternatives-rejected-across-the-stack

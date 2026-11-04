# Driver app

The app drivers use on the road. It is built from one codebase for iOS, Android and the browser.
Expo SDK 58, React Native 0.88, React 19, TypeScript 6.0, npm.

## Layout

- `index.ts` registers `App.tsx`, which today renders only a heading.
- `app.json` holds the Expo configuration (name "DispatchFlow Driver", slug
  `dispatchflow-driver`). `assets/` holds the icons and the splash image.
- Tests sit next to the code they test, as `*.test.tsx`.

## Rules

- Add a dependency with `npx expo install <package>`, so its version matches SDK 58. Put
  tooling under `devDependencies`.
- The browser build is a real target ([ADR-003]). A feature that only a phone can offer must
  degrade in the browser. For example, the browser build reports location only while it is open
  (FR-243), and it needs a connection (FR-326).
- `tsconfig.json` sets `"types": ["jest"]`, because TypeScript 6 no longer loads every
  installed `@types` package by default. A new global type package goes into that list.
- `osv-scanner.toml` lists the vulnerabilities the audit ignores, each with a reason and an
  `ignoreUntil` date ([ADR-016], [ADR-023]). The zod and deepmerge entries await the owner's
  review. Add an entry only with both, and fix or re-justify an entry when it expires.

## Tests

- Jest through `jest-expo`, with React Native Testing Library.
- Query the screen by role, label or text, never by component internals.
- End-to-end tests will run on the web build. Native behaviour is checked by hand on real phones
  ([ADR-028], NFR-140).

## Commands

Run `just` recipes from the repository root, and `npm` or `npx` from this folder.

| Command                  | What it does                                             |
| ------------------------ | -------------------------------------------------------- |
| `just driver-app-check`  | ESLint, Prettier check, `tsc --noEmit`, Jest, web export |
| `just driver-app-format` | Prettier                                                 |
| `just driver-app-run`    | Expo dev server for phones and the browser               |
| `npx jest App.test.tsx`  | One test file                                            |

[ADR-003]: ../../../docs/ADRs/ADR-003-dispatcher-console-and-driver-app.md
[ADR-016]: ../../../docs/ADRs/ADR-016-dependency-audit-with-osv-scanner.md
[ADR-023]: ../../../docs/ADRs/ADR-023-accept-minimatch-and-glob-advisories.md
[ADR-028]: ../../../docs/ADRs/ADR-028-driver-app-through-test-tracks-with-a-device-checklist.md

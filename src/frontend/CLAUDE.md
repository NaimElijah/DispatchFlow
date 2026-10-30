# Frontend

DispatchFlow's two clients ([ADR-003]). Both talk only to `src/backend/core`, through
`contracts/client-api/v1`. Each app has its own `CLAUDE.md` with its layout, rules and commands.

| Folder | Who uses it | Runs on | Stack |
|---|---|---|---|
| `console/` | Admins and dispatchers | Desktop browsers | React, TypeScript, Vite, npm |
| `driver-app/` | Drivers | iOS, Android and the browser, from one codebase | React Native, Expo SDK 58, TypeScript, npm |

## Rules

- The API client is generated from `contracts/client-api/v1`, never written by hand, and
  generated code is never edited.
- The server decides what each role may do. The UI hides what a role cannot use, but never
  stands in for the server's check.
- Nothing secret goes into either app: whatever ships to a browser or a phone is public.
- Maps built on OpenStreetMap data show the attribution its licence requires.
- The two apps share no code yet. Add a shared package once hand-written code, not generated
  code, would otherwise be copied between them ([ADR-008]).
- TypeScript is held at 6.0 in both apps, because typescript-eslint does not support 7 yet
  ([ADR-012]).

## Tests

- Unit tests sit next to the code they test, named Given/When/Then.
- Browser end-to-end tests for both apps will live in `/tests/e2e`. The tool for native builds
  is not chosen yet.

## Format and lint

ESLint and Prettier in both apps, with the same Prettier style: no semicolons, single quotes,
100 columns. The console uses ESLint 10 and the driver app ESLint 9, the version Expo SDK 58
installs ([ADR-012]).

[ADR-003]: ../../docs/ADRs/ADR-003-dispatcher-console-and-driver-app.md
[ADR-008]: ../../docs/ADRs/ADR-008-monorepo-layout-under-src.md
[ADR-012]: ../../docs/ADRs/ADR-012-javascript-tooling-npm-eslint-prettier.md

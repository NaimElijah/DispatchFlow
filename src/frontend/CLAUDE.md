# Frontend

DispatchFlow's two clients, each serving Admins, Dispatchers and Drivers ([ADR-035]). Both talk
only to `src/backend/core`, through `contracts/client-api/v1`. Each app has its own `CLAUDE.md`
with its layout, rules and commands.

| Folder | Who uses it | Runs on | Stack |
|---|---|---|---|
| `console/`, the web app | Admins, dispatchers and drivers (drivers online only) | Desktop browsers; its driver screens also phone browsers | React, TypeScript, Vite, npm |
| `driver-app/`, the phone app | Admins, dispatchers and drivers, with every capability | iOS and Android, from one codebase; its web build is a development aid and a test target | React Native, Expo SDK 58, TypeScript, npm |

Accepted, and not in the tree yet: [ADR-039] replaces npm with a pnpm workspace and shared
`@dispatchflow/*` packages, from the Alpha; [ADR-037] renames `console/` to `src/web` and
`driver-app/` to `src/mobile`, and adds `src/packages`, in the M0 restructure.

## Rules

- The API client is generated from `contracts/client-api/v1`, never written by hand, and
  generated code is never edited. Once `@dispatchflow/api-client` exists, it alone talks to the
  server ([TS §10]).
- The client never decides feasibility. Violations come from the server's validator ([TS §10]).
- The web app's feature folders, `src/features/<context>`, mirror the bounded contexts ([TS §10]).
- An error is translated from its stable `code` through `@dispatchflow/i18n`. No stack trace and no
  bare code reaches a screen ([PL §6.2]).
- The phone app is built from Semester B (M10). Until then the driver screens run in the web
  app's browser ([TS §11], [PL §4.1]).
- The server decides what each role may do. The UI hides what a role cannot use, but never
  stands in for the server's check.
- Nothing secret goes into either app: whatever ships to a browser or a phone is public.
- Maps built on OpenStreetMap data show the attribution its licence requires.
- The two apps share no code yet. Add a shared package once hand-written code, not generated
  code, would otherwise be copied between them ([ADR-008]). [ADR-039] (Accepted) sets those packages
  up at the Alpha.
- TypeScript is held at 6.0 in both apps, because typescript-eslint does not support 7 yet
  ([ADR-012]).

## Tests

- Unit tests sit next to the code they test, named Given/When/Then.
- Browser end-to-end tests for both apps will live in `/tests/e2e`, run on the web app and on
  the phone app's web build (NFR-83). No end-to-end tool exists yet; [ADR-037] (Accepted) puts
  the end-to-end tests in a root `e2e/`, in the M0 restructure. Native builds get no automated UI tests: they are checked
  by hand on real phones (ADR-028, NFR-140).

## Format and lint

ESLint and Prettier in both apps, with the same Prettier style: no semicolons, single quotes,
100 columns. The web app uses ESLint 10 and the phone app ESLint 9, the version Expo SDK 58
installs ([ADR-012]).

[ADR-035]: ../../docs/ADRs/ADR-035-both-apps-serve-every-role.md
[ADR-008]: ../../docs/ADRs/ADR-008-monorepo-layout-under-src.md
[ADR-012]: ../../docs/ADRs/ADR-012-javascript-tooling-npm-eslint-prettier.md
[ADR-037]: ../../docs/ADRs/ADR-037-flat-layout-under-src-with-deploy-and-infra.md
[ADR-039]: ../../docs/ADRs/ADR-039-one-pnpm-workspace-for-both-apps-and-shared-packages.md
[PL §4.1]: ../../docs/init_impl_ref/technical_implementation_plan.md#41-whats-in-what-waits
[PL §6.2]: ../../docs/init_impl_ref/technical_implementation_plan.md#62-core-api-conventions
[TS §10]: ../../docs/architecture/tech_stack.md#10-web-app
[TS §11]: ../../docs/architecture/tech_stack.md#11-phone-app

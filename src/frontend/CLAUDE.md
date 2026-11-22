# Frontend

DispatchFlow's two clients, each serving Admins, Dispatchers and Drivers ([ADR-035]). Both talk
only to `src/backend/core`, through `contracts/client-api/v1`. Each app has its own `CLAUDE.md`
with its layout, rules and commands.

| Folder | Who uses it | Runs on | Stack |
|---|---|---|---|
| `web/`, the web app | Admins, dispatchers and drivers (drivers online only) | Desktop browsers; its driver screens also phone browsers | React, TypeScript, Vite, npm |
| `mobile/`, the phone app | Admins, dispatchers and drivers, with every capability | iOS and Android, from one codebase; its web build is a development aid and a test target | React Native, Expo SDK 58, TypeScript, npm |

Accepted, and not in the tree yet: [ADR-039] replaces npm with a pnpm workspace and shared
`@dispatchflow/*` packages, from the Alpha, in `src/frontend/packages/`. The folders have
[ADR-123]'s names: `console/` became `web/` and `driver-app/` became `mobile/`.

## Rules

- The API client is generated from `contracts/client-api/v1`, never written by hand, and
  generated code is never edited. Once `@dispatchflow/api-client` exists, it alone talks to the
  server ([TS §10]).
- The client never decides feasibility. Violations come from the server's validator ([TS §10]).
- The web app's feature folders, `src/features/<context>`, mirror the bounded contexts ([TS §10]).
- An error is translated from its stable `code` through `@dispatchflow/i18n`. No stack trace and no
  bare code reaches a screen ([ADR-043]).
- The phone app is built from Semester B (M10). Until then the driver screens run in the web
  app's browser ([TS §11], [roadmap §4.1]).
- The server decides what each role may do. The UI hides what a role cannot use, but never
  stands in for the server's check.
- Nothing secret goes into either app: whatever ships to a browser or a phone is public.
- The driver screens of both apps need no action while the vehicle is moving (NFR-45), and no
  prompt on them hides the route (NFR-139).
- Every screen is in Hebrew, right to left, and in English, with strings from i18next (NFR-27,
  [ADR-089]). In the web app, mixed-direction text sits in `<bdi>` (NFR-136) and fonts come through
  Fontsource ([TS §10]). The phone app switches direction with `I18nManager.forceRTL` ([TS §11]).
- Times are shown with `Intl` and `timeZone: 'Asia/Jerusalem'` ([ADR-088], [TS §10]).
- Every call carries `X-App-Version` and `X-App`, and a driver's calls also `X-Device-Time`
  ([ADR-082]). From the Pilot the phone app also sends its over-the-air update ID ([ADR-081],
  [TS §11]).
- Both apps meet WCAG 2.2 AA (NFR-46, [ADR-070]).
- Maps use MapLibre with the Protomaps tiles that Caddy serves, and show the attribution
  OpenStreetMap's licence requires ([ADR-084], [TS §9]).
- The two apps share no code yet. Add a shared package once hand-written code, not generated
  code, would otherwise be copied between them ([ADR-008], restated by [ADR-123]).
  [ADR-039] (Accepted) sets those packages up at the Alpha.
- TypeScript is held at 6.0 in both apps, because typescript-eslint does not support 7 yet
  ([ADR-039], [TS §10]).

## Tests

- Unit tests sit next to the code they test, as `*.test.ts` or `*.test.tsx`, named
  Given/When/Then.
- Query the screen by role, label or text, and assert on what the user sees, never on component
  internals or state.
- Browser end-to-end tests for both apps will live in `/tests/e2e`, run on the web app and on
  the phone app's web build (NFR-83). No end-to-end tool exists yet. Native builds get no automated UI
  tests: they are checked by hand on real phones (ADR-028, NFR-140).

## Format and lint

ESLint and Prettier in both apps, with the same Prettier style: no semicolons, single quotes,
100 columns. The web app uses ESLint 10 and the phone app ESLint 9, the version Expo SDK 58
installs ([ADR-039]).

[ADR-035]: ../../docs/ADRs/ADR-035-both-apps-serve-every-role.md
[ADR-008]: ../../docs/ADRs/ADR-008-monorepo-layout-under-src.md
[ADR-123]: ../../docs/ADRs/ADR-123-the-code-keeps-its-grouped-layout-and-three-components-take-new-names.md
[ADR-039]: ../../docs/ADRs/ADR-039-one-pnpm-workspace-for-both-apps-and-shared-packages.md
[ADR-070]: ../../docs/ADRs/ADR-070-security-and-accessibility-standards.md
[ADR-081]: ../../docs/ADRs/ADR-081-eas-update-for-javascript-only-fixes-during-the-pilot.md
[ADR-082]: ../../docs/ADRs/ADR-082-supported-phone-app-versions.md
[ADR-084]: ../../docs/ADRs/ADR-084-maplibre-in-both-apps-with-self-hosted-protomaps-tiles.md
[ADR-088]: ../../docs/ADRs/ADR-088-israel-local-time-for-every-date-and-schedule.md
[ADR-089]: ../../docs/ADRs/ADR-089-interface-languages.md
[roadmap §4.1]: ../../docs/roadmap.md#41-whats-in-what-waits
[ADR-043]: ../../docs/ADRs/ADR-043-generated-code-from-both-contracts-and-problem-details-errors.md
[TS §9]: ../../docs/architecture/tech_stack.md#9-routing-and-geodata
[TS §10]: ../../docs/architecture/tech_stack.md#10-web-app
[TS §11]: ../../docs/architecture/tech_stack.md#11-phone-app

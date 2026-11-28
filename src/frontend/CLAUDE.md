# Frontend

DispatchFlow's two clients, each serving Admins, Dispatchers and Drivers ([ADR-035]). Both talk
only to `src/backend/core`, through `contracts/client-api/v1`. Each app has its own `CLAUDE.md`
with its layout, rules and commands.

| Folder | Who uses it | Runs on | Stack |
|---|---|---|---|
| `web/`, the web app | Admins, dispatchers and drivers (drivers online only) | Desktop browsers; its driver screens also phone browsers | React, TypeScript, Vite, pnpm |
| `mobile/`, the phone app | Admins, dispatchers and drivers, with every capability | iOS and Android, from one codebase; its web build is a development aid and a test target | React Native, Expo SDK 58, TypeScript, npm, outside the workspace until M10 |

## Rules

- `@dispatchflow/api-client` alone talks to the server ([TS §10], ADR-161). `just web-gen`, which
  `just web-setup` and `just web-check` run, writes its `src/generated/` from
  `contracts/client-api/v1`: run it after a contract change, and never commit or edit that folder.
- The client never decides feasibility. Violations come from the server's validator ([TS §10]).
- An error is translated from its stable `code` through `@dispatchflow/i18n`. No stack trace and no
  bare code reaches a screen ([ADR-043]).
- The phone app is built from Semester B. Until then the driver screens run in the web
  app's browser ([TS §11], [roadmap §4.1]).
- The server decides what each role may do. The UI hides what a role cannot use, but never
  stands in for the server's check.
- Nothing secret goes into either app: whatever ships to a browser or a phone is public.
- The driver screens of both apps need no action while the vehicle is moving (NFR-45), and no
  prompt on them hides the route (NFR-139).
- Every screen is in Hebrew, right to left, and in English, with strings from i18next (NFR-27,
  [ADR-089]).
- The strings are `@dispatchflow/i18n`'s, one namespace per area: `src/he/<area>.ts` is typed
  against `src/en/<area>.ts` (`tsc`), and `resources.test.ts` fails a gap between them.
- Dates, times and quantities are shown through `@dispatchflow/formatters` ([ADR-088], [TS §10]).
- The client sets `X-App` and `X-App-Version`; a driver's calls add `X-Device-Time` ([ADR-082]).
- Both apps meet WCAG 2.2 AA (NFR-46, [ADR-070]).
- Maps use MapLibre with the Protomaps tiles that Caddy serves, and show the attribution
  OpenStreetMap's licence requires ([ADR-084], [TS §9]).
- Each app's feature folders, `src/features/<context>`, mirror the bounded contexts ([TS §10],
  [ADR-130]).
- Add a shared package once hand-written code, not generated code, would otherwise be copied
  between the two apps ([ADR-008], restated by [ADR-123]). The pnpm workspace is rooted here
  ([ADR-039]): `pnpm-workspace.yaml` lists `web` and `packages/*`, with one `pnpm-lock.yaml`, and
  each package is `@dispatchflow/*`. A `packages/` folder is a placeholder until the ticket that
  fills it adds its `package.json` ([ADR-130]).
- A TypeScript package ships its source, as `i18n` does: `exports` points at `src/index.ts`, its
  imports carry `.ts`, its `build` is `tsc -b`, and its `eslint.config.js` adds
  `typeChecked(import.meta.dirname, [.ts files outside every tsconfig])` (ADR-161). `just web-check`
  generates `api-client`'s types first ([ADR-143], [ADR-144]).
- pnpm fails an import that a package doesn't declare: declare each one in that package's own
  `package.json`.
- Depend on a `@dispatchflow/*` package only as `workspace:*`, added with `pnpm add --workspace`:
  a plain `pnpm add` resolves the name from the public registry. `web/src/test/workspace.test.ts`
  fails when `pnpm-lock.yaml` resolves one from a registry.
- `permissions` in `pnpm-workspace.yaml` holds what each dependency may do; never add
  `allowBuilds`, which it overrides. A dependency with an install script fails the install until
  it has `build: true` or `build: false`. One that asks to install agent skills waits for
  `pnpm approve`: refuse it with `skills: false` ([ADR-143]).
- A relative path in a shared tsconfig resolves against the shared file, so `include` and
  `tsBuildInfoFile` stay in each project's own tsconfig. So does `types`: each project names the
  type packages it declares.
- TypeScript is held at 6.0 in both apps, because typescript-eslint does not support 7 yet
  ([ADR-039], [TS §10]).

## Tests

- Unit tests sit next to the code they test, as `*.test.ts` or `*.test.tsx`.
- Query the screen by role, label or text, and assert on what the user sees.
- Browser end-to-end tests for both apps go in `/tests/e2e`, run on the web app and on the phone
  app's web build (NFR-83). Native builds get no automated UI tests: they are checked by hand on
  real phones (ADR-028, NFR-140).

## Format and lint

ESLint and Prettier in both apps, with the same Prettier style: no semicolons, single quotes,
100 columns. The web app uses ESLint 10 and the phone app ESLint 9, the version Expo SDK 58
installs ([ADR-039]).

- `@dispatchflow/eslint-config` is for ESLint 10 only: the phone app keeps its own configuration
  until it joins.
- Every package has `format` and `format:check` scripts: `requiredScripts` in
  `pnpm-workspace.yaml` fails `just web-format` and `just web-check` without them ([ADR-141]).
- Prettier's one file is `.prettierrc.json` here, for the web app and every package ([ADR-141]).
  Leave the phone app's identical copy alone: #147 deletes it.

[ADR-035]: ../../docs/ADRs/ADR-035-both-apps-serve-every-role.md
[ADR-008]: ../../docs/ADRs/ADR-008-monorepo-layout-under-src.md
[ADR-123]: ../../docs/ADRs/ADR-123-the-code-keeps-its-grouped-layout-and-three-components-take-new-names.md
[ADR-039]: ../../docs/ADRs/ADR-039-one-pnpm-workspace-for-both-apps-and-shared-packages.md
[ADR-070]: ../../docs/ADRs/ADR-070-security-and-accessibility-standards.md
[ADR-082]: ../../docs/ADRs/ADR-082-supported-phone-app-versions.md
[ADR-084]: ../../docs/ADRs/ADR-084-maplibre-in-both-apps-with-self-hosted-protomaps-tiles.md
[ADR-088]: ../../docs/ADRs/ADR-088-israel-local-time-for-every-date-and-schedule.md
[ADR-089]: ../../docs/ADRs/ADR-089-interface-languages.md
[ADR-130]: ../../docs/ADRs/ADR-130-every-folder-is-declared-ahead-empty-and-documented.md
[ADR-141]: ../../docs/ADRs/ADR-141-prettiers-settings-live-in-one-file-in-src-frontend.md
[ADR-143]: ../../docs/ADRs/ADR-143-the-web-app-shell-declares-routes-per-area-opens-in-english-and-renders-no-style-element.md
[ADR-144]: ../../docs/ADRs/ADR-144-the-web-apps-features-stay-apart-and-its-lower-folders-never-import-upward.md
[roadmap §4.1]: ../../docs/roadmap.md#41-whats-in-what-waits
[ADR-043]: ../../docs/ADRs/ADR-043-generated-code-from-both-contracts-and-problem-details-errors.md
[TS §9]: ../../docs/architecture/tech_stack.md#9-routing-and-geodata
[TS §10]: ../../docs/architecture/tech_stack.md#10-web-app
[TS §11]: ../../docs/architecture/tech_stack.md#11-phone-app

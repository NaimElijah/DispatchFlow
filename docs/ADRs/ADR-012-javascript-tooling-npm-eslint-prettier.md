# ADR-012: Both frontend apps use npm, ESLint and Prettier, with TypeScript held at 6.0

- **Status:** Superseded by ADR-039
- **Date:** 2026-10-30

## Context

Two apps are written in TypeScript and React: the dispatcher console in `src/frontend/console`
(Vite, for desktop browsers) and the driver app in `src/frontend/driver-app` (React Native and
Expo SDK 58, for iOS, Android and the browser). See [ADR-003]. Each has its own `package.json`
and `package-lock.json`, and the two share no code yet ([ADR-008]).

The forces:

- **Both apps are checked by the same CI that checks everything else** ([NFR-16], [ADR-009]).
  Each needs a lint, a format check, tests and a build that one recipe can run.
- **A contributor who moves between the apps should meet one style.** A linter and a formatter
  that differ between the apps double what they have to know.
- **Some current versions do not work together yet.**
  - typescript-eslint 8.72 declares support for TypeScript `>=4.8.4 <6.1.0` in both lockfiles.
  - The create-vite template pinned TypeScript 7.0, and `npm install` failed with `ERESOLVE`.
  - The create-vite template now defaults to Oxlint, not ESLint.
- **Expo owns the versions of the packages it ships with.** `npx expo install` picks the version
  that matches the SDK. A version chosen by hand can break the native build.

## Decision

We use npm, ESLint and Prettier in both apps, and hold TypeScript at `~6.0.3` in both.

- **npm is the package manager.** It ships with Node ([ADR-010]), and the Vite and Expo
  templates default to it. The recipes run `npm ci`.
- **ESLint and Prettier run in both apps.** Prettier's settings are the same in both
  `.prettierrc.json` files: no semicolons, single quotes, 100 columns.
  - The console uses `@eslint/js`, typescript-eslint, `eslint-plugin-react-hooks` and
    `eslint-plugin-react-refresh`.
  - The driver app uses Expo's `eslint-config-expo` (`~58.0.4`), which brings its own
    typescript-eslint.
- **The console tests with Vitest** (`^5.0.5`), Testing Library for React and jsdom.
- **The driver app tests with Jest** (`~30.5.2`) through the `jest-expo` preset (`~58.0.13`),
  and React Native Testing Library (`^14.1.0`).
- **TypeScript stays at `~6.0.3`** in both `package.json` files, until typescript-eslint
  supports TypeScript 7.
- **The two apps run different ESLint majors.** The console has ESLint `^10.13.0`, which is what
  create-vite installed. The driver app has `^9.39.5`, which is what `npx expo install` chooses
  for Expo SDK 58. They converge when an Expo SDK moves to ESLint 10.
- **In the driver app, a dependency is added with `npx expo install`**, so its version matches
  the SDK. Tooling (ESLint, Prettier, Jest, the type definitions) goes under `devDependencies`.
  Both rules are in `src/frontend/driver-app/CLAUDE.md`.

The recipes `console-check` and `driver-app-check` run the scripts in each `package.json`:

| App | Runs, in order |
|---|---|
| Console | `npm run lint`, `format:check`, `npm test`, `npm run build` (`tsc -b && vite build`) |
| Driver app | `lint`, `format:check`, `typecheck` (`tsc --noEmit`), `npm test`, `build` (`expo export --platform web`) |

## Consequences

- **Easy:** one package manager and one lockfile format across the repository's JavaScript, and
  the same Prettier output in both apps.
- **Easy:** Expo's own lint config and `npx expo install` keep the driver app inside what the SDK
  supports.
- **Hard:** TypeScript stays one major version behind. TypeScript 7 features and fixes wait for
  typescript-eslint.
- **Hard:** the two apps do not use the same ESLint major version, so a rule can behave
  differently between them. A shared lint rule has to be checked in both.
- **Hard:** the driver app's versions are not ours to pick. A package added with `npm install`
  instead of `npx expo install` can differ from what the SDK expects, and nothing in CI checks
  for that.
- **Hard:** the apps share no tooling configuration. The Prettier settings are written twice and
  must change together.
- **Cost:** the pin on TypeScript is a manual setting that Dependabot's grouped updates do not
  remove ([ADR-015]). A major bump arrives as its own pull request and must be declined or
  waited out.

## Alternatives considered

- **pnpm.** Rejected: npm ships with Node, and the Vite and Expo templates default to it.
- **Oxlint, the create-vite default.** Rejected: ESLint lets both apps use the same linter, and
  it works with Expo's `eslint-config-expo`.
- **TypeScript 7, as the create-vite template pinned.** Rejected for now: the install failed with
  `ERESOLVE` because typescript-eslint does not support it.

## Revisit when

- **typescript-eslint supports TypeScript 7.** Move both apps to it, and remove the `~6.0.3`
  pin.
- **An Expo SDK moves to ESLint 10.** Move the driver app to it, so both apps run one major.
- **The apps would copy hand-written code between them.** Add a shared package ([ADR-008]), and
  put the Prettier and ESLint settings in it.
- **Oxlint supports what `eslint-config-expo` provides**, and Expo recommends it. Reconsider it
  for both apps.

[ADR-003]: ADR-003-dispatcher-console-and-driver-app.md
[ADR-008]: ADR-008-monorepo-layout-under-src.md
[ADR-009]: ADR-009-just-as-the-single-entry-point.md
[ADR-010]: ADR-010-toolchain-pins-in-version-files.md
[ADR-015]: ADR-015-ci-one-workflow-one-job-per-component.md
[NFR-16]: ../requirements/requirements.md#nfr-16

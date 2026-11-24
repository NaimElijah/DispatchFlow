# ADR-039: Both apps and the shared TypeScript packages live in one pnpm workspace

- **Status:** Accepted; extended by ADR-130; amended by ADR-123
- **Date:** 2026-11-13

## Context

Two TypeScript apps exist today, and each is its own npm project ([ADR-012]):

| App | Today | Lint and format | Tests |
|---|---|---|---|
| `src/frontend/web` | Vite, `eslint` `^10.13.0` | ESLint, Prettier | Vitest |
| `src/frontend/mobile` | Expo SDK 58, `eslint` `^9.39.5` | ESLint with `eslint-config-expo`, Prettier | Jest through `jest-expo` |

Each has its own `package.json` and `package-lock.json`, the `justfile` runs `npm ci` in each, and
both hold `typescript` at `~6.0.3`. The two apps share no code. [ADR-008] and [ADR-012] chose npm
because it ships with Node and the templates default to it. They deferred a shared package until
hand-written code would otherwise be copied between the apps.

The tech stack now names the shared code ([TS §12][TS-12]):

- `@dispatchflow/api-client`, generated from `core-api` with openapi-typescript and openapi-fetch;
- `@dispatchflow/schemas`, `@dispatchflow/i18n`, `@dispatchflow/map-style` and
  `@dispatchflow/formatters`, each used by both apps;
- `@dispatchflow/eslint-config` and `@dispatchflow/tsconfig`.

TS §12 gives the reason for the shared packages as "one definition of each, used by both apps". It
says copies in each app drift. Both apps serve every role ([ADR-035]), and both read the same
contract ([NFR-21]). The implementation plan's tree lists five packages, without `eslint-config` and
`tsconfig` ([PL §5][PL-5]); this ADR follows the tech stack.

Two rows of the tech stack's web app section touch [ADR-012]'s decisions ([TS §10][TS-10]):

- **Lint:** ESLint with typescript-eslint, react-hooks and jsx-a11y, and Prettier. Biome is faster
  and has type-aware rules since version 2. They cover a subset of typescript-eslint's, and its type
  inference is its own reimplementation of the TypeScript compiler.
- **Base:** TypeScript 6.0 (strict). typescript-eslint supports only versions below 6.1, so TypeScript
  7 would lose type-aware lint rules.

## Decision

We put both apps and the shared packages in one pnpm workspace.

- **pnpm workspaces** replace npm in both apps, and pnpm's lockfile replaces both
  `package-lock.json` files. [TS §12][TS-12] gives the reason: strict dependencies catch undeclared
  imports, and installs are fast.
- **The workspace holds** the web app, the phone app and the packages under `src/packages`
  ([ADR-037]). The phone app joins when it exists, from M10.
- **If Metro cannot resolve dependencies,** the workspace uses pnpm's hoisted linker ([TS §12][TS-12]).
- **The shared packages:**

| Package | Holds |
|---|---|
| `@dispatchflow/api-client` | Types and client generated from `core-api` |
| `@dispatchflow/schemas` | Zod schemas for the forms of both apps |
| `@dispatchflow/i18n` | Hebrew and English strings, with text and a next step for every error code |
| `@dispatchflow/map-style` | One MapLibre style and the route colours |
| `@dispatchflow/formatters` | Dates, times, distances, weights, volumes and shekels |
| `@dispatchflow/eslint-config` | The shared ESLint configuration |
| `@dispatchflow/tsconfig` | The shared TypeScript configuration |

- **What changes in the accepted ADRs.** [ADR-012] is replaced whole. [ADR-035] changes in one
  part: line 58, "Both use React, TypeScript and npm", becomes React, TypeScript and pnpm. Its
  decision that both apps serve every role stands.
- **Carried forward from [ADR-012], unchanged:**
  - **ESLint and Prettier** lint and format both apps. ESLint is kept over Biome for the reason
    above, and Prettier keeps its style: no semicolons, single quotes, 100 columns.
  - **TypeScript is held at 6.0** (`~6.0.3` today), until typescript-eslint supports TypeScript 7.
  - **The test tools:** Vitest and Testing Library in the web app, Jest through `jest-expo` and
    React Native Testing Library in the phone app. The tech stack adds MSW to the web app's tests
    ([TS §10][TS-10]).
  - **In the phone app, a dependency is added with `npx expo install`,** so its version matches the
    Expo SDK. Not checked under pnpm.
- **Decided in the M0 restructure pull request** (deferred to it on 2026-11-14), against the real tree:
  - whether the workspace keeps two ESLint majors, as [ADR-012] records (10 in the web app, 9 in
    the phone app), and how `@dispatchflow/eslint-config` then loads under both;
  - where Prettier's settings live: they are not among the packages;
  - how Renovate keeps the pins [ADR-021] kept: TypeScript majors, and the versions the Expo SDK
    fixes. [ADR-046] covers Renovate.

## Consequences

- **Easy:** each shared concern has one definition, so a fix lands once for both apps.
- **Easy:** an import of an undeclared dependency fails, where it works by accident under looser
  layouts.
- **Easy:** the ESLint and TypeScript settings get one definition each. [ADR-012] listed the apps'
  unshared tooling configuration as a Hard point. Prettier's settings are not covered (see above).
- **Hard:** pnpm does not ship with Node, which was [ADR-012]'s reason for npm. mise installs it
  ([ADR-038]).
- **Hard:** Metro and pnpm's strict layout may not agree. The hoisted linker is the fallback, and it
  may give up some of the strictness that is one reason for pnpm. Not checked: the hoisted linker
  with Expo SDK 58.
- **Hard:** [ADR-023]'s reasoning needs revisiting under pnpm. Its facts come from npm: the
  `glob` and `minimatch` versions in each `package-lock.json`, the exposure measured on
  `@expo/cli` 58.1.17, and the npm `overrides` it tried and rejected. pnpm resolves and lays out
  dependencies differently and has its own override mechanism, not tested here. Its five
  `[[IgnoredVulns]]` entries expire on 2027-01-31 whatever happens. Not checked: that OSV-Scanner
  ([ADR-016]) reads pnpm's lockfile.
- **Hard:** the `web-setup` and `mobile-setup` recipes run `npm ci`, and the `web`
  and `mobile` CI jobs cache npm. `.claude/skills/sync-with-dev/update-from-dev.md` names
  `npm install --package-lock-only`. [ADR-021]'s npm rules in `.github/dependabot.yml` have no
  equivalent in the new setup until [ADR-046] is settled.
- **Cost:** two lockfiles are replaced and every dependency is re-resolved once. The generated
  `@dispatchflow/api-client` and the packages need a build order the workspace must define.
- **Cost:** pnpm is a new tool. Record its licence and maintenance status before adopting it, as
  `.claude/CLAUDE.md` requires of every new dependency. The sources give neither.
- **Risk:** the TypeScript pin is still a manual setting. A TypeScript major arrives as its own
  update and must be declined or waited out ([ADR-012]).

## Alternatives considered

- **npm workspaces, or Yarn workspaces.** Rejected: looser, so an undeclared dependency works by
  accident until it does not ([TS §12][TS-12]). [ADR-012] kept npm because it ships with Node;
  that no longer outweighs strictness.
- **Nx or Turborepo.** Rejected for now: build caching that a repository this size does not need
  yet ([TS §12][TS-12]). Reopen when a workspace CI job nears its timeout (15 minutes for each npm
  job in `ci.yml` today).
- **Separate projects, with copies in each app.** Rejected: copies drift ([TS §12][TS-12]). This was
  [ADR-008]'s deferral, and the packages above are the case it waited for.
- **Biome in place of ESLint and Prettier.** Rejected for now: its type-aware rules cover a subset
  of typescript-eslint's, and its type inference is its own reimplementation rather than the
  TypeScript compiler ([TS §10][TS-10]).
- **Oxlint,** as [ADR-012] considered. Still rejected: ESLint serves both apps, with Expo's
  `eslint-config-expo` in the phone app.
- **TypeScript 7.** Still rejected: typescript-eslint does not support it, so the web app would lose
  type-aware lint rules ([TS §10][TS-10]).

## Revisit when

- typescript-eslint supports TypeScript 7. Move both apps and remove the pin.
- Biome's type-aware rules mature to cover the typescript-eslint rules the team uses
  ([TS §10][TS-10]).
- An Expo SDK moves to ESLint 10. Both apps then run one major ([ADR-012]).
- Metro needs the hoisted linker for the phone app, and strict dependencies are gone in practice.
  Compare npm workspaces again.
- A workspace CI job nears its timeout. Compare Nx or Turborepo.

[ADR-008]: ADR-008-monorepo-layout-under-src.md
[ADR-012]: ADR-012-javascript-tooling-npm-eslint-prettier.md
[ADR-016]: ADR-016-dependency-audit-with-osv-scanner.md
[ADR-021]: ADR-021-dependabot-targets-dev-and-skips-pinned-majors.md
[ADR-023]: ADR-023-accept-minimatch-and-glob-advisories.md
[ADR-035]: ADR-035-both-apps-serve-every-role.md
[ADR-037]: ADR-037-flat-layout-under-src-with-deploy-and-infra.md
[ADR-038]: ADR-038-mise-pins-every-toolchain-and-core-moves-to-java-25.md
[NFR-21]: ../requirements/requirements.md#nfr-21
[PL-5]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#5-repository-and-local-environment
[TS-10]: ../architecture/tech_stack.md#10-web-app
[TS-12]: ../architecture/tech_stack.md#12-shared-typescript-packages
[ADR-046]: ADR-046-renovate-replaces-dependabot-and-targets-dev.md

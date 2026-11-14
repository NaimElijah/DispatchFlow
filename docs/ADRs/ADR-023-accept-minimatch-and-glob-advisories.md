# ADR-023: Accept the minimatch and glob advisories in the frontends' tooling until 2027-01-31

- **Status:** Accepted; extended by ADR-095
- **Date:** 2026-11-03

## Context

The `audit` CI job (`just audit`, OSV-Scanner v2.7.0, [ADR-016]) failed on `main` at `ab2fb74` and
on `dev` at `76d1246`. The other six CI jobs passed. The last green audit run was on 2026-10-30
at 18:43 UTC. Two medium advisories were published the same day at about 23:09 UTC. The commits
that went red changed only `docs/requirements/requirements.md`. [ADR-016] names this as a known
risk: a pull request that touched nothing can fail because a new advisory appeared.

The two advisories:

| ID | Package and CVSS | Flaw | Found in |
|---|---|---|---|
| `GHSA-g3hv-75wg-m249` (CVE-2026-118764) | `minimatch` < 11.0.1; 6.9 as shown by OSV-Scanner | At `optimizationLevel` 2 or higher, a glob pattern from an attacker can cause excessive CPU and memory use | Console: 10.2.6. Driver app: 3.1.5, 9.0.9, 10.2.6 |
| `GHSA-3g3m-2fjr-6pw4` (CVE-2026-118773) | `glob` >= 9.0.0 and <= 14.0.1, fixed in 14.0.2; 6.3 | A walk that is aborted keeps reading directories in the background | Driver app only: 10.5.0, 13.0.6 |

`glob` 7.2.3 is also in the driver-app tree, outside the advisory's range.

The forces:

- **The fixed versions are majors that the tooling does not accept yet:**
  - `eslint@10.14.0`, the latest release and the console's major, asks for `minimatch` `^10.2.6`;
  - `eslint@9.39.5`, the driver app's version ([ADR-012]), asks for `minimatch` `^3.1.5`;
  - `@typescript-eslint/typescript-estree@8.72.1` asks for `minimatch` `^10.2.2`;
  - `eslint-plugin-import@2.32.0` and `eslint-plugin-react@7.37.5` ask for `minimatch` `^3.1.2`;
  - `@expo/cli@58.1.18` asks for `glob` `^13.0.0`;
  - `jest-config@30.5.2` asks for `glob` `^13.0.6`.
- **Expo owns the driver app's versions.** [ADR-012] warns that a version chosen by hand can break
  the native build, and [ADR-021] moves them only with an SDK upgrade. CI builds only the web
  export (`expo export --platform web`), so it would not catch a native break.
- **A job that is always red stops being read.** Until upstream moves, every branch and pull
  request would fail `audit`, and a new advisory would go unnoticed.
- **The exposure is small, as measured on 2026-11-02 and 2026-11-03 on `@expo/cli` 58.1.17:**
  - **Bundles.** The driver app's web export holds 18 packages, and its iOS and Android exports
    hold 30 each, read from their source maps. Neither `glob` nor `minimatch` is among them, and
    the string `minimatch` appears 0 times in any of the three. The console's production build
    holds `react`, `react-dom` and `scheduler` only, also with 0 occurrences. No application code
    imports either package.
  - **Lockfiles.** In the console, `minimatch` is dev-only. In the driver app, the top-level
    `glob` 13.0.6 and `minimatch` 10.2.6 are not marked dev, because `expo` depends on `@expo/cli`
    and its neighbours. They are still absent from the bundles. All other copies are dev.
  - **The `minimatch` flaw.** The flawed code runs only at `optimizationLevel` 2 or higher. The
    default is 1, and only `glob` sets 2. One 100-byte pattern produced 1024 parts at level 2 in
    `minimatch` 9.0.9 and 10.2.6, and in `glob` 13.0.6, and 1 part at the default level. The
    console has no `glob`, so the flawed code never runs there. `minimatch` 3.1.5 does not contain
    it: the advisory's range is wider than the code.
  - **`glob` patterns.** The `glob` calls in `@expo/*`, Jest and `test-exclude` take literals,
    repository config or command-line arguments. None takes a pattern from a network request. This
    was a read of the call sites, not a full taint trace.
  - **The abort flaw.** One live caller passes a signal: `TypeScriptProjectPrerequisite` in
    `@expo/cli`, with `AbortSignal.timeout(5000)` and the fixed pattern `**/*.@(ts|tsx)`.
    `expo start` and `expo run` reach it only when the project has no `tsconfig.json`.
    `src/frontend/driver-app/tsconfig.json` is committed, so the check returns before the walk, and
    `expo export` does not reach it at all. If it ever ran, the walk would continue over the
    developer's own project after the timeout. `anyMatchAsync` in `@expo/cli` also passes a signal,
    and has no caller.

## Decision

On 2026-11-03 the owner, Naim Elijah, accepted both advisories. We ignore them in the audit until
2027-01-31, the same date as the entries in [ADR-016].

- **Three `[[IgnoredVulns]]` entries**, each with `ignoreUntil = 2027-01-31` and a reason:
  - `GHSA-g3hv-75wg-m249` and `GHSA-3g3m-2fjr-6pw4` in `src/frontend/driver-app/osv-scanner.toml`;
  - `GHSA-g3hv-75wg-m249` in `src/frontend/console/osv-scanner.toml`, a new file.
- **The lockfiles and `package.json` files do not change.** The flagged versions stay.
- **This decision does not confirm the zod and deepmerge entries** from [ADR-016]. They still
  await the owner's review.

The driver app now has four entries and the console one.

## Consequences

- **Easy:** `audit` passes again, so a red job means a new problem. `just audit` printed
  `Loaded filter from: /src/src/frontend/console/osv-scanner.toml` and ended `No issues found` with
  exit 0.
- **Easy:** no tooling runs on a major outside its declared range.
- **Hard:** the flagged versions stay in the lockfiles until upstream moves.
- **Hard:** the audit fails again from 2027-01-31, on the date itself, unless the entries are
  fixed, justified again or removed. All five entries in the frontends expire together.
- **Cost:** the console gets a second `osv-scanner.toml` for the audit to read, and the team has
  one more file to keep current.
- **Risk:** `IgnoredVulns` filters by advisory ID for the whole folder. If a new dependency or a
  new code path passes `glob` a pattern from outside the repository, the audit will not flag it.
  Only the reviewer of that change can catch it.
- **Risk:** the lockfile cannot show everything. `glob` 13.0.6 carries its own copy of `minimatch`
  in `dist/commonjs/index.min.js` and does not load the installed package. If the lockfile's
  `minimatch` moves to a fixed version while `glob` stays on 13, the flawed code still runs and the
  scan reports nothing.
- **Risk:** the call-site results describe `@expo/cli` 58.1.17, and [ADR-021] lets Expo patch
  updates through. A patch could add a caller or move the `tsconfig.json` check.

## Alternatives considered

The first three were tried in a scratch copy of the repository.

- **Blanket npm `overrides` in the driver app** (`minimatch: 11.0.3`, `glob: 14.0.3`). Rejected.
  Lint, format check, typecheck, tests, the web build and the audit all passed. It still broke
  tooling that those checks do not exercise:
  - `test-exclude@6` fails to load with `ERR_INVALID_ARG_TYPE`, because it runs
    `promisify(require('glob'))` and this override replaces `glob` 7, which the advisory does not
    cover.
  - Enabling `import/no-extraneous-dependencies` with a glob option crashes ESLint with
    `(0 , _minimatch2.default) is not a function`. It exits 2, where the current versions exit 0.
- **Version-scoped `overrides` in the driver app** (`glob@>=9: 14.0.3`, `minimatch@>=9: 11.0.3`).
  This route works, and it fixes most of the findings. We rejected it by judgment, not because it
  is impossible. Lint, format check, typecheck, tests, coverage and the web build passed.
  `test-exclude@6` loaded, the ESLint probe above exited 0, and a real call to `everyMatchAsync`
  in `@expo/cli` worked. The audit then reported only `minimatch` 3.1.5, dev-only, so one exception
  would still be needed. It lost on four counts:
  - The flaws it would remove give an attacker nothing, for the reasons in Context.
  - It runs Expo, ESLint and Jest on majors outside their declared ranges, against the warning in
    [ADR-012].
  - It adds a transitive package, `@isaacs/nonrecursive-glob-match`, which `minimatch` 11 depends
    on.
  - Only one Expo `glob` call was exercised. The `expo start`, `prebuild` and `run:ios` paths
    remain untested, and CI builds only the web export.
- **An `overrides` entry in the console only** (`minimatch: 11.0.3`). Rejected. Lint, format check,
  tests and the build passed, its lockfile scanned clean, and the console would need no exception
  file. Three reasons outweigh that: the flawed code never runs there, because the console has no
  `glob`; ESLint and typescript-eslint would run outside their declared `minimatch` ranges; and it
  adds the same transitive package.
- **Leave `audit` red until upstream moves.** Rejected, for the reason in Context: a job that is
  always red stops being read.

## Revisit when

- **ESLint and typescript-eslint accept `minimatch` 11.0.1 or later.** Upgrade the console, and
  remove its entry.
- **An Expo SDK brings the driver app `glob` 14.0.2 or later and `minimatch` 11.0.1 or later.**
  Upgrade with `npx expo install --fix` ([ADR-021]), then remove the entries. Remove the `minimatch`
  entry only once `glob` is off 13, because of its bundled copy. If only `minimatch` 3.1.5 remains,
  through `eslint-plugin-import` or `eslint-plugin-react`, justify the entry again: that version
  does not contain the flawed code.
- **An entry reaches its `ignoreUntil` date.** Fix it, justify it again with a new date, or remove
  it. This date is the only check certain to happen, because Dependabot skips the driver app's
  major and minor updates ([ADR-021]).
- **`glob` takes a pattern from outside the repository, or either package reaches a shipped bundle
  or a running service.** Fix at once. If overrides are the only route, first exercise
  `expo start`, `prebuild` and `run:ios`, and record the deviation from [ADR-012] and [ADR-021] in
  a new ADR.
- **`tsconfig.json` leaves the driver app, or an Expo update changes the TypeScript check.** The
  reason for the `glob` entry rests on both. Read the caller again.

[ADR-012]: ADR-012-javascript-tooling-npm-eslint-prettier.md
[ADR-016]: ADR-016-dependency-audit-with-osv-scanner.md
[ADR-021]: ADR-021-dependabot-targets-dev-and-skips-pinned-majors.md

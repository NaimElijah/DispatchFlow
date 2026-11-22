# ADR-021: Dependabot opens its updates against `dev`, and skips the versions other ADRs pin

- **Status:** Superseded by ADR-046
- **Date:** 2026-10-30

## Context

[ADR-015] set Dependabot to propose weekly updates for every ecosystem. Its first scan, on
2026-10-30, opened eight pull requests (#6 to #13), all against `main`, the default branch, and
several of them against decisions already recorded:

- **#6:** the core image from Java 21 to 24. [ADR-010] keeps Java 21, the LTS the plan names.
- **#11 and #13:** TypeScript 6 to 7, in both apps. [ADR-012] holds TypeScript at 6.0 until
  typescript-eslint supports 7.
- **#12:** ESLint 9 to 10 in the phone app. The Expo SDK fixes the phone app's versions
  ([ADR-012]).
- **#10:** `@types/node` 24 to 26. The Node line is 24 ([ADR-010]).
- **#8:** React 19.3.0 to 19.3.1 in the phone app, a version the Expo SDK pins exactly.
- **#7 and #9:** `uv_build` up to below 0.14. uv is pinned to 0.12.7 in `ci.yml` and in the optimizer
  `Dockerfile`, which Dependabot does not read, and the three pins change together ([ADR-010]).

The team works on `dev`, and `main` is production: every pull request goes into `dev`.

## Decision

We keep Dependabot, and constrain it to the decisions above (`.github/dependabot.yml`).

- **Every update targets `dev`** (`target-branch: dev`), like any other pull request.
- **The Docker updates** skip a new Java major (`eclipse-temurin`) and a new Python minor or major
  (`python`).
- **The web app** skips TypeScript and `@types/node` majors.
- **The phone app** has its own entry and skips:
  - every major and minor update;
  - every update to `react`, `react-dom`, `react-native` and `react-native-web`.

  Its versions move with an Expo SDK upgrade (`npx expo install --fix`). React Native is still 0.x,
  so Dependabot counts its next release as a minor.
- **The uv projects** skip `uv-build` 0.13 and later.
- The pull requests #6 to #13, opened before this change, are closed by hand.

## Consequences

- **Easy:** Dependabot's pull requests land where the team works, and none proposes a version an
  ADR rules out.
- **Hard:** Dependabot no longer announces a new Expo SDK, a new Java LTS, a new Python, TypeScript
  7 or a new uv. Someone has to watch for them (see *Revisit when*).
- **Hard:** the ignores repeat decisions held elsewhere. When one of those decisions changes, its
  ignore has to change in the same pull request.
- **Risk:** GitHub's security updates, if they are turned on, go to the default branch and do not
  follow these settings.

## Alternatives considered

- **Leave Dependabot as it was, and close the unwanted pull requests as they come.** Rejected: it
  turns every weekly scan into triage, and a green pull request can be merged by mistake.
- **Turn Dependabot off for the frontends.** Rejected: patch and minor updates to the web app, and
  patch updates to the phone app's other packages, are worth having.
- **Make `dev` the default branch instead of setting `target-branch`.** Not instead, but as well:
  it is still to do, and it also makes pull requests target `dev` by default.

## Revisit when

- **typescript-eslint supports TypeScript 7.** Remove the TypeScript ignore together with the
  `~6.0.3` pin ([ADR-012]).
- **A new Expo SDK is released.** Upgrade the phone app with `npx expo install --fix`. The team
  checks for one each month, because Dependabot stays silent.
- **The team moves to another Java LTS, Python or Node line.** Change the ignore in the same pull
  request as the pin ([ADR-010]).
- **uv is upgraded** in `ci.yml` and the optimizer `Dockerfile`. Raise the `uv-build` bound with it.

[ADR-010]: ADR-010-toolchain-pins-in-version-files.md
[ADR-012]: ADR-012-javascript-tooling-npm-eslint-prettier.md
[ADR-015]: ADR-015-ci-one-workflow-one-job-per-component.md

# ADR-141: Prettier's settings live in one file in src/frontend

- **Status:** Accepted
- **Date:** 2026-11-25

## Context

[ADR-039] deferred "where Prettier's settings live: they are not among the packages" to the M0
restructure pull request. [TS §22][TS-22] listed it among the details deferred to M0 until this ADR, and
[#147][ISSUE-147] carries it, with a recommendation of one file in `src/frontend/`.

- The web app and the phone app hold identical `.prettierrc.json` files: no semicolons, single
  quotes, 100 columns.
- [#148][ISSUE-148] adds the pnpm workspace in `src/frontend/` and its first two packages,
  `@dispatchflow/eslint-config` and `@dispatchflow/tsconfig`. Each runs its own Prettier check, and
  needs the same settings now, before [#147][ISSUE-147] merges.
- Prettier searches upward from each file it formats and uses the first configuration file it
  finds.

## Decision

Decided on 25 November 2026.

1. **One `.prettierrc.json` in `src/frontend/`,** moved unchanged from `src/frontend/web/`. The web
   app and every workspace package use it.
2. **It is not a package.** [ADR-039] keeps Prettier's settings out of the shared packages.
3. **The phone app's copy stays until [#147][ISSUE-147] deletes it,** as that ticket lists. It is
   identical, and while it exists the phone app reads it, because it is the nearer file.
4. **Each workspace package runs its own `prettier --check .`,** through its `format:check` script,
   which `just web-check` runs in every package.
5. **The workspace root's own files are not Prettier-checked.** `pnpm -r` runs no script of the
   root, and pnpm rewrites `package.json` and `pnpm-workspace.yaml` itself.

## Consequences

- **Easy:** the style changes in one place, for the web app and every package.
- **Hard:**
  - A package that needs another style needs its own file, nearer its files.
  - The root's `package.json` and `pnpm-workspace.yaml` are not checked.
- **Cost:** none beyond the move.
- **Risk:** the phone app's copy can drift from this file until [#147][ISSUE-147] deletes it. Both
  are identical today.

## Alternatives considered

- **A copy in each package.** Rejected: copies drift, the reason [ADR-039] gives for the shared
  packages.
- **A shared Prettier configuration package.** Rejected: [ADR-039] keeps Prettier out of the
  packages, and it would add a dependency to every package for three settings.
- **A `"prettier"` key in the root `package.json`.** Rejected: that file is the workspace's
  manifest, which pnpm rewrites, and a separate file names what it holds.
- **Leave it to [#147][ISSUE-147].** Rejected: the packages need the settings now.

## Revisit when

- A package needs another style.
- Biome or another formatter replaces Prettier ([ADR-039] keeps ESLint and Prettier until Biome's
  type-aware rules mature).
- The phone app joins the workspace, from M10 ([ADR-039]).

[ADR-039]: ADR-039-one-pnpm-workspace-for-both-apps-and-shared-packages.md
[ISSUE-147]: https://github.com/NaimElijah/DispatchFlow/issues/147
[ISSUE-148]: https://github.com/NaimElijah/DispatchFlow/issues/148
[TS-22]: ../architecture/tech_stack.md#22-open-decisions

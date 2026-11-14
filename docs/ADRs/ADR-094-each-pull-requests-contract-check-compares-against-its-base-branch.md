# ADR-094: Each pull request's contract check compares against its base branch

- **Status:** Accepted
- **Date:** 2026-11-14

## Context

[ADR-043] catches breaking contract changes with `oasdiff` against `main`, from the January break.
`main` holds what is released, and so what phones in the field run ([NFR-134]).

Pull requests go into `dev`. Compared against `main`, a pull request into `dev` also reports the
breaking changes that earlier pull requests left unreleased on `dev`.

ADR-043 left open whether pull requests into `dev` compare against their base branch. [ADR-082]
says `oasdiff` checks every change against `main`.

## Decision

On 2026-11-14 the owner, Naim Elijah, decided:

- `oasdiff` compares each pull request against its base branch: the pull request's merge result
  against the base branch's tip, so a change merged into the base after the branch was cut is not
  reported as this pull request's.
- A pull request into `dev` is compared against `dev`.
- The pull request that brings `dev` into `main` is compared against `main`, so every change that
  reaches phones is checked once against what is released.
- The check still starts at the January break.
- A breaking change still needs a new major version ([ADR-006]).

## Consequences

- **Easy:** each pull request reports only its own change.
- **Hard:** the `dev` to `main` pull request reports every breaking change since the last release
  together. That is the release check.
- **Cost:** none beyond ADR-043's CI step.
- **Risk:** a breaking change accepted on `dev` is reported again at the release, by design.
- **Risk:** [tech stack §18][TS-18], in its adapter and contract tests row, still says "oasdiff
  against `main`". That now describes the `dev` to `main` check, and the line is corrected when the
  tech stack is next edited ([ADR-093]'s precedence: the ADR wins).

## Alternatives considered

- **Always compare against `main`.** Rejected: every pull request into `dev` would repeat breaking
  changes it did not make, and that noise hides its own.
- **Compare against both `dev` and `main`.** Rejected: the comparison against `main` repeats the
  same noise as comparing against `main` alone.
- **Check only `dev` to `main`.** Rejected: a breaking change would be found at the release, far
  from the pull request that made it.

## Revisit when

- Releases move to every merge, so `dev` and `main` no longer differ.
- A `dev` to `main` check reports a breaking change that no pull request into `dev` reported.

[ADR-006]: ADR-006-versioned-openapi-contracts.md
[ADR-043]: ADR-043-generated-code-from-both-contracts-and-problem-details-errors.md
[ADR-082]: ADR-082-supported-phone-app-versions.md
[ADR-093]: ADR-093-the-implementation-plan-is-retired-and-a-roadmap-holds-the-dates.md
[NFR-134]: ../requirements/requirements.md#nfr-134
[TS-18]: ../architecture/tech_stack.md#18-cicd-and-supply-chain

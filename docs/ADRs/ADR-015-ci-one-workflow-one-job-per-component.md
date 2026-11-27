# ADR-015: CI is one workflow with one job per component, and Dependabot keeps dependencies current

- **Status:** Accepted; extended by ADR-021; amended by ADR-046, ADR-135 and ADR-151
- **Date:** 2026-10-30

## Context

The repository holds five components, two OpenAPI contracts and a dependency audit
([ADR-008], [ADR-006], [ADR-016]). "CI builds and tests every change" ([NFR-16]). The team is
three people, and CI runs on GitHub Actions.

The forces:

- **A red pipeline must be able to block a merge.** That works only if every check reports a
  result on every pull request. A required check that never reports leaves the merge blocked.
- **CI must run the commands developers run.** A second definition of a check drifts from the
  first ([ADR-009]).
- **A workflow runs third-party code with a token.** The token should carry the least access it
  needs, and an action referenced by a movable tag can change under us.
- **Dependencies age.** Four lockfiles, a pom, the Dockerfiles and the workflow itself all hold
  versions that need updates ([ADR-010]). Doing that by hand gets forgotten.
- **Branch protection is not configured yet** (issue #2), so nothing enforces a green check today.

## Decision

We run one workflow, `.github/workflows/ci.yml`, with one job per component. Each job calls
a `just` recipe. Dependabot proposes the updates.

- **Seven jobs**: `core`, `optimizer`, `research`, `web`, `mobile`, `contracts` and
  `audit`. Each runs on `ubuntu-24.04` and calls its component's recipes (`core-check`;
  `web-setup` then `web-check`; `audit`; and so on), through
  `uvx --from rust-just==1.59.0 just`.
- **Triggers**: every push, and every pull request into `dev` or `main`.
- **No path filters.** A workflow that path filtering skips never reports its check, and a
  required check that never reports blocks the merge.
- **Actions are pinned to full commit SHAs**, each with its version in a comment: `checkout`
  v7.0.1, `setup-java` v6.1.0, `setup-node` v7.1.0 and `setup-uv` v10.3.3.
- **The token has `contents: read`** and nothing else, and no checkout keeps it
  (`persist-credentials: false`), because no step pushes. Third-party code that the jobs run
  cannot read it from `.git/config`.
- **Every job has a timeout** (10 to 20 minutes), so a hung run stops early instead of using
  the default six hours.
- **A newer run cancels the older one on the same pull request.** The concurrency group is `ci-`
  followed by the pull request number, or the ref for a push. Pushes to `dev` and `main` are
  never cancelled, so every commit there keeps its own result.
- **Dependabot runs weekly** (`.github/dependabot.yml`) for `github-actions`, `maven`, `uv`,
  `npm` and `docker`. It covers the core, the optimizer, the research project and both frontend
  apps. Minor and patch updates are grouped, except for Docker. A major update gets its own
  pull request. Commit messages start with `chore` and the scope.
- **Branch protection is not configured yet.** Making the CI jobs a required check is tracked in
  issue #2.
- **CI has never run on GitHub**, because nothing has been pushed. The workflow has not been
  exercised there.

## Consequences

- **Easy:** a pull request shows one result for each component, so a failure names the
  component that broke.
- **Easy:** changing a check means changing a recipe. The workflow does not repeat it
  ([ADR-009]).
- **Easy:** a compromised or retagged action cannot change what we run, and a leaked token can
  only read the repository.
- **Easy:** version updates arrive weekly as pull requests, and CI checks them like any other
  change.
- **Hard:** every pull request runs every job, whatever it touched.
- **Hard:** a push to a branch that has a pull request runs the workflow twice, once for the
  push and once for the pull request. The two have different concurrency groups, so neither
  cancels the other.
- **Hard:** a pinned SHA is unreadable without its comment, and the comment can drift from the
  SHA it describes.
- **Hard:** until issue #2 is done, a red job does not block a merge. And until the first push,
  nothing shows that the jobs pass on a GitHub runner, including the two that need Docker
  (`core` and `audit`).
- **Cost:** every job installs uv, to run `just` ([ADR-009]).

## Alternatives considered

- **Path-filtered CI jobs.** Rejected for now: a skipped workflow leaves its required check
  pending, and blocks the merge.
- **Workflow steps that repeat each command**, without `just`. Rejected: the workflow would
  define "everything CI checks" a second time ([ADR-009]).

## Revisit when

- **CI takes more than about 10 minutes of wall time.** Add path filtering behind one gate job
  that always reports, and make that job the required check.
- **Branch protection is configured** (issue #2). Record which checks are required, and check
  that the job names match.
- **The first run on GitHub fails for a reason local runs do not show.** Record what differs
  between the runner and the developer machine.
- **Dependabot's pull requests become too many.** Group Docker updates, or move to a monthly
  schedule.

[ADR-006]: ADR-006-versioned-openapi-contracts.md
[ADR-008]: ADR-008-monorepo-layout-under-src.md
[ADR-009]: ADR-009-just-as-the-single-entry-point.md
[ADR-010]: ADR-010-toolchain-pins-in-version-files.md
[ADR-016]: ADR-016-dependency-audit-with-osv-scanner.md
[NFR-16]: ../requirements/requirements.md#nfr-16

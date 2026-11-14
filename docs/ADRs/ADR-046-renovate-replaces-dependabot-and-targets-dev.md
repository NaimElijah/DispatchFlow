# ADR-046: Renovate replaces Dependabot and keeps opening updates against `dev`

- **Status:** Accepted
- **Date:** 2026-11-13

## Context

[ADR-015] decided that CI is one workflow with one job per component, and that Dependabot proposes
the updates. [ADR-021] then constrained Dependabot after its first scan opened eight pull requests,
all against `main`, several against versions other ADRs pin.

Today `.github/dependabot.yml` has six entries, all weekly and all with `target-branch: dev`:

- `github-actions` at the root;
- `maven` for the core, `uv` for the solver and the research project, and `npm` for each of the two
  apps;
- `docker` for the core's and the solver's `Dockerfile` folders.

Each opens commits as `chore(scope)`. Minor and patch updates are grouped, except Docker's. The
ADR-021 ignores are in the file:

- `uv-build` 0.13 and later;
- TypeScript and `@types/node` majors, in the console;
- every major and minor update, plus `react`, `react-dom`, `react-native` and `react-native-web`,
  in the driver app;
- a new Java major or Python minor, for the images.

Today's pins:

- `ci.yml` pins its actions to full commit SHAs, each with its version in a comment, and the
  `github-actions` entry keeps them current ([ADR-015]).
- The `Dockerfile` base images are pinned by tag, which the `docker` entry keeps current.
- The `justfile` pins the OSV-Scanner image by tag and digest. No Dependabot entry covers the
  `justfile`.

The proposed design adds forces ([TS §5], the *Dependency updates* row; [TS §18]):

- **More files hold pins.** `mise.toml` pins the JDK, Node.js, Python, uv, pnpm, just and Terraform
  ([TS §5]). Images move to digests ([ADR-047]). Dependabot "doesn't update `mise.toml` or convert
  tags into pinned digests and SHAs", though it updates Compose images and keeps existing pins
  current.
- **Pins must stay pinned.** "Pin every third-party action to a commit SHA and every image to a
  digest." On 19 March 2026, 76 of the 77 version tags of `aquasecurity/trivy-action` were
  force-pushed to credential-stealing code, and a pinned SHA cannot be moved ([TS §18]).
- **The team works on `dev`, and `main` is production.** [TS §5]'s *Branching* row proposes
  short-lived branches into `main`. The owner did not adopt it: `dev` and `main` stay
  (`.claude/CLAUDE.md`, Git).

## Decision

We replace Dependabot with Renovate, grouped weekly, and Renovate targets `dev`.

- **Coverage:** Maven, uv, pnpm, Compose images, GitHub Actions and `mise.toml` ([TS §5]), and the
  `Dockerfile` base images, which hold pins too ([ADR-047]). TS §5 names only Compose images, so
  the `Dockerfile` entry is this ADR's addition. Without it, the claim that Renovate maintains
  every file that holds a pin would not be true.
- **Pins:** it turns tags into pinned digests and commit SHAs, and keeps them current
  ([TS §5], [TS §18]).
- **Safeguards.** Renovate proposes a change to every pin, and a moved tag can arrive as one, so:
  - no update is auto-merged: each one is a pull request that a person reviews;
  - a digest or SHA change with no version change needs the upstream release checked before
    merge, because a moved tag, the March 2026 `trivy-action` case, arrives that way ([TS §18]);
  - third-party actions and images wait a minimum release age before Renovate proposes them. The
    sources give no number, so the team sets it.
- **Branch:** every update opens against `dev`, like any other pull request, as [ADR-021] decided.
  This differs from [TS §5]'s *Branching* row, which the team did not adopt.
- **Skipped versions:** it keeps ADR-021's rule of skipping the versions other ADRs pin. The rules
  are written from the lines [TS §21] fixes, not copied from today's file. Those include Java 25,
  Python 3.14, TypeScript 6.0, PostgreSQL 18 ("don't switch during the project") and the Expo SDK
  with its React Native.
  - The `uv-build` ignore stays until the uv pins move together in one place Renovate reads.
- **Titles:** a pull request is squash-merged and its title is the commit, so each update's title is
  a Conventional Commit (`.claude/CLAUDE.md`, Git).
- **ADR-015 otherwise stands:** one workflow, one job per component, actions pinned to SHAs, a
  read-only token. Only its Dependabot bullet, which says Dependabot runs weekly, is replaced.
- **Renovate runs as Mend's hosted Renovate app,** decided by the owner on 2026-11-14. It is free,
  holds its own credentials, and uses no GitHub Actions minutes. It gets write access to the
  repository, which holds code only, never company data ([NFR-50]).
- **Digest pins start in January,** with Renovate ([TS §5]; decided on 2026-11-14). From then,
  `Dockerfile` base images and the images in `compose.dev.yaml` are pinned by digest ([ADR-047]),
  and Renovate opens the bumps, grouped weekly. Until then they stay on tags ([ADR-019]).
- **The configuration** is `renovate.json` at the repository root ([TS §4]).
- **Adoption is the January break** ([TS §20]). Until then `.github/dependabot.yml` and ADR-021
  stand unchanged. The pull request that adds Renovate removes `.github/dependabot.yml`.

## Consequences

- **Easy:** one tool maintains every file that holds a pin, including `mise.toml`, digests and SHAs.
- **Easy:** a tag no longer moves under a build once Renovate has pinned it ([ADR-047]).
- **Hard:** Renovate stays silent about the versions it skips, as Dependabot did. A new Expo SDK, a
  new Java LTS or a new Python still needs someone watching ([ADR-021]).
- **Hard:** the ignores repeat decisions held elsewhere. When one of those decisions changes, its
  ignore changes in the same pull request. The move to Java 25 ([ADR-038]) changes ADR-021's Java
  ignore.
- **Hard:** how Renovate runs is open: a hosted app has write access to the repository, and a
  workflow would add one more third-party action to pin.
- **Hard:** the digest date is open between January and the pilot, and `Dockerfile` pins follow it.
- **Cost:** a second tool's configuration to learn and own, and a window in which pins differ
  between Dependabot's and Renovate's reading of them.
- **Cost:** Renovate is a new tool. Record its licence and maintenance status before adopting it,
  as `.claude/CLAUDE.md` requires of every new dependency. The sources give neither.
- **Risk:** the OSV-Scanner digest in the `justfile` stays a hand-kept pin unless the configuration
  covers it. [TS §5]'s list does not name the `justfile`.
- **Risk:** GitHub's own security updates went to the default branch under Dependabot, outside its
  settings ([ADR-021]). How Renovate's vulnerability updates choose a branch is not checked here.

## Alternatives considered

- **Keep Dependabot, as ADR-021 has it.** Rejected: it cannot update `mise.toml` or turn tags into
  digests and SHAs ([TS §5]).
- **Update pins by hand.** Rejected, as in [ADR-015]: it gets forgotten.
- **Renovate against `main`, with short-lived branches** ([TS §5], *Branching*). Not chosen: the
  owner kept `dev` and `main`.

## Revisit when

- Renovate's pull requests become too many despite the weekly grouping. Move to a monthly schedule,
  as ADR-015 already allows.
- The team adopts short-lived branches into `main`. Change the target branch with it.
- A skipped version moves on: the Expo SDK, a Java LTS, a Python line, TypeScript 7. Change the
  ignore in the same pull request as the pin, and check for new Expo SDKs each month ([ADR-021]).
- The uv pins move together in one place Renovate reads. Drop the `uv-build` ignore.
- Mend's hosted app changes its terms, its access or its price, or the repository comes to hold
  company data. Move Renovate to a pinned workflow with our own token.

[ADR-015]: ADR-015-ci-one-workflow-one-job-per-component.md
[ADR-019]: ADR-019-container-images-run-as-non-root.md
[ADR-021]: ADR-021-dependabot-targets-dev-and-skips-pinned-majors.md
[ADR-038]: ADR-038-mise-pins-every-toolchain-and-core-moves-to-java-25.md
[ADR-047]: ADR-047-images-pinned-by-digest-and-core-on-temurin-25-jre.md
[TS §4]: ../architecture/tech_stack.md#4-repository-layout
[TS §5]: ../architecture/tech_stack.md#5-development-environment-and-workflow
[TS §18]: ../architecture/tech_stack.md#18-cicd-and-supply-chain
[TS §20]: ../architecture/tech_stack.md#20-adoption-order
[TS §21]: ../architecture/tech_stack.md#21-version-baseline
[NFR-50]: ../requirements/requirements.md#nfr-50

# ADR-016: One OSV-Scanner pass audits every lockfile, and an accepted vulnerability expires

- **Status:** Accepted; extended by ADR-023 and ADR-072; amended by ADR-095 and ADR-134
- **Date:** 2026-10-30

## Context

The repository pins its dependencies in four lockfiles and one `pom.xml`: `uv.lock` in the
optimizer and in the research project, `package-lock.json` in the web app and in the phone app,
and the core's pom ([ADR-008]). The project rules require pinned dependencies and a failing
build on a known vulnerability (`.claude/CLAUDE.md`, Security).

The forces:

- **One answer for three ecosystems.** Maven, uv and npm each have their own audit tooling, with
  their own report format and their own way to accept a risk.
- **Some vulnerabilities cannot be fixed yet.** A fix may need a major version that another
  package does not accept, or it may not exist. The audit must allow a documented exception
  without turning the check off.
- **An exception must not become permanent.** Nobody remembers an old exception unless
  something forces it back into view.
- **The audit must run the same locally and in CI** ([NFR-16], [ADR-009]).
- **The repository is private.** A hosted scanner that needs a paid plan for private
  repositories is a cost, not a default.

## Decision

We scan every lockfile and the pom with OSV-Scanner, in one pass, from its container image. A
known vulnerability fails the audit.

- **OSV-Scanner v2.7.0**, from `ghcr.io/google/osv-scanner:v2.7.0`. The image is pinned in the
  `justfile` by its tag and its digest, so a re-pointed tag cannot change what runs.
- **`just audit` runs it:** `docker run --rm -v <repository>:/src:ro <image> scan source -r /src`.
  The repository is mounted read-only.
  It scans the whole repository recursively. CI runs the same recipe as the `audit` job
  ([ADR-015]). It needs Docker.
- **An accepted vulnerability goes into that folder's `osv-scanner.toml`**, as an
  `[[IgnoredVulns]]` entry with its `id`, a `reason` and an `ignoreUntil` date. When the date
  passes, the audit fails again, and the entry must be fixed, justified again or removed.
- **Two vulnerabilities are accepted today**, both in `src/frontend/mobile/osv-scanner.toml`
  and both until 2027-01-31:

| ID | Package | Why it is accepted |
|---|---|---|
| `GHSA-mrhx-c7xc-m3w5` | zod 3.25.76 | Reaches the app only through build tooling (`@expo/cli`, and `eslint-plugin-react-hooks` via `eslint-config-expo`), never the app bundle. It validates only our own config files. The only fix is zod 4, which `@expo/cli` does not accept. |
| `GHSA-gcqx-w48m-w2h4` | deepmerge 4.3.1 | Reaches the app only through build and test tooling (`@expo/config`, `jest-config`), never the app bundle. It merges only our own config objects. No fixed version exists. |

- **Accepting these two is not yet confirmed.** Until they are reviewed, the acceptance is a
  proposal written down so that the audit can pass. It is not a decision.

## Consequences

- **Easy:** one tool, one report format and one exception file (`osv-scanner.toml`) cover Java,
  Python and JavaScript.
- **Easy:** an exception names its reason, sits beside the lockfile it concerns, and expires
  on its own, so the audit forces the decision back.
- **Easy:** a new component's lockfile is found by the recursive scan, with no change to the
  recipe.
- **Hard:** `just audit` needs Docker, and the first run pulls the image ([ADR-014]).
- **Hard:** the two accepted vulnerabilities need a fix or a new justification by 2027-01-31, or
  `audit` and CI fail. Fixing zod depends on Expo, which we do not control.
- **Hard:** the scan covers declared dependencies in lockfiles and the pom. It does not scan the
  base images in the `Dockerfile`s.
- **Risk:** the pom's dependency versions come from the Spring Boot parent, which OSV-Scanner
  does not resolve. It finds the core's 15 declared packages with no version and skips them, so
  **no Java dependency is checked today**. The fix is a resolved SBOM of the core (for example
  from a CycloneDX Maven plugin) that the audit scans; it needs its own ADR, because it adds a
  build plugin.
- **Risk:** an `ignoreUntil` date gets extended without the reason being checked again. The
  reviewer of that pull request is the only control.
- **Risk:** the vulnerability database is online and moves. A pull request that touched nothing
  can fail because a new advisory appeared.
- **Cost:** the image tag is one more version to keep current. Dependabot's Docker ecosystem
  does not read the `justfile` ([ADR-015]), so a person has to bump it.

## Alternatives considered

- **A separate audit tool for each ecosystem** (`npm audit`, a uv audit, OWASP
  dependency-check). Rejected: it means several tools, several report formats and several ways
  to accept a risk. OWASP dependency-check also needs an NVD API key.
- **GitHub code scanning for dependencies.** Rejected: it needs GitHub Advanced Security on a
  private repository.

## Revisit when

- **Expo accepts zod 4, or an accepted vulnerability reaches its `ignoreUntil` date.** Upgrade,
  or write a new justification with a new date.
- **An accepted vulnerability is rejected.** Remove its entry, and fix or replace the
  package that brings it in.
- **The repository becomes public, or GitHub Advanced Security is paid for.** Compare GitHub's
  dependency scanning with this audit.
- **Container images are deployed** ([NFR-16] asks for Docker Compose). Add an image scan for
  the base images.
- **Before the core serves real data.** Close the Java gap above.

[ADR-008]: ADR-008-monorepo-layout-under-src.md
[ADR-009]: ADR-009-just-as-the-single-entry-point.md
[ADR-014]: ADR-014-real-dependencies-in-containers-for-tests-and-local-runs.md
[ADR-015]: ADR-015-ci-one-workflow-one-job-per-component.md
[NFR-16]: ../requirements/requirements.md#nfr-16

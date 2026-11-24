# ADR-038: mise pins every toolchain, and the core moves to Java 25

- **Status:** Accepted
- **Date:** 2026-11-13

## Context

[ADR-010] pins each language in a version file and rejected mise as "a second tool to install".
The repository holds these pins today:

| Pin | Value | Where |
|---|---|---|
| Java | 21 | `.java-version`, `<java.version>` in `src/backend/core/pom.xml`, both `FROM` lines of the core `Dockerfile` (`eclipse-temurin:21-jdk-noble`, `eclipse-temurin:21-jre-noble`) |
| Python | 3.14 | `.python-version`, `requires-python = ">=3.14,<3.15"` in both `pyproject.toml` files, `FROM python:3.14-slim` |
| Node | 24 | `.nvmrc` |
| uv | 0.12.7 | `UV_VERSION` in `.github/workflows/ci.yml`, the optimizer `Dockerfile`, and `uv_build` in both `pyproject.toml` files |
| Maven | 3.10.0 | `src/backend/core/.mvn/wrapper/maven-wrapper.properties` |
| `just` | 1.59.0 | `uvx --from rust-just==1.59.0 just` in `ci.yml` ([ADR-009]) |

[ADR-002] and [ADR-013] both state Java 21, and [ADR-010] gives its reason: the implementation plan
named it.

The tech stack now proposes `mise.toml`, which pins the JDK, Node.js, Python, uv, pnpm, just and
Terraform ([TS §5][TS-5]). Three things have changed since [ADR-010]:

- **The team is four people on Windows and macOS laptops** ([TS §1][TS-1], rule 3: "Four students,
  seven months"; [TS §5][TS-5], the *Tool versions* row). TS §5 gives the reason for mise: one file
  gives four laptops and CI the same versions of three language toolchains, on Windows and macOS.
- **More tools need a pin.** pnpm ([ADR-039]) and Terraform (from M13) have none today, and
  `just` is pinned only in CI.
- **The tech stack proposes Java 25** ([TS §6][TS-6], Language row). Since Java 24, a virtual
  thread no longer pins its carrier inside `synchronized`, which JDBC drivers and pools use. The
  tech stack has the core serve HTTP on virtual threads. Java 25 is also supported longer than 21.
  Both are LTS.

TS §21 says build files and `mise.toml` pin exact versions, because [NFR-62] and [NFR-115]
require solver and library versions to be recorded. It also says the Java library versions could
not be checked against Maven Central, so they are checked when the build files are written
([TS §21][TS-21]).

## Decision

We pin every toolchain in one root file, `mise.toml`, and move the core to Java 25.

- **`mise.toml` pins** the JDK, Node.js, Python, uv, pnpm, just and Terraform ([TS §5][TS-5]).
  A contributor runs `mise install`, then `just setup` ([PL §5][PL-5]).
- **The versions:**
  - Java 25, Eclipse Temurin ([TS §21][TS-21]).
  - Python 3.14, unchanged ([TS §7][TS-7]). `requires-python` stays in both `pyproject.toml`
    files, because uv reads it ([ADR-010]).
  - Node.js: the current LTS ([TS §21][TS-21]). The sources do not name the major. [ADR-010]'s
    open question about Expo and Node 26 carries over, and the M0 restructure pull request
    settles it when it writes `mise.toml` (decided on 2026-11-14).
  - uv, pnpm, just and Terraform: exact versions, chosen when `mise.toml` is written.
- **`mise.toml` replaces `.java-version`, `.python-version` and `.nvmrc`.** The root files in
  [TS §4][TS-4] list `mise.toml` and none of the three.
- **`just` comes from mise,** not from uv through `rust-just` ([ADR-009]'s install method). The
  `justfile` stays the single entry point.
- **Java 25 replaces Java 21** in `<java.version>` and in the core `Dockerfile`. [ADR-047]
  covers the runtime image.
- **What changes in the accepted ADRs.** [ADR-010] is replaced whole. Three others change in one
  part each, and the rest of each stands:
  - [ADR-002]: the Java 21 it states for `core`. Its split into a Java core and a Python service
    stands.
  - [ADR-013]: the Java 21 it states, and `java.version` set to 21. Its Maven wrapper and Spotless
    stand.
  - [ADR-009]: the install of `just` through `rust-just` and `uvx`. The `justfile` as the single
    entry point stands.
- **What stands:**
  - [ADR-013]'s build: Maven through the wrapper, which pins Maven beside the code, and Spotless
    with palantir-java-format.
  - [ADR-011]'s Python tooling.
  - Pins in files that cannot read `mise.toml`: the `Dockerfile` base images, `<java.version>`,
    the `uv_build` range and the Maven wrapper. They change together with the mise pins.

How CI installs the toolchain from `mise.toml` is not stated in the sources, and this ADR leaves
it open.

## Consequences

- **Easy:** one file holds every tool version, and the same file serves every laptop and CI.
- **Easy:** a new contributor installs one tool, runs `mise install`, then `just setup`.
- **Easy:** virtual threads no longer pin their carrier in `synchronized` code, which the Spring
  MVC adapter on virtual threads relies on ([TS §6][TS-6]).
- **Hard:** mise is a second tool to install, which was [ADR-010]'s objection. TS §5 says it works
  on Windows and macOS. Not run here: mise itself, and whether its tools resolve inside `just`
  recipes under PowerShell ([ADR-009]).
- **Hard:** CI changes. `ci.yml` reads `.java-version` and `.nvmrc` through `actions/setup-java`
  and `actions/setup-node`, and installs `just` and uv through `uvx` and `astral-sh/setup-uv`.
  Any action that installs mise must be pinned to a commit SHA ([ADR-015]).
- **Hard:** Java 25 is not checked against the build. Not checked: that Spring Boot 4.1.2 (the
  pom's parent), Spotless 3.10.4 with palantir-java-format 2.103.0, and the Maven 3.10.0 wrapper
  run on JDK 25.
- **Hard:** several pins still repeat, and nothing checks that they agree ([ADR-010]'s Hard note).
  mise removes the version files, not the repeats in `Dockerfile`s and the pom.
- **Cost:** these files name Java 21 or the version files, and change in the same pull request
  (each checked with a search):
  - Java 21: `src/backend/core/pom.xml` (line 17), `src/backend/core/Dockerfile` (lines 1 and 10),
    `justfile` (line 24, a comment), `.github/dependabot.yml` (line 101, a comment beside the
    `eclipse-temurin` major-version ignore), `.claude/CLAUDE.md` (line 23),
    `.claude/agents/df-code-reviewer.md` (line 11), `src/backend/CLAUDE.md` (line 8),
    `src/backend/core/CLAUDE.md` (line 4), `docs/architecture/architecture.md` (line 36),
    `docs/architecture/code-structure.md` (lines 22 and 34) and `README.md` (lines 44 and 139).
  - The version files: `.github/workflows/ci.yml` (`java-version-file: .java-version`),
    `.claude/agents/df-debugger.md` (line 28), `docs/architecture/code-structure.md` and
    `README.md`.
  - [ADR-046] has Renovate keep `mise.toml` current ([TS §5][TS-5]).
- **Cost:** mise is a new tool. Record its licence and maintenance status before adopting it, as
  `.claude/CLAUDE.md` requires of every new dependency. The sources give neither.
- **Risk:** the Node major is open. If Expo does not support the LTS that mise installs, the phone
  app breaks on a toolchain this ADR chose.

## Alternatives considered

- **Keep [ADR-010]'s version files.** Not chosen, for this ADR's own reasoning, not a source's: they
  pin three languages and nothing else, so pnpm and Terraform would need a pin elsewhere, and
  `just` stays pinned in CI only. The sources do not compare mise with the version files.
- **asdf.** Rejected: no native Windows support ([TS §5][TS-5]).
- **SDKMAN, nvm and pyenv.** Rejected: three tools and three configuration files, and pyenv is not
  native on Windows.
- **Dev containers.** Rejected: heavy on Windows laptops, and weaker in IntelliJ.
- **Stay on Java 21.** Rejected: it lacks the virtual-thread fix, and Java 25 is supported longer
  ([TS §6][TS-6]).

## Revisit when

- mise does not work on a contributor's Windows or macOS laptop, or inside `just` recipes. Go back
  to [ADR-010]'s version files.
- A plugin or library the build needs does not support JDK 25. Pin Java 21 until it does.
- Expo supports Node 26, or the LTS that mise installs breaks the phone app ([ADR-010]). Move the
  Node pin.
- OR-Tools ships wheels for CPython 3.15 ([TS §7][TS-7]). Consider Python 3.15.

[ADR-002]: ADR-002-core-backend-and-separate-optimization-service.md
[ADR-009]: ADR-009-just-as-the-single-entry-point.md
[ADR-010]: ADR-010-toolchain-pins-in-version-files.md
[ADR-011]: ADR-011-python-tooling-uv-ruff-mypy-pytest.md
[ADR-013]: ADR-013-java-tooling-maven-wrapper-and-spotless.md
[ADR-015]: ADR-015-ci-one-workflow-one-job-per-component.md
[ADR-039]: ADR-039-one-pnpm-workspace-for-both-apps-and-shared-packages.md
[NFR-62]: ../requirements/requirements.md#nfr-62
[NFR-115]: ../requirements/requirements.md#nfr-115
[PL-5]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#5-repository-and-local-environment
[TS-1]: ../architecture/tech_stack.md#1-what-decides-close-calls
[TS-4]: ../architecture/tech_stack.md#4-repository-layout
[TS-5]: ../architecture/tech_stack.md#5-development-environment-and-workflow
[TS-6]: ../architecture/tech_stack.md#6-core-service-java
[TS-7]: ../architecture/tech_stack.md#7-optimization-service-python
[TS-21]: ../architecture/tech_stack.md#21-version-baseline
[ADR-046]: ADR-046-renovate-replaces-dependabot-and-targets-dev.md
[ADR-047]: ADR-047-images-pinned-by-digest-and-core-on-temurin-25-jre.md

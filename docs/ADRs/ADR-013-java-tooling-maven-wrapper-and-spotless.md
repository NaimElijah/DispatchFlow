# ADR-013: The core backend builds with the Maven wrapper and formats with Spotless and palantir-java-format

- **Status:** Accepted; amended by ADR-038
- **Date:** 2026-10-30

## Context

The core backend, `src/backend/core`, is Java 21 on Spring Boot ([ADR-002], [ADR-010]). It is
the only Java component, and it has one `pom.xml`.

The forces:

- **A build must run the same for everyone** ([NFR-16]). Three people work on Windows and
  Linux, and CI runs on Linux. Nobody should need a Maven installed by hand ([ADR-009]).
- **Formatting must be a failing check, not a review comment.** The project rules make the
  formatter authoritative (`.claude/CLAUDE.md`). Hand-formatting and style arguments in review
  cost time that a tool does not.
- **Unit tests and integration tests have different costs.** The integration tests start a
  PostgreSQL container and need Docker ([ADR-014]). The unit tests do not, and should be
  runnable alone.
- **Spring Boot publishes a parent POM** that manages the versions of the libraries it supports.

## Decision

We build with Maven through its wrapper, and enforce formatting with Spotless in the `verify`
phase.

- **Maven, through the wrapper.** The `justfile` runs `mvnw` on Linux and `mvnw.cmd` on
  Windows. The wrapper's pins are in [ADR-010].
- **The parent is `spring-boot-starter-parent` 4.1.2**, which sets the versions of Spring and
  its libraries. `pom.xml` sets `java.version` to 21.
- **Spotless formats with palantir-java-format** (`2.103.0`, through
  `spotless-maven-plugin` `3.10.4`), and removes unused imports. Both versions are properties
  in `pom.xml`.
  - `spotless:check` is bound to `verify`, so `mvnw verify` fails on unformatted code.
  - `mvnw spotless:apply` fixes it. `just core-format` runs it.
- **Surefire runs the `*Test` classes** and **Failsafe runs the `*IT` classes**. Failsafe's
  `integration-test` and `verify` goals are bound in `pom.xml`. The `*IT` classes use
  Testcontainers and need Docker ([ADR-014]).
- **`just core-check` runs `mvnw verify`**, which covers the format check, the unit tests and
  the integration tests in one command.
- **Flyway owns the schema** ([ADR-007]).

## Consequences

- **Easy:** `mvnw verify` is the one command CI and a developer both run. Nothing has to be
  installed besides a JDK 21 and, for the integration tests, Docker.
- **Easy:** a formatting problem fails the build with a fix that is one command.
- **Easy:** the Spring Boot parent chooses compatible library versions, so `pom.xml` lists
  dependencies without most of their versions.
- **Hard:** `verify` is also the formatting gate, so a developer cannot run the integration
  tests alone without it checking format first. `mvnw spotless:apply` before `verify` avoids
  the failure.
- **Hard:** the 120-column limit differs from the 100 columns used in Python and in Prettier
  ([ADR-011], [ADR-012]). A reader moves between two line lengths.
- **Hard:** the integration tests make `verify` need Docker. The `Dockerfile` therefore packages
  with `-DskipTests`, and CI runs the tests.
- **Cost:** the Spotless plugin and palantir-java-format are versions that nobody else bumps
  for us unless Dependabot does ([ADR-015]). A new palantir release can reformat code, and the
  reformat then lands in its own commit.

## Alternatives considered

Both choices were made without a recorded reason. The trade-offs below are what can be
observed, not stated reasons.

- **Gradle instead of Maven.** Rejected. Gradle has a build cache and incremental builds, and
  its build logic is code in Groovy or Kotlin. Maven's build is declarative XML, with one
  lifecycle (`verify` includes the format check and both test kinds) and the Spring Boot
  parent POM already used here. The build is small enough that speed is not a constraint
  today.
- **google-java-format instead of palantir-java-format.** Rejected. palantir-java-format is a
  fork of google-java-format. Its style has a 120-column limit where google-java-format's
  default has 100, and it formats lambdas and long chains differently. Spotless supports
  both, so the choice can be reversed with a one-line change and a reformat.

## Revisit when

- **A build takes long enough to hurt** (CI above about 10 minutes, see [ADR-015]) and the
  cause is Maven's lack of incremental or cached builds. Compare Gradle then.
- **The 120-column limit proves a problem** for review or editors. Switch Spotless to
  google-java-format.
- **A second Java component joins the repository.** Share the Spotless and plugin versions
  through a parent POM.

[ADR-002]: ADR-002-core-backend-and-separate-optimization-service.md
[ADR-007]: ADR-007-postgresql-with-flyway-migrations.md
[ADR-009]: ADR-009-just-as-the-single-entry-point.md
[ADR-010]: ADR-010-toolchain-pins-in-version-files.md
[ADR-011]: ADR-011-python-tooling-uv-ruff-mypy-pytest.md
[ADR-012]: ADR-012-javascript-tooling-npm-eslint-prettier.md
[ADR-014]: ADR-014-real-dependencies-in-containers-for-tests-and-local-runs.md
[ADR-015]: ADR-015-ci-one-workflow-one-job-per-component.md
[NFR-16]: ../requirements/requirements.md#nfr-16

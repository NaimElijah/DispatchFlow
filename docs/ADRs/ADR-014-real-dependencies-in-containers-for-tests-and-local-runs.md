# ADR-014: Integration tests and local runs use a real PostgreSQL in a container

- **Status:** Accepted
- **Date:** 2026-10-30

## Context

The core backend keeps its data in PostgreSQL ([ADR-007]). Two things need a running database:
the integration tests, and a developer who starts the backend to try it.

The forces:

- **The project rule is "real dependencies in containers (Testcontainers) for things we do not
  own; mock only our own ports"** (`.claude/CLAUDE.md`, Testing). A mock of the database would
  test our expectations of PostgreSQL, not PostgreSQL.
- **A database's behaviour is part of the system.** Flyway migrations, constraints, a job
  queue, if it is a table, and later PostGIS ([ADR-007]) all depend on the real engine.
- **Nobody should need to install and manage a database** to run the tests or the backend. Three
  people use different machines ([ADR-009]).
- **A test must not share state.** A test run should start from an empty database that nobody
  else is using.
- **One Docker Compose command is a requirement** for starting every server-side component
  ([NFR-16]). It is not met yet.

## Decision

We start a real PostgreSQL in a Docker container, through Testcontainers, for the integration
tests and for local runs. Docker is a prerequisite of `just check`.

- **One configuration serves both.** `TestcontainersConfiguration`, in
  `src/backend/core/src/test/java/com/dispatchflow`, declares a `PostgreSQLContainer` for the
  image `postgres:18.6`. `@ServiceConnection` wires it in as the application's datasource, so
  no connection setting is written anywhere.
- **The integration tests import it.** `DispatchFlowApplicationIT` is a `@SpringBootTest` that
  imports the configuration. It passes when the application's context loads against the
  container. Failsafe runs `*IT` classes ([ADR-013]).
- **A local run uses it too.** `TestDispatchFlowApplication` starts the application with
  `SpringApplication.from(...).with(TestcontainersConfiguration.class)`. `just core-run` runs it
  through `mvnw spring-boot:test-run`, on port 8080, against a throwaway database ([ADR-009]).
- **Docker is needed for `just check`.** The core's integration tests need it, and so does
  `just audit` ([ADR-016]). The `Dockerfile` skips the tests, and CI runs them.

## Consequences

- **Easy:** the tests run against the engine the system uses. A migration, a constraint or a
  query that PostgreSQL rejects fails in the test, not in production.
- **Easy:** every run starts from an empty database. No state leaks between runs, and no
  cleanup is needed.
- **Easy:** a new developer runs `just core-run` with only Docker and a JDK installed.
- **Hard:** `just check` and `just core-run` need Docker. A machine without it cannot run the
  core's integration tests.
- **Hard:** a container takes seconds to start, so the integration tests are slower than ones
  against an in-memory database.
- **Hard:** the PostgreSQL version is a string in `TestcontainersConfiguration.java`. Dependabot's
  Docker updates cover the two `Dockerfile` folders ([ADR-015]), so nothing proposes a new
  version of this string, and it must be matched by hand when a deployment target is chosen.
- **Risk:** the only migration is the placeholder example's ([ADR-007]). `WidgetApiIT` runs it
  against the container, but no real schema is tested yet. Every `*IT` uses the one
  `@IntegrationTest` annotation, so they share one Spring context and one container.
- **Cost:** a Docker daemon on every developer machine and on the CI runner.

## Alternatives considered

- **An in-memory H2 database.** Rejected: its SQL dialect differs from PostgreSQL's, so a test
  could pass on H2 and fail on PostgreSQL, or the reverse.
- **A shared development database.** Rejected: tests would share state with each other and
  with developers, and a run would depend on the network.
- **A PostgreSQL installed and managed by hand on each machine.** Rejected: each machine would
  drift in version and data, and a new contributor would have to set it up.
- **A Docker Compose file for the database.** Deferred, not rejected. [NFR-16] asks for one
  Compose command that starts every server-side component, and that file does not exist yet.
  When it does, a developer can use it for a long-running database. The tests keep Testcontainers
  because each run still needs its own empty database.

## Revisit when

- **The Compose file exists.** Decide whether `just core-run` should use its database instead of
  a throwaway container.
- **The integration tests take too long** for the team to run them before a push. Reuse one
  container across test classes, or run fewer of them in the default recipe.
- **A deployment target fixes the PostgreSQL version.** Make the test image match it, and put
  the version in one place.
- **A machine without Docker must run the tests.** Docker is then the constraint to remove, not
  the test.

[ADR-007]: ADR-007-postgresql-with-flyway-migrations.md
[ADR-009]: ADR-009-just-as-the-single-entry-point.md
[ADR-013]: ADR-013-java-tooling-maven-wrapper-and-spotless.md
[ADR-015]: ADR-015-ci-one-workflow-one-job-per-component.md
[ADR-016]: ADR-016-dependency-audit-with-osv-scanner.md
[NFR-16]: ../requirements/requirements.md#nfr-16

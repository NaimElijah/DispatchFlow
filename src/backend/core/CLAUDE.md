# Core

The REST API that both clients call (`contracts/client-api/v1`) and DispatchFlow's system of
record. It is the only client of the solver (`contracts/optimization-api/v1`). Java 21, Spring
Boot 4.1, Spring Data JPA, Flyway, PostgreSQL, Maven through the wrapper.

## Layout

- `src/main/java/com/dispatchflow/` holds one package per bounded context, each split into
  `api`, `domain`, `application` and `adapter` ([ADR-001]). No real context exists yet (#5).
  `example` is a placeholder that shows the structure to copy: see
  `docs/architecture/code-structure.md`.
- `src/main/resources/db/migration/` holds the Flyway migrations ([ADR-007]).
- `src/test/java/com/dispatchflow/` holds the tests, `TestcontainersConfiguration` (PostgreSQL
  18.6 in a container) and `TestDispatchFlowApplication`, which `just core-run` starts.

## Rules

- `domain` imports nothing from Spring, Jakarta Persistence, Jackson or the web layer. JPA
  entities and request and response types live in `adapter` and are mapped to domain objects.
- A context uses another only through that context's `api` package.
- The schema changes only through a new migration, `V<n>__<what_it_does>.sql`. Never edit a
  migration that has been applied anywhere; add the next one.
- Configuration comes from environment variables. A new key goes into `.env.example` with no
  value.
- Queries take parameters: Spring Data methods, or JPQL and SQL with bound parameters. Never
  concatenate input into a query.
- The image runs as the non-root user `dispatchflow` ([ADR-019]). Keep it that way.

## Tests

- `*Test` classes are unit tests, run by Surefire. `*IT` classes are integration tests, run by
  Failsafe against a real PostgreSQL: annotate them with `@IntegrationTest`, so they all share one
  Spring context and one container. They need Docker ([ADR-014]).
- Test a context through its `api` and its use cases.

## Commands

Run `just` recipes from the repository root, and `mvnw` from this folder (`.\mvnw.cmd` on
Windows).

| Command | What it does |
|---|---|
| `just core-check` | `mvnw verify`: Spotless check, unit tests, integration tests |
| `just core-format` | `mvnw spotless:apply` |
| `just core-run` | Starts the core on port 8080 against a throwaway PostgreSQL container |
| `./mvnw -Dtest=FooTest test` | One unit test class |
| `./mvnw -Dit.test=FooIT verify` | One integration test class, after the unit tests and the format check |

## Gotchas

- Spotless runs inside `verify`, so an unformatted file fails `just core-check`. Run
  `just core-format` first.
- `mvnw` must stay executable in git. When adding it again, use
  `git add --chmod=+x src/backend/core/mvnw`, or CI fails with "permission denied".

[ADR-001]: ../../../docs/ADRs/ADR-001-domain-driven-design-with-hexagonal-architecture.md
[ADR-007]: ../../../docs/ADRs/ADR-007-postgresql-with-flyway-migrations.md
[ADR-014]: ../../../docs/ADRs/ADR-014-real-dependencies-in-containers-for-tests-and-local-runs.md
[ADR-019]: ../../../docs/ADRs/ADR-019-container-images-run-as-non-root.md

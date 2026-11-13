# Core

The REST API that both clients call (`contracts/client-api/v1`) and DispatchFlow's system of
record. It is the only client of the solver (`contracts/optimization-api/v1`). Java 21, Spring
Boot 4.1, Spring Data JPA, Flyway, PostgreSQL, Maven through the wrapper.

Proposed changes, not in the tree yet: Spring Modulith ([ADR-040], Proposed) and jOOQ in place of
JPA ([ADR-041], Proposed). The layout, jOOQ and module rules below are the design's: see
`.claude/CLAUDE.md` (Documentation) for when they bind.

## Layout

- `src/main/java/com/dispatchflow/` holds `DispatchFlowApplication` and `web/`, the error handler
  that belongs to no context. No bounded context exists yet (#5). Each will be one module, laid out
  as in [TS §4] and [PL §11.1]:

  ```text
  <module>/domain/, events/
  <module>/application/{api,spi,port/out,service}
  <module>/adapter/{in/web,in/job,in/listen,...,out/persistence,...}
  shared/                      the shared kernel, beside the modules
  modules: access fleet orders planning liveops messaging reporting audit
  ```

- `src/main/resources/db/migration/` holds the Flyway migrations ([ADR-007]).
- `src/test/java/com/dispatchflow/` holds the tests, `TestcontainersConfiguration` (PostgreSQL
  18.6 in a container) and `TestDispatchFlowApplication`, which `just core-run` starts.

## Rules

- `domain` stays pure ([DL §3.9]). Six rules, each an ArchUnit test once [ADR-040] lands:
  1. It imports only the JDK, the shared kernel, its own domain, its own module's `events`
     package, JSpecify, libphonenumber and JTS. [DL §3.9] does not list `events`; aggregates
     raise events that live there ([DL §7.3]), and [ADR-040] records that reading.
  2. It never calls `Instant.now()`, `LocalDate.now()`, `Clock.system…()` or
     `System.currentTimeMillis()`.
  3. It never calls `UUID.randomUUID()`; factories take the new ID.
  4. It uses no randomness, I/O or logging.
  5. It never creates an `IllegalArgumentException` or an `IllegalStateException`; it throws the
     module's named error.
  6. Only persistence adapters, and the factories of the snapshots read under a lock
     (`ActiveAdmins`, `DateCapacity`, `LaneCapacity`), call `restore`.
- Web and persistence types live in `adapter` and are mapped to domain objects.
- Another module is reached only through its `api`, `spi` and `events` packages ([DL §2.3]).
  Spring Modulith's `verify()` fails the build on any other use ([ADR-040], Proposed).
- Each module owns its tables, in its own PostgreSQL schema. No SQL reads another module's schema
  ([DL §2.3], rule 3).
- A use case follows [PL §11.2]:
  - One class per use case. Its one `@Transactional` method changes one existing aggregate, after
    the outside calls.
  - `@ApplicationModuleListener` reactions are idempotent, because delivery is at least once.
  - Every changing use case writes its `AuditLog` record in the same transaction, with no personal
    data ([TS §6.2], NFR-94).
- No Lombok: records and plain classes ([TS §6]).
- The schema changes only through a new migration, `V<n>__<what_it_does>.sql`. Never edit a
  migration that has been applied anywhere; add the next one.
- Configuration comes from environment variables. A new key goes into `.env.example` with no
  value.
- Persistence is jOOQ through hand-written mappers ([ADR-041], Proposed; [TS §6.3]). The pom keeps
  Spring Data JPA until M0 replaces it. Never concatenate input into a query: bind parameters.
  jOOQ's plain-SQL API (methods annotated `@PlainSQL`, such as `DSL.field(String)` and
  `DSL.condition(String)`) never takes input.
- The image runs as the non-root user `dispatchflow` ([ADR-019]). Keep it that way.

## Tests

- `*Test` classes are unit tests, run by Surefire. `*IT` classes are integration tests, run by
  Failsafe against a real PostgreSQL: annotate them with `@IntegrationTest`, so they all share one
  Spring context and one container. They need Docker ([ADR-014]).
- Once a module exists, test it in layers ([DL §14], [PL §24]):
  - Domain tests use no Spring and no mocks. Aggregates take values, `now` and new IDs as
    parameters. Every invariant in [DL §4] has a test that breaks it and asserts the module's named
    error. Every transition has one test that takes it, plus one per other state that must refuse
    it.
  - Application tests run each use case against in-memory fakes of its ports.
  - Module and event tests (`@ApplicationModuleTest` boots one module alone; `Scenario` tests an
    event reaction) boot Spring, so they are not Surefire unit tests. The suffix and the container
    wiring are decided with the first module (M0). Publish each event twice: after the second
    delivery the state equals the state after the first. Wait through `Scenario`, never `sleep`.
  - Permission tests are generated from the contract's `x-capability` mapping and the permission
    matrix ([NFR-176]).
  - Build test data with one builder per aggregate, such as `aPlan()`. No test reads a shared
    fixture file that other tests also change ([PL §24]).
  - The failure paths the design names are listed in `.claude/agents/df-tester.md`.

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
- Coming with jOOQ ([ADR-041], Proposed): code generation runs the migrations in a Testcontainers
  PostgreSQL, so Docker must run for `./mvnw verify`.

[ADR-007]: ../../../docs/ADRs/ADR-007-postgresql-with-flyway-migrations.md
[ADR-014]: ../../../docs/ADRs/ADR-014-real-dependencies-in-containers-for-tests-and-local-runs.md
[ADR-019]: ../../../docs/ADRs/ADR-019-container-images-run-as-non-root.md
[ADR-040]: ../../../docs/ADRs/ADR-040-core-is-a-spring-modulith-monolith-with-one-module-per-context.md
[ADR-041]: ../../../docs/ADRs/ADR-041-one-postgresql-schema-per-module-read-and-written-through-jooq.md
[NFR-176]: ../../../docs/requirements/requirements.md#nfr-176
[DL §2.3]: ../../../docs/architecture/domain_layer/domain_layer_design.md#23-rules-between-contexts
[DL §3.9]: ../../../docs/architecture/domain_layer/domain_layer_design.md#39-purity
[DL §4]: ../../../docs/architecture/domain_layer/domain_layer_design.md#4-aggregates-context-by-context
[DL §7.3]: ../../../docs/architecture/domain_layer/domain_layer_design.md#73-shape-and-delivery
[DL §14]: ../../../docs/architecture/domain_layer/domain_layer_design.md#14-testing-the-domain
[PL §11.1]: ../../../docs/init_impl_ref/technical_implementation_plan.md#111-module-skeleton
[PL §11.2]: ../../../docs/init_impl_ref/technical_implementation_plan.md#112-rules-every-use-case-follows
[PL §24]: ../../../docs/init_impl_ref/technical_implementation_plan.md#24-testing
[TS §4]: ../../../docs/architecture/tech_stack.md#4-repository-layout
[TS §6]: ../../../docs/architecture/tech_stack.md#6-core-service-java
[TS §6.2]: ../../../docs/architecture/tech_stack.md#62-application-layer
[TS §6.3]: ../../../docs/architecture/tech_stack.md#63-persistence

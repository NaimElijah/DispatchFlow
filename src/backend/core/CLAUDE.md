# Core

The REST API that both clients call (`contracts/client-api/v1`) and DispatchFlow's system of
record. It is the only client of the optimizer (`contracts/optimization-api/v1`). Java 25, Spring
Boot 4.1, Spring Data JPA, Flyway, PostgreSQL, Maven through the wrapper.

## Layout

- `src/main/java/com/dispatchflow/` holds `DispatchFlowApplication`, `web/` (the error handler that
  belongs to no context), and:
  - `shared/`, the shared kernel: the typed IDs, the shared values with their errors (`Attempt` is
    one, [ADR-111]), and the building blocks ([ADR-097], [ADR-106]). Never add a type that holds one
    context's rule.
  - `shared/application/`, the application ports every module uses: `Clock`, `IdGenerator`,
    `ProcessedEvents`, `NotFound` and `StaleVersion` ([ADR-120]), a package of its own under the
    kernel's purity rules: no Spring, but `@NamedInterface` on its `package-info` ([ADR-121]).
  ```text
  <module>/domain/, events/
  <module>/application/{api,spi,port/out,service}      bodies throw (ADR-119)
  <module>/adapter/{in/web,in/job,in/listen,...,out/persistence,...}
  modules: access fleet orders planning liveops messaging reporting audit
  ```
- Package size ([ADR-125], which amends ADR-118): the root's 20-file threshold counts Java files,
  not `package-info.java`. A domain splits by aggregate over a root, `domain/`, that holds its error
  and shared values, then by concept; an application package splits by aggregate, then by a concept
  beneath it (`service/plan/edit`). Files the language binds (package-private access, a sealed
  hierarchy) or a dependency cycle holds stay together: never widen production visibility or add a
  cycle to split. The kernel's domain types and every `api`, `spi` and `events` package stay flat.
  Each new package carries `@NullMarked`. `DomainPackageRules` and `ApplicationRules` check the
  cycles, the root imports and `@NullMarked`.
- `src/test/java/com/dispatchflow/` holds the tests, `TestcontainersConfiguration` (PostgreSQL
  18.6 in a container) and `TestDispatchFlowApplication`, which `just core-run` starts.

## Rules

- `domain`, `events` and `shared` stay pure ([DL §3.9], [ADR-097]). `DomainPurityRules`, in
  `src/test/java/com/dispatchflow/architecture/`, holds the full lists and fails the build. What
  goes against Java's defaults:
  - A module's domain imports only the JDK, the kernel, its own domain and `events`, JSpecify,
    libphonenumber and JTS. The kernel takes only the JDK, JSpecify and libphonenumber, and an
    `events` package only the JDK, JSpecify, the kernel and itself. Spring Modulith annotations are
    allowed on a `package-info` only.
  - They never read the clock (`now()`, `Clock`, `Date`, `System.currentTimeMillis()`,
    `Thread.sleep` and the like). They never call `UUID.randomUUID()`: factories take the new ID,
    and a method that creates many children takes a `FreshIds`.
  - They use no randomness, I/O, logging, reflection, threads, system properties or resources
    (`java.net.IDN` is the one allowed `java.net` class), and no default locale: a case change,
    `String.format`, `DateTimeFormatter.ofPattern`, `toFormatter` and the `*Format` classes take a
    `Locale`, and `formatted` and `DateTimeFormatter.ofLocalized*` are never used.
  - They never create an `IllegalArgumentException` or an `IllegalStateException`, or a subclass of
    either. They throw the module's named error in a `RuleViolation`, or a `StoredStateInvalid` from
    `restore`.
  - Only persistence adapters call an aggregate's `restore`, or the `readUnderLock` factory of a
    locked snapshot. Only the optimizer adapter builds Planning's `ValidationReport` or `Evaluation`
    ([DL §13]).
- `DomainShapeRules` checks the shape. What goes against Java's defaults:
  - The constructors of an aggregate root, a locked snapshot, `ValidationReport`, `Evaluation` and
    `ValidatedSolution` are private. Every interface that extends `DomainError` is sealed, and no
    error declares its own `code()`.
  - Error fields become a response's `params` ([ADR-043]), so they never hold text, an `Object`, an
    array or anything personal ([ADR-106]).
  - Every personal value implements `PersonalData`. No test checks that; review does. Every concrete
    `PersonalData` type declares its own redacting `toString()`: a record's implicit one doesn't
    count.
  - An event's fields are typed IDs, `EventId`, `Version`, the shared values other than
    `PhoneNumber`, `GeoPoint`, `PlainText` and `Attempt`, the package's own enums, `Instant`, `int`,
    `long`, `boolean`, or lists of these (a field is never boxed). No field holds a `PersonalData`
    value.
- `ApplicationRules` ([ADR-119], [ADR-120]) numbers its rules in its methods' Javadoc, with a
  fixture under `archfixture/zeta/` for each. What goes against the defaults:
  - The application layer imports only the JDK, JSpecify, the kernel, its own module's `domain`,
    `events` and `application`, and other modules' `api` and `events` (another module's `spi` only
    as the rule allows). No `adapter`, no libphonenumber or JTS, and from Spring only
    `@Transactional`.
  - Modules depend on each other along [DL §2.1]'s arrows and no others. No `api` or `spi` type uses
    a domain type.
  - Each `api` interface `X` has one implementation, `XService`, in its module's
    `application.service`. Each `spi` interface has one, in the module DL §2.1's dashed arrow names
    (`SPI_IMPLEMENTERS`): `XService`, or `FleetUsageService` and `CustomerUsageService` for the two
    `RecordUsage` ports.
  - No cycle between a module's application packages. No domain, `events` or kernel-root class
    imports `shared.application`. Reporting uses `liveops`, `planning` and `orders` only through
    their `events`, and Access's `api` only through `Actor` and `ActorRole` ([ADR-121]).
- Error codes and event names are unique across modules (the allow-listed codes mean the same
  thing), and every error has a refusal test: `ErrorCodeUniquenessTest`, `EventNameUniquenessTest`
  and `ErrorRefusalTestsTest`. Every rule has a deliberately wrong fixture under `archfixture/` that
  it must flag, and a new module is covered without editing the tests.
- A value rebuilt from storage goes through `StoredStateInvalid.whileRestoring`, and a stored phone
  number through `PhoneNumber.ofStored`. So a damaged or since-refused value is an incident, never a
  user-facing refusal. No test checks this; review does.
- Web and persistence types live in `adapter` and are mapped to domain objects.
- Another module is reached only through its `api`, `spi` and `events` packages ([DL §2.3]). Spring
  Modulith ([ADR-040]) isn't in the build yet: until its `verify()` runs, review checks this for
  adapters, which `ApplicationRules` doesn't cover. Each module owns its tables, in its own
  PostgreSQL schema: no SQL reads another module's schema.
- An application body that would do work throws `UnsupportedOperationException("Built in M<n>")`
  until its milestone builds it. `grep "Built in M5"` lists a milestone's remaining work. To build a
  body ([ADR-119], decision 5): write the failing test first, replace the `throw`, then build the
  adapter. A milestone may change a declared signature or add a class, with every caller in the same
  pull request. Delete the Javadoc's "Built in M<n>" when the body is built.
- A use case follows [TS §6.2], [ADR-063] and [ADR-064]:
  - One class per use case. Its one `@Transactional` method changes one existing aggregate, after
    the outside calls.
  - The application layer takes only `@Transactional` from Spring. `@Retryable` and
    `@ConcurrencyLimit` sit on the adapter that makes the outside call, where a retry is safe.
  - `@ApplicationModuleListener` reactions are idempotent, because delivery is at least once.
  - Every changing use case writes its `AuditLog` record in the same transaction, with no personal
    data (NFR-94).
- Every handler is authorized by role from its operation's `x-capability`. A check that depends on
  the data, such as a driver's own route, lives in the use case ([ADR-068]).
- Flyway runs as `dispatchflow_owner` in a one-shot `migrate` step before the core starts. The owner
  credential lives only in `migrate`'s environment. The core connects as `app` and never owns a
  table ([ADR-041], [TS §6.3]). Spring Boot still runs Flyway when the core starts: the change that
  adds `migrate` moves it there.
- Optimistic locking is `UPDATE … WHERE id = ? AND version = ?` on the root table. Zero rows updated
  answers 412 `STALE_VERSION`, through `ETag` and `If-Match`, on a call that sent a version. A call
  that sent none is retried at once, up to three times, then answered 503 with `Retry-After`
  ([ADR-121]). Child tables carry no version ([ADR-057]).
- The `e2e` profile's test-only endpoints exist only in that profile, and the profile refuses to
  boot when the SMS credential is present, with a test for each (NFR-206).
- Until M5's sign-in, the stand-in session exists only when `dev` or `test` is the only active
  profile, and the core refuses to boot with the stand-in and any other profile active. Unless `dev`
  is the only active profile, the core refuses to boot with an access-token lifetime other than 15
  minutes. Each refusal has a test. M5 deletes the stand-in, and M6 the dev allowance ([ADR-121]).
- The schema changes only through a new migration in `src/main/resources/db/migration/`,
  `V<n>__<what_it_does>.sql`. Never edit a migration that has been applied anywhere; add the next
  one.
- No Lombok: records and plain classes. Configuration comes from environment variables.
- Persistence is jOOQ through hand-written mappers ([ADR-041]; [TS §6.3]). The pom keeps Spring Data
  JPA until M0 replaces it: never write new JPA code. jOOQ's plain-SQL API (methods annotated
  `@PlainSQL`, such as `DSL.field(String)` and `DSL.condition(String)`) never takes input.

## Tests

- `*Test` classes are unit tests, run by Surefire. `*IT` classes are integration tests, run by
  Failsafe against a real PostgreSQL: annotate them with `@IntegrationTest`, so they all share one
  Spring context and one container. They need Docker.
- Test in layers ([DL §14], [TS §17.1]):
  - Domain tests use no Spring and no mocks. Aggregates take values, `now` and new IDs as
    parameters. Every invariant in [DL §4] has a test that breaks it and asserts the module's named
    error. Every transition has one test that takes it, plus one per other state that must refuse
    it.
  - A refusal test asserts through `DomainAssertions.assertRefused` (or `assertAllRefused` for a
    `RuleViolations`). It is a `@Test` or `@ParameterizedTest` whose name ends in
    `_Then<ErrorName>`, and may sit anywhere under its error's package.
  - Application tests run each use case against in-memory fakes of its ports.
  - Module and event tests (`@ApplicationModuleTest` boots one module alone; `Scenario` tests an
    event reaction) boot Spring, so they are not Surefire unit tests. Their suffix and container
    wiring are set with the first module (M0). Publish each event twice: after the second delivery
    the state equals the state after the first. Wait through `Scenario`, never `sleep`.
  - Generate permission tests from the contract's `x-capability` mapping and the permission matrix
    ([NFR-176]).
  - A test sits in the package of the type it tests, and one that covers several packages' values
    sits in the module's root. A helper that several test packages use is public.
  - Build test data with one builder per aggregate, such as `aPlan()`. No test reads a shared
    fixture file that other tests also change.
  - The failure paths the design names are listed in `.claude/agents/df-tester.md`.

## Commands

Run `just` recipes from the repository root, and `mvnw` from this folder (`.\mvnw.cmd` on Windows).

| Command | What it does |
|---|---|
| `just core-check` | `mvnw verify`: Spotless check, unit tests, integration tests |
| `just core-format` | `mvnw spotless:apply` |
| `just core-run` | Starts the core on port 8080 against a throwaway PostgreSQL container |
| `./mvnw -Dtest=FooTest test` | One unit test class |
| `./mvnw -Dit.test=FooIT verify` | One integration test class, after the unit tests and the format check |

- Spotless checks in `validate`, so every build (`test`, `verify`, `just core-run`) fails on an
  unformatted file. Run `just core-format` first.
- `mvnw` must stay executable in git. When adding it again, use
  `git add --chmod=+x src/backend/core/mvnw`, or CI fails with "permission denied".

[ADR-040]: ../../../docs/ADRs/ADR-040-core-is-a-spring-modulith-monolith-with-one-module-per-context.md
[ADR-041]: ../../../docs/ADRs/ADR-041-one-postgresql-schema-per-module-read-and-written-through-jooq.md
[ADR-043]: ../../../docs/ADRs/ADR-043-generated-code-from-both-contracts-and-problem-details-errors.md
[ADR-057]: ../../../docs/ADRs/ADR-057-concurrent-changes-to-a-plan-use-optimistic-locking.md
[ADR-063]: ../../../docs/ADRs/ADR-063-a-transaction-changes-one-aggregate-and-every-rule-has-one-owner.md
[ADR-064]: ../../../docs/ADRs/ADR-064-events-only-where-they-earn-their-place-through-the-modulith-registry.md
[ADR-068]: ../../../docs/ADRs/ADR-068-every-web-handler-is-authorized-by-role-from-the-contract.md
[ADR-097]: ../../../docs/ADRs/ADR-097-the-shared-kernel-holds-the-domains-building-blocks.md
[ADR-106]: ../../../docs/ADRs/ADR-106-free-text-is-normalized-and-capped-by-one-kernel-rule.md
[ADR-111]: ../../../docs/ADRs/ADR-111-attempt-is-a-shared-kernel-value-and-names-and-addresses-drop-invisible-fillers.md
[ADR-119]: ../../../docs/ADRs/ADR-119-the-application-layer-is-declared-ahead-as-signatures-whose-bodies-throw.md
[ADR-120]: ../../../docs/ADRs/ADR-120-the-application-layer-takes-an-actor-and-a-call-context-and-reads-through-query-ports.md
[ADR-121]: ../../../docs/ADRs/ADR-121-the-application-skeletons-review-settles-its-seams-conflicts-retention-and-scoring-inputs.md
[ADR-125]: ../../../docs/ADRs/ADR-125-a-folder-above-20-files-splits-wherever-a-group-of-four-can-move.md
[DL §2.1]: ../../../docs/architecture/domain_layer/domain_layer_design.md#21-module-graph
[DL §2.3]: ../../../docs/architecture/domain_layer/domain_layer_design.md#23-rules-between-contexts
[DL §3.9]: ../../../docs/architecture/domain_layer/domain_layer_design.md#39-purity
[DL §4]: ../../../docs/architecture/domain_layer/domain_layer_design.md#4-aggregates-context-by-context
[DL §13]: ../../../docs/architecture/domain_layer/domain_layer_design.md#13-inside-each-module-hexagon
[DL §14]: ../../../docs/architecture/domain_layer/domain_layer_design.md#14-testing-the-domain
[NFR-176]: ../../../docs/requirements/requirements.md#nfr-176
[TS §6.2]: ../../../docs/architecture/tech_stack.md#62-application-layer
[TS §6.3]: ../../../docs/architecture/tech_stack.md#63-persistence
[TS §17.1]: ../../../docs/architecture/tech_stack.md#171-by-layer

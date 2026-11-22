# Core

The REST API that both clients call (`contracts/client-api/v1`) and DispatchFlow's system of
record. It is the only client of the optimizer (`contracts/optimization-api/v1`). Java 21, Spring
Boot 4.1, Spring Data JPA, Flyway, PostgreSQL, Maven through the wrapper.

Accepted changes, not in the tree yet: Spring Modulith ([ADR-040], Accepted) and jOOQ in place of
JPA ([ADR-041], Accepted). The layout, jOOQ and module rules below are the design's: see
`.claude/CLAUDE.md` (Documentation) for when they bind.

## Layout

- `src/main/java/com/dispatchflow/` holds:
  - `DispatchFlowApplication`;
  - `web/`, the error handler that belongs to no context;
  - `shared/`, the shared kernel: the typed IDs, the shared values with their errors (`Attempt` is
    one, [ADR-111]), and the building blocks ([ADR-097], [ADR-106]). These are `DomainError`, `RuleViolation(s)`,
    `StoredStateInvalid`, `Version`, `AggregateRoot`, `DomainEvent`, `EventId`, `FreshIds`,
    `PlainText` with its `TextKind`, and the `PersonalData` marker.
    Never add a type that holds one context's rule.
  - `shared/application/`, the application ports every module uses: `Clock`, `IdGenerator`,
    `ProcessedEvents`, `NotFound` and `StaleVersion` ([ADR-120]). It is a package of its own, so the
    kernel's domain types stay flat ([ADR-118]).
- Built so far: the `domain` packages of `access`, `audit`, `fleet`, `liveops`, `messaging`,
  `orders`, `planning` and `reporting`, and the `events` packages of the six that raise events (not
  `audit` or `reporting`). The contexts are decided ([DL §1]), and each is
  one module, laid out as in [TS §4] and [ADR-040]. Each module's domain is built ahead of its
  milestone, and M0 adds the first adapters ([roadmap §2.2]).
- Declared, not built: each module's `application` layer, in whichever of `api`, `spi`, `port/out`
  and `service` it has something for ([ADR-119]). Every use case, query, port and module API has its
  signature and Javadoc, and every body that would do work throws
  `UnsupportedOperationException("Built in M<n>")`. Nothing is wired: no Spring annotation, no bean,
  no adapter. `grep "Built in M5"` lists a milestone's remaining work.

  ```text
  <module>/domain/, events/
  <module>/application/{api,spi,port/out,service}      declared, bodies throw (ADR-119)
  <module>/adapter/{in/web,in/job,in/listen,...,out/persistence,...}      not in the tree yet
  shared/                      the shared kernel, beside the modules
  shared/application/          Clock, IdGenerator, ProcessedEvents, NotFound, StaleVersion (ADR-120)
  modules: access fleet orders planning liveops messaging reporting audit
  ```

  A module's domain splits into `domain/<aggregate>/` packages over a root, `domain/`, that holds
  the module's error and what several packages use ([ADR-118]). `access`, `fleet`, `liveops`,
  `messaging` and `planning` are split; `orders`, `reporting` and `audit` are flat, and so are
  the kernel's domain types and every `events/`. Planning's packages are `plan`, `solution`,
  `basis`, `revision`, `reconciliation` and `settings`. The threshold applies to every source
  folder, so an application package above 30 files splits by aggregate too: Planning's `service` and
  `port/out`, Access's `service`, and Live Operations' `service` and `port/out` are split.
- `src/main/resources/db/migration/` holds the Flyway migrations, which [ADR-041] keeps from
  ADR-007.
- `src/test/java/com/dispatchflow/` holds the tests, `TestcontainersConfiguration` (PostgreSQL
  18.6 in a container) and `TestDispatchFlowApplication`, which `just core-run` starts.

## Rules

- `domain`, `events` and `shared` stay pure ([DL §3.9], [ADR-097]). Six rules, each an ArchUnit
  test in `src/test/java/com/dispatchflow/architecture/`:
  1. A module's domain imports only the JDK, the shared kernel, its own domain's packages, its own
     module's `events` package, JSpecify, libphonenumber and JTS ([DL §7.3] for `events`). An
     `events` package imports only the JDK, JSpecify, the kernel and itself. Spring Modulith
     annotations are allowed on a `package-info` only.
  2. It never reads the clock:
     - no `now()` or `dateNow()`;
     - no `Clock`, `InstantSource`, `Date`, `Calendar` or `TimeZone`, and no default zone;
     - no `System.currentTimeMillis()` or `nanoTime()`, and no `Thread.sleep` or `TimeUnit.sleep`.
  3. It never calls `UUID.randomUUID()`. Factories take the new ID, and a method that creates many
     children takes a `FreshIds`.
  4. It uses no randomness, I/O or logging. `java.net.IDN` is the one allowed `java.net` class.
     These count as I/O:
     - the default locale, named or implicit: a case change, `String.format` or `formatted`,
       `DateTimeFormatter.ofPattern`, `DateTimeFormatterBuilder.toFormatter`, and a `NumberFormat`,
       `DecimalFormat`, `MessageFormat` or `DateFormat` built, each with no `Locale`; and
       `DateTimeFormatter.ofLocalized*`;
     - system properties: `System.getProperty`, `Integer.getInteger`, `Long.getLong` and
       `Boolean.getBoolean`;
     - `java.util.prefs`, `java.lang.management`, `ResourceBundle` and `ServiceLoader`;
     - `getResource*` on a class, a class loader or a module, and `Class.forName`;
     - reflection: `java.lang.reflect` and `MethodHandles`;
     - starting a thread: `new Thread` or a subclass, `Thread.ofPlatform`, `ofVirtual` or
       `startVirtualThread`, `Executors`, `ForkJoinPool`, `ThreadPoolExecutor`, `Timer`,
       `Cleaner.create`, a `CompletableFuture`'s `*Async` methods, and `parallelStream` or
       `parallel()`.
  5. It never creates an `IllegalArgumentException` or an `IllegalStateException`, or a subclass of
     either. It throws the module's named error in a `RuleViolation`, or a `StoredStateInvalid`
     from `restore`.
  6. Only persistence adapters call an aggregate's `restore`, or the `readUnderLock` factory of a
     snapshot read under a lock (`ActiveAdmins`, `DateCapacity`, `LaneCapacity`).

  [DL §13] adds a seventh: only the optimizer adapter builds Planning's `ValidationReport` or
  `Evaluation`, by any factory, constructor or nested type.
- Shape rules (`DomainShapeRules`):
  - These constructors are private:
    - an aggregate root's;
    - a snapshot's that is read under a lock;
    - those of `ValidationReport`, `Evaluation` and `ValidatedSolution`.
  - Every interface that extends `DomainError` is sealed, and no error declares its own `code()`.
  - Error fields become a response's `params` ([ADR-043]), so they never hold text
    (`CharSequence` or `PlainText`), an `Object` or an array. Nor do they hold a value that
    implements `PersonalData`, a list of one, or a DispatchFlow type that holds one at any depth
    ([ADR-106]). Every personal value implements it; no test checks that, review does.
  - Every concrete `PersonalData` type declares its own `toString()`, which prints redacted. A
    record's implicit one doesn't count.
  - Events live in an `events` package. Their fields are typed IDs, `EventId`, `Version`, the shared
    values other than `PhoneNumber`, `GeoPoint`, `PlainText` and `Attempt`, the package's own enums,
    `Instant`, `int`, `long`, `boolean`, or lists of these (a list holds their boxes; a field is
    never boxed). No field holds a `PersonalData` value.
- Application rules (`ApplicationRules`, [ADR-119], [ADR-120]), each with a deliberately wrong
  fixture under `archfixture/zeta/`:
  1. The application layer and `shared/application/` import only the JDK, JSpecify, the kernel, their
     own module's `domain`, `events` and `application`, and other modules' `api` and `events`.
     Another module's `spi` interface is allowed only to the module that owns it and to the
     implementer that `SPI_IMPLEMENTERS` names, and the records beside it to any module that implements
     one of that owner's interfaces. They import no `adapter`, their own module's included, and no
     libphonenumber or JTS. From Spring they take only `@Transactional` and its enums ([TS §2.2]);
     Spring Modulith's annotations sit on a `package-info` only. `shared/application/` also falls
     under the kernel's purity rules, so it takes no `@Transactional`, and its `package-info` may
     carry `@NamedInterface` and no other Modulith annotation ([ADR-121]).
  2. Modules depend on each other along [DL §2.1]'s arrows and no others.
  3. No `api` or `spi` type uses a domain type.
  4. Each `api` interface `X` has exactly one implementation, `XService`, under its own module's
     `application.service`. Each `spi` interface has exactly one, under the `application.service` of
     the module that DL §2.1's dashed arrow names (`SPI_IMPLEMENTERS`): `XService`, or
     `FleetUsageService` and `CustomerUsageService` for the two `RecordUsage` ports
     (`SPI_IMPLEMENTATION_NAMES`, [ADR-120] decision 5). A sealed interface whose permitted
     subclasses are all records or enums is a fact's variants, and is skipped.
  5. Every application package is `@NullMarked`.
  6. No class in a `domain` or `events` package, or in the kernel root, imports
     `shared.application`, so a domain can't reach the `Clock` port.
  7. No cycle between a module's application packages, and the `service` and `port/out` roots import
     none of their own subpackages ([ADR-118]'s rules, carried to the application). A test checks
     that `MODULE_GRAPH` has no cycle.
  8. No class of `reporting`, its adapters included, uses a class of `liveops`, `planning` or
     `orders` outside their `events`, or any of Access's `api` but `Actor` and `ActorRole`
     (`REPORTING_NARROWED_APIS`; [ADR-120] P5: Reporting reads them through the views they publish;
     [ADR-121]).
- Package rules (`DomainPackageRules`, [ADR-118]): no cycle between a module's domain packages, a
  domain root that imports none of its own subpackages, and every kernel, domain and `events`
  package is `@NullMarked`. A module's domain never gets a subpackage to dodge a rule: nothing is
  made more visible in production code, and no package cycle is created, to move a file.
- `ErrorCodeUniquenessTest`, `EventNameUniquenessTest` and `ErrorRefusalTestsTest` check three
  things: error codes and event names are unique across modules, and every error has a refusal
  test. Each rule has a deliberately wrong fixture under `archfixture/` that it must flag, so a
  mistyped package pattern can't make it pass on nothing. The fixtures sit one subpackage per rule
  under `archfixture/alpha/domain/`, plus `epsilon/` for the package rules. A new module is covered
  without editing any of these tests.
- A value rebuilt from storage goes through `StoredStateInvalid.whileRestoring`, and a stored phone
  number through `PhoneNumber.ofStored`. So a damaged or since-refused value is an incident, never a
  user-facing refusal. No test checks this; review does.
- Web and persistence types live in `adapter` and are mapped to domain objects.
- Another module is reached only through its `api`, `spi` and `events` packages ([DL §2.3]).
  Spring Modulith's `verify()` fails the build on any other use ([ADR-040], Accepted).
- Each module owns its tables, in its own PostgreSQL schema. No SQL reads another module's schema
  ([DL §2.3], rule 3).
- Building a declared body ([ADR-119], decision 5): write the failing test first, replace the
  `throw`, then build the adapter. A milestone may change a declared signature or add a class, with
  every caller in the same pull request. Delete the Javadoc's "Built in M<n>" when the body is built.
- A use case follows [TS §6.2], [ADR-063] and [ADR-064]:
  - One class per use case. Its one `@Transactional` method changes one existing aggregate, after
    the outside calls.
  - The application layer takes only `@Transactional` from Spring. `@Retryable` and
    `@ConcurrencyLimit` sit on the adapter that makes the outside call, where a retry is safe
    ([ADR-040], [TS §6.2]).
  - `@ApplicationModuleListener` reactions are idempotent, because delivery is at least once.
  - Every changing use case writes its `AuditLog` record in the same transaction, with no personal
    data ([TS §6.2], NFR-94).
- Every handler is authorized by role from its operation's `x-capability`. A check that depends on
  the data, such as a driver's own route, lives in the use case ([ADR-068]; [TS §6], Authorization
  row).
- Flyway runs as `dispatchflow_owner` in a one-shot `migrate` step before the core starts. The owner
  credential lives only in `migrate`'s environment. The core connects as `app` and never owns a
  table ([ADR-041], [TS §6.3]). Not in the tree yet: today Spring Boot runs Flyway when the core
  starts.
- Optimistic locking is `UPDATE … WHERE id = ? AND version = ?` on the root table. Zero rows updated
  answers 412 `STALE_VERSION`, through `ETag` and `If-Match`, on a call that sent a version. A call
  that sent none is retried at once, up to three times, then answered 503 with `Retry-After`
  ([ADR-121]). Child tables carry no version ([ADR-057], [TS §6.3]).
- The `e2e` profile's test-only endpoints exist only in that profile, and the profile refuses to
  boot when the SMS credential is present, with a test for each (NFR-206, [TS §17.1]).
- Until M5's sign-in, the stand-in session exists only when `dev` or `test` is the only active
  profile, and the core refuses to boot with the stand-in and any other profile active. Unless `dev`
  is the only active profile, the core refuses to boot with an access-token lifetime other than 15
  minutes. Each refusal has a test. M5 deletes the stand-in, and M6 the dev allowance ([ADR-121]).
- No Lombok: records and plain classes ([TS §6]).
- The schema changes only through a new migration, `V<n>__<what_it_does>.sql`. Never edit a
  migration that has been applied anywhere; add the next one.
- Configuration comes from environment variables. A new key goes into `.env.example` with no
  value.
- Persistence is jOOQ through hand-written mappers ([ADR-041], Accepted; [TS §6.3]). The pom keeps
  Spring Data JPA until M0 replaces it. Never concatenate input into a query: bind parameters.
  jOOQ's plain-SQL API (methods annotated `@PlainSQL`, such as `DSL.field(String)` and
  `DSL.condition(String)`) never takes input.
- The image runs as the non-root user `dispatchflow` ([ADR-019]). Keep it that way.

## Tests

- `*Test` classes are unit tests, run by Surefire. `*IT` classes are integration tests, run by
  Failsafe against a real PostgreSQL: annotate them with `@IntegrationTest`, so they all share one
  Spring context and one container. They need Docker: [ADR-042] keeps Testcontainers for tests.
- Once a module exists, test it in layers ([DL §14], [TS §17.1]):
  - Domain tests use no Spring and no mocks. Aggregates take values, `now` and new IDs as
    parameters. Every invariant in [DL §4] has a test that breaks it and asserts the module's named
    error. Every transition has one test that takes it, plus one per other state that must refuse
    it.
  - A refusal test asserts through `DomainAssertions.assertRefused` (or `assertAllRefused` for a
    `RuleViolations`). It is a `@Test` or `@ParameterizedTest` whose name ends in
    `_Then<ErrorName>`, and `ErrorRefusalTestsTest` fails when any module's error has no such test.
    It may sit anywhere under its error's package. `ErrorCodeUniquenessTest` fails on a code two modules
    share, except the allow-listed ones that mean the same thing.
  - Application tests run each use case against in-memory fakes of its ports.
  - Module and event tests (`@ApplicationModuleTest` boots one module alone; `Scenario` tests an
    event reaction) boot Spring, so they are not Surefire unit tests. The suffix and the container
    wiring are decided with the first module (M0). Publish each event twice: after the second
    delivery the state equals the state after the first. Wait through `Scenario`, never `sleep`.
  - Permission tests are generated from the contract's `x-capability` mapping and the permission
    matrix ([NFR-176]).
  - A test sits in the package of the type it tests, and one that covers several packages' values
    sits in the module's root. A helper that several test packages use is public.
  - Build test data with one builder per aggregate, such as `aPlan()`. No test reads a shared
    fixture file that other tests also change.
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
- Coming with jOOQ ([ADR-041], Accepted): code generation runs the migrations in a Testcontainers
  PostgreSQL, so Docker must run for `./mvnw verify`.

[ADR-019]: ../../../docs/ADRs/ADR-019-container-images-run-as-non-root.md
[ADR-040]: ../../../docs/ADRs/ADR-040-core-is-a-spring-modulith-monolith-with-one-module-per-context.md
[ADR-041]: ../../../docs/ADRs/ADR-041-one-postgresql-schema-per-module-read-and-written-through-jooq.md
[ADR-042]: ../../../docs/ADRs/ADR-042-compose-dev-for-local-runs-testcontainers-for-tests.md
[ADR-043]: ../../../docs/ADRs/ADR-043-generated-code-from-both-contracts-and-problem-details-errors.md
[ADR-057]: ../../../docs/ADRs/ADR-057-concurrent-changes-to-a-plan-use-optimistic-locking.md
[ADR-068]: ../../../docs/ADRs/ADR-068-every-web-handler-is-authorized-by-role-from-the-contract.md
[ADR-097]: ../../../docs/ADRs/ADR-097-the-shared-kernel-holds-the-domains-building-blocks.md
[ADR-106]: ../../../docs/ADRs/ADR-106-free-text-is-normalized-and-capped-by-one-kernel-rule.md
[ADR-111]: ../../../docs/ADRs/ADR-111-attempt-is-a-shared-kernel-value-and-names-and-addresses-drop-invisible-fillers.md
[ADR-118]: ../../../docs/ADRs/ADR-118-a-domain-package-above-30-files-splits-by-aggregate.md
[ADR-119]: ../../../docs/ADRs/ADR-119-the-application-layer-is-declared-ahead-as-signatures-whose-bodies-throw.md
[ADR-120]: ../../../docs/ADRs/ADR-120-the-application-layer-takes-an-actor-and-a-call-context-and-reads-through-query-ports.md
[ADR-121]: ../../../docs/ADRs/ADR-121-the-application-skeletons-review-settles-its-seams-conflicts-retention-and-scoring-inputs.md
[NFR-176]: ../../../docs/requirements/requirements.md#nfr-176
[DL §1]: ../../../docs/architecture/domain_layer/domain_layer_design.md#1-subdomains-and-bounded-contexts
[DL §2.1]: ../../../docs/architecture/domain_layer/domain_layer_design.md#21-module-graph
[DL §2.3]: ../../../docs/architecture/domain_layer/domain_layer_design.md#23-rules-between-contexts
[DL §3.9]: ../../../docs/architecture/domain_layer/domain_layer_design.md#39-purity
[DL §4]: ../../../docs/architecture/domain_layer/domain_layer_design.md#4-aggregates-context-by-context
[DL §7.3]: ../../../docs/architecture/domain_layer/domain_layer_design.md#73-shape-and-delivery
[DL §13]: ../../../docs/architecture/domain_layer/domain_layer_design.md#13-inside-each-module-hexagon
[DL §14]: ../../../docs/architecture/domain_layer/domain_layer_design.md#14-testing-the-domain
[ADR-063]: ../../../docs/ADRs/ADR-063-a-transaction-changes-one-aggregate-and-every-rule-has-one-owner.md
[ADR-064]: ../../../docs/ADRs/ADR-064-events-only-where-they-earn-their-place-through-the-modulith-registry.md
[TS §17.1]: ../../../docs/architecture/tech_stack.md#171-by-layer
[TS §2.2]: ../../../docs/architecture/tech_stack.md#22-enforcement
[TS §4]: ../../../docs/architecture/tech_stack.md#4-repository-layout
[TS §6]: ../../../docs/architecture/tech_stack.md#6-core-service-java
[TS §6.2]: ../../../docs/architecture/tech_stack.md#62-application-layer
[TS §6.3]: ../../../docs/architecture/tech_stack.md#63-persistence
[roadmap §2.2]: ../../../docs/roadmap.md#22-alpha-track

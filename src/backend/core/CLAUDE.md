# Core

The REST API that both clients call (`contracts/client-api/v1`) and DispatchFlow's system of
record. It is the only client of the optimizer (`contracts/optimization-api/v1`). Java 25, Spring
Boot 4.1, Spring Data JPA, Flyway, PostgreSQL, Maven through the wrapper.

## Layout

- `src/main/java/com/dispatchflow/` holds `DispatchFlowApplication`, `web/` (above the modules, used
  by none, using only their `api` and `events`: `ApplicationRules` 2, 11 and 13, [ADR-140]) and:
  - `shared/`, the shared kernel ([ADR-097], [ADR-106], [ADR-111]): add no type holding one
    context's rule. `shared/application/`, the ports every module uses ([ADR-120]), keeps the
    kernel's purity rules (no Spring) but has `@NamedInterface` on its `package-info` ([ADR-121]).
  - `shared/adapter/{in/web,out/{persistence,clock,uuid,optimizer,sse}}/`, shared adapters beside
    the kernel, outside its purity rules, imported by no application class ([ADR-126], [ADR-130]).
  - A module's `adapter/out/<name>` is the outside system, else `persistence` or the technology.
- Package size ([ADR-125], which amends ADR-118): its 20-file threshold counts Java files,
  not `package-info.java`. A domain splits by aggregate over a root, `domain/`, that holds its error
  and shared values, then by concept; an application package splits by aggregate, then by a concept
  beneath it (`service/plan/edit`). Files the language binds (package-private access, a sealed
  hierarchy) or a dependency cycle holds stay together: never widen production visibility or add a
  cycle to split. The kernel's domain types and every `api`, `spi` and `events` package stay flat.
  Each new package carries `@NullMarked`. `DomainPackageRules` and `ApplicationRules` check the
  cycles, the root imports, `@NullMarked` and that the flat packages have no subpackage.
- `src/test/java/com/dispatchflow/` holds the tests, `TestDispatchFlowApplication` (`just core-run`)
  and `TestcontainersConfiguration`: PostgreSQL 18.6, with `db/fixture`'s `V0_*` test migrations.

## Rules

- `domain`, `events` and `shared` stay pure ([DL §3.9], [ADR-097]). `DomainPurityRules`, in
  `src/test/java/com/dispatchflow/architecture/`, holds the full lists and fails the build. What
  goes against Java's defaults:
  - A module's domain imports only the JDK, the kernel, its own domain and `events`, JSpecify,
    libphonenumber and JTS; the kernel only the JDK, JSpecify and libphonenumber; an `events`
    package only the JDK, JSpecify, the kernel and itself. A domain and the kernel also carry
    [ADR-126]'s persistence annotations, nothing else of JPA. Modulith's go on `package-info` only.
  - They never read the clock (`now()`, `Clock`, `Date`, `System.currentTimeMillis()`,
    `Thread.sleep` and the like). They never call `UUID.randomUUID()`: factories take the new ID,
    and a method that creates many children takes a `FreshIds`.
  - They use no randomness, I/O, logging, reflection, threads, system properties or resources
    (`java.net.IDN` is the one allowed `java.net` class), and no default locale: a case change,
    `String.format`, `DateTimeFormatter.ofPattern`, `toFormatter` and the `*Format` classes take a
    `Locale`, and `formatted` and `DateTimeFormatter.ofLocalized*` are never used.
  - They never create an `IllegalArgumentException` or an `IllegalStateException`, or a subclass of
    either. They throw a `RuleViolation` with the module's named error, or `StoredStateInvalid`.
  - Only persistence adapters call `readUnderLock`, and only Planning's optimizer adapter makes
    `ValidationReport` or `Evaluation` ([DL §13]).
- `DomainShapeRules` checks the shape. What goes against Java's defaults:
  - The constructors of an aggregate root (bar Hibernate's protected no-argument one), a locked
    snapshot, `ValidationReport`, `Evaluation` and `ValidatedSolution` are private; a root's
    constructors are called only from within it, and no class extends a root ([ADR-126]). Every
    interface that extends `DomainError` is sealed, and no error declares its own `code()`.
  - Error fields become a response's `params` ([ADR-043]), so they never hold text, an `Object`, an
    array or anything personal ([ADR-106]).
  - Every personal value implements `PersonalData`, which review checks, not a test. Each concrete
    `PersonalData` type declares its own redacting `toString()`, never a record's implicit one.
  - An event's fields are typed IDs, `EventId`, `Version`, the shared values but `PhoneNumber`,
    `GeoPoint`, `PlainText` and `Attempt`, the package's own enums, `Instant`, `int`, `long`,
    `boolean`, or lists of these, never boxed, and none holds a `PersonalData` value.
- `ApplicationRules` ([ADR-119], [ADR-120]) numbers its rules in its methods' Javadoc, with a
  fixture under `archfixture/zeta/` for each. What goes against the defaults:
  - The application layer imports only the JDK, JSpecify, the kernel, its own module's `domain`,
    `events` and `application`, and other modules' `api` and `events` (another module's `spi` only
    as the rule allows). No `adapter`, libphonenumber or JTS, and from Spring only `@Transactional`.
  - Modules depend only along [DL §2.1]'s arrows. No `api` or `spi` type uses a domain type.
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
  it must flag. An adapter package missing from `DECLARED_ADAPTERS` fails `ApplicationTest`.
- A value rebuilt from storage goes through `StoredStateInvalid.whileRestoring`, and a stored phone
  number through `PhoneNumber.ofStored`. So a damaged or since-refused value is an incident, never a
  user-facing refusal. No test checks this; review does.
- A controller in `adapter/in/web` implements a tag's generated `<Tag>Api`, maps web types to domain
  objects and passes the `Actor` from `CurrentActor`, or the `CallContext` from `CurrentCall` before
  sign-in, to the use case. Only `web` and the `in/web` adapters use the two (`ApplicationRules` 14,
  [ADR-140]), and neither exists yet (#170). Every use case that changes an aggregate calls its
  port's `save` or `add`, which publishes its events; a test fake's calls `SaveEvents.publish`.
- Another module is reached only through its `api`, `spi` and `events` packages ([DL §2.3]).
  Review checks an adapter's use of another module until Modulith's `verify()` runs ([ADR-040]);
  `ApplicationRules` rules 9 to 12 keep adapters apart and shared ones off modules ([ADR-130]). Each
  module owns its tables, in its own PostgreSQL schema: no SQL reads another module's schema.
- An unbuilt application body throws `UnsupportedOperationException("To Be Implemented.")`;
  `grep -rF "To Be Implemented."` lists the work left ([ADR-132]). To build one ([ADR-119] decision
  5): write the failing test, replace the `throw`, build the adapter, add the use case to its
  module's `<Module>Configuration` ([ADR-140]; the first comes with #162), delete the tag. Building
  may change a declared signature or add a class, with every caller in the same pull request.
- A use case follows [TS §6.2], [ADR-063], [ADR-064] and [ADR-129]:
  - A use case is one class, or two when a `<UseCase>Change` holds its transaction. A changing one
    changes one aggregate after its outside calls, and audits in that transaction (NFR-94).
  - `TransactionRules` checks where transactions start. Review checks that a service changing
    nothing is `readOnly`; a `Change` reads in `readOnly` methods, and a refusal audits after it.
  - `@Retryable` and `@ConcurrencyLimit` sit on the adapter that makes the outside call, where a
    retry is safe. A listener is `@Async`, so each retry gets a fresh transaction; it is idempotent.
- Every handler is authorized by role from its `x-capability`, in `web.security`'s interceptor,
  which answers every 401 and 403 once the handler is chosen, before the body is validated; the
  security filter answers none. Neither exists yet (#170, #171). A check on the data, such as a
  driver's own route, lives in the use case ([ADR-068], [ADR-140]).
- The schema changes only through a new migration, `src/main/resources/db/migration/V<n>__<x>.sql`:
  never edit an applied one, and `ddl-auto` stays `validate`. Flyway runs as `dispatchflow_owner` in
  a one-shot `migrate` step, which alone holds that credential; the core connects as `app`, which
  owns no table ([ADR-041], [TS §6.3]). Until #160, Spring Boot runs Flyway as the core's own user.
- Optimistic locking is `AggregateRoot`'s `@Version` ([ADR-057]): every `save` but a session's
  contact-only one goes through `VersionedSave`, which moves it by 1, and a use case checks a sent
  version with `StaleVersion.requireCurrent`. `STALE_VERSION` answers 412 to `If-Match` or
  `If-None-Match` and 409 to a body version, neither retried; a call with none, and any failed lock
  wait, is re-run in a new transaction, up to 3 times, then 503 ([ADR-121], [ADR-140]).
- The `e2e` profile's test-only endpoints, in `web.e2e`, exist only in that profile, and the profile
  refuses to boot when the SMS credential is present, with a test for each (NFR-206).
- Until sign-in is built, a stand-in session (`web.security`, #170, unbuilt) exists only when `dev`
  or `test` is the only active profile; the core refuses to boot with it beside any other profile,
  and, unless `dev` is the only one, with an access-token lifetime other than 15 minutes. Each
  refusal has a test. Building sign-in deletes it, and refresh the dev allowance ([ADR-121]).
- No Lombok: records and plain classes. Configuration comes from environment variables.
- Persistence is JPA ([ADR-126], [ADR-127], [TS §6.3]). A port's `@Component` works over a
  package-private Spring Data `Repository<Root, UUID>` with `save` and the port's own queries; never
  `@Repository`, whose exception translation turns a port's own `IllegalArgumentException` into a
  Spring one. Native SQL binds every value; SQL and bind logging stay off. `PersistenceRules` checks
  the mapping, a `*PersistenceIT` the stored state; a load check refuses a list gap first. A call
  that flushes (an `add`, a native statement, or a query that reads a changed table) writes every
  changed aggregate: save a changed aggregate before one, or `VersionedSave` fails its one-step
  check. Renaming a stored error or its field needs a migration.

## Tests

- `*Test` classes are unit tests, run by Surefire. `*IT` classes are integration tests, run by
  Failsafe against a real PostgreSQL: annotate them with `@IntegrationTest`, so they all share one
  Spring context and one container. They need Docker.
- Test in layers ([DL §14], [TS §17.1]):
  - Domain tests use no Spring and no mocks. Aggregates take values, `now` and new IDs as
    parameters. Every invariant in [DL §4] has a test that breaks it and asserts the module's named
    error. Each transition has a test that takes it, plus one per other state that must refuse it.
  - A refusal test asserts through `DomainAssertions.assertRefused` (or `assertAllRefused` for a
    `RuleViolations`). It is a `@Test` or `@ParameterizedTest` whose name ends in
    `_Then<ErrorName>`, and may sit anywhere under its error's package.
  - Application tests run each use case against in-memory fakes of its ports.
  - Module and event tests (`@ApplicationModuleTest` boots one module alone; `Scenario` tests an
    event reaction) boot Spring, so they are not Surefire unit tests. Their suffix and container
    wiring are set with the first module. Publish each event twice: after the second delivery
    the state equals the state after the first. Wait through `Scenario`, never `sleep`.
  - Permission tests come from the contract's `x-capability` and the permission matrix ([NFR-176]).
  - A test sits in the package of the type it tests, and one that covers several packages' values
    sits in the module's root. A helper that several test packages use is public.
  - Build test data with one builder per aggregate, such as `aPlan()`, through its commands and
    `AsSaved`. No test reads a shared fixture file that other tests also change.
  - The failure paths the design names are listed in `.claude/agents/df-tester.md`.

## Commands

Run `just` recipes from the repository root, and `mvnw` from this folder (`.\mvnw.cmd` on Windows).

| Command | What it does |
|---|---|
| `just core-check` | `mvnw verify`: Spotless check, unit tests, integration tests |
| `just core-format` | `mvnw spotless:apply` |
| `just core-run` | Starts the core on port 8080 against a throwaway PostgreSQL container |
| `just core-schema-draft` | Writes the DDL Hibernate would generate to `target/schema-draft.sql`, a draft to copy into a migration; touches no database |
| `./mvnw -Dtest=FooTest test` | One unit test class |
| `./mvnw -Dit.test=FooIT verify` | One integration test class, after the unit tests and the format check |

- Spotless checks in `validate`, so any build fails on an unformatted file. Run `just core-format`.
- Keep `mvnw` executable in git (`git add --chmod=+x mvnw`), or CI fails with "permission denied".

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
[ADR-126]: ../../../docs/ADRs/ADR-126-jpa-replaces-jooq-and-the-domain-carries-its-mapping.md
[ADR-127]: ../../../docs/ADRs/ADR-127-how-the-aggregates-are-mapped-and-where-their-stored-state-is-tested.md
[ADR-129]: ../../../docs/ADRs/ADR-129-transactions-start-only-in-the-service-layer.md
[ADR-130]: ../../../docs/ADRs/ADR-130-every-folder-is-declared-ahead-empty-and-documented.md
[ADR-132]: ../../../docs/ADRs/ADR-132-unbuilt-bodies-say-to-be-implemented.md
[ADR-140]: ../../../docs/ADRs/ADR-140-the-app-wide-web-package-sits-above-the-modules.md
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

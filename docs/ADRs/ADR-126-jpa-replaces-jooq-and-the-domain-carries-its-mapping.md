# ADR-126: JPA replaces jOOQ in the core, and the domain classes carry their own mapping

- **Status:** Accepted; amended by ADR-127
- **Date:** 2026-11-23

## Context

[ADR-041] chose jOOQ for the core's persistence on 2026-11-13, with hand-written mappers so that the
domain carries no annotations, and turned down JPA because a pure domain needs a second set of entity
classes. [DL §3.9][DL-39] and [ADR-040] hold the domain to six purity rules that forbid Jakarta
types, and [ADR-097] has persistence rebuild an aggregate through its `restore` factory.

On 2026-11-22 the team chose JPA, Hibernate through Spring Data JPA, over jOOQ for the core's
persistence. `README.md` lists Spring Data JPA in the core's stack and calls it "the team's strongest
stack". On 2026-11-23 the owner, Naim Elijah, decided how it is used, in 16 points
([issue #101][ISSUE-101]). They are final.

What the repository held on 2026-11-23:

- **The code has no jOOQ.** `src/backend/core/pom.xml` declares `spring-boot-starter-data-jpa` and its
  test starter, and no jOOQ. Spring Boot 4.1.2 resolves them to Hibernate 7.4.13.Final, Jakarta
  Persistence 3.2.0 and Spring Data JPA 4.1.2.
- **`application.properties` already sets** `spring.jpa.hibernate.ddl-auto=validate` and
  `spring.jpa.open-in-view=false`.
- **No migration and no entity exist.** `src/main/resources/db/migration/` holds a `.gitkeep`. No
  persistence adapter has merged, so nothing has to be undone.
- **The version check is decided, not built.** [ADR-057] has every aggregate root carry a version and
  every save update `WHERE id = ? AND version = ?`. Under jOOQ that is hand-written for each
  repository ([ADR-041], Hard).
- **The domain has 20 aggregate roots:** `UserAccount`, `Session`, `CompanyProfile`, `Driver`,
  `FleetRegister`, `DayRoster`, `Customer`, `Order`, `ImportBatch`, `Plan`, `SolveJob`,
  `ReoptimizationProposal`, `PlanningSettings`, `Delivery`, `RouteRun`, `Alert`, `StopMessages`,
  `RatingRequest`, `MessagingSettings` and `Baseline`. Each builds a snapshot record for `restore`.
  `Version.NEW` appears 114 times in 77 files of the core on `dev`, most of them in tests and
  Javadoc.

The forces:

- Hibernate writes the version check itself, so [ADR-057]'s mechanism needs no code of ours.
- A JPA setup with no second set of entity classes means the domain classes carry the mapping, which
  [DL §3.9][DL-39] rule 1 forbids. An accepted ADR comes before the code it governs, so no persistence
  adapter can merge until this one is accepted. It blocks the walking skeleton's first repository
  (M0, [roadmap][RM]).
- The domain stays small and testable without Spring or a database ([DL §3.9][DL-39]). A rule that
  allows annotations must not let a framework call into the domain.
- A plan can hold 200 stops, and a change to it must not rewrite every child row ([ADR-041]).
- PostgreSQL features carry real rules: partial unique indexes ([BR-34], [BR-38]), `FOR UPDATE SKIP
  LOCKED`, `ON CONFLICT` and advisory locks.

## Decision

We replace jOOQ with JPA in the core's persistence adapters, and the domain classes carry their own
mapping. This amends [ADR-001], [ADR-040], [ADR-041], [ADR-097], [ADR-101] and [ADR-121] (the list is at
the end of this section), and extends [ADR-125].

The numbers in parentheses are the owner's 16 decisions of 2026-11-23, in the order of
[issue #101][ISSUE-101].

**Mapping.**

- **The domain classes carry the mapping (1).** There is no second set of entity classes and no
  hand-written mapper, which were [ADR-041]'s reasons to reject JPA.
- **An entity, or a value (7).** A child with its own ID is an `@Entity`. A value without one stays a
  record, stored in its owner's columns: one column through a converter, or several as an
  `@Embeddable`.
  - In `Plan`, `Route` (`RouteId`) and `PlannedStop` (`StopId`) become entities, and the internal
    `Layout` record dissolves into `Plan`'s own fields (`routes`, `dropped`, `pins`). The same
    applies to `FleetRegister`'s depots and vehicles and to `DayRoster`'s entries.
  - A list of values is an `@ElementCollection` with `@OrderColumn`, and never holds a set that
    grows with the day's work.
  - The records of [DL §3.2][DL-32] (positions, audit records, SMS replies, opt-outs) are `@Immutable`
    entities: inserted, never updated, with no version.
- **States and optional values are private columns (9).** The public types stay as they are, so
  nothing outside the class sees the mapping.
  - A sealed state is a private `@Enumerated(EnumType.STRING)` kind plus nullable private fields for
    each state's data. The public getter builds the sealed record, and a transition sets the fields.
    The migration adds a `CHECK` constraint per state, and the status column carries the partial
    unique indexes [BR-34] and [BR-38] need.
  - An `Optional` value is a nullable private field whose getter returns `Optional`.
  - Every enum attribute is `STRING`, because the default stores the position, which a reordered
    enum silently corrupts.
- **A root's key is a private `UUID` (10).** `@Id private UUID id;` with a getter that returns the
  typed ID (`PlanId.of(id)`). No `@GeneratedValue`: IDs come from `IdGenerator` ([DL §3.9][DL-39]
  rule 3), and Spring Data interfaces are typed `Repository<Plan, UUID>`. A root with a typed ID
  keys on its value. A singleton keys on a constant `UUID`. A root found by its date keys on a
  `UUID` beside a date its table keeps unique, so every root has a `UUID` key for the shared
  version helper to name in a `StaleVersion`.
- **Classes and collections.** A mapped class is not `final`, its persistent fields are not `final`,
  and it has a protected no-argument constructor for Hibernate. Annotations go on the fields, so no
  setter is added ([DL §3.10][DL-310]).
  - Ordered children use `@OrderColumn`, so no two lists Hibernate fetches are bags
    (`MultipleBagFetchException`).
  - A position constraint, `UNIQUE(owner, index)` on an `@OrderColumn`, is `DEFERRABLE INITIALLY
    DEFERRED`. Hibernate rewrites the indexes row by row, so a plain unique constraint fails on an
    insert and on a removal.
  - Collections inside an aggregate use `cascade = ALL, orphanRemoval = true`.
  - A command never assigns a new collection to a mapped field; it changes the one Hibernate loaded.
- **Relationships stay inside one aggregate.** Between aggregates and between modules, only IDs
  ([DL §3.1][DL-31]).
- **Kernel values in columns.**
  - Each single-value kernel type gets one auto-applied converter: the typed IDs that point to
    another aggregate, `DeliveryDate`, `IsraelDateTime`, `Priority`, `Weight`, `Volume`, `Attempt`,
    `PhoneNumber` (through `PhoneNumber.ofStored`) and `EventId`.
  - Multi-column kernel values (`TimeWindow`, `GeoPoint`, `DaySeconds`) are `@Embeddable` records,
    built through an instantiator that an adapter registers with
    `@EmbeddableInstantiatorRegistration`, so the kernel never refers to an adapter class.
  - Both build through `StoredStateInvalid.whileRestoring`, so a stored value that a since-tightened
    rule refuses is an incident, not a refusal.
  - `TimeWindow`'s two components map to the columns `starts_at` and `ends_at`, because PostgreSQL
    reserves `end`. An entity that embeds two windows overrides the names of both.
- **Every entity names its module's schema,** such as `@Table(schema = "planning")` ([ADR-041]). The
  schema is the module's package name, as [ADR-041] lists them. An architecture test checks every
  table an entity maps: its `@Table`, each `@CollectionTable`, each `@JoinTable`, and the default
  join table of a one-to-many with neither `mappedBy` nor a `@JoinColumn`. A second test checks
  that a module's adapter uses no other module's `@Entity`. Together they are how [ADR-040]'s rule
  that each persistence adapter uses only its own module's schema is checked now that no generated
  classes exist. Neither reads the text of a JPQL or native query, so a query that names another
  module's schema is for review to catch.
- **The plan's travel-time matrix** (`bytea`, [TS §6.5][TS-65]) is mapped outside the `Plan` entity,
  in its own table that only the solve path reads, so editing a plan never loads it.

**Loading, and changing in place.**

- **A `@PostLoad` check on each root replaces `restore` (8).** It runs the checks `restore` runs
  today and throws `StoredStateInvalid`. Then `restore`, and every snapshot type nothing else still
  uses, go. An application view that reuses a snapshot's nested record (`HeldReportsView` and
  `StopHistoryView` use `DeliverySnapshot.HeldReport`) moves that record to its own type first.
- **An aggregate loads whole ([DL §3.1][DL-31]).** With `open-in-view=false`, each repository's `get`
  loads every collection inside the transaction, through an entity graph or fetch joins. Fetching
  several collections in one query multiplies the rows, so those are split into separate queries.
- **A command changes the loaded entities in place and checks the result at its end (11).** It moves
  the stop object between two routes' lists, for example, then runs the same invariants as
  `@PostLoad` on what it left. A broken invariant throws `AssertionError`, which rolls the
  transaction back, because Spring rolls back on any `Error`, so a broken state is never saved.
  `Plan`'s `Next` and its swap in `commit` go.

**Versioning.**

- **`@Version` sits on `AggregateRoot`, a `@MappedSuperclass`,** so every root has it and no other
  class declares one. Not on child entities, on the `@Immutable` records, on the idempotency records
  behind `ProcessedEvents`, or on Spring Modulith's publication table. The field is a nullable
  `Long`, `null` until the first save, because Jakarta Persistence applies no converter to a version
  attribute, so it cannot hold the kernel's `Version`. The field is named `storedVersion` and mapped to
  the `version` column, because Spring Data reads the version attribute by name to tell a new
  aggregate, and Spring's bean wrapper would call `version()`, which throws before the first save,
  for a field named `version`.
- **A new aggregate's first save stores version 0 (2).** Hibernate seeds a `null` version with 0 on
  insert (`LongJavaType.seed` returns 0 in 7.4.13), and Spring Data's `save` treats a `null` version
  as new and calls `persist` with no `SELECT` first. `Version.NEW` goes: a creation has no version
  until its first save, and once its lane maps it, its factory calls the protected no-argument
  constructor. `EventId` accepts version 0, and its "at least
  1" check and `FIRST_SAVED_VERSION` go, because `Version` already refuses negatives. `raise` stamps
  each event with the version its change is saved at: 0 for a never-saved aggregate, and the loaded
  version plus 1 otherwise.
- **Every save moves the root's version by exactly 1 (3),** through one shared helper that every
  adapter's `save` calls:
  1. the Spring Data interface's `save` publishes the events;
  2. `EntityManager.flush()` writes the changes;
  3. if the version did not move, because only child rows changed, `em.lock(root,
     LockModeType.PESSIMISTIC_FORCE_INCREMENT)` raises it at once. Hibernate 7.4.13 writes it inside
     `lock`, whereas `OPTIMISTIC_FORCE_INCREMENT` waits for the commit;
  4. a conflict becomes `StaleVersion` there, inside `save`, with the entity's name as its kind,
     the root's key as its ID, and the exception kept as its cause. A conflict is Jakarta
     Persistence's `OptimisticLockException`, which Hibernate throws on a stale `flush`, or
     Spring's `OptimisticLockingFailureException`, which its repository proxy throws. It is also a
     lock the save can't get: `PessimisticLockException`, `LockTimeoutException` or Spring's
     `PessimisticLockingFailureException`, because two writers of one aggregate can deadlock or wait
     on each other, and either way another transaction is changing it. And on a first save only, a
     unique-constraint violation is one: another transaction stored the same key, or the same unique
     value, first. A retry then runs the use case against what is stored, and its own rules refuse
     it if they should. Any other constraint violation passes through, as a bug.

  The lock is the row lock any update of the root takes, held until commit. [ADR-057] stands:
  Hibernate issues its `UPDATE … WHERE id = ? AND version = ?`, and only its cost "written by hand
  with jOOQ" goes.

  **An aggregate is saved once per transaction, at the end of its change, with no flush between two
  of its changes.** Hibernate ignores the forced increment of a root its transaction already wrote,
  and moves the version at every flush of a change. So a second save, or a query that flushes
  between two changes, would store a version the events don't carry, and the next change would
  raise an `EventId` a listener has already processed. The helper throws `AssertionError` when a
  save leaves the version where it was, or stores a version that one of the pending events doesn't
  carry.
- **The use case compares `If-Match` (4),** through `StaleVersion.requireCurrent(kind, id, loaded,
  expected)`, which every use case calls straight after loading. It compares the `If-Match` version,
  or the version in the body when accepting a proposal ([ADR-121] P31), with the loaded one. An
  overload takes two `Optional<Version>`, for an aggregate that may not be stored yet: it refuses
  when one is empty and the other is not, and when both are there and differ. `@Version` alone
  guards only the window between this transaction's load and its commit.
- **Bookkeeping that [DL §4.1][DL-41] allows to leave the version alone** (a session's contact times)
  is mapped `@OptimisticLock(excluded = true)` and saved through its own port method, which forces
  nothing.
- **A retry re-runs the whole use case in a new transaction.** After a conflict the transaction is
  rollback-only and the persistence context unusable. So [ADR-121]'s retries (for a call that sent
  no version) and [DL §11][DL-11]'s (listeners, the midnight job) wrap the `@Transactional` call from
  outside it.
- **The version before a first save.** A never-saved aggregate has no version, and `version()` called
  before the first save throws `AssertionError`. `savedVersion()` is public and returns an
  `Optional<Version>`, empty before the first save, for a use case that holds an aggregate that may
  not be stored yet.
  - The views of an aggregate that may not be stored yet (the singletons and a date's roster:
    `RegisterView`, `RosterView`, `MessagingSettingsView`, `PlanningSettingsView` and the queries
    that return them) carry an empty `Optional<Version>` before the first save. The use cases that
    may create such an aggregate (`ChangeCompanyProfile`, `AddDepot`, `AddVehicle`, the five roster
    changes, `ChangeMessagingSettings` and `ChangePlanningSettings`) take `Optional<Version>
    expected`, empty for `If-None-Match: *`. `CompanyView` exists only once the profile does, so it
    keeps a plain `Version`.
  - A creation's response reads the version after `save`.
- **Until each lane maps its aggregate,** the snapshot records' `version` is nullable (null until
  the first save), and `AggregateRoot` keeps a transitional constructor that takes a nullable stored
  `Version`. Both go with the last `restore`.

**Events (5).** `AggregateRoot` carries `@DomainEvents` and `@AfterDomainEventPublication`, so Spring
Data publishes the pending events when a repository method whose name starts with `save` or `delete`
runs, then clears them, and Spring Modulith stores them in the same transaction. We annotate our own
`AggregateRoot` instead of extending Spring's `AbstractAggregateRoot`, which would replace the way
`raise` builds and checks each `EventId`. `pullEvents()` goes; `pendingEvents()` stays for tests that
check a refused call recorded nothing.

- **Every use case that changes an aggregate calls its port's `save` or `add`.** Hibernate writes a
  change to a loaded aggregate even without the call, but then no event goes out. Each use case's
  test checks the events its change raised.
- Use cases no longer publish events themselves.
- Events go out inside `save`, before the flush. When the flush or the forced increment fails, the
  transaction rolls back, and Modulith's stored publications with it.
- The in-memory fakes in the application tests publish the pending events on `save` and clear them,
  as Spring Data does, through a test helper, `SaveEvents.publish`, because the two methods Spring
  Data calls are package-private.

**Adapters (6).**

- Each persistence adapter implements its port with a package-private Spring Data interface that
  extends `Repository<Root, UUID>` and declares `save` plus only the lookups the port needs, and uses
  `EntityManager` for `flush` and `lock`. It never extends `JpaRepository`, `CrudRepository`,
  `ListCrudRepository` or `JpaSpecificationExecutor`, so there is no `saveAll`, `deleteAll`, batch
  delete or bulk update that passes by `@Version`, `@PostLoad` and the events.
- `add` and `save` both call the interface's `save`, and `save` then runs the shared version helper.
- Query ports ([ADR-120]) read into their `View` records with JPQL constructor expressions or native
  queries, never by loading aggregates ([DL §3.6][DL-36]).
- PostgreSQL-specific SQL (the solve queue's `FOR UPDATE SKIP LOCKED`, `ON CONFLICT`, advisory
  locks) runs as native queries, each covered by an adapter test against the Testcontainers
  PostgreSQL.
- The snapshots read under a lock (`ActiveAdmins`, `DateCapacity`, `LaneCapacity`, [DL §5][DL-5]) use
  `LockModeType.PESSIMISTIC_WRITE` or a native advisory lock, each with an adapter test.

**The shared adapter.** The shared version helper `VersionedSave`, the kernel's converters (the 14
typed IDs in its `id` subpackage, plus `Attempt`, `Priority`, `DeliveryDate`, `IsraelDateTime`,
`Weight`, `Volume`, `PhoneNumber` through `ofStored`, and `EventId`) and the instantiators of the
embeddables `TimeWindow`, `GeoPoint` and `DaySeconds`, which carry `@Embeddable`, live in
`com.dispatchflow.shared.adapter.out.persistence`. A module's own converters and instantiators live
in its `adapter/out/persistence`.

That package is an adapter, not part of the kernel. The purity rules leave
`shared.adapter.out.persistence..` out, and no pure class may depend on any class of `shared.adapter`.
[ADR-125] decision 4's guard, which keeps the kernel's packages flat, lets that one package through.
So this ADR extends ADR-125: the kernel's one subpackage besides `shared.application` is an adapter.
M0 decides how Spring Modulith exposes `shared` (ADR-125, Revisit when), and it also decides how a
module's adapters reach `shared.adapter.out.persistence`.

**Configuration (12 to 15).**

- **No JDBC batching for now (12).** A new 200-stop plan takes about 400 statements, an `INSERT` and
  an index `UPDATE` per stop under `@OrderColumn`. That is an estimate, not a measurement.
- **Spring Modulith's event publication registry stays on JDBC (13)** (`spring-modulith-starter-jdbc`,
  as [TS §6][TS-6] already says), so publication rows stay out of the persistence context that the
  version helper flushes. When Modulith joins the build, its table comes from a Flyway migration that
  copies Modulith's documented PostgreSQL schema, with
  `spring.modulith.events.jdbc.schema-initialization.enabled=false`.
- **The schema changes only through Flyway (14),** and `spring.jpa.hibernate.ddl-auto` is `validate`
  in every profile that connects to a database, so Hibernate checks the mapping against the migrated
  schema at startup ([ADR-041]). The schema-draft profile connects to none and sets `none`, because
  `validate` needs a database. No profile sets `create` or `update`.
- **A `just` recipe writes a draft (15).** `just core-schema-draft` writes the DDL Hibernate would
  generate for the current mapping to `src/backend/core/target/schema-draft.sql`, through a
  `schema-draft` profile that sets `jakarta.persistence.schema-generation.scripts.action=create`
  and `…scripts.create-target`, with `jakarta.persistence.schema-generation.database.action=none`.
  It also creates each module's schema (`jakarta.persistence.create-database-schemas=true`), so the
  draft names the schemas its first migration must create. It opens no database connection, so
  Hibernate reads no database metadata, and the profile names the PostgreSQL dialect and major
  version 18 itself. A
  developer copies what they need into a new migration and adds the indexes, constraints and grants
  by hand.
- **No SQL, bind-value or error-detail logging in any profile:** `spring.jpa.show-sql=false`, no
  logging configured for `org.hibernate.orm.jdbc.bind`, and
  `spring.datasource.hikari.data-source-properties.logServerErrorDetail=false`. Bind values hold
  personal data ([NFR-19]), and PostgreSQL's error detail quotes the failing row, which Hibernate
  logs at `WARN` with every SQL error.

**What the domain and the kernel may carry (the exception to [DL §3.9][DL-39] rule 1).**

- `jakarta.persistence` annotations;
- from Hibernate, `@OptimisticLock` and `@Immutable`. An `@EmbeddableInstantiatorRegistration` lives
  in an adapter, never in the domain;
- from Spring Data, `@DomainEvents` and `@AfterDomainEventPublication`.

Annotations only: no `EntityManager`, Spring or Hibernate call. Never in an `events` package. This
is a deliberate exception to "the domain never imports frameworks", and the architecture tests allow
those annotation types, and the enums the Jakarta Persistence annotations take as values (such as
`EnumType`, or the `LockModeType` of `@NamedQuery`), and nothing else from those packages.

**Architecture tests.** The eight rules are in a new `PersistenceRules`, each with a deliberately
wrong fixture:

- relationships stay inside one aggregate: no root relates to another root, a child refers back
  only to its own root, no entity is reached from two roots, and none is in another module;
- no class other than `AggregateRoot` declares `@Version`, on a field or a method;
- every enum attribute is stored by name: `@Enumerated(EnumType.STRING)`, and
  `@MapKeyEnumerated(STRING)` for an enum map key;
- every `@Entity` that extends `AggregateRoot` declares a `@PostLoad` method;
- no `@GeneratedValue`;
- no interface extends `CrudRepository`, `ListCrudRepository`, `JpaRepository` or
  `JpaSpecificationExecutor`;
- every table an entity maps is in its module's schema (the tables listed under the mapping above);
- a module's adapter uses no other module's `@Entity`.

`DomainShapeRules`' rule that a root's constructors are private allows the protected no-argument
constructor Hibernate needs. One more rule waits for the change that maps the last of the 20 roots:
every subclass of `AggregateRoot` is an `@Entity`.

**Tests.**

- The version helper is tested against the Testcontainers PostgreSQL, on a test-only aggregate with
  one child entity under `src/test`, in 20 tests that prove:
  - a created aggregate is stored at 0 with its events at 0, and a change to the root, to a child
    only, to a child's list, and to both each move the version by exactly 1, as does a save with
    nothing changed;
  - a conflict ends in `StaleVersion`, for a change to the root and for a change to a child only:
    transaction A loads, a `REQUIRES_NEW` transaction B loads, changes and commits, and A changes
    and saves, on one thread with no `sleep`. So does a save of an aggregate loaded in an earlier
    transaction, a save that times out on a row lock another transaction holds, and a first save
    that loses a race on the same key, after which the first stands;
  - the events are published on `save`, and a listener bound to the commit never sees those of a
    save that fails;
  - a query that flushes between two changes makes the save fail loudly, with nothing stored;
  - a row the database refuses reaches neither the error nor the log with its values;
  - the kernel's converters and embeddables are stored and read back on the same aggregate, against
    `ddl-auto=validate`, and a stored blank name, a window that ends before it starts and a due time
    before the day are each refused when loaded.
- Its table, in its own schema `persistence_fixture`, comes from a test-only migration `V0_1__…` in
  `src/test/resources/db/fixture/`. Migration versions `V0_*` are reserved for test fixtures, so a
  real migration never collides with one. A test configuration, which the integration tests and
  `just core-run` both load, adds that location to Flyway's and keeps the ArchUnit fixtures out of
  Hibernate's scan.
- Two public test helpers serve the fixtures and the in-memory fakes: `SaveEvents.publish` and
  `StoredVersions.afterSave`.
- Every lane's repository adds one conflict test through its port, built the same way.

**What this amends.**

- **[ADR-001]:** its rule that the domain imports no framework now has this ADR's exception for
  persistence annotations. DDD with ports and adapters stands.
- **[ADR-041]:** JPA replaces jOOQ. The rest stands: one PostgreSQL schema per module, Flyway the only
  way to change the schema, the two database roles, the one-shot `migrate` step and the audit purge
  function. Code generation, its plugin and the generated code's place in the build go with jOOQ.
- **[ADR-040]:** purity rule 1, because the domain may carry the annotations above, and rule 6,
  because `restore` goes: only Hibernate rebuilds an aggregate, and the factories of the snapshots
  read under a lock keep their half of the rule.
- **[ADR-097]:** `restore`, and the version a first save stores. `StoredStateInvalid` is now thrown
  by the `@PostLoad` check and the converters.
- **[ADR-101], decision 38:** a command checks the state it leaves after changing it in place,
  against the same invariants `@PostLoad` checks, and a break there stays a bug, not a refusal.
- **[ADR-121], P31:** the company profile's first save is no longer the only one that sends
  `If-None-Match: *`. The first change of every aggregate that may not be stored yet, the
  singletons and a date's roster, sends it, and the use case takes an `Optional<Version>` that is
  empty then.

**What the accepted ADRs that name jOOQ in passing say now.** They stand as written:

- [ADR-057] stands: Hibernate issues its version check.
- [ADR-059]'s revision rows are `@Immutable` entities.
- [ADR-075]'s `@PersonalData` marker on each personal column goes on the entity attribute that maps
  the column, instead of on a jOOQ field.
- [ADR-047]'s GraalVM note now reads Hibernate for jOOQ, and Docker is still needed for the
  integration tests.
- [ADR-110] and [ADR-120] are unaffected.

## Consequences

- **Easy:** Hibernate writes [ADR-057]'s `UPDATE … WHERE id = ? AND version = ?` for every root, so
  no repository carries its own.
- **Easy:** no generated code and no code-generation plugin, so the build no longer needs Docker for
  code generation. The integration tests still need it.
- **Easy:** no hand-written mapper and no second set of entity classes: a new column touches the
  migration and the class that maps it.
- **Easy:** the events leave with `save`, one place for every use case, and the in-memory fakes do
  the same.
- **Easy:** `ddl-auto=validate` checks the mapping against the migrated schema at startup, and
  `just core-schema-draft` gives a first draft of a migration.
- **Hard:** the domain imports `jakarta.persistence`, Hibernate and Spring Data annotations. Rule 1
  has an exception, and a reviewer must keep it to annotations. The architecture tests check which
  annotation types, not that an annotation is the right one.
- **Hard:** a mapped class is not `final`, its persistent fields are not `final`, and it has a
  protected no-argument constructor. Hibernate rebuilds an aggregate without a factory, so a
  stored value that breaks an invariant is caught by the `@PostLoad` check, not by construction.
- **Hard:** a command changes entities in place, so the check at its end is what keeps a broken
  state from being saved. A command that forgets it saves what it broke.
- **Hard:** an aggregate loads whole inside the transaction. A lazy collection touched outside it
  fails, and fetching several collections in one query multiplies the rows.
- **Hard:** every use case that changes an aggregate must call `save` or `add`. Without it the change
  is written but no event goes out.
- **Hard:** a retry wraps the transaction from outside, so a retrying caller cannot be inside a
  `@Transactional` method of its own.
- **Hard:** PostgreSQL-specific SQL is a string in a native query, which no compiler checks. Each one
  has an adapter test against the real database for that reason.
- **Hard:** a view of an aggregate that may not be stored yet carries an `Optional<Version>`, and a
  use case that creates it takes one.
- **Hard:** an aggregate is saved once per transaction, with no flush between two of its changes. A
  use case that saves twice, or queries between two changes, fails with `AssertionError`.
- **Hard:** a position constraint must be `DEFERRABLE INITIALLY DEFERRED`, which the migration
  writes by hand, as it does the partial unique indexes.
- **Cost:** all 20 roots are mapped, one lane at a time, each removing `final` from its classes,
  turning its record children into entities and replacing its `restore` with `@PostLoad`. Up to then
  the transitional constructor and the nullable snapshot version stay.
- **Cost:** the 114 mentions of `Version.NEW` and the tests that expect a creation event at version 1
  change with the kernel.
- **Cost:** a save that changes only child rows takes one more statement and holds a row lock on
  the root until commit.
- **Cost:** a new plan's stops are written without batching, about 400 statements for 200 stops,
  estimated and not measured.
- **Cost:** the documents that decide the design change with this ADR: `tech_stack.md`,
  `domain_layer_design.md`, `requirements.md` and `roadmap.md`. The documents that describe the code
  as built change with the code.
- **Risk:** the version helper relies on two behaviours of Hibernate 7.4.13: a `null` version is
  seeded with 0, and `PESSIMISTIC_FORCE_INCREMENT` writes the increment inside `lock`. A Hibernate
  upgrade can change either. The helper's adapter tests against PostgreSQL are what catch it.
- **Risk:** Spring Data publishes events only when a repository method whose name starts with `save`
  or `delete` runs. A use case that changes an aggregate and skips `save` publishes nothing, and its
  test of the events it raised catches it.
- **Risk:** `just core-schema-draft` is a draft. It does not write the partial unique indexes, the
  `CHECK` constraints or the grants, so a migration copied from it as it is lacks what the rules
  rely on ([BR-34], [BR-38]).
- **Risk:** the schema rule checks the tables an entity maps, and that an adapter uses no other
  module's entity. A JPQL or native query whose text names another module's schema is not checked,
  so review checks it.

## Alternatives considered

- **jOOQ,** as [ADR-041] decided. Rejected: the team chose JPA on 2026-11-22. It would keep the
  hand-written version check and the mappers that [ADR-041] lists as Hard, and the code-generation
  plugin and its generated code.
- **JPA with a second set of entity classes and mappers,** keeping the domain free of annotations.
  Rejected (decision 1): it doubles the classes and adds a mapper per aggregate, which is what
  [ADR-041] turned JPA down for.
- **Spring Data JDBC.** Rejected, for [ADR-041]'s reason: it saves a changed collection by deleting
  and reinserting every child row, so every edit would rewrite a 200-stop plan. Revisit when a
  measured 200-stop plan shows that JPA's writes of its child rows cost more than Spring Data
  JDBC's would.
- **MyBatis and `JdbcTemplate`.** Still rejected, for [ADR-041]'s reasons: SQL in XML with no
  compile-time checking, and no type safety across the tables. Revisit when native queries, with
  their adapter tests, can no longer carry a read or a write that JPA can't.
- **`JpaRepository`, `CrudRepository`, `ListCrudRepository` or `JpaSpecificationExecutor`** for the
  adapters. Rejected (decision 6): they bring `saveAll`, `deleteAll`, batch deletes and bulk
  updates, which no aggregate needs. An adapter declares only the methods its port needs. Revisit
  when an aggregate needs a batch delete that one native `DELETE` can't serve.
- **Not adopted, each with the trigger that would reopen it (16):**

| Feature | Why not | Revisit when |
|---|---|---|
| Spring Data's `AbstractAggregateRoot` | It would replace the way `raise` builds and checks each `EventId`, so we annotate our own `AggregateRoot` instead | `raise` stops building the `EventId` |
| Auditing annotations (`@CreatedDate`, `@LastModifiedDate`, `@CreationTimestamp`, `@UpdateTimestamp`) | They read the system clock, not the `Clock` port, so tests and the end-to-end clock ([NFR-175]) can't move them, and [DL §3.9][DL-39] rule 2 makes `now` a parameter. No requirement asks for these columns | A requirement asks for change times on rows that don't have them |
| Hibernate Envers | It copies personal data into history tables that retention ([ADR-075]) would also have to purge. `AuditLog` ([NFR-93], [NFR-94]) and `PlanRevision` rows ([ADR-059]) already cover history, and Envers doubles every write | A requirement needs a field-level history that neither covers |
| `@SoftDelete` | Retention must really remove personal data ([ADR-075]); hidden rows still count against unique constraints such as [BR-45]'s; the domain models deactivation as a state | A requirement asks to restore deleted records |
| Second-level and query caches | One instance with small data, and Caffeine already caches the hot reads ([TS §6][TS-6]). Cached aggregates can go stale next to optimistic locking, and native queries skip the cache | A measured read hotspot that a Caffeine cache at the port can't serve |
| Bytecode enhancement | Another plugin rewriting the domain classes, beside Error Prone, NullAway and JaCoCo, for nothing that needs it: each transaction loads one aggregate, and the plan's matrix is mapped outside `Plan` | A large column must live on an aggregate's own row and slows its loads |
| Spring Modulith's JPA publication registry | Its publications would become entities Hibernate tracks and flushes with our aggregates. The JDBC registry runs in the same transaction without that | The M0 test shows the JDBC registry doesn't commit or roll back together with the JPA change |
| `ddl-auto` set to `create` or `update` | `create` wipes the data. `update` only adds: it never renames or drops, and it can't write partial indexes, `CHECK` constraints, grants, functions or data migrations. The core's `app` role owns no table anyway | We drop Flyway |
| JDBC batching | About 400 statements for a new plan's 200 stops, an `INSERT` and an index `UPDATE` per stop, estimated, not measured | [TS §17.4][TS-174]'s measurement shows writes matter |

## Revisit when

- Semester B's Reporting or Live Operations reads pile up as native SQL. Then add jOOQ for those
  reads only, and leave the writes with JPA.
- A condition in the last column of the table above holds.
- An aggregate's mapping cannot be written with the annotations listed, and needs a framework call
  in the domain. Then reconsider a second set of entity classes for that aggregate.

[ISSUE-101]: https://github.com/NaimElijah/DispatchFlow/issues/101
[ADR-001]: ADR-001-domain-driven-design-with-hexagonal-architecture.md
[ADR-040]: ADR-040-core-is-a-spring-modulith-monolith-with-one-module-per-context.md
[ADR-041]: ADR-041-one-postgresql-schema-per-module-read-and-written-through-jooq.md
[ADR-047]: ADR-047-images-pinned-by-digest-and-core-on-temurin-25-jre.md
[ADR-057]: ADR-057-concurrent-changes-to-a-plan-use-optimistic-locking.md
[ADR-059]: ADR-059-plan-revisions-are-stored-as-rows.md
[ADR-075]: ADR-075-retention-deletes-records-and-anonymizes-people-in-place.md
[ADR-097]: ADR-097-the-shared-kernel-holds-the-domains-building-blocks.md
[ADR-101]: ADR-101-completed-plans-take-late-outcomes-as-revisions-and-routes-keep-an-index.md
[ADR-110]: ADR-110-audit-records-hold-only-ids-codes-enums-numbers-and-instants-record-refusals-and-are-kept-a-year.md
[ADR-120]: ADR-120-the-application-layer-takes-an-actor-and-a-call-context-and-reads-through-query-ports.md
[ADR-121]: ADR-121-the-application-skeletons-review-settles-its-seams-conflicts-retention-and-scoring-inputs.md
[ADR-125]: ADR-125-a-folder-above-20-files-splits-wherever-a-group-of-four-can-move.md
[RM]: ../roadmap.md
[DL-11]: ../architecture/domain_layer/domain_layer_design.md#11-concurrency
[DL-31]: ../architecture/domain_layer/domain_layer_design.md#31-aggregates
[DL-32]: ../architecture/domain_layer/domain_layer_design.md#32-records
[DL-310]: ../architecture/domain_layer/domain_layer_design.md#310-a-rich-model
[DL-36]: ../architecture/domain_layer/domain_layer_design.md#36-repositories
[DL-39]: ../architecture/domain_layer/domain_layer_design.md#39-purity
[DL-41]: ../architecture/domain_layer/domain_layer_design.md#41-access
[DL-5]: ../architecture/domain_layer/domain_layer_design.md#5-rules-over-sets-of-aggregates
[TS-6]: ../architecture/tech_stack.md#6-core-service-java
[TS-65]: ../architecture/tech_stack.md#65-core--optimizer-calls
[TS-174]: ../architecture/tech_stack.md#174-performance-measurement
[BR-34]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-38]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-45]: ../requirements/requirements.md#access
[NFR-19]: ../requirements/requirements.md#nfr-19
[NFR-93]: ../requirements/requirements.md#nfr-93
[NFR-94]: ../requirements/requirements.md#nfr-94
[NFR-175]: ../requirements/requirements.md#nfr-175

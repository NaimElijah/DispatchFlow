# ADR-127: How the 20 aggregates are mapped, and where their stored state is tested

- **Status:** Accepted
- **Date:** 2026-11-23

## Context

[ADR-126] replaced jOOQ with JPA and decided how a domain class carries its mapping. It left each
root's mapping to the change that builds its repository. [Issue #112][ISSUE-112] maps all 20 roots
at once, and doing so meets questions that ADR-126 does not settle, or settles in a way the code
cannot follow:

- **The damaged-snapshot tests.** About 150 domain tests build a damaged snapshot, call `restore`,
  and expect `StoredStateInvalid`. Once `restore` goes (ADR-126 decision 8), no domain code can build
  a damaged aggregate, because every factory and command keeps the invariants.
- **The reload helpers.** 18 builders and fixtures simulate a save and a reload through
  `restore(snapshot)`, to move the version and forget the events between two commands.
- **Children without an ID.** ADR-126 makes `DayRoster`'s entries entities. But a `RosterEntry` has no
  ID of its own: it is keyed by its driver. ADR-126's own rule 7 makes a child without an ID a value.
- **Values JPA cannot store as they are.** Many value records have sealed or `Optional` components,
  such as a `DroppedOrder`'s `DropReason` or an `Edit` in `Plan`'s journal. Some value lists hold
  value lists, such as a proposal's solved routes and their stops. An `@Embeddable` cannot hold an
  `Optional`, a sealed type or a collection.
- **The DL §3.2 records.** ADR-126 makes positions, audit records, SMS replies and opt-outs
  `@Immutable` entities. But `KeptPosition` and `InboundReply` are records in
  `application.port.out`, and ApplicationRules rule 1 lets an application package import no
  persistence annotation. `AuditRecord` is a Java record, which Hibernate cannot map as an entity.
  And none of the four has an ID, while an entity needs one, and `@GeneratedValue` is forbidden.
- **The import preview's errors.** Each preview row keeps its `DomainError`s, which can be records
  from any module's error type: 28 in Orders and 12 in the kernel alone, each with its own
  components. ADR-126's relational rules have no table for "any error record".
- **Grants.** [ADR-041] asks every migration that adds a table to grant the `app` role what it needs.
  Neither role exists yet: the database has one user, and ADR-041's roles come with the `migrate`
  step, which is not built. A `GRANT` to a missing role fails the migration.
- **Migration versions and lock keys.** Eight modules each add tables in one change, and several
  adapters take advisory locks.

## Decision

We map the 20 roots as ADR-126 decides, with these nine decisions where it is silent or cannot be
followed.

1. **A stored-state check is tested where it runs: when the aggregate loads.** Each repository's
   integration test saves a valid aggregate, damages one stored value with SQL, and loads it again.
   It expects `StoredStateInvalid` with the subject and detail `restore` gave. Where a migration's
   `CHECK` constraint already forbids the damaged value, the test expects the database to refuse the
   write instead. The round-trip tests move to the same integration tests. Domain tests reach every
   state through factories and commands only.
2. **A domain test simulates a save with one helper,** `AsSaved.asSaved(root)` in the kernel's test
   package. It does to the instance what saving it does: it publishes and clears the pending events,
   as Spring Data does, and moves the version field, as Hibernate does, by reflection. It replaces
   every reload helper, and it is the only place a test reaches into a class's private state.
3. **A child without its own ID is a value,** stored in an `@ElementCollection` with an
   `@OrderColumn`. That includes `DayRoster`'s entries, which ADR-126's text lists with the entities.
   - A value whose components are sealed or `Optional` is stored as a private `@Embeddable` class in
     the domain package, with a kind and nullable fields, as ADR-126 decision 9 does for a root's
     state. The public record stays the type every caller sees, and its getter builds it.
   - A value list inside a value list is flattened into one element collection keyed by both
     positions, because JPA cannot nest a collection in an embeddable. Proposals, solve results and
     revisions are stored this way.
4. **Maps and sets.**
   - An `EnumMap` is an element-collection map keyed with `@MapKeyEnumerated(STRING)`.
   - An insertion-ordered map (the register's depots and vehicles, the roster's entries) becomes a
     list with an `@OrderColumn`, which the class searches by key.
   - A set of enums is an element collection stored by name.
5. **Keys.**
   - A root keyed by another aggregate's ID uses that UUID as its key: `Driver` (the account),
     `Delivery` and `StopMessages` (the stop), `RouteRun` (the route), `Baseline` (the plan).
   - A singleton keys on a constant UUID that its class documents.
   - `DayRoster`, found by its date, takes a UUID from `IdGenerator`, beside a date its table keeps
     unique.
6. **The four DL §3.2 records are stored through `@Immutable` entity classes in their module's
   persistence adapter:** `KeptPosition`, `InboundReply`, `AuditRecord` and `OptOutRecord`. The
   record types stay free of annotations. A row's key is the record's natural key where it has one,
   such as an SMS reply's provider ID, and otherwise a random UUID that the adapter assigns and
   nothing outside the table sees.
7. **An import preview row's errors are stored as JSON text,** through a converter in Orders'
   persistence adapter that uses Jackson, which Spring Boot already brings. Its type validator admits
   only records that implement `DomainError` and live under `com.dispatchflow`, so a stored row cannot
   name another class. It rebuilds each error through `StoredStateInvalid.whileRestoring`. This is
   the one JSON column. Every other value is relational, as ADR-126 decides.
8. **A migration creates its tables without grants until [ADR-041]'s roles exist.** The change that
   adds the roles and the `migrate` step grants `app` what it needs on every table made before it.
   From then on, every migration grants as ADR-041 says.
9. **Migration versions and lock keys.**
   - `V1__create_module_schemas.sql` creates the eight schemas of ADR-041. Each module then has one
     migration: `V2` access, `V3` audit, `V4` fleet, `V5` liveops, `V6` messaging, `V7` orders,
     `V8` planning and `V9` reporting. Later migrations take the next free number.
   - An advisory lock's key is `hashtext('<module>:<lock>:' || <value>)`, taken with
     `pg_advisory_xact_lock`, so each module's locks are named apart and released at commit.

**What this amends.**

- **[ADR-126]:** `DayRoster`'s entries are values, not entities (3). The records are `@Immutable`
  entities in their adapters, not the record types themselves (6). A preview row's errors are one
  JSON column (7).
- **[ADR-041]:** a migration grants from the change that adds the roles, not before (8).

## Consequences

- **Easy:** every stored-state check is tested through the path that runs it in production: a row
  Hibernate loads and the `@PostLoad` check refuses.
- **Easy:** a domain test can no longer build a state the commands cannot reach, so the states the
  tests use are states the system can be in.
- **Easy:** private embeddables keep each table's columns typed and checkable, and leave every
  public record as it was.
- **Hard:** the stored-state cases are integration tests, so they need the PostgreSQL container, and
  each needs SQL that names real columns. A column rename changes them.
- **Hard:** a builder that used to restore a deep state now drives the commands that lead to it,
  so it is longer.
- **Hard:** a private embeddable repeats a public record's components as fields, and its getter and
  its setter code must match the record.
- **Hard:** the preview's errors are the one place whose stored form a Java type does not check.
  Their converter's tests are what check it.
- **Cost:** until the roles change lands, the core reads and writes its tables as one user, as it
  does today.

## Alternatives considered

- **Keep `restore` as a factory only tests call.** Rejected: ADR-126 removes it because only
  Hibernate rebuilds an aggregate. A test-only factory could build states no command reaches, and
  would keep the snapshot types alive.
- **Keep a state record per root, and test the invariants on it directly.** Rejected: that is the
  snapshot under another name, and it would test a function the `@PostLoad` check calls instead of
  the check itself.
- **Set a damaged state by reflection in domain tests.** Rejected: it ties every such test to the
  private field names of each class.
- **Map `RosterEntry` as an entity with a key made of the roster and the driver.** Rejected: a
  composite key over a join column that the parent also writes is harder to keep right than an
  element collection, and the entries have no identity of their own.
- **Store every deep value as JSON** (the journal, the drop reasons, the violations, the solve
  results). Rejected: ADR-126 chose columns, so that the database checks them and reports read
  them with SQL. JSON would also need a decoder for every value type that keeps the `ofStored`
  checks.
- **Store the preview's errors relationally,** as a code and a list of parameters. Rejected: each
  error record has its own components, so rebuilding it needs the same per-type decoding as JSON,
  with more tables.
- **Give each record an ID in the domain and map the record type itself.** Rejected: two of the
  types are Java records and two live in the application layer, so they can't carry the mapping,
  and an ID only the table uses would change every caller that builds one.
- **Create the roles in `V1`.** Rejected: a role is cluster-wide, so creating it needs a privilege
  the migration's owner should not hold, and the `migrate` step that ADR-041 gives the owner
  credential to is not built.

## Revisit when

- A report needs to filter on an import preview's errors in SQL.
- The stored-state integration tests make the core's checks too slow for a pull request.
- The roles and the `migrate` step land: decision 8 ends then.

[ISSUE-112]: https://github.com/NaimElijah/DispatchFlow/issues/112
[ADR-041]: ADR-041-one-postgresql-schema-per-module-read-and-written-through-jooq.md
[ADR-126]: ADR-126-jpa-replaces-jooq-and-the-domain-carries-its-mapping.md

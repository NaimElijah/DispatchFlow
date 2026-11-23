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
- **Concurrent contacts.** A session keeps the times its holder was in contact, and two requests
  can record one at once. ADR-126's ordered list numbers every row, so two concurrent contacts
  would take the same position.
- **Children that move.** A plan's stops move between its routes and keep their IDs. Jakarta
  Persistence forbids moving an orphan-removed entity to another relationship, which ADR-126's
  rule for collections would have each route's list do.
- **Lookups by a value the root doesn't hold.** Live Ops finds a date's route runs, and the open
  alerts of a date's plan, by delivery date. Neither `RouteRun` nor `Alert` holds that date: it is
  their plan's, in Planning.

## Decision

We map the 20 roots as ADR-126 decides, with these twelve decisions where it is silent or cannot be
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
   every reload helper, and it is the only place a test reaches into a class's private state. A
   domain test in the root's package may also call the package-private `@PostLoad` check, to show
   that a state its commands reach would load again.
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
6. **The four DL §3.2 records, and how a plan's baseline was settled, are stored through
   `@Immutable` entity classes in their module's persistence adapter:** `KeptPosition`,
   `InboundReply`, `AuditRecord` and `OptOutRecord`, and Reporting's `StoredBaselineOutcome`, which
   its repository writes once per plan. The record types stay free of annotations. A row's key is
   the record's natural key where it has one, such as an SMS reply's provider ID or the baseline's
   plan, and otherwise a random UUID that the adapter assigns and nothing outside the table sees.
7. **An import preview row's errors, and an unreadable row's field errors, are stored as JSON text,**
   each in a column of its own, through a converter in Orders'
   persistence adapter that uses Jackson, which Spring Boot already brings. Its type validator admits
   only records that implement `DomainError` and live under `com.dispatchflow`, so a stored row cannot
   name another class. It rebuilds each error through `StoredStateInvalid.whileRestoring`.
   These are the only JSON columns. Every other value is relational, as ADR-126 decides.
8. **A migration creates its tables without grants until [ADR-041]'s roles exist.** The change that
   adds the roles and the `migrate` step grants `app` what it needs on every table made before it.
   From then on, every migration grants as ADR-041 says.
9. **Migration versions and lock keys.**
   - `V1__create_module_schemas.sql` creates the eight schemas of ADR-041. Each module then has one
     migration: `V2` access, `V3` audit, `V4` fleet, `V5` liveops, `V6` messaging, `V7` orders,
     `V8` planning and `V9` reporting. Later migrations take the next free number.
   - An advisory lock's key is `hashtext('<module>:<lock>:' || <value>)`, taken with
     `pg_advisory_xact_lock`, so each module's locks are named apart and released at commit.
10. **A session's contact times are a set of instants, ordered by value,** in a table keyed by the
    session and the instant, with no position, and mapped `@OptimisticLock(excluded = true)`. A
    contact is then one row inserted and a dropped one is one row deleted, so two requests that save
    contacts at once each keep their own, and a drop never rewrites the rest. ADR-126's ordered list
    would number every row, so two concurrent contacts would take the same position, and dropping
    the oldest would renumber up to two weeks of them. Two contacts at the very same instant are
    kept once, as BR-133's contact window reads them: one call's set holds it once, and when two
    calls keep it at once, the later save answers `StaleVersion`, and its retry finds it kept. The
    stored instant is rounded to the microsecond, so two instants within one are one.
11. **A child entity that moves between parents inside one aggregate is owned by the root.** A
    `Plan`'s stops move between its routes and keep their IDs (DL §3.1). The root holds every stop
    in a collection of its own, mapped `cascade = ALL, orphanRemoval = true`, which alone creates and
    deletes them. Each route's ordered list holds only which stops it serves, in order: it cascades
    `PERSIST` and `MERGE` and has no orphan removal. A composite foreign key from a stop's route and
    plan to the route's ID and plan, `DEFERRABLE INITIALLY DEFERRED`, keeps a stop on a route of its
    own plan, and the plan's end check keeps the two collections holding the same stops. Jakarta
    Persistence forbids moving an orphan-removed entity to another relationship, and Hibernate
    deletes each list's orphans as it flushes that list, so with ADR-126's mapping on the routes'
    lists, whether a moved stop survived depended on the order the flush visited the routes.
12. **A value a root doesn't hold, kept only for a lookup, is a column its adapter writes.** A
    `RouteRun` and an `Alert` are stored with their plan's delivery date in a `delivery_date`
    column that neither class maps. Their repository's `add` takes the date from its caller and
    writes it with a native `UPDATE` right after the insert, which must change exactly one row, so
    the column is nullable and indexed. Only the by-date lookups read it: `RouteRunRepository`'s
    `startedOn` and `LiveOpsViews`' `unacknowledgedAt` (FR-261, BR-102). Live Ops' date queries then
    stay in its own schema, with no join into Planning's.

**What this amends.**

- **[ADR-126]:** `DayRoster`'s entries are values, not entities (3). The records are `@Immutable`
  entities in their adapters, not the record types themselves (6). A preview row's errors are JSON
  text (7). A session's contact times are a set, not an ordered list (10). A child that moves
  between parents has its lifecycle in the root's collection, not in its parent's list (11).
  `AsSaved` replaces ADR-126's test helper `StoredVersions.afterSave`, which is deleted (2).
  A table may hold a column its root doesn't map, which only its adapter writes and only lookups
  read (12).
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
- **Hard:** the preview's errors are the one stored form a Java type does not check.
  Their converter's tests are what check it.
- **Hard:** each stored error names its record's class, so renaming or moving a `DomainError`
  record an import can raise leaves the preview rows that name it unloadable (`StoredStateInvalid`).
  A change that renames or moves one carries a migration that rewrites the stored name. A preview's
  rows are deleted at most two hours after it was made (FR-399), so few rows are ever at stake.
- **Hard:** a run's or an alert's delivery date is in its table but not in its class, so no domain
  rule can read it, and the column can't be `NOT NULL`, because Hibernate inserts the row before
  the adapter writes the date. The adapter's one-row check keeps the two writes together.
- **Hard:** a `Plan` keeps its stops in two collections, the root's and their route's, so each
  command that adds or takes off a stop changes both. The end check catches the two drifting apart.
- **Risk:** decision 11 relies on Hibernate leaving a stop's `route_id` alone when the stop leaves
  a route's list, which it does because that join column is not nullable, and on the deferred keys
  being checked only at commit. The stop-move tests in `PlanPersistenceIT` show it, and would show
  a Hibernate upgrade that changes it.
- **Risk:** each root's `@PostLoad` check reads its collections, so they load while the root
  loads. Jakarta Persistence calls relationship access in a callback unportable, so the adapters
  still fetch every collection, and a load stays whole if a check stops reading one.
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
- **Store each preview error under a stable code that a registry maps to its record.**
  Rejected: the registry must list every `DomainError` record an import can raise, across modules,
  and be kept in step with them, while a preview's rows live at most two hours, so a rename rarely
  meets a stored row.
- **Give each record an ID in the domain and map the record type itself.** Rejected: two of the
  types are Java records and two live in the application layer, so they can't carry the mapping,
  and an ID only the table uses would change every caller that builds one.
- **Keep ADR-126's `cascade = ALL, orphanRemoval = true` on each route's list of stops.** Rejected:
  a stop moved from a later route to an earlier one was deleted when the flush came at commit,
  because Hibernate visits each list in turn, and the specification calls the move unportable. Its
  tests passed only because the shared save flushes in one particular order.
- **Make a moved stop a new entity with the same ID.** Rejected: an entity deleted and inserted
  again under one ID in one flush is refused by the persistence context, and the stop's identity is
  what the domain says a move keeps.
- **Create the roles in `V1`.** Rejected: a role is cluster-wide, so creating it needs a privilege
  the migration's owner should not hold, and the `migrate` step that ADR-041 gives the owner
  credential to is not built.
- **Give `RouteRun` and `Alert` a delivery date field.** Rejected: neither aggregate's rules use the
  date, and every factory and test that makes one would have to be given it, only so that a query
  can find it.
- **Keep the delivery dates in a lookup table of their own.** Rejected: every by-date query would
  join it, and it is one more table to keep in step with the runs and alerts, for no check the
  column lacks.

## Revisit when

- A report needs to filter on an import preview's errors in SQL.
- The stored-state integration tests make the core's checks too slow for a pull request.
- The roles and the `migrate` step land: decision 8 ends then.
- A Hibernate upgrade changes how a one-to-many deletes its rows, or how lazy loading behaves
  inside a callback (decision 11, and the load checks).
- Retries of concurrent contacts on one session show in latency (decision 10).
- A stored preview error must outlive its preview, or the error records an import can raise are
  renamed often enough that their migrations cost more than a code registry (decision 7).
- A domain rule needs a run's or an alert's delivery date, or a third root needs a column it
  doesn't hold (decision 12).
- Renumbering an element collection's positions costs more than giving its rows their own IDs,
  as a full undo journal's 100 rows might (decision 3).

[ISSUE-112]: https://github.com/NaimElijah/DispatchFlow/issues/112
[ADR-041]: ADR-041-one-postgresql-schema-per-module-read-and-written-through-jooq.md
[ADR-126]: ADR-126-jpa-replaces-jooq-and-the-domain-carries-its-mapping.md

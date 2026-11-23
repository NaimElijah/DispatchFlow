# ADR-128: The persistence rules the JPA mapping settled after ADR-127

- **Status:** Accepted
- **Date:** 2026-11-23

## Context

[Issue #249][ISSUE-249] audited the core's JPA persistence after [ADR-126] and [ADR-127], and
[pull request #252][PR-252] acts on it. The code has six rules that no ADR records, or that an ADR
records otherwise:

- **A session's writes.** ADR-126's Versioning section says that a session's contact times are
  "saved through its own port method, which forces nothing". The code has two writes besides `add`:
  - `SessionRepository.save` checks the version unless the change only kept or dropped contact
    times, which a transient flag on the session, `changedBeyondContactTimes`, tells the adapter;
  - `saveRevocation` ([ADR-121] P20), which the use case calls for a session the domain revoked:
    the adapter detaches the session and writes the revoked state over a live row with one native
    `UPDATE`, which moves the version.
- **State that no entity maps.** ADR-127 decision 12 allows "a column its root doesn't map, which
  only its adapter writes and only lookups read". Two shapes go beyond that:
  - `reporting.baseline.outcome` is a column `Baseline` doesn't map. Its default writes it,
    `NOT NULL DEFAULT 'COMPUTED'`, and the composite foreign key `baseline_settles_its_plan` reads
    it. Together they require a baseline's plan to be settled as computed, so that no plan holds
    both a baseline and a refusal (FR-39).
  - `messaging.counted_answer` is a table that no entity maps. It holds the reply answers counted
    toward a day's message cap (NFR-196, ADR-121 P24). `StopMessagesRepository.countAnswer` writes
    it with native SQL, and the cap reads its count.
- **Durations.** The shared adapter's `DurationConverter` is applied to every `java.time.Duration`
  and stores it as `bigint` nanoseconds. ADR-126 lists the shared adapter's converters and doesn't
  name it.
- **Gaps in a stored list.** Hibernate 7.4.13 rebuilds an `@OrderColumn` list by padding it with
  `null` up to each row's position, and the schema keeps positions unique but not contiguous. A gap
  therefore loaded as a `null` element, which most load checks met as a `NullPointerException`
  rather than `StoredStateInvalid` (ADR-127 decision 1), and some didn't meet at all. A negative
  position made Hibernate throw `IndexOutOfBoundsException` before any load check ran.
- **Instants.** PostgreSQL keeps an instant to the microsecond, and pgjdbc rounds each instant it
  binds on its own. A value computed in memory from instants with nanoseconds, such as a reported
  outcome's corrected time (BR-114), could then differ by a microsecond from the one its stored
  parts give back, and fail its load check.
- **Loads that read a collection twice.** A root's load check can read a collection its query
  didn't fetch, as `Delivery`'s reads its held reports, so loading several roots cost a statement
  per root.

On 2026-11-23 the owner, Naim Elijah, accepted all six decisions after the review of
[pull request #252][PR-252]. They are final.

## Decision

1. **A session has two writes besides `add`, and the session or the use case picks each.**
   - `save` goes through `VersionedSave` unless the session's flag is false. The flag is true for a
     session that was never saved, and for one whose tokens, state, generation or password time
     changed since it was loaded: the private helpers every such change goes through set it, and
     `recordContact` and `dropOldContacts` don't. When it is false, the adapter saves and flushes
     with no version check and no forced increment (DL §4.1); the flush writes whatever else is
     pending.
   - `saveRevocation` stays as [ADR-121] P20 decides it.
   - `SessionPersistenceIT` checks each command's flag against the version check.
2. **The schema may hold state that no entity maps, in two more shapes than ADR-127 decision 12's,**
   and `data-model.md` names each one. Neither may hold personal data, because the planned check
   that every personal column is in the data inventory (NFR-261, ADR-075, #251) reads the mapping.
   - A column that the database writes by its default and a constraint reads, such as
     `reporting.baseline.outcome`.
   - A table that only its adapter's native SQL writes and reads, and that holds no aggregate's
     state, such as `messaging.counted_answer`.
3. **A bare `Duration` is stored as `bigint` nanoseconds,** through the shared adapter's
   auto-applied `DurationConverter`, so that every module stores one the same way and exactly. A
   value type that holds a duration keeps its own converter, as a solve limit and
   `reporting.baseline.driver_hours` ([ADR-109]) do.
4. **Every root's load check refuses a gap in its stored lists first.**
   - The kernel's `StoredLists` answers whether any of a set of lists holds a `null` element. It is
     a building block every module's domain uses, as ADR-097's are, and imports only the JDK.
   - Each root's one check of its stored state, which serves both its `@PostLoad` check and every
     command's end check, calls it on every ordered list the root and its owned children hold,
     before it reads an element.
   - Migration `V10` refuses a negative position on all 41 list tables, as Hibernate's own draft of
     the mapping does, and `ListPositionsIT` checks that each list table has the check.
   - `StoredAuditRecord` and `StoredPlanRevision`, which nothing loads yet, get the check with
     their first reader.
5. **An instant a load check compares is cut to the microsecond where it enters the domain.** The
   `Clock` port's `now()` promises it, and its adapter (M0) keeps the promise. `CorrectedTime` cuts
   a device's instants itself. Contact times are rounded by the database, and kept once (ADR-127
   decision 10).
6. **Hibernate batch-fetches collections, 200 roots at a time.** `default_batch_fetch_size` is 200,
   a plan's most stops (NFR-05), so a collection a load check reads costs one statement per 200
   roots. This is not JDBC write batching, which TS §6.3 leaves off.

**What this amends.**

- **[ADR-126]:** a session's contact-only change is told apart by the session's flag inside its one
  `save`, not saved through a port method of its own (1). The shared adapter's converters gain
  `DurationConverter` (3). Loads also batch-fetch collections (6).
- **[ADR-127]:** two more shapes of state that no entity maps (2), and the gap rule beside decision
  1's load checks (4).
- **[ADR-097]:** the kernel's building blocks gain `StoredLists` (4).

## Consequences

- **Easy:** the session, which knows what a command changed, decides whether its save checks the
  version. A use case calls `save` after any command, and `saveRevocation` only for a revocation.
- **Easy:** FR-39 holds in the schema, and `Baseline` carries no field that always holds one value.
- **Easy:** a duration in any module is stored the same way and comes back exactly.
- **Easy:** damage to any stored list, a gap or a negative position, fails at load with
  `StoredStateInvalid` or the database's refusal, as every other damage does.
- **Easy:** a stored instant comes back equal to the one in memory, so a load check can compare
  values computed from instants.
- **Hard:** `ddl-auto=validate` doesn't see a column or table that no entity maps, and
  `just core-schema-draft` leaves them out. A migration rewritten from the draft would drop them, so
  `data-model.md` names each one.
- **Hard:** SQL that reads a duration column reads nanoseconds and has to convert them itself.
- **Hard:** a new ordered list must be added to its root's `StoredLists` call, and a new source of
  instants that a load check compares must cut them. Each root's IT damage table is what shows a
  list left out.
- **Hard:** a call that flushes writes every pending change, so a use case saves a changed
  aggregate before any call that flushes: an `add`, a native statement, which flushes the whole
  persistence context, or a query that reads a changed table. Otherwise `VersionedSave` fails, or
  what the call reads includes the unsaved change. The ports whose methods flush say so.
- **Cost:** a few lines in each root's check, one migration, and a few extra statements where a
  load check now initializes a collection the adapter also fetches.
- **Risk:** a new `Session` command that forgets the flag has its change flushed with no version
  check. `SessionPersistenceIT`'s table of commands is what catches it.

## Alternatives considered

- **ADR-126's separate port method for contact times.** With it, `save` would always check the
  version. Rejected: a revocation's write can be the use case's choice, because it writes one state
  over a live row and the domain has already decided it. A contact save would have to write exactly
  the contact rows a command added or dropped, and nothing else of a session a flush would write
  whole. The session knows which change it holds; a use case would have to know it too.
- **Map `baseline.outcome` as a field that `Baseline` always sets to `COMPUTED`.** Rejected: the
  field would add nothing to the domain, because it exists only for the key.
- **Map `counted_answer` as an `@Immutable` entity in messaging's adapter.** Rejected: the adapter
  reads only a count and whether an answer was counted, and writes with `ON CONFLICT DO NOTHING`. An
  entity would add a class with no reader.
- **Store a duration as PostgreSQL's `interval`.** Rejected: an interval keeps microseconds, so a
  `Duration`'s nanoseconds would not come back.
- **Hibernate's own `hibernate.type.preferred_duration_jdbc_type` for `bigint` durations.**
  Rejected: it is one global setting that no mapping shows, where the converter is a class the
  kernel converters' tests cover and a reader finds by its type.
- **Refuse a gap inside Hibernate,** with a collection type of our own that refuses a `null` as it
  loads. Rejected: it reaches into Hibernate's collection SPI for every list's mapping, where one
  kernel call per root does the same.
- **Keep positions contiguous in the schema,** with a deferred trigger on each list table.
  Rejected: 41 triggers, against one call per root that the ITs already exercise.
- **Store instants as `bigint` nanoseconds,** so nothing is cut. Rejected: `timestamptz` is decided
  for instants ([ADR-088]), and SQL over instants needs it.
- **A `@BatchSize` on each collection,** in place of one default. Rejected: one default covers every
  root and every collection a later change adds.

## Revisit when

- A second root needs a write that leaves its version alone, or a session needs a third
  (decision 1).
- A third shape of state that no entity maps is needed, or one of them would hold personal data
  (decision 2).
- A report or a migration reads a duration column in SQL (decision 3).
- A Hibernate upgrade loads a missing position as an error of its own, or a list needs gaps
  (decision 4).
- An instant a load check compares enters the domain anywhere but the `Clock` or a device report, or
  PostgreSQL keeps nanoseconds (decision 5).
- A load reads more than 200 roots at once, or a profile shows the batch statements as a cost
  (decision 6).

[ISSUE-249]: https://github.com/NaimElijah/DispatchFlow/issues/249
[PR-252]: https://github.com/NaimElijah/DispatchFlow/pull/252
[ADR-088]: ADR-088-israel-local-time-for-every-date-and-schedule.md
[ADR-097]: ADR-097-the-shared-kernel-holds-the-domains-building-blocks.md
[ADR-109]: ADR-109-reporting-computes-exact-on-time-shares-from-current-facts-and-keeps-no-personal-data.md
[ADR-121]: ADR-121-the-application-skeletons-review-settles-its-seams-conflicts-retention-and-scoring-inputs.md
[ADR-126]: ADR-126-jpa-replaces-jooq-and-the-domain-carries-its-mapping.md
[ADR-127]: ADR-127-how-the-aggregates-are-mapped-and-where-their-stored-state-is-tested.md

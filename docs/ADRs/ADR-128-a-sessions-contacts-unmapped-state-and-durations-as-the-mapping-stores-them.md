# ADR-128: A session's contacts, the state no entity maps, and durations, as the mapping stores them

- **Status:** Proposed
- **Date:** 2026-11-23

## Context

[Issue #249][ISSUE-249] audited the core's JPA persistence after [ADR-126] and [ADR-127]. It found
three shapes that [pull request #244][PR-244] built and that no ADR records, or that an ADR records
otherwise:

- **A session's contact times.** ADR-126's Versioning section says that a session's contact times
  are "saved through its own port method, which forces nothing". The code has one
  `SessionRepository.save`. A transient flag on the session, `changedBeyondContactTimes`, tells the
  adapter whether the change went beyond the contact times. If it didn't, the adapter flushes the
  session without `VersionedSave`.
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
  and stores it as `bigint` nanoseconds. ADR-126 lists the shared adapter's converters and does not
  name it, and its own Javadoc cites ADR-127, which does not decide it.

## Decision

1. **A session's change is saved through its one `save`, and the session says which save it needs.**
   - `Session.changedBeyondContactTimes()` is true for a session that was never saved. It is also
     true for one whose tokens, state, generation or password time changed since it was loaded.
   - The private helpers that every such change goes through set the flag. `recordContact` and
     `dropOldContacts` don't.
   - When the flag is false, the adapter flushes the contact times alone, with no version check and
     no forced increment (DL §4.1). When it is true, the adapter saves through `VersionedSave`.
   - `SessionPersistenceIT` checks each command's flag against the version check.
2. **The schema may hold state that no entity maps, in two more shapes than ADR-127 decision 12's.**
   `data-model.md` names each one.
   - A column that the database writes by its default and a constraint reads, such as
     `reporting.baseline.outcome`.
   - A table that only its adapter's native SQL writes and reads, and that holds no aggregate's
     state, such as `messaging.counted_answer`.
3. **A bare `Duration` is stored as `bigint` nanoseconds,** through the shared adapter's
   auto-applied `DurationConverter`, so that every module stores one the same way and exactly. A
   value type that holds a duration keeps its own converter, as a solve limit and
   `reporting.baseline.driver_hours` ([ADR-109]) do.

## What this amends

- [ADR-126] Versioning: a session's contact times are told apart by the session's flag, inside its
  one `save`, not saved through a port method of their own (1).
- [ADR-127] decision 12: two more shapes of state that no entity maps (2).
- [ADR-126]'s list of the shared adapter's converters gains `DurationConverter` (3).

## Consequences

- **Easy:** a use case saves a session one way, whatever its command changed. The session, which
  knows what changed, decides how the change is saved.
- **Easy:** FR-39 holds in the schema, and `Baseline` carries no field that always holds one value.
- **Easy:** a duration in any module is stored the same way and comes back exactly.
- **Hard:** a new `Session` command must set the flag, either through the existing helpers or
  itself. Otherwise its change is flushed with no version check. Its row in `SessionPersistenceIT`'s
  table of commands is what catches a command that doesn't.
- **Hard:** `ddl-auto=validate` doesn't see a column or table that no entity maps, and
  `just core-schema-draft` leaves them out. A migration rewritten from the draft would drop them, so
  `data-model.md` names each one.
- **Hard:** SQL that reads a duration column reads nanoseconds and has to convert them itself.

## Alternatives considered

- **ADR-126's separate port method for contact times.** With it, `save` would always check the
  version, and only a contact use case would call the other method. Rejected: a flush writes every
  change the session holds, so the contact save would have to detach the session and write the
  contact rows by native SQL to be sure it writes nothing else. And the use case, not the session,
  would decide which save a change needs.
- **Map `baseline.outcome` as a field that `Baseline` always sets to `COMPUTED`.** Rejected: the
  field would add nothing to the domain, because it exists only for the key.
- **Map `counted_answer` as an `@Immutable` entity in messaging's adapter.** Rejected: the adapter
  reads only a count and whether an answer was counted, and writes with `ON CONFLICT DO NOTHING`. An
  entity would add a class with no reader.
- **Store a duration as PostgreSQL's `interval`.** Rejected: an interval keeps microseconds, so a
  `Duration`'s nanoseconds would not come back.

## Revisit when

- A `Session` command changes state that the flag's helpers don't cover, or a second root needs a
  save that leaves its version alone (decision 1).
- A third shape of state that no entity maps is needed, or `counted_answer` needs a reader beyond
  its count (decision 2).
- SQL reads durations often enough that converting nanoseconds costs more than an interval's lost
  precision would (decision 3).

[ISSUE-249]: https://github.com/NaimElijah/DispatchFlow/issues/249
[PR-244]: https://github.com/NaimElijah/DispatchFlow/pull/244
[ADR-109]: ADR-109-reporting-computes-exact-on-time-shares-from-current-facts-and-keeps-no-personal-data.md
[ADR-126]: ADR-126-jpa-replaces-jooq-and-the-domain-carries-its-mapping.md
[ADR-127]: ADR-127-how-the-aggregates-are-mapped-and-where-their-stored-state-is-tested.md

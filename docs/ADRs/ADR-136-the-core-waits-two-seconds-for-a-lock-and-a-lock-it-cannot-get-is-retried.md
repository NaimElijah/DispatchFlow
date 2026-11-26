# ADR-136: The core waits at most 2 seconds for a lock, and a call that can't get one is retried

- **Status:** Accepted; extended by ADR-145; amended by ADR-140
- **Date:** 2026-11-24

## Context

PostgreSQL's `lock_timeout` defaults to 0, which means no limit, and the core set none. A
statement that needs a lock another transaction holds waits until that transaction ends, and keeps
its pool connection the whole time. The pool is Hikari's default: 10 connections, and a request
waits up to 30 seconds for one. The review of PR #104 found this (finding 26), and [#106][ISSUE-106]
asked for a decision.

The core waits for these locks:

- the advisory locks that six persistence adapters take with `pg_advisory_xact_lock`, under the keys
  of [ADR-127] decision 9 (the active Admins, a date's capacity, a plan's date, a solve lane, an
  alert's key and a day's message cap);
- the `FOR UPDATE` read of the active Admins (DL §5's locked snapshots);
- the row locks Hibernate takes to update or insert, and the root's lock that [ADR-126]'s
  `VersionedSave` forces. The solve queue's claim uses `FOR UPDATE SKIP LOCKED`, which never waits
  for a row.

Each lock lasts as long as its transaction. [ADR-129] keeps outside calls out of transactions, and
[ADR-063] has a transaction change one aggregate, so most locks are held for milliseconds. Two use
cases hold one longer: `ConfirmOrderImport` and `CopyOrdersFromDate` create all their orders in one
transaction, under the date's capacity lock ([ADR-086] for the import). No use case is built yet:
on 24 November 2026, 401 bodies in the core still throw "To Be Implemented.", so nothing waits
today. The first transaction that holds a lock during slow work would make every other writer of
that aggregate, or every reader of that locked snapshot, wait with no limit.

[NFR-59] asks for 500 ms at the 95th percentile, and [NFR-239] counts an answer over 2 seconds as an
error in the load test. [ADR-126] already answers a lock a save can't get with `StaleVersion`.
[ADR-121] decision 4 answers `StaleVersion` with 412 to a call that sent a version, and re-runs a
call that sent none whole, up to three more times, then answers 503 with `Retry-After`. #162 builds
that retry.

A wait that the timeout ends fails with SQL state `55P03`. The caller sees one of two exception
types, read from Hibernate 7.4.13 and spring-orm 7.0.10, the versions the core resolves:

- Spring's `PessimisticLockingFailureException`, from a Spring Data query, such as
  `activeAdminsUnderLock`; `UserAccountPersistenceIT` and `AlertPersistenceIT` show it;
- JPA's `PessimisticLockException`, from a call on the entity manager itself, such as a native
  query, which `DispatchFlowApplicationIT` shows, or a flush. Hibernate converts both the same way.

A deadlock that PostgreSQL breaks fails with `40P01`, and reaches the caller as the same two types.
PostgreSQL looks for a deadlock after `deadlock_timeout`, 1 second by default.

## Decision

Decided on 24 November 2026. This extends ADR-121 decision 4 to waits for a lock; everything else
in it stands.

1. **Every connection of the core sets `lock_timeout` to 2 seconds,** through the driver's `options`
   property, `-c lock_timeout=2s`, in `application.properties`. The limit applies to each lock a
   statement waits for: row locks, `FOR UPDATE`, advisory locks, and the locks a migration waits for
   while Flyway runs on the core's own data source (until #160 gives it a step of its own). It
   stays above `deadlock_timeout`, so a deadlock is still found as one.
2. **A transaction that needs another limit sets it with `SET LOCAL lock_timeout`,** as its first
   statement, with a comment that says why. A plain `SET` would stay on the pooled connection for
   the next caller, so it is never used. None does this in production; the persistence tests set
   200 ms to keep their runs short.
3. **A save keeps [ADR-126]'s answer:** `VersionedSave` turns a lock it can't get into
   `StaleVersion`. A call that sent a version then gets 412, as for any conflict in a save, and one
   that sent none is re-run.
4. **Any other wait for a lock that fails is re-run too.** The retry that #162 builds re-runs the
   whole call in a new transaction when the call throws `PessimisticLockingFailureException`,
   `PessimisticLockException` or JPA's `LockTimeoutException`, the three types `VersionedSave`
   catches. It runs up to three more times, then answers 503 with `Retry-After`. This holds whether
   or not the call sent a version, because a lock wait outside a save is not a stale version. It
   holds for a deadlock too. The retry matches the type that was thrown, never its causes:
   `VersionedSave` keeps the lock failure as the cause of `StaleVersion`, and that call must still
   get 412.
5. **Who else retries.** A listener retries as it retries `StaleVersion` (DL §11). A schedule, job,
   callback or command-line adapter answers these types as it answers `StaleVersion`, which the
   issue that builds it decides.

## Consequences

- **Easy:**
  - A waiter gives up after 2 seconds per lock instead of waiting until the holder ends.
  - A migration cannot queue behind a long transaction while every later statement queues behind
    the migration.
  - A busy moment in a read under lock gets a retry and then 503, not 500.
- **Hard:**
  - A transaction that holds a lock for more than 2 seconds makes each of its waiters fail. Over
    four attempts, a waiter can spend about 8 seconds before its 503.
  - The timeout ends the waits, not the holder. A transaction that keeps a busy key's lock while it
    sits idle still takes a connection from each waiter for 2 seconds per attempt, so enough calls
    on that key can still fill the pool.
  - A large import or copy that holds a date's lock past 2 seconds makes orders placed on that date
    meanwhile fail.
  - A call that sent a version gets two answers for a lock it can't get: 412 when the wait was in its
    save, and 503 after the retries when it was in a read under lock.
  - A migration whose wait for a lock times out fails the core's startup, because Flyway runs inside
    it until #160; only a restart retries it.
  - The retry has to recognise three exception types, because the adapters don't translate what the
    entity manager throws.
- **Cost:** one property and two integration tests.
- **Risk:** the retry re-runs the whole call, including the outside calls a use case makes before its
  transaction, as the retry already does for `StaleVersion`.

## Alternatives considered

- **5 seconds.** Rejected: a wait past 2 seconds already counts as an error in the load test
  ([NFR-239]), and four attempts could hold a caller for 20 seconds.
- **No timeout until a use case needs one.** Rejected: the first transaction that held a lock during
  slow work would hold every waiter and its connection, with nothing to show it but a full pool.
- **A timeout only on the methods that take an advisory lock or `FOR UPDATE`.** Rejected: Hibernate's
  own row locks wait too, and a new locking method without its own timeout would go unnoticed.
- **A `statement_timeout` instead.** Rejected: it would also stop a long read that holds no lock,
  such as a report's.
- **Answer 500 when a read under lock times out.** Rejected: a busy moment would become an error with
  no retry, while a stale version gets one.
- **Limits on the holder,** such as `idle_in_transaction_session_timeout` or `statement_timeout`.
  Not decided here: #106 asked about waits. They are the next step if the pool fills behind a holder.

## Revisit when

- `ConfirmOrderImport` or `CopyOrdersFromDate` holds a date's lock for more than about a second at
  the size [NFR-60] tests: give it `SET LOCAL lock_timeout`, split its transaction, or cap the rows.
- The load test ([NFR-10]) or the core's logs show lock timeouts, or 503s from the retry.
- The pool fills behind a transaction that holds a lock: limit the holder.
- #160 moves Flyway to a step of its own: choose that step's own lock timeout.

[ADR-063]: ADR-063-a-transaction-changes-one-aggregate-and-every-rule-has-one-owner.md
[ADR-086]: ADR-086-csv-imports-upload-then-confirm-within-an-hour.md
[ADR-121]: ADR-121-the-application-skeletons-review-settles-its-seams-conflicts-retention-and-scoring-inputs.md
[ADR-126]: ADR-126-jpa-replaces-jooq-and-the-domain-carries-its-mapping.md
[ADR-127]: ADR-127-how-the-aggregates-are-mapped-and-where-their-stored-state-is-tested.md
[ADR-129]: ADR-129-transactions-start-only-in-the-service-layer.md
[ISSUE-106]: https://github.com/NaimElijah/DispatchFlow/issues/106
[NFR-10]: ../requirements/requirements.md#nfr-10
[NFR-59]: ../requirements/requirements.md#nfr-59
[NFR-60]: ../requirements/requirements.md#nfr-60
[NFR-239]: ../requirements/requirements.md#nfr-239

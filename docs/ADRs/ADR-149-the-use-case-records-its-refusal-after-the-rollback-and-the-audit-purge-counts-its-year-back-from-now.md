# ADR-149: The use case records its refusal after the rollback, and the audit purge counts its year back from now

- **Status:** Accepted; extended and amended by ADR-150
- **Date:** 2026-11-26

## Context

Issue #180 builds the audit log, `AuditLogService`, and the database function that deletes
expired audit records ([NFR-23], [NFR-93], [NFR-258], [ADR-041], [ADR-075], [ADR-110]). Two
questions were still open, and building the function raised a third.

- **Who writes a refused attempt's record.** [ADR-129] decision 5 says it is written once the
  refused call's transaction has rolled back, with no transaction open. It leaves to #180 which
  class writes it: the inbound adapter that called the use case, or the use case once its
  `Change` has refused.
  - `TransactionRules` rule 7 lets no service without a transaction hold `AuditLog`, as [ADR-129]
    decision 3 lists it.
  - 51 of the 134 services that hold `AuditLog` are one `@Transactional` class. Such a class can't
    write anything after its own rollback.
  - `CallRetry` already writes the record of a lost race, and `AccessInterceptor` that of a 401,
    each after every transaction of the call has ended ([ADR-142], [ADR-147]).
- **Where "older than a year" is measured from.** [ADR-110] decision 2 defines a record as past its
  retention once the time is after the same Israel local time a year after the record. The purge
  function must compute its own cutoff ([ADR-041]) and match that rule.
  - Near 29 February the rule can't be one cutoff. A record made on 28 February 2028 at 12:00:01
    is past its year at 28 February 2029 12:00:01. One made on 29 February 2028 at 11:00 is past
    its year at 28 February 2029 11:00, because 2029 has no 29 February.
  - So at 12:00 on 28 February 2029 the rule deletes the newer record and keeps the older one. No
    "delete everything made before X" does that, so the function can't use the `at` index and still
    match.
- **Daylight saving time.** A local time a year back can fall in the hour the clocks skipped or the
  hour they repeated, and then stands for two instants an hour apart. The later one is too late: a
  cutoff there deletes a record made in that hour up to an hour before its year is up. Yet both
  sides pick it by default.
  - On `postgres:18.6`, `AT TIME ZONE` reads both kinds of time as standard time (`+02:00`), the
    later instant.
  - `java.time` moves a skipped time forward, which is the later instant too. In a repeated hour it
    keeps the offset of the time it counted from.
- **One function.** [ADR-041] and [ADR-075] give `app` `EXECUTE` on one audit function, the purge,
  and nothing else on Audit.

## Decision

Decided on 26 November 2026.

1. **The use case writes its refused attempt's record, after its `Change` has refused.**
   - The use case opens no transaction ([ADR-129] decision 3). It calls its `Change`, catches the
     `RuleViolation` or `RuleViolations` that leaves it, and only then calls `AuditLog.refused`.
   - The record carries the refusal's code and the records the attempt names. The call is the
     use case's `CallContext` and the time is its `Clock`'s.
   - It then rethrows the refusal, or throws the substitute the use case answers with in its
     place, such as `SignInRefused` or `LinkVoided`. So the record keeps the specific code, and the
     answer doesn't reveal it ([NFR-145]).
   - A refusal raised outside any transaction, before the `Change`, is written the same way, by
     the use case that raised it. `SolverNotOffered` and `UploadProof`'s refusals about the stored
     image are such refusals.
   - **Audit's own refusals are bugs.** Every part of a record comes from the server, so
     `AuditLogService` rethrows Audit's refusal of a malformed part as an `IllegalStateException`,
     with the refusal as its cause. A use case's `catch` of its `Change`'s `RuleViolation` never
     takes it for the change's own refusal.
   - A failed write of the refused record is thrown, with the refusal added as suppressed, so the
     call answers 500 and the failure is loud ([ADR-110], Risk).
   - This settles [ADR-129] decision 5.
2. **A use case whose transaction can refuse is two classes.**
   - It is the use case and its `<UseCase>Change`, as [ADR-129] decision 3 already makes a use
     case with outside calls. This extends that decision.
   - A one-class service that can refuse is split in the pull request that builds its body. Until
     then the skeletons keep their shape.
   - A `Change` may still write `AuditLog.refused` in its own transaction when no refusal leaves
     it, so that its transaction commits the record on purpose. Two skeletons do:
     - `RequestPasswordRecoveryChange` answers every recovery request alike;
     - `RefreshSessionChange`'s refused refresh revokes the session.

     Such a refusal must come before the `Change` changes any entity it loaded, or the commit stores
     that change beside the record.
3. **`TransactionRules` checks where a success and a refusal are written.**
   - Rule 7 lets a service without a transaction hold `AuditLog`.
   - **Rule 9:** only a transactional class in a module's `application.service` calls
     `AuditLog.succeeded`, or refers to it. The class must sit at the default propagation, and the
     method must not be read-only. So a success is recorded in its change's transaction and commits
     with it ([NFR-23], TS §6.2).
   - **Rule 10:** no transactional class calls `AuditLog.refused` but a `Change` that decision 2
     lists by name. So a one-class service can't record its refusal into the transaction the refusal
     rolls back. A new `Change` of that kind joins the list in review.
   - Each rule has fixtures it must flag, and a nested class counts as the class it sits in.
   - **At runtime,** `JpaAuditRecords.append` refuses to run outside a read-write transaction. A
     read-only one never flushes, so a record written there would be lost with no error.
4. **An adapter writes only the refusals no use case sees.** These are the 401 of a changing call
   (`AccessInterceptor`), a lost race (`CallRetry`), #171's 403, and a file the reader refuses
   before the use case runs (`PreviewOrderImport`). A race is no `RuleViolation`, so a use case
   never records one: `StaleVersion` and a failed lock wait stay the call wrapper's.
5. **A record is past its retention once it was made before the same Israel local time a year
   earlier.**
   - `AuditRecord.retentionCutoff(now)` takes `now` to `Asia/Jerusalem`, goes back
     `AuditRecord.RETENTION`, and returns that instant. `isPastRetention(now)` is
     `at.isBefore(retentionCutoff(now))`.
   - A record exactly a year old is still kept.
   - A record of 29 February is kept through 28 February of the next year, and is past its
     retention from 1 March at 00:00 Israel time. This amends [ADR-110]'s reading, which kept it
     until the same time on 28 February.
   - A local time the clocks skipped or repeated stands for the earlier of its two instants, the
     time read with the summer offset. So near a clock change a record is kept up to an hour
     longer, and never deleted before its year is up.
6. **The purge and its cutoff are two functions of migration `V12`, both owned by
   `dispatchflow_owner`.**
   - **`audit.retention_cutoff(asked_at)`** is decision 5's rule in SQL. It is `STABLE`, with a
     SQL-standard body, so its names are bound when it is created.
     - It takes the least of three readings: the local time itself, and the local time an hour
       either side, moved back by that hour.
     - Israel's clocks move by an hour, months apart, so that least is the earlier instant in a
       skipped or repeated hour, and the one instant elsewhere.
     - A test compares it with the code at every clock change and leap day up to 2036.
   - **`audit.purge_expired_records()`** deletes every record made before
     `audit.retention_cutoff(now())`, with its named records by cascade, and returns how many it
     deleted.
     - It takes no parameter, so no caller can pass a date ([ADR-041]).
     - It runs with its owner's rights (`SECURITY DEFINER`), with `search_path` fixed to
       `pg_catalog, pg_temp`.
     - Its cutoff is a `STABLE` expression, so its `DELETE` can read the `audit_record_at` index.
   - **Grants.** `EXECUTE` is revoked from `PUBLIC` on both. The purge alone is granted to `app`,
     so `app` still executes one audit function, as [ADR-041] and [ADR-075] say. The cutoff is
     granted to nobody: only the purge, running as its owner, calls it.
7. **The purge's caller waits for the nightly retention run.** `PurgeExpiredAuditRecords` and
   `AuditRecords.purgeExpired` stay unbuilt until [ADR-121] decision 9 builds that run. The
   guarantee lives in the schema, and the tests call the function directly.
8. **`AuditLog` is a bean, injected plainly.** `AuditConfiguration` imports `AuditLogService`.
   `CallRetry` and `AccessInterceptor` take `AuditLog` in their constructors instead of looking it
   up when they write. So a 401 on a changing call, and an audited race, answer their own status
   with their record stored.

**What this amends and extends.**

- **[ADR-110]:** decision 2's reading of 29 February, and the cutoff the database computes
  (decision 5). It is amended.
- **[ADR-129]:**
  - decision 5's open question is settled (decision 1);
  - decision 3 is extended to a use case whose transaction can refuse (decision 2);
  - decision 3's "other than `AuditLog`", among what a use case without a transaction may hold, is
    amended (decision 3).
- **[ADR-142] and [ADR-147]:** their lazy lookup of the audit log, and the 500 their Risks left
    until #180, are replaced by plain injection (decision 8). They are amended.

## Consequences

- **Easy:**
  - A refused record names what the attempt named, such as the plan, so an Admin who filters by
    a record also finds the attempts refused on it ([FR-126]).
  - Sign-in and the links record the specific reason, while the answer stays the same for all.
  - The same code records a refusal whether a web call, a CLI step or a job ran the use case.
- **Easy:**
  - The code and the database agree on every record's retention, by one rule, which never deletes
    a record before its year is up.
  - The purge can read an index, not every row.
- **Easy:** the build fails on a success recorded outside its change's transaction (rule 9), and
  on a refusal recorded inside one (rule 10).
- **Hard:** most changing use cases are two classes when built, and both need tests.
- **Hard:** each use case must record each of its refusals. No rule can see a missing `catch`, so
  the use case's application tests check each refusal's record, and review checks them.
- **Cost:** none in money. The whole change is the audit module, two web classes, one migration,
  the transaction rules and their fixtures.
- **Risk:** a `Change` on decision 2's list that records a refusal and then lets one leave loses
  the record in the rollback. Its tests catch that; no rule does.
- **Risk:** every changing call refused for want of a signed-in caller now writes a row kept for a
  year, and no rate limit counts such calls yet ([ADR-071] is not built). A flood of them could
  hold the connection pool, and the row count grows with it. No operation is served yet, but this
  needs a limit before the first deployment that faces the internet.
- **Risk:** `app` can't change or delete a record, but it may insert. A compromised core could
  backdate a record, or add a named record to an old one. The database keeps the trail
  append-only, not unforgeable.

## Alternatives considered

- **The web call wrapper records every refused call.** It would need no code in any use case, and
  no API call could forget its record. Rejected:
  - the wrapper doesn't know the records an attempt names, so a refused record would name none,
    and NFR-93 wouldn't hold for refusals;
  - sign-in's specific code would need a new kernel type to carry it to the wrapper;
  - a refusal in a CLI step or a job would go unrecorded;
  - the domain document (§4.8) and [ADR-110] give the job to the application layer.
- **Each controller records its use case's refusal.** Rejected: the same `catch` in every
  controller, which still doesn't record a CLI step's refusal.
- **`@Transactional(noRollbackFor = RuleViolation.class)`,** so that a one-class service commits
  its record and rethrows. Rejected: anything the service changed before the refusal would commit
  too. Hibernate flushes a changed managed entity at commit, whether or not it was saved.
- **Keep "a year after the record", with SQL computing each record's own end.** Rejected: no single
  cutoff exists, so the purge reads every row or a pre-filter whose bound must be proved. The
  29-February rule it keeps moves at most a day, once every four years.
- **Take the later instant of a skipped or repeated local time,** as PostgreSQL's own reading
  does. Rejected: it deletes records up to an hour early, which the guarantee forbids.
- **Delete in batches,** so a backlog doesn't become one long statement. Rejected for now: one
  company's audit trail grows by a day's records a night, and a batch needs a caller that loops,
  which waits for the nightly run.

## Revisit when

- A use case must record a refusal that its `Change` can't hand back, such as one raised after its
  commit.
- An audited operation must record something a use case can't name, such as a body the contract
  refused before the handler ran.
- Legal advice ([Q-42]) changes the retention period, or makes it differ by record.
- PostgreSQL or the JDK changes how a skipped or repeated local time is read, or Israel's clocks
  move by more than an hour; the comparison test reports either.
- A purge must delete a backlog, such as after nights the run missed, or a statement timeout
  bounds it; then it deletes in batches.
- The first operation is served on a deployment that faces the internet, before which the
  unsigned changing calls need a rate limit.

[ADR-041]: ADR-041-one-postgresql-schema-per-module-read-and-written-through-jooq.md
[ADR-071]: ADR-071-api-rate-limits-in-process-with-bucket4j.md
[ADR-075]: ADR-075-retention-deletes-records-and-anonymizes-people-in-place.md
[ADR-110]: ADR-110-audit-records-hold-only-ids-codes-enums-numbers-and-instants-record-refusals-and-are-kept-a-year.md
[ADR-121]: ADR-121-the-application-skeletons-review-settles-its-seams-conflicts-retention-and-scoring-inputs.md
[ADR-129]: ADR-129-transactions-start-only-in-the-service-layer.md
[ADR-142]: ADR-142-the-call-wrappers-retry-its-400s-and-its-error-endpoint-as-162-built-them.md
[ADR-147]: ADR-147-the-stand-in-condition-sits-in-the-root-package-and-a-refused-access-token-answers-one-code.md
[FR-126]: ../requirements/requirements.md#company-users-and-roles
[NFR-23]: ../requirements/requirements.md#nfr-23
[NFR-93]: ../requirements/requirements.md#nfr-93
[NFR-145]: ../requirements/requirements.md#nfr-145
[NFR-258]: ../requirements/requirements.md#nfr-258
[Q-42]: ../requirements/requirements.md#open-questions

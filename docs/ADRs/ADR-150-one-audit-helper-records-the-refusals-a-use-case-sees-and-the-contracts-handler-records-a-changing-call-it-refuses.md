# ADR-150: One Audit helper records the refusals a use case sees, and the contract's handler records a changing call it refuses

- **Status:** Accepted
- **Date:** 2026-11-27

## Context

A review of #180 after it merged checked [ADR-149] against the use cases still to be built. It
found four kinds of refused changing call that nobody records. [NFR-23] wants one record for every
changing call, and [ADR-110] decision 1 audits every failure, adapter codes such as `STALE_VERSION`
included.

- **A not-found.** A changing call that names an ID with no record answers 404 `NOT_FOUND`.
  [ADR-149] decision 1 has the use case catch only a `RuleViolation` or `RuleViolations`, and no
  adapter in decision 4 records a `NotFound`.
- **A conflict the use case answers itself.** `RefreshSession`, on a second conflict, and
  `RequestPasswordRecovery`, when a concurrent request won, never let their `StaleVersion` leave.
  So the call wrapper, which records a race ([ADR-140] decision 6), never sees it, and the record
  their `Change` wrote rolls back with the conflict.
- **A malformed request.** Spring reads and validates a handler's arguments before the call wrapper
  runs the handler. A signed-in changing call whose body or parameters break the contract answers
  400 or 413 from the contract's handler, and nothing records it. [ADR-149] named this case as a
  reason to revisit.
- **About 134 hand-written blocks.** Under [ADR-149] decision 1, every changing use case writes the
  same block: catch the refusal, record it, rethrow it, and add it as suppressed if the write
  fails. No rule can see a missing one.

The same review found two flaws in [ADR-149] itself.

- **Decision 1's 500 didn't hold.** Spring matches an exception handler by the thrown type and then
  by each of its causes, and binds the cause it matched. So the `IllegalStateException` that
  `AuditLogService` throws for a malformed part, with Audit's `RuleViolation` as its cause, answered
  422 with Audit's internal code, logged at INFO.
- **Decision 2's precondition contradicts a class it lists.** It says a listed `Change` records its
  refusal "before the `Change` changes any entity it loaded". `RefreshSessionChange` records its
  refusal after the exchange has revoked the session, on purpose.

## Decision

Decided on 27 November 2026.

1. **One helper records each refusal a use case sees.**
   - `audit.application.api.Refusals.recordedBy(auditLog, call, at, records, attempt)` runs the
     attempt: the use case's `Change`, and any check the use case makes before it.
   - When a `RuleViolation`, `RuleViolations` or `NotFound` leaves the attempt, the helper writes
     `AuditLog.refused` with the refusal's code and the records the attempt names, then rethrows
     the refusal. A failed write is thrown in its place, with the refusal added as suppressed.
   - A use case writes no `catch` of its own to record. It catches the rethrown refusal only to throw
     the substitute it answers with, such as `SignInRefused`.
   - It is a static helper over the port, not a method of `AuditLog`. `AuditLogService` is
     transactional at class level, so a method of the bean would open one transaction around the
     `Change`. The `Change` would join it, and the refusal's rollback would take the record with
     it.
   - It records no race: a `StaleVersion` or a failed lock wait passes through untouched, for the
     call wrapper ([ADR-140] decision 6).
   - This amends [ADR-149] decision 1, whose `catch` it writes once.
2. **A not-found is a refusal like any other.** The helper records it with `NOT_FOUND`, naming the
   records the attempt names.
3. **A use case that answers a conflict itself records it.** When a `StaleVersion` never leaves the
   use case, the use case writes `AuditLog.refused` with `STALE_VERSION` once the rollback is done.
   The record names what the use case knows. `RefreshSession` and `RequestPasswordRecovery` do
   this. Every other race stays the call wrapper's. This amends [ADR-140] decision 6's "A use case
   never audits a `StaleVersion`", and extends [ADR-149] decision 4.
4. **The contract's handler records a changing call it refuses before the handler runs.**
   - `ContractErrorHandler` writes `AuditLog.refused` for a 4xx it answers, with the answer's code
     and no records, when all of these hold:
     - the request is in its own dispatch, not an error dispatch;
     - a caller is kept, which means a call to an annotated handler under `/api/v1` that passed
       the access interceptor;
     - the method may change something: any method but `GET`, `HEAD`, `OPTIONS` and `TRACE`;
     - the call wrapper hasn't started the handler.
   - So a 406 raised while writing an answer the handler already produced writes no second record.
     A call with no principal answered 401 before its body was read, and its record is the
     interceptor's.
   - A failed write answers 500 `UNEXPECTED_ERROR` at ERROR, with the contract's exception added as
     suppressed. Thrown from an exception handler, it would leave only a WARN line, and Spring would
     then answer the original exception itself.
   - This extends [ADR-149] decision 4.
5. **Rule 10 covers the helper, and its list keeps two `Change`s, by a stated test.**
   - No transactional class calls `Refusals`, the listed `Change`s included: inside a transaction,
     the refusal it rethrows rolls its own record back.
   - A `Change` joins the list only when no exception leaves it. Its transaction then commits, and
     the commit carries the record and only what the refusal itself must store. Both listed
     `Change`s pass:
     - `RequestPasswordRecoveryChange` stores nothing beside its record;
     - `RefreshSessionChange` stores the revocation, which commits with its record or not at all
       ([NFR-23], [NFR-88]).
   - Each `Change` alone knows the account or session its record names, which its use case doesn't.
   - This replaces [ADR-149] decision 2's "Such a refusal must come before the `Change` changes any
     entity it loaded".
6. **A refusal answers as one only when it is the exception thrown.** Each refusal handler of
   `KernelErrorHandler` also takes the exception thrown, as its race handler already does. It
   answers 500 at ERROR when the refusal is only a cause, so [ADR-149] decision 1's bug answers 500.

The tech stack's §6.2 and the domain document's §2.2 and §4.8 are corrected to point at [ADR-149]
and this ADR. [#299][ISSUE-299] tracks [ADR-149]'s Risk of a flood of unsigned changing calls,
and blocks any deployment beyond loopback.

**What this amends and extends.**

- **[ADR-149]:** decisions 1 and 2 are amended (decisions 1 and 5), and decision 4 is extended
  (decisions 3 and 4).
- **[ADR-140]:** decision 6's rule that a use case never audits a `StaleVersion` is amended
  (decision 3).

## Consequences

- **Easy:**
  - A use case records every refusal it sees with one call, and a not-found among them.
  - [NFR-23] holds for a 404, for a conflict a use case answers itself, and for a malformed
    changing request.
- **Easy:** the build fails on the helper inside a transaction (rule 10).
- **Easy:** a bug that wraps a refusal answers 500 and pages, never a refusal's 4xx.
- **Hard:** a refusal that leaves no exception, such as a listed `Change`'s or a conflict a use
  case answers, is still written by hand. Its tests and review check it.
- **Hard:** a malformed signed-in changing call now writes a row kept for a year. One without a
  principal still answers 401 first.
- **Cost:** none in money: one helper, one handler's record, a mark on the request, and the rule's
  fixtures.
- **Risk:** rule 10 reads one class's own calls. A module's own intermediary that writes
  `AuditLog.refused` for a transactional caller is left to review.

## Alternatives considered

- **A default method on `AuditLog`.** Rejected: the bean's class-level transaction would wrap the
  `Change`, and the refusal's rollback would take its record.
- **The helper also records a `StaleVersion`.** Rejected: the call wrapper records the race it
  answers, after its retries, so the use case would write one more record per try.
- **The call wrapper records the malformed request, around the reading of the arguments.**
  Rejected: the contract's handler chooses the answer's code, and a second copy of that mapping
  would drift.
- **Drop rule 10's two listed `Change`s,** so each returns its refusal and the use case records it
  after the transaction. Rejected: each `Change` would have to hand out the account or session it
  found, and a revocation could commit without its record.
- **Leave a not-found or a malformed request unaudited.** Rejected: [ADR-110] audits every
  failure, and a probe for another person's IDs would leave no trail.

## Revisit when

- A use case must record a refusal that leaves its attempt as another exception type.
- A third `Change` asks to join rule 10's list.
- [#299][ISSUE-299]'s limit decides that a refused call without a token writes no record.

[ADR-110]: ADR-110-audit-records-hold-only-ids-codes-enums-numbers-and-instants-record-refusals-and-are-kept-a-year.md
[ADR-140]: ADR-140-the-app-wide-web-package-sits-above-the-modules.md
[ADR-149]: ADR-149-the-use-case-records-its-refusal-after-the-rollback-and-the-audit-purge-counts-its-year-back-from-now.md
[ISSUE-299]: https://github.com/NaimElijah/DispatchFlow/issues/299
[NFR-23]: ../requirements/requirements.md#nfr-23
[NFR-88]: ../requirements/requirements.md#nfr-88

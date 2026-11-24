# ADR-057: Concurrent changes to a plan use optimistic locking, and one re-optimization runs at a time

- **Status:** Accepted; amended by ADR-121
- **Date:** 2026-11-13

## Context

Decided on 2026-11-13: the rule that the requirements mark "ADR
pending: concurrent changes to a plan" ([BR-38]). Two requirements meet there. A plan has at most one
re-optimization running at a time ([BR-38]). Two dispatchers cannot silently overwrite each other's
changes to a plan: an out-of-date change is refused with its reason ([BR-76]).

`Plan` is the only aggregate with several writers on a normal day: dispatchers (edit, publish,
accept, hand over) and six automatic writers. They are the solve-result listener, the listeners for
cancelled and for rescheduled orders, Live Operations' late-outcome call, the route re-check job and
the midnight job, which completes or abandons a plan (`complete`, `abandon`).
It sees a few writes a minute at its peak ([DL §11][DL-11]). The 15 phones' positions are records
with no version, and their reports go to 200 separate `Delivery` aggregates, never to `Plan`.

The domain document's mechanism for [BR-76] is a version on every aggregate root, checked on every
save ([DL §3.1][DL-31], [DL §3.6][DL-36]). This ADR records it and the rule for [BR-38] beside it.

## Decision

- **Every aggregate root has a version, and `save` checks it.** The repository updates with
  `WHERE id = ? AND version = ?`. When no row matches, the change is out of date and fails with
  `STALE_VERSION`. Child tables carry no version of their own ([TS §6.3][TS-63]).
- **The API passes the version through.** A read returns `ETag: "<aggregate version>"`, a change
  sends `If-Match`, and a stale version answers 412 with the code `STALE_VERSION` ([PL §6.2][PL-62]).
  The dispatcher refetches and decides again ([DL §11][DL-11]).
- **A listener that loses the race retries at once, up to three times.** It then fails the
  event publication, and Spring Modulith's registry resubmits it, at most five times, before the team
  is alerted ([PL §11.2][PL-112], [PL §11.3][PL-113]).
- **The midnight job retries the same way.** When the version check refuses its `complete` or
  `abandon`, the job reloads the plan and retries, up to three times like a listener, then alerts the
  team. A dispatcher's save that beat it, or the other way round, ends in the same refusal as for any
  other writer.
- **One running re-optimization per plan is a rule over a set of jobs,** so a version check does not
  hold it. Its owner is `SolveJob.queue`, backed by a partial unique index on running
  re-optimization jobs per plan. A second request fails with `ReoptimizationAlreadyRunning` and the
  reason ([DL §5][DL-5]). An open preview does not count ([BR-38]).
- **A transaction changes one existing aggregate,** so the one version on that aggregate is the only
  check a use case needs ([ADR-063]).
- **The other aggregates follow the same rule,** with the outcome [DL §11][DL-11] gives each: a
  `Delivery` retry carries the same idempotency key and gets the stored answer ([NFR-65]); otherwise
  the version decides and the first outcome wins. A `SolveJob`'s loser finds it ended and stops.

## Consequences

- **Easy:** no lock is held across a dispatcher's read and the change that follows, so a slow
  dispatcher blocks nobody. The check costs one extra predicate on the update.
- **Easy:** one mechanism for every aggregate, tested the same way, and one error code (`STALE_VERSION`)
  for both apps to translate.
- **Hard:** `Plan` is one aggregate for the whole date, so any two writers conflict, even when they
  touch different routes. A dispatcher editing route A can be refused because a late outcome was
  recorded on route B.
- **Hard:** every listener must tolerate a failed save. Its reaction must be idempotent, and
  its test publishes the event twice ([PL §11.2][PL-112]).
- **Cost:** the optimistic locking and the writes of child rows are written by hand with jOOQ
  ([ADR-041]), and `ETag` and `If-Match` are part of the client contract ([ADR-043]).
- **Risk:** if `Plan` writes become frequent, retries pile up. The design counts a few writes a
  minute at peak ([DL §11][DL-11]); that is an estimate, not a measurement.

## Alternatives considered

- **Last write wins.** Rejected: it is what [BR-76] forbids.
- **A pessimistic lock on the plan** (a row lock or an advisory lock). A database lock lasts one
  transaction, which is milliseconds ([DL §11][DL-11]), and a dispatcher's conflict spans two
  requests, the read and the change. Only a version carries the first across to the second. The same
  reasoning rejected an advisory lock per order for the cancel-versus-report race, which it would not
  settle offline ([DL §8][DL-8]).
- **Merge two edits automatically.** Rejected: every edit is judged by the validator on the version
  it was made from, and `Plan` refuses an evaluation of another version (`StaleEvaluation`,
  [DL §4.4][DL-44]).
- **A flag on `Plan` for the running re-optimization.** Rejected: jobs end independently, and the plan
  must not be written for every job's phase ([DL §5][DL-5]).

## Revisit when

- Dispatchers on pilot days see `STALE_VERSION` on two or more saves a day that do not follow a
  hand-over or an accepted re-optimization. Then consider smaller aggregates for routes, which [DL §16][DL-16]
  weighed and rejected for the rules that span the date.
- The measured writes to `Plan` at peak exceed the few a minute that [DL §11][DL-11] assumes, or a
  listener's retries run out in the logs more than once on a pilot day. The figure is a proposal.

[ADR-041]: ADR-041-one-postgresql-schema-per-module-read-and-written-through-jooq.md
[ADR-043]: ADR-043-generated-code-from-both-contracts-and-problem-details-errors.md
[ADR-063]: ADR-063-a-transaction-changes-one-aggregate-and-every-rule-has-one-owner.md
[BR-38]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-76]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[NFR-65]: ../requirements/requirements.md#nfr-65
[DL-11]: ../architecture/domain_layer/domain_layer_design.md#11-concurrency
[DL-16]: ../architecture/domain_layer/domain_layer_design.md#16-why-it-looks-like-this
[DL-31]: ../architecture/domain_layer/domain_layer_design.md#31-aggregates
[DL-36]: ../architecture/domain_layer/domain_layer_design.md#36-repositories
[DL-44]: ../architecture/domain_layer/domain_layer_design.md#44-planning
[DL-5]: ../architecture/domain_layer/domain_layer_design.md#5-rules-over-sets-of-aggregates
[DL-8]: ../architecture/domain_layer/domain_layer_design.md#8-when-a-fact-contradicts-the-plan
[TS-63]: ../architecture/tech_stack.md#63-persistence
[PL-62]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#62-core-api-conventions
[PL-112]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#112-rules-every-use-case-follows
[PL-113]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#113-event-publication-making-retries-happen

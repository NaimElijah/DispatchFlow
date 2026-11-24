# ADR-059: Each published plan revision is stored as rows, one per stop

- **Status:** Accepted; extended by ADR-121
- **Date:** 2026-11-14

## Context

Decided on 2026-11-14: how a published plan keeps its history.
A published plan changes only by a new revision that records its cause ([BR-77], [DL §9.1][DL-91]).
Revisions come from an accepted re-optimization, a hand-over, a removed stop (a cancelled or
rescheduled order) and a late outcome. Revision 1 is the publication.

Four requirements read that history:

- **Judging a late report** ([BR-110]): whether a stop was on the driver's route is judged from the
  route's history at the report's corrected time ([BR-114]), not from today's route.
- **The history of accepted re-optimizations** ([FR-104]): who accepted each, when, and what changed.
- **Route-change diffs** ([FR-249]): either app shows which stops a change added, removed or
  reordered.
- **History stays** ([BR-37]): a completed plan keeps the values it was solved and reported with.

`Plan` is one aggregate for the date, with a version check on every save ([ADR-057]). Every change
to it loads it whole ([DL §3.1][DL-31]). The implementation plan listed "plan revisions stored as
rows" among the decisions to record ([PL §25][PL-25]), and sketched the tables ([PL §12][PL-12]).

## Decision

- **Two tables hold the history,** in Planning's schema ([PL §12][PL-12]):
  - `plan_revision (plan_id, no, effective_at, cause, proposal_id, by_user_id)`, keyed on
    `(plan_id, no)`: when the revision took effect, why, the proposal it cites if any, and who made
    it ([FR-104], [BR-77]);
  - `plan_revision_stop (plan_id, revision_no, stop_id, route_id, vehicle_id, driver_id, order_id,
    attempt, seq, planned_arrival)`, keyed on `(plan_id, revision_no, stop_id)`: every stop of the
    revision, with the route, vehicle and driver it was on ([BR-110], [FR-249]).
- **Rows are appended and never changed.** The plan holds only the number of its current revision.
- **`Plan` never loads past revisions.** A change that needs a revision fills a pending
  `PlanRevision` snapshot, and the repository appends it in the same transaction ([DL §4.4][DL-44]).
  A plan with thirty revisions costs the same to change as a plan with one.
- **A late report is judged by one query:** was this stop on this driver's route in the revision
  that was in force at the corrected time? The revision in force is the highest `no` whose
  `effective_at` is not after that time ([PL §12][PL-12]).
- **Planning owns the query.** Live Operations asks Planning's `RouteMembership` API and never reads
  Planning's tables. History for screens and reports comes through `PlanHistory` ([DL §4.4][DL-44]).

## Consequences

- **Easy:** [BR-110], [FR-104] and [FR-249] read the same rows. No document is parsed to find a
  stop.
- **Easy:** the cost of a change does not grow as the day goes on, because only the current routes
  load.
- **Hard:** every revision writes a row for each stop of the plan. A plan is limited to 200 stops
  ([NFR-05]), so a revision adds a bounded number of rows, but the table grows with every
  revision of every plan.
- **Hard:** the repository must append the snapshot in the same transaction as the change, or the
  history and the routes disagree.
- **Cost:** two tables, one index-backed query, and a migration that creates them; the schema is
  written by hand with jOOQ ([ADR-041]).
- **Risk:** `effective_at` decides which revision judged a report, so it depends on the corrected
  time of the report being right ([BR-114]) and on the server's clock at publishing and at each
  change.

## Alternatives considered

- **One JSON snapshot per revision.** Rejected. A report would be judged by reading a
  whole document and searching it for the stop, where rows answer with one indexed lookup on the
  plan, the stop and the driver. Diffs between revisions would need the same parsing. The sources
  state the query, not a measured comparison.
- **Keep the revisions inside the `Plan` aggregate.** Rejected: `Plan` loads whole, so every change
  would read the history too, and a plan with thirty revisions would cost more than one with one
  ([DL §4.4][DL-44]).
- **Judge a late report from the current route.** Rejected: [BR-110] asks about the route at the
  report's corrected time, and a stop that moved since would be judged wrongly ([DL §8][DL-8]).

## Revisit when

- The `plan_revision_stop` table and its indexes grow until disk or backup time on the single VM
  ([NFR-14]) becomes a problem. Then consider keeping only the changed stops of each revision.
- The late-report query is among the slow queries in the load test ([NFR-10]). Then revisit its
  indexes before the table design.

[ADR-041]: ADR-041-one-postgresql-schema-per-module-read-and-written-through-jooq.md
[ADR-057]: ADR-057-concurrent-changes-to-a-plan-use-optimistic-locking.md
[BR-37]: ../requirements/requirements.md#orders-records-and-history
[BR-77]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-110]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-114]: ../requirements/requirements.md#driver-reports-and-outcomes
[FR-104]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-249]: ../requirements/requirements.md#publishing-and-the-phone-app
[NFR-05]: ../requirements/requirements.md#nfr-05
[NFR-10]: ../requirements/requirements.md#nfr-10
[NFR-14]: ../requirements/requirements.md#nfr-14
[DL-31]: ../architecture/domain_layer/domain_layer_design.md#31-aggregates
[DL-44]: ../architecture/domain_layer/domain_layer_design.md#44-planning
[DL-8]: ../architecture/domain_layer/domain_layer_design.md#8-when-a-fact-contradicts-the-plan
[DL-91]: ../architecture/domain_layer/domain_layer_design.md#91-plan-br-12
[PL-12]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#12-database-schema
[PL-25]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#25-decisions-to-record-as-adrs

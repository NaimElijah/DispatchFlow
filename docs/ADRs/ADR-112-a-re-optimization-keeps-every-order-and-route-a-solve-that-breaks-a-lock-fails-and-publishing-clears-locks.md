# ADR-112: A re-optimization keeps every order and route, a solve that breaks a lock fails, and publishing clears locks

- **Status:** Accepted
- **Date:** 2026-11-15

## Context

A review of the whole domain layer on 2026-11-15 found that Planning's re-optimization path and its
locks could lose orders or trap a plan, and that a few smaller rules were left open:

- **Accepting a proposal lost stops already reported.** The optimizer keeps a stop reported done or
  in progress off every route of a proposal ([BR-10], [BR-108], [ADR-105] point 2). `Plan.accept`
  built each route only from the proposal's stops, so a delivered stop was on no route and in no
  drop ([BR-07], [FR-268], [BR-83]). A vehicle withdrawn again lost the stops its withdrawn route
  kept, against [ADR-101] point 30. A route the proposal left out without withdrawing it left the
  plan, so a late fact for it was refused with `RouteNotInPlan` ([DL §8][DL-8]). And a stop reported
  since the basis that the proposal kept after the reported ones was refused as outdated, though
  BR-83 says that report stands.
- **A lock the solve couldn't keep trapped the plan.** `applySolveResult` refused a result that
  broke a lock only after the job had succeeded. The optimizer has no lock rule yet ([ADR-105] point
  8), so a retry could not succeed, the watchdog could not expire an ended job, and the plan stayed
  *solving* until it was abandoned at midnight ([FR-147], [FR-238]). A daily result also got no
  check of [BR-07] and [BR-39], though a proposal does.
- **Locks outlived publishing.** A published plan kept its locks, and accepting a proposal neither
  kept nor cleared them, though a lock binds solves only ([FR-147]).
- **A confirmed hand-over left [BR-105] open.** Where breaks are reserved, one late return breaks
  both [BR-06] and BR-105, and `handOver` waived only BR-06 ([FR-304], [ADR-101] point 28).
- **A dropped order put back onto another date trapped the old date's plan.** The old date's ready
  plan kept the drop, and publishing then refused the order as changed since the solve ([FR-75],
  [BR-71]). No edit removes a drop.

The domain also left three smaller rules to its implementer: what a command does with a `now` that a
clock stepped back puts before an earlier recorded time, which `restore` then refuses; how many
edits the undo journal keeps ([FR-145]); and what a sequence lock on an empty route keeps.

This ADR amends [ADR-101] points 18, 28, 30 and 37, and [ADR-105] point 8. The rest of each stands.

## Decision

On 2026-11-15 the owner, Naim Elijah, decided:

1. **Accepting a re-optimization keeps every route in force, with the stops the proposal neither
   serves nor drops, and refuses a proposal that loses an order** ([BR-07], [BR-10], [BR-83],
   [BR-108], [FR-268]).
   - Each route keeps its reported prefix, the stops the proposal neither serves nor drops, ahead of
     the proposal's stops for its vehicle.
   - A route the proposal gives no route stays with its prefix, or with no stops left. It is marked
     withdrawn when the proposal withdraws its vehicle, and otherwise stays in force, so a late fact
     can still name it.
   - A withdrawn route keeps its ID, its index and its stops, including when its vehicle is
     withdrawn again.
   - Every order and attempt the plan held is still served or dropped after acceptance. Otherwise
     acceptance is refused with `ProposalLosesOrder`, which names the proposal, the order and the
     attempt.
   - A stop reported since the basis stands when the route as accepted, prefix included, keeps it
     on its vehicle after the same stops.
   - The routes keep their order in the plan, and a new route joins the end.

   This amends [ADR-101] point 30, whose withdrawn route took the proposal's stops, and which
   dropped a route the proposal left out.
2. **A daily solve that breaks a lock fails its job, and a daily result is checked as a proposal
   is** ([FR-147], [FR-238], [BR-07], [BR-39]).
   - `Plan.locksBrokenBy(ValidatedSolution)` is a query that changes nothing. It names each locked
     route the solution doesn't keep, as a `FR-147` violation on that route.
   - The worker asks it before `SolveJob.succeed`. When it names any, the worker fails the job with
     `SolveFailure.lockNotHonoured` and those violations, and the plan returns to *ready* through
     `solveFailed`.
   - `applySolveResult` keeps its `RouteLocked` refusal as a backstop.
   - `SolveJob.succeed(ValidatedSolution)` runs the check a proposal gets when it is created: an
     order and attempt once (`OrderAlreadyInPlan`), a vehicle on one route (`VehicleRoutedTwice`),
     and a driver on one route (`DriverRoutedTwice`). A result no plan could hold is refused there,
     and its job fails, instead of the plan refusing it later.

   This amends [ADR-101] point 18: until the optimizer has a lock rule, Planning names the locks a
   result doesn't keep, and the job fails with `LOCK_NOT_HONOURED`. It amends [ADR-105] point 8:
   Planning's `RouteLocked` check stays only as a backstop.
3. **Publishing clears every lock, and a re-optimization takes none** ([FR-147]). A lock binds
   daily solves only, as a pin does ([ADR-105] point 8). Publishing leaves no route locked, and a
   published, completed or cancelled plan that holds a lock doesn't load.
4. **A confirmed hand-over waives the route's [BR-105] along with its [BR-06]** ([FR-304]). A
   shift is still broken only by BR-06, so BR-105 alone needs no confirmation and stays open. This
   amends [ADR-101] point 28, which waived BR-06 only.
5. **A dropped order put back onto another date leaves the old date's ready plan** ([FR-75],
   [FR-258], [FR-316], [BR-71]). `Plan.removePutBackOrder(order, attempt)` takes the order and
   attempt off the drops of a ready plan, at once and without a re-optimization, and clears the
   journal ([FR-145]). Planning's listener calls it on the plan of the date the order left, with the
   attempt that was dropped or failed, when Customers & Orders raises `OrderPutBack`. That event is
   raised only for a put-back to another date.
   - A stop of that attempt stays where it is. A put-back stop can only have failed, and FR-258
     keeps the earlier stop's outcome as recorded.
   - Every other state changes nothing. A draft holds no orders, a published, completed or
     cancelled plan keeps the drop as the day's history, and a solving plan waits for its new
     solve's result.

<!-- Part B's decisions follow here, numbered from 6: untimed and past-the-day times, vehicle
     exclusions, the post-edit queries, and the PlanRevised fields with the objective terms. -->

**Part B's points (6 onward) go here.**

### Readings this ADR settles

These rules no document settled; the code takes them.

- **A clock that steps back.** A command whose `now` falls before the time it must follow records
  that earlier time instead, and is never refused for it. This covers `SolveJob.claim`,
  `searchStarted`, both `succeed`s and `fail`, against the job's latest recorded time;
  `Plan.applySolveResult`, against the solve's request; and `ReoptimizationProposal.decline`,
  against the proposal's creation. Their callers are workers and listeners, which would only retry
  with the same clock, so a refusal would leave a job or a plan stuck.
- **The journal holds at most 100 edits**, undo and redo together. A new edit past that forgets the
  oldest. [FR-145] undoes and redoes one edit at a time, and nothing reads further back than the
  dispatcher steps. A plan that holds more doesn't load. Locking a route with the lock it already
  has changes nothing and journals nothing, which amends [ADR-101] point 37's "re-locking
  replaces a lock kind".
- **A sequence lock on an empty route** stays on its own vehicle's route, and is kept only while
  that route stays empty. Every route has the same empty sequence, so the lock would otherwise be
  kept by any empty route and copied to all of them.
- **A hand-over is evaluated with the vehicle rostered to the new driver.** The validator judges
  BR-06 by the vehicle's rostered driver and reports [BR-20] when the route names someone else,
  while the roster follows the hand-over only after `RouteHandedOver` ([FR-191]).
- **A `SolveFailure` whose violations don't match its reason** is a bug in the worker that built it,
  and fails as one. Persistence rebuilds a stored failure through `SolveFailure.ofStored`, which
  refuses a damaged one as stored state.

## Consequences

- **Easy:** a delivered stop stays on its route through any number of re-optimizations, and an
  acceptance never leaves the plan with fewer orders, so reports and the baseline read every stop
  made.
- **Easy:** a late fact can always name the route it was served on, because no route leaves the
  plan in force.
- **Easy:** a lock the optimizer ignores fails the solve with its reason, and the plan stays
  editable; a dispatcher sees FR-238's failure instead of a plan stuck until midnight.
- **Easy:** a dropped order moved to another date no longer blocks the old date's publishing.
- **Hard:** the worker must read the plan and ask it before it ends a daily job, so ending a job
  needs two aggregates read, though it still changes only the job.
- **Hard:** a route the proposal leaves out stays with its driver, so a proposal that gives that
  driver another vehicle is refused under BR-39 until the roster changes.
- **Cost:** the published plan no longer shows which routes were locked while it was ready.
- **Risk:** a stop the proposal omits by mistake, not because it was reported, stays on its old
  route. The validator's rules keep that from happening; the plan doesn't check why a stop was
  omitted.

## Alternatives considered

- **The optimizer adapter splices the reported prefix in after validation.** Not chosen: every
  caller of `accept` would depend on the adapter doing it, and the plan could still lose an order.
- **`applySolveResult` treats a refused result as a failed solve.** Not chosen: it would change the
  plan in reaction to a job that had already succeeded, so the job and the plan would disagree.
- **Ship lock editing no earlier than the optimizer's lock rule.** Not chosen: the lock editing is
  built, and the plan can name a broken lock itself.
- **Keep locks after publishing for the next solve.** Not chosen: a published plan is re-solved only
  by a re-optimization, which takes no lock, so the lock would bind nothing.
- **Refuse a command whose `now` is out of order, with a named error.** Not chosen: the callers are
  workers and listeners, which would retry with the same clock until the midnight job.
- **Take a put-back order's stop off too.** Not chosen: it would erase a failed stop's recorded
  outcome, against FR-258.

## Revisit when

- The optimizer gains its own lock rule ([FR-147]'s milestone), and Planning's query can give way to
  its report.
- A dispatcher needs locks on a published plan, for example for [FR-159]'s chosen routes.
- A pilot shows that a dispatcher needs more than 100 undo steps.
- A put-back from a published plan must leave its drop, for example because the old date's report
  counts it twice.

[ADR-101]: ADR-101-completed-plans-take-late-outcomes-as-revisions-and-routes-keep-an-index.md
[ADR-105]: ADR-105-a-re-optimizations-problem-carries-its-conventions-and-only-a-vehicle-that-is-out-has-no-driver.md
[BR-06]: ../requirements/requirements.md#routing-constraints
[BR-07]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-10]: ../requirements/requirements.md#re-optimization
[BR-20]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-39]: ../requirements/requirements.md#routing-constraints
[BR-71]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-83]: ../requirements/requirements.md#re-optimization
[BR-105]: ../requirements/requirements.md#routing-constraints
[BR-108]: ../requirements/requirements.md#re-optimization
[DL-8]: ../architecture/domain_layer/domain_layer_design.md#8-when-a-fact-contradicts-the-plan
[FR-75]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-145]: ../requirements/requirements.md#manual-editing
[FR-147]: ../requirements/requirements.md#manual-editing
[FR-159]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-191]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-238]: ../requirements/requirements.md#manual-editing
[FR-258]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-268]: ../requirements/requirements.md#reporting-and-baseline
[FR-304]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-316]: ../requirements/requirements.md#customers-and-orders

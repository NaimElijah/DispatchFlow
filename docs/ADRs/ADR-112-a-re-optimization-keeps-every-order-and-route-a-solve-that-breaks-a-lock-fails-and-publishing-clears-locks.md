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
- **The core couldn't hold what the validator returns.** The validator gives no times to a route
  with no stops, to a stop it can't reach ([BR-14]) and to a return with no road to the end, and
  caps no time at the date's end ([ADR-113] point 3). Planning required every route and stop to be
  timed, and held every time as the kernel's `DaySeconds`, which refuses a time past the date's
  end. So a dispatcher who moved the last stop off a route made every later evaluation fail, and a
  drop whose earliest return fell after midnight failed the solve, though [FR-23] and [FR-233]
  want such a plan shown with its violations. A route's return was not checked against its
  departure, as a stop's times are ([ADR-101] point 39).
- **No type named the vehicles a solve left out** ([FR-197]). [ADR-100] decision 2 makes the
  date's vehicle exclusions the fact of a *no vehicle available* drop, and the optimizer reports
  each with its cause, but the report, the result and the plan had no place for them.
- **An edit's evaluation had to be built from a copy of the edit.** Each edit takes the
  validator's judgement of the plan after the change ([ADR-101] point 15), and the use case asks
  for it before the transaction ([DL §6.3][DL-6.3]). Only `Plan` knew that layout, so the use
  case would have had to repeat each move, take-off, placement, undo, redo and hand-over.
- **`PlanRevised` didn't say what Reporting needs.** The baseline is recomputed for added orders
  as well as withdrawn vehicles ([FR-39], [ADR-109]), and it leaves out a vehicle marked
  unavailable or left without a driver but gives one whose shift ran out its whole shift
  ([FR-271]). The event named only the withdrawn vehicles.
- **`ObjectiveTerms` no longer matched the optimizer's terms.** [ADR-113] point 1 splits route
  stability into `orphaned_stops` and `moved_stops`, and Java's one `stability` term matched
  neither.

The domain also left three smaller rules to its implementer: what a command does with a `now` that a
clock stepped back puts before an earlier recorded time, which `restore` then refuses; how many
edits the undo journal keeps ([FR-145]); and what a sequence lock on an empty route keeps.

This ADR amends [ADR-101] points 15, 18, 19, 28, 30, 37 and 39, and [ADR-105] point 8. The rest of
each stands.

## Decision

On 2026-11-15 the owner, Naim Elijah, decided:

1. **Accepting a re-optimization keeps every route in force, with the stops the proposal neither
   serves nor drops, and refuses a proposal that loses an order** ([BR-07], [BR-10], [BR-83],
   [BR-108], [FR-268]).
   - Each route keeps its reported prefix, the stops the proposal neither serves nor drops, ahead of
     the proposal's stops for its vehicle.
   - A route the proposal gives no route stays with its prefix, or with no stops left. It is marked
     withdrawn when the proposal withdraws its vehicle, and otherwise stays in force, so a late fact
     can still name it. When the proposal takes stops from it, it is *not yet checked*, for the
     re-check job ([FR-395]), and a route left with no stops has no times (point 6).
   - A withdrawn route keeps its ID, its index and its stops, including when its vehicle is
     withdrawn again.
   - The proposal's drops replace the plan's, and a drop of the plan that the proposal neither
     serves nor drops is decided by its order. When the proposal serves or drops a later attempt of
     that order, the old drop leaves: the order was put back onto its own date, and the optimizer
     knows only its current attempt ([ADR-115] point 3). Otherwise the drop is carried forward as it
     was, as the day's history: the order was put back onto another date, or the optimizer left it
     out (point 5).
   - So every other order and attempt the plan held is still served or dropped after acceptance.
     Each step above keeps what the proposal doesn't hold, so `accept` checks this as a bug, not as
     a refusal, and no error names it.
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
   - The worker fails that job with `SolveFailure.rejected`, as `VALIDATOR_REJECTED`, naming the
     broken rule as the validator names it: `BR-07` with the order, or `BR-39` with the vehicle or
     the driver. The validator reports the same violations for the same faults, and [FR-65] already
     tells the dispatcher of a validator rejection, so no new reason is added.

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
   - A published plan keeps the drop, and every re-optimization accepted later carries it forward
     unchanged, because the old date's problem no longer holds the order (point 1).
   - A stop of that attempt stays where it is. A put-back stop can only have failed, and FR-258
     keeps the earlier stop's outcome as recorded.
   - Every other state changes nothing. A draft holds no orders, a published, completed or
     cancelled plan keeps the drop as the day's history, and a solving plan waits for its new
     solve's result.

6. **A route or stop the validator couldn't time has no times, and a time may pass the date's
   end** ([FR-23], [FR-233], [BR-14], [ADR-113] point 3).
   - A route's plan is optional in `RouteTimes`, in `SolvedRoute` and on the plan's route, and a
     stop's plan is optional in `StopTiming`, as it already is on the plan's stop. A route has none
     when the validator gives it no departure or no return: it has no stops, or no road to its end.
     A stop has none when it breaks BR-14 or, in a re-optimization, [BR-10].
   - An evaluation that gives a route or stop no times leaves it with none when the plan takes the
     evaluation's times.
   - A time the optimizer reports is a `SolveTime`: whole seconds from the date's local midnight,
     0 or more, with no upper bound. It holds a stop's times, a route's departure and return, a
     confirmed return ([FR-394]), a revision's planned arrival, and the earliest arrival or return
     of the *time window*, *shift* and *depot hours* drops. A negative one is refused with
     `TimeBeforeTheDay`.
   - A time that must lie in the day stays the kernel's `DaySeconds`, unchanged: a drop's window
     end, latest return and depot closing.
   - A route's return is never before its departure, and both count from one date, or the route's
     plan is refused with `InvalidRouteTimes`, as a stop's times are with `InvalidStopTimes`.

   This amends [ADR-101] point 19, whose fact time was a time of the date, and extends point 39's
   value checks to a route.
7. **A solve names the vehicles it left out, and the plan keeps them** ([FR-197], [ADR-100]
   decision 2).
   - `ExcludedVehicle` pairs a vehicle with its `ExclusionCause`. The causes mirror the Python
     `VehicleExclusion` in Planning's words: *depot without location* and *no road between depots*,
     which a daily solve gives, and *vehicle unavailable*, *no driver*, *shift ran out*, *route
     finished* and *withdrawn*, which come from a re-optimization's vehicle state ([ADR-050]).
   - `ValidationReport`, `ValidatedSolution`, `SolveResult` and the proposal hold them, and only
     the optimizer adapter builds the report.
   - Applying a daily result and accepting a proposal replace the plan's exclusions with theirs,
     and the plan's snapshot stores them. A draft holds none.
   - A *no vehicle available* drop still carries no field: the plan's exclusions are its fact.
8. **`Plan` answers what a change would leave, and changes nothing** ([ADR-101] point 15,
   [DL §6.3][DL-6.3]).
   - `afterMove`, `afterTakeOff`, `afterPlacing`, `afterPin`, `afterUnpin`, `afterUndo`,
     `afterRedo` and `afterHandOver` return the plan's routes, drops and pins as the command would
     leave them, as a read-only `PlanLayout`. `layout()` returns the plan as it is, for the
     publishing re-check and the route check.
   - Each command builds its layout with the same function its query calls, so the two never
     differ. A query refuses what its command refuses, except what needs `today`, the evaluation
     or the new driver's standing.
   - A hand-over now refuses a driver who already drives a route before it reads the evaluation
     or the standing.

   This extends [ADR-101] point 15: the use case reads the layout the evaluation must describe
   from the plan.
9. **`PlanRevised` names each withdrawn vehicle's cause and the orders the revision added**
   ([FR-39], [FR-267], [FR-271], [ADR-109]).
   - `withdrawalCauses` holds each withdrawn vehicle's cause, at the vehicle's position in
     `withdrawnVehicles`, because an event holds no record of its own ([DL §7.3][DL-7.3]).
     `WithdrawalCause` moves to Planning's `events` package, which the domain imports.
   - `addedOrders` names the orders an accepted re-optimization put into the plan that it held
     under no attempt before, in plan order. Every other cause adds none.
10. **`ObjectiveTerms` mirrors the optimizer's `Cost` term for term** ([FR-103], [FR-256],
    [BR-115], [ADR-113] point 1). Its terms are `travel`, `drops`, `doorWindow`, `fixedVehicles`,
    `lateness`, `orphanedStops` and `movedStops`. `orphanedStops` and `movedStops` together are
    the route-stability cost, and `routeStability()` is their sum, the preview's stability figure.

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
- **A route with stops but no road to its end has no plan at all.** The validator still gives its
  departure, but a route plan without a return would be a second kind of partial plan, and the
  stops keep their own times.
- **An added order is an order, not an attempt.** A later attempt of an order the plan held, such
  as a put-back's, is not an added order. A late outcome that puts back a cancelled order's stop
  (BR-130) adds none either, because only a re-optimization brings new orders to a published plan.
- **The new IDs a result draws.** Applying a daily result draws at most one ID per route and one
  per stop of the result, and accepting a proposal at most one per route and one per stop of the
  proposal; the use case supplies that many. Too few is a bug in the use case, and `FreshIds`
  fails as one.
- **A seed's range.** Any `long` is a seed, negative ones included. The solver port takes a seed
  ([ADR-044]), but no adapter or contract operation passes one yet; the contract that adds it
  settles the range both sides accept ([NFR-13]).
- **Planning's events check every part is present**, as the other modules' events do.
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
  route, and a drop it omits by mistake is carried forward. The validator's rules keep that from
  happening; the plan doesn't check why a stop or a drop was omitted.
- **Easy:** an emptied route, an unreachable stop and a late return are shown with their
  violations, and the next edit still applies.
- **Easy:** the use case asks the plan for the layout to send to the optimizer, so it repeats no
  edit, and a test checks that each command leaves exactly its query's layout.
- **Easy:** Reporting recomputes the baseline from `PlanRevised` alone, and the dispatcher sees
  the vehicles a solve left out and why.
- **Hard:** Planning holds two kinds of time, `SolveTime` for what the optimizer reports and
  `DaySeconds` for limits that lie in the day, so a reader picks the one the field means.
- **Hard:** a consumer of `PlanRevised` reads a vehicle's cause by position, and the event fails
  as a bug when the two lists differ in length.
- **Cost:** each snapshot of a plan or a proposal stores its excluded vehicles.
- **Risk:** [FR-36]'s per-driver changes and [FR-334]'s goods hand-overs still have no domain
  owner: `ReoptimizationResult` keeps them as IDs, as [ADR-101] point 41 decided, until the
  optimizer's re-optimization report settles their shape in M9.

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
- **Refuse a proposal that neither serves nor drops a drop the plan holds** (`ProposalLosesOrder`).
  Rejected: the optimizer knows no attempts, so after a put-back onto the same date a proposal holds
  only the new attempt, and after a put-back onto another date it holds neither. Every
  re-optimization of the date would then be refused, though re-optimizing is how [FR-75] brings the
  order back.
- **A failure reason of its own for a result no plan could hold.** Not chosen: the validator reports
  the same faults as violations, and a new reason would add one to [FR-65]'s list.
- **Take a put-back order's stop off too.** Not chosen: it would erase a failed stop's recorded
  outcome, against FR-258.
- **Widen the kernel's `DaySeconds` past the day's end.** Not chosen: every other module relies on
  it lying in the day, and a window or a shift must.
- **Cap the optimizer's times at the date's end.** Rejected in [ADR-113]: a drop fact would lie
  about how far an order misses.
- **Make `Layout` public for the use case.** Not chosen: it is the plan's working state, with
  locks, checks and times, and the use case needs only what the evaluation must describe.
- **Three lists of withdrawn vehicles, one per cause.** Not chosen: `withdrawnVehicles` stays as
  every consumer reads it, and a new cause would need a new field.

## Revisit when

- The optimizer gains its own lock rule ([FR-147]'s milestone), and Planning's query can give way to
  its report.
- A dispatcher needs locks on a published plan, for example for [FR-159]'s chosen routes.
- A pilot shows that a dispatcher needs more than 100 undo steps.
- A put-back from a published plan must leave its drop, for example because the old date's report
  counts it twice.
- The optimizer's contract adds the seed, or its re-optimization report settles the shape of
  FR-36's per-driver changes and FR-334's goods hand-overs (M9).
- A screen needs a route's departure when its return can't be timed.
- The baseline needs a later attempt of a held order counted as added.

[ADR-101]: ADR-101-completed-plans-take-late-outcomes-as-revisions-and-routes-keep-an-index.md
[ADR-044]: ADR-044-solver-port-takes-start-budget-and-seed-and-problem-judges.md
[ADR-050]: ADR-050-re-optimization-conventions-live-only-in-the-python-problem-factory.md
[ADR-100]: ADR-100-k-covers-door-windows-outside-a-shift-and-no-vehicle-is-a-drop-reason.md
[ADR-105]: ADR-105-a-re-optimizations-problem-carries-its-conventions-and-only-a-vehicle-that-is-out-has-no-driver.md
[ADR-109]: ADR-109-reporting-computes-exact-on-time-shares-from-current-facts-and-keeps-no-personal-data.md
[ADR-113]: ADR-113-route-stability-is-its-own-cost-the-rules-read-a-problem-through-a-protocol-and-untimed-outputs-are-documented.md
[ADR-115]: ADR-115-import-rows-keep-their-own-recipient-put-backs-raise-an-event-and-orders-clamp-a-stepped-back-clock.md
[BR-06]: ../requirements/requirements.md#routing-constraints
[BR-07]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-10]: ../requirements/requirements.md#re-optimization
[BR-20]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-39]: ../requirements/requirements.md#routing-constraints
[BR-71]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-83]: ../requirements/requirements.md#re-optimization
[BR-105]: ../requirements/requirements.md#routing-constraints
[BR-115]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-14]: ../requirements/requirements.md#routing-constraints
[BR-108]: ../requirements/requirements.md#re-optimization
[DL-6.3]: ../architecture/domain_layer/domain_layer_design.md#63-outside-calls-happen-before-the-transaction
[DL-7.3]: ../architecture/domain_layer/domain_layer_design.md#73-shape-and-delivery
[DL-8]: ../architecture/domain_layer/domain_layer_design.md#8-when-a-fact-contradicts-the-plan
[FR-23]: ../requirements/requirements.md#manual-editing
[FR-36]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-39]: ../requirements/requirements.md#reporting-and-baseline
[FR-65]: ../requirements/requirements.md#daily-planning
[FR-75]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-103]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-145]: ../requirements/requirements.md#manual-editing
[FR-147]: ../requirements/requirements.md#manual-editing
[FR-159]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-191]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-197]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-233]: ../requirements/requirements.md#manual-editing
[FR-238]: ../requirements/requirements.md#manual-editing
[FR-256]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-258]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-267]: ../requirements/requirements.md#reporting-and-baseline
[FR-268]: ../requirements/requirements.md#reporting-and-baseline
[FR-271]: ../requirements/requirements.md#reporting-and-baseline
[FR-304]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-316]: ../requirements/requirements.md#customers-and-orders
[FR-334]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-394]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-395]: ../requirements/requirements.md#fleet-depots-and-shifts
[NFR-13]: ../requirements/requirements.md#nfr-13

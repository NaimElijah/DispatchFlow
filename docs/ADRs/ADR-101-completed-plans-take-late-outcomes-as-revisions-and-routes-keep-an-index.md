# ADR-101: A completed plan takes late outcomes as revisions, a hand-over takes an optional check, and each route keeps an index from 1 to 30

- **Status:** Accepted; amended by ADR-112 and ADR-126
- **Date:** 2026-11-14

## Context

The Planning domain is built ahead of its milestones, from the requirements and the domain document
([DL §4.4]). Reading the two together left a dozen rules undecided:

- what a late outcome does to a *completed* plan ([BR-118] against [BR-102] and [BR-89]), and to a
  *cancelled* one ([BR-130]);
- how a hand-over checks the new driver's shift when the optimizer may be down ([FR-304] against
  [ADR-058]);
- how drivers learn that a plan was cancelled ([FR-337]);
- what a late `SolveSucceeded` does to an *abandoned* plan. [ADR-053] names only `SolveFailed`;
- what the undo journal covers ([FR-145]);
- how a route gets its label and colour ([FR-148]);
- where [FR-394]'s confirmed return time lives;
- how long building the travel-time matrix may take;
- whether a queued re-optimization counts under [BR-38];
- which reports count as changes against a proposal ([BR-25], [BR-82]);
- who owns the departure allowance and its starting value ([FR-67]).

Building the domain then met a second set of rules that no document settles: how the validator's
answers reach the plan, what each lock kind keeps, and what the listeners do in each state. Each is
a reading of the narrowest kind, recorded here so a maintainer doesn't have to reverse-engineer it.

This ADR amends [ADR-053] in one point, a late `SolveSucceeded` on an abandoned plan. The rest of
ADR-053 stands.

## Decision

Points 1 to 12 were decided on 2026-11-14.

1. **A completed plan accepts only a late outcome,** through `reconcileLateOutcome`. It is recorded
   as a new revision, and the plan stays *completed*: it is never republished ([BR-89], [BR-102],
   [BR-118]). Every other command on a completed plan is refused or changes nothing.
2. **A late outcome on a cancelled plan changes nothing in the plan.** Live Operations records it as
   *served after cancellation*, and the plan stays cancelled with its routes as they were ([BR-130],
   [BR-103]).
3. **`handOver` takes an optional evaluation** from the optimizer ([FR-304], [ADR-058]):
   - one that shows the route breaking the new driver's shift refuses the hand-over with
     `BreakNotConfirmed`, unless the dispatcher confirmed;
   - with none, the route is *not yet checked*, and the re-check job settles it ([FR-395]).
4. **A new event, `PlanCancelled`,** names the plan and its routes, so the drivers are alerted
   ([FR-337]).
5. **A late `SolveSucceeded` on an abandoned plan is ignored,** as a late `SolveFailed` is. This
   amends [ADR-053].
6. **The undo journal covers moves, removals, placements, pins and locks** ([FR-145]).
   - A new edit clears redo.
   - An applied solve, or a stop removed by `removeStopOf`, clears the whole journal.
7. **Each route keeps a stable index from 1 to 30,** which is its label and its colour slot
   ([FR-148]).
   - A re-solve keeps a vehicle's route index.
   - The range doesn't follow the fleet limit: a withdrawn route keeps its index beside its vehicle's
     new one. The 15-vehicle limit stays ([BR-36], [NFR-04], [ADR-060]).
   - The palette stays in `map-style` ([ADR-084]).
8. **[FR-394]'s confirmed return time lives on the route,** and other modules read it through
   Planning's API.
9. **Building the matrix has its own deadline of 2 minutes,** a starting value. The watchdog fails a
   job past it with *matrix took too long* (`MATRIX_TOO_LONG`), as it fails one queued past 5
   minutes ([BR-112]) or running 30 seconds past its limit ([BR-32]).
10. **A queued re-optimization counts under [BR-38],** like a running one.
11. **`ChangesSinceBasis` counts accepted outcomes and arrivals.** A report held for the dispatcher
    ([BR-132]) doesn't count until it is confirmed ([BR-25], [BR-82]).
12. **The departure allowance belongs to `PlanningSettings`,** and starts at 0 minutes ([FR-67],
    [FR-183]).

### Readings this ADR settles

Building the domain needed these readings, which no document settles. Each takes the narrowest
reading of the requirements.

13. **A solve's result lives on its job.** `SolveJob.succeed` takes a `ValidatedSolution` and keeps
    its routes and drops. `Plan.applySolveResult` reads them from the job, and `Plan.accept` takes the
    `ReoptimizationProposal` itself, not a snapshot of it. Only the optimizer adapter builds a
    `ValidationReport`, so a listener could never rebuild a `ValidatedSolution` from storage
    ([DL §13]). [BR-15] still holds by type on both paths: only a validated solution makes a job
    succeed or creates a proposal, and only persistence restores either.
14. **The plan's states keep their own facts.** *Completed* and *cancelled* keep the last revision
    number, and *cancelled* keeps when it happened, which [BR-130]'s judgement needs. The solve the
    routes came from is kept apart from the one the plan waits for. So a failed solve returns the plan
    to *ready* ([BR-59]), and publishing still knows when that solve was requested.
15. **An evaluation must describe the plan after the change.** It names the version it was read
    for. Each of its routes names the vehicle and driver it was judged with, and its routes, their
    vehicles, drivers and stop sequences must equal the plan's after the change, or the plan refuses it
    as `StaleEvaluation` ([FR-23], [FR-233]). So an evaluation made with a route's old driver can't
    pass a hand-over. Its per-stop times are the recalculated ETAs.
16. **A pin takes an evaluation in *draft* too** ([FR-91]). In *draft* it covers a plan with no
    routes and checks the version. A draft keeps no violations, so the evaluation's violations are
    not stored; they are judged again once the plan has routes. A lock changes no route, so it needs
    none. Undoing a lock still takes one, because `undo` always does.
17. **An edit while the optimizer is down is refused,** with `ValidatorUnavailable`. [ADR-058] lets
    hand-overs, cancellations and date changes complete without a check, but nothing says the same of
    manual edits, and [FR-23] needs their recalculated times. Publishing's `RecheckUnavailable` stays
    publishing's.
18. **The two lock kinds, as read from [FR-147].** FR-147 names a lock on "a route, or the sequence
    of its stops", but not what the second keeps.
    - `WHOLE_ROUTE` keeps the route's vehicle, its driver and its exact stop sequence.
    - `STOP_SEQUENCE` keeps the stops in their order, on whichever one route serves them; the lock
      moves with them.
    - The optimizer reports a lock it can't keep, and the job fails with `LOCK_NOT_HONOURED`
      ([FR-238]).
    - `applySolveResult` checks the locks anyway, because the domain document makes the plan their
      owner. A failure there is a bug upstream: its `RouteLocked` refusal exhausts the listener's
      retries and alerts the team.
19. **Drop reasons are [FR-223]'s full list,** in the optimizer's order. That list includes *vehicle
    limit* ([FR-201]), which the optimizer doesn't produce yet, and *no vehicle available*, second in
    the order ([ADR-100]). Each reason carries its fact ([FR-144]). A fact time is a time of the
    date, whose length is the planning horizon ([ADR-088]).
20. **Listeners and jobs take no `today`.** These are `applySolveResult`, `solveFailed`,
    `removeStopOf`, `reconcileLateOutcome` and `recordRouteCheck`. A `DateInPast` refusal would only
    make a listener retry until the midnight job runs, and [BR-128] already covers the date.
21. **A result or a failure from any other job, or in any other state, changes nothing** ([BR-59]).
    That covers listener repeats and point 5 alike. `removeStopOf` and `recordRouteCheck` likewise
    change nothing where they have nothing to do (points 25 and 26).
22. **"A re-optimization needs a published plan" is the plan's rule** ([FR-35]).
    `Plan.requestReoptimization` refuses unless the plan is published. It creates the job with the
    plan's revision as its basis and a 20-second limit ([BR-84]), and changes nothing in the plan.
23. **[BR-38] is checked under the lane's lock.** `LaneCapacity` lists the plans of the lane's queued
    and started jobs, so a second re-optimization of a plan is refused (point 10). The partial unique
    index stays as the backstop.
24. **When an order changed "while solving"** ([BR-71], [FR-317], [FR-316]). Publishing refuses with
    `OrderChangedSinceSolve` in three cases:
    - an order placed after the solve was requested;
    - one whose last change fell between the request and the moment the plan applied the result;
    - one the plan holds that is no longer among the date's uncancelled orders.

    A change after the result was applied waits for the re-check. An order changed both while
    solving and after shows only its later change.
25. **`removeStopOf` acts only on a ready or published plan.**
    - It takes the order's stop off its route, or its entry off the drops ([BR-30]). It marks the
      route *not yet checked*, and it overrides a lock.
    - On a published plan it is a revision, even when only a drop goes.
    - In any other state it changes nothing. A solving plan's publishing step catches the order
      ([FR-317]), and a completed plan takes only late outcomes (point 1).
26. **`recordRouteCheck` records check states and open violations only.** It changes no time or stop,
    so it makes no revision: "the rest of the route stays as it is" ([FR-207]). Outside *ready* and
    *published* it changes nothing.
27. **Cancelling a plan makes no revision.** [ADR-059]'s revision causes don't include it.
    Cancelling needs no route started and no stop reported ([FR-336]). A stop recorded on a driver's
    behalf starts no route, so a reported stop refuses on its own, with `StopAlreadyReported`. Each
    refusal lists every route or stop it found.
28. **The hand-over's details** ([FR-302] to [FR-304], [FR-394]):
    - only [BR-06] counts as a broken shift, not [BR-105]'s break reserve;
    - an evaluation that couldn't be read counts as none;
    - the route keeps its stops and times ([FR-192]);
    - a confirmed break sets the confirmed return to the evaluated return time. A hand-over that is
      unconfirmed or unchecked clears it, and a broken shift that a later check finds is only
      flagged;
    - a withdrawn route is refused with `RouteWithdrawn`, because moving the roster with its vehicle
      ([FR-191]) would be wrong;
    - the evaluation's other violations become the open violations.
29. **Reconciling a late outcome** ([DL §8]):
    - the stop is found by its ID or by its order and attempt, so an order is never added twice;
    - the stop joins the end of the serving route; the real order of the facts is Live Operations'
      ([BR-29]);
    - a stop that comes from another route keeps its times; one from the drops or a cancelled order
      has none;
    - a completed plan's revision raises `PlanRevised` too.

    A rescheduled order's late fact waits for its own decision; until then, a stop the plan holds
    nowhere counts as *served after cancellation*.
30. **Accepting a proposal** ([BR-25], [BR-82], [BR-83], [BR-64], [BR-65]):
    - **A revision since the basis** is a change the proposal relies on. It refuses through
      `ProposalOutdated`, whose changes name each such revision, as the other changes are named
      ("what changed is named", [BR-82]).
    - **A stop stays put** when it keeps its vehicle and the same stops before it, in the same order.
      That is BR-83's "same vehicle and in the same order".
    - **What the proposal relies on:** every order it serves or drops, and every vehicle and driver
      it uses.
    - **The order of refusals:** `ProposalOutdated` first, then `VehicleUnavailable`, which lists
      every vehicle the proposal keeps driving that fails [BR-20] now.
    - **IDs at acceptance:** the routes in force are the basis revision's, so a vehicle keeps its
      route's ID and index, and its confirmed return when it keeps its driver; an order keeps its
      stop's ID.
    - **A withdrawn vehicle with no route in the proposal** keeps its route, marked withdrawn, without
      the orders the proposal serves or drops, so no order is held twice. Its index stays reserved.
    - **A vehicle withdrawn earlier and withdrawn again** keeps that withdrawn route: its ID, index
      and stops. Stop IDs come from every route, withdrawn ones included.
    - **An order held by a withdrawn route** that the proposal serves elsewhere refuses with
      `OrderAlreadyInPlan`: BR-07 is checked over the whole plan the acceptance would leave.
    - **A proposal holds what a plan could hold.** One check runs both when a proposal is created and
      when it is restored: an order and attempt once (`OrderAlreadyInPlan`), a vehicle on one route,
      withdrawn or not (`VehicleRoutedTwice`), and a driver on one route that isn't withdrawn
      (`DriverRoutedTwice`). Creating one refuses with the error; a stored one that breaks it doesn't
      load.
31. **How a route gets its index:** a vehicle keeps its route's index, and a new route takes the
    lowest free one. A vehicle withdrawn more than once can use up all 30, which refuses with
    `RouteIndexesUsedUp`. An index isn't kept across a solve that leaves the vehicle out, because the
    plan keeps no record of it.
32. **The solve job's own refusals.**
    - A command out of phase is refused with `JobNotQueued`, `JobNotBuildingMatrix` or
      `JobNotRunning`. A result of the other lane's kind is refused with `ResultForAnotherLane`, and
      a lane capacity read for the other lane with `CapacityForAnotherLane`.
    - No failure reason is tied to a phase: startup recovery and the midnight job fail any phase
      ([BR-74], [BR-85], [BR-128]).
    - [FR-159], re-optimizing only chosen routes, is left out: no rule of the domain needs it yet.
33. **`ValidatedSolution.of` refuses a rejected report** with `SolutionRejected` ([BR-57]).
34. **A solver is an opaque name** ([FR-20], [FR-224]): a lower-case letter, then up to 39
    lower-case letters, digits or underscores. Adding a solver changes nothing in the core.
35. **`PlanningSettings` start at the requirements' values:** [FR-321]'s buckets, a 10% buffer
    ([FR-227]), a 45-second limit ([FR-19]), no fixed vehicle cost ([FR-21]), and the allowance of
    point 12.
    - Its checks are the fewest that keep a value meaningful: factors above zero, bucket hours
      ordered and not overlapping, a buffer of zero or more, a fixed cost above zero when on, and an
      allowance of zero or more.
    - It has no upper bounds, because none is stated.
    - The allowance is whole minutes, because FR-67 says minutes. The fixed cost is in seconds,
      because the objective counts seconds ([BR-126]).
36. **Opening a plan reads the date's uncancelled plan first** ([BR-34]). The partial unique index is
    the backstop.
37. **Small edit rules:**
    - re-pinning replaces a pin, and re-locking replaces a lock kind;
    - removing a pin or lock that isn't there is refused;
    - a pin set in *draft* isn't journaled, because the journal covers edits since the last solve;
    - undoing a removal restores the same stop ID;
    - an order dropped for its location stays unplaceable until the next solve ([FR-237]), because
      the plan can't tell that the location was fixed;
    - publishing and abandoning clear the journal.
38. **Every command checks the state it would leave** against the same at-rest invariants `restore`
    checks, and a break there is a bug, not a refusal. The journal's last entries must name their
    stop on its route at its position, and the position undoing or redoing returns it to must be in
    range. A *solving* plan that was never solved holds no violations.
39. **Value checks.** A stop's times stay in order on one date, a leg is never negative, and a rule
    code is a requirement row's ID. Revision numbers and route indexes are made only by the plan, so
    a bad one is damaged stored state, as with the kernel's `Version`.
40. **Declining an accepted proposal is allowed.** Acceptance lives on the plan, so declining changes
    nothing it applied.
41. **The preview's facts are kept as IDs and durations:** late stops, vehicles with no usable
    position, per-driver changes and goods hand-overs ([FR-36], [FR-103], [FR-334], [BR-81],
    [BR-107]). Their final shape waits for the optimizer's re-optimization report.

## Consequences

- **Easy:**
  - every Planning command has one answer in every state, and a parameterized test checks the
    whole matrix;
  - a listener's repeat is harmless, so no reaction needs its own idempotency record for the plan;
  - a route is told apart by its index everywhere it is shown, through a whole day of
    re-optimizations;
  - a hand-over works while the optimizer is down.
- **Hard:**
  - the domain document's Planning rows change to match: the class diagram, the error table, the
    lifecycle and the watchers;
  - Planning has more named errors than the document listed. Each needs a translation in both apps
    (NFR-144);
  - an edit can't be made while the optimizer is down.
- **Cost:** about 30 more named errors and their tests, and one more deadline for the watchdog.
- **Risk:**
  - a sequence lock's meaning is a reading, and the optimizer has no lock rule yet;
  - an order changed twice, once while solving and once after, publishes without being solved again.

## Alternatives considered

- **Refuse a late outcome on a completed plan.** Rejected, as the recommendation
  advised: the fact happened, and facts outrank intent ([ADR-065]).
- **Refuse a hand-over while the optimizer is down.** Rejected: [NFR-42] and [ADR-058] keep
  hand-overs working.
- **Tie the route index to the 15-vehicle limit,** an index from 1 to 15. This was the
  recommendation, and it was rejected because the index doesn't follow the fleet limit. A
  withdrawn route and its vehicle's new route both need an index.
- **Let the watchdog fail a matrix build at the queue's 5 minutes.** This was the recommendation,
  and it was rejected in favour of a deadline of its own. The recommendation's case was that
  one deadline is simpler; a separate one also stops a stuck build from holding the lane as long.
- **Store the `ValidatedSolution` on the job and rebuild it on load.** Rejected: only the optimizer
  adapter may build a `ValidationReport`, so rebuilding one in persistence would open BR-15's door.
- **Mark an edited route *not yet checked* while the optimizer is down, as a hand-over is.** Not
  chosen: manual edits are a planning-time tool, and their recalculated times are the point of them
  ([FR-23]).
- **A solver enum of OR-Tools and our solver.** Not chosen: it breaks [FR-224] when a solver is
  added.
- **A separate refusal for a revision since a proposal's basis.** Not chosen: it is a change the
  proposal relies on, and [BR-82] names what changed within one refusal.

## Revisit when

- The pilot shows a late outcome on a completed plan that should have alerted a driver.
- The optimizer gains a lock rule, and its meaning of a sequence lock differs from point 18.
- A day withdraws vehicles often enough that 30 route indexes run out.
- Matrix builds pass 2 minutes on the demo day, which would make point 9's starting value too short.
- The re-optimization report (M9) fixes the preview's shape, which replaces point 41.
- Dispatchers ask to edit while the optimizer is down.

[ADR-053]: ADR-053-plan-lifecycle-publishing-stability-drop-reasons-and-abandoned-plans.md
[ADR-058]: ADR-058-routes-changed-while-the-optimizer-is-down-are-checked-when-it-returns.md
[ADR-059]: ADR-059-plan-revisions-are-stored-as-rows.md
[ADR-060]: ADR-060-hard-limits-on-fleet-and-plan-size.md
[ADR-065]: ADR-065-facts-outrank-intent.md
[ADR-084]: ADR-084-maplibre-in-both-apps-with-self-hosted-protomaps-tiles.md
[ADR-088]: ADR-088-israel-local-time-for-every-date-and-schedule.md
[ADR-100]: ADR-100-k-covers-door-windows-outside-a-shift-and-no-vehicle-is-a-drop-reason.md
[BR-06]: ../requirements/requirements.md#routing-constraints
[BR-105]: ../requirements/requirements.md#routing-constraints
[BR-15]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-57]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-126]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-20]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-32]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-34]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-38]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-59]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-64]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-65]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-71]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-74]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-102]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-103]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-112]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-118]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-128]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-25]: ../requirements/requirements.md#re-optimization
[BR-81]: ../requirements/requirements.md#re-optimization
[BR-82]: ../requirements/requirements.md#re-optimization
[BR-83]: ../requirements/requirements.md#re-optimization
[BR-84]: ../requirements/requirements.md#re-optimization
[BR-85]: ../requirements/requirements.md#re-optimization
[BR-107]: ../requirements/requirements.md#re-optimization
[BR-29]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-89]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-130]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-132]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-30]: ../requirements/requirements.md#orders-records-and-history
[BR-36]: ../requirements/requirements.md#time-and-limits
[DL §4.4]: ../architecture/domain_layer/domain_layer_design.md#44-planning
[DL §8]: ../architecture/domain_layer/domain_layer_design.md#8-when-a-fact-contradicts-the-plan
[DL §13]: ../architecture/domain_layer/domain_layer_design.md#13-inside-each-module-hexagon
[FR-19]: ../requirements/requirements.md#daily-planning
[FR-20]: ../requirements/requirements.md#daily-planning
[FR-21]: ../requirements/requirements.md#daily-planning
[FR-67]: ../requirements/requirements.md#daily-planning
[FR-91]: ../requirements/requirements.md#daily-planning
[FR-144]: ../requirements/requirements.md#daily-planning
[FR-223]: ../requirements/requirements.md#daily-planning
[FR-224]: ../requirements/requirements.md#daily-planning
[FR-227]: ../requirements/requirements.md#daily-planning
[FR-238]: ../requirements/requirements.md#manual-editing
[FR-321]: ../requirements/requirements.md#daily-planning
[FR-23]: ../requirements/requirements.md#manual-editing
[FR-145]: ../requirements/requirements.md#manual-editing
[FR-147]: ../requirements/requirements.md#manual-editing
[FR-233]: ../requirements/requirements.md#manual-editing
[FR-237]: ../requirements/requirements.md#manual-editing
[FR-148]: ../requirements/requirements.md#dispatcher-views
[FR-183]: ../requirements/requirements.md#company-users-and-roles
[FR-191]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-192]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-201]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-302]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-304]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-394]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-395]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-207]: ../requirements/requirements.md#customers-and-orders
[FR-316]: ../requirements/requirements.md#customers-and-orders
[FR-317]: ../requirements/requirements.md#customers-and-orders
[FR-336]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-337]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-35]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-36]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-103]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-159]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-334]: ../requirements/requirements.md#live-operations-and-re-optimization
[NFR-04]: ../requirements/requirements.md#nfr-04
[NFR-42]: ../requirements/requirements.md#nfr-42

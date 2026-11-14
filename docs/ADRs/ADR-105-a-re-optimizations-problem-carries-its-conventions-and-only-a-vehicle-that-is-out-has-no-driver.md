# ADR-105: A re-optimization's problem carries its conventions, and only a vehicle that is out has no driver

- **Status:** Accepted
- **Date:** 2026-11-14

## Context

[ADR-050] has `ProblemFactory.reoptimization` apply six conventions and turn a live day into an
ordinary, smaller problem. It says that "the validator applies the same conventions", but not how
the problem carries them. [ADR-044] says that the problem's re-optimization context holds the
reported prefixes and each stop's origin vehicle. The daily domain was built first ([DL §4.9]), and
these gaps were left to this change:

- **Where the conventions live.** `validate`, `cost` and `explain_drop` must stay the one definition
  ([BR-42]), and a daily problem must behave exactly as before.
- **Which rule a broken convention breaks.** [BR-40] gives way to [BR-51] and [BR-52] in a
  re-optimization. [BR-10], [BR-106] and [BR-108] can break only there. [BR-107] keeps a late stop
  and flags it, so a late stop is not a violation.
- **A vehicle with no driver.** ADR-050's vehicle state includes "no driver". In the daily domain
  every vehicle's driver is a valid index, so the problem could not hold such a vehicle.
- **K and lateness.** [ADR-049]'s derivation counts "in a re-optimization a stop past its window"
  in its 5L term. [ADR-100] left "BR-115's lateness penalty needs the same bound" as its trigger
  to revisit.

This ADR extends [ADR-050]: it says how the problem carries each of its conventions. It amends
[ADR-044]: the context holds the facts' consequences, as flags per stop (locked, the vehicle a stop
stays on, orphaned), not the reported prefixes and each stop's origin vehicle. It amends [ADR-049]
and [ADR-100]: in a re-optimization, L also covers lateness, the door term starts from each
vehicle's start time, and the rate is the larger of two. The rest of each stands.

## Decision

1. **A `Problem` has an optional `ReoptContext`.** A daily problem has none, and every check that
   reads the context is skipped, so a daily problem behaves as before. The context holds `now`,
   each vehicle's start time, the locked stops, the vehicle each stop stays on, the orphans, the
   vehicles with no usable position ([BR-81]) and the warm start. The rest of each convention goes
   into the arrays every check already reads:
   - `Vehicles.start_location` is where the vehicle is placed ([BR-51]). For a withdrawn vehicle,
     `end_location` is its last reported stop ([BR-52]).
   - A vehicle that is out is excluded for its state: *unavailable*, *no driver* or *out of
     shift* before it made a stop; *finished*; *withdrawn*. So the drop reasons, the reachability
     of orders and K see only the active vehicles. A vehicle that made a stop and then became
     unavailable, lost its driver or ran out of shift is withdrawn ([FR-267]).
   - A stop that stays on its vehicle carries the moved-stop cost in its drop penalty ([BR-115]).
2. **The validator reports the re-optimization rules this way:**
   - A locked stop on a route, or listed as dropped, breaks [BR-10]. Its report serves its order,
     so it is never unserved and pays no drop.
   - A route that starts anywhere but where its vehicle is placed breaks [BR-51]. A withdrawn
     route that ends anywhere but its last reported stop breaks [BR-52].
   - A stop on a finished or withdrawn route breaks [BR-108].
   - A stop on a vehicle other than the one it stays on breaks [BR-106].
   - A withdrawn route does not count under [BR-39], and its driver may drive another vehicle.
     The roster's one-vehicle-per-driver check ([FR-54]) counts only the vehicles that are not out.
   - A stop with a hard window served past its end is a `LateStop` in the report: flagged for the
     preview ([FR-103]), and never a violation ([BR-107]). The window's start stays hard, and the
     return stays bound by the shift and the break reserve. A door stop pays its window penalty
     and is never flagged ([BR-120]).
   - Only what follows `now` is timed: every route leaves at its vehicle's start time ([BR-23]).
3. **The objective adds two terms in a re-optimization** ([BR-115], [BR-56]). *Lateness* is the
   lateness rate, 5 per second to start with, times the seconds stops with a hard window are
   served late. *Orphaned stops* is the moved-stop cost, 300 seconds to start with, times the
   orphans: the constant of ADR-050's step 5. A daily problem's terms are 0, and its total is
   unchanged.
4. **K's L covers lateness.** In a re-optimization, L is the largest of the longest shift, the
   longest a door stop can spend outside its window, and the longest a stop with a hard window can
   be late within a shift: the latest shift end, less the earliest window end, over the active
   vehicles and the orders not locked. A vehicle already on the road may leave before its shift
   starts, so the door term uses each active vehicle's start time, not its shift start. A daily
   problem's K is [BR-126]'s, as before.
5. **A vehicle with no driver is held as driver -1, and only a vehicle that is out may have
   one.** `Vehicles` refuses -1 on a vehicle that is not excluded for a re-optimization state, and
   refuses a *no driver* exclusion that names a driver. So a vehicle that can take a stop always
   has a driver, and the shift checks never read a missing one. The factory refuses an *active*
   vehicle with no driver, and a *no driver* vehicle that names one. A daily problem refuses -1
   and every re-optimization state.
6. **The factory places each vehicle in ADR-050's order:**
   - **Its stop in progress,** at the later of now and that stop's arrival plus its service. This
     is the last stop in the reported order, when its report is only *arrived*.
   - **Its usable position,** at now. A position is usable if it is at most 5 minutes old, inside
     the service area, and at most 200 km/h from the position before it, each limit inclusive
     ([BR-80], [NFR-168]). The speed is the great-circle distance between the two positions over
     the time between their receipts.
   - **Its last reported stop,** at now.
   - **Its start depot,** no earlier than now, its driver's shift start and the depot's opening
     ([BR-24]).

   Stops count as done in the order of their first report, ties in planned order ([BR-91]).
7. **A stop that stays on its vehicle but can't be routed pays no moved-stop cost.** A stop that
   lost its location or its road is dropped whatever the solver does, so the charge would only
   shift every solution's score by the same amount, as the orphans' constant does, without that
   constant's term to show it.
8. **On 2026-11-14 the owner, Naim Elijah, decided four rules:**
   - **A stop on a vehicle that went out before any stop is a [BR-106] orphan.** The vehicle may
     be unavailable, without a driver or out of shift. A solution that keeps a stop there breaks
     BR-106; a route on a vehicle with no driver also breaks [BR-20].
   - **An active vehicle that can drive no route counts as out.** Its position or depot may have no
     road to its end depot, or its depot no coordinates. Its stops are orphans.
   - **Pins bind daily solves only.** A re-optimization takes none, and the factory refuses a
     pinned order ([FR-91]).
   - **The optimizer's lock rule waits for [FR-147]'s milestone.** Until then, Planning's
     `RouteLocked` check stands ([FR-238]). [BR-10]'s locked stops are built.

   He also allowed one clause in [BR-126]. In a re-optimization, L also covers the longest a stop
   can be served late within a shift, and the rate is the larger of the door-window and lateness
   rates. Both rates start at 5, so K is BR-126's 5L until one of them is tuned.

## Consequences

- **Easy:** one definition of feasibility, cost and drop reasons serves both kinds of problem. No
  check reads a flag that says "re-optimization" where the arrays already hold the convention.
- **Easy:** a vehicle without a driver is a state the problem can hold, and the illegal one, a
  vehicle that can take a stop but has no driver, is refused when the problem is built.
- **Easy:** the catalogue names each re-optimization rule by its row, one case for each way it
  breaks ([NFR-169]).
- **Hard:** `VehicleExclusion` gains five values that only a re-optimization gives, and the
  contract's vehicle exclusions gain them when the re-optimization operation is added.
- **Hard:** K grows on a date where a window was lost long ago, as it grew in ADR-100 for door
  windows. Travel then weighs less against a drop, which keeps [BR-55]'s promise.
- **Cost:** one pass over the open orders for the lateness bound, and one sort of each vehicle's
  reported stops.
- **Risk:** the readings below are the narrowest the rows allow, and a pilot may show another is
  needed. Each is one place in `factory.py`, `feasibility.py` or `drops.py`.

## Alternatives considered

- **A copy of each check for re-optimization,** in its own module. Rejected: two definitions of
  feasibility, which [BR-42] forbids.
- **Every convention in the context, with `Vehicles` left as the daily depots.** Not chosen: every
  check that reads a vehicle's start, its end or its usability would then read both, and the drop
  reasons and K would need their own branches.
- **A vehicle with no driver left out of the problem.** Rejected: a withdrawn vehicle without a
  driver has a reported prefix and an end ([BR-52]), and the problem's vehicle indices would no
  longer match the request's.
- **A separate `has_driver` array beside a placeholder driver index.** Rejected: two fields for one
  fact, and a usable vehicle with the placeholder would still be representable.
- **L left as the daily one.** Rejected: a stop whose window was lost before the shift began can
  cost more in lateness than its drop, and the solver would drop it, against [BR-55] and ADR-049's
  derivation.

## Readings this ADR settles

These follow from the rows, and are recorded so they can be checked:

- [BR-81] names every vehicle whose latest position is not usable, whatever its state, including
  one placed at its stop in progress.
- A position with no position before it has no speed to judge, so only its age and area count.
- A stop that stays on its vehicle and that the vehicle cannot reach by road is dropped as
  *unreachable location* ([FR-66]), the reason for an order no allowed vehicle can reach.
- A vehicle already on the road leaves at now even before its shift starts: that it left is a
  fact ([BR-23]), and only its return is bound by the shift.
- `now` lies on the date, from 0 to the day's length, both included.

## Revisit when

- A pilot shows one of the readings above misleads a dispatcher.
- The re-optimization operation is added to the optimizer contract, and its vehicle states or drop
  reasons need a value this ADR does not give.
- The study tunes the lateness rate or the moved-stop cost away from 5 and 300 ([BR-115]).
- [FR-147]'s milestone arrives, or [FR-159] is built, and a locked route or a chosen-routes mode
  needs its own rule.

[ADR-044]: ADR-044-solver-port-takes-start-budget-and-seed-and-problem-judges.md
[ADR-049]: ADR-049-drop-penalty-unit-k-scaled-by-priority.md
[ADR-050]: ADR-050-re-optimization-conventions-live-only-in-the-python-problem-factory.md
[ADR-100]: ADR-100-k-covers-door-windows-outside-a-shift-and-no-vehicle-is-a-drop-reason.md
[BR-10]: ../requirements/requirements.md#re-optimization
[BR-20]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-23]: ../requirements/requirements.md#re-optimization
[BR-24]: ../requirements/requirements.md#re-optimization
[BR-39]: ../requirements/requirements.md#routing-constraints
[BR-40]: ../requirements/requirements.md#routing-constraints
[BR-42]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-51]: ../requirements/requirements.md#routing-constraints
[BR-52]: ../requirements/requirements.md#routing-constraints
[BR-55]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-56]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-80]: ../requirements/requirements.md#re-optimization
[BR-81]: ../requirements/requirements.md#re-optimization
[BR-91]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-106]: ../requirements/requirements.md#re-optimization
[BR-107]: ../requirements/requirements.md#re-optimization
[BR-108]: ../requirements/requirements.md#re-optimization
[BR-115]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-120]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-126]: ../requirements/requirements.md#solving-dropping-and-validation
[DL §4.9]: ../architecture/domain_layer/domain_layer_design.md#49-optimization-python
[FR-54]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-66]: ../requirements/requirements.md#daily-planning
[FR-91]: ../requirements/requirements.md#daily-planning
[FR-103]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-147]: ../requirements/requirements.md#manual-editing
[FR-159]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-238]: ../requirements/requirements.md#manual-editing
[FR-267]: ../requirements/requirements.md#reporting-and-baseline
[NFR-168]: ../requirements/requirements.md#nfr-168
[NFR-169]: ../requirements/requirements.md#nfr-169

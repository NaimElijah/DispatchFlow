# ADR-109: Reporting computes exact on-time shares from current facts and keeps no personal data

- **Status:** Accepted
- **Date:** 2026-11-14

## Context

The Reporting domain is built ahead of its milestone ([DL §4.7][DL-47]). It holds one aggregate,
`Baseline`, which is the nearest-neighbour plan that each published plan is compared with
([FR-39]). It also holds two values. `OnTimeRate` is the daily summary's on-time percentage
([FR-264]), and `PlannedOnTimeRate` is the planned on-time percentage that the comparison uses
([FR-266]). Every other part of Reporting is a read model, which a later milestone builds.

The domain document's on-time row said "reached by their window's end". The requirements say more,
and they win:

- A leave-at-door stop is on time only inside its preferred window ([FR-370]).
- A delivered stop with no arrival report counts its delivery time as its arrival ([FR-340]).
- Figures use the dispatcher's corrections ([FR-333]).
- Cancelled orders are not counted ([BR-30]).
- A cancelled plan has no baseline comparison ([FR-338]).

Reporting reads the facts of other contexts through the views they publish ([DL §2.3][DL-23], rule
3). Those facts are a stop's arrival and effective outcome from Live Operations, its window and
whether it may be left at the door from Customers & Orders, and the plan's state and planned times
from Planning. Reporting models each fact as its own value (rule 1). A re-optimization can withdraw
a vehicle mid-route, and then the comparison drops driver-hours and names the withdrawn vehicles
([FR-267] to [FR-271]). Each module may keep personal data only for its stated retention period
([NFR-31]), and reports keep their non-personal figures after a person's data is deleted
([NFR-152]).

## Decision

On 2026-11-14 the owner, Naim Elijah, decided one rule for every module, and one for the baseline:

1. **Each module owns its retention periods.** Reporting holds no personal data. A `Baseline` holds
   a plan ID, order and vehicle IDs, windows, planned times, metres and driver-hours. So Reporting
   defines no retention period: a baseline stays as long as its plan, and its figures outlive any
   person's data ([NFR-31], [NFR-152]).
2. **A baseline is recomputed when a revision withdraws a vehicle or adds orders.** [FR-39]
   compares a plan with its baseline "on the same orders and fleet". When a revision adds
   orders, for example a re-optimization that inserts a new order ([FR-35]), the baseline is solved
   again too, so both sides cover the same orders, kilometres included. `recompute` runs when the
   revision withdrew a vehicle, added orders, or both, and names what changed. Added orders alone
   keep the comparison on every measure; a withdrawn vehicle still drops driver-hours ([FR-269]).
   A cancellation is not an addition: it only leaves the order out of both planned on-time
   rates (reading 7).

### Readings this ADR settles

No document settles the rules below, so the code takes these readings.

1. **A rate is an exact share.** `OnTimeRate.Measured` and `PlannedOnTimeRate.Measured` keep both
   counts as integers. The domain never divides and never rounds; only a screen does. Counts that
   are not a share are refused with `InvalidOnTimeCounts`: a total below 1, or an on-time count
   below 0 or above the total.
2. **With nothing to count, the rate is a variant, not a number.** With no delivered stop,
   `OnTimeRate` is `Unavailable`, never 0 ([FR-265]). `PlannedOnTimeRate` is `Unavailable` with no
   order to count, for example when every order of the day is cancelled.
3. **A hard window is judged at its end, and a door window at both ends.** A stop with a hard window
   is on time when it is reached at or before the window's end ([FR-264]). An earlier arrival counts,
   because the vehicle waits ([BR-03]). A leave-at-door stop is served on arrival ([BR-122]), so it
   is on time only when it is reached inside its window, both ends included ([FR-370]). Every
   comparison is between instants. So on the day the clock goes back, an arrival in the second
   01:00 to 01:59 comes after a window that ends at the first 01:30 ([BR-111]).
4. **A stop is reached at its arrival, or else at its delivery.** The arrival and the delivery are
   corrected for the device's clock, as Live Operations records them ([BR-114], [FR-340]). When a
   stop has both, the arrival decides.
5. **Planned on-time judges the planned start of service.** An order is on time when its planned
   start lies inside its window, both ends included, for a hard window and a door window alike
   ([FR-266], [FR-370]). A dropped order counts as not on time. The validator never plans a hard
   window's start before its opening, and a door stop starts on arrival, so one rule serves both.
6. **Cancelled orders are left out of both rates, whatever happened to them.** That includes a stop
   *served after cancellation* ([BR-130]): [BR-30] says on-time figures no longer count a cancelled
   order. The cancelled orders arrive as one value, `CancelledOrders`, read from Customers & Orders'
   view at the time of the question.
7. **The baseline keeps each order's planned service, so a later cancellation leaves the order out
   of its planned on-time without a new solve.** `compareWith` takes today's cancelled orders and
   leaves them out of both sides' planned on-time ([BR-30]). The kilometres and driver-hours stay as
   solved, because a cancellation is not an addition, and only a withdrawal or an addition
   recomputes a baseline (decision 2).
8. **Figures follow corrections because they are computed when they are asked for.** `OnTimeRate`
   takes each stop's effective outcome: the latest correction, or else the first outcome. It is
   never stored, so a correction made after the plan completes changes the next figure
   ([FR-333]). Which outcome is effective is Live Operations' rule ([BR-18]), and Reporting doesn't
   restate it.
9. **A recompute adds what changed to what was named before.** A withdrawn route never returns
   ([FR-268]), so the cause, `Recomputed`, names every vehicle withdrawn so far ([FR-269]) and
   every order added so far, each once, in the order first named. A recompute that names neither
   changes nothing. `restore` refuses a stored `Recomputed` that names nothing, or names a vehicle
   or an order twice.
10. **The comparison follows the baseline's cause and the plan's standing.** After a publication it
    is on kilometres, driver-hours and planned on-time. After a withdrawal it is on kilometres and
    planned on-time, with the withdrawn vehicles named ([FR-269]); after added orders alone it is
    on every measure. For a cancelled plan it is
    `NotCompared` ([FR-338]). The plan's standing comes from Planning's view as Reporting's own
    `PlanStanding`. Reporting doesn't hear `PlanCancelled` ([DL §2.2][DL-22]).
11. **Each stop and each order is counted once.** A stop given twice to one rate is refused with
    `StopCountedTwice`, and an order given twice to one plan's figures with `OrderCountedTwice`. Two
    stops of one order count separately: after a put-back, the failed stop counts in neither part
    and the new stop counts if it is delivered ([FR-258]).
12. **Distances and driver-hours are exact and never negative.** `Distance` holds whole metres and
    `DriverHours` a `Duration`. A negative value is refused with `NegativeDistance` or
    `NegativeDriverHours`.
13. **Reporting raises no event.** [DL §7.2][DL-72] lists none, so there is no `reporting.events`
    package.

## Consequences

- **Easy:** a summary shows the same figure whatever rounding a screen picks, and two figures
  compare exactly.
- **Easy:** "unavailable" can't be mistaken for 0%, because the compiler makes every `switch` on a
  rate handle it.
- **Easy:** a correction, a late report or a cancellation shows in the next figure, with no stored
  summary to repair.
- **Easy:** a cancelled order leaves the baseline's on-time without a call to the optimizer.
- **Easy:** an order added after publishing is on both sides of the comparison ([FR-39]).
- **Hard:** each figure is computed when it is asked for, from three modules' views. A day has at
  most 200 orders ([BR-101]), so each figure is one pass over at most 200 rows.
- **Hard:** after a cancellation, the baseline's kilometres still include the detour to the
  cancelled order, while the plan's kilometres don't once the stop is removed.
- **Cost:** a baseline stores up to 200 planned services, beside its three figures.
- **Cost:** one nearest-neighbour solve for each revision that withdraws a vehicle or adds orders.
- **Risk:** Planning's `PlanRevised` names the withdrawn vehicles but not the added orders, so
  the listener must read those from Planning's view.
- **Risk:** Live Operations' view must give the effective outcome and the corrected times. If it
  gave the first outcome instead, the figures would ignore corrections, and no Reporting test would
  catch it.

## Alternatives considered

- **Store the rate as a percentage, or as a decimal rounded to two places.** Not chosen: the domain
  would round, two days would compare wrongly at the rounding edge, and "unavailable" would need a
  sentinel value.
- **Return 0 or an empty `Optional` for "no data".** Not chosen: 0 contradicts [FR-265], and an
  `Optional` doesn't say why it is empty.
- **Judge a leave-at-door stop at its window's end only, as the domain document said.** Not chosen:
  [FR-370] counts it on time only inside the window.
- **Count a stop served after cancellation.** Not chosen: [BR-30] leaves every cancelled order out
  of the on-time figures.
- **Recompute the baseline only after a withdrawal, as the domain document said.** Not chosen: an
  order added after publishing would be on the plan's side only, which breaks [FR-39]'s same
  orders.
- **Recompute the baseline when an order is cancelled.** Not chosen: a cancellation is not an
  addition, and leaving the order out of both on-time rates needs no solve.
- **Store the summary's figures when the plan completes.** Not chosen: a later correction or late
  report would leave the stored figures wrong ([FR-333], [BR-129]).
- **Replace the withdrawn vehicles on each recompute.** Not chosen: the comparison must name every
  withdrawn vehicle ([FR-269]), and a later re-optimization never brings one back ([FR-268]).

## Revisit when

- The company wants a cancelled order's detour taken out of the baseline's kilometres.
- Reporting starts to keep personal data, such as the per-driver report of [FR-109]. It then needs
  a retention period ([NFR-31]).
- A screen needs a figure over more than one day, such as the trends of [FR-41], and computing it
  from the views each time is too slow.

[BR-03]: ../requirements/requirements.md#routing-constraints
[BR-18]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-30]: ../requirements/requirements.md#orders-records-and-history
[BR-101]: ../requirements/requirements.md#time-and-limits
[BR-111]: ../requirements/requirements.md#time-and-limits
[BR-114]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-122]: ../requirements/requirements.md#routing-constraints
[BR-129]: ../requirements/requirements.md#orders-records-and-history
[BR-130]: ../requirements/requirements.md#driver-reports-and-outcomes
[DL-22]: ../architecture/domain_layer/domain_layer_design.md#22-relationships
[DL-23]: ../architecture/domain_layer/domain_layer_design.md#23-rules-between-contexts
[DL-47]: ../architecture/domain_layer/domain_layer_design.md#47-reporting
[DL-72]: ../architecture/domain_layer/domain_layer_design.md#72-the-events
[FR-35]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-39]: ../requirements/requirements.md#reporting-and-baseline
[FR-41]: ../requirements/requirements.md#reporting-and-baseline
[FR-109]: ../requirements/requirements.md#reporting-and-baseline
[FR-258]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-264]: ../requirements/requirements.md#reporting-and-baseline
[FR-265]: ../requirements/requirements.md#reporting-and-baseline
[FR-266]: ../requirements/requirements.md#reporting-and-baseline
[FR-267]: ../requirements/requirements.md#reporting-and-baseline
[FR-268]: ../requirements/requirements.md#reporting-and-baseline
[FR-269]: ../requirements/requirements.md#reporting-and-baseline
[FR-271]: ../requirements/requirements.md#reporting-and-baseline
[FR-333]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-338]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-340]: ../requirements/requirements.md#reporting-and-baseline
[FR-370]: ../requirements/requirements.md#reporting-and-baseline
[NFR-31]: ../requirements/requirements.md#nfr-31
[NFR-152]: ../requirements/requirements.md#nfr-152

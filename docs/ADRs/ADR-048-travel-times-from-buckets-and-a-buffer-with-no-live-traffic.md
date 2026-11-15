# ADR-048: Travel times come from time-of-day buckets and a buffer, with no live traffic

- **Status:** Accepted; amended by ADR-098
- **Date:** 2026-11-13

## Context

The requirements rule out live traffic: travel times come from the road network with time-of-day
multipliers, and ETA drift is a known limitation. The non-goal carries the marker "ADR pending: no
live traffic", and [FR-227], [FR-321] and [FR-322] cite [Q-20] for the buckets and factors.

OSRM, the road router, gives one base duration for each pair of locations and no time of day
([TS §9], [PL §7.1]). A planned travel time must still vary by time of day ([FR-90]). OR-Tools'
travel-time callback takes only from and to ([ADR-029]).

[ADR-029] fixed three buckets, a solver that plans at the highest factor, ETAs and a validator that
use each bucket's factor, and a buffer that starts at 10%. It left five points open under [Q-20]:
the buckets' hours, the factor of an arc that crosses a boundary, which factor the objective and the
lateness penalty use, whether the validator ignores the buffer, and what an early arrival costs at a
door stop. [ADR-032] added door stops that are served on arrival ([BR-122]) and left the last point
to Q-20.

On 2026-11-13 the owner, Naim Elijah, decided all five for the team. This ADR settles the marker "ADR
pending: no live traffic". It extends [ADR-029] and [ADR-032].

## Decision

- **No live traffic.** Travel time is the base duration scaled by a time-of-day factor and a
  buffer ([PL §7.1]):

  ```text
  t(i, j, departure) = ceil(d(i, j) * factor(bucket of departure) * (1 + buffer))
  ```

  `d` is OSRM's base duration in seconds. The static study uses factor 1 and buffer 0 ([NFR-110]).
- **Starting values.** The buckets are 07:00–09:30 (morning peak) and 15:30–18:30 (evening peak), and
  every other time is off-peak. Factors are 1.3, 1.3 and 1.0 ([FR-321]), and the buffer is 10%
  ([FR-227]). The hours, the factors and the buffer are estimates, not measurements, and are
  calibrated together from pilot arrivals. A research script fits the factors and the buffer from
  planned against actual leg times and proposes a settings change for review ([PL §14]).
- **An arc takes the factor of its departure bucket,** for its whole length, even when it ends in the
  next bucket. A bucket's hours are start-inclusive and end-exclusive, so 07:00–09:30 means
  [07:00, 09:30), and a departure's bucket is read from its local wall-clock time ([FR-408]).
- **Solvers search on the upper bound,** `d × highest factor × (1 + buffer)`, which is 1.43 times the
  base duration at the starting values. So a solved plan stays feasible all day ([FR-322]).
- **The validator and the ETAs use buffered times.** The validator re-times each route with the real
  bucket of every departure, so what is published is what was checked.
- **The objective's travel cost uses per-bucket times without the buffer,** because the buffer is a
  margin, not a cost. The lateness and door-window penalties use the validator's times, with the
  buffer ([BR-115], [BR-120]).
- **Door stops.** OR-Tools may plan a wait before a door stop and gets only a soft upper bound on its
  start. The validator re-times the route with no waiting ([BR-122]) and scores an early arrival as
  time outside the window ([BR-120]). The validator's score is the one stored, shown and compared, for
  every solver alike.
- **The upper bound stays, and M1 measures what it costs.** On the demo day M1 solves once at the
  upper bound and once at per-bucket times, re-times both and compares vehicles used and orders
  dropped. If the upper bound needs an extra vehicle, or drops an order the per-bucket solve serves,
  an ADR decides whether FR-322 changes.

### Why re-timing never breaks a plan

The validator re-times a route with factors no higher than the highest, which the search used. So
each re-timed arrival is no later than its upper-bound arrival: a departure that is no later arrives
no later, at a factor no higher than the highest. Waiting at a window absorbs the difference. A door
stop reached earlier moves later stops earlier, and they can still wait. The return to the depot is
no later, so the shift is met. The argument needs the search to use the highest factor, because
step-shaped factors do not keep "leave earlier, arrive earlier" true in general. If a solver ever
searches on time-dependent times, the validator becomes the only guard ([PL §7.1]).

### Why door stops are re-timed

OR-Tools can always wait before a node, so a soft lower bound on a door stop's start never charges
anything. A test scheduled the stop at 08:00 with no penalty, though the vehicle would arrive at
07:10. Forbidding waiting at some nodes only would need a custom constraint ([PL §8]).

## Consequences

- **Easy:** one travel-time model, applied in one place, the optimizer's `TravelTimeModel`
  ([DL §10.2]). The core never applies a factor, because the plan stores each leg's seconds and
  metres.
- **Easy:** the search runs on one upper-bound matrix, which OR-Tools registers instead of calling
  Python. On a 216-node, 15-vehicle test, registered matrices ran the same search in 0.44 s against
  1.97 s for callbacks ([PL §8]).
- **Hard:** a leg has three travel times. The solver searches on the upper bound, the validator and
  ETAs use buffered per-bucket times, and the travel cost uses unbuffered per-bucket times. Tests
  must name which one each rule reads.
- **Hard:** OR-Tools never sees what an early door arrival costs, so a route it proposes can score
  worse than it believed.
- **Cost:** a calibration script, and the M1 measurement.
- **Risk:** planning at the highest factor is cautious off-peak, so an order that would have fit may
  be dropped ([ADR-029]).
- **Risk:** the departure bucket decides a whole arc. A leg that leaves at 09:29 takes the peak
  factor for its whole length, and one that leaves at 06:59 takes the off-peak factor, though it runs
  into the peak.
- **Risk:** the hours, factors and buffer have no measurement behind them. ETA accuracy is judged by
  [NFR-33], and drift stays a stated limitation.

## Alternatives considered

- **Live traffic from a hosted matrix API (Google, Mapbox, HERE).** Rejected: it is priced per
  element, about 47,000 per solve, and the data leaves Israel ([TS §9]).
- **Time-dependent routing in Valhalla.** Rejected: its matrices are slower. Reconsider it only if
  vehicle-type road limits ([FR-118]) are built ([TS §9]).
- **One factor per plan, or fully time-dependent travel in the solvers.** Rejected by [ADR-029]: the
  first makes ETAs wrong around rush hour, and the second is hard in OR-Tools and could make the
  solver comparison less fair.
- **A validator that ignores the buffer.** Not chosen: a plan would be published with times that
  were not the ones checked.
- **The buffer as a cost in the objective.** Not chosen: it is a margin on arrival times, not a cost
  of travel.
- **Solvers that plan per bucket instead of at the upper bound.** Not chosen for now: re-timing could
  then break a plan. M1's measurement decides whether the cost of the upper bound justifies it.
- **Forbidding waiting at door stops inside OR-Tools.** Not chosen: it needs a custom constraint.
- **A solver that chooses each door stop's start time.** Rejected by [ADR-032]: the solution would
  carry start times, which changes the optimization contract.

## Revisit when

- M1's comparison shows that the upper bound needs an extra vehicle or drops an order the per-bucket
  solve serves, on the demo day.
- Pilot arrivals miss [NFR-33] after the factors, buffer and hours are calibrated.
- A solver is changed to search on time-dependent times, which ends the argument above.
- The company asks for live traffic. The non-goal is then reopened.

[ADR-029]: ADR-029-solver-objective-and-re-optimization-rules.md
[ADR-032]: ADR-032-leave-at-door-windows-are-preferences.md
[BR-115]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-120]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-122]: ../requirements/requirements.md#routing-constraints
[DL §10.2]: ../architecture/domain_layer/domain_layer_design.md#102-one-travel-time-model-in-python
[FR-90]: ../requirements/requirements.md#daily-planning
[FR-118]: ../requirements/requirements.md#daily-planning
[FR-227]: ../requirements/requirements.md#daily-planning
[FR-321]: ../requirements/requirements.md#daily-planning
[FR-322]: ../requirements/requirements.md#daily-planning
[FR-408]: ../requirements/requirements.md#daily-planning
[NFR-33]: ../requirements/requirements.md#nfr-33
[NFR-110]: ../requirements/requirements.md#nfr-110
[PL §7.1]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#71-time-model
[PL §8]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#8-or-tools-model
[PL §14]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#14-travel-times-and-geodata
[Q-20]: ../requirements/requirements.md#open-questions
[TS §9]: ../architecture/tech_stack.md#9-routing-and-geodata

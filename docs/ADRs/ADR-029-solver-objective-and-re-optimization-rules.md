# ADR-029: How both solvers trade drops, moved stops, lateness and travel time

- **Status:** Accepted
- **Date:** 2026-11-04

## Context

OR-Tools and our own solver share one objective and one validator ([ADR-004], [ADR-005], [BR-42]),
so these rules bind both, and the comparison depends on them. [Q-06], [Q-20], [Q-23], [Q-32], [Q-51]
and [Q-52] left them open. From Python, OR-Tools' travel-time callback takes only from and to, not the
time of day.

[ADR-005] says the study's values stay open until the academic advisor agrees them. The owner fixed
them on 2026-11-04 instead ([Q-29], [Q-30], [Q-84]), so this ADR extends it, and the advisor has not
seen them.

## Decision

- **Dropping:** a drop's penalty is proportional to 6 minus the order's priority, and large enough
  that no order drops just to save travel ([BR-54], [BR-55]). Several low-priority orders can outweigh
  one high-priority order.
- **Stability:** each stop that leaves its vehicle costs 5 minutes of travel time ([BR-115]). A dropped
  stop uses no arc, so arc costs cannot charge it: the charge is part of its drop penalty.
- **Lateness:** in a re-optimization only, a stop may stay past its window end within its driver's
  shift, flagged, at a cost of 5 minutes for each late minute ([BR-107], [BR-115]). A solve keeps hard
  windows ([BR-03]).
- **Goods:** a stop changes van only if its van is out or it is a new order ([BR-106], [BR-117]), and the preview
  flags those for a hand-over ([FR-334]). No depot visit is modelled.
- **Locked stops:** a stop locks when its driver marks it *arrived* ([BR-10]).
- **Travel time:** solvers plan with the highest factor, and ETAs and the validator use each bucket's
  factor ([FR-321], [FR-322]). Arrival is never later than the plan assumed, and a vehicle that arrives
  early waits ([BR-03]), so a plan stays feasible all day. The buckets' hours, the objective's factor
  and the buffer's effect on the validator stay open ([Q-20]).
- **Settings:** objective code lives in the solver's `domain`. Weights and penalties travel in the problem
  ([ADR-004]), from `core`'s settings or the research harness's configuration.
- **Study values:** fixed as [Q-29], [Q-30] and [Q-84] say, which replaces [ADR-005]'s clause that they
  stay open until the advisor agrees. The gaps left are in [Q-93].

The penalties are starting points, tuned on a separate set and frozen before the study's measured runs.
None of this has been tried in either solver.

## Consequences

- **Easy:** fewer stops are dropped, because a late delivery can replace a drop. A driver is not sent
  someone else's stops to save a few minutes.
- **Hard:** both solvers and the validator must treat windows as hard in a solve and soft in a
  re-optimization, and must restrict which vans each stop may use.
- **Cost:** changes to both solvers and the validator, and tuning runs before the study.
- **Risk:** a high-priority order can be dropped to save several low-priority ones.
- **Risk:** a late driver cannot shed stops to another van, so re-optimization has less room to rescue
  a late route, and the stability measure ([FR-256]) moves less. Stability then varies
  only through drops, which weakens [ADR-005]'s fallback to a stability focus.
- **Risk:** planning at the peak factor is cautious off-peak, so an order that would have fit may drop.
- **Risk:** the advisor may change the study's values, and the targets would move with them.

## Alternatives considered

- **Strict drop penalties,** each priority outweighing all lower ones. Not chosen: it needs a rule for
  pins and locks, and a many-for-one trade is acceptable to the owner.
- **Hard windows in a re-optimization.** Not chosen: a stop 10 minutes late would drop with its goods
  on the van.
- **Any stop may move between vans,** with the preview naming transfers. Rejected: only the stability
  penalty would stop the solver asking for a transfer to save minutes.
- **Strictly no moves between vans.** Rejected: a broken van's stops and a new order would have nowhere
  to go, which breaks [FR-07], [FR-11], [FR-82] and [NFR-25].
- **Modelling a depot reload.** Rejected for now: a large change to both solvers and the validator.
- **One travel-time factor per plan,** or fully time-dependent travel. Rejected: the first makes ETAs
  wrong around rush hour, and the second is hard in OR-Tools and could make the comparison less fair.

## Revisit when

- The pilot shows high-priority orders dropped to save smaller ones.
- The company moves goods between vans routinely, or needs vans to reload during the day ([FR-111]).
- Pilot arrivals show the peak factor drops orders that would have fit.
- The advisor asks for different study values.

[ADR-004]: ADR-004-pluggable-solver-port.md
[ADR-005]: ADR-005-or-tools-first-research-solver-for-re-optimization.md
[BR-03]: ../requirements/requirements.md#routing-constraints
[BR-10]: ../requirements/requirements.md#re-optimization
[BR-42]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-54]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-55]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-106]: ../requirements/requirements.md#re-optimization
[BR-107]: ../requirements/requirements.md#re-optimization
[BR-115]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-117]: ../requirements/requirements.md#re-optimization
[FR-07]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-11]: ../requirements/requirements.md#customers-and-orders
[FR-82]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-111]: ../requirements/requirements.md#daily-planning
[FR-256]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-321]: ../requirements/requirements.md#daily-planning
[FR-322]: ../requirements/requirements.md#daily-planning
[FR-334]: ../requirements/requirements.md#live-operations-and-re-optimization
[NFR-25]: ../requirements/requirements.md#nfr-25
[Q-06]: ../requirements/requirements.md#open-questions
[Q-20]: ../requirements/requirements.md#open-questions
[Q-23]: ../requirements/requirements.md#open-questions
[Q-29]: ../requirements/requirements.md#open-questions
[Q-30]: ../requirements/requirements.md#open-questions
[Q-32]: ../requirements/requirements.md#open-questions
[Q-51]: ../requirements/requirements.md#open-questions
[Q-52]: ../requirements/requirements.md#open-questions
[Q-84]: ../requirements/requirements.md#open-questions
[Q-93]: ../requirements/requirements.md#open-questions

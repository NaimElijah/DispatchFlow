# ADR-050: Re-optimization conventions live only in the Python problem factory

- **Status:** Accepted; extended by ADR-105
- **Date:** 2026-11-13

## Context

A re-optimization starts from a live day, not an empty one. Some stops are done, some vehicles have
no usable position, some windows are already lost, and a stop may only move off its vehicle in
narrow cases. The requirements fix these conventions:

- facts are not violations: a locked stop that ran late never fails the solution ([BR-23]);
- a vehicle with no usable position starts from its last reported stop, or from its start depot
  ([BR-24]);
- a position is not usable when it is older than 5 minutes, outside the service area, or implies a
  speed above 200 km/h ([BR-80]).

The rows carry the marker "ADR pending: validator and reoptimization problem conventions". [ADR-044]
left the re-optimization conventions that `ProblemFactory` applies to their own pending ADR.

On 2026-11-13 the owner, Naim Elijah, decided for the team where the conventions live. This ADR settles
the marker and extends [ADR-044]. It follows the plan's design in [PL §9], which will be deleted, so
it keeps the conventions' steps.

## Decision

- **The core sends raw facts, and the Python `ProblemFactory.reoptimization` applies every
  convention.** It turns them into an ordinary, smaller problem. The product and the disruption
  simulation call the same function ([PL §9], [DL §4.9]). So [BR-10], [BR-23], [BR-24], [BR-51],
  [BR-79], [BR-80], [BR-91], [BR-106] to [BR-108] and [BR-117] exist once, in Python ([BR-42]).
- **The core sends** ([PL §9.1]):
  - `now`, and the routes of the plan's latest revision;
  - each stop's delivery status and actual times;
  - each vehicle's state: active, unavailable, no driver, out of shift, finished or withdrawn;
  - each vehicle's last two positions, with their server receive times and an in-service-area flag
    (the core holds the service-area polygon, [TS §9]);
  - new and put-back orders;
  - the matrix, with an extra source row for each vehicle's latest position;
  - the solver, limit, seed, and the chosen routes if [FR-159] is used.
- **`ProblemFactory.reoptimization` does six things** ([PL §9.2]):
  1. **Fixes what happened** ([BR-10], [BR-91]). Delivered and failed stops leave the problem. A stop
     marked *arrived* is in progress, and the vehicle's new start is that stop, at `max(now, arrival +
     service)`. Stops reported out of order count as done in the order reported.
  2. **Places each vehicle** ([BR-79], [BR-80], [BR-24], [BR-51]). It uses the latest position if it
     is usable, else the last reported stop, else the start depot. It returns the vehicles with no
     usable position for the preview ([BR-81]).
  3. **Decides which vehicle each stop may go to** ([BR-106], [BR-117], [BR-108]). A stop on an active
     vehicle may only stay there. Stops on a vehicle that is out become orphans, allowed on any
     active vehicle. New, dropped and moved-to-the-date orders may go anywhere. A failed stop put
     back stays with its vehicle. Finished and withdrawn routes get nothing.
  4. **Relaxes windows that are already lost** ([BR-107]). A non-door stop keeps a hard window start.
     Its window end becomes a soft bound at 5 per second late, with the vehicle's latest return as the
     hard limit. A daily solve never does this.
  5. **Charges for instability** ([FR-37], [BR-115]). It adds 300 seconds to the drop penalty of every
     stop now on a vehicle. The orphans' 300 seconds each is a constant: scored, but unable to steer
     the search.
  6. **Builds the warm start** from the current remaining sequences, with orphans left out ([FR-280]).
- **The validator applies the same conventions.** The reported prefix is taken as fact, and only what
  follows `now` is checked ([BR-23], [PL §7.3]).
- **The optimizer stores nothing.** Its answer is the remaining routes with new times, the drops, the
  late minutes, the objective by term and the validator's report. The core stores it as an immutable
  proposal ([PL §9.3]).

## Consequences

- **Easy:** one definition of every convention, so the product and the study score a re-optimization
  identically ([BR-42], [BR-58]).
- **Easy:** the core holds no solver rule. A convention changes in one place.
- **Hard:** the request carries everything the conventions need. The optimizer keeps no state, so the
  core must send complete facts each time, including the extra matrix rows.
- **Hard:** one convention, the service area, splits across the services. The core computes the flag
  and Python decides what an out-of-area position means.
- **Cost:** the conventions arrive in M9. The Alpha has the daily-solve rules only ([DL §4.9]).
- **Risk:** a fact the core forgets to send is invisible to the optimizer, which has no way to ask for
  it.
- **Risk:** the 300-second instability charge on orphans is a constant, so it cannot influence which
  vehicle takes them.

## Alternatives considered

- **The conventions in the Java core,** which would send an ordinary problem. Rejected: the
  validator, the objective, the conventions, the bucket factors and every drop reason live only in
  Python, so one definition of feasibility serves the product and the research ([DL §16], [ADR-044]).
  The simulation would need its own copy.
- **Each solver applying the conventions itself.** Rejected: [ADR-004] has every solver judged by one
  validator and one objective, and the adapters would drift apart.
- **The core deciding what a usable position is.** Not chosen: age and speed are conventions of
  [BR-80], which Python applies. Only the area test needs the polygon that the core holds, so only its
  flag crosses.

## Revisit when

- A convention needs data that the optimizer cannot be sent as a fact or a flag.
- The study finds the simulation and the product disagreeing on the same live state, which would mean
  a convention sits outside `ProblemFactory`.
- M9's measurement shows that building the problem and the extra matrix rows cost more of the 10
  seconds that [NFR-07] leaves beyond the 20-second search than they can spare ([PL §9.3]).

[ADR-004]: ADR-004-pluggable-solver-port.md
[ADR-044]: ADR-044-solver-port-takes-start-budget-and-seed-and-problem-judges.md
[BR-10]: ../requirements/requirements.md#re-optimization
[BR-23]: ../requirements/requirements.md#re-optimization
[BR-24]: ../requirements/requirements.md#re-optimization
[BR-42]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-51]: ../requirements/requirements.md#routing-constraints
[BR-58]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-79]: ../requirements/requirements.md#re-optimization
[BR-80]: ../requirements/requirements.md#re-optimization
[BR-81]: ../requirements/requirements.md#re-optimization
[BR-91]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-106]: ../requirements/requirements.md#re-optimization
[BR-107]: ../requirements/requirements.md#re-optimization
[BR-108]: ../requirements/requirements.md#re-optimization
[BR-115]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-117]: ../requirements/requirements.md#re-optimization
[DL §4.9]: ../architecture/domain_layer/domain_layer_design.md#49-optimization-python
[DL §16]: ../architecture/domain_layer/domain_layer_design.md#16-why-it-looks-like-this
[FR-37]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-159]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-280]: ../requirements/requirements.md#research-and-evaluation
[NFR-07]: ../requirements/requirements.md#nfr-07
[PL §7.3]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#73-the-validator
[PL §9]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#9-re-optimization
[PL §9.1]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#91-what-the-core-sends-post-v1reoptimize
[PL §9.2]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#92-what-problemfactoryreoptimization-does
[PL §9.3]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#93-what-comes-back-and-what-happens-to-it
[TS §9]: ../architecture/tech_stack.md#9-routing-and-geodata

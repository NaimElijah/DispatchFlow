# ADR-060: The system refuses a sixteenth vehicle and a date's 201st order

- **Status:** Accepted
- **Date:** 2026-11-13

## Context

Decided on 2026-11-13: how the system holds its size limits,
the rule the requirements mark "ADR pending: hard limits on fleet and plan size" ([BR-36],
[BR-101]).

The limits are 15 vehicles ([NFR-04]) and 200 stops per plan ([NFR-05]). Q-90 settled on 2026-11-04
that they are final, and that a larger company reopens them, and [NFR-07] with them ([Q-90]). Serving
enterprise-scale fleets is a non-goal ([Non-goals]). The performance targets are stated and measured at exactly this
scale:

- a re-optimization preview within 30 seconds at the 95th percentile ([NFR-07]);
- the travel-time matrix built within 5 seconds ([NFR-57]);
- an incremental re-optimization with a 5-second limit ([NFR-25]);
- both apps' maps at 30 frames a second on 200 stops and 15 routes ([NFR-109]);
- the API load test with 15 phones on a plan of 200 stops ([NFR-10]).

What counts toward the order limit was settled in [Q-70] and [BR-113]. Orders move between dates
([FR-258], [FR-315]), so no aggregate holds a date's orders ([DL §5][DL-5]).

## Decision

- **A limit is a refusal, not a warning.** Adding a sixteenth vehicle is refused, and an order beyond
  the date's limit is refused. The message names the limit ([BR-36], [BR-101]).
- **The vehicle limit counts active vehicles.** A retired vehicle does not count ([BR-36], [FR-307]).
  `FleetRegister` owns it, in `addVehicle` and `reactivateVehicle`, which refuse with `FleetFull`.
  The register is one aggregate and its version check makes a lock unnecessary
  ([DL §4.2][DL-42], [PL §11.2][PL-112]).
- **The order limit counts a date's uncancelled orders,** failed ones included, and a put-back
  does not count again ([BR-113]). It is checked whenever an order joins a date: import, entry,
  copy, put-back or date change.
- **`DateCapacity` owns the order limit.** Every method that adds an order to a date requires one.
  It is built only by the repository query that reads the date's orders under a transaction-scoped
  advisory lock on that date, and it refuses with `DateFull` ([DL §5][DL-5]).
- **No new drop reason is needed:** an order beyond the limit never enters the plan ([Q-70]).

## Consequences

- **Easy:** every performance target holds by construction at the sizes it was measured at, and a
  load test at 15 vehicles and 200 stops is the worst case.
- **Easy:** the dispatcher learns of the limit when entering the order, and can act then (move it to
  another date), not when a solve drops it.
- **Hard:** a day with 201 orders cannot be planned in one plan. The company must move an order to
  another date.
- **Hard:** the order limit needs a lock on the date, and every path that adds an order must go
  through `DateCapacity`. An ArchUnit rule allows no other caller of its factory ([DL §5][DL-5]).
- **Cost:** two owners and two backstops (an invariant on `FleetRegister`, a snapshot read under a
  lock) in place of one general mechanism.
- **Risk:** the limits are fixed rules, final for one company ([Q-90]). A company that exceeds them
  needs a change to the rules and a new measurement.
- **Risk:** the date lock serializes concurrent imports and entries for one date. The request holds
  it for milliseconds ([DL §11][DL-11]); that is the design's figure, not a measurement.

## Alternatives considered

- **No hard limit, only the performance targets.** Rejected: the targets are measured at 15 vehicles
  and 200 stops, so above them no figure is known to hold ([NFR-181]).
- **Accept the order and drop it in the solve.** Rejected: [Q-70] settled that no new drop reason
  is needed, and the dispatcher would learn of the limit only after a solve.
- **A per-date aggregate that holds the date's orders.** Rejected: an order can move from one date to
  another, so a move would change two aggregates in one transaction ([DL §5][DL-5]).
- **A limit that an Admin can raise in settings.** Rejected: [Q-90] made the limits final, and the
  targets above are measured only at these sizes.

## Revisit when

- A company with more than 15 vehicles, or days of more than 200 orders, wants DispatchFlow, or the
  pilot company's real day exceeds a limit ([Q-90], [Q-01]). Raise the limits, then measure every
  target in the list above again.
- A measurement at 200 stops and 15 vehicles meets a target with a wide margin, and the company asks
  for more. Raising the limits is then a measurement, not a guess.

[BR-36]: ../requirements/requirements.md#time-and-limits
[BR-101]: ../requirements/requirements.md#time-and-limits
[BR-113]: ../requirements/requirements.md#time-and-limits
[FR-258]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-307]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-315]: ../requirements/requirements.md#customers-and-orders
[NFR-04]: ../requirements/requirements.md#nfr-04
[NFR-05]: ../requirements/requirements.md#nfr-05
[NFR-07]: ../requirements/requirements.md#nfr-07
[NFR-10]: ../requirements/requirements.md#nfr-10
[NFR-25]: ../requirements/requirements.md#nfr-25
[NFR-57]: ../requirements/requirements.md#nfr-57
[NFR-109]: ../requirements/requirements.md#nfr-109
[NFR-181]: ../requirements/requirements.md#nfr-181
[Non-goals]: ../requirements/requirements.md#non-goals
[Q-01]: ../requirements/requirements.md#open-questions
[Q-70]: ../requirements/requirements.md#open-questions
[Q-90]: ../requirements/requirements.md#open-questions
[DL-11]: ../architecture/domain_layer/domain_layer_design.md#11-concurrency
[DL-42]: ../architecture/domain_layer/domain_layer_design.md#42-fleet
[DL-5]: ../architecture/domain_layer/domain_layer_design.md#5-rules-over-sets-of-aggregates
[PL-112]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#112-rules-every-use-case-follows

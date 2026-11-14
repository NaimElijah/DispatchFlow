# ADR-067: Live arrival times propagate delay along the stored legs, and are computed when asked

- **Status:** Accepted
- **Date:** 2026-11-14

## Context

On 2026-11-14 the owner, Naim Elijah, decided for the team that the domain layer design (DL) is
settled, and that its projected arrivals get their own ADR. No "ADR pending" marker covers this
decision. The implementation plan lists it among the decisions that need an ADR ([PL §25][PL-25]).

Three requirements need a projected arrival time while a route is in progress:

- a stop is at risk when its projected arrival is later than its window end minus 15 minutes
  ([FR-253]);
- a stop's projected arrival is its planned arrival plus the route's observed delay ([FR-254]);
- a route's observed delay is the actual time of its latest event, its start or a stop's arrival or
  outcome, minus that event's planned time ([FR-383]).

Messaging also needs them, every minute, for "arriving soon" and "time changed" ([FR-352], [FR-353]).

The forces:

- **One travel-time model.** The Python optimizer alone applies the bucket factors and the buffer
  ([DL §10.2][DL-102], [ADR-048]). The core must never apply a factor.
- **The optimizer may be down.** Published routes must keep working then ([NFR-42]).
- **A projection depends on `now`.** It changes as time passes, not only when something is reported.
- **The domain never reads the clock** ([DL §10.1][DL-101]).

## Decision

**A projection is a value, `ArrivalProjection.of(legs, facts, now)`, computed when asked and never
stored** ([DL §10.4][DL-104]). It does not re-apply factors. It propagates what has happened along
the legs the optimizer stored for the current revision. Each leg's travel seconds and metres come
back with the routes, and the plan stores them ([DL §10.2][DL-102]).

1. **Leaving the last reported stop:** `d` is the outcome's corrected time if that stop has one. If it
   is only *arrived*, `d = max(arrival + service, now)`, because the vehicle is still there. Before
   the first report, the planned times stand.
2. **The next stop:** `arrival = max(d + leg, now)`. The vehicle is either still on the leg, or it
   is late and has not reported. In both cases it cannot arrive before now.
3. **Every stop after that:** `start = max(arrival, window start)` for a stop with a hard window, so
   waiting absorbs delay. For a leave-at-door stop, `start = arrival`, because the vehicle never
   waits there ([BR-122], [ADR-032]). Then `next arrival = start + service + leg`.

**A delay that pushes a departure into another bucket keeps the planned bucket.** Exact re-timing
happens whenever the optimizer produces a new revision.

**A projection informs and never decides.** It feeds messages and at-risk alerts ([FR-253],
[FR-254], [FR-383]). It never decides feasibility, which stays with the validator.

**Who asks:**

- Messaging's job runs every minute, asks `ArrivalProjections` for the routes in progress and calls
  `StopMessages.onArrivalProjected` ([DL §10.3][DL-103]).
- Live Operations' minute job raises the at-risk alerts from the same projections
  ([PL §13.3][PL-133]).
- No event announces a changed projection ([DL §7.4][DL-74], [ADR-064]).

## Consequences

- **Easy:** live arrival times keep working while the optimizer is down, and the core still never
  applies a factor.
- **Easy:** the rule is a pure function of its inputs and `now`, so a test fixes `now` and needs no
  clock.
- **Easy:** a late report moves every later arrival on the route with it, and waiting at a window
  start absorbs the delay, as it does on the road.
- **Hard:** the projection is only as good as the stored legs. It does not see a delay that changes
  later travel times.
- **Hard:** a projection is recomputed for every route in progress on every ask. Nothing caches it.
- **Cost:** a minute job in Messaging and one in Live Operations, each reading the legs of the current
  revision.
- **Risk:** keeping the planned bucket understates a delay that carries a vehicle into the evening
  peak, and an at-risk alert can come late. The peak hours are an estimate to calibrate
  ([ADR-048]).
- **[FR-254] states this method.** It said "planned arrival plus the route's observed delay", which
  differs when waiting absorbs the delay or `now` binds. The owner chose this ADR's method on
  2026-11-14, and FR-254 was reworded to match.

## Alternatives considered

- **Re-time every route through the optimizer on each report.** Rejected: live arrival times would
  stop when the optimizer is down. The optimizer re-times when it produces a new revision
  ([DL §16][DL-16]).
- **Apply the bucket factors in the core.** Rejected: the Python `TravelTimeModel` is the only place factors and
  the buffer are applied ([DL §10.2][DL-102]).
- **Add the observed delay to every planned arrival,** as [FR-254] reads literally. Not chosen: it
  ignores that waiting absorbs delay and that an arrival cannot lie before now.
- **Store projections, and raise an event when one changes.** Rejected: projections change as time
  passes, not only when something is reported, so Messaging asks every minute instead
  ([DL §7.4][DL-74]).

## Revisit when

- The pilot's arrivals show the projection's error is systematically large after a delay crosses into
  a peak bucket. Re-time projections with the optimizer's bucket factors.
- A recipient tracking page is built ([DL §15][DL-15]) and asks for projections on every page load.
  Measure the cost of the ask first.

[ADR-032]: ADR-032-leave-at-door-windows-are-preferences.md
[ADR-048]: ADR-048-travel-times-from-buckets-and-a-buffer-with-no-live-traffic.md
[ADR-064]: ADR-064-events-only-where-they-earn-their-place-through-the-modulith-registry.md
[BR-122]: ../requirements/requirements.md#routing-constraints
[DL-15]: ../architecture/domain_layer/domain_layer_design.md#15-growth-likely-changes-and-where-they-go
[DL-16]: ../architecture/domain_layer/domain_layer_design.md#16-why-it-looks-like-this
[DL-74]: ../architecture/domain_layer/domain_layer_design.md#74-considered-and-left-out
[DL-101]: ../architecture/domain_layer/domain_layer_design.md#101-principles
[DL-102]: ../architecture/domain_layer/domain_layer_design.md#102-one-travel-time-model-in-python
[DL-103]: ../architecture/domain_layer/domain_layer_design.md#103-every-rule-that-depends-on-time
[DL-104]: ../architecture/domain_layer/domain_layer_design.md#104-projected-arrivals
[FR-253]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-254]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-352]: ../requirements/requirements.md#messages-to-recipients
[FR-353]: ../requirements/requirements.md#messages-to-recipients
[FR-383]: ../requirements/requirements.md#live-operations-and-re-optimization
[NFR-42]: ../requirements/requirements.md#nfr-42
[PL-25]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#25-decisions-to-record-as-adrs
[PL-133]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#133-driver-report

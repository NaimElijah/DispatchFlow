# ADR-100: K's L covers door windows outside a shift, and *no vehicle available* is a drop reason

- **Status:** Accepted; amended by ADR-105
- **Date:** 2026-11-14

## Context

The optimizer's domain found two gaps while it was built ([DL §4.9]).

**K's bound fails for a door window wholly outside its driver's shift.** [ADR-049] sets the drop
unit K = 5L + 2T + F + 1, with L the date's longest shift. Its derivation assumes a leave-at-door
stop is outside its window for at most L seconds. That holds when the window meets the shift
somewhere. It fails when the window lies wholly outside it, because a door stop is served on
arrival within the shift ([BR-119]), however far that is from its window. Its window penalty
([BR-120]) can then pass 5L, and serving the order alone can cost more than K. The solver may then
drop it to avoid the penalty, which [BR-121] forbids.

An example: a shift of 12:00 to 20:00, so L is 28,800 seconds, and a door order with a window of
06:00 to 07:00, 14,000 seconds from the depot. Served alone, it arrives at 15:53, 32,000 seconds
after its window ends. Travel of 28,000 and a window penalty of 160,000 cost 188,000. K is
5 × 28,800 + 2 × 14,000 + 1 = 172,001, so dropping the order is cheaper.

**[FR-223] has no reason for a date with no usable vehicle.** Every vehicle can be out, for example
when every depot lacks coordinates ([FR-197]). Every order of the date is then dropped. The code
reported *capacity*, with a largest capacity of 0 kg: true in arithmetic, and misleading to a
dispatcher, who would look at the order instead of the depots.

On 2026-11-14 the owner, Naim Elijah, decided both, and allowed the edits to [BR-126] and
[FR-223]. He also allowed one phrase of [BR-121] to be aligned with the new L: the window penalty
"the longest shift on its date allows" became the one "a leave-at-door stop on its date can
incur". This ADR amends [ADR-032]'s statement of that bound, [ADR-049]'s definition of L and
[ADR-053]'s list and order of drop reasons. The rest of each stands.

## Decision

1. **L is the larger of the date's longest shift and D** ([BR-126]). D is the longest a
   leave-at-door stop can spend outside its window during a shift on that date.
   - `ProblemFactory.daily` computes D over every door order, window [w0, w1], and every usable
     vehicle's rostered shift [s0, s1], as the validator applies that shift: a door stop is served
     on arrival, no earlier than the shift starts, and [BR-119] refuses a start after it ends. Each
     pair gives max(0, w0 − s0, s1 − w1), and D is the largest. Each term depends on the order
     alone and the shift alone, so D is computed from the extremes: max(0, the latest w0 − the
     earliest s0, the latest s1 − the earliest w1).
   - A window may lie off the date, but its start and end must each lie within 2,147,483,647
     seconds of the date's start, the int32 range of [ADR-052]'s matrices. One door window sets K
     for the whole date, even an order's that is kept out of the search, so an absurd one would
     otherwise drown the travel terms or overflow K. This refuses absurd inputs and does not cap D.
   - D is at most the day's length when the windows lie on the date, because every shift does
     ([FR-190]). The code does not cap it explicitly: a cap would understate D for a window the
     problem holds off the date, and K's promise would fail again.
   - `PenaltyScale.for_date` takes D as its own argument and applies the formula. The rate in 5L
     stays the door-window rate, so K still follows the rate when it is tuned ([ADR-049]).
   - In the example above, D is 46,800 seconds (20:00 less 07:00), and K is 262,001.
2. **A drop on a date with no usable vehicle has the reason *no vehicle available*** ([FR-223]).
   Like *missing location*, it carries no field. Its fact is the date's vehicle exclusions, which
   already name each vehicle and its cause ([FR-144], [FR-197]).
   - `explain_drop` tests it **second**: after *missing location*, and before *unreachable
     location* and every other test. A fact about the whole date comes before any per-vehicle
     test. A missing location stays the order's own fault even when a vehicle is added, so it
     still comes first.
   - The order of [ADR-053] becomes, as `explain_drop` applies it: missing location; no vehicle
     available; unreachable location, then a dispatcher's removal; an impossible pin; capacity;
     time window; shift, depot hours or vehicle limit; displaced.
   - *Capacity* against 0 kg is no longer given.

## Consequences

- **Easy:** [BR-121] holds for every door window, wherever it lies against the shift. The property
  "K exceeds the cost of serving any one order" now draws door windows wholly before and after
  the shift.
- **Easy:** a dispatcher sees why a date with no vehicle drops everything, and fixes the depots or
  the roster instead of the orders.
- **Hard:** K is larger on dates with far-off door windows, so the drop penalty scale grows. On a
  date whose windows lie on it, D is at most the day's length, which makes 5L up to 5 × 90,000
  seconds on a 25-hour date. A window off the date can raise it further, up to the int32 bound.
  Travel then weighs less against a drop, which is the intent, but the gap between the terms
  widens.
- **Hard:** a problem with a window more than 2,147,483,647 seconds from its date's start is
  refused with `InvalidProblemError`, naming the window.
- **Hard:** the drop reasons in the optimizer contract and the core's sentence for each gain one
  value when the solve operations are added.
- **Cost:** one pass over the door orders and one over the usable vehicles per problem, because D
  comes from their extremes, not from every pair.
- **Risk:** D counts every door order, including one kept out of the search, so K can be larger
  than any order that can be served needs. It is never smaller.

## Alternatives considered

- **Keep [BR-126] as written,** with L the longest shift. Rejected: a door order whose window lies
  wholly outside its driver's shift can cost more to serve than to drop, against [BR-121].
- **Cap D at the day's length.** Not chosen: a no-op when every window lies on the date, and wrong
  for a window that does not.
- **Keep *capacity* at 0 kg** for a date with no usable vehicle. Rejected: it names the order as
  the cause when the date has no vehicle at all.
- **Place *no vehicle available* first,** before *missing location*. Rejected: an order with no
  coordinates is dropped even when a vehicle is added, so its own reason is the one to fix.

## Revisit when

- The academic advisor asks for a different form of K ([ADR-049]).
- Windows are required to lie on their date. An explicit cap on D would then be harmless.
- The re-optimization conventions are built (M9). [BR-115]'s lateness penalty for a stop past its
  window needs the same bound.
- Vehicle limits ([FR-201]) are built, and a date whose vehicles are all over a limit needs a place
  in the order.
- Pilot dispatchers misread *no vehicle available*.

[ADR-032]: ADR-032-leave-at-door-windows-are-preferences.md
[ADR-049]: ADR-049-drop-penalty-unit-k-scaled-by-priority.md
[ADR-052]: ADR-052-each-plan-stores-the-matrix-and-values-it-was-solved-with.md
[ADR-053]: ADR-053-plan-lifecycle-publishing-stability-drop-reasons-and-abandoned-plans.md
[BR-115]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-119]: ../requirements/requirements.md#routing-constraints
[BR-120]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-121]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-126]: ../requirements/requirements.md#solving-dropping-and-validation
[DL §4.9]: ../architecture/domain_layer/domain_layer_design.md#49-optimization-python
[FR-144]: ../requirements/requirements.md#daily-planning
[FR-190]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-197]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-201]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-223]: ../requirements/requirements.md#daily-planning

# ADR-032: A leave-at-door order's time window is a preference, priced into the objective

- **Status:** Accepted
- **Date:** 2026-11-10

## Context

Avi Segal, the seminar mentor, asked on 2026-11-10 that an order can be marked to be left at the
door. Nobody needs to be home for such an order, so its time window matters less. The owner chose
on the same day to keep the window as a preference rather than drop it, and to serve such a stop
when the van arrives.

[ADR-029] made windows hard in a solve and soft only in a re-optimization, where a late minute costs
5 minutes of travel time ([BR-107], [BR-115]). The feasibility validator is the one judge of every
solution, and both solvers are scored by one objective ([BR-42], [BR-09]).

## Decision

- **A dispatcher marks an order** as one that may be left at the door ([FR-364]), and can change the
  mark as other fields ([FR-57]).
- **Its window is a preference in solves and re-optimizations:** service may start outside it,
  within the driver's shift and on the order's date ([BR-119]). This narrows ADR-029's "a solve keeps
  hard windows" for these orders only; every other order keeps it.
- **The van never waits at a door stop:** it serves the stop when it arrives ([BR-122]), so the
  stop's start time follows from the route. Solvers plan with the highest travel-time factor and
  the validator uses each bucket's ([FR-322]), so a van can reach a door stop earlier than planned
  and be charged for it; [Q-20] keeps that open.
- **Time outside the window costs 5 times its length** in the objective, early or late ([BR-120]).
  It replaces the lateness penalty of [BR-115] for that stop, so no minute is charged twice, and it
  is tuned with that penalty.
- **The order is never dropped for its window:** its drop penalty exceeds the largest window penalty
  the longest shift on its date allows ([BR-121]). It never raises an at-risk alert ([FR-369]), and
  the on-time percentages count it on time only inside its window ([FR-370]).
- **The validator accepts such a stop outside its window,** within the driver's shift and on the
  order's date ([NFR-194]).
- **When nobody takes it,** the driver may leave it at the door, with a photo as its proof ([FR-366],
  [FR-368]).
- **The study:** the static study ([NFR-24]) runs on the Solomon and Gehring-Homberger instances,
  which carry no door orders. The plans of the re-optimization study have none unless its
  configuration sets a share before the measured runs ([NFR-195]). Pilot days ([FR-276], [FR-46])
  will contain door orders, so both solvers must model them before they run one.

## Consequences

- **Easy:** a door order can fill a gap in a route instead of being dropped on a day with tight
  windows. No change to the solution contract, because start times follow from the route.
- **Hard:** both solvers and the validator need a soft, two-sided window per order in a solve as
  well. How OR-Tools' routing model expresses "serve on arrival, penalise time outside" has not
  been checked.
- **Cost:** changes to both solvers, the validator, the baseline ([FR-342]), the order form and
  import, and the driver app. The optimization contract's problem and the client contract's orders
  each gain the mark ([ADR-006]).
- **Risk:** the penalty is the only brake. A package left at 07:00 for a customer who asked for
  17:00 waits outside all day.
- **Risk:** serving on arrival can cost more than a short wait would, when a van reaches a door stop
  a few minutes early; the solver can only reorder to avoid it.
- **Risk:** the on-time percentage falls when door orders are served outside their windows. That
  is intended, but it can look like a worse day.

## Alternatives considered

- **Ignore the window,** serving the order at any time in the shift. Not chosen: the owner wanted the
  customer's preferred time to still count.
- **Keep the window binding,** with leaving at the door changing only what happens when nobody is
  home. Not chosen: it gives up the freedom in routing that leaving at the door allows.
- **Penalise lateness only,** so an early van waits as at any stop. Not chosen: a van would wait
  hours outside a door order's window, which defeats leaving it at the door. It would reuse
  ADR-029's rule with no new mechanism, which makes it the fallback if two-sided windows prove hard.
- **Let the solver choose each door stop's start time.** Not chosen: the solution would carry start
  times, which changes the optimization contract and both solvers.

## Revisit when

- Door orders in the pilot often land more than an hour outside their windows, or customers complain.
- Packages left at the door go missing, which would argue for an earliest drop-off time that binds.
- Either solver cannot express the two-sided window within the study's time limits.

[ADR-006]: ADR-006-versioned-openapi-contracts.md
[ADR-029]: ADR-029-solver-objective-and-re-optimization-rules.md
[BR-09]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-42]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-107]: ../requirements/requirements.md#re-optimization
[BR-115]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-119]: ../requirements/requirements.md#routing-constraints
[BR-120]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-121]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-122]: ../requirements/requirements.md#routing-constraints
[FR-46]: ../requirements/requirements.md#research-and-evaluation
[FR-57]: ../requirements/requirements.md#customers-and-orders
[FR-276]: ../requirements/requirements.md#research-and-evaluation
[FR-322]: ../requirements/requirements.md#daily-planning
[FR-342]: ../requirements/requirements.md#reporting-and-baseline
[FR-364]: ../requirements/requirements.md#customers-and-orders
[FR-366]: ../requirements/requirements.md#publishing-and-the-driver-app
[FR-368]: ../requirements/requirements.md#publishing-and-the-driver-app
[FR-369]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-370]: ../requirements/requirements.md#reporting-and-baseline
[NFR-24]: ../requirements/requirements.md#nfr-24
[NFR-194]: ../requirements/requirements.md#nfr-194
[NFR-195]: ../requirements/requirements.md#nfr-195
[Q-20]: ../requirements/requirements.md#open-questions

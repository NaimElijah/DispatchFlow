# ADR-053: A plan follows one lifecycle that ends in completed, cancelled or abandoned, and every drop has a reason

- **Status:** Accepted; amended by ADR-100 and ADR-101
- **Date:** 2026-11-14

## Context

The requirements cite this record, as "ADR pending: plan lifecycle publishing stability and drop
reasons", for four connected questions. [Q-05], [Q-09] and [Q-19] were resolved on 2026-10-09:

- [Q-05]: the states a plan has and the transitions between them ([BR-12]);
- [Q-09]: whether a plan with flagged violations may be published. It may not ([BR-17]);
- [Q-19]: which reasons the system can give for a dropped order, and how it finds them.

The fourth is how route stability is measured, which [ADR-029] priced but did not define ([Q-06]).

[BR-12] gives a plan the states `draft`, `solving`, `ready`, `published` and `completed`, and
`cancelled` for a published plan that never runs ([BR-103]). A plan for a past date cannot be created
or published ([BR-118]), and the domain refuses to solve or change one (`DateInPast`, [DL §4.4]). So
a plan that nobody published by the end of its date has no transition left. It would stay `draft`,
`solving` or `ready` for good.

On 2026-11-13 and 2026-11-14 the owner, Naim Elijah, decided for the team. On 2026-11-14 he added the
end state for such a plan, which he named *abandoned*. He rejected leaving the plan in its state. The
case for leaving it overstated the cost of the new state: the midnight job that completes published
plans already exists. The cost is one state, its transitions and its tests.

This ADR settles the marker. It extends [ADR-029], which fixed the stability penalty and the
drop-penalty scale.

## Decision

### Lifecycle

- **A plan moves along these transitions only** ([BR-12], [DL §9.1]):

  | From | To | When |
  |---|---|---|
  | `draft` | `solving` | A solve is requested |
  | `solving` | `ready` | A validated solution arrives ([BR-15]) |
  | `solving` | `draft`, or `ready` if it was solved before | The solve fails ([BR-59]) |
  | `ready` | `solving` | The plan is solved again ([BR-60]) |
  | `ready` | `ready` | A manual edit ([BR-61]) |
  | `ready` | `published` | Every check passes ([BR-17], [BR-72]) |
  | `published` | `published` | A new revision, which records its cause ([BR-62], [BR-77]) |
  | `published` | `completed` | Midnight, Israel time, after its date ([BR-102]) |
  | `published` | `cancelled` | A dispatcher cancels it while no route has started ([BR-103]) |
  | `draft`, `solving`, `ready` | `abandoned` | Midnight, Israel time, after its date |

- **A plan completes at midnight, and late reports are still accepted** ([BR-102], [BR-27]). A stop
  that is unreported at midnight leaves its order *planned*. The summary shows "not reported", and a
  late report or a dispatcher's record settles it ([Q-94], [BR-33]).
- **`completed`, `cancelled` and `abandoned` are terminal.** The record stays ([DL §9.7]).

### Abandoned plans

- **At midnight after its date, an unpublished plan becomes *abandoned*.** The job that completes
  published plans does it. An abandoned plan is terminal and read-only.
- **Every queued or running solve job of that date fails with the reason "its date passed",**
  whether its plan is published or not (the owner, 2026-11-14). The midnight job then completes the
  plan if it was published, or abandons it if not, including a plan in `solving`. A `SolveFailed`
  that reaches an abandoned plan changes nothing ([DL §4.4]). The jobs fail because a
  re-optimization of a finished day could never be accepted.
- **A plan that was already published becomes *completed*,** as before ([BR-102]).
- **Orders on an abandoned plan show *unplanned*** ([BR-33]; the owner, 2026-11-14). The plan never
  reached a driver, so *planned* would claim something that did not happen.

### Publishing

- **A plan with any flagged violation cannot be published.** The dispatcher fixes it or solves again
  ([BR-17]). Edits are flagged, never silently fixed ([BR-16]).
- **Refusals fire in one order,** and the first step that refuses lists all its refusals ([BR-72],
  [DL §4.4]): changed vehicles, an order the plan does not account for, flagged violations, then the
  re-check ([ADR-054]).

### Stability

- **Route stability is the number of stops that leave their vehicle, dropped stops included**
  ([FR-256]). A re-optimization pays 5 minutes of travel time for each ([BR-115], [ADR-029]). Under
  [BR-106], only drops and the stops of a vehicle that is out can count.

### Drop reasons

- **A solve still returns a plan and lists every dropped order with its reason** ([FR-18]). The
  reasons are *capacity*, *time window*, *shift*, *displaced*, and *missing location*, plus the
  ones that [FR-66] (unreachable location), [FR-146] (a stop taken off by hand), [FR-195] (depot
  hours), [FR-201] (vehicle limit) and [FR-228] (an impossible pin) add ([FR-223]).
- **The optimizer finds the reason after the solve,** in `Problem.explain_drop`, which tests the
  order against these in sequence and stops at the first that fails ([PL §7.5], [ADR-044]):
  1. missing or unreachable location, decided before the search;
  2. a pin to a vehicle or driver that is out, or cannot serve the order even alone;
  3. capacity: heavier or larger than every allowed vehicle;
  4. time window: the earliest possible arrival is after the window ends;
  5. shift, depot hours or vehicle limit: depot to order to depot does not fit the latest return;
  6. displaced: it would fit alone, and other orders took its place.
- **Each reason comes with its fact,** in plain language ([FR-144]). For *displaced*, the fact says
  that other orders took the place, without claiming one cause ([Q-51]).
- **A leave-at-door order is never dropped for its window** ([BR-121], [ADR-049]).

## Consequences

- **Easy:** every plan ends in a named state, and nothing is deleted ([DL §9.7]). A plan for a past
  date cannot sit in `solving`.
- **Easy:** a dispatcher reads one reason and one fact for each drop, from one place.
- **Hard:** one more state. [BR-12], [BR-102], [DL §9.1], [DL §9.2] and [DL §9.7] change to match,
  with a Hebrew term for *abandoned* in the language of [DL §17]. Every screen that lists plans
  handles it.
- **Hard:** the midnight job now does three things: complete, fail jobs and abandon. [DL §6.1] lets a
  transaction change one existing aggregate, and `SolveJob` and `Plan` are two. So failing the jobs and
  completing or abandoning the plan are separate steps, as in a failed solve ([PL §13.1]). The
  version check refuses a late writer, and a `SolveFailed` that reaches an abandoned plan changes
  nothing.
- **Cost:** one state, its transitions and tests. The midnight job already exists.
- **Risk:** the drop reasons are tested in a fixed order, so an order that fails two tests shows the
  first. *Displaced* is the last test, so it names no single cause when several orders compete.

## Alternatives considered

- **Leave an unpublished plan in its state** after its date. Rejected by the owner, on 2026-11-14.
  The plan would stay `draft`, `solving` or `ready`, with no transition out and no way to be solved
  or published ([BR-118]).
- **Cancel it** with the published plans' state. Not chosen: `cancelled` is for a published plan that
  does not run ([BR-103]). An abandoned plan was never published.
- **Delete the plan.** Rejected: nothing the business keeps history about is deleted ([DL §9.7]).
- **Allow publishing with flagged violations.** Rejected on 2026-10-09 ([Q-09]).

## Revisit when

- Pilot dispatchers want to revive a plan whose date has passed, for example to publish it late.
  *Abandoned* would then need a way back.
- A drop reason shown to a dispatcher is wrong in the pilot, which would show that the order of the
  tests misleads.
- The midnight job fails in the pilot and leaves a plan neither completed nor abandoned.

[ADR-029]: ADR-029-solver-objective-and-re-optimization-rules.md
[ADR-044]: ADR-044-solver-port-takes-start-budget-and-seed-and-problem-judges.md
[ADR-049]: ADR-049-drop-penalty-unit-k-scaled-by-priority.md
[ADR-054]: ADR-054-publishing-re-checks-every-route-against-the-current-fleet.md
[BR-12]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-15]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-16]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-17]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-27]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-33]: ../requirements/requirements.md#orders-records-and-history
[BR-59]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-60]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-61]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-62]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-72]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-77]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-102]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-103]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-106]: ../requirements/requirements.md#re-optimization
[BR-115]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-118]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-121]: ../requirements/requirements.md#solving-dropping-and-validation
[DL §4.4]: ../architecture/domain_layer/domain_layer_design.md#44-planning
[DL §6.1]: ../architecture/domain_layer/domain_layer_design.md#61-the-rule
[DL §9.1]: ../architecture/domain_layer/domain_layer_design.md#91-plan-br-12
[DL §9.2]: ../architecture/domain_layer/domain_layer_design.md#92-solvejob
[DL §9.7]: ../architecture/domain_layer/domain_layer_design.md#97-how-things-end
[DL §17]: ../architecture/domain_layer/domain_layer_design.md#17-ubiquitous-language
[FR-18]: ../requirements/requirements.md#daily-planning
[FR-66]: ../requirements/requirements.md#daily-planning
[FR-144]: ../requirements/requirements.md#daily-planning
[FR-146]: ../requirements/requirements.md#manual-editing
[FR-195]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-201]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-223]: ../requirements/requirements.md#daily-planning
[FR-228]: ../requirements/requirements.md#daily-planning
[FR-256]: ../requirements/requirements.md#live-operations-and-re-optimization
[PL §7.5]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#75-drop-reasons
[PL §13.1]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#131-solve
[Q-05]: ../requirements/requirements.md#open-questions
[Q-06]: ../requirements/requirements.md#open-questions
[Q-09]: ../requirements/requirements.md#open-questions
[Q-19]: ../requirements/requirements.md#open-questions
[Q-51]: ../requirements/requirements.md#open-questions
[Q-94]: ../requirements/requirements.md#open-questions

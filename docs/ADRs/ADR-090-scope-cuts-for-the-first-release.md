# ADR-090: The first release cuts ten capabilities, and each has its own trigger to reopen

- **Status:** Accepted
- **Date:** 2026-11-13

## Context

The Non-goals section of the requirements lists what DispatchFlow deliberately does not do, so
nobody re-proposes it ([Non-goals]). Ten of its entries carried the marker "ADR pending: scope cuts
for the first release". Four are older cuts: payroll and attendance, inventory, fleet maintenance
and demand forecasting. Six were added on 2026-11-03 and tagged "Proposed": telematics, offline
dispatching, orders from end customers, electric-vehicle range, cargo temperature, and
re-optimization without a dispatcher.

The product serves one delivery company ([ADR-024]). It plans each day's routes, re-plans them when
the day goes wrong and measures the result ([Purpose and scope][PS]). No company has been
identified yet, and the synthetic-data fallback stands until one is ([Q-01]). Every cut below is
therefore a judgement made before a real company could be asked.

On 2026-11-13 the owner, Naim Elijah, confirmed all eight proposed non-goals for the team. The same
decision records the four older cuts, so that one ADR holds every cut and the trigger each
non-goal already states.

## Decision

The first release does not build the capabilities below. A proposal to build one is a reopening, and
it is judged against that capability's trigger. The triggers are the ones the requirements state.

| Cut | What the first release does instead | Reopen when |
|---|---|---|
| Payroll, attendance or statutory driver-hours records | Time is reserved for breaks by a default ([BR-105]) until the law is verified ([Q-39]) | The company requires it, or a legal duty is confirmed |
| Inventory or warehouses | Nothing | The company cannot load vehicles without it |
| Fleet maintenance | Nothing | The company asks to plan around scheduled servicing |
| Demand forecasting | Nothing | The company needs a plan prepared before its orders exist |
| Vehicle telematics or trackers | Positions come only from the apps ([FR-31], [FR-243]) | The company's vehicles already carry trackers |
| Offline dispatching | Only a driver's route work runs offline, in the phone app ([FR-69], [FR-70]); printed routes are the dispatcher's fallback ([FR-72]) | The company's dispatcher cannot get through an outage on printed routes |
| Orders from end customers | Orders come from a dispatcher, an import or, later, an API ([FR-116]) | The company asks to take orders directly from its buyers |
| Electric-vehicle range and charging | Nothing | The company's fleet includes electric vehicles whose range limits a day |
| Cargo temperature monitoring | Refrigeration is at most a vehicle attribute ([FR-112]) | The company must keep temperature records for its goods |
| Starting or applying a re-optimization without a dispatcher | Only a person starts one ([FR-332]) and applies it ([FR-255], [BR-47]) | The company runs days with no dispatcher on duty |

Two further non-goals were confirmed in the same answer. They carry no "scope cuts" marker and stay
governed where they are written:

- **Conversations with recipients.** A reply is read only for a rating, a comment or an opt-out
  ([FR-357], [FR-360], [FR-363]). Reopen when the company wants to answer recipients by message.
- **WhatsApp or email to recipients.** Messages go by SMS only ([ADR-031]). Reopen when
  [ADR-031]'s revisit trigger fires.

The other non-goals are recorded elsewhere and are not part of this decision: one company only
([ADR-024]), no live traffic ([ADR-048]), payments ([Q-57]), a marketplace of drivers, enterprise
scale ([Q-90]) and the solver comparison ([ADR-005]).

## Consequences

- **Easy:** the first release stays one company's route planning. Nothing on this list needs a
  schema, a screen or a test.
- **Easy:** a request for one of these capabilities has a written answer and a written trigger, so
  the team does not decide it again from scratch.
- **Hard:** a company that already needs one of them gets nothing for it.
- **Hard:** the triggers all depend on facts about a company that is not yet identified ([Q-01]).
  None has been tested against a real one.
- **Cost:** none in code. The cost is the work of reopening a cut, which begins with a new ADR that
  supersedes or amends this one.
- **Risk:** once a company is identified, its answers may fire several triggers at once, and the
  first release then lacks several capabilities together.

## Alternatives considered

- **Build any of them in the first release.** Rejected: nothing in the requirements needs one, and
  the project rule is that complexity is pulled by a real constraint, never added in anticipation
  of one.
- **Leave them as non-goals with an "ADR pending" marker.** Rejected: the triggers lived only in the
  requirements, and the project records a decision not to adopt something as an ADR, with the
  trigger that would reopen it.
- **Keep them as proposed non-goals, or decide later.** Rejected: the owner confirmed the eight
  proposed non-goals on 2026-11-13.

## Revisit when

- Each trigger in the table fires. Write a new ADR that amends this one for that cut and leaves the
  others standing.
- A company is identified ([Q-01]). Check all ten triggers against what it says, before the pilot
  protocol is fixed ([Q-40]).

[ADR-005]: ADR-005-or-tools-first-research-solver-for-re-optimization.md
[ADR-024]: ADR-024-one-delivery-company-no-multi-tenancy.md
[ADR-031]: ADR-031-sms-through-an-israeli-provider-for-recipient-messages.md
[ADR-048]: ADR-048-travel-times-from-buckets-and-a-buffer-with-no-live-traffic.md
[Non-goals]: ../requirements/requirements.md#non-goals
[PS]: ../requirements/requirements.md#purpose-and-scope
[BR-47]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-105]: ../requirements/requirements.md#routing-constraints
[FR-31]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-69]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-70]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-72]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-112]: ../requirements/requirements.md#daily-planning
[FR-116]: ../requirements/requirements.md#customers-and-orders
[FR-243]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-255]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-332]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-357]: ../requirements/requirements.md#messages-to-recipients
[FR-360]: ../requirements/requirements.md#messages-to-recipients
[FR-363]: ../requirements/requirements.md#messages-to-recipients
[Q-01]: ../requirements/requirements.md#open-questions
[Q-39]: ../requirements/requirements.md#open-questions
[Q-40]: ../requirements/requirements.md#open-questions
[Q-57]: ../requirements/requirements.md#open-questions
[Q-90]: ../requirements/requirements.md#open-questions

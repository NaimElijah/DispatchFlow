# ADR-054: Publishing re-checks every route against the current fleet and orders

- **Status:** Accepted
- **Date:** 2026-11-13

## Context

A plan is solved against the fleet and orders as they were at the solve. It may be published later,
after a shift, a capacity or a depot has changed. A changed record applies to later plans. On an open
plan, the re-check at publishing ([BR-21]) or a flag catches any conflict ([FR-306]).

[BR-20] already refuses to publish a plan whose vehicle is unavailable or whose driver no longer holds
the Driver role. It does not look at shifts, capacities, depots or windows. [Q-28] asked whether
publishing should catch those too. The requirements answered it on 2026-10-10 under the marker "ADR
pending: recheck every route when a plan is published", and the rows [BR-21] and [BR-66] to [BR-70]
state the rule.

On 2026-11-13 the owner, Naim Elijah, decided for the team to record it. This ADR settles the marker.

## Decision

- **Before a `ready` plan is published, the shared validator re-checks every route once** against the
  current fleet and the orders of its date: shifts, capacities, depots and time windows ([BR-21]).
  Where [FR-133] is built, it also checks vehicle limits ([BR-66]).
- **The re-check is an `evaluate` call** to the optimizer, the same validator that judges solves and
  manual edits ([BR-42], [PL §6.1]). The use case gathers its inputs before any transaction: each
  route's answer from the fleet roster, the date's uncancelled orders with their last change time,
  and the optimizer's `Evaluation` of the plan's current version ([PL §13.2], [DL §6.3]).
- **`Plan.publish` walks four checks in order** and stops at the first step that refuses, listing
  every refusal at that step ([BR-72], [DL §4.4]):
  1. vehicles and drivers ([BR-20], [BR-63]);
  2. every order accounted for ([BR-22], [BR-71]);
  3. no flagged violation ([BR-17]);
  4. the re-check ([BR-21]).
- **A route that breaks a rule refuses publishing.** Nothing is published, every broken rule is
  listed, and the dispatcher solves again or edits the route ([BR-67]).
- **When nothing is broken, the re-checked times are published.** They equal the solved times unless
  the fleet changed since the solve, and then they follow it. The plan becomes revision 1 with them
  ([BR-68], [PL §13.2]).
- **A plan without routes needs no re-check** ([BR-69]).
- **If the re-check cannot run, nothing is published** ([BR-70]). An optimizer that cannot be
  reached becomes an `Evaluation` that says so (`RecheckUnavailable`). `Plan` refuses an `Evaluation`
  that was read for another version of the plan (`StaleEvaluation`) ([DL §4.4]).

## Consequences

- **Easy:** a published plan has been checked against the fleet it will run with, not the fleet it
  was solved with. A route that breaks a shift, a capacity or a depot's hours changed after the solve is
  refused before any driver sees it.
- **Easy:** one validator, one call, for edits, publishing and acceptance ([BR-42]).
- **Hard:** publishing needs the optimizer. While it is down, nothing is published ([BR-70]), though
  published routes, driver reports, hand-overs and cancellations keep working ([NFR-42]).
- **Hard:** the times a dispatcher saw on the solved plan can differ from the published times when the
  fleet changed in between ([BR-68]).
- **Hard:** a refusal shows only the first failing step's refusals, so a dispatcher may fix one step
  and meet the next.
- **Cost:** one `evaluate` call for up to 15 routes and 200 stops on every publish. The sources set
  no budget for it. The pilot's goal of 15 minutes from orders to a published plan is [NFR-34].
- **Risk:** a fleet change made after the solve now costs the dispatcher an edit or a new solve.

## Alternatives considered

- **Publish as solved,** with only the vehicle and driver check of [BR-20]. Not chosen: a shift,
  capacity or depot change after the solve would reach drivers unnoticed ([Q-28]).
- **Re-check only the routes a change touched.** Not chosen: it needs a map from each fleet or order
  change to its routes. One `evaluate` call of the plan's current version covers all of them.
- **Publish with the last evaluation when the optimizer is down.** Rejected by [BR-70]. The decision
  for routes changed while the optimizer is down is in [ADR-058].
- **Re-check in the Java core.** Rejected: the validator lives only in Python ([ADR-044]).

## Revisit when

- Dispatchers in the pilot are refused repeatedly for fleet changes that an edit would have fixed
  without a new solve. Offer the fix in the refusal.
- The re-check slows publishing past [NFR-34]'s 15 minutes, or the optimizer is down when a pilot
  plan must go out ([NFR-29]).
- Per-vehicle route limits ([FR-133]) are built, so [BR-66] takes effect.

[ADR-044]: ADR-044-solver-port-takes-start-budget-and-seed-and-problem-judges.md
[ADR-058]: ADR-058-routes-changed-while-the-optimizer-is-down-are-checked-when-it-returns.md
[BR-17]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-20]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-21]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-22]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-42]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-63]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-66]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-67]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-68]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-69]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-70]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-71]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-72]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[DL §4.4]: ../architecture/domain_layer/domain_layer_design.md#44-planning
[DL §6.3]: ../architecture/domain_layer/domain_layer_design.md#63-outside-calls-happen-before-the-transaction
[FR-133]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-306]: ../requirements/requirements.md#fleet-depots-and-shifts
[NFR-29]: ../requirements/requirements.md#nfr-29
[NFR-34]: ../requirements/requirements.md#nfr-34
[NFR-42]: ../requirements/requirements.md#nfr-42
[PL §6.1]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#61-optimizer-api-v1
[PL §13.2]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#132-publish
[Q-28]: ../requirements/requirements.md#open-questions

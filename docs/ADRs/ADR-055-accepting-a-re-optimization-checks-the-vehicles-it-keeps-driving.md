# ADR-055: Accepting a re-optimization checks the vehicles it keeps driving

- **Status:** Accepted
- **Date:** 2026-11-13

## Context

A published plan is valid only while, for every route, the vehicle is not marked unavailable and its
driver holds the Driver role and is the route's driver ([BR-20]). Publishing refuses a plan that
fails this, names every changed vehicle and sends the dispatcher back to solve again ([BR-63]).

A re-optimization preview can sit open while the day changes. Accepting it changes the published
plan in place ([BR-62]). The proposal moves, drops or reorders stops. Most of its routes are left
alone, and their vehicles and drivers keep driving. Between the preview and the acceptance, one of
those vehicles may have been marked unavailable, or its driver may have lost the Driver role.

Accepting a re-optimization re-checks what changed since the preview ([BR-25], [BR-82], [ADR-056]).
That rule names the stops the proposal would move or drop, and the orders, vehicles and drivers it
relies on. The rows [BR-64] and [BR-65] add a separate check of every vehicle the proposal keeps
driving, and carry the marker "ADR pending: check the vehicles when a re-optimization is
accepted".

On 2026-11-13 the owner, Naim Elijah, decided for the team. This ADR settles the marker.

## Decision

- **Accepting a re-optimization applies [BR-20] to every vehicle the proposal keeps driving**
  ([BR-64]). A route that stays in the plan after acceptance has an available vehicle, and a driver
  who holds the Driver role and is the route's driver.
- **A vehicle withdrawn mid-route is skipped.** A re-optimization withdraws a vehicle when it leaves
  it out while it still has stops it made, because the vehicle was marked unavailable, was left
  without a driver or ran out of shift ([FR-267]). Such a route counts as finished ([BR-108]), so it
  could never pass [BR-20].
- **A vehicle that no longer meets [BR-20] refuses the acceptance.** The refusal names every changed
  vehicle, and the dispatcher re-optimizes again ([BR-65]). The plan stays `published` and every
  route stays as it was ([BR-26]).
- **The check is in `Plan.accept`,** which takes `ChangesSinceBasis`, including the roster changes
  since the proposal's basis, and refuses with `VehicleUnavailable` ([DL §4.4], [PL §13.4]).
  `AcceptProposal` reads those changes before the call, so `Plan` changes only itself.
- **The driver condition of [BR-20] is the Driver role.** Fleet stops counting a driver record while
  its user lacks the role ([ADR-062]).

## Consequences

- **Easy:** a published plan never holds a route that fails [BR-20], after publishing or after any
  acceptance. The rule holds for the plan's whole life, not only at the first publish.
- **Easy:** the dispatcher learns which vehicle changed, and re-optimizes from the live state.
- **Hard:** acceptance can now be refused for a route the proposal did not touch. The dispatcher
  re-runs the search for a change that sits outside the preview.
- **Cost:** `ChangesSinceBasis` carries the roster changes, and `Plan.accept` evaluates [BR-20] for
  each kept vehicle.
- **Risk:** a refusal at acceptance costs a new re-optimization, up to 20 seconds of search
  ([BR-84]), while the day runs.
- **Risk:** the check for a driver who lost the role depends on [ADR-062]. The sources do not say
  how acceptance reads it.

## Alternatives considered

- **No separate check,** relying on [BR-82] alone. Not chosen: it names what the proposal moves,
  drops or relies on, and the owner chose a check of every kept vehicle ([BR-64]), so that no kept
  route can break [BR-20] after acceptance.
- **Drop or reassign the changed vehicle's route at acceptance.** Not chosen: acceptance would apply
  something other than the preview the dispatcher reviewed, and nothing changes until it is
  accepted ([FR-255]). [BR-65] has the dispatcher re-optimize.
- **Check the withdrawn vehicles too.** Rejected: they fail [BR-20] by definition ([FR-267]), so every
  proposal that withdrew one would be refused.

## Revisit when

- Acceptance is refused under [BR-65] on several pilot days. Count them in the audit records, and
  consider offering the dispatcher a re-optimization that starts from the new roster.
- Several trips a day per vehicle ([FR-111]) are built, which changes what "keeps driving" means.

[ADR-056]: ADR-056-accepting-a-re-optimization-checks-changes-recorded-since-its-basis.md
[ADR-062]: ADR-062-fleet-checks-the-driver-role-when-it-reads.md
[BR-20]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-25]: ../requirements/requirements.md#re-optimization
[BR-26]: ../requirements/requirements.md#re-optimization
[BR-62]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-63]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-64]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-65]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-82]: ../requirements/requirements.md#re-optimization
[BR-84]: ../requirements/requirements.md#re-optimization
[BR-108]: ../requirements/requirements.md#re-optimization
[DL §4.4]: ../architecture/domain_layer/domain_layer_design.md#44-planning
[FR-111]: ../requirements/requirements.md#daily-planning
[FR-255]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-267]: ../requirements/requirements.md#reporting-and-baseline
[PL §13.4]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#134-re-optimize-and-accept

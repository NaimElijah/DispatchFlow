# ADR-061: A vehicle's capacity is a weight in kilograms and a volume in litres, both required and positive

- **Status:** Accepted
- **Date:** 2026-11-13

## Context

Decided on 2026-11-13: the rule that the requirements mark
"ADR pending: capacity weight and volume" ([Q-11], [NFR-68]).

A dispatcher registers each vehicle with a weight capacity and a volume capacity ([FR-05]) and
enters each order with a weight and a volume demand ([FR-09]). On every route the total weight never
exceeds the vehicle's weight capacity, and the total volume never exceeds its volume capacity
([BR-05]).

Q-11 asked in what units capacity is counted, and what happens to an order with no volume. It was
resolved twice:

- **2026-11-04:** kilograms and litres, and a blank volume counts as zero.
- **2026-11-11:** weight and volume must be positive, and a blank one is refused ([BR-50]). The
  pre-flight check no longer lists orders with no volume ([FR-230]).

Q-88 asked whether a package count or a size class could stand in for an unknown volume. It was
resolved on 2026-11-04: nothing stands in, and a package count ([FR-136]) is only shown to the driver.

Both solvers and the validator must read capacity the same way, because the validator is the one
judge of feasibility ([BR-42]).

## Decision

- **Capacity has two dimensions, weight and volume,** each a hard limit on a route ([BR-05]). A
  route that breaks either one is infeasible.
- **Units are kilograms and litres.** Screens show them as such, in both interface languages
  ([NFR-68]). The shared kernel has a `Weight` and a `Volume`, each present and positive; a
  non-positive value fails with `NonPositiveQuantity` ([DL §2.3][DL-23], [DL §3.3][DL-33]).
- **A blank weight or volume is refused,** for a vehicle and for an order ([BR-50]). The database
  backs it: the vehicle and order tables hold `weight_kg numeric(8,2)` and `volume_l numeric(9,2)`,
  not null, each with a check that it is greater than zero ([PL §12][PL-12]).
- **The optimizer contract carries them as `weightKg` and `volumeL`** ([PL §6.1][PL-61]).
- **OR-Tools gets two capacity dimensions,** one for weight and one for volume. The values are scaled
  to grams and millilitres, because OR-Tools takes integers ([PL §8][PL-8]).
- **The DispatchFlow solver checks both loads** at each insertion in constant time
  ([PL §10.1][PL-101]).
- **The pre-flight check lists** orders heavier or larger than every vehicle ([FR-230]), and a solve
  that cannot serve such an order drops it with a capacity reason that gives the order's weight and
  the largest capacity ([PL §7.5][PL-75]).
- **A package count is not capacity.** It is information for the driver ([FR-136]).

## Consequences

- **Easy:** a route's load is two numbers a dispatcher can check by eye, in units they already use.
- **Easy:** every order has both numbers, so no solver or validator rule needs a case for a missing
  volume.
- **Hard:** a small business may not know an order's volume in litres ([Q-88] raised it). It must
  enter an estimate, because nothing stands in.
- **Hard:** a CSV import row with a blank weight or volume is refused, with the rest of that row's
  broken rules listed beside it ([PL §15.2][PL-152]).
- **Cost:** two dimensions in OR-Tools and in the DispatchFlow solver's insertion check, and the
  scaling to integers.
- **Risk:** the scaling to grams and millilitres is a conversion in the model. A test must compare
  the solver's capacity decisions with the validator's at the boundary.
- **Risk:** an estimated volume that is wrong makes a route infeasible on the road. The system cannot
  detect it.

## Alternatives considered

- **Weight only.** Rejected: [FR-05] and [BR-05] give a vehicle a volume capacity as well, and a
  light bulky order would fit by weight alone.
- **A package count or size class as the unit.** Rejected by [Q-88]: nothing stands in for volume.
  The count stays on the order for the driver ([FR-136]).
- **A blank volume counts as zero.** Decided on 2026-11-04 and changed on 2026-11-11. The sources do
  not record the reason. Such an order would never count against a vehicle's volume capacity.
- **Cubic metres instead of litres.** Q-11 asked the question, and the answer was litres. The sources
  do not record why.
- **One combined load unit.** Not proposed in any source; it would hide which limit a route breaks,
  and [BR-05] names two.

## Revisit when

- The pilot company's orders have no usable volume, so dispatchers enter placeholder volumes on
  most orders. Then reopen a stand-in or an optional volume, with the pre-flight check listing the
  orders that have none.
- A vehicle's capacity is a count of items or pallets rather than kilograms and litres, for a
  company that DispatchFlow serves later ([Q-90]).

[BR-05]: ../requirements/requirements.md#routing-constraints
[BR-42]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-50]: ../requirements/requirements.md#routing-constraints
[FR-05]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-09]: ../requirements/requirements.md#customers-and-orders
[FR-136]: ../requirements/requirements.md#customers-and-orders
[FR-230]: ../requirements/requirements.md#daily-planning
[NFR-68]: ../requirements/requirements.md#nfr-68
[Q-11]: ../requirements/requirements.md#open-questions
[Q-88]: ../requirements/requirements.md#open-questions
[Q-90]: ../requirements/requirements.md#open-questions
[DL-23]: ../architecture/domain_layer/domain_layer_design.md#23-rules-between-contexts
[DL-33]: ../architecture/domain_layer/domain_layer_design.md#33-value-objects
[PL-152]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#152-validating-rows
[PL-12]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#12-database-schema
[PL-61]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#61-optimizer-api-v1
[PL-75]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#75-drop-reasons
[PL-8]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#8-or-tools-model
[PL-101]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#101-route-state-and-the-constant-time-insertion-check

# ADR-099: A route may carry the service starts its solver claims, and the validator checks them

- **Status:** Accepted
- **Date:** 2026-11-14

## Context

[NFR-169] asks the validator's test suite to reject a crafted violation of [BR-04], service time at
each stop, and to name the rule. [ADR-044] makes a `Solution` the routes and the set of dropped
orders. [ADR-048] has the validator re-time every route itself. Under those two, a solution can't
break BR-04: the validator adds each stop's service time when it times the route, so the rule holds
by construction, and no catalogue case for it can exist.

[ADR-032] rejected "let the solver choose each door stop's start time", because the solution would
carry start times. Its concern was the solver deciding the times, not the solver reporting them.

## Decision

On 2026-11-14 the owner, Naim Elijah, decided that a route in a `Solution` may carry the service
starts its solver claims, one per stop (`planned_starts`). The validator still times every route
itself, and its timings are the ones used, stored and shown ([ADR-048]). It compares each claim
with its own timing:

- a claim earlier than the stop's window opens breaks [BR-03];
- any other claim earlier than the validator's own start breaks [BR-04]. That covers a solver that
  left out service time, or timed travel with another factor or no buffer;
- a later claim is a planned wait, which is accepted, and the validator's timing stands (FR-403).

A route with no claims is judged as before.

This extends [ADR-044]: a route may hold claims as well as its vehicle, driver, ends and stops. It
leaves [ADR-032] as it stands. A door stop is still served on arrival, and a claim never moves it.

## Consequences

- **Easy:** the catalogue can break BR-04 and name it. A solver whose time model disagrees with the
  validator's is caught when it reports its times.
- **Hard:** the optimizer contract gains an optional field per route when the solve operations are
  added.
- **Cost:** none.
- **Risk:** a solver that sends no claims is never checked this way. Claims are optional, so the
  check catches only solvers that report their times.

## Alternatives considered

- **Attribute BR-04 by deduction:** report it when a route is feasible without service time and
  infeasible with it. Not chosen: it names a rule from a guess about the cause.
- **Accept the case as BR-03 or BR-06,** the rules a late service start breaks, and note it against
  NFR-169. Not chosen: NFR-169 asks for BR-04 by name.

## Revisit when

- No solver ever sends claims, so the check never runs outside the catalogue.
- The optimizer contract is designed and the field costs more than it gives.

[ADR-032]: ADR-032-leave-at-door-windows-are-preferences.md
[ADR-044]: ADR-044-solver-port-takes-start-budget-and-seed-and-problem-judges.md
[ADR-048]: ADR-048-travel-times-from-buckets-and-a-buffer-with-no-live-traffic.md
[BR-03]: ../requirements/requirements.md#routing-constraints
[BR-04]: ../requirements/requirements.md#routing-constraints
[NFR-169]: ../requirements/requirements.md#nfr-169

# ADR-058: A route changed while the optimizer is down is marked not yet checked, and checked when it returns

- **Status:** Accepted
- **Date:** 2026-11-13

## Context

On 2026-11-13 the owner, Naim Elijah, decided for the team what happens to a published or ready
route that changes while the optimizer is down. He adopted the proposal that the tech stack's open
decisions listed under "Validator while the optimizer is down". It completes what [NFR-42], [FR-304]
and [FR-315] leave open: what a route shows until the optimizer answers.

[NFR-42] says that if the optimization service, the road-routing service or the geocoder is down,
published routes, driver reports, hand-over and cancellation keep working. But the validator lives
only in the optimizer, in Python, so that the product and the research score routes identically
([BR-42], [DL §16][DL-16]). The core cannot judge a route by itself.

Three actions change a route without a solve:

- a hand-over to another driver, which is flagged when it breaks the new driver's shift and needs
  the dispatcher's confirmation ([FR-304]);
- a cancellation, which takes the stop off the driver's route at once ([FR-58], [FR-207]);
- a date change, which does the same and flags a route the change breaks until it is
  re-optimized ([FR-315]).

Publishing re-checks every route through the optimizer's `/v1/evaluate` ([BR-21]; [PL §13.2][PL-132]).
If that re-check cannot run, nothing is published ([BR-70]).

## Decision

- **Hand-overs, cancellations and date changes complete** while the optimizer is down. The dispatcher
  is not blocked, and the driver's route changes at once ([NFR-42], [FR-207]).
- **Each changed route is marked *not yet checked*.** The state is the `check_state` of the route
  (`not_yet_checked`, otherwise `checked`) and the screens show it ([PL §12][PL-12]).
- **A job runs every minute while any route is *not yet checked*.** Once the optimizer answers, it
  evaluates those routes and calls `Plan.recordRouteCheck`. A route the change breaks is then
  flagged as [FR-304] and [FR-315] say ([DL §12][DL-12], [PL §11.4][PL-114]).
- **Publishing refuses while the optimizer is down** ([BR-70]). The use case turns an unreachable
  optimizer into an `Evaluation` that says so, and `Plan.publish` refuses with `RecheckUnavailable`
  ([DL §4.4][DL-44], [PL §13.2][PL-132]).
- **The core has no validator of its own.** A route stays *not yet checked* until the optimizer
  judges it.
- **Fault injection tests it:** a Toxiproxy between the core and the optimizer takes the optimizer
  down in a test of [NFR-42] ([TS §17.2][TS-172]).

## Consequences

- **Easy:** a dispatcher can hand a route over or cancel an order in an outage, as [NFR-42]
  requires.
- **Easy:** one definition of feasibility stays in one place ([BR-42]).
- **Hard:** every screen and report that shows a route needs a third answer beside *valid* and
  *violating*.
- **Hard:** the job is one more watcher to keep alive ([DL §12][DL-12]).
- **Cost:** a column on the route, a job, and the `RecheckUnavailable` path in `Plan.publish`.
- **Risk:** during an outage a route can be driven in a state no validator has judged since the
  change. A hand-over that breaks a shift is caught only when the optimizer returns.
- **Risk:** a long outage on a publishing day stops publishing, because [BR-70] allows no
  publishing without the re-check.

## Alternatives considered

- **Refuse hand-overs and cancellations while the optimizer is down.** Rejected: [NFR-42] says
  they keep working, and a cancelled stop must leave the route at once ([FR-207], [FR-316]).
- **A second validator in the core,** to judge routes in an outage. Rejected: it creates a second
  definition of feasibility that must agree with the first ([BR-42]). The domain document keeps the
  validator, the objective and the conventions only in Python ([DL §16][DL-16]).
- **Treat a changed route as checked.** Rejected: it would hide an unjudged change, and a published
  route changes only through named, audited actions ([BR-47], [BR-77], [BR-78]).
- **Queue the change until the optimizer returns.** Rejected: the driver would keep a stop that was
  cancelled, or the old driver would keep a route that was handed over.

## Revisit when

- A pilot day has an outage that keeps a route *not yet checked* past the start of its driver's
  shift, or one where a dispatcher cannot publish. Then consider a reduced check in the core for the
  cases that need no road times, such as a shift and break check.
- The optimizer is down on more than one pilot day. [NFR-14] runs the system on a single VM with
  no redundancy, so revisit this rule, and its cost to the dispatcher, if outages recur.

[BR-21]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-42]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-47]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-70]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-77]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-78]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[FR-58]: ../requirements/requirements.md#customers-and-orders
[FR-207]: ../requirements/requirements.md#customers-and-orders
[FR-304]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-315]: ../requirements/requirements.md#customers-and-orders
[FR-316]: ../requirements/requirements.md#customers-and-orders
[NFR-14]: ../requirements/requirements.md#nfr-14
[NFR-42]: ../requirements/requirements.md#nfr-42
[DL-12]: ../architecture/domain_layer/domain_layer_design.md#12-who-notices-when-an-asynchronous-step-doesnt-happen
[DL-16]: ../architecture/domain_layer/domain_layer_design.md#16-why-it-looks-like-this
[DL-44]: ../architecture/domain_layer/domain_layer_design.md#44-planning
[TS-172]: ../architecture/tech_stack.md#172-fault-injection
[PL-114]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#114-scheduled-jobs-and-watchers
[PL-12]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#12-database-schema
[PL-132]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#132-publish

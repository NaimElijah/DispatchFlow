# ADR-056: Accepting a re-optimization re-checks every change recorded since its preview

- **Status:** Accepted
- **Date:** 2026-11-13

## Context

On 2026-11-13 the owner, Naim Elijah, decided for the team the rule that the requirements mark "ADR
pending: check recorded changes when a re-optimization is accepted" ([BR-25]).

A re-optimization has a limit of 20 seconds from the start of its search, and has failed if it has
not returned 30 seconds past it ([BR-84]). The dispatcher then reads the preview and accepts or
declines it. While that happens the day goes on: drivers report stops, orders are cancelled or
changed, a route is handed over, a vehicle is marked unavailable. Nothing changes for drivers until
the dispatcher accepts ([FR-255]), and an open preview does not stop any of this, because it does
not count as a running re-optimization ([BR-38]).

A proposal never changes once written. It records its basis: the plan's revision and the time of the
facts it started from ([DL §4.4][DL-44]). Accepting it is one `Plan` method that creates a revision
([DL §6.2][DL-62]). The same step also checks the vehicles the proposal keeps driving, which
[ADR-055] decides.

## Decision

- **Accepting reads what was recorded since the proposal's basis,** before the transaction. The use
  case gathers it into one value, `ChangesSinceBasis`: deliveries, cancellations, order changes,
  roster (fleet) changes and plan revisions ([DL §4.4][DL-44], [PL §13.4][PL-134]). [BR-25] names
  the kinds: every stop status, cancellation, order change, hand-over and fleet change.
- **`Plan.accept` refuses** if the proposal would move or drop a stop that changed, or relies on an
  order, vehicle or driver that changed ([BR-82]). Nothing is applied. The refusal is
  `ProposalOutdated(changes)` and names each change. The dispatcher then re-optimizes.
- **A report that does not touch the proposal does not refuse it.** A report on a stop the proposal
  leaves on the same vehicle and in the same order stands, and the proposal is still accepted
  ([BR-83]).
- **The check reads recorded times when it runs.** No event invalidates a proposal when something
  changes: an order's details change raises no event, and publishing, accepting and re-optimizing
  all read the order's last change time when they need it ([DL §7.4][DL-74], [DL §10.3][DL-103]).
- **Success writes `Plan` only:** a new revision that cites the proposal ([ADR-059] stores it). A
  proposal that is declined, refused or fails leaves the plan `published` with every route as it was
  ([BR-26]).

## Consequences

- **Easy:** a dispatcher cannot apply a preview that a driver's report or a cancellation has made
  false, and is told what changed. Reports on untouched stops do not waste a preview.
- **Easy:** no listener has to keep proposals up to date, so the event catalogue stays smaller
  ([DL §7.4][DL-74]).
- **Hard:** `AcceptProposal` reads five kinds of change from several modules (Live Operations,
  Customers & Orders, Fleet and Planning, per [DL §2.2][DL-22]) and passes them in as values.
  Deliveries reach Planning through the `ExecutionFacts` port, which Planning owns and Live
  Operations implements ([DL §2.1][DL-21]). Each kind needs a read by time, so each module must
  record when something changed.
- **Hard:** deciding which changes touch the proposal (a moved or dropped stop, a relied-on order,
  vehicle or driver) is the rule's logic. It needs its own tests for each kind of change.
- **Cost:** one more read across modules on every acceptance, before the transaction.
- **Risk:** on a busy day, a preview can be refused as outdated before the dispatcher accepts it,
  and the dispatcher waits for another re-optimization (up to 30 seconds at the 95th percentile,
  [NFR-07]).
- **Risk:** a change that no module records with a time cannot be seen by this check. The list in
  [BR-25] is the contract; a new kind of change needs adding to it.

## Alternatives considered

- **Refuse the preview on any change since its basis.** Simple, but a preview would go stale as soon
  as any of 15 drivers reports a stop ([NFR-10]). It also contradicts [BR-83], where a report on an
  untouched stop does not refuse.
- **Invalidate proposals with events as changes happen.** Each event is a contract another module
  depends on, and the design found no change that does better with an event than with a read at the
  moment it matters ([DL §7.4][DL-74], [DL §16][DL-16]).
- **Lock the plan while a preview is open.** It would stop drivers' reports and cancellations from
  reaching the plan, which [BR-27] (late outcomes are still accepted) and [FR-207] (a cancelled
  stop leaves the route at once) do not allow. [BR-38] also says an open preview does not count as
  running.
- **Apply the proposal and let the validator flag what broke.** Rejected: a re-optimization that
  moves a stop already delivered or cancelled would reach drivers' phones before anyone sees the
  conflict, against [FR-255].

## Revisit when

- Pilot audit records show the same dispatcher re-optimizing twice for one disruption on two or more
  pilot days, because the first preview was refused as outdated. The figure is a proposal, not a
  measurement; the first pilot week may set a better one. Then consider recomputing the unaffected
  part of the proposal at acceptance instead of refusing it.
- A change that this ADR does not read (a fact recorded outside the five kinds) causes a wrong
  acceptance. Add its kind to [BR-25] and `ChangesSinceBasis`.

[ADR-055]: ADR-055-accepting-a-re-optimization-checks-the-vehicles-it-keeps-driving.md
[ADR-059]: ADR-059-plan-revisions-are-stored-as-rows.md
[BR-25]: ../requirements/requirements.md#re-optimization
[BR-26]: ../requirements/requirements.md#re-optimization
[BR-27]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-38]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-82]: ../requirements/requirements.md#re-optimization
[BR-83]: ../requirements/requirements.md#re-optimization
[BR-84]: ../requirements/requirements.md#re-optimization
[FR-207]: ../requirements/requirements.md#customers-and-orders
[FR-255]: ../requirements/requirements.md#live-operations-and-re-optimization
[NFR-07]: ../requirements/requirements.md#nfr-07
[NFR-10]: ../requirements/requirements.md#nfr-10
[DL-103]: ../architecture/domain_layer/domain_layer_design.md#103-every-rule-that-depends-on-time
[DL-16]: ../architecture/domain_layer/domain_layer_design.md#16-why-it-looks-like-this
[DL-21]: ../architecture/domain_layer/domain_layer_design.md#21-module-graph
[DL-22]: ../architecture/domain_layer/domain_layer_design.md#22-relationships
[DL-44]: ../architecture/domain_layer/domain_layer_design.md#44-planning
[DL-62]: ../architecture/domain_layer/domain_layer_design.md#62-commands
[DL-74]: ../architecture/domain_layer/domain_layer_design.md#74-considered-and-left-out
[PL-134]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#134-re-optimize-and-accept

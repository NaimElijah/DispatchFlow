# ADR-065: Facts outrank intent: an accepted report stands, the plan is corrected, and a person is told

- **Status:** Accepted
- **Date:** 2026-11-14

## Context

On 2026-11-14 the owner, Naim Elijah, decided for the team that the domain layer design (DL) is
settled, and that its rule for a fact that contradicts the plan gets its own ADR. He also decided
in the same round how an order's status ranks a cancellation against an outcome ([BR-33]). No
"ADR pending" marker covers either.

Facts arrive late. A phone can work offline for hours ([FR-69], [FR-70]), so a report can reach the
server after a dispatcher has moved, dropped or cancelled the stop. No lock can prevent this, because
the delivery has already happened on the street ([DL §8][DL-8]).

The requirements already say part of the answer:

- an outcome a driver recorded while the stop was on their route is accepted however late it arrives
  ([BR-27]);
- the first outcome wins, and a contradicting report is refused ([BR-18], [BR-88], [BR-90]);
- an accepted late outcome takes the stop off the other driver's route, tells that driver and alerts
  the dispatcher ([BR-89]);
- whether the stop was on the driver's route is judged from the route's history at the report's
  corrected time ([BR-110], [BR-114]);
- an order cannot be changed or cancelled once its stop is *arrived* or has an outcome ([BR-92]).

This ADR records the mechanism that makes those rules hold together.

## Decision

**Planning owns intent. Live Operations owns facts.** `Plan` is one aggregate; `Delivery` and
`RouteRun` are others. So reports from many phones never bump the version that protects dispatchers'
edits ([BR-76], [DL §16][DL-16]). When a fact contradicts the plan, the fact stands, the plan is
corrected and a person is told.

1. **Entitlement decides whether a fact is accepted.** The use case asks Planning's `RouteMembership`
   whether the stop was on that driver's route in the revision in force at the report's corrected
   time, and passes the answer to `Delivery` as an `Entitlement`. A report that contradicts a recorded
   outcome is refused, and the dispatcher is alerted.
2. **An accepted fact stands.** `Delivery` records it and raises `OutcomeRecorded`. A listener in Live
   Operations compares it with the current revision. If they disagree, it calls
   `ReconcileLateOutcome`, which changes `Plan` and returns what it changed. The listener then raises
   the dispatcher's alert with a dedupe key. Both steps are idempotent.
3. **The plan is corrected so that the stop ends on the route that served it:**

| In the current plan the stop is... | `Plan.reconcileLateOutcome` | Who is told |
|---|---|---|
| Still on the reporting driver's route | Nothing | No one |
| On another driver's route | Removes it there and records it as *served late* on the reporting driver's route, as a new revision ([BR-89]) | The dispatcher, and the other driver by change notice |
| Dropped by a later re-optimization | Moves it from *dropped* to *served late* on the reporting driver's route | The dispatcher |
| Gone because its order was cancelled | Keeps the cancellation and records *served after cancellation* on the reporting driver's route | The dispatcher |

   **The fourth row is the rule.** A report whose corrected time is before the cancellation is
   accepted as *served after cancellation* ([BR-130]), and the dispatcher is alerted; one timed after
   it is refused and shown ([BR-88], [BR-90]). The owner chose this on 2026-11-14, and [BR-88] and
   [BR-90] were reworded to say it ([PL §24][PL-24] tests it).
4. **No lock.** The cancel-versus-report race is the same path. `Order.cancel` refuses when the
   `StopState` it was given already shows a fact ([BR-92]). When the fact lands after that read, the
   table above settles it.
5. **A corrected time later than the server's receipt time is cut to the receipt time** ([BR-110]).
   The owner decided this on 2026-11-13 ([Q-92], answer 8).
6. **A backdated report is held.** A late report that would move a stop off another driver's route,
   or override a cancellation, waits for the dispatcher's confirmation if its session made contact
   with the server between 2 minutes after the report's corrected time and 2 minutes before the
   report arrived ([BR-132]). The owner decided this on 2026-11-14.
   - **Contact** is any request from the session other than a token refresh or an outbox send. The
     server keeps each session's contact times for the day ([BR-133]), because a single "last contact"
     would let a later request hide an earlier one inside the window.
   - **While the report is held,** an outcome recorded on the stop's current route wins.
   - **The dispatcher confirms or refuses it.** A confirmed report follows items 2 and 3. A refused
     one is treated as [BR-90]: refused and shown to the dispatcher.
7. **An order's status is derived, never stored,** and facts come first. `OrderStatus.derive` takes
   one value from each of its three owners and returns the first match ([DL §9.6][DL-96]):
   1. *delivered* or *failed*, the effective outcome of the current attempt;
   2. *cancelled*;
   3. *planned* or *dropped*, where the plan holds the current attempt, unless that plan is
      *abandoned* ([BR-131], [ADR-053]);
   4. *unplanned*, including a put-back that starts a new attempt ([FR-258]) and an order on an
      abandoned plan.

   A cancellation still shows beside an outcome. The owner decided this precedence on 2026-11-14 and
   it is added to [BR-33]. The `OrderStatuses` query reads a whole date at once, so a screen of 200
   orders makes three queries.

## Consequences

- **Easy:** one mechanism handles the online race and the offline case, and the offline case is the
  common one ([DL §8][DL-8]).
- **Easy:** reports, the next re-optimization and the "every order accounted for" check ([BR-22]) see
  where each stop was served, not where it was planned.
- **Easy:** a delivery to a cancelled order is visible. The order reads *delivered*, and the
  cancellation is shown beside it.
- **Hard:** one report takes three transactions: `Delivery`, then `Plan`, then the alert. Between the
  first and the second the plan still shows the old route, and the other driver may still be heading
  to a stop that has been served.
- **Hard:** a dispatcher who is editing the plan when a reconciliation lands gets `STALE_VERSION` and
  refetches ([DL §11][DL-11]).
- **Hard:** a screen of orders needs three queries and a `derive` call per order.
- **Cost:** `Plan.reconcileLateOutcome`, a table of cases to test, an alert kind with a dedupe key,
  and a test that makes the reconcile call fail once and checks the stop still ends up on the
  serving route ([PL §24][PL-24]).
- **Risk:** the judgment depends on the device's clock offset ([BR-114]). A wrong offset can pick the
  wrong revision. The cap of item 5 limits only a corrected time that lands after the receipt.
- **Risk:** the hold of item 6 compares the report's corrected time with the session's contact times,
  with 2 minutes' grace at each end of the window. A report backdated by less than the grace is not
  held, and a phone whose offset is wrong by more than the grace can be held when it should not be,
  or escape the hold. Token refreshes and outbox sends do not count as contact, so a phone that only
  syncs its outbox is never held. The pilot's alert log shows how often.

## Alternatives considered

- ***Cancelled* ranks above an outcome.** Rejected: it would show *cancelled* for an order a driver
  delivered, which hides a fact ([DL §9.6][DL-96]).
- **Refuse a report for a stop that has moved.** Rejected: [BR-27] accepts an outcome recorded while
  the stop was on the driver's route, and the goods have already been handed over.
- **An advisory lock per order.** Rejected: it would serialize the online race but not the offline
  case. One mechanism for both is simpler than two ([DL §8][DL-8]).
- **Judge a report against the current plan** and not the revision at its corrected time. Rejected:
  [BR-110] judges by the route's history.
- **Store the order's status and mirror it through the actions of [BR-93] to [BR-96].** Rejected: it
  invites drift between three owners ([DL §16][DL-16]).

## Revisit when

- The pilot's alert log shows that most reconciliations come from online races, a report seconds
  after a hand-over or a cancellation, and not from offline phones. A per-order lock would then
  cover the common case.
- The company's dispatcher reads an order that shows *delivered* beside a cancellation as wrong.
  Review the precedence of [BR-33] with the company's own terms ([DL §17][DL-17]).

[BR-18]: ../requirements/requirements.md#driver-reports-and-outcomes
[ADR-053]: ADR-053-plan-lifecycle-publishing-stability-drop-reasons-and-abandoned-plans.md
[BR-22]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-27]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-33]: ../requirements/requirements.md#orders-records-and-history
[BR-76]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-88]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-89]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-90]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-92]: ../requirements/requirements.md#orders-records-and-history
[BR-93]: ../requirements/requirements.md#orders-records-and-history
[BR-96]: ../requirements/requirements.md#orders-records-and-history
[BR-110]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-114]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-130]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-131]: ../requirements/requirements.md#orders-records-and-history
[BR-132]: ../requirements/requirements.md#driver-reports-and-outcomes
[DL-8]: ../architecture/domain_layer/domain_layer_design.md#8-when-a-fact-contradicts-the-plan
[DL-11]: ../architecture/domain_layer/domain_layer_design.md#11-concurrency
[DL-16]: ../architecture/domain_layer/domain_layer_design.md#16-why-it-looks-like-this
[DL-17]: ../architecture/domain_layer/domain_layer_design.md#17-ubiquitous-language
[DL-96]: ../architecture/domain_layer/domain_layer_design.md#96-order-status-br-33-is-derived-not-stored
[FR-69]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-70]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-258]: ../requirements/requirements.md#live-operations-and-re-optimization
[Q-92]: ../requirements/requirements.md#open-questions
[PL-24]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#24-testing
[BR-133]: ../requirements/requirements.md#driver-reports-and-outcomes

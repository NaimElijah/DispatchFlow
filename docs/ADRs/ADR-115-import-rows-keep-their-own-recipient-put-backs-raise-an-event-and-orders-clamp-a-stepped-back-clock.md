# ADR-115: An import row keeps its own recipient, a put-back onto another date raises `OrderPutBack`, and Orders' commands clamp a clock that stepped back

- **Status:** Accepted
- **Date:** 2026-11-15

## Context

A review of the whole domain layer, on `dev` at `4701fd5`, found three defects in Customers &
Orders ([DL §4.3][DL-43]).

- **An import gives a neighbour's messages to the first resident.** `apartment` is an order column
  ([FR-309], [ADR-086]), so every flat in a building has the same normalized address. [ADR-102]
  reads that rows whose addresses normalize alike, and match no customer, share one new customer,
  made from the first of them, and a row that matches a customer takes only its defaults. Neither
  row's name nor its phone reached its order. Row 1 is Dana Levi, 052-1111111, apartment 3; row 2
  is Avi Cohen, 054-2222222, at the same address, apartment 9, with no recipient columns. Both
  orders pointed at Dana, so [FR-348] sent Avi's SMS and rating questions to Dana's phone, and
  Avi's name and phone were stored nowhere. A row matching an existing customer under another name
  lost the same way.
- **A put-back onto another date left the old date's plan stuck.** [ADR-102] read that a put-back
  raises no event, because [DL §7.2][DL-72] gives it none. An order dropped from tomorrow's ready
  plan and put back onto next week ([FR-75], [FR-258]) left its drop on tomorrow's plan. That plan
  then refused to publish with `OrderChangedSinceSolve`, because it held an order no longer on its
  date ([BR-72]), and no dispatcher's edit could take the drop off.
- **Commands accepted a time that `restore` refuses.** `ImportBatch.restore` refuses a state older
  than the preview, and `Customer.restore` a retirement older than the customer's creation. But
  `ImportBatch.confirm` and `discard` and `Customer.retire` took any `now`. A server clock that
  stepped back between two requests, as an NTP correction does, saved a row that could never be
  loaded again. `ImportBatch.expire` was already safe: before the preview's hour has passed it is
  refused with `ImportNotYetExpired`.

## Decision

On 2026-11-15 the owner, Naim Elijah, decided:

1. **An import row that names someone else gives its order that person as the recipient**
   ([FR-87], [FR-348], [FR-311]). When a row gives no recipient, and its name or its phone differs
   from those of the customer it matches or shares, the row's name and phone become its order's
   recipient. A row whose recipient columns are filled keeps them as they are. A row whose name and
   phone are the customer's, or that gives neither, gets no recipient of its own. The match a use
   case builds for an existing customer therefore carries the customer's name and phone beside its
   defaults (`CustomerMatch.Candidate`). This amends [ADR-102]'s reading that rows sharing a new
   customer are made from the first of them: they still share that customer, and each later row
   keeps its own contact as its order's recipient.
2. **Putting an order back onto another date raises `OrderPutBack`** ([FR-75], [FR-258], [FR-316]).
   The event, in `orders.events`, carries:
   - `eventId`, built as every event's is, at the order's next version ([DL §7.3][DL-73]);
   - `occurredAt`, the time of the put-back;
   - `order`, the order's `OrderId`;
   - `from`, the `DeliveryDate` it left, whose plan held the attempt;
   - `to`, the `DeliveryDate` it is on now;
   - `attempt`, the number of the attempt that failed or was dropped, as an `int`, as
     `OrderRescheduled` and `OrderCancelled` carry it ([ADR-111]). The order's current attempt is the
     next one.

   Planning's listener, built with its milestone, takes that order and attempt off the plan of
   `from`, as it does for a date change ([FR-316]). A dropped attempt leaves the plan's drops. A
   failed attempt's stop keeps its recorded outcome ([FR-258]). This supersedes [ADR-102]'s reading
   that a put-back raises no event, and amends [ADR-064]'s event catalogue: it adds `OrderPutBack`.
3. **A put-back onto the order's own date raises nothing** ([BR-113]). The order keeps its date, so
   the date's orders carry the new attempt. Until the plan is solved or re-optimized again, a ready
   plan refuses to publish with `OrderNotAccounted` for the new attempt, which is the refusal the
   dispatcher needs: the new attempt has no stop yet ([BR-72]). The next solve, or the re-optimization
   that [FR-75] names, replaces the plan's drops, so the old attempt's drop leaves with no event.
4. **Orders' commands clamp a time that a stepped-back clock gives.** `ImportBatch.confirm` and
   `discard` record the preview's time when `now` is earlier, and `Customer.retire` records the
   customer's creation time when `now` is earlier. So the state they save is one `restore` accepts.
   `confirm` still judges the hour by the `now` it is given, and the clamped time is never later than
   that, so a confirmation inside the hour stays inside it ([FR-399]). The retention counted from a
   clamped retirement moves by the size of the step ([NFR-257]).

## Consequences

- **Easy:** each flat's recipient gets their own messages and rating questions, and the driver sees
  the person the row named beside the customer's contact ([FR-87]).
- **Easy:** a put-back onto another date frees the old date's plan without a dispatcher's edit, the
  same way a reschedule does, and the listener retries until it succeeds ([ADR-064]).
- **Easy:** a clock step never makes an import batch or a customer unloadable, and no user sees a
  refusal for something they can't fix.
- **Hard:** the use case reads each matched customer's name and phone with its defaults, so the
  match query returns two more columns, both personal data.
- **Hard:** a row whose name differs from the customer's only in how it is spelled, such as "Dana
  Levi" against "דנה לוי", gets a recipient of its own. Names are compared in the kernel's normal
  form ([ADR-106]) and nothing more.
- **Hard:** a row that gives another name and no phone gets a recipient with no phone, so [FR-348]
  still sends its messages to the customer's phone.
- **Cost:** Planning's `OrderPutBack` listener arrives with its milestone, beside the
  `OrderRescheduled` one.
- **Risk:** a clamped time differs from the real time by the clock's step, so an audit trail may
  show a retirement or a discard a few seconds later than it happened.

## Alternatives considered

- **Refuse a row whose name or phone differs from its customer's,** with a named error. Not chosen:
  a building's tenants are a normal file, and refusing them would make the dispatcher split the file
  or type each recipient by hand.
- **Make each differing row its own new customer.** Rejected: [FR-310] matches a customer on its
  address, so the next import would find several customers at the address and refuse every row
  ([FR-312]).
- **Carry the attempt as the kernel's `Attempt` in `OrderPutBack`.** Not chosen: [ADR-111] keeps
  events carrying it as an `int`, and the other two Orders events do.
- **Raise `OrderPutBack` for a put-back on the same date too.** Not chosen: the plan already holds
  the date's orders and the next solve replaces its drops, so no module would need the event
  ([ADR-064]).
- **Refuse a `now` before the preview or the creation with a named error.** Not chosen: the user
  can't act on it, a retry fails until the clock catches up, and the error would need a code and a
  message in both apps for an infrastructure fault.
- **Let `restore` accept a state older than the preview or the creation.** Rejected: the at-rest
  check is what catches a damaged row, and it would no longer.

## Revisit when

- A pilot file holds recipient columns that some rows fill and others don't, and dispatchers expect
  the filled ones to apply to the whole flat.
- Dispatchers see recipients created for names that differ only in spelling, and want a looser
  comparison.
- Planning's listener finds a plan state where taking the drop off is wrong, such as a completed
  plan.
- A clock step larger than a few seconds is seen in production: alert on it, rather than clamping
  silently.

[ADR-064]: ADR-064-events-only-where-they-earn-their-place-through-the-modulith-registry.md
[ADR-086]: ADR-086-csv-imports-upload-then-confirm-within-an-hour.md
[ADR-102]: ADR-102-order-references-are-checked-on-every-move-to-a-date.md
[ADR-106]: ADR-106-free-text-is-normalized-and-capped-by-one-kernel-rule.md
[ADR-111]: ADR-111-attempt-is-a-shared-kernel-value-and-names-and-addresses-drop-invisible-fillers.md
[DL-43]: ../architecture/domain_layer/domain_layer_design.md#43-customers--orders
[DL-72]: ../architecture/domain_layer/domain_layer_design.md#72-the-events
[DL-73]: ../architecture/domain_layer/domain_layer_design.md#73-shape-and-delivery
[BR-72]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-113]: ../requirements/requirements.md#time-and-limits
[FR-75]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-87]: ../requirements/requirements.md#customers-and-orders
[FR-258]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-309]: ../requirements/requirements.md#customers-and-orders
[FR-310]: ../requirements/requirements.md#customers-and-orders
[FR-311]: ../requirements/requirements.md#customers-and-orders
[FR-312]: ../requirements/requirements.md#customers-and-orders
[FR-316]: ../requirements/requirements.md#customers-and-orders
[FR-348]: ../requirements/requirements.md#messages-to-recipients
[FR-399]: ../requirements/requirements.md#customers-and-orders
[NFR-257]: ../requirements/requirements.md#nfr-257

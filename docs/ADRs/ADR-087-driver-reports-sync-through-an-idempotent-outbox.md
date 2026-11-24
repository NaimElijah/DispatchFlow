# ADR-087: Driver reports wait in an outbox on the phone and reach the server with a user-scoped idempotency key

- **Status:** Accepted; extended by ADR-121
- **Date:** 2026-11-13

## Context

A driver can start and finish a route and report stops without a connection ([FR-70]). The phone
sends those reports later, over a connection that may drop halfway, so the same report can arrive
twice and a late one can arrive after the stop has left the driver's route. The requirements say:

- a report received twice takes effect once, and the repeat gets the first answer ([BR-28]);
- the request key is scoped to the signed-in user ([NFR-65]), and a key reused with a different
  body is refused ([NFR-130]);
- reports of one route are sent in the order they were made ([FR-247]);
- a report stays queued until the server acknowledges it, and a refused one is shown to the driver,
  except a rate-limited one, which stays silent ([FR-248], [NFR-100], [NFR-101]);
- an outcome recorded while the stop was on the driver's route is accepted however late it arrives,
  except as [BR-88] says ([BR-27]).

These rows carried the marker "ADR pending: driver report sync and idempotency". On 2026-11-13
it was decided to adopt the design of [PL §13.3] and [PL §19]: user-scoped keys, a `request_key`
store on the server, and a per-route order in the phone's outbox ([PL §25]). This extends
[ADR-043], which left idempotency keys on driver reports to an ADR of their own.

## Decision

**On the phone** ([PL §19], [TS §11]):

- **Every report is written to an outbox first.** The outbox is a table in the phone's encrypted
  database (SQLCipher, key in `expo-secure-store`, [NFR-39]). A `ReportQueue` port sits over it.
- **The outbox row's `id` is the `Idempotency-Key`,** a UUID made when the driver acts. A row also
  holds the user, the route, a per-route sequence number, the kind, the body, the device time, a
  state (`pending`, `sending` or `refused`) and an attempt count.
- **A proof photo waits in its own table,** keyed by the outbox row, and never in the gallery
  ([NFR-204]).
- **One sender loop runs at a time.** It takes the oldest `pending` row by `(route_id, seq)`:
  1. If the row has a proof, `PUT /proofs/{outbox_id}` first. That call is idempotent.
  2. `POST /routes/{routeId}/reports` with `Idempotency-Key` set to the row's `id` and
     `X-Device-Time` set to the phone's clock.
  3. On a 2xx answer, delete the row and its proof.
  4. On 409 or 422, mark the row `refused` and show it to the driver.
  5. On 429, wait for `Retry-After`, silently, without counting an attempt ([NFR-100], [NFR-101]).
  6. On a network error or a 5xx answer, back off 5, 10, 20, 40, then 60 seconds.
- **A reconnect and the app coming to the foreground both start the loop at once,** which meets the
  60-second rule ([FR-246]).
- **Reports wait for their own user.** A row carries its user, and reports queued when a session
  ends stay encrypted and are sent after the same user signs in again ([FR-331]). Sign-out keeps the
  outbox. A refused refresh wipes the route data and keeps the outbox ([Q-92] #4). A phone offline for
  14 days wipes the queued reports and proofs with the route data ([Q-92] #3).
- **A phone below the minimum app version** is answered `APP_UPDATE_REQUIRED` on screen loads, but
  the server still accepts its reports and positions, so the queue always drains ([NFR-134],
  [ADR-082]).

**On the server** ([PL §13.3], [PL §6.2]):

- **Idempotency.** The core looks up `(user, Idempotency-Key)` in `request_key`, whose primary key is
  `(user_id, idem_key)`. It stores the body's hash and the response.
  - The same body hash returns the stored response ([BR-28]).
  - A different hash answers 409 with the code `IDEMPOTENCY_KEY_REUSED` ([NFR-130]).
  - The `request_key` row, the change and the audit record are written in one transaction.
- **Clock correction.** The offset is the server's receive time minus `X-Device-Time`. The report's
  corrected time is its device time plus the offset ([BR-44], [BR-114]). A corrected time later than
  the server's receipt is cut to the receipt time ([BR-110]).
- **Entitlement.** The use case asks Planning's `RouteMembership` whether the stop was on this
  driver's route in the revision in force at the corrected time ([BR-110]), and passes the answer to
  `Delivery`. So a late report on a stop that has since moved is still accepted ([BR-27]). Revisions
  are stored as rows for this reason ([ADR-059]).
- **The first outcome wins.** A report that contradicts a recorded outcome, or is timed *after* its
  order or plan was cancelled, is refused and shown to the dispatcher ([BR-88], [BR-90]). A report
  timed *before* the cancellation is served after cancellation: it is accepted and the dispatcher is
  alerted ([BR-130], [ADR-065]).
- **A backdated report can be held.** A late report that would move a stop off another driver's
  route, or override a cancellation, waits for the dispatcher's confirmation if a session of the
  reporting account made contact with the server between 2 minutes after the report's corrected time
  and 2 minutes before the report arrived ([BR-132]). This was decided on 2026-11-14.
  - Contact is any request from any session of the reporting account, not only the session that sent
    the report, other than a token refresh or an outbox send. The server keeps contact times for 14
    days, as long as a late report can arrive ([NFR-235]), only for sessions of accounts with the
    Driver role and at most one per minute. The data inventory lists them ([NFR-72], [BR-133]).
  - A further late report for a stop that already has a held report is held too.
  - While the report is held, an outcome recorded on the stop's current route wins.
  - The dispatcher confirms or refuses it. A refused held report is treated as [BR-90]: refused
    and shown to the dispatcher.
  - The outbox gets an acknowledgement for a held report, so it stops resending it.
- **Reconciliation.** When the current plan disagrees, because the stop moved, was dropped or its
  order was cancelled, a listener on `OutcomeRecorded` reconciles the plan, and the dispatcher is
  alerted ([BR-89], [ADR-065]).

## Consequences

- **Easy:** the phone can retry any report any number of times, and a dropped connection after the
  server committed does no harm: the retry gets the first answer.
- **Easy:** a driver's offline work and its order survive a restart of the app, because the outbox is
  in the encrypted database.
- **Easy:** one user's keys cannot match another's rows, because the user is part of the primary key.
- **Hard:** the server keeps a stored response for every report, and a `request_key` row is written
  in each report's transaction.
- **Hard:** a late report can change a plan the dispatcher is looking at. Another driver loses a stop
  and is told ([BR-89]).
- **Hard:** the phone must keep the outbox across sign-out, so a shared phone holds the previous
  driver's encrypted reports until that driver signs in again.
- **Cost:** a table, a hash of every body, and a sender loop with its own backoff rules.
- **Risk:** no retention period is set for `request_key` rows. The table grows until a rule is
  written.
- **Risk:** the 14-day wipe ([Q-92] #3) discards queued reports and proofs of a phone that stays
  offline that long.

## Alternatives considered

- **A key scoped to the whole system, not the user.** Rejected: [NFR-65] scopes it to the signed-in
  user.
- **Answer a repeat with an error.** Rejected: [BR-28] says the repeat gets the first answer.
- **Judge a late report against the plan as it is now.** Rejected: a report on a stop that has since
  moved would be refused, against [BR-27].
- **Send queued reports in parallel, or in any order.** Rejected: [FR-247] sends the reports of one
  route in the order they were made.
- **Show a rate-limit refusal to the driver, or count it as an attempt.** Rejected: [NFR-100] and
  [NFR-101] keep it silent and keep the report.

## Revisit when

- A pilot report is applied twice, or an unchanged body is refused as `IDEMPOTENCY_KEY_REUSED`.
- A retention period is set for `request_key` rows, or the table slows a report's transaction.
- The device checklist finds queued reports not sent within 60 seconds of the connection
  returning ([FR-246], [NFR-140]).
- Drivers share a phone in the pilot. Reconsider keeping an outbox across sign-out.

[ADR-043]: ADR-043-generated-code-from-both-contracts-and-problem-details-errors.md
[ADR-059]: ADR-059-plan-revisions-are-stored-as-rows.md
[ADR-065]: ADR-065-facts-outrank-intent.md
[ADR-082]: ADR-082-supported-phone-app-versions.md
[PL §13.3]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#133-driver-report
[PL §19]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#19-phone-app
[PL §25]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#25-decisions-to-record-as-adrs
[PL §6.2]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#62-core-api-conventions
[TS §11]: ../architecture/tech_stack.md#11-phone-app
[BR-27]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-28]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-44]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-88]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-89]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-90]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-110]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-114]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-130]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-132]: ../requirements/requirements.md#driver-reports-and-outcomes
[FR-70]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-246]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-247]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-248]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-331]: ../requirements/requirements.md#publishing-and-the-phone-app
[NFR-39]: ../requirements/requirements.md#nfr-39
[NFR-65]: ../requirements/requirements.md#nfr-65
[NFR-100]: ../requirements/requirements.md#nfr-100
[NFR-101]: ../requirements/requirements.md#nfr-101
[NFR-130]: ../requirements/requirements.md#nfr-130
[NFR-134]: ../requirements/requirements.md#nfr-134
[NFR-140]: ../requirements/requirements.md#nfr-140
[NFR-204]: ../requirements/requirements.md#nfr-204
[Q-92]: ../requirements/requirements.md#open-questions
[BR-133]: ../requirements/requirements.md#driver-reports-and-outcomes
[NFR-72]: ../requirements/requirements.md#nfr-72
[NFR-235]: ../requirements/requirements.md#nfr-235

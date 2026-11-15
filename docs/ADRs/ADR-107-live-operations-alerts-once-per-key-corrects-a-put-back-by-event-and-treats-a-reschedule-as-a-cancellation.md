# ADR-107: Live Operations alerts once per key, corrects a put-back by event, and treats a reschedule as a cancellation

- **Status:** Accepted; amended by ADR-114
- **Date:** 2026-11-14

## Context

The Live Operations domain is built ahead of its milestone ([DL §4.5][DL-45]). It holds three
aggregates: `Delivery`, the facts of one stop; `RouteRun`, a route being driven; and `Alert`,
something a dispatcher must look at. The requirements and the domain document settle most of their
rules: the first outcome wins ([BR-18]), a late outcome is judged by the route's history
([BR-110]), and a backdated report is held ([BR-132] to [BR-136]). Five decisions were left open:

- **A correction after a put-back.** [FR-404] allows a failed stop to be corrected to *delivered*
  after a put-back ([FR-258]) while the new stop has no outcome. The new stop then leaves its
  route. Otherwise the correction is refused and the conflict is named. `Delivery` belongs to Live
  Operations and the route to Planning, and Planning is upstream ([DL §2.3][DL-23], rule 2).
- **When an unconfirmed route change alerts.** [FR-157] lists unconfirmed route changes
  ([FR-97]) among the alerts, with no time.
- **A late outcome for a rescheduled order.** [BR-88], [BR-90] and [BR-130] cover a cancelled order
  or plan. [ADR-101] left the rescheduled case for its own decision.
- **The alert dedupe key.** [DL §5][DL-5] backs "an alert per dedupe key" with a unique key, and
  [DL §8][DL-8] relies on it so a retried listener alerts once. Neither says what the key is.
- **Small readings:** whether the hold window of [BR-133] includes its ends, and who owns Live
  Operations' retention periods ([NFR-184]).

Two cross-module questions arrived while the domain was built. Reporting asked which time a stop
has once a correction makes it *delivered*. Messaging found that a correction sends no message
([FR-374]).

## Decision

On 2026-11-14 the owner, Naim Elijah, decided:

1. **A correction after a put-back is checked against the new stop, and announced by an event.**
   `Delivery.correct` takes the put-back's state as a fact (`LaterAttempt`): no put-back, a new stop
   with no outcome, or a new stop with an outcome. Correcting failed to delivered refuses with
   `LaterAttemptHasOutcome` when the new stop has an outcome, and the error names that stop, its
   attempt and its outcome. Otherwise it raises `PutBackSuperseded`, which names the new stop, its
   attempt and the delivery date it is on, as `OrderCancelled` names its date. Planning's listener,
   built with its milestone, finds that date's plan and calls `Plan.removeStopOf` with the FR-404
   cause, `CORRECTED_AFTER_PUT_BACK` ([FR-404], [FR-258], [FR-333]).
2. **An unconfirmed route change alerts 10 minutes after its revision took effect.** This is a
   starting value, `ConfirmationDeadline.GRACE`, and the deadline instant counts as passed
   ([FR-97], [FR-157], [FR-250]).
3. **A late outcome for a rescheduled order is handled like a cancellation.** One timed before the
   reschedule is accepted and served late on the old date. One timed at or after it is refused with
   `ReportAfterCancellation` and shown to the dispatcher. A backdated one is held as one that
   overrides a cancellation would be ([BR-88], [BR-90], [BR-130], [BR-132]).
4. **An alert is raised once per dedupe key, and acknowledging it silences the key for good.**
   The key is the alert's kind plus the IDs its kind names, defined per kind in `AlertSubject`.
   Where a new fact should alert again, the key includes that fact:
   - a stop at risk keys on the stop and the revision;
   - an unconfirmed change keys on the route and the revision;
   - a problem report keys on its own alert ID, so every report alerts.

   The repository's unique index on the key, with an insert that does nothing on conflict, makes a
   second raise do nothing ([FR-157], [FR-260], [DL §5][DL-5]).
5. **Small readings:**
   - The hold window of [BR-133] includes both its ends: from 2 minutes after the report's
     corrected time to 2 minutes before its receipt.
   - Live Operations owns its retention periods, in `RetentionPeriods`: 7 days for positions and
     90 days for proofs ([NFR-184]). The text of an *other* failure reason and of a correction
     reason is personal data too, and NFR-31 gives each kind a period, so it is kept 90 days, as
     the proofs it sits beside are ([NFR-31]).

**A corrected outcome's time** answers Reporting's question. A correction may state when the
corrected outcome happened. If it states none, the outcome keeps the original report's time.
`Delivery.outcomeTime()` gives the effective outcome's time, and this is the time Live Operations'
view gives. For a driver's report it is the corrected time ([BR-114]). For a record made on a
driver's behalf it is the time of the record. A stated time after the correction is made is
refused (`CorrectionTimeInFuture`).

**A correction raises no event of its own.** A correction sends no message ([FR-374]), and
Reporting reads corrected figures from Live Operations' view, so no module would hear an
`OutcomeCorrected`. [ADR-064] allows an event only where it earns its place, so there is none. A
correction after a put-back still raises `PutBackSuperseded`, which Planning hears.

On 2026-11-15 the owner, Naim Elijah, decided:

6. **One held report per driver and route on a stop.** While a driver's report from one route is
   held on a stop, their next report there is refused, so a phone can't flood the dispatcher
   with held reports: a repeat silently (`SameOutcomeAlreadyRecorded`), and a different one as a
   contradiction (`OutcomeAlreadyRecorded`, [BR-18]). A further late report from another driver or
   route is still held. The owner allowed one edit to `requirements.md` for this, and only
   [BR-136]'s row was changed to say so.

**The event catalogue.** This ADR amends [ADR-064]'s event catalogue: it removes
`OutcomeCorrected` and adds `PutBackSuperseded`.

### Readings this ADR settles

These rules no document settled; the code takes them.

- **Delivery's state is derived** from its records: the effective outcome first, then an open held
  report, then an arrival, else *pending* ([BR-87], DL §9.3).
- **Order of refusals.** A driver's report is checked in this order. Then the report is held or
  recorded.
  1. Its content: the proof ([FR-367]), a proof that names no file (`ProofHasNoFile`), the door
     ([FR-366]) and the photo ([FR-368]).
  2. A cancellation at or before its time.
  3. Its entitlement.
  4. The outcome that stands.
  5. The same driver's open held report on the stop (point 6).

  The cancellation comes before the entitlement. A stop whose order was cancelled has left the
  revision in force, and BR-90 has such a report shown to the dispatcher.
- **Identical against contradicting** ([BR-88], [BR-90]). A repeat is refused silently
  (`SameOutcomeAlreadyRecorded`) when it has the same kind and, for a failure, the same reason.
  Neither the text of *other*, nor a proof, nor *left at the door* counts. Anything else contradicts
  and is shown (`OutcomeAlreadyRecorded`).
- **A correction may also change just a failure's reason, keeping it *failed*.** A correction to
  the outcome the stop already has is refused (`CorrectionChangesNothing`).
- **A dispatcher's outcome carries no proof.** `recordOnBehalf` and `correct` take a
  `DispatcherOutcome`, which can hold a reason or *left at the door* but no proof, so only a
  driver's report brings one, and its retention runs from that report ([FR-367], [NFR-184]).
- **Instant boundaries:**
  - A report timed exactly at a cancellation counts as after it, so it is refused.
  - `StartDeadline` and `ConfirmationDeadline` count as passed at their own instant.
  - A stop is at risk only when its projection is strictly later than its window's end less 15
    minutes ([FR-253]).
- **On-behalf records** ([FR-73]):
  - check only the first-outcome rule and the door;
  - need no proof, entitlement or hold check, and count from the time they are recorded;
  - overtake held reports ([BR-134]);
  - start no route run, because they carry no `DriverReport`.
- **The failed-stop alert** ([FR-102]) follows a driver's recorded *failed*. A held report alerts as
  *report held* instead. No failed-stop alert follows a dispatcher's record or a correction.
- **Tracking** ([FR-328], [FR-394], [NFR-30]):
  - a position counts from the run's start, included, to an hour after shift end, excluded;
  - a finished run's finish is included;
  - the shift end is the current driver's, or the confirmed return time, read as a fact when
    each position is checked (`TrackingStanding`). After a hand-over, the new driver's positions
    are kept until their own shift end plus 1 hour. This supersedes an earlier reading, approved
    on 2026-11-14 and dropped the same day in review, that fixed the shift end at the start:
    the requirements outrank it;
  - a position from the old driver after a hand-over, or from a driver marked unavailable, is
    refused first.
- **A proof** names its uploaded file with a local `ProofId` ([ADR-087]). A driver's proof that
  names no file is refused (`ProofHasNoFile`).
- **Retention keeps every record and erases its personal content.** `Delivery.eraseExpired` erases
  a proof's file and note and keeps its kind, and blanks the text of an *other* reason and of a
  correction reason and keeps the reason's kind. It is a sweep that does nothing when nothing is
  due ([NFR-31], [NFR-184]).
- **`HoldCheck` filters the contact times itself** against the report's window, so the window has
  one owner. It holds the times Access answered.
- **Projections** ([FR-254], [FR-383], [ADR-067]):
  - the latest event in time anchors the chain: the route's start, or a stop's arrival or outcome;
  - with no event, the vehicle leaves at its planned departure;
  - no arrival is projected before now;
  - a later stop that already has a fact is passed on its leg with no service time;
  - a stop skipped before the anchor gets no projection and is never at risk.
- **FR-95's problems** are the three the row names: breakdown, running late and vehicle full, with
  no text. A problem report raises a `DRIVER_PROBLEM` alert and changes no route run.
- **Seen past the plan's current revision** is refused (`UnknownRevision`), so one bad number can't
  mark later revisions seen. An older or equal one is ignored, and a finished run takes Seen too.
- **Seen on a route with no run** stores nothing. A revision that took effect before the run started
  counts as seen, because the driver starts on the route the phone has just read again ([FR-347]).
  The 10-minute alert therefore covers started routes only.
- **Finishing a route with no run** is answered *not started* by the application layer; the domain
  has no error for it.
- **A late report for a put-back stop that FR-404 took off** is judged as a report for a stop on no
  route, so it is recorded as an ordinary late outcome. This is open: see Revisit when.

## Consequences

- **Easy:** Planning reacts to a correction after a put-back without Live Operations calling
  upstream, and a failed call is retried with the event ([ADR-064]).
- **Easy:** a listener that retries raises its alert once, and a dispatcher who acknowledges an
  alert never sees it come back for the same fact.
- **Easy:** a rescheduled order needs no rule of its own: it follows the cancellation path end to
  end.
- **Easy:** Reporting and the live view read one time per outcome, `outcomeTime()`, whatever made
  the outcome.
- **Easy:** a hand-over tracks the new driver for their own shift, with no change to the run.
- **Hard:** a stop that is at risk again under the same revision doesn't alert again once the
  dispatcher acknowledged it.
- **Hard:** a driver who has not started can't confirm a change, so the dispatcher's list shows the
  route as *not started* rather than *unconfirmed*.
- **Hard:** after 90 days a correction shows that it happened and who made it, but not why.
- **Cost:** Planning's `PutBackSuperseded` listener, and the repository's unique index on the key,
  arrive with their milestones.
- **Risk:** Planning's late-outcome reconciliation records a rescheduled order's stop as *served
  after cancellation* ([ADR-101], point 29), which names the case wrongly until Planning gives it
  its own wording.

## Alternatives considered

- **An `OutcomeCorrected` event beside `PutBackSuperseded`.** Not chosen: no module would hear it
  ([ADR-064]), and an event is published language that is never removed once a module listens.
- **Fix the tracking cut-off when the run starts.** Not chosen: after a hand-over to a driver with
  a later shift, their positions would stop at the first driver's shift end ([FR-328], [FR-394]).
- **Hold every further report, the same driver's included.** Not chosen: one phone could fill the
  dispatcher's list with held reports for one stop.
- **Live Operations' listener asks Planning to take the new stop off.** This was the first
  recommendation. Not chosen: the stop is Planning's, and Planning hearing an event keeps the call
  where its owner is.
- **Raise the dedupe only while an alert is open.** Not chosen: the minute jobs would raise a
  route-not-started or at-risk alert again one minute after each acknowledgement.
- **Refuse every late outcome for a rescheduled order.** Not chosen: a delivery that happened before
  the reschedule would be lost, which [ADR-065] rules out.
- **A route run in a *not started* state, created by Seen.** Not chosen: the first report or a
  confirmed held report would then change two existing aggregates in one transaction ([ADR-063]).
- **A hold window that excludes its ends.** Not chosen: the owner's answer was "inclusive".

## Revisit when

- Drivers need to confirm changes before they start, and FR-250's screen must show them.
- An acknowledged alert must come back for a fact that recurs under one revision.
- A late report arrives for a put-back stop that FR-404 took off, and the order shows two
  deliveries.
- Legal advice changes the retention periods ([NFR-184]).

[ADR-063]: ADR-063-a-transaction-changes-one-aggregate-and-every-rule-has-one-owner.md
[ADR-064]: ADR-064-events-only-where-they-earn-their-place-through-the-modulith-registry.md
[ADR-065]: ADR-065-facts-outrank-intent.md
[ADR-067]: ADR-067-live-arrivals-propagate-delay-along-stored-legs-when-asked.md
[ADR-087]: ADR-087-driver-reports-sync-through-an-idempotent-outbox.md
[ADR-101]: ADR-101-completed-plans-take-late-outcomes-as-revisions-and-routes-keep-an-index.md
[BR-18]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-87]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-88]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-90]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-110]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-114]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-130]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-132]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-133]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-134]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-136]: ../requirements/requirements.md#driver-reports-and-outcomes
[DL-23]: ../architecture/domain_layer/domain_layer_design.md#23-rules-between-contexts
[DL-45]: ../architecture/domain_layer/domain_layer_design.md#45-live-operations
[DL-5]: ../architecture/domain_layer/domain_layer_design.md#5-rules-over-sets-of-aggregates
[DL-8]: ../architecture/domain_layer/domain_layer_design.md#8-when-a-fact-contradicts-the-plan
[FR-73]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-97]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-102]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-157]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-250]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-253]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-254]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-258]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-260]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-328]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-333]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-347]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-366]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-367]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-368]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-374]: ../requirements/requirements.md#messages-to-recipients
[FR-394]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-383]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-404]: ../requirements/requirements.md#live-operations-and-re-optimization
[NFR-30]: ../requirements/requirements.md#nfr-30
[NFR-31]: ../requirements/requirements.md#nfr-31
[NFR-184]: ../requirements/requirements.md#nfr-184

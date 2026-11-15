# ADR-114: Live Operations reaches Planning from its own listener, and names every fact it judges

- **Status:** Accepted
- **Date:** 2026-11-15

## Context

A review of the whole domain layer, on `dev` at `4701fd5`, found five gaps in Live Operations that
need a decision, and a few smaller readings in Live Operations and Audit that no document settled.

- **A planned listener would make a module cycle.** [DL §2.1][DL-21] has Live Operations depend on
  Planning. [ADR-107], point 1, made Planning the listener of `PutBackSuperseded`, which would add
  Planning → Live Operations. [ADR-040] says the module graph points one way, and
  [DL §2.3][DL-23], rule 2, says a downstream module reaches upstream from a listener on its own
  event. `OutcomeRecorded` already reaches Planning that way.
- **A skipped stop gets no projected arrival.** [ADR-107] read [FR-254] so that a stop skipped
  before the route's latest event gets no projection, because no leg runs to it from where the
  vehicle is. Messaging's *out for delivery*, *arriving soon* and *time changed* each need one
  ([FR-351] to [FR-353]). So when a route starts with an out-of-order first report ([FR-327],
  [BR-29]), the recipients of the stops it skipped hear none of the three.
- **A correction can't change only *left at the door*, or only an *other* reason's text.**
  `Delivery.correct` judged "changes nothing" with the comparison [ADR-107] defines for repeated
  reports ([BR-88]), which ignores the door flag and the text of *other*. So a dispatcher could not
  fix either alone ([FR-333]).
- **Two different refusals share one alert key.** [ADR-107], point 4, keyed a refused report's
  alert on its plan, route and stop. After a dispatcher acknowledges it, a refusal from another
  driver, or of another outcome, on that stop never alerts ([BR-90], [BR-135]).
- **An entitlement doesn't name the driver.** `Entitlement.OnRoute` said only that the stop was on
  the report's route in the revision in force at the report's corrected time. Nothing compared the
  reporting driver with the route's driver then, so a driver the route was handed away from could
  report on it, and start or finish it, after the hand-over ([BR-02], [BR-110]).

A second review, of the fixes on this branch, found three gaps they left:

- **A skipped stop's planned arrival moves with the clock.** Once its planned arrival has passed,
  the projection is held at now, so it is always zero minutes away and always later than the
  arrival last sent. Its recipient got *arriving soon* a minute after the start, and *time changed*
  twice in the hour after ([FR-352], [FR-353], [FR-384]).
- **A former driver can still act on the route by backdating.** A report or a finish timed before
  the hand-over was judged by the revision in force then, which names the former driver. So an
  offline phone's late finish ended the new driver's run, its *Seen* hid the new driver's
  unconfirmed change, and its late outcome was recorded on the new driver's route ([FR-68],
  [FR-97], [FR-250], [FR-328], [FR-329]).
- **A refused arrival can't alert.** The refused-report key needed an outcome kind, and an arrival
  refused for a cancellation has none ([BR-90], [FR-157]).

## Decision

On 2026-11-15 the owner, Naim Elijah, decided:

1. **Live Operations' own listener calls a Planning API to take off a superseded put-back's stop.**
   `Delivery.correct` still raises `PutBackSuperseded` as [ADR-107] has it. Live Operations' own
   listener on that event calls a Planning API, built with Planning's milestone, which finds the
   plan of the date the event names and calls `Plan.removeStopOf` with the FR-404 cause,
   `CORRECTED_AFTER_PUT_BACK`. Planning never listens to Live Operations, so the graph keeps
   pointing one way. The listener is retried, as every listener is, so the call does nothing when
   the stop is already gone ([FR-404], [FR-258], [ADR-064]). This supersedes [ADR-107], point 1, in
   its last sentence only: the event, its fields and the refusal `LaterAttemptHasOutcome` stand.
2. **A pending stop with no projection uses its planned arrival, never earlier than now.**
   `ArrivalProjection` gives a stop skipped before the latest event, with no fact, its planned
   arrival: from the planned departure along the stored legs, with each window's wait and each
   stop's service as planned and no fact applied, and never earlier than now ([FR-254],
   [ADR-067]). A skipped stop is never *at risk* on that basis alone, because nothing observed
   carries delay to it ([FR-253]). This amends [ADR-107]'s reading that such a stop "gets no
   projection and is never at risk": the second half stands. The projection says which arrivals it
   carried forward along the legs, from the route's start or its latest event, and which are such
   a planned arrival (`ArrivalProjection.isCarriedForward`), and Messaging's `ProjectedArrival`
   carries that basis. *Out for delivery* may give a planned arrival ([FR-351]); *arriving soon* and
   *time changed* go only on one carried forward ([FR-352], [FR-353]), because a planned arrival held
   at now moves with the clock alone and says nothing new about the stop.
3. **A correction may change only the door flag, or only an *other* reason's text.**
   `Delivery.correct` refuses `CorrectionChangesNothing` only when the corrected outcome states
   exactly the effective one: the same kind, the same *left at the door*, and for a failure the
   same reason with the same text. The proof is not compared, because a dispatcher's outcome never
   carries one ([FR-333], [FR-366], [FR-367]). Repeated driver reports keep [ADR-107]'s looser
   comparison ([BR-88]). This amends [ADR-107]'s reading "A correction may also change just a
   failure's reason".
4. **The refused-report alert key adds the reporting driver and the refused fact.**
   `AlertSubject.ReportRefused` names the plan, the route, the stop, the reporting driver and what
   the report stated, as a `RefusedFact`: *arrived*, *delivered* or *failed*. Its key is the stop,
   the route, the driver and that fact. A retried identical refusal still alerts once; another
   driver's refusal, or another fact's, alerts again, and an arrival refused for a cancellation
   alerts as an outcome does ([BR-90], [BR-135], [FR-157]). This amends [ADR-107], point 4.
5. **An entitlement names the route's driver then and now, and only the current driver acts
   directly.** `Entitlement.OnRoute` carries the revision in force at the report's corrected time,
   that revision's driver of the route, and the route's driver now. `Delivery` refuses a driver's
   arrival, *delivered* or *failed* from any account other than the driver then with
   `StopNotOnYourRoute`, in the entitlement's place in [ADR-107]'s order of refusals. A report from
   the driver then who no longer drives the route, as after a hand-over, is never recorded
   directly: its *delivered* or *failed* is held for the dispatcher, whatever the account's
   contacts ([BR-132], [BR-133], [BR-27]), and its arrival, which can't be held, is refused with
   `StopNotOnYourRoute`. `RouteRun.start` takes the route's driver at the report's corrected time.
   `RouteRun.finish` takes that driver and the route's current one, and `RouteRun.acknowledge` the
   sender and the current driver; each refuses any other with `NotYourRoute`, before any other rule
   ([BR-02], [BR-110], [FR-68], [FR-97], [FR-250], [FR-328], [FR-329]). This amends [ADR-107]'s
   reading of the order of refusals.

### Readings this ADR settles

No document said these, and the implementation reads them so:

- **`StopNotOnYourRoute` covers the handed-over driver too.** Its meaning, that the stop was not on
  the reporting driver's route at the report's time, already holds when the route had another
  driver, so no new code for `Delivery`. `RouteRun` has no stop to name, so it gets `NotYourRoute`.
- **`RouteRun` takes the route's drivers, not an `Entitlement`.** Starting or finishing a route is
  about the route, not a stop. For a first stop report, the use case passes the driver the stop's
  `Entitlement` names.
- **A held former driver's report starts no run.** A run starts on a recorded first report, and a
  held one is recorded only when the dispatcher confirms it ([DL §6.2][DL-62]). A driver's explicit
  start, timed before the hand-over, is still judged by the driver then alone: only `finish` and
  `acknowledge` ask for the current driver.
- **The refused-report alert names a refused fact.** [BR-90] and [BR-135] show the dispatcher a
  refused report. Live Operations' own `RefusedFact` names it, rather than the events package's
  `OutcomeKind`, because an arrival is not an outcome.
- **An erased reason states nothing exact.** Retention erases the text of *other* after 90 days
  ([ADR-107], point 5). A failure whose text was erased matches no reason in the correction's
  comparison, so a correction from one *other* text to another stays valid at rest once both texts
  are erased.
- **`Delivery.hasArrival()`** answers whether an arrival is recorded, whatever the state shows.
  While a report is held, `status()` shows *held* over an arrival, so a rule that turns on *arrived*
  or an outcome, such as [BR-92]'s order lock and [FR-373]'s message cut-off, asks this beside the
  effective outcome.
- **`OutcomeRecorded` carries *left at the door*,** from the first outcome, for Messaging's message
  ([FR-356], [FR-374]). After a correction, the effective outcome no longer shows it.
- **A device time is bounded.** `CorrectedTime` refuses a device time or a device clock outside the
  years 1 to 9999 with `DeviceTimeOutOfRange`, the same bound `DeliveryDate` chose. A time near
  `Instant.MIN` or `Instant.MAX` would otherwise fail the correction with a `DateTimeException`,
  which an adapter would answer as a server error ([BR-114]).
- **Audit's caps.** An `ErrorCode` is at most 100 characters, the cap `OperationName` already has,
  checked before the pattern, and refused with `MalformedErrorCode`. An `AuditRecord` names at most
  500 records, refused with `TooManyNamedRecords`. An operation on a whole plan names at most the
  plan, its 15 routes and vehicles, and its 200 stops and their orders ([NFR-04], [NFR-05]), 431 in
  all, so 500 leaves room and still bounds one write ([NFR-93]). This extends [ADR-110].

## Consequences

- **Easy:** no module cycle. Planning keeps the call where its owner is, and the call is retried
  with the event, as for `OutcomeRecorded` ([ADR-064]).
- **Easy:** every pending stop of a started route has a projected arrival, so its recipient gets
  *out for delivery*; a skipped stop's recipient gets *arriving soon* and *time changed* once an
  arrival is carried forward to it.
- **Easy:** a dispatcher can fix a door flag or a typo in a reason, and each fix keeps the
  original beside it.
- **Easy:** a dispatcher who acknowledged one refusal still sees another driver's.
- **Easy:** after a hand-over, only the new driver's reports are recorded directly, and only the
  new driver finishes the route or confirms its changes. A former driver's late outcome reaches the
  dispatcher, who decides.
- **Hard:** a skipped stop's projection ignores the delay the route has built up, so its message
  can be early. It is never earlier than now, and it moves once the stop has a fact or a later one
  anchors.
- **Hard:** a refusal that a driver retries with another outcome alerts again.
- **Hard:** a skipped stop's recipient hears no *arriving soon* until the driver's next report
  carries an arrival to the stop, and may get none if the driver serves it next.
- **Hard:** every late outcome a former driver sends after a hand-over waits for the dispatcher,
  even one with no contact in [BR-133]'s window.
- **Cost:** Planning's API for the superseded put-back, and Live Operations' listener that calls it,
  arrive with their milestones. The use cases must ask Planning for the route's driver at the
  report's corrected time and for its driver now.
- **Risk:** an operation that names more than 500 records fails its audit write, and with it the
  change. None does today.

## Alternatives considered

- **Planning listens to `PutBackSuperseded`,** as [ADR-107] had it. Rejected: it adds
  Planning → Live Operations, a cycle against [ADR-040].
- **Project a skipped stop from the anchor along its planned leg.** Not chosen: no leg runs from
  where the vehicle is to a stop it has passed, and the result would be a guess dressed as a
  propagation.
- **Amend FR-351 to send nothing to a skipped stop.** Rejected: its recipient is still waiting for
  the delivery.
- **Reuse the repeated-report comparison for corrections.** Rejected: it is what made a door flag
  or an *other* text impossible to correct.
- **Key a refused report on its own alert ID,** as a problem report is. Rejected: a phone that
  retries a refused report would alert on every retry.
- **A separate `Delivery` error for a handed-over driver.** Not chosen: `StopNotOnYourRoute` already
  means it, and both apps translate one code.
- **Send *arriving soon* and *time changed* on a planned arrival too.** Rejected: held at now, it is
  always due and always moving, which is what sent both messages too early.
- **Record a former driver's outcome timed before the hand-over, as [BR-27] reads alone.** Rejected:
  the phone's clock is the report's only evidence of its time, so a backdated report would take a
  stop off the new driver's route without anyone deciding.
- **Hold a former driver's outcome only with a contact in [BR-133]'s window.** Not chosen: without
  contact, the report would be recorded straight onto the new driver's route.
- **Key a refused arrival on `OutcomeKind`, with a new constant.** Rejected: `OutcomeKind` is the
  events' outcome, which an arrival is not, and `OutcomeRecorded` would then admit an arrival.
- **Cut a device time to a bound instead of refusing it.** Not chosen: the corrected time would be
  a made-up instant that the dispatcher sees as a fact.

## Revisit when

- Planning's API for the superseded put-back is built, and it needs a fact the event doesn't name.
- Recipients of skipped stops complain that *out for delivery* came too early, or that *arriving
  soon* never came.
- Dispatchers find most held reports after hand-overs are routine, and ask for some to be recorded
  without them.
- An operation must name more than 500 records, such as a bulk change across dates.

[ADR-040]: ADR-040-core-is-a-spring-modulith-monolith-with-one-module-per-context.md
[ADR-064]: ADR-064-events-only-where-they-earn-their-place-through-the-modulith-registry.md
[ADR-067]: ADR-067-live-arrivals-propagate-delay-along-stored-legs-when-asked.md
[ADR-107]: ADR-107-live-operations-alerts-once-per-key-corrects-a-put-back-by-event-and-treats-a-reschedule-as-a-cancellation.md
[ADR-110]: ADR-110-audit-records-hold-only-ids-codes-enums-numbers-and-instants-record-refusals-and-are-kept-a-year.md
[BR-02]: ../requirements/requirements.md#access
[BR-27]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-29]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-88]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-90]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-92]: ../requirements/requirements.md#orders-records-and-history
[BR-110]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-114]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-132]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-133]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-135]: ../requirements/requirements.md#driver-reports-and-outcomes
[DL-21]: ../architecture/domain_layer/domain_layer_design.md#21-module-graph
[DL-23]: ../architecture/domain_layer/domain_layer_design.md#23-rules-between-contexts
[DL-62]: ../architecture/domain_layer/domain_layer_design.md#62-commands
[FR-68]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-97]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-157]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-250]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-253]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-254]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-258]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-327]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-328]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-329]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-333]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-351]: ../requirements/requirements.md#messages-to-recipients
[FR-352]: ../requirements/requirements.md#messages-to-recipients
[FR-353]: ../requirements/requirements.md#messages-to-recipients
[FR-356]: ../requirements/requirements.md#messages-to-recipients
[FR-366]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-367]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-373]: ../requirements/requirements.md#messages-to-recipients
[FR-374]: ../requirements/requirements.md#messages-to-recipients
[FR-384]: ../requirements/requirements.md#messages-to-recipients
[FR-404]: ../requirements/requirements.md#live-operations-and-re-optimization
[NFR-04]: ../requirements/requirements.md#nfr-04
[NFR-05]: ../requirements/requirements.md#nfr-05
[NFR-93]: ../requirements/requirements.md#nfr-93

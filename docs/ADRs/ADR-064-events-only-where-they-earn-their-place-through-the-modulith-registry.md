# ADR-064: Events exist only where they earn their place, and Spring Modulith's registry delivers them

- **Status:** Accepted; extended by ADR-129; amended by ADR-101, ADR-103, ADR-104, ADR-107 and ADR-115
- **Date:** 2026-11-14

## Context

Decided on 2026-11-14: the domain layer design (DL) is
settled, and its event rules get their own ADR. No "ADR pending" marker covers this decision.
[ADR-040] deferred it, with two points: when an event earns its place, and the event publication
registry. Two of its rejections lean on this ADR. It turned down a
Maven multi-module build and ArchUnit alone because neither has an event registry. Both reasons stand
only if the registry is kept, and this ADR keeps it.

The forces:

- **One transaction changes one aggregate** ([ADR-063]). The next change must follow reliably, even
  if the process stops between the two commits.
- **The module graph points one way** ([DL §2.3][DL-23]). Planning cannot call Live Operations,
  Messaging or Reporting, so they must hear about a plan without Planning knowing them.
- **Every event is a contract.** A module in another service could depend on it later.
- **Screens are not other modules.** A missed screen update costs one refetch.
- **The deployment is one VM** ([NFR-14]). [TS §1][TS-1] rules out a message broker for a close call,
  and PostgreSQL already holds the solve queue, the message outbox and the event registry
  ([TS §23][TS-23]).

## Decision

**An event is used only where one of three reasons holds, and a direct call or doing nothing would
be worse** ([DL §7.1][DL-71]):

1. **Another module must react, and the emitter must not know it exists.**
2. **Another aggregate must change after this one, reliably.** An event is stored with the change and
   delivered again until its listener succeeds. A call after commit cannot promise that.
3. **Several paths produce the same fact.** A job ends in its worker, in the watchdog or at startup
   recovery. One event means the plan's reaction is written once.

Two things are never reasons on their own: updating a screen, and calling a module that this one may
call anyway. A screen is told through the `LiveUpdates` port after the commit ([ADR-026]).

**The catalogue** ([DL §7.2][DL-72]) has eleven rows, twelve events: `SolveSucceeded` and
`SolveFailed`, `PlanPublished`, `PlanRevised`, `RouteHandedOver`, `OrderCancelled`, `OrderRescheduled`,
`RouteStarted`, `OutcomeRecorded`, `OutcomeCorrected`, `RatingReceived` and `SessionsRevoked`. Each row
names who raises it, who hears it and which of the three reasons applies. A new event needs a row there.

**Left out** ([DL §7.4][DL-74]): a proposal is ready, a late outcome was reconciled, a route finished,
a projected arrival changed, an order's details changed, a user's roles changed and a stop was arrived
at. On 2026-11-14 an eighth was added: editing an order on a published plan. The command's answer
says at once that the order is on a published route, and the order's last-change time is enough for the
live view to mark the stop "changed since publishing", so nothing needs an event.

**Shape** ([DL §7.3][DL-73]):

- An event is a past-tense, immutable record. It lives in its module's `events` package, which is a
  Spring Modulith named interface: `<module>/events/`.
- **It carries only IDs, shared-kernel values and small enums defined in its own `events`
  package,** such as a revision cause or an outcome kind (decided 2026-11-14). It has its own ID,
  `occurredAt` and the aggregate's ID and version. It is never a copy of the aggregate's state. A listener that needs more asks the
  owner.
- **The domain may import its own module's `events` package.** Aggregates raise events, so the
  purity rule of [ADR-040] allows it.
- **The package is published language.** A field is added and never renamed or removed.
- **Aggregates raise events, not use cases.** An event says the aggregate's state changed.

**Delivery** is Spring Modulith's JDBC event publication registry ([TS §6][TS-6], Event publication row):

- The registry stores each publication in the same transaction as the change and delivers it after
  commit, at least once.
- Modulith 2.x does not retry a failed publication unless it is configured to. DispatchFlow turns on
  republication at restart, a 2-minute staleness limit, and a job that resubmits failed publications
  every minute, at most 5 times each. A publication still failing after 5 resubmissions alerts the
  team with the listener's name ([PL §11.3][PL-113], [TS §16][TS-16]).
- Every reaction is idempotent, by nature or by recording `(listener, event ID)` first. Each
  reaction's test publishes its event twice ([PL §11.2][PL-112]).
- A listener that loses a version race retries three times, then the registry resubmits it.

## Consequences

- **Easy:** a downstream module reacts to a plan without Planning knowing it, and the build fails on
  a dependency in the wrong direction.
- **Easy:** a lost reaction is retried without hand-written code, and a crash between commit and
  delivery does not lose it.
- **Easy:** splitting a module into a service later lets Modulith externalize the same events to a
  broker, with no domain code changed ([DL §2.3][DL-23]).
- **Hard:** a change that spans two aggregates is eventually consistent. Between the two commits the
  second aggregate still shows the old state.
- **Hard:** every listener needs an idempotency argument and a test that delivers its event twice.
- **Hard:** the `events` package can only grow. A mistake in a field name stays.
- **Cost:** the registry table, a resubmission job, a retention job that purges completed publications
  older than 7 days, and the staleness settings ([PL §11.3][PL-113]).
- **Risk:** the registry's settings come from the implementation plan. They have not been run, or
  checked against Modulith 2.1's documentation, yet.

## Alternatives considered

- **A direct call after commit.** Rejected for reason 2: it cannot promise that the second change
  happens if the process stops in between.
- **A hand-written outbox.** Rejected: it is the same idea, with code to write and test ourselves
  ([TS §6][TS-6]).
- **A message broker,** such as RabbitMQ or Kafka. Rejected: one VM, and PostgreSQL already covers it
  ([TS §23][TS-23], [NFR-14]).
- **An event for every state change,** with screens as listeners. Rejected: each event is a contract
  another module depends on, and seven candidates did no better than a call or nothing
  ([DL §16][DL-16]). The eighth, above, did no better either.
- **An event that carries a copy of the aggregate's state.** Rejected: [DL §7.3][DL-73] never copies
  state into an event. A listener that needs more asks the owner.
- **Use cases raise the events.** Rejected: an event says the aggregate's state changed, so only the
  aggregate's method can raise it ([DL §7.3][DL-73]).

## Revisit when

- A module is split into its own service ([NFR-14] lifted). Externalize its events through Modulith's
  broker support.
- A publication is lost, or reaches its sixth failure on one listener, in a test or in the pilot. The
  registry's configuration then needs a second look, and a hand-written outbox is compared.
- A listener needs data that the rule above keeps out of an event, and asking the owner is too slow or
  too coupled. Allow a named exception.
- A second core instance is planned. Republishing at startup is safe only with one instance.

[ADR-026]: ADR-026-server-sent-events-and-push-for-live-updates.md
[ADR-040]: ADR-040-core-is-a-spring-modulith-monolith-with-one-module-per-context.md
[ADR-063]: ADR-063-a-transaction-changes-one-aggregate-and-every-rule-has-one-owner.md
[DL-16]: ../architecture/domain_layer/domain_layer_design.md#16-why-it-looks-like-this
[DL-23]: ../architecture/domain_layer/domain_layer_design.md#23-rules-between-contexts
[DL-71]: ../architecture/domain_layer/domain_layer_design.md#71-when-an-event-earns-its-place
[DL-72]: ../architecture/domain_layer/domain_layer_design.md#72-the-events
[DL-73]: ../architecture/domain_layer/domain_layer_design.md#73-shape-and-delivery
[DL-74]: ../architecture/domain_layer/domain_layer_design.md#74-considered-and-left-out
[NFR-14]: ../requirements/requirements.md#nfr-14
[PL-112]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#112-rules-every-use-case-follows
[PL-113]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#113-event-publication-making-retries-happen
[TS-1]: ../architecture/tech_stack.md#1-what-decides-close-calls
[TS-6]: ../architecture/tech_stack.md#6-core-service-java
[TS-16]: ../architecture/tech_stack.md#16-observability-and-alerting
[TS-23]: ../architecture/tech_stack.md#23-alternatives-rejected-across-the-stack

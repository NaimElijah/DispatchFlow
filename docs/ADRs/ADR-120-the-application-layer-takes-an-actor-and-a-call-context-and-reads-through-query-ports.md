# ADR-120: The application layer takes an Actor and a CallContext, keeps its shared ports in shared.application, and reads through query ports

- **Status:** Accepted
- **Date:** 2026-11-15

## Context

[ADR-119] declares the whole application layer ahead of its milestones. Declaring it means fixing what
[TS §6.2][TS-62] and [DL §13][DL-13] leave open.

**How a use case learns who is acting.**
- [DL §2.1][DL-21] names an `Actor` value, with the account ID and the roles.
- Every changing use case writes an audit record ([NFR-93]) that also needs:
  - the session;
  - the client's declared app;
  - the operation;
  - the trace ID.
- Audit may depend on nothing but the kernel. Access depends on Audit.

**Where the ports every module needs live.** `Clock`, `IdGenerator`, the listeners' processed-event
record, and the not-found and stale-version errors are the same in all eight modules. Three earlier
decisions bear on them:
- [DL §2.3][DL-23], rule 5: a change to the shared kernel needs every module owner's agreement.
- [ADR-097]: declined a second shared package for the domain's building blocks.
- [ADR-118], point 9: keeps the kernel flat.

**How screens read.** [TS §2.1][TS-21], rule 5, lets reads bypass aggregates. But an adapter reaches
another only through a port. So a screen's read needs a port that the application owns.

**What the docs leave open.** The research for [#81] found about 30 gaps between the requirements, the
domain document, the tech stack and the ADRs. Some of them decide where a class goes.

On 2026-11-15 the owner, Naim Elijah, chose the shared package and the read side recorded here. He
confirmed the shared package after being told that it amends ADR-118.

## Decision

1. **The caller.**
   - Audit's `api` holds `CallContext`: the user, the session, the client, the operation and the trace
     ID, each optional where a call may lack it. `CallContext.serverStep` builds one for a job, a
     listener or a CLI step.
   - Access's `api` holds `Actor`: the account, its roles, and the call's `CallContext`.
   - **Signatures:**
     - a web-called method takes `Actor` first;
     - a job or CLI method takes a `CallContext`;
     - a listener method takes the event record, then a `CallContext` built by `serverStep`.
   - Every listener or job that changes an aggregate writes its audit record with that context,
     because [ADR-110] audits server steps too.
   - `AuditLog` takes a `CallContext` and never an `Actor`, so Access still depends only on Audit
     ([ADR-040]).
2. **The shared application ports are in `com.dispatchflow.shared.application`:** `Clock`,
   `IdGenerator`, `ProcessedEvents`, `NotFound` and `StaleVersion`.
   - This amends [ADR-118] point 9 narrowly: the kernel's domain types stay flat, and the application
     ports have their own subpackage.
   - ADR-097's declined package was for domain building blocks, not ports.
   - The owner's yes stands as the module owners' agreement that DL §2.3, rule 5 asks for.
   - M0 exposes the package to Spring Modulith as a named interface.
3. **The domain can't reach those ports.** An ArchUnit rule bans any class in a module's `domain` or
   `events`, and any in the kernel root, from depending on `shared.application`. Without it, the
   kernel's own allow-list would let a domain class import a `Clock` port.
4. **Module APIs carry no domain type.** An `api` or `spi` type uses only:
   - kernel types and JDK types;
   - its own package's records, enums and sealed interfaces;
   - Audit's `CallContext`.

   The calling module's application layer translates the answer into its own domain values
   ([DL §2.3][DL-23], rule 1).
   - Attempts are the kernel's `Attempt`, and a revision is an `int`.
   - A planned time that may fall past the date's end is an `Instant` ([ADR-112]).
5. **Names:**
   - an `api` interface `X` is implemented by `XService` in its own module;
   - another module's `spi` is implemented by `XService` in the implementing module, or by
     `FleetUsageService` and `CustomerUsageService` for the two ports named `RecordUsage`;
   - a command is `VerbNoun` with one method named for its verb;
   - a query class is `XQueries`, one per screen family.
6. **Reads.**
   - A screen's read is a query class in `service`, which reads a port `<Module>Views` in `port.out`.
   - The persistence adapter implements the port.
   - Each query returns a view record whose fields are what its requirement rows ask the screen to
     show. The milestone's client contract may reshape it.
   - A data-scoped check, such as a driver seeing only their own route ([BR-41]), sits in the query
     class.
7. **Parameters and errors.**
   - A method that changes an existing aggregate ends with `Version expected` ([BR-76]).
   - Above five parameters, a method takes a nested `Command` record.
   - A missing record is `NotFound` (404), and a stale version `StaleVersion` (412). Both carry a
     kind and an ID for the problem's `params`.
8. **Placements where the docs leave the owner open** (for the owner's veto):
   - **P1:** Access owns push tokens. Sign-out and revocation remove them ([NFR-263]).
   - **P2:** Live Operations owns the phone's request-key record (`ReportReceipts`, [ADR-087]). The
     shared `ProcessedEvents` holds the listeners' records.
   - **P3:** with no solver named, a solve uses OR-Tools. `PlanningSettings` has no solver field.
   - **P4:** depot geocoding gets a port of Fleet's own at M7, because Fleet and Orders have no
     relationship.
   - **P5:** Reporting reads the baseline's inputs through Planning's `PlanFigures`.
   - **P6:** the driver's day is Live Operations' query, and the route diff is Planning's.
   - **P7:** the settings page is three use cases, one per settings aggregate.
   - **P8:** `SessionContacts` returns contact times, as [DL §3.3][DL-33] and [ADR-107] have it.
   - **P9:** both `RecordUsage` ports keep their name.
   - **P10:** `ReceiveReply` sends the thank-you, the reminder and the opt-out confirmation.
   - **P11:** Could-priority rows get no class until they are scheduled.

   Shapes the build settled:
   - Orders' `api` offers `ServiceAreaCheck`, because [ADR-050] has the core compute each
     re-optimization position's in-service-area flag.
   - Orders' `RecordUsage` asks about a customer's orders, not the customer, so Planning keeps no
     customer of its own.
   - `OrderPlacement` tells an unpublished plan from a published one, for [FR-396].
   - `PublishedRoutes` and `RouteMembership` answer from the stored revisions: a stop's route now,
     a completed or cancelled plan's routes, what a revision did to its stops, and an order's stop
     after Planning took it off ([ADR-059]).
   - `PushNotifier` reaches staff as well as drivers ([ADR-083]).
9. **Left out until a design exists:**
   - customer import, historical manual routes, order history, and ratings in the daily summary (which
     needs a Reporting → Messaging arrow);
   - the pilot-day, personal-data and company exports;
   - SMS delivery reports.
10. **The architecture rules** (ArchUnit, each with negative fixtures):
    - the application layer's allowed imports, with only `@Transactional` from Spring ([TS §2.2][TS-2-2]);
    - DL §2.1's module graph, and no other arrow;
    - no domain type in an `api` or `spi`;
    - exactly one implementation per `api` interface in its module, and per `spi` interface outside
      it;
    - `@NullMarked` on every application package;
    - decision 3.

## Consequences

- **Easy:**
  - Every use case has one way to learn the caller and write its audit record.
  - A new cross-module call that breaks DL §2.1 fails the build.
  - A screen read has one place.
- **Hard:**
  - The kernel gains a second package, under the same agreement rule.
  - A view record shaped from the requirement rows may change when the contract arrives.
- **Cost:**
  - Five shared types.
  - One translation step in every caller of another module's `api`.
- **Risk:**
  - A placement in decision 8 turns out wrong at its milestone. Each one is listed so the milestone
    can reopen it.
  - Spring Modulith must expose `shared.application`. M0 checks it with `verify()`.

## Alternatives considered

- **Put the shared ports in the kernel root.** Rejected by the owner on 2026-11-15:
  - ports would sit among the domain's building blocks;
  - keeping a domain away from `Clock` would need a rule that names each class.
- **A copy of each shared port in every module.** Rejected: eight copies that must stay identical.
- **An `Actor` without the call context, and a second parameter for audit.** Rejected: every web-called
  method would take two context parameters.
- **Domain types in module APIs.** Rejected: [DL §2.3][DL-23], rule 1.
- **Screens read jOOQ from the web adapter directly.** Rejected: an adapter reaches another only through
  a port ([TS §2.1][TS-21]), and the data-scoped checks need a use case.

## Revisit when

- M0 adopts Spring Modulith, and `verify()` can't expose `shared.application` as a named interface.
- The client contract reshapes most of a milestone's view records.
- A placement in decision 8 is reopened at its milestone.

[#81]: https://github.com/NaimElijah/DispatchFlow/issues/81
[ADR-040]: ADR-040-core-is-a-spring-modulith-monolith-with-one-module-per-context.md
[ADR-050]: ADR-050-re-optimization-conventions-live-only-in-the-python-problem-factory.md
[ADR-059]: ADR-059-plan-revisions-are-stored-as-rows.md
[ADR-083]: ADR-083-admins-and-dispatchers-get-alerts-by-stream-and-push-in-the-phone-app.md
[ADR-087]: ADR-087-driver-reports-sync-through-an-idempotent-outbox.md
[ADR-097]: ADR-097-the-shared-kernel-holds-the-domains-building-blocks.md
[ADR-107]: ADR-107-live-operations-alerts-once-per-key-corrects-a-put-back-by-event-and-treats-a-reschedule-as-a-cancellation.md
[ADR-110]: ADR-110-audit-records-hold-only-ids-codes-enums-numbers-and-instants-record-refusals-and-are-kept-a-year.md
[ADR-112]: ADR-112-a-re-optimization-keeps-every-order-and-route-a-solve-that-breaks-a-lock-fails-and-publishing-clears-locks.md
[ADR-118]: ADR-118-a-domain-package-above-30-files-splits-by-aggregate.md
[ADR-119]: ADR-119-the-application-layer-is-declared-ahead-as-signatures-whose-bodies-throw.md
[BR-41]: ../requirements/requirements.md#access
[BR-76]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[FR-396]: ../requirements/requirements.md#customers-and-orders
[NFR-93]: ../requirements/requirements.md#nfr-93
[NFR-263]: ../requirements/requirements.md#nfr-263
[DL-13]: ../architecture/domain_layer/domain_layer_design.md#13-inside-each-module-hexagon
[DL-21]: ../architecture/domain_layer/domain_layer_design.md#21-module-graph
[DL-23]: ../architecture/domain_layer/domain_layer_design.md#23-rules-between-contexts
[DL-33]: ../architecture/domain_layer/domain_layer_design.md#33-value-objects
[TS-21]: ../architecture/tech_stack.md#21-the-dependency-rule
[TS-2-2]: ../architecture/tech_stack.md#22-enforcement
[TS-62]: ../architecture/tech_stack.md#62-application-layer

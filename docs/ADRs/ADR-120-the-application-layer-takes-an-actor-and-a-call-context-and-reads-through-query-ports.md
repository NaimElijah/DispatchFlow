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
   - Every listener or job that changes an aggregate writes its audit record with the server step's
     `CallContext`, because [ADR-110] audits server steps too.
   - A refusal that happens before any use case runs is written by the interceptor or limiter that
     refuses: [ADR-068]'s role check, [ADR-071]'s limits, a 401. It writes through `AuditLog.refused`,
     in its own transaction.
   - `Actor` refuses a `CallContext` that names another user or no session.
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
   - A data-scoped check, such as a driver seeing only their own route ([BR-02]), sits in the query
     class.
7. **Parameters and errors.**
   - **Signature order:** `Actor` first, then the target's ID, then the values. A `Command` record
     holds the edited values when there are many (above five parameters). `Version expected` comes
     last, always outside the `Command`, in a method that changes an existing aggregate ([BR-76]).
   - **Missing records:**
     - a method answers `Optional`, or an empty list, when absence is an expected answer;
     - it throws `NotFound` when the ID should exist;
     - a caller that gets `NotFound` from another module's `api` translates it, because it isn't a 404
       about the caller's resource.
   - **Errors:** application errors are unchecked exceptions with a stable `code()`: `NotFound` (404),
     `StaleVersion` (412), `CompanyNotSetUp` (409), `SignInRefused` (401), `IdempotencyKeyReused`
     (409), `ProofIdTaken` (409), `ProofImageTooLarge` (422) and `GeocoderUnavailable`. `NotFound` and
     `StaleVersion` carry a kind and an ID for the problem's `params`.
   - **Retries:** a version conflict in a listener or job is retried by its adapter, up to three times
     ([DL §11][DL-11]). A use case doesn't retry; it declares `StaleVersion`.
8. **Placements where the docs leave the owner open** (for the owner's veto):
   - **P1:** Access owns push tokens. Sign-out and revocation remove them ([NFR-263]).
   - **P2:** Live Operations owns the phone's request-key record (`ReportReceipts`, [ADR-087]). The
     shared `ProcessedEvents` holds the listeners' records.
   - **P3:** with no solver named, `RequestSolve` and `RequestReoptimization` use the planning
     settings' default for their kind, as [ADR-004] decides. `PlanningSettings` gains that field at M4,
     and `SolversView` shows both defaults.
   - **P4:** depot geocoding gets a port of Fleet's own at M7, because Fleet and Orders have no
     relationship.
   - **P5:** Reporting reads every other module only through the read-only views they publish
     ([ADR-041], [ADR-109]), through its own `ReportingViews` port. It calls no other module's Java
     API. Planning's `PlanFigures` and Live Operations' `StopResults` were removed.
   - **P6:** the driver's day is Live Operations' query, and the route diff is Planning's.
   - **P7:** the settings page is three use cases, one per settings aggregate.
   - **P8:** `SessionContacts` returns contact times, as [DL §3.3][DL-33] and [ADR-107] have it.
   - **P9:** both `RecordUsage` ports keep their name.
   - **P10:** `ReceiveReply` sends the thank-you, the reminder and the opt-out confirmation.
   - **P11:** Could-priority rows get no class until they are scheduled.
   - **P12:** admin-only changes outside Access ask Access's `StepUp` API for a recent password
     ([NFR-214], [ADR-068]). Access's own use cases call `Session.requireFreshStepUp` directly.
   - **P13:** [NFR-215]'s daily recovery-email cap is counted on the send path (`RecoveryEmailCap`), so
     an unknown address can't spend it. [FR-172]'s per-account limit stays the web adapter's Bucket4j
     limit ([ADR-071]).
   - **P14:** a proof is stored under its uploader and kind, and a delivered report may name only the
     driver's own proof of the stated kind ([BR-02], [FR-367], [FR-368]). Its pixel size is checked
     before decoding ([NFR-205]).
   - **P15:** sign-in answers one `SignInRefused` after the same hash work, with a decoy for an unknown
     address or an account with no password. The specific code goes only to the audit record
     ([NFR-145], [NFR-225]).

   Shapes the build settled:
   - Orders' `api` offers `ServiceAreaCheck`, because [ADR-050] has the core compute each
     re-optimization position's in-service-area flag.
   - Orders' `RecordUsage` asks about a customer's orders, not the customer, so Planning keeps no
     customer of its own.
   - `OrderPlacement` tells an unpublished plan from a published one, for [FR-396].
   - `PublishedRoutes` and `RouteMembership` answer from the stored revisions: a stop's route now,
     a completed or cancelled plan's routes, what a revision did to its stops, and an order's stop
     after Planning took it off ([ADR-059]).
   - `PushNotifier` reaches staff as well as drivers ([ADR-083]), and every alert-raising use case also
     pushes to staff (`PushNotifier.alertStaff`, [FR-405]).
   - Live Operations also hears `RouteHandedOver`, to tell the driver a route left ([FR-71]).
   - Messaging reads Planning's `PublishedRoutes` and `RouteMembership`.
   - Live Operations reads Orders' `OrdersForDate` (windows and door flags for projections, [ADR-067])
     and Planning's `PlanningSettingsFacts` (the departure allowance).
   - Planning's `ExecutionFacts` is asked with the plan's own routes and stops, so Live Operations
     never asks Planning back.
   - Orders' `RecordUsage` passes every non-anonymized order of the customer, and Planning decides
     "not yet completed".
   - A re-optimization adds rows only (`TravelTimes.from`, `TravelRows`, [ADR-052]).
   - `Company.profile()` answers `Optional`, and its callers refuse with `CompanyNotSetUp`.
9. **Left out until a design exists:**
   - customer import, historical manual routes, order history, and ratings in the daily summary (which
     needs a Reporting → Messaging arrow);
   - the pilot-day, personal-data and company exports;
   - SMS delivery reports.
10. **The architecture rules** (ArchUnit, each with negative fixtures):
    1. The application layer's allowed imports ([TS §2.2][TS-2-2]): the JDK, JSpecify, the kernel, its
       own module's domain, events and application, other modules' `api`, `spi` and `events`, and from
       Spring only `@Transactional`. It forbids the module's own `adapter`, and allows neither
       libphonenumber nor JTS. Spring Modulith's annotations sit on a `package-info` only.
    2. DL §2.1's module graph, and no other arrow.
    3. No domain type in an `api` or `spi`. The rule enforces only this ban; the allow-list in decision
       4 is the convention.
    4. Exactly one implementation per `api` interface in its module. Each `spi` interface has one
       implementation, in the module that DL §2.1's dashed arrow names (`SPI_IMPLEMENTERS`). Sealed
       interfaces are skipped.
    5. `@NullMarked` on every application package.
    6. Decision 3.
    7. No cycle between a module's application packages, and the `service` and `port.out` roots import
       none of their own subpackages ([ADR-118]'s rules, carried to the application).
11. **The solver port returns a `SearchResult`.** This amends [ADR-044]'s return type, which the owner
    decided on 2026-11-15:
    - the optimizer's `Solver.solve` returns `SearchResult(solution, reached)`, not a bare `Solution`.
      `reached` is the iterations or solutions the search reached ([ADR-004], ADR-044's own Risk line,
      [NFR-13], [NFR-115]);
    - a solver failure becomes a `Failed` outcome in `Solve`, `Reoptimize` and `Baseline` ([ADR-004]).

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
[ADR-004]: ADR-004-pluggable-solver-port.md
[ADR-040]: ADR-040-core-is-a-spring-modulith-monolith-with-one-module-per-context.md
[ADR-041]: ADR-041-one-postgresql-schema-per-module-read-and-written-through-jooq.md
[ADR-044]: ADR-044-solver-port-takes-start-budget-and-seed-and-problem-judges.md
[ADR-050]: ADR-050-re-optimization-conventions-live-only-in-the-python-problem-factory.md
[ADR-052]: ADR-052-each-plan-stores-the-matrix-and-values-it-was-solved-with.md
[ADR-059]: ADR-059-plan-revisions-are-stored-as-rows.md
[ADR-067]: ADR-067-live-arrivals-propagate-delay-along-stored-legs-when-asked.md
[ADR-068]: ADR-068-every-web-handler-is-authorized-by-role-from-the-contract.md
[ADR-071]: ADR-071-api-rate-limits-in-process-with-bucket4j.md
[ADR-083]: ADR-083-admins-and-dispatchers-get-alerts-by-stream-and-push-in-the-phone-app.md
[ADR-087]: ADR-087-driver-reports-sync-through-an-idempotent-outbox.md
[ADR-097]: ADR-097-the-shared-kernel-holds-the-domains-building-blocks.md
[ADR-109]: ADR-109-reporting-computes-exact-on-time-shares-from-current-facts-and-keeps-no-personal-data.md
[ADR-107]: ADR-107-live-operations-alerts-once-per-key-corrects-a-put-back-by-event-and-treats-a-reschedule-as-a-cancellation.md
[ADR-110]: ADR-110-audit-records-hold-only-ids-codes-enums-numbers-and-instants-record-refusals-and-are-kept-a-year.md
[ADR-112]: ADR-112-a-re-optimization-keeps-every-order-and-route-a-solve-that-breaks-a-lock-fails-and-publishing-clears-locks.md
[ADR-118]: ADR-118-a-domain-package-above-30-files-splits-by-aggregate.md
[ADR-119]: ADR-119-the-application-layer-is-declared-ahead-as-signatures-whose-bodies-throw.md
[BR-02]: ../requirements/requirements.md#access
[BR-76]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[FR-71]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-172]: ../requirements/requirements.md#company-users-and-roles
[FR-367]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-368]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-396]: ../requirements/requirements.md#customers-and-orders
[FR-405]: ../requirements/requirements.md#live-operations-and-re-optimization
[NFR-13]: ../requirements/requirements.md#nfr-13
[NFR-93]: ../requirements/requirements.md#nfr-93
[NFR-115]: ../requirements/requirements.md#nfr-115
[NFR-145]: ../requirements/requirements.md#nfr-145
[NFR-205]: ../requirements/requirements.md#nfr-205
[NFR-214]: ../requirements/requirements.md#nfr-214
[NFR-215]: ../requirements/requirements.md#nfr-215
[NFR-225]: ../requirements/requirements.md#nfr-225
[NFR-263]: ../requirements/requirements.md#nfr-263
[DL-11]: ../architecture/domain_layer/domain_layer_design.md#11-concurrency
[DL-13]: ../architecture/domain_layer/domain_layer_design.md#13-inside-each-module-hexagon
[DL-21]: ../architecture/domain_layer/domain_layer_design.md#21-module-graph
[DL-23]: ../architecture/domain_layer/domain_layer_design.md#23-rules-between-contexts
[DL-33]: ../architecture/domain_layer/domain_layer_design.md#33-value-objects
[TS-21]: ../architecture/tech_stack.md#21-the-dependency-rule
[TS-2-2]: ../architecture/tech_stack.md#22-enforcement
[TS-62]: ../architecture/tech_stack.md#62-application-layer

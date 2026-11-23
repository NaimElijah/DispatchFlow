# ADR-129: Transactions start only in the service layer

- **Status:** Accepted
- **Date:** 2026-11-24

## Context

A check of the core's persistence after [ADR-126], [ADR-127] and [ADR-128] ([issue #255][ISSUE-255])
found that no rule holds a transaction to where [ADR-063] and [TS §6.2][TS-62] put it: one use case,
its outside calls first, then one transaction that changes one existing aggregate and writes its
audit record ([NFR-23], [NFR-93]).

- **The persistence adapters open their own.** All 24 carry a class-level `@Transactional`, whose
  default propagation joins a caller's transaction or starts one. Five methods name `MANDATORY`. So
  an adapter called with no transaction commits on its own. A use case wired without the annotation
  would commit an order, then write its audit record in a second transaction, and a failure between
  the two keeps the order with no record. Nothing calls an adapter outside the integration tests
  yet, because nothing wires the use cases ([ADR-119], decision 4).
- **Spring adds a transaction through a proxy.** In Spring Boot's default class-based mode, the
  proxy is a generated subclass. 188 of the 195 classes in the services' packages are `final`.
  Spring can't subclass a `final` class, so the context fails to start. A `final` method is skipped
  with only a log line, and its call runs with no transaction.
- **A call to the class's own method goes around the proxy.** It runs with no transaction and no
  error. TS §6.2's "outside calls first; then one `@Transactional` method" is that call when both
  steps sit in one class, as `RequestSolve`'s skeleton has them.
- **Listeners retry from outside the transaction.** A listener that loses a version race retries
  three times ([ADR-064]), each try in a new transaction, because a conflict leaves the transaction
  rollback-only ([ADR-126], [DL §11][DL-11]). Spring Modulith's `@ApplicationModuleListener` opens a
  `REQUIRES_NEW` transaction around the listener itself. A retry inside it would run in the
  transaction the conflict already doomed.
- **Some services run each item in a transaction of its own,** so that one stale save doesn't hold
  back the rest: the retention jobs, `DropOldLinks`, `AnonymizeDeactivatedAccounts` and
  `AnonymizeRetiredDrivers`. One transaction around the whole loop breaks that.

On 2026-11-24 the owner, Naim Elijah, decided where transactions start, and how, for the team
([issue #257][ISSUE-257]). The decisions are final.

## Decision

1. **Only the service layer carries `@Transactional`.**
   - In production code, only a class or method in a module's `application.service` packages carries
     it, directly or through an annotation that carries it.
   - No adapter, port, `api` or `spi` interface, web handler, job or listener carries it.
2. **Every service carries it, now.**
   - The annotation sits on the class.
   - The skeletons carry it too: this is the one Spring annotation a skeleton has. A skeleton still
     registers no bean, so no throwing body is reachable.
   - A service is a public class in `application.service` with a public constructor. A value, an
     exception or a sealed result type in those packages isn't one.
   - A transactional class is not `final` and not a record, and declares no `final` method, so that
     Spring can subclass it.
3. **A use case with outside calls, or with one transaction per item, is two classes.**
   - **The use case** keeps its name and its `api` implementation, and carries no `@Transactional`.
     It makes its outside calls before the transaction and its notifications after it. Where there
     is one transaction per item, it loops over the items.
   - **`<UseCase>Change`**, in the same package, is `@Transactional` and holds one transaction's
     work: load, command, save, audit record. The use case calls it through a field, so the call
     goes through the proxy.
   - **The outside-call ports** are `RouteOptimizer`, `FeasibilityChecker`, `TravelTimes`,
     `RouteShapes`, `Geocoder`, `RoadSnap`, `SmsGateway`, `EmailSender`, `PushNotifier`,
     `LiveUpdates`, `BaselinePlanner` and `ProofStore`. A transactional class holds none of them, so
     no database transaction waits on HTTP or disk ([ADR-063]).
   - **The use case never touches the database itself.** It holds its outside-call ports, its
     `Change`, and only ports that store nothing, such as `Clock` and `IdGenerator`.
     - What it must read before an outside call, and the items it loops over, come from a method of
       its `Change` that is `@Transactional(readOnly = true)`. That method returns a value, never
       an entity.
     - The write that follows loads again. Where the aggregate has a version, it takes the version
       the read returned and refuses with `StaleVersion` if it moved ([DL §6.3][DL-63]).
     - Another module's `api` or `spi` is in process, not an outside call, so the `Change` calls it.
     - These points were settled while the classes were split, so that one rule holds for every
       module.
   - **Four service packages split under [ADR-125].** The `Change` classes took each past 20
     files, so each moved a group by concept:
     - `access.application.service.account.state`;
     - `liveops.application.service.delivery.dispatcher`;
     - `messaging.application.service.message`;
     - `planning.application.service.plan.reaction`.
4. **A service that changes nothing is `@Transactional(readOnly = true)`.**
   - Hibernate then skips its dirty checking and flush.
   - PostgreSQL refuses any write the service makes by mistake.
   - A service that holds `AuditLog` changes something, so it is never read-only.
5. **A service names another propagation only where an ADR decides it.** The one so far is the
   record of a refused attempt, written in its own transaction ([ADR-110], point 1), which audit
   builds as `REQUIRES_NEW`.
6. **The persistence adapters carry no `@Transactional`.**
   - **A method that takes a lock that lasts only as long as its transaction** checks that one is
     open, and throws `IllegalTransactionStateException` if not. A lock taken outside a transaction
     is released at once, and the check it guards no longer protects anything. These methods are:
     - the five reads under lock: `activeAdminsUnderLock`, both `capacityUnderLock`, `claimNext` and
       `capOn`;
     - the three writes that lock first: `countAnswer`, `addUnlessKeyTaken` and the plan's `add`.
   - **Every other method relies on the service's transaction.**
   - `TransactionRules` finds each method that takes such a lock by its query's SQL, and checks that
     it calls the check.
7. **Event listeners have no transaction of their own.**
   - A listener is `@Async` and `@TransactionalEventListener`, never `@ApplicationModuleListener`.
   - The service it calls opens each transaction, so each of the three retries gets a fresh one.
   - Spring Modulith's registry tracks every listener annotated, directly or through another
     annotation, with `@TransactionalEventListener`, as its reference documentation for 2.x says.
     That is to be confirmed against the version the build adopts.
8. **`TransactionRules` checks decisions 1 to 4 and 6 in six ArchUnit rules,** each with a
   deliberately wrong fixture.

**What this amends and extends.**

- **[ADR-119]:** decision 4's skeleton carries `@Transactional` and no other Spring annotation
  (2). It is amended.
- **[ADR-063]:** a use case whose work around its transaction includes outside calls, or one
  transaction per item, is two classes (3). It is extended.
- **[ADR-064]:** a listener's form, so that its retries work (7). It is extended.
- **[ADR-125]:** four more service packages split by its rule (3). It is extended.
- **[TS §6.2][TS-62]** is corrected to match: its "one `@Transactional` method" is the transactional
  service or its `Change`.

## Consequences

- **Easy:**
  - A transaction starts in one place, and the build fails on a service without one.
  - The proxy traps can't arise: no transactional class is `final`, and no class calls its own
    transactional method, because the one class that needs two steps is two classes.
- **Easy:** a listener's and a web call's retries each run in a fresh transaction, as [ADR-064] and
  [ADR-121] decide.
- **Hard:** about 69 use cases are two classes, and both need tests when their bodies are built.
- **Hard:** an adapter called outside any service writes half. Spring Data's `save` commits on its
  own, then the flush finds no transaction and throws. Only code that bypasses the service layer can
  do that, and review checks for it.
- **Hard:** a new port that calls out must be added to the outside-call list, or a transactional
  class may hold it.
- **Cost:** every service file changes once now, and the teammates' branches that touch them need a
  merge.
- **Risk:** a service marked read-only that does change something fails loudly, with PostgreSQL's
  "cannot execute … in a read-only transaction".

## Alternatives considered

- **`MANDATORY` on every adapter,** so an adapter refuses to run outside a transaction. Rejected: the
  annotation stays in the service layer only, and a runtime check remains only where a lock needs it.
- **A transaction port** that the use case calls after its outside calls, built on
  `TransactionTemplate`. That would avoid the proxy, so `final` classes and calls to their own
  methods would work. Rejected: it changes TS §6.2's annotation for a lambda in every use case.
- **`@ApplicationModuleListener`.** Rejected: its own transaction makes an in-listener retry run in a
  transaction the conflict already doomed.
- **Annotating each use case when its body is built.** Rejected: until every service carries it, no
  rule can check that one is missing.
- **No runtime check in the lock-taking methods.** Rejected: outside a transaction, the lock is
  released when its statement ends. Two solve requests would then both pass the lane's capacity
  check.

## Revisit when

- A transaction must span an outside call.
- The core adopts AspectJ weaving or interface-based proxies, under which `final` classes and calls
  to the class's own methods are transactional too.
- The Spring Modulith version the build adopts doesn't track a plain `@TransactionalEventListener`.
- A service needs a propagation that no ADR decides.
- Something that is not a service needs to call an adapter.

[ISSUE-255]: https://github.com/NaimElijah/DispatchFlow/issues/255
[ISSUE-257]: https://github.com/NaimElijah/DispatchFlow/issues/257
[ADR-063]: ADR-063-a-transaction-changes-one-aggregate-and-every-rule-has-one-owner.md
[ADR-064]: ADR-064-events-only-where-they-earn-their-place-through-the-modulith-registry.md
[ADR-110]: ADR-110-audit-records-hold-only-ids-codes-enums-numbers-and-instants-record-refusals-and-are-kept-a-year.md
[ADR-119]: ADR-119-the-application-layer-is-declared-ahead-as-signatures-whose-bodies-throw.md
[ADR-125]: ADR-125-a-folder-above-20-files-splits-wherever-a-group-of-four-can-move.md
[ADR-121]: ADR-121-the-application-skeletons-review-settles-its-seams-conflicts-retention-and-scoring-inputs.md
[ADR-126]: ADR-126-jpa-replaces-jooq-and-the-domain-carries-its-mapping.md
[ADR-127]: ADR-127-how-the-aggregates-are-mapped-and-where-their-stored-state-is-tested.md
[ADR-128]: ADR-128-the-persistence-rules-the-mapping-settled-after-adr-127.md
[DL-11]: ../architecture/domain_layer/domain_layer_design.md#11-concurrency
[DL-63]: ../architecture/domain_layer/domain_layer_design.md#63-outside-calls-happen-before-the-transaction
[TS-62]: ../architecture/tech_stack.md#62-application-layer
[NFR-23]: ../requirements/requirements.md#nfr-23
[NFR-93]: ../requirements/requirements.md#nfr-93

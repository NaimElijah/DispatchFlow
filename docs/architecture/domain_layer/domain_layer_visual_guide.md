# DispatchFlow — Domain layer visual guide

**Status:** Describes the code at commit `6e3028f` on `dev`, 15 November 2026. The shared kernel's
`AggregateRoot` and `Version`, the save sequences and the load check that replaced `restore` and the snapshots
were brought up to date on 23 November 2026 for [ADR-126](../../ADRs/ADR-126-jpa-replaces-jooq-and-the-domain-carries-its-mapping.md) and [ADR-127](../../ADRs/ADR-127-how-the-aggregates-are-mapped-and-where-their-stored-state-is-tested.md), and the file and line counts
with them. The placeholder message was brought up to date on 24 November 2026 for [ADR-132](../../ADRs/ADR-132-unbuilt-bodies-say-to-be-implemented.md); the body counts are still those of `6e3028f`.

This guide is a map of the domain layer **as it is built today**, for a person learning the repository
before building a milestone. After reading it you should be able to find any aggregate, value, event or
error in the code, say which module owns it, and say how the pieces fit.

It decides nothing and states no rule. The decided model is
[`domain_layer_design.md`](domain_layer_design.md) (the "DL"), and **where this guide and the DL
disagree, the DL wins**: the guide is out of date, or the code is behind the design. Each section links
the DL section that owns its topic instead of repeating it. [`bounded-contexts.md`](../bounded-contexts.md)
and [`optimization-engine.md`](../optimization-engine.md) are the short prose versions of the same map.

**Paths.** Four prefixes keep the paths short:

| Prefix | Means |
|---|---|
| `core/` | `src/backend/core/src/main/java/com/dispatchflow/` |
| `core-test/` | `src/backend/core/src/test/java/com/dispatchflow/` |
| `optimizer/` | `src/backend/optimizer/src/dispatchflow_optimizer/` |
| `optimizer-test/` | `src/backend/optimizer/tests/` |

[ADR-123](../../ADRs/ADR-123-the-code-keeps-its-grouped-layout-and-three-components-take-new-names.md) keeps this tree and gave the optimizer its name: `src/backend/optimizer`, with the
package `dispatchflow_optimizer`. The prefixes above use those names.

**Reading the diagrams.** They use no colour. In a class diagram, `<<root>>` is an aggregate root,
`<<entity>>` an object held inside a root, `<<value>>` a value object, `<<fact>>` a value the use case
reads first and passes in, and `<<sealed>>` a sealed interface whose records are listed as its
members. A filled diamond means "holds inside the aggregate". A dotted arrow is a dependency that its
label names (raises, throws, requires, returns); between two aggregates it means "names the other by its
ID only" (DL [§3.1](domain_layer_design.md#31-aggregates)). Class diagrams show the fields and methods
that explain the shape, not every member.

**Contents**

1. [Where the domain sits](#1-where-the-domain-sits)
2. [The nine contexts on one map](#2-the-nine-contexts-on-one-map)
3. [The shared kernel](#3-the-shared-kernel)
4. [Anatomy of one aggregate](#4-anatomy-of-one-aggregate)
5. [Each context](#5-each-context)
6. [Lifecycles the DL does not draw](#6-lifecycles-the-dl-does-not-draw)
7. [Events across modules](#7-events-across-modules)
8. [The Python Optimization domain](#8-the-python-optimization-domain)
9. [How a use case will drive the domain](#9-how-a-use-case-will-drive-the-domain)
10. [Where the code is behind the design](#10-where-the-code-is-behind-the-design)
11. [Names that look alike](#11-names-that-look-alike)
12. [A reading path](#12-a-reading-path)

---

## 1. Where the domain sits

The domain is the only layer that is built. Each Java module and the Python optimizer has three layers on
paper, but only the inner one holds working code.

```mermaid
flowchart TB
  subgraph CORE["core (Java): one module per context"]
    CADP["adapters<br/>persistence only; the other packages are declared, empty"]
    CAPP["application: api, spi, port/out, service<br/>declared, not built"]
    CDOM["domain and events<br/>built and tested"]
    CKER["shared: the kernel<br/>and shared/application"]
    CADP --> CAPP
    CAPP --> CDOM
    CAPP --> CKER
    CDOM --> CKER
  end
  subgraph OPTIMIZER["optimizer (Python): the Optimization context"]
    SADP["adapters/inbound/http<br/>a liveness probe only"]
    SAPP["application<br/>declared, not built"]
    SDOM["domain<br/>built and tested"]
    SADP --> SAPP
    SAPP --> SDOM
  end
  CAPP -.->|"HTTP: no client yet"| SADP
```

An arrow means *depends on*. The sources for each box:

- **Built and tested.** Every module's `domain/` and `events/`, and the kernel `shared/`, under `core/`;
  `optimizer/domain/`. Their tests are in `core-test/` and `optimizer-test/domain/`.
- **Declared, not built** ([ADR-119](../../ADRs/ADR-119-the-application-layer-is-declared-ahead-as-signatures-whose-bodies-throw.md)).
  Each module's `application/` holds the use cases, ports and module APIs as signatures with Javadoc.
  A body that would do work throws `UnsupportedOperationException("To Be Implemented.")`. At this commit
  there are 384 such bodies in 274 files under `core/`, and 7 `NotImplementedError("To Be Implemented.")`
  in `optimizer/application/`. Two of the 384 are the blanking methods of Planning's solve-job adapter. `grep -rn "To Be Implemented." src/backend` lists the remaining work.
  The two kernel errors in `shared/application/` (`NotFound`, `StaleVersion`) are complete classes; its
  three ports (`Clock`, `IdGenerator`, `ProcessedEvents`) are interfaces. `Clock` and `IdGenerator`
  have implementations, `SystemClock` in `shared/adapter/out/clock` and `UuidV7Generator` in
  `shared/adapter/out/uuid`; only `ProcessedEvents` has none.
- **Wired.** One use case is wired: Audit's `AuditLogService`, which `AuditConfiguration` imports
  ([ADR-149](../../ADRs/ADR-149-the-use-case-records-its-refusal-after-the-rollback-and-the-audit-purge-counts-its-year-back-from-now.md)). Each module has a persistence adapter, in
  `core/<module>/adapter/out/persistence/`, which implements the module's repository and record
  ports over the module's PostgreSQL schema, and nothing calls it yet but Audit's, which
  `AuditLogService` appends through. Planning's `Plan` has its own
  in `core/planning/adapter/out/persistence/plan/`. The kernel has the shared one,
  `core/shared/adapter/out/persistence/`, and beside it the problem factory in `shared/adapter/in/web/`
  and the clock and ID adapters. Every other adapter package of the core is declared, empty
  ([ADR-130](../../ADRs/ADR-130-every-folder-is-declared-ahead-empty-and-documented.md)). The Spring annotations are `@SpringBootApplication` on the
  entry point `core/DispatchFlowApplication.java`; in `core/web/`, `@RestControllerAdvice` on the three
  error handlers, `@ControllerAdvice` on `BodySizeLimit`, `@RestController` on the error endpoint,
  `@Configuration` on `ServedJson` and `@Component` on the filter and the call wrapper's classes, while
  `Race`, `WebCodes`, `Traceparent` and `ContractBreaks` carry none; `@Configuration` on `SchedulingConfiguration`
  and on each module's root `<Module>Configuration`; `@Component` on the adapters, `SystemClock` and
  `UuidV7Generator` among them, and on `VersionedSave`; a class-level `@Transactional` on every service or its `<UseCase>Change` ([ADR-129](../../ADRs/ADR-129-transactions-start-only-in-the-service-layer.md)), and Spring Data's
  `@DomainEvents` and `@AfterDomainEventPublication` on `AggregateRoot`. The adapters also carry Jakarta
  Persistence's `@Converter`, and the kernel's values, every aggregate and `AggregateRoot` carry its
  mapping annotations ([ADR-126](../../ADRs/ADR-126-jpa-replaces-jooq-and-the-domain-carries-its-mapping.md), [ADR-127](../../ADRs/ADR-127-how-the-aggregates-are-mapped-and-where-their-stored-state-is-tested.md)). The optimizer's only adapter is the liveness probe in
  `optimizer/adapters/inbound/http/app.py`; its three solver packages are declared, empty.

**Dependency direction.** Dependencies point inwards, as DL [§13](domain_layer_design.md#13-inside-each-module-hexagon)
draws. The rules that keep the domain pure are DL [§2.3](domain_layer_design.md#23-rules-between-contexts)
and [§3.9](domain_layer_design.md#39-purity), and ArchUnit tests in `core-test/architecture/` fail the
build when one breaks (`DomainPurityTest`, `DomainShapeTest`, `DomainPackageTest`, `ApplicationTest`, `PersistenceTest`). A
scan of every `import` at this commit finds no file under any `domain/` or `events/` package that imports
another module. The only cross-module imports are in `application/` packages. In Python,
import-linter does the same job (`src/backend/optimizer/pyproject.toml`, section `[tool.importlinter]`).

---

## 2. The nine contexts on one map

Eight contexts are Java modules in `core`. The ninth, Optimization, is the Python optimizer. DL
[§1](domain_layer_design.md#1-subdomains-and-bounded-contexts) defines them; DL
[§2.1](domain_layer_design.md#21-module-graph) draws the module graph. The diagram below is the same
graph with each module's aggregates inside it, which the DL does not show. An arrow points upstream and
means *depends on*.

```mermaid
flowchart BT
  OPT["Optimization (Python)<br/>no aggregates<br/>Problem, ProblemFactory"]
  ACC["Access<br/>UserAccount, Session,<br/>CompanyProfile"]
  AUD["Audit<br/>no aggregate<br/>AuditRecord"]
  FL["Fleet<br/>FleetRegister, Driver,<br/>DayRoster"]
  CO["Customers & Orders<br/>Customer, Order,<br/>ImportBatch"]
  PL["Planning<br/>Plan, SolveJob,<br/>ReoptimizationProposal,<br/>PlanningSettings"]
  LO["Live Operations<br/>Delivery, RouteRun,<br/>Alert"]
  MS["Recipient Messaging<br/>StopMessages, RatingRequest,<br/>MessagingSettings"]
  RP["Reporting<br/>Baseline"]

  FL --> ACC
  PL --> FL
  PL --> CO
  LO --> PL
  LO --> CO
  LO --> FL
  MS --> LO
  MS --> PL
  MS --> CO
  RP --> LO
  RP --> PL
  RP --> CO
  PL -->|"HTTP"| OPT
  RP -->|"HTTP"| OPT
```

Not drawn, because every module has them: each depends on the kernel and on Audit's `AuditLog` port, and
each but Access depends on Access for the acting user (DL §2.1). The four dashed "implements a port the
upstream module owns" arrows are in the DL diagram.

The import scan agrees with the graph: every cross-module `import` in `core/` points along one of these
arrows (or to Access or Audit), and `audit/` imports no other module.

### The modules in numbers

| Module (`core/`) | Domain packages | Domain files | Events |
|---|---|---|---|
| `access` | root, `account`, `session`, `companyprofile` | 33 | 2 |
| `audit` | flat | 11 | none |
| `fleet` | root, `driver`, `register`, `roster` | 30 | 1 |
| `liveops` | root, `delivery`, `outcome`, `proof`, `alert`, `routerun` | 52 | 3 |
| `messaging` | root, `message`, `rating`, `settings` | 39 | 1 |
| `orders` | flat | 40 | 3 |
| `planning` | root, `plan`, `solution`, `drop`, `basis`, `revision`, `reconciliation`, `settings` | 85 | 6 |
| `reporting` | root, `baseline` | 20 | none |

"Domain files" counts `.java` files under `<module>/domain/`, not counting `package-info.java`.
A package above 20 files splits by aggregate, then by concept, wherever a group of 4 files can move
([ADR-118](../../ADRs/ADR-118-a-domain-package-above-30-files-splits-by-aggregate.md), [ADR-125](../../ADRs/ADR-125-a-folder-above-20-files-splits-wherever-a-group-of-four-can-move.md)):
the root `domain/` keeps the module's error type and what several packages use, and imports none of its
subpackages. `orders` has 40 files and stays flat, because its types form a cycle (`OrderStatus`,
`OrdersError` and `Order` name each other) and `ImportBatch` calls package-private methods of `Customer`
and `Order`; ADR-118 records it. The kernel and every `api/`, `spi/` and `events/` package stay flat
(ADR-118, ADR-125).

### Every aggregate root

There are 20 roots in Java (`extends AggregateRoot`). Audit has none, and the Python domain has none.

| Aggregate (module) | Key | Creation factory | Lines |
|---|---|---|---|
| `UserAccount` (Access) | `AccountId` | `open`, `openFirstAdmin` | 853 |
| `Session` (Access) | `SessionId` | `open` | 693 |
| `CompanyProfile` (Access) | none: one per company | `create` | 162 |
| `FleetRegister` (Fleet) | none: one per company | `empty` | 427 |
| `Driver` (Fleet) | `AccountId`, the driver's account | `register` | 428 |
| `DayRoster` (Fleet) | `DeliveryDate` | `open` | 506 |
| `Customer` (Orders) | `CustomerId` | `register` | 624 |
| `Order` (Orders) | `OrderId` | `place` | 689 |
| `ImportBatch` (Orders) | `ImportBatchId` | `preview` | 554 |
| `Plan` (Planning) | `PlanId` | `open` | 2,711 |
| `SolveJob` (Planning) | `JobId` | `queue`, package-private | 704 |
| `ReoptimizationProposal` (Planning) | `ProposalId` | `create`, package-private | 566 |
| `PlanningSettings` (Planning) | none: one per company | `startingValues` | 224 |
| `Delivery` (Live Operations) | `StopId` | `firstFact` | 811 |
| `RouteRun` (Live Operations) | `RouteId` | `start` | 427 |
| `Alert` (Live Operations) | `AlertId` | `raise` | 329 |
| `StopMessages` (Messaging) | `StopId` | `open` | 656 |
| `RatingRequest` (Messaging) | `RatingRequestId` | `open` | 439 |
| `MessagingSettings` (Messaging) | none: one per company | `create` | 146 |
| `Baseline` (Reporting) | `PlanId` | `compute` | 401 |

Lines are the line counts of the root's file at this commit. Where a root lives: `core/<module>/domain/`
plus the aggregate's package from the table above (`Plan.java` is in `core/planning/domain/plan/`).

---

## 3. The shared kernel

`core/shared/` holds what every module's domain is made of. DL [§2.3](domain_layer_design.md#23-rules-between-contexts)
rule 5 and [ADR-097](../../ADRs/ADR-097-the-shared-kernel-holds-the-domains-building-blocks.md) set what
may go in it: building blocks, typed IDs, ten shared values and one text rule, and nothing that holds one
context's rule. It is 38 files; its tests are the 24 files in `core-test/shared/`, which include `AsSaved`, the helper that saves an aggregate in memory (ADR-127 decision 2). Beside it, `core/shared/adapter/out/persistence/`
holds the persistence adapter every module shares ([ADR-126](../../ADRs/ADR-126-jpa-replaces-jooq-and-the-domain-carries-its-mapping.md)): the converters and instantiators that store the
kernel's values, and `VersionedSave`. That package is an adapter, not part of the kernel.

### The building blocks

```mermaid
classDiagram
  direction LR
  class AggregateRoot {
    <<abstract>>
    Long storedVersion
    List pendingEvents
    version() Version
    raise(type, aggregateId, build) E
    pendingEvents() List
    savedVersion() Optional~Version~
  }
  class Version {
    <<value>>
    long value
    of(value)
    next() Version
  }
  class DomainEvent {
    <<interface>>
    eventId() EventId
    occurredAt() Instant
  }
  class EventId {
    <<value>>
    String eventType
    UUID aggregateId
    Version version
    int ordinal
    parse(text)
    toText() String
  }
  class DomainError {
    <<interface>>
    code() String
    codeOf(type)
  }
  class SharedKernelError {
    <<sealed>>
  }
  class RuleViolation {
    <<exception>>
    DomainError error
  }
  class RuleViolations {
    <<exception>>
    DomainError summary
    List errors
    throwIfAny(summary, errors)
  }
  class StoredStateInvalid {
    <<exception>>
    String subject
    String detail
  }
  class FreshIds {
    of(ids)
    next() UUID
    remaining() int
  }
  class PersonalData {
    <<marker>>
  }
  class PlainText {
    <<value>>
    of(kind, raw)
    ofStored(kind, stored)
  }
  class TextKind {
    <<enum>>
    NAME
    ADDRESS
    NOTE
  }
  AggregateRoot --> Version : loaded at
  AggregateRoot ..> DomainEvent : records
  DomainEvent --> EventId
  EventId --> Version
  SharedKernelError ..|> DomainError
  RuleViolation --> DomainError : carries one
  RuleViolations --> DomainError : carries several
  PlainText --> TextKind
```

How the pieces work together:

- **`AggregateRoot`** (`core/shared/AggregateRoot.java`, 142 lines) is a `@MappedSuperclass` ([ADR-126](../../ADRs/ADR-126-jpa-replaces-jooq-and-the-domain-carries-its-mapping.md)). It holds
  the version the aggregate was loaded at and the events its methods raised. A never-saved aggregate has no
  version: `version()` throws `AssertionError` before its first save, and the first save stores 0. The
  field is `storedVersion`, a nullable `Long` that `@Version` maps to the `version` column; it has another
  name because Spring Data reads the version attribute by name to tell a new aggregate, and for a field named
  `version` it would call `version()`. `raise` builds the event's `EventId` from the type's simple name, the
  aggregate's UUID, the version this change will be saved at (0 for a never-saved aggregate, else the loaded
  version plus 1) and the event's position in the change. It throws `AssertionError` when the event is not
  exactly the type given or does not carry that ID, because that is a bug and not a refusal. Spring Data
  publishes the recorded events when the port's `save` runs, through the package-private `@DomainEvents`
  method `domainEvents()`, then calls `clearEvents()` (DL [§3.6](domain_layer_design.md#36-repositories)).
  `savedVersion()` returns the stored version, or empty before the first save, for a use case that holds an
  aggregate that may not be stored yet. An aggregate is saved once per transaction, with no flush between two
  of its changes; the shared `VersionedSave` fails with `AssertionError` otherwise.
- **`EventId`** has a text form, `<eventType>:<aggregateId>:<version>:<ordinal>`, which `toText` writes
  and `parse` reads. Optimistic locking lets one change commit per aggregate and version, so the four
  parts are unique without a random part.
- **`DomainError`** is the interface of every named refusal. `code()` derives the stable code from the
  record's simple name (`DriverInUse` becomes `DRIVER_IN_USE`), so a code can't drift from its type. Each
  module has a sealed error interface (`FleetError`, `OrdersError`, …); the kernel's own are
  `SharedKernelError` (DL [§3.8](domain_layer_design.md#38-domain-errors)).
- **`FreshIds`** is a supply of new UUIDs for a method that creates an unknown number of children, such
  as the routes and stops of a solve result. The use case fills it from `IdGenerator`, because the
  domain never makes an ID.
- **`PersonalData`** is a marker. A type that identifies or describes a person implements it and prints
  itself redacted; a rule in `DomainShapeRules`, run by `DomainShapeTest`, refuses it in an error or
  event field.

### What can go wrong, and what is thrown

| Situation | Thrown | Where it is defined |
|---|---|---|
| A business rule refuses | `RuleViolation` with one `DomainError`, or `RuleViolations` with all of them (publishing, an import) | `core/shared/` |
| Stored state can't become a valid object | `StoredStateInvalid`, not a `DomainError`: a damaged row is an incident | `core/shared/` |
| A use case is asked about a record that does not exist | `NotFound` (404) | `core/shared/application/` |
| A version moved since the caller read it | `StaleVersion` (412 for a version sent in `If-Match`, 409 for a version sent in the body ([ADR-140](../../ADRs/ADR-140-the-app-wide-web-package-sits-above-the-modules.md)), or 503 after three retries for a call that sent no version) | `core/shared/application/` |
| An aggregate's caller broke a contract | `AssertionError` in `AggregateRoot.raise`, `NoSuchElementException` in `FreshIds.next` | `core/shared/` |

### Typed IDs

Every ID is a record wrapping a UUID, so one kind can't be passed for another (DL
[§3.3](domain_layer_design.md#33-value-objects)). There are 14, each in `core/shared/`:

| ID | Names | Also the key of |
|---|---|---|
| `AccountId` | an account | `Driver`, whose ID is the account's |
| `SessionId` | a signed-in device | none |
| `CustomerId`, `OrderId`, `ImportBatchId` | the three Orders aggregates | none |
| `DepotId`, `VehicleId` | entities in `FleetRegister` | none |
| `PlanId` | a plan | `Baseline` |
| `RouteId` | a route, an entity in `Plan` | `RouteRun` |
| `StopId` | a stop, an entity in `Plan` | `Delivery`, `StopMessages` |
| `JobId`, `ProposalId` | the other two Planning aggregates | none |
| `AlertId` | an alert | none |
| `RatingRequestId` | a rating request | none |

Ids that only one module names stay in that module: `RefreshTokenId` in `core/access/domain/session/`,
`CredentialLinkId` in `core/access/domain/account/`, `OutgoingMessageId` in `core/messaging/domain/`,
`HeldReportId` in `core/liveops/domain/` and `ProofId` in `core/liveops/domain/proof/`.

### Shared values

DL [§3.3](domain_layer_design.md#33-value-objects) lists what each checks and the error it raises.

| Value | In `core/shared/` | Notes |
|---|---|---|
| `DeliveryDate` | `DeliveryDate.java` | The calendar day; `midnight()` and `lengthInSeconds()` know the day's 23, 24 or 25 hours |
| `IsraelDateTime` | `IsraelDateTime.java` | A local time in `Asia/Jerusalem`; holds the zone as `ZONE` |
| `DaySeconds` | `DaySeconds.java` | Seconds since the date's local midnight, within that day |
| `TimeWindow` | `TimeWindow.java` | Start before end, on one date; built by `TimeWindow.on` |
| `GeoPoint`, `Weight`, `Volume` | one file each | `Weight` and `Volume` share the checks in the package-private `Quantities` |
| `Priority`, `Attempt` | one file each | `Priority.DEFAULT` is 3; `Attempt.FIRST` and `next()` |
| `PhoneNumber` | `PhoneNumber.java` | `parse` for input, `ofStored` for a stored number |
| `PlainText`, `TextKind` | two files | The one rule for names, addresses and notes ([ADR-106](../../ADRs/ADR-106-free-text-is-normalized-and-capped-by-one-kernel-rule.md)) |

---

## 4. Anatomy of one aggregate

Most of the 20 roots follow one pattern. The clearest small example with an event is `Driver`
(`core/fleet/domain/driver/Driver.java`, 428 lines). `Alert` (`core/liveops/domain/alert/Alert.java`,
329 lines) is a small root that raises none.

```mermaid
classDiagram
  direction LR
  class AggregateRoot {
    <<abstract>>
  }
  class Driver {
    <<root>>
    AccountId id
    DriverStatus status
    DriverContact contact
    ShiftHours defaultShift
    VehicleId defaultVehicle
    WeeklyShifts weeklyShifts
    register(id, contact, defaultShift, defaultVehicle, account) Driver
    checkStored() "PostLoad"
    changeDetails(...)
    retire(uses, today, now)
    anonymize(now)
    anonymizeWithAccount(now)
    rosterDefaultOn(date, account) Optional
  }
  class DriverStatus {
    <<sealed>>
    Active
    Retired(Instant retiredAt)
    Anonymized(Optional retiredAt, Instant anonymizedAt)
  }
  class DriverRetired {
    <<event>>
    EventId eventId
    Instant occurredAt
    AccountId driver
  }
  class DriverInUse {
    <<error>>
    AccountId driver
    PlanId plan
    DeliveryDate date
  }
  class PlanUses {
    <<fact>>
  }
  AggregateRoot <|-- Driver
  Driver --> DriverStatus
  Driver ..> DriverRetired : raises in retire
  Driver ..> DriverInUse : throws in RuleViolation
  Driver ..> PlanUses : retire is given it
```

The pattern, with where `Driver` shows each part:

1. **A private constructor**, which `DomainShapeRules` requires, taking what the factory has:
   `private Driver(AccountId id, DriverContact contact, ...)`. A protected no-argument constructor
   beside it is for Hibernate, which sets the fields from the stored row. Nothing outside the class can
   build an instance except through a static factory: `DomainShapeRules` also refuses a call to a root's
   constructor from outside the root, and any class that extends a root.
2. **A creation factory checks every invariant of a new aggregate** and returns it with no version yet. Its
   name says what the business does: `register` here, `place`, `open`, `start`, `raise`, `compute` in the
   table of roots in §2. The new ID and `now` are parameters, never made inside (DL
   [§3.5](domain_layer_design.md#35-factories)). `Driver.register` also takes the account's `Standing`,
   a fact the driver can't see itself.
3. **Only Hibernate rebuilds a stored aggregate, and a `@PostLoad` check refuses a damaged one.**
   `checkStored` runs the invariants at rest and throws `StoredStateInvalid` for a damaged row (it checks
   that a driver has a name and phone exactly until anonymized). A command that changes
   state ends with the same check, which throws `AssertionError` and rolls the transaction back, so it
   can't save a state that would not load. There is no `restore` and no snapshot record: only Hibernate
   rebuilds an aggregate, and a domain test reaches a saved state through commands and `AsSaved`
   ([ADR-126](../../ADRs/ADR-126-jpa-replaces-jooq-and-the-domain-carries-its-mapping.md),
   [ADR-127](../../ADRs/ADR-127-how-the-aggregates-are-mapped-and-where-their-stored-state-is-tested.md)).
4. **The mapping is on the class.** `@Entity` and `@Table(schema = "fleet")`, annotated fields, a key that
   is the account's UUID, and a sealed state stored as a private kind (`StatusKind`) beside nullable
   columns, which `DriverStatus` is built from. The tables are in
   [data-model.md](../data-model.md#fleet-v4).
5. **Business methods are named for what the business does** and are called once per command (`retire`,
   not `setStatus`). Each checks the current state first, then throws `new RuleViolation(new FleetError.X(...))`,
   then changes state, then raises events. A refused call changes nothing and records nothing.
6. **State is a sealed interface of records** (`DriverStatus`). A `switch` over it must cover every case,
   as `Driver.anonymize` does (DL [§3.4](domain_layer_design.md#34-states-and-transitions)).
7. **Events are recorded by the root**, never by a use case: `raise(DriverRetired.class, id.value(), …)`.
   The record is in the module's `events/` package, `core/fleet/events/DriverRetired.java`.
8. **Facts the aggregate can't see arrive as parameters**: `PlanUses`, `today`, `now`, `Standing`. That is
   why no domain test needs a mock or a clock.

A call to `Driver.retire`, from a use case that is declared but not built (`RetireDriver`,
`core/fleet/application/service/driver/RetireDriver.java`):

```mermaid
sequenceDiagram
  participant UC as RetireDriver (declared)
  participant Use as RecordUsage (Fleet spi)
  participant Repo as DriverRepository
  participant D as Driver
  participant Root as AggregateRoot
  UC->>Use: the plans that use the driver
  Use-->>UC: PlanUses
  UC->>Repo: get(driver)
  Repo-->>UC: Driver, loaded whole
  UC->>UC: StaleVersion.requireCurrent(Driver, id, driver.version(), expected)
  UC->>D: retire(uses, today, now)
  D->>D: requireActive
  alt an uncancelled plan uses the driver
    D-->>UC: throws RuleViolation(DriverInUse)
  else the driver is free
    D->>D: status = Retired(now)
    D->>Root: raise(DriverRetired.class, ...)
    Root-->>D: event with EventId(DriverRetired, uuid, loaded version + 1, 0)
    D-->>UC: returns
    UC->>Repo: save(driver)
    Note over UC,Repo: The adapter saves through VersionedSave: Spring Data publishes DriverRetired in the same transaction, the flush checks the version again, and a save that left it unmoved forces one increment (DL 3.6)
  end
```

---

## 5. Each context

Each subsection gives the aggregates and what they hold, the package layout, the events, and where to
start reading. The invariants and their errors are in DL [§4](domain_layer_design.md#4-aggregates-context-by-context);
this guide does not repeat them.

### 5.1 Access

DL [§4.1](domain_layer_design.md#41-access). Accounts, roles, sessions and the company profile.

```mermaid
classDiagram
  direction LR
  class UserAccount {
    <<root>>
    AccountId id
    EmailAddress email
    RoleSet roles
    PasswordHash passwordHash
    AccountState state
    Language language
    AccountGeneration generation
    open(...)
    openFirstAdmin(...)
    changeRoles(...)
    deactivate(...)
    reactivate()
    changePassword(...)
    issueLink(...)
    setPasswordByLink(...)
    anonymize(now)
  }
  class CredentialLink {
    <<entity>>
    CredentialLinkId id
    LinkPurpose purpose
    TokenHash tokenHash
    Instant expiresAt
    LinkStatus status
  }
  class Session {
    <<root>>
    SessionId id
    AccountId account
    AccountGeneration accountGeneration
    SessionGeneration generation
    SessionState state
    open(...)
    exchange(...)
    confirmPassword(...)
    requireFreshStepUp(generation, now)
    recordContact(now)
    revoke(...)
  }
  class RefreshToken {
    <<entity>>
    RefreshTokenId id
    TokenHash tokenHash
    Instant issuedAt
    TokenState state
  }
  class CompanyProfile {
    <<root>>
    CompanyName name
    Language language
    DefaultWindow defaultWindow
    DefaultServiceTime defaultServiceTime
    create(...)
    rename(...)
    changeDefaultWindow(...)
  }
  class RoleSet {
    <<value>>
    can(Capability) boolean
  }
  class ActiveAdmins {
    <<fact>>
  }
  UserAccount "1" *-- "many" CredentialLink
  Session "1" *-- "many" RefreshToken
  UserAccount --> RoleSet
  UserAccount ..> ActiveAdmins : changeRoles and deactivate require it
  Session ..> AccountId : holds the ID
  Session ..> UserAccount : open calls checkSignInAllowed
```

- **Packages** (`core/access/domain/`): the root holds `AccessError`, `AccountGeneration`,
  `CommonPasswords`, `Language`, `NewPassword` and `TokenHash`; `account/` (16 files) holds
  `UserAccount`, `CredentialLink`, `RoleSet`, `Role`, `Capability`, `ActiveAdmins`, `EmailAddress`,
  `EmailHolder` and the states; `session/` (7) holds `Session`, `RefreshToken`, `TokenState`,
  `RefreshOutcome`; `companyprofile/` (4) holds `CompanyProfile`, `CompanyName`, `DefaultWindow`,
  `DefaultServiceTime`. `session/` imports `account/` once: `Session.open` takes the `UserAccount`.
- **Two counters, not one.** `AccountGeneration` is on the account and `SessionGeneration` on the
  session, as distinct types. A token issued under an older generation is refused (DL §3.3).
- **Step-up lives on the session.** `confirmPassword` is the Admin step-up (NFR-214, NFR-227): it
  supersedes the current refresh token and issues a new one under a new `SessionGeneration`.
  `requireFreshStepUp` refuses an Admin-only operation once `STEP_UP_LIFETIME` (5 minutes) has passed
  since the password was last given. An Admin-only use case calls it rather than checking by itself.
- **Events:** `SessionsRevoked` and `AccountAnonymized`, both raised by `UserAccount`.
- **Start reading:** `CompanyProfile.java` (162 lines) for the pattern, then `Session.java` (698, the
  refresh-token chain), then `UserAccount.java` (857). Tests: `core-test/access/domain/account/`
  (`UserAccountTest`, `UserAccountLifecycleTest`, `CapabilityMatrixTest`), `session/` (`SessionTest`,
  `SessionLifecycleTest`) and `companyprofile/`.

### 5.2 Fleet

DL [§4.2](domain_layer_design.md#42-fleet). Depots, vehicles, drivers, and who drives what on a date.

```mermaid
classDiagram
  direction LR
  class FleetRegister {
    <<root>>
    addDepot(...)
    changeDepot(...)
    retireDepot(...)
    addVehicle(...)
    changeVehicle(...)
    retireVehicle(...)
    reactivateVehicle(...)
    standingOf(vehicle) Standing
    activeVehicles() List
  }
  class Depot {
    <<entity>>
    DepotId id
    DepotName name
    GeoPoint location
    DepotHours hours
    Instant retiredAt
  }
  class Vehicle {
    <<entity>>
    VehicleId id
    VehicleDetails details
    Instant retiredAt
  }
  class VehicleDetails {
    <<value>>
    VehicleName name
    Plate plate
    Capacity capacity
    DepotId startDepot
    DepotId endDepot
  }
  class Driver {
    <<root>>
    AccountId id
    DriverStatus status
    DriverContact contact
    ShiftHours defaultShift
    VehicleId defaultVehicle
    WeeklyShifts weeklyShifts
  }
  class DayRoster {
    <<root>>
    DeliveryDate date
    List entries
    List unavailableVehicles
    open(date)
    fill(defaults, activeVehicles) RosterFill
    assign(...)
    moveVehicleWithRoute(...)
    checkAssignable(...) Assignability
  }
  class RosterEntry {
    <<value>>
    AccountId driver
    Shift shift
    RosterVehicle vehicle
    Availability availability
  }
  class RosterVehicle {
    <<sealed>>
    NotSet
    NoVehicle
    Assigned(VehicleId vehicle)
  }
  FleetRegister "1" *-- "many" Depot
  FleetRegister "1" *-- "many" Vehicle
  Vehicle --> VehicleDetails
  Vehicle ..> Depot : DepotId, twice
  DayRoster "1" *-- "many" RosterEntry
  RosterEntry --> RosterVehicle
  RosterEntry ..> Driver : AccountId
  RosterVehicle ..> Vehicle : VehicleId
  Driver ..> Vehicle : default VehicleId
```

- **Packages** (`core/fleet/domain/`): the root holds `FleetError`, `PlanUse`, `PlanUses`, `RosterDefault`,
  `ShiftHours` and `Standing`; `register/` (10 files) holds `FleetRegister`, `Depot`, `Vehicle`, `Plate`,
  `Capacity`, `DepotHours`, `DepotName`, `VehicleName`; `driver/` (5) holds `Driver`, `DriverStatus`,
  `DriverName`, `WeeklyShifts`; `roster/` (9) holds `DayRoster`, `RosterEntry`, `Shift`, `RosterFill`, `Assignability`.
  The three aggregate packages import only the root.
- **`Depot` and `Vehicle` are package-private classes.** Nothing outside `register/` can change one; the
  only way is through `FleetRegister`. ADR-118 names this as an example of package-private access that
  binds a root to its parts.
- **Two kinds of fact come in.** `Standing` (active or not active) is what Access and the register say
  about a driver or vehicle; `PlanUses` is what Planning says about plans that use a record. Both arrive as
  parameters.
- **Events:** `DriverRetired`, raised by `Driver.retire`. `Driver.anonymize` and
  `anonymizeWithAccount` raise none; the second one is called from the listener for Access's
  `AccountAnonymized`.
- **Start reading:** `Driver.java` (§4 above), then `FleetRegister.java` (432), then `DayRoster.java`
  (510). Tests: `core-test/fleet/domain/driver/DriverTest`, `register/FleetRegisterTest`,
  `roster/DayRosterTest`.

### 5.3 Customers & Orders

DL [§4.3](domain_layer_design.md#43-customers--orders). Who receives what: customers, orders and
imports. This is the one flat module with more than 30 files, for the reason in §2.

```mermaid
classDiagram
  direction LR
  class Customer {
    <<root>>
    CustomerId id
    ContactName name
    PhoneNumber phone
    Address address
    DriverNote note
    Location location
    Set flags
    OrderDefaults defaults
    State state
    register(...)
    changeAddress(...)
    recordGeocode(...)
    placeLocation(...)
    retire(usage, now)
    anonymize(lastDeliveryDate, now)
  }
  class Order {
    <<root>>
    OrderId id
    CustomerId customer
    DeliveryDate date
    TimeWindow window
    Weight weight
    Volume volume
    ServiceTime serviceTime
    Priority priority
    boolean leaveAtDoor
    ExternalReference reference
    Recipient recipient
    AccessDetails access
    Attempt attempt
    State state
    place(...)
    change(...)
    reschedule(...)
    cancel(progress, now)
    putBack(...)
    anonymize(today)
  }
  class ImportBatch {
    <<root>>
    ImportBatchId id
    FileHash hash
    FileEncoding encoding
    List rows
    State state
    preview(...)
    confirm(...)
    discard(...)
    expire(...)
  }
  class DateCapacity {
    <<fact>>
    locked read of one date
  }
  class Location {
    <<sealed>>
    Missing
    Geocoded(GeoPoint at, MatchQuality quality)
    HandPlaced(GeoPoint at)
  }
  class OrderStatus {
    <<enum>>
    UNPLANNED
    PLANNED
    DROPPED
    DELIVERED
    FAILED
    CANCELLED
  }
  class StopState {
    <<sealed>>
    NoFact
    Arrived(Attempt)
    Delivered(Attempt)
    Failed(Attempt)
  }
  class ImportResult {
    <<value>>
    List customers
    List orders
  }
  Customer --> Location
  Order ..> Customer : CustomerId
  Order ..> DateCapacity : place, reschedule and putBack require it
  Order ..> StopState : cancel and change are given it
  ImportBatch ..> ImportResult : confirm returns it
  ImportResult ..> Customer : new customers
  ImportResult ..> Order : new orders
  OrderStatus ..> Order : derived, never stored
```

- **Package:** one flat package, `core/orders/domain/` (40 files, the largest of the flat ones). Beyond
  the three roots: the personal-data values (`Address`, `ContactName`, `DriverNote`, `Floor`,
  `Apartment`, `EntranceCode`, `AccessDetails`, `Recipient`), `NormalizedAddress` and `CustomerMatch` for
  matching, `OrderDefaults` and `DailyWindow`, `ServiceArea` for the area test, and the import types
  (`ImportLine`, `ImportRow`, `PreviewRow`, `ImportContext`, `ImportResult`).
- **`OrderStatus` is derived, not stored.** It comes from the order's own `State`, a `Placement`
  (Planning's answer) and a `StopState` (Live Operations' answer). DL
  [§9.6](domain_layer_design.md#96-order-status-br-33-is-derived-not-stored) draws it.
- **`ImportBatch.confirm` creates other aggregates.** It returns an `ImportResult` holding new
  `Customer`s and `Order`s, which the use case saves with the batch in one transaction (DL
  [§6.1](domain_layer_design.md#61-the-rule)). ImportBatch's lifecycle is DL
  [§9.4](domain_layer_design.md#94-importbatch).
- **Events:** `OrderCancelled`, `OrderRescheduled` and `OrderPutBack`, all raised by `Order`.
- **Start reading:** `Order.java` (689) with `OrderStatus.java` and `DateCapacity.java`, then
  `Customer.java` (624), then `ImportBatch.java` (558). Tests: `core-test/orders/domain/` (`OrderTest`,
  `OrderStatusTest`, `CustomerTest`, `ImportBatchTest`, `CustomerMatchTest`, `NormalizedAddressTest`),
  with builders `OrderBuilder`, `CustomerBuilder`, `ImportBatchBuilder`.

### 5.4 Planning

DL [§4.4](domain_layer_design.md#44-planning), whose class diagram already draws `Plan`, `Route`,
`PlannedStop`, `PlanRevision`, `SolveJob` and `ReoptimizationProposal`. This guide adds what the DL
does not draw: the package map, why three aggregates share one package, and what `Plan` holds beyond
its routes. Planning is the largest module: 85 domain files and about 9,000 lines.

**Package map.** Arrows point from the package that imports to the package it imports (ADR-118 rule 7).
The root imports none of its subpackages.

```mermaid
flowchart LR
  PLAN["plan (35 files)<br/>Plan, SolveJob,<br/>ReoptimizationProposal"]
  SOL["solution (20)<br/>results, ValidationReport,<br/>ValidatedSolution"]
  DROP["drop (4)<br/>DroppedOrder, DropReason,<br/>ExcludedVehicle"]
  BAS["basis (4)<br/>SolveBasis, ProposalBasis,<br/>ChangesSinceBasis"]
  REV["revision (3)<br/>PlanRevision"]
  REC["reconciliation (4)<br/>LateFact, Reconciliation"]
  SET["settings (7)<br/>PlanningSettings,<br/>TravelBuckets, Buffer"]
  ROOT["root domain (8)<br/>PlanningError, RevisionNo,<br/>SolveTime, SolveLimit, RuleCode, PinTarget"]
  PLAN --> SOL
  PLAN --> DROP
  SOL --> DROP
  DROP --> ROOT
  PLAN --> BAS
  PLAN --> REV
  PLAN --> REC
  PLAN --> ROOT
  SOL --> ROOT
  BAS --> ROOT
  REV --> ROOT
  SET --> ROOT
```

**Why `SolveJob` and `ReoptimizationProposal` live in `plan/`.** Because of Java's package-private
access, not because of size. `SolveJob.queue` is a package-private static factory that only `Plan.requestSolve` and
`Plan.requestReoptimization` call, so a job exists only with a plan that can hold it. And
`ReoptimizationProposal.create` is a package-private static factory that only `SolveJob.succeed` calls,
so a proposal exists only for a job that succeeded. Java's package-private access needs the three in one
package, and ADR-118 forbids widening visibility to move a file. The package Javadoc says the same
(`core/planning/domain/plan/package-info.java`).

```mermaid
flowchart LR
  subgraph PKG["package plan"]
    PLAN["Plan<br/>requestSolve, requestReoptimization,<br/>applySolveResult, accept"]
    JOB["SolveJob<br/>queue: package-private"]
    PROP["ReoptimizationProposal<br/>create: package-private"]
  end
  ADP["optimizer adapter<br/>declared, not built"]
  VAL["ValidatedSolution<br/>built only from an accepted ValidationReport"]
  PLAN -->|"queue"| JOB
  JOB -->|"succeed with a re-optimization result: create"| PROP
  ADP -->|"builds the report"| VAL
  VAL -->|"succeed(ValidatedSolution, now)"| JOB
  JOB -.->|"SolveSucceeded: applySolveResult reads the job"| PLAN
  PROP -->|"accept takes the proposal"| PLAN
```

The solid arrows are Java calls. The dotted one is an event (§7). Only the optimizer adapter may build a
`ValidationReport` or an `Evaluation`, so `Plan` can't be handed an unvalidated solution (DL
[§13](domain_layer_design.md#13-inside-each-module-hexagon); the rule is
`DomainPurityRules.optimizerResultsOnlyFromOptimizerAdapter`, run by `DomainPurityTest`).

**What `Plan` holds.** Its fields are in `core/planning/domain/plan/Plan.java`, and the tables it is stored
in are in [data-model.md](../data-model.md#planning-v8).

```mermaid
classDiagram
  direction LR
  class Plan {
    <<root>>
    PlanId id
    DeliveryDate date
    Instant openedAt
    Status status
    AppliedSolve applied
    List routes
    Set stops
    List dropped
    List pins
    List excluded
    List violations
    List undo
    List redo
    PlanRevision pendingRevision
  }
  class Status {
    <<sealed>>
    Draft
    Solving(SolveBasis pending)
    Ready
    Published(RevisionNo revision)
    Completed(RevisionNo revision)
    Cancelled(RevisionNo revision, Instant at)
    Abandoned
  }
  class Route {
    <<entity>>
    RouteId id
    RouteIndex index
    VehicleId vehicle
    AccountId driver
    RouteLock lock
    CheckState check
    Withdrawal withdrawal
    SolveTime confirmedReturn
    RoutePlan plan
    List stops "which stops, in order"
  }
  class PlannedStop {
    <<entity>>
    StopId id
    OrderId order
    Attempt attempt
    StopPlan plan
    LateOutcome late
  }
  class Edit {
    <<sealed>>
    MovedStop
    TookOff
    Placed
    Pinned
    Unpinned
    Locked
    Unlocked
  }
  class PlanRevision {
    <<value>>
    RevisionNo number
    Instant effectiveAt
    RevisionCause cause
    List~RevisionRoute~ routes
  }
  Plan --> Status
  Plan "1" *-- "many" Route
  Plan "1" *-- "many" PlannedStop : creates and deletes
  Route "1" o-- "many" PlannedStop : serves, in order
  Plan "1" *-- "many" Edit : undo and redo journals
  Plan "1" *-- "0..1" PlanRevision : pending
  PlannedStop ..> OrderId : order and attempt
```

- **`Route` and `PlannedStop` are package-private entity classes**, not public ones. A stop moves between
  routes and keeps its ID, so `Plan` holds every stop in a collection of its own that alone creates and
  deletes them, and a route's list only says which stops it serves ([ADR-127](../../ADRs/ADR-127-how-the-aggregates-are-mapped-and-where-their-stored-state-is-tested.md), decision 11). Other code
  sees a plan through `PlanLayout` (public: routes, dropped orders and pins by ID), a read-only view that
  the `layout()` and `after…` queries return.
- **`Edit` is the undo journal.** Each of the seven records stores what an edit changed, so `undo` and
  `redo` reverse it. DL [§4.4](domain_layer_design.md#44-planning) states its limit of 100 edits.
- **The `after…` queries** (`afterMove`, `afterTakeOff`, `afterPlacing`, `afterPin`, `afterUnpin`,
  `afterUndo`, `afterRedo`, `afterHandOver`) return the `PlanLayout` an edit would leave, so a use case
  can ask the optimizer to check it before the transaction (DL
  [§6.3](domain_layer_design.md#63-outside-calls-happen-before-the-transaction)).
- **What is in `solution/`.**

| Role | Types in `core/planning/domain/solution/` |
|---|---|
| What a solve returns | `SolveResult`, `SolvedRoute`, `SolvedStop`, `StopPlan`, `StopTimes`, `RoutePlan`, `TravelLeg`, `RouteTimes`, `StopTiming` |
| Gate types only the optimizer adapter builds | `ValidatedSolution`, `ValidationReport`, `Evaluation` |
| What a re-optimization previews | `ReoptimizationResult`, `ObjectiveTerms`, `LateStop`, `DriverChange`, `VehicleWithdrawal`, `Withdrawal` |
| Other | `Violation`, `SolveFailure` |

Why an order or a vehicle is out sits in `drop/`: `DropReason` (a sealed interface of eleven
records), `DroppedOrder`, `ExcludedVehicle` and `ExclusionCause`, which `solution` and `plan` use
([ADR-125](../../ADRs/ADR-125-a-folder-above-20-files-splits-wherever-a-group-of-four-can-move.md)).
`PinTarget` sits in the root, because `plan` and `drop` both use it.

- **Events** (`core/planning/events/`): `PlanPublished`, `PlanRevised`, `RouteHandedOver`, `PlanCancelled`
  from `Plan`; `SolveSucceeded`, `SolveFailed` from `SolveJob`. The package also holds the enums these
  events use: `Lane`, `RevisionCause`, `SolveFailureReason`, `WithdrawalCause`.
- **Start reading:** `SolveJob.java` (711) and `ReoptimizationProposal.java` (581) first. Then `Plan.java`
  (2,718 lines) by section: its source has banner comments (`// ---- Solving ----`), and its tests are split
  by topic, so read a section beside its test. Tests: `core-test/planning/domain/plan/`
  (`PlanLifecycleTest`, `PlanSolvingTest`, `PlanEditingTest`, `PlanPublishingTest`, `PlanPublishedTest`,
  `PlanRoutesAndFactsTest`, `PlanSequenceTest`, `PlanQueriesTest`, `PlanPendingRevisionTest`,
  `PlanOptimizerOutputsTest`, `SolveJobTest`, `ReoptimizationProposalTest`, `PlanRefusalsTest`) and `core-test/planning/events/PlanningEventsTest`.

### 5.5 Live Operations

DL [§4.5](domain_layer_design.md#45-live-operations). What happened on the street: arrivals, outcomes,
route runs and alerts. Planning owns intent and Live Operations owns facts (DL
[§8](domain_layer_design.md#8-when-a-fact-contradicts-the-plan)).

```mermaid
classDiagram
  direction LR
  class Delivery {
    <<root>>
    StopId stop
    OrderId order
    Attempt attempt
    ArrivalRecord arrival
    List outcomes
    List heldReports
    status() DeliveryStatus
    firstFact(stop, order, attempt)
    arrive(report, entitlement, where)
    deliver(...) ReportAnswer
    fail(...) ReportAnswer
    recordOnBehalf(...) ReportAnswer
    correct(...)
    confirmHeld(...) ReportAnswer
    refuseHeld(...)
    eraseExpired(now)
  }
  class OutcomeRecord {
    <<entity>>
    Outcome outcome
    AccountId by
    Instant factTime
    Origin origin
  }
  class HeldReport {
    <<entity>>
    HeldReportId id
    Outcome outcome
    AccountId driver
    RouteId route
    RevisionNo revision
    CorrectedTime time
    HeldState state
  }
  class Outcome {
    <<sealed>>
    Delivered(Optional proof, boolean leftAtDoor)
    Failed(FailureReason reason)
  }
  class Origin {
    <<sealed>>
    Reported
    ConfirmedHeld
    OnBehalf
    Correction
  }
  class HeldState {
    <<sealed>>
    Open
    Confirmed(by, at)
    RefusedByDispatcher(by, at)
    Overtaken(at)
  }
  class Entitlement {
    <<fact>>
  }
  class HoldCheck {
    <<fact>>
  }
  class StopWhereabouts {
    <<fact>>
  }
  Delivery "1" *-- "many" OutcomeRecord
  Delivery "1" *-- "many" HeldReport
  OutcomeRecord --> Outcome
  OutcomeRecord --> Origin
  HeldReport --> HeldState
  Delivery ..> Entitlement : arrive, deliver, fail
  Delivery ..> HoldCheck : deliver and fail
  Delivery ..> StopWhereabouts : arrive, deliver, fail
```

The other two aggregates are smaller.

```mermaid
classDiagram
  direction LR
  class RouteRun {
    <<root>>
    RouteId route
    AccountId startedBy
    RunStartCause cause
    Instant startedAt
    RunStatus status
    RevisionNo confirmed
    start(...) RouteRun
    finish(...)
    acknowledge(...)
    checkPositionAt(...)
    acceptsPositionAt(...) boolean
    isConfirmationOverdue(...) boolean
  }
  class RunStatus {
    <<sealed>>
    Started
    Finished(Instant at, AccountId by, List pending)
  }
  class Alert {
    <<root>>
    AlertId id
    AlertSubject subject
    Instant raisedAt
    AlertState state
    raise(id, subject, now)
    acknowledge(by, now)
  }
  class AlertSubject {
    <<sealed>>
    StopAtRisk
    DriverProblemReported
    RouteNotStarted
    StopFailed
    RouteChangeUnconfirmed
    LateReport
    ReportHeld
    ReportRefused
    RouteFinishedWithPendingStops
    LowRating
  }
  class AlertState {
    <<sealed>>
    Open
    Acknowledged(by, at)
  }
  RouteRun --> RunStatus
  Alert --> AlertSubject
  Alert --> AlertState
```

- **Packages** (`core/liveops/domain/`): the root (8 files) holds `LiveOpsError`, `CorrectedTime`,
  `DriverReport`, `ContactWindow`, `RevisionNo`, `HeldReportId`, `CancellationKind`, `TrackingRefusal`;
  `delivery/` (18) holds `Delivery`, the package-private types that store its outcome records and held
  reports (`StoredOutcomeRecord`, `StoredOutcome`, `StoredHeldReport`), `ArrivalProjection`,
  `ArrivalRecord` and `RetentionPeriods`; `outcome/` (9) holds `Outcome`, `FailureReason`,
  `FailureReasonKind`, `DispatcherOutcome`, and the outcomes a delivery keeps on record:
  `OutcomeRecord`, `Origin`, `CorrectionReason`, `HeldReport`, `HeldState`. It uses
  `proof/` (4), which holds `Proof`, `ProofId`, `ProofKind`, `ProofNote`; `alert/` (8) holds `Alert`, `AlertSubject`, `AlertKind`,
  `DedupeKey`; `routerun/` (5) holds `RouteRun`, `RunStatus`, `TrackingWindow`, `ConfirmationDeadline`.
- **`Delivery`'s status is derived.** `DeliveryStatus` (`PENDING`, `ARRIVED`, `HELD`, `DELIVERED`,
  `FAILED`) is computed from the outcomes and held reports, effective outcome first; it is not stored.
  DL [§9.3](domain_layer_design.md#93-delivery-br-87) draws it.
- **Each alert kind has its own subject record**, and each `AlertSubject` defines its `DedupeKey`, so an
  alert is raised once per key. `Alert.raise` is the creation factory. It raises no event, because
  screens and push follow the commit.
- **Many facts are passed in**: who may report (`Entitlement`), where the stop is now (`StopWhereabouts`),
  whether a late report must be held (`HoldCheck`), whether a later attempt exists (`LaterAttempt`). Each
  is read by the use case from another module first (DL
  [§4.5](domain_layer_design.md#45-live-operations)).
- **Events** (`core/liveops/events/`): `OutcomeRecorded` and `PutBackSuperseded` from `Delivery`;
  `RouteStarted` from `RouteRun.start`. Enums beside them: `OutcomeKind`, `RunStartCause`.
- **Start reading:** `Alert.java` (329), `RouteRun.java` (431), then `Delivery.java` (823) with
  `DeliveryInvariants.java`. Tests: `core-test/liveops/domain/alert/AlertTest`, `routerun/RouteRunTest`,
  `delivery/` (`DeliveryLifecycleTest`, `DeliveryReportsTest`, `DeliveryCorrectionTest`,
  `DeliveryRefusalsTest`, `DeliverySequenceTest`, `ArrivalProjectionTest`).

### 5.6 Recipient Messaging

DL [§4.6](domain_layer_design.md#46-recipient-messaging). SMS to recipients, their replies, ratings and
opt-outs.

```mermaid
classDiagram
  direction LR
  class StopMessages {
    <<root>>
    StopId stop
    List messages
    open(stop)
    onRouteStarted(...)
    onJoinedStartedRoute(...)
    onArrivalProjected(...)
    onDeliveryChanged(...)
    onOutcome(...)
    claim(...)
    accepted(...)
    refused(...)
    outcomeUnknown(...)
    dropStale(now)
  }
  class OutgoingMessage {
    <<entity>>
    OutgoingMessageId id
    MessageType type
    int sequence
    PhoneNumber phone
    MessageText text
    QuarterHour arrivalSent
    boolean asksForRating
    Instant triggeredAt
    MessageState state
  }
  class MessageState {
    <<sealed>>
    Pending(Instant since)
    Sending(Instant since)
    Accepted(Instant since, ProviderReference reference)
    Unknown(Instant since)
    Dropped(Instant since, DropCause cause)
  }
  class RatingRequest {
    <<root>>
    RatingRequestId id
    StopId stop
    PhoneNumber phone
    Instant acceptedAt
    Rated rating
    List comments
    OneTimeReply reminder
    OneTimeReply thankYou
    open(id, question)
    answer(...) Answer
    removePersonalData(now)
    statusAt(now) Status
  }
  class ReceivedComment {
    <<entity>>
    Comment comment
    Instant receivedAt
  }
  class MessagingSettings {
    <<root>>
    Switch messages
    Switch ratingQuestion
    DispatchPhone dispatchPhone
    create(dispatchPhone)
    conditions(...) SendingConditions
  }
  class Wording {
    <<sealed>>
    OutForDelivery
    ArrivingSoon
    TimeChanged
    DeliveryChanged
    MissedYou
    Delivered
    Answering
  }
  StopMessages "1" *-- "many" OutgoingMessage
  OutgoingMessage --> MessageState
  OutgoingMessage --> Wording : MessageText is built from one
  RatingRequest "1" *-- "many" ReceivedComment
  StopMessages ..> MessagingSettings : given SendingConditions
  RatingRequest ..> StopMessages : same StopId
```

- **Packages** (`core/messaging/domain/`): the root (19 files) holds `MessagingError`, `MessageText`,
  `Wording`, `PhoneHash`, `OptOutRecord`, `OptOutStatus`, `QuarterHour`, `SendingConditions`;
  `message/` (12) holds `StopMessages`, `OutgoingMessage`, `MessageState`, `MessageType`, `DailySendCap`;
  `rating/` (7) holds `RatingRequest`, `Rating`, `Comment`, `Reply`, `ReplyText`; `settings/` (1) holds
  `MessagingSettings`.
- **Records, not aggregates.** Opt-outs (`OptOutRecord`, `OptOutStatus`) and inbound replies are
  append-only records (DL [§3.2](domain_layer_design.md#32-records)). A message's lifecycle is DL
  [§9.5](domain_layer_design.md#95-a-message-to-a-recipient).
- **`RatingRequest` has a status that is not stored.** `statusAt(now)` returns `OPEN`, `RATED` or
  `CLOSED`, and it is closed once 48 hours have passed (`ANSWER_WINDOW`).
- **Event:** `RatingReceived`, raised by `RatingRequest.answer` (`core/messaging/events/`).
- **Start reading:** `MessagingSettings.java` (146), `RatingRequest.java` (444), then `StopMessages.java`
  (657). Tests: `core-test/messaging/domain/message/` (`StopMessagesTriggersTest`,
  `StopMessagesSendingTest`), `rating/` (`RatingRequestTest`, `ReplyTest`), `settings/`, and the root
  (`MessageTextTest`, `OptOutStatusTest`).

### 5.7 Reporting

DL [§4.7](domain_layer_design.md#47-reporting). The daily summary and the comparison with the baseline.
It has one aggregate; everything else in Reporting is a value or, later, a read model.

```mermaid
classDiagram
  direction LR
  class Baseline {
    <<root>>
    PlanId plan
    PlannedFigures result
    BaselineCause cause
    Instant computedAt
    compute(plan, result, now)
    recompute(...)
    compareWith(...) BaselineComparison
    withdrawnFleetAfter(...) WithdrawnFleet
  }
  class PlannedFigures {
    <<value>>
    Distance distance
    DriverHours driverHours
    PlannedOrders orders
  }
  class PlannedOrder {
    <<value>>
    OrderId order
    TimeWindow window
    PlannedService service
  }
  class BaselineCause {
    <<sealed>>
    Published
    Recomputed(List withdrawn, List added)
  }
  class BaselineComparison {
    <<sealed>>
    AllMeasures
    KilometresAndOnTime
    NotCompared
  }
  class PlannedOnTimeRate {
    <<sealed>>
    Measured(int onTime, int orders)
    Unavailable
  }
  Baseline --> PlannedFigures
  Baseline --> BaselineCause
  PlannedFigures "1" *-- "many" PlannedOrder : via PlannedOrders
  Baseline ..> BaselineComparison : compareWith returns
  BaselineComparison --> PlannedOnTimeRate
```

- **Packages** (`core/reporting/domain/`, 20 files): the root (10) holds `ReportingError`, the two rate
  types and the input facts `StopResult` and `StopOutcome`; `baseline/` (10) holds `Baseline`, `Distance`, `DriverHours`, `WithdrawnFleet`, `Withdrawal`, `WithdrawalCause` and
  `PlanStanding`, and uses the root one way
  ([ADR-125](../../ADRs/ADR-125-a-folder-above-20-files-splits-wherever-a-group-of-four-can-move.md)).
- **The rates are exact shares of two counts.** `PlannedOnTimeRate` (in the diagram) and `OnTimeRate`
  (the actual rate, from `StopResult`s) are each `Measured` with two integers, or `Unavailable`; only a
  screen rounds. With no delivered stop the answer is `Unavailable`, never 0.
- **`WithdrawalCause` here is Reporting's own copy**, mapped from Planning's `WithdrawalCause` event enum
  when `PlanRevised` is heard (§11).
- **No events and no personal data.**
- **Start reading:** `OnTimeRate.java` (87), then `baseline/Baseline.java` (414). Tests: `core-test/reporting/domain/`
  (`OnTimeRateTest`, `PlannedOnTimeRateTest`, and in `baseline/`, `BaselineTest` and `WithdrawnFleetTest`).

### 5.8 Audit

DL [§4.8](domain_layer_design.md#48-audit). The append-only audit trail. **It has no aggregate.** An
`AuditRecord` is an immutable record (DL [§3.2](domain_layer_design.md#32-records)), written through the
`AuditLog` port: a success in the same transaction as the change it describes, a refusal once that
transaction has rolled back ([ADR-149](../../ADRs/ADR-149-the-use-case-records-its-refusal-after-the-rollback-and-the-audit-purge-counts-its-year-back-from-now.md)).

```mermaid
classDiagram
  direction LR
  class AuditRecord {
    <<value>>
    Instant at
    Optional user
    Optional session
    Optional app
    OperationName operation
    List records
    AuditOutcome outcome
    TraceId traceId
    RETENTION
    MAX_RECORDS
  }
  class AuditOutcome {
    <<sealed>>
    Succeeded
    Refused(ErrorCode code)
  }
  class DeclaredApp {
    <<value>>
    ClientApp app
    AppVersion version
  }
  class NamedRecord {
    <<value>>
    RecordKind kind
    UUID id
  }
  AuditRecord --> AuditOutcome
  AuditRecord --> DeclaredApp
  AuditRecord "1" *-- "up to 500" NamedRecord
  AuditRecord ..> AccountId : user
  AuditRecord ..> SessionId : session
```

- **Package:** one flat package, `core/audit/domain/` (11 files): `AuditRecord`, `AuditOutcome`,
  `ErrorCode`, `OperationName`, `NamedRecord`, `RecordKind`, `DeclaredApp`, `ClientApp`, `AppVersion`,
  `TraceId`, `AuditError`. Every field is an ID, a code, an enum, a number or an instant, never free text.
- **Audit depends on nothing but the kernel.** It takes `AccountId` and `SessionId`, never an `Actor`
  (DL [§2.1](domain_layer_design.md#21-module-graph)). It raises no events.
- **Start reading:** `AuditRecord.java` (128 lines). Tests: `core-test/audit/domain/` (`AuditRecordTest`,
  `AuditOutcomeTest`, `NamedRecordTest`).

### 5.9 Optimization (Python)

DL [§4.9](domain_layer_design.md#49-optimization-python). The optimizer's domain has no aggregates and keeps
no state between calls. It is value objects whose methods are the rules. §8 maps its modules.

---

## 6. Lifecycles the DL does not draw

DL [§9](domain_layer_design.md#9-lifecycles) draws five: [Plan](domain_layer_design.md#91-plan-br-12),
[SolveJob](domain_layer_design.md#92-solvejob), [Delivery](domain_layer_design.md#93-delivery-br-87),
[ImportBatch](domain_layer_design.md#94-importbatch) and
[a message to a recipient](domain_layer_design.md#95-a-message-to-a-recipient). Order status, which is
derived, is [§9.6](domain_layer_design.md#96-order-status-br-33-is-derived-not-stored), and the table of
how every kind of thing ends is [§9.7](domain_layer_design.md#97-how-things-end). The lifecycles below
come from the sealed state types in the code and the methods that move between them. Each arrow is one
method, except where its label says that time passes.

### UserAccount (`AccountState`)

`core/access/domain/account/AccountState.java`. Anonymized is final.

```mermaid
stateDiagram-v2
  [*] --> Active : open or openFirstAdmin
  Active --> Deactivated : deactivate or deactivateOnWrittenRequest
  Deactivated --> Active : reactivate
  Deactivated --> Anonymized : anonymize after a year
  Anonymized --> [*]
```

`deactivate` and `deactivateOnWrittenRequest` also void every open link and revoke every session, which
raises `SessionsRevoked`. `anonymize` raises `AccountAnonymized`. An active account that is anonymized is
refused with `AccountNotDeactivated`.

### Session (`SessionState`) and its refresh tokens (`TokenState`)

`core/access/domain/session/SessionState.java` and `TokenState.java`. The session has two states; the
interesting part is the chain of refresh tokens inside it (NFR-88, NFR-178 in the requirements).

```mermaid
stateDiagram-v2
  [*] --> Live : open
  Live --> Revoked : revoke
  Live --> Revoked : revokeWithAccount
  Live --> Revoked : exchange, when a spent token is reused
  Revoked --> [*]
```

```mermaid
stateDiagram-v2
  [*] --> Current : open, exchange or confirmPassword issues it
  Current --> Exchanged : exchange
  Exchanged --> Exchanged : exchange again within 30 seconds, while its successor is current
  Current --> Replaced : its predecessor is exchanged again within 30 seconds
  Current --> Superseded : confirmPassword
```

These revoke the session: a `Replaced` token presented again, an `Exchanged` one after its 30 seconds,
and an `Exchanged` one within them whose successor is no longer current (for example, because it was
exchanged too). A `Superseded` token is refused and revokes nothing, and so is an `Exchanged` one,
within its 30 seconds, whose successor a password confirmation superseded (`Session.exchange` and its
private `repeated`). `Session.exchange` answers with a `RefreshOutcome`
(`Issued` or `Refused`) instead of throwing, so the caller can save the revocation.

### Driver (`DriverStatus`)

`core/fleet/domain/driver/DriverStatus.java`.

```mermaid
stateDiagram-v2
  [*] --> Active : register
  Active --> Retired : retire
  Retired --> Anonymized : anonymize after a year
  Active --> Anonymized : anonymizeWithAccount
  Retired --> Anonymized : anonymizeWithAccount
  Anonymized --> [*]
```

`retire` raises `DriverRetired`. The `anonymize` call on a driver that is already anonymized changes
nothing, because the nightly run repeats.

### Customer (`Customer.State`)

`core/orders/domain/Customer.java`.

```mermaid
stateDiagram-v2
  [*] --> Active : register
  Active --> Retired : retire when no plan uses it
  Retired --> Anonymized : anonymize a year after retirement
  Active --> Anonymized : anonymize a year after the later of creation and last order
```

### Order (`Order.State`, plus a flag)

`core/orders/domain/Order.java`. An order has only two states. A reschedule or a put-back changes its
date or attempt but not its state, and anonymizing sets a boolean (`anonymized`) beside the state.

```mermaid
stateDiagram-v2
  [*] --> Active : place
  Active --> Active : change, reschedule or putBack
  Active --> Cancelled : cancel
  Cancelled --> [*]
```

What the business calls the order's status (`UNPLANNED`, `PLANNED`, `DROPPED`, `DELIVERED`, `FAILED`,
`CANCELLED`) is the derived `OrderStatus`, in DL §9.6.

### RouteRun (`RunStatus`)

`core/liveops/domain/routerun/RunStatus.java`.

```mermaid
stateDiagram-v2
  [*] --> Started : start, by the driver or the first report
  Started --> Finished : finish
  Finished --> [*]
```

`acknowledge` records the revision the driver confirmed and changes no state. A second `start` on an
existing run is the instance overload `RouteRun.start(report, routeDriver, currentDriver, now)`, which
only refuses.

### Alert (`AlertState`)

`core/liveops/domain/alert/AlertState.java`.

```mermaid
stateDiagram-v2
  [*] --> Open : raise
  Open --> Acknowledged : acknowledge
  Acknowledged --> [*]
```

An acknowledged alert's dedupe key stays used, so the same alert is never raised again.

### RatingRequest (`Status`, derived from the time)

`core/messaging/domain/rating/RatingRequest.java`. Its status is computed by `statusAt(now)` and never
stored; what is stored is a rating and, separately, when the phone number was removed.

```mermaid
stateDiagram-v2
  [*] --> OPEN : open, when the question is accepted
  OPEN --> RATED : answer, with the first rating
  OPEN --> CLOSED : 48 hours pass
  RATED --> CLOSED : 48 hours pass
  CLOSED --> [*]
```

`removePersonalData` takes the phone away 90 days after the question was accepted, and each comment 90
days after its own reply, without changing the status.

### ReoptimizationProposal (`State`)

`core/planning/domain/plan/ReoptimizationProposal.java`. It never changes except to be declined once.
Accepting it is not a state of the proposal: `Plan.accept` takes it and makes a revision that cites it
(DL [§9.7](domain_layer_design.md#97-how-things-end)).

```mermaid
stateDiagram-v2
  [*] --> Ready : SolveJob.succeed creates it
  Ready --> Declined : decline
```

---

## 7. Events across modules

DL [§7](domain_layer_design.md#7-domain-events) says when an event earns its place and
[§7.2](domain_layer_design.md#72-the-events) lists them. At this commit the `events/` packages hold **16
event records**, in the 15 rows of the DL table (one row names two events). Six modules raise them;
Audit and Reporting raise none. An event is raised by an aggregate's method and recorded on the root
(§3), and is a past-tense record of IDs and kernel values.

The diagram below shows which module raises each event and which module's declared listener hears it.
An arrow from a module to itself is a listener in the same module. The listeners are in the
`application/service/` packages and are declared, not built.

```mermaid
flowchart LR
  ACC["Access"]
  FL["Fleet"]
  CO["Customers & Orders"]
  PL["Planning"]
  LO["Live Operations"]
  MS["Recipient Messaging"]
  RP["Reporting"]

  CO -->|"OrderCancelled, OrderRescheduled, OrderPutBack"| PL
  CO -->|"OrderCancelled, OrderRescheduled"| MS
  PL -->|"PlanPublished, PlanRevised, PlanCancelled, RouteHandedOver"| LO
  PL -->|"PlanRevised"| MS
  PL -->|"PlanPublished, PlanRevised"| RP
  PL -->|"SolveSucceeded, SolveFailed, RouteHandedOver"| PL
  LO -->|"RouteStarted, OutcomeRecorded"| MS
  LO -->|"OutcomeRecorded, PutBackSuperseded"| LO
  MS -->|"RatingReceived"| MS
  ACC -->|"AccountAnonymized"| FL
  ACC -->|"SessionsRevoked"| ACC
  FL -->|"DriverRetired"| FL
```

| Event | Raised by | Declared listeners (`core/<module>/application/service/`) |
|---|---|---|
| `SolveSucceeded` | `SolveJob.succeed` | `plan/reaction/ApplySolveResult` (Planning) |
| `SolveFailed` | `SolveJob.fail`, `expireIfOverdue` | `plan/reaction/RecordSolveFailure` (Planning) |
| `PlanPublished` | `Plan.publish` | `routerun/TellDriversRoutesReady` (Live Operations); `ComputeBaseline` (Reporting) |
| `PlanRevised` | `Plan.accept`, `handOver`, `removeStopOf`, `reconcileLateOutcome` | `routerun/TellDriversOfRevision` (Live Operations); `message/MessagePlanRevised` (Messaging); `RecomputeBaseline` (Reporting) |
| `RouteHandedOver` | `Plan.handOver` | `plan/route/MoveRosterWithRoute` (Planning); `routerun/TellDriversOfRevision` (Live Operations) |
| `PlanCancelled` | `Plan.cancel` | `routerun/TellDriversPlanCancelled` (Live Operations) |
| `OrderCancelled` | `Order.cancel` | `plan/reaction/RemoveStopOfOrder` (Planning); `message/MessageDeliveryChanged` (Messaging) |
| `OrderRescheduled` | `Order.reschedule` | `plan/reaction/RemoveStopOfOrder` (Planning); `message/MessageDeliveryChanged` (Messaging) |
| `OrderPutBack` | `Order.putBack`, on another date only | `plan/reaction/RemovePutBackOrder` (Planning) |
| `RouteStarted` | `RouteRun.start` | `message/MessageRouteStarted` (Messaging) |
| `OutcomeRecorded` | `Delivery.deliver`, `fail`, `recordOnBehalf`, `confirmHeld` | `delivery/ReconcileOutcome` (Live Operations); `message/MessageOutcome` (Messaging) |
| `PutBackSuperseded` | `Delivery.correct` | `delivery/RemoveSupersededStop` (Live Operations) |
| `RatingReceived` | `RatingRequest.answer` | `AlertLowRating` (Messaging) |
| `DriverRetired` | `Driver.retire` | `RemoveRoleOfRetiredDriver` (Fleet) |
| `SessionsRevoked` | `UserAccount` methods that revoke every session | `session/RevokeSessionsWithAccount` (Access) |
| `AccountAnonymized` | `UserAccount.anonymize` | `AnonymizeDriverWithAccount` (Fleet) |

The reason each is an event and not a call is DL §7.2's last column. Two things to know when you read
the listeners:

- **Delivery is at least once.** A listener must be idempotent by nature, or record
  `(listener, event ID)` through `ProcessedEvents` first (DL
  [§7.3](domain_layer_design.md#73-shape-and-delivery)).
- **`SessionsRevoked` has a second listener that is not declared.** DL §7.2 names a live-updates adapter
  that closes the account's open streams. Adapter classes are absent (the packages are declared, empty), so only Access's own listener exists.

---

## 8. The Python Optimization domain

DL [§4.9](domain_layer_design.md#49-optimization-python) and `optimization-engine.md`. The optimizer's
domain is `optimizer/domain/`: 15 modules plus `__init__.py`, about 4,100 lines, with about 5,300 lines of
tests in `optimizer-test/domain/`. It is pure: import-linter
forbids frameworks, clocks, randomness and I/O in it (`pyproject.toml`).

```mermaid
flowchart LR
  FACT["factory<br/>ProblemFactory"]
  PROB["problem<br/>Problem"]
  DROP["drops<br/>explain_drop"]
  OBJ["objective<br/>cost"]
  FEAS["feasibility<br/>validate"]
  VIEW["problem_view<br/>ProblemView protocol"]
  PART["problem_parts<br/>Orders, Vehicles, Drivers,<br/>ReoptContext, Pin"]
  PEN["penalty<br/>PenaltyScale"]
  TT["travel_time<br/>TravelTimeModel"]
  LEAF["solution, rules, exclusions<br/>import no other domain module"]
  FND["errors, indices, _arrays<br/>input checks"]

  FACT --> PROB
  FACT --> TT
  FACT --> PEN
  FACT --> LEAF
  FACT --> FND
  PROB --> DROP
  PROB --> FEAS
  PROB --> OBJ
  PROB --> PART
  PROB --> PEN
  PROB --> TT
  PROB --> LEAF
  PROB --> FND
  DROP --> FEAS
  DROP --> VIEW
  DROP --> PART
  DROP --> LEAF
  DROP --> FND
  OBJ --> FEAS
  OBJ --> VIEW
  OBJ --> LEAF
  FEAS --> VIEW
  FEAS --> LEAF
  FEAS --> FND
  VIEW --> PART
  VIEW --> PEN
  VIEW --> TT
  VIEW --> LEAF
  PART --> PEN
  PART --> LEAF
  PART --> FND
  PEN --> FND
  TT --> FND
```

An arrow means *imports*. An arrow to a grouped node means the module imports at least one module in the
group. The arrows come from the `import` lines of each file in `optimizer/domain/`.

- **Why `feasibility`, `objective` and `drops` never import `problem`.** `Problem`'s methods delegate to
  them, so if they imported `problem` there would be a cycle. Each types its argument as `ProblemView`, a
  `typing.Protocol` in `problem_view.py`, which `Problem` satisfies by shape. The module docstrings say so
  and import-linter enforces it (ADR-113). `problem` is imported in the domain by `factory` only.
- **`Problem`** (`optimizer/domain/problem.py`, 511 lines) is a frozen dataclass holding the orders,
  vehicles, drivers, the two matrices (`durations`, `distances`), the `TravelTimeModel`, the
  `PenaltyScale`, `drop_penalties`, `day_length`, `breaks_reserved`, `removed_by_dispatcher` and an
  optional `ReoptContext`. Its three rule methods are `validate(solution) -> ValidationReport`,
  `cost(solution, timings) -> Cost` and `explain_drop(order, solution) -> DropReason`. It also has two
  small helpers, `unserved(solution)` and `is_locked(order)`, which the `ProblemView` protocol requires.
- **`ProblemFactory`** (`optimizer/domain/factory.py`, 1,060 lines) has two static methods:
  `daily(...)` builds a `Problem` from the date's orders, depots, vehicles and drivers, and
  `reoptimization(...)` builds one from a live day. The conventions for a re-optimization are decided
  once there and written into the `Problem` (ADR-050, ADR-105). The inputs are small dataclasses in the
  same file: `OrderSpec`, `DepotSpec`, `VehicleSpec`, `DriverSpec`, `LiveVehicleSpec`, `StopFact`,
  `VehiclePosition` and the enums `VehicleState` and `Report`.
- **Values:** `Solution` and `Route` (`solution.py`, no domain imports), `TravelTimeModel` and `TravelBucket`
  (`travel_time.py`), `PenaltyScale` (`penalty.py`), `ValidationReport` and `Violation` (`feasibility.py`),
  `Cost` (`objective.py`), the drop-reason dataclasses (`drops.py`), and the two enums `OrderExclusion`
  and `VehicleExclusion` (`exclusions.py`). `rules.py` holds `RuleId`, one member per rule named after its
  requirement row. Two errors guard input: `InvalidProblemError` and `InvalidSolutionError`.
- **Start reading:** `solution.py` (117), `rules.py` (77), `problem_view.py` (74), `feasibility.py` (611),
  `problem.py`, then `factory.py`. Tests in `optimizer-test/domain/` include `test_solution.py`, `test_problem.py`,
  `test_violation_catalogue.py` (the `RuleId` each crafted case must trigger), `test_objective.py`,
  `test_drops.py`, `test_factory.py`, `test_reoptimization.py`, and `test_properties.py` (Hypothesis).
  `optimizer-test/test_dependency_boundary.py` keeps PyVRP out.

---

## 9. How a use case will drive the domain

Nothing runs this today. This sequence follows DL [§6.1](domain_layer_design.md#61-the-rule),
[§6.2](domain_layer_design.md#62-commands) and [§6.3](domain_layer_design.md#63-outside-calls-happen-before-the-transaction)
for the command "Request a solve" and shows the classes the code declares for it. **Everything outside
`Plan`, `SolveJob` and the kernel is declared, not built.**

The use case is `RequestSolve` (`core/planning/application/service/plan/RequestSolve.java`), and its
transaction is `RequestSolveChange` beside it. The lane's worker is `RunSolveJob`
(`core/planning/application/service/solvejob/`), whose transactions are `RunSolveJobChange`'s. The
listener that ends the solve is `ApplySolveResult` (`service/plan/reaction/`). The repositories are
ports in `core/planning/application/port/out/`. A use case with outside calls makes them itself,
outside any transaction, and its `Change` holds the transaction ([ADR-129](../../ADRs/ADR-129-transactions-start-only-in-the-service-layer.md)).

```mermaid
sequenceDiagram
  participant Web as Web adapter (absent)
  participant UC as RequestSolve (declared)
  participant C as RequestSolveChange (declared)
  participant Opt as RouteOptimizer (port)
  participant PR as PlanRepository
  participant JR as SolveJobRepository
  participant P as Plan
  participant J as SolveJob
  participant W as RunSolveJob (declared)
  participant WC as RunSolveJobChange (declared)
  participant L as ApplySolveResult (declared)
  Web->>UC: request(actor, plan, solver, limit, expected)
  Note over UC,Opt: From M4: outside calls first, before any transaction (DL 6.3). M0 asks no list and runs OR-Tools
  UC->>Opt: solvers()
  Opt-->>UC: the solvers on offer
  UC->>C: apply(...)
  Note over C,J: One transaction: one existing aggregate changes
  C->>PR: get(plan)
  PR-->>C: Plan, loaded whole
  C->>C: StaleVersion.requireCurrent(Plan, id, plan.version(), expected)
  C->>JR: capacityUnderLock(Lane.DAILY)
  JR-->>C: LaneCapacity
  C->>P: requestSolve(jobId, solver, limit, seed, capacity, today, now)
  P->>J: queue(jobId, plan, DAILY, ...)
  J-->>P: SolveJob, queued
  P->>P: status = Solving(SolveBasis)
  P-->>C: the new SolveJob
  C->>PR: save(plan), through VersionedSave, which checks the version again
  C->>JR: add(job)
  Note over C,JR: The audit record is written in this transaction
  C-->>UC: JobId, after the commit
  UC-->>Web: JobId
  Note over W,J: Later, in the daily lane's worker. Each phase is its own transaction in RunSolveJobChange
  W->>WC: claim(Lane.DAILY)
  WC->>JR: claimNext(Lane.DAILY)
  JR-->>WC: the oldest queued SolveJob, locked
  WC->>J: claim(now)
  WC->>JR: save(job)
  WC-->>W: the job, its plan and date
  Note over W,Opt: The matrix is built here, outside any transaction (M3)
  W->>WC: searchStarted(job)
  WC->>J: searchStarted(now)
  WC->>JR: save(job)
  W->>Opt: solve(request), outside any transaction
  Opt-->>W: DailyAnswer
  W->>WC: endDaily(job, request, answer)
  WC->>PR: get(plan)
  WC->>P: locksBrokenBy(solution)
  P-->>WC: no lock broken
  WC->>JR: get(job)
  WC->>J: succeed(ValidatedSolution, now)
  J->>J: raises SolveSucceeded
  WC->>JR: save(job), which stores the event with it
  Note over L,P: The event is delivered after the commit, at least once
  L->>P: applySolveResult(job, ids, now)
  P->>P: status = Ready
```

Reading the diagram:

- **Who supplies what.** `now` and `today` come from the `Clock` port, the job's ID from `IdGenerator`, the
  seed from `Seeds`. The domain makes none of them (DL §3.9).
- **The version check.** `expected` is the plan version the dispatcher saw. The use case compares it with
  the loaded plan's version through `StaleVersion.requireCurrent` and throws `StaleVersion` when they differ;
  `save`, through `VersionedSave`, then catches only a change made between the load and the save. Either way, only the optimizer call is lost (DL §6.3).
- **The worker saves each phase.** `claim` and `searchStarted` are each saved in their own transaction,
  so `SolveJob.expireIfOverdue` judges the job by its current phase's deadline (DL
  [§9.2](domain_layer_design.md#92-solvejob)). A daily result is checked against the plan's locks
  (`Plan.locksBrokenBy`) before `succeed`; a broken lock fails the job instead (`RunSolveJob`'s Javadoc).
- **Two aggregates, one transaction.** The plan changes (`Solving`) and a new `SolveJob` is created. This
  is the allowed case of DL §6.1: one existing aggregate changes, and a new one of the same module is
  created beside it.
- **The plan follows by an event.** `ApplySolveResult` is idempotent by nature: `Plan.applySolveResult`
  ignores a job the plan no longer waits for, an abandoned plan included.

Other commands follow the same shape; DL §6.2 lists each one with what it changes, creates and raises.

---

## 10. Where the code is behind the design

The DL and the accepted ADRs describe a model that the code does not fully have yet. The code says so
itself: the Javadoc of the declared use case or port that needs the missing piece mentions a domain gap. `grep -rn -i "domain gap" src/backend/core/src/main` lists them all at this commit. A milestone's
pull request closes each one. The ones below are the ones a reader is most likely to trip over.

| Module | What is missing in the domain | Written in | Closed by |
|---|---|---|---|
| Orders | `Order.endPutBack`, and a put-back keeping the earlier attempt's date and window ([ADR-121](../../ADRs/ADR-121-the-application-skeletons-review-settles-its-seams-conflicts-retention-and-scoring-inputs.md)) | `orders/application/service/order/EndSupersededPutBackService.java` | M8 |
| Orders | A `Customer` method that changes the customer's fields; `Customer.activeDefaults()` | `orders/application/service/customer/ChangeCustomer.java`, `order/PlaceOrder.java` | M2 |
| Orders | A method that copies an order to another date | `orders/application/service/order/CopyOrdersFromDate.java` | unscheduled |
| Planning | A default solver of each kind in `PlanningSettings` | `planning/application/service/plan/RequestSolve.java` | M4 |
| Planning | `SolveJob.succeed` and `fail` keep no objective and no stats, which NFR-11 records | `planning/application/port/out/solvejob/DailyAnswer.java`, `planning/application/service/solvejob/RunSolveJob.java` | M0 |
| Planning | Retention periods in Planning's domain | `planning/application/service/EraseExpiredLocations.java` | M13 |
| Access | `UserAccount.dropOldLinks`, and a method that voids every open link | `access/application/service/account/credentiallink/DropOldLinks.java`, `access/application/service/session/SecureAfterRestore.java` | M13 |
| Access | `AccessError` codes for an unknown address and a wrong password, which the audit refusal needs (audit only: the answer stays one `SignInRefused`, NFR-145) | `access/application/service/session/SignIn.java` | M5 |
| Messaging | `Rating.isLow()`; a key version on `PhoneHash` and an `OptOutRecord` factory that copies an entry under a new hash | `messaging/application/service/AlertLowRating.java`, `RehashOptOuts.java` | M12 |
| Reporting | `PlannedFigures` has a plain `Distance`, so a plan with an untimed route can't say so | `reporting/application/port/out/PlannedVsActualView.java` | M5 |

Each row's milestone is the roadmap's; the Javadoc names the feature, not the milestone. [`roadmap.md`](../../roadmap.md) holds the dates.

---

## 11. Names that look alike

The same simple name is used for different things in different modules. The type checker tells them
apart; a reader scanning a file does not. The table lists those that a scan of every type name in the
domain and `events/` packages finds.

| Name | Where each one lives |
|---|---|
| `CompanyName` | `access/domain/companyprofile/` and `messaging/domain/`: two types, two rules |
| `RevisionNo` | `planning/domain/` and `liveops/domain/`: Live Operations keeps its own copy (DL §3.3) |
| `WithdrawalCause` | `planning/events/` and `reporting/domain/baseline/`: Reporting maps Planning's into its own |
| `Withdrawal` | `planning/domain/solution/` and `reporting/domain/baseline/` |
| `AccountAnonymized` | an event in `access/events/`, and an error record in `AccessError` |
| `PlanStanding` | `orders/domain/Placement.java` (a nested enum) and `reporting/domain/baseline/PlanStanding.java` |
| `Published` | `Plan.Status.Published` and `BaselineCause.Published` |
| `Capacity`, `Shift`, `TimeWindow`, `DepotHours` | the Fleet and kernel values, and records of the same name nested in Planning's `DropReason` |
| `Delivered`, `Failed`, `Dropped` | variants of `Outcome`, `DispatcherOutcome`, `StopState`, `StopOutcome`, `ReportedOutcome`, `Placement`, `StopWhereabouts`, `MessageState` and `PlannedService` |
| `State`, `Status` | nested in `Customer`, `Order`, `ImportBatch`, `ReoptimizationProposal` (`State`) and `Plan`, `RatingRequest` (`Status`) |

Two uniqueness rules are tested. `ErrorCodeUniquenessTest` fails when two error records derive the same
code, except for three allow-listed codes that several modules share on purpose: `DATE_IN_PAST`,
`RETENTION_NOT_OVER` and `NOT_AN_ACTIVE_DRIVER`. `EventNameUniquenessTest` fails when two events share a
name. Both are in `core-test/architecture/`.

---

## 12. A reading path

Read in this order. Each step names the tests to open beside the code. To run one Java test class, use
`./mvnw -Dtest=<ClassName> test` from `src/backend/core`; to run one Python test, use
`uv run --locked pytest tests/path/test_x.py::test_name` from `src/backend/optimizer` (both from the
component `CLAUDE.md` files).

| Step | Read | With the tests |
|---|---|---|
| 1. The words | DL [§17](domain_layer_design.md#17-ubiquitous-language) and [§3](domain_layer_design.md#3-building-blocks-the-rules-every-module-follows) | none |
| 2. The kernel | `core/shared/AggregateRoot.java`, `DomainError.java`, `RuleViolation.java`, `Version.java`, `EventId.java` | `core-test/shared/AggregateRootTest`, `DomainErrorTest`, `EventIdTest` |
| 3. The smallest aggregate | `core/liveops/domain/alert/Alert.java` and its state | `liveops/domain/alert/AlertTest` |
| 4. An aggregate with an event | `core/fleet/domain/driver/Driver.java` and `core/fleet/events/DriverRetired.java` | `fleet/domain/driver/DriverTest` |
| 5. A sealed lifecycle | `core/access/domain/session/Session.java`, `TokenState.java` | `access/domain/session/SessionLifecycleTest` |
| 6. A rule over a set | `core/orders/domain/DateCapacity.java`, `Order.java` | `orders/domain/OrderTest` |
| 7. A register and a roster | `core/fleet/domain/register/FleetRegister.java`, `roster/DayRoster.java` | `fleet/domain/register/FleetRegisterTest`, `roster/DayRosterTest` |
| 8. Facts from the street | `core/liveops/domain/delivery/Delivery.java`, `routerun/RouteRun.java` | `liveops/domain/delivery/DeliveryLifecycleTest`, `DeliverySequenceTest` |
| 9. Planning | `core/planning/domain/plan/SolveJob.java`, `ReoptimizationProposal.java`, then `Plan.java` by section | `planning/domain/plan/SolveJobTest`, `PlanLifecycleTest`, `PlanSequenceTest` |
| 10. Messaging and reporting | `core/messaging/domain/rating/RatingRequest.java`, `reporting/domain/OnTimeRate.java`, `baseline/Baseline.java` | `messaging/domain/rating/RatingRequestTest`, `reporting/domain/baseline/BaselineTest` |
| 11. The optimizer | `optimizer/domain/solution.py`, `problem_view.py`, `feasibility.py`, `problem.py`, `factory.py` | `optimizer-test/domain/test_problem.py`, `test_violation_catalogue.py` |
| 12. The guards | The rules: `core-test/architecture/DomainPurityRules.java`, `DomainShapeRules.java`, `DomainPackageRules.java`, `ApplicationRules.java`, `PersistenceRules.java`, which `DomainPurityTest`, `DomainShapeTest`, `DomainPackageTest`, `ApplicationTest` and `PersistenceTest` run over the production classes | `DomainPurityRulesTest`, `DomainShapeRulesTest`, `DomainPackageRulesTest`, `ApplicationRulesTest`, `PersistenceRulesTest`: each rule must flag its deliberately wrong fixture in `core-test/archfixture/` |
| 13. What is next | `core/planning/application/service/plan/RequestSolve.java`, then `grep -rn "To Be Implemented." src/backend` | none: bodies throw |

After step 4 you can read any other aggregate with the §4 pattern in hand. Step 9 is the longest: `Plan.java`
is 2,711 lines, so read it by the banner comments and keep the matching test open.

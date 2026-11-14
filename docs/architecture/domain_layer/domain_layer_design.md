# DispatchFlow — Domain layer design

**Status:** Decided, 14 November 2026.

This is the one place that defines DispatchFlow's bounded contexts, aggregates, value objects, events,
domain errors and the rules between them. [`tech_stack.md`](../tech_stack.md) names the tools and
[`roadmap.md`](../../roadmap.md) says when each part is built;
neither repeats it. Where they disagree with this file, this file wins. IDs in brackets point to the
requirements (FR, BR, NFR, UC, Q). `Q-94 #1` means the first numbered sub-question in Q-94's row. Names
in `code` are the names in the code, and §17 defines every one of them.

**The design in seven lines**

1. **Nine bounded contexts** form a one-way dependency graph that Spring Modulith verifies. Each could
   become its own service by changing adapters only.
2. **A transaction changes one existing aggregate.** It may also create new aggregates of the same module
   and write technical records. Everything else follows by an event or a later call ([ADR-063]).
3. **Every business rule has exactly one owner**: an aggregate, or a value object that the one aggregate
   method able to break the rule requires. There are no domain services; §3.7 says why none was needed.
4. **Planning owns intent; Live Operations owns facts.** When a fact contradicts the plan, the fact
   stands, the plan is corrected, and a person is told ([ADR-065]).
5. **An event exists only where it beats a direct call and beats doing nothing** (§7, [ADR-064]).
6. **Feasibility, the objective, travel-time factors and the re-optimization rules exist once**, in the
   Python Optimization domain.
7. **The domain is pure.** It never reads the clock, makes IDs, or touches a framework, so every invariant
   and transition is tested with plain values and no mocks.

---

## 1. Subdomains and bounded contexts

| Context | Subdomain | What it owns | Module |
|---|---|---|---|
| **Planning** | Core | The plan for each delivery date: solving, editing, publishing, re-optimizing and handing over routes. The intent | `planning` |
| **Optimization** | Core | What a feasible, good plan is: the problem, the validator, the objective, the solvers | Python service |
| **Live Operations** | Supporting | What happened on the street: arrivals, outcomes, route runs, positions, projected arrivals, alerts | `liveops` |
| **Customers & Orders** | Supporting | Who receives what: customers and their locations, orders, imports | `orders` |
| **Fleet** | Supporting | What delivers: depots, vehicles, drivers, and who drives what on a date | `fleet` |
| **Recipient Messaging** | Supporting | SMS to recipients, their replies, ratings and opt-outs | `messaging` |
| **Reporting** | Supporting | The daily summary and the comparison with the baseline | `reporting` |
| **Access** | Generic | Accounts, roles, sessions and the company profile | `access` |
| **Audit** | Generic | The append-only audit trail | `audit` |

A boundary is drawn where the language changes or a consistency rule stops. A route in Planning is
intent that a dispatcher edits; in Live Operations a route run is what a driver did, and it must never
contend with a dispatcher's edit (BR-76). The core contexts get the richest model and the most tests; the
generic ones stay as simple as their rules allow. Why customers and orders share a context is in §16.

---

## 2. Context map

### 2.1 Module graph

Every arrow points upstream and means *depends on*. A dashed arrow means *implements a port the upstream
module owns*. No arrow points back, so `ApplicationModules.verify()` passes.

```mermaid
flowchart BT
  OPT[["Optimization service (core)<br/>Python, separate deployable"]]
  ACC["Access (generic)<br/>accounts, roles, sessions"]
  AUD["Audit (generic)"]
  FL["Fleet"]
  CO["Customers & Orders"]
  PL[["Planning (core)"]]
  LO["Live Operations"]
  MS["Recipient Messaging"]
  RP["Reporting"]

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
  PL -->|"RouteOptimizer, FeasibilityChecker<br/>optimizer-api v1"| OPT
  RP -->|BaselinePlanner| OPT
  LO -.->|"implements ExecutionFacts"| PL
  LO -.->|"implements StopProgress"| CO
  PL -.->|"implements OrderPlacement, RecordUsage"| CO
  PL -.->|"implements RecordUsage"| FL
```

Every module depends on the shared kernel. Audit depends on nothing else. Every *other* module also
depends on Audit's `AuditLog` port, and every one of them but Access depends on Access for the acting user,
whom the web adapter passes to each use case as an `Actor` value (account ID and roles).

Audit's `AuditLog` takes shared-kernel IDs (account, session) and never an `Actor`, so Access may depend on
Audit without a cycle ([ADR-040]).

### 2.2 Relationships

| Upstream | Downstream | Relationship | How they talk |
|---|---|---|---|
| Access | Every core module | Open host for identity; the others conform to its account IDs and roles | The `Actor` value; Access's `Accounts` query; for Live Operations also the `SessionContacts` query, which answers whether any session of an account made contact within a window (BR-132) |
| Audit | Every core module | Open host | `AuditLog.record`, in the same transaction as the change (NFR-23) |
| Access | Fleet | Customer and supplier; shared identity: a `Driver` has the ID of the driver's account | Fleet asks `Accounts` whether an account is active and holds the Driver role |
| Fleet | Planning | Customer and supplier | Planning reads the roster and the fleet register through Fleet's API, translating them into its own values; calls `MoveVehicleWithRoute`; implements Fleet's `RecordUsage` |
| Customers & Orders | Planning | Customer and supplier | Planning reads `OrdersForDate`; hears `OrderCancelled` and `OrderRescheduled`; implements `OrderPlacement` and `RecordUsage` |
| Planning | Live Operations | Customer and supplier | Live Operations hears `PlanPublished` and `PlanRevised`; asks `RouteMembership` and `PublishedRoutes`; calls `ReconcileLateOutcome`; implements `ExecutionFacts` |
| Customers & Orders | Live Operations | Customer and supplier | Live Operations reads the order details a report is judged by (leave at the door); implements `StopProgress` |
| Fleet | Live Operations | Customer and supplier | Live Operations reads shifts, for the start deadline and the tracking cut-off |
| Live Operations, Planning, Customers & Orders | Recipient Messaging | Customer and supplier | Messaging hears `RouteStarted`, `OutcomeRecorded`, `OutcomeCorrected`, `PlanRevised`, `OrderCancelled` and `OrderRescheduled`; asks `ArrivalProjections` and the recipient's phone; calls Live Operations' `RaiseAlert` |
| Planning, Live Operations, Customers & Orders | Reporting | Customer and supplier | Reporting hears `PlanPublished` and `PlanRevised`, and reads the read-only views each module publishes |
| Optimization | Planning, Reporting | Customer and supplier with a published language: `optimizer-api` v1 (OpenAPI). The core's optimizer adapter is an anti-corruption layer | HTTP. The optimizer knows nothing about the core |
| OSRM, the geocoders, the SMS provider, Expo push, Amazon SES | The module that uses each | Conformist, behind an anti-corruption adapter | Each sits behind a port named in DispatchFlow's language |

Fleet and Customers & Orders have no relationship. Neither does any pair not in the table.

### 2.3 Rules between contexts

1. **No domain object crosses a boundary.** A module's domain imports only its own domain, its own
   module's `events` package, the shared kernel and pure libraries. What another module's API returns is
   translated into the caller's own values by the caller's application layer. Contexts share IDs, events
   and API calls, never entities.
2. **An upstream module never imports a downstream type.** When it needs something from downstream, it
   owns a port that the downstream module implements (Planning asks `ExecutionFacts` whether a route has
   started), or the downstream module calls the upstream API from a listener on its own event, so a failed
   call is retried (§8).
3. **Each module owns its tables, in its own PostgreSQL schema.** No SQL reads another module's schema
   ([ADR-041]). Reporting reads read-only views that the owning module publishes as part of its API.
4. **A module's contract is three packages**, each a `@NamedInterface`: `api` (what other modules call),
   `events` (what they hear) and `spi` (the ports it owns for them to implement). Spring Modulith fails
   the build on any other dependency.
5. **The shared kernel is tiny.** It holds the typed IDs (UUIDv7) of every aggregate and every entity
   another context names (§3.1), and nine values that mean the same everywhere: `DeliveryDate`,
   `IsraelDateTime`, `DaySeconds`, `TimeWindow`, `GeoPoint`, `Weight`, `Volume`, `Priority` and
   `PhoneNumber`. It also holds the building blocks every module's domain uses: `DomainError`,
   `RuleViolation(s)`, `StoredStateInvalid`, `Version`, `AggregateRoot`, `DomainEvent`, `EventId` and
   `FreshIds` ([ADR-097]). Nothing with behaviour specific to one context goes there, and a change to it
   needs the agreement of every module's owner.

**Splitting a module into a service later** changes adapters only: its API calls become HTTP clients
generated from a new contract, its events go through a message broker (Spring Modulith can externalize
them), the ports it implements become endpoints, and its schema moves to its own database. Because no
domain object crossed the boundary, no domain class changes. The one shared write is the audit record,
written in the same transaction; a split-off module would write it through an outbox instead.

---
## 3. Building blocks: the rules every module follows

### 3.1 Aggregates

- **An aggregate is drawn around a consistency rule, not around a noun or a table.** It holds exactly
  what one or more invariants must see together, and nothing more. §4 names each aggregate's invariants;
  an aggregate with none would be a record (§3.2).
- **A transaction changes one existing aggregate** and saves it with a version check (BR-76). §6 gives the
  two exceptions: creating new aggregates of the same module, and writing technical records.
- **Inside an aggregate, the root holds its entities and values directly**, as object references:
  `Plan` holds its `Route`s, each `Route` holds its `PlannedStop`s. Nothing outside the aggregate changes
  them, and the root never exposes a mutable collection (§3.10).
- **Between aggregates, only IDs.** `Delivery` holds a `StopId`, `Route` holds a `VehicleId`, never the
  object. An ID may name an entity inside another aggregate when the business refers to that thing
  directly (a stop, a route, a vehicle, a depot). Such an ID is part of the owner's published language:
  issued once, never reused, and stable while the entity moves inside its aggregate (a stop keeps its ID
  when it moves to another route). The holder only ever reaches that entity through its root's API, and
  never changes it.
- **An aggregate is loaded whole**, so its invariants are always checked against everything they cover.
  That is why each one stays small; §4 notes its size and its writers.

### 3.2 Records

Some things the business keeps have no rule between their entries: positions, audit records, inbound SMS
replies and opt-outs. They are **records**, not aggregates: immutable, appended with a plain insert, with
no version, so fifteen phones sending positions never contend. Each kind is written through an
append-only port (`PositionLog`, `AuditLog`, `ReplyLog`, `OptOutList`). The rule about whether to accept
one belongs to an aggregate that is not the record: whether a position is kept is `RouteRun`'s rule
(FR-328).

### 3.3 Value objects

A value object is an immutable Java record (a frozen dataclass in Python) that checks itself in its
constructor, so an invalid one can't exist. Equality is by value. No signature in the domain takes a raw
`String`, `int`, `UUID` or `Instant` for a business concept.

| Value | Context | What it holds and checks | Error |
|---|---|---|---|
| Typed IDs (`PlanId`, `StopId`, `OrderId`, …) | Shared | A UUIDv7 that names one kind of thing; one can't be passed for another | — |
| `DeliveryDate` | Shared | The calendar day a plan, its orders and its roster are for, in years 1 to 9999; knows whether it is before a given day | `DateOutOfRange` |
| `IsraelDateTime` | Shared | A local time in `Asia/Jerusalem`; refuses a time in the skipped hour, reads a repeated hour as its first occurrence (BR-31, BR-111) | `NonexistentLocalTime` |
| `DaySeconds` | Shared | Elapsed seconds since the date's local midnight, within that day's 23, 24 or 25 hours (§10) | `OutsideTheDay` |
| `TimeWindow` | Shared | A start before an end, both on one date (BR-35) | `InvalidTimeWindow` |
| `GeoPoint` | Shared | Latitude and longitude in range | `InvalidCoordinates` |
| `Weight`, `Volume` | Shared | Kilograms and litres, present and positive (BR-50), with at most two decimals and the digits their columns hold ([ADR-061]) | `NonPositiveQuantity`, `QuantityOutOfRange` |
| `Priority` | Shared | 1 to 5, default 3 (BR-53) | `PriorityOutOfRange` |
| `PhoneNumber` | Shared | E.164, parsed with region IL from digits only; knows whether it is an Israeli mobile (FR-377, FR-378). A stored number is checked as E.164 only, so a newer phone-number library never refuses it | `InvalidPhoneNumber` |
| `EmailAddress` | Access | The canonical form one account is keyed by (BR-45) | `InvalidEmail` |
| `RoleSet` | Access | A set of Admin, Dispatcher and Driver; `can(capability)` is the permission matrix (NFR-85) | — |
| `NewPassword` | Access | At least 8 characters and not on the bundled list of the 3,000 most common (NFR-95) | `PasswordTooShort`, `PasswordTooCommon` |
| `Generation` | Access | A counter that only grows; tokens issued under an older one are refused (NFR-177) | — |
| `ActiveAdmins` | Access | The active Admin accounts, read under row locks; refuses a change that leaves fewer than two (BR-19) | `TooFewAdmins` |
| `Capacity`, `Plate`, `Shift`, `DepotHours`, `WeeklyShifts` | Fleet | A vehicle's weight and volume limits; a normalized plate; a start before an end | `InvalidPlate`, `InvalidShift` |
| `Address`, `NormalizedAddress` | Customers & Orders | The address as typed, and the form addresses are matched by (FR-310, Q-94 #4) | `BlankAddress` |
| `CustomerMatch` | Customers & Orders | None, one, or several active customers for a normalized address; several can't be used (FR-310 to FR-312) | `AmbiguousAddress` |
| `Location` | Customers & Orders | None, geocoded (with its quality), or placed by hand; a hand-placed location always wins (BR-13, FR-215) | — |
| `ServiceArea` | Customers & Orders | The company's area, from the road-data build; `contains(point)` (FR-320) | — |
| `ServiceTime`, `Attempt`, `ExternalReference` | Customers & Orders | Positive seconds (BR-124); 1 or more (FR-258); a non-blank reference | `NonPositiveServiceTime` |
| `DateCapacity` | Customers & Orders | A date's uncancelled orders, read under the date's lock, and the limit (BR-101, BR-113) | `DateFull` |
| `StopState` | Customers & Orders | What has happened at an order's stop: nothing, arrived, or an outcome (BR-92) | — |
| `OrderStatus` | Customers & Orders | Derived from three owners (§9.6) | — |
| `SolveBasis`, `ProposalBasis` | Planning | What a result was computed from (§17, *basis*) | — |
| `LaneCapacity` | Planning | A lane's queued jobs, read under the lane's lock, and the limit of 5 (BR-112) | `SolveQueueFull` |
| `Violation`, `DroppedOrder`, `DropReason`, `Pin`, `RouteLock`, `PlanRevision` | Planning | A broken rule with its references; an unserved order with its reason (FR-144); an order fixed to a vehicle; a fixed route; a published snapshot | — |
| `ValidatedSolution`, `Evaluation` | Planning | Solutions and checks only the optimizer adapter can build (§13); an evaluation names the plan version it checked | `StaleEvaluation` |
| `PublishingChecks`, `DriverStanding`, `ChangesSinceBasis`, `StartedRoutes` | Planning | The facts from other modules that `Plan`'s methods judge (§4.4) | — |
| `CorrectedTime` | Live Operations | A report's device time moved by the phone's clock offset, and cut to the server's receipt time if it would be later (BR-44, BR-110, BR-114) | — |
| `Entitlement` | Live Operations | Planning's answer that a stop was on a driver's route in the revision in force at a time (BR-110) | — |
| `HoldCheck` | Live Operations | Where the stop is now (on the reporting driver's route, on another's, or its order cancelled), from Planning's `RouteMembership`, and whether any session of the reporting account made contact between 2 minutes after the corrected time and 2 minutes before the report arrived, from Access's `SessionContacts`. `holds(correctedTime, receivedAt)` is true when the report would move the stop or override a cancellation and such a contact exists (BR-132). It doesn't cover a stop that already has a held report: `Delivery` holds a further report for it without asking (BR-136) | — |
| `Proof`, `FailureReason`, `Outcome` | Live Operations | A signature or a photo (FR-367); a reason from the company's list; delivered or failed with its details | — |
| `ArrivalProjection`, `StartDeadline`, `TrackingWindow` | Live Operations | Projected arrivals (§10.4); shift start plus allowance plus 15 minutes (FR-101); start to 1 hour after shift end (FR-328) | — |
| `MessageText`, `Reply`, `Rating`, `PhoneHash`, `DailySendCap` | Messaging | A text starting with the company name, within 70 characters (FR-382); a parsed reply (FR-363, FR-376); 1 to 10; a keyed hash (NFR-202); the day's accepted messages and the cap (NFR-196) | `RatingOutOfRange` |
| `OnTimeRate` | Reporting | Delivered stops reached by their window's end over delivered stops; *unavailable* when there are none (FR-264, FR-265, FR-340, FR-370) | — |
| `Problem`, `Solution`, `TravelTimeModel`, `PenaltyScale`, `ValidationReport` | Optimization | Everything one solve needs, and what comes back (§4.9) | — |

DispatchFlow has no money in its domain: the objective's terms are in seconds, not shekels.

### 3.4 States and transitions

Every lifecycle is a sealed interface whose records are the states (`Plan.Status` is `Draft`,
`Solving(basis)`, `Ready`, `Published(revision)`, `Completed`, `Cancelled`, `Abandoned`). Each transition is one
method on the root, in business language, that checks the current state first and refuses with a named
error (`PlanNotReady`). A `switch` over a sealed state must cover every state, so adding a state makes
the compiler point at every place that has to decide about it. That is what sealed types guarantee; they
don't make an illegal call fail to compile. So every refused transition also has a test (§14). §9 draws
each lifecycle.

### 3.5 Factories

An aggregate is created only by a static factory on its root (`Plan.open`, `Order.place`,
`Delivery.firstFact`) that checks every invariant of a new one. The new ID and `now` are parameters;
the use case gets them from the `IdGenerator` and `Clock` ports. Repositories rebuild aggregates through a
separate `restore` factory that checks the structure again, so even a damaged row can't become an invalid
object; only persistence adapters may call `restore`. Python's `ProblemFactory` builds a `Problem` from a
request (§4.9).

### 3.6 Repositories

- **One repository per aggregate root**, as a secondary port in the module's `application` package,
  implemented with jOOQ in `adapter/out/persistence`. Records use their append-only logs instead (§3.2).
- **It speaks in whole aggregates**: `get(id)`, the lookups use cases need (`PlanRepository.forDate`),
  `add(newAggregate)` and `save(aggregate)`. `save` updates with `WHERE version = ?` and fails with
  `STALE_VERSION` when nothing matched (BR-76).
- **It has no queries for screens.** Screens and reports read read models (CQRS-lite), so a repository
  never grows a method per screen and never returns half an aggregate.
- **Events leave with the save.** An aggregate records the events its methods raise; the use case
  publishes them when it saves, in the same transaction, and Spring Modulith stores them with the change.

### 3.7 Domain services: none

A domain service is the right home for an operation that belongs to the domain but to no aggregate or
value object. Each candidate in this design found an owner:

- **A rule over one aggregate's own state goes on that aggregate**, with the facts it can't see passed in
  as values. Publishing, accepting a proposal and handing over a route are `Plan`'s methods.
- **A rule that defines a value goes on that value**: order status (`OrderStatus.derive`), an address
  match (`CustomerMatch`), a projected arrival (`ArrivalProjection`), the on-time rate (`OnTimeRate`), a
  message's text (`MessageText`), a solution's feasibility, cost and drop reasons (`Problem`'s methods).
- **A rule over a set of aggregates goes on an aggregate that holds the set**, when the set is small and
  changes together (`FleetRegister`, `DayRoster`, `StopMessages`). Otherwise it goes on a **value that is
  a snapshot of the set**, read under a lock, that the one aggregate method able to break the rule
  requires as a parameter (`ActiveAdmins`, `DateCapacity`, `LaneCapacity`); §5 lists them.

A domain service would be added for an operation that coordinates several domain objects and belongs to
none of them. None exists in the current requirements.

### 3.8 Domain errors

- **Every refusal is a named error** in business language, a record of the module's sealed error type
  (`PlanningError`), carrying what the message needs (`DriverAlreadyHasRoute(driver, route)`). §4 names
  the error of every invariant.
- **A method throws `RuleViolation` with one error, or `RuleViolations` with all of them** where the
  requirements say to list everything at once (publishing, BR-72; an import, every row).
- **Each error has a stable code** (`DRIVER_ALREADY_HAS_ROUTE`) that the web adapter puts in an RFC 9457
  response and both apps translate (NFR-69, NFR-144). A test fails when an error has no translation.
- **No generic errors.** Domain code never throws `IllegalArgumentException` or `IllegalStateException`
  for a business rule. Null and programming errors are caught by NullAway at compile time.

### 3.9 Purity

Six ArchUnit tests keep the Java domain pure ([ADR-040]); import-linter does the same in Python.

1. A module's domain imports only the JDK, the shared kernel, its own domain, its own module's `events`
   package (§7.3), JSpecify annotations and pure libraries (libphonenumber, JTS). Not Spring, jOOQ, Jackson, Jakarta or `java.net.http`, not any
   `application` or `adapter` package, and not another module.
2. No clock: domain code never calls `Instant.now()`, `LocalDate.now()`, `Clock.system…()` or
   `System.currentTimeMillis()`. `now` and `today` are parameters.
3. No ID generation: never `UUID.randomUUID()`. Factories take the new ID.
4. No randomness, I/O or logging.
5. No `new IllegalArgumentException` or `new IllegalStateException` (§3.8).
6. Only persistence adapters call `restore` (§3.5), and the factories of the snapshots read under a
   lock (`ActiveAdmins`, `DateCapacity`, `LaneCapacity`, §5).

Password hashing, token signing and hashing with a secret key are adapters behind ports (`PasswordHasher`,
`TokenSigner`, `Hmac`); the domain receives their results as values (`PasswordHash`, `PhoneHash`).

### 3.10 A rich model

- **Behaviour lives where the data is**, in methods named for what the business does: `publish`,
  `handOver`, `putBack`, `reconcileLateOutcome`, never `setStatus` or `setStops`.
- **No setters, and no mutable collection leaves an aggregate.** Getters return unmodifiable views or
  copies, and value objects copy their lists in their constructors (`List.copyOf`).
- **Tell, don't ask.** A use case calls the method that decides; it never reads an aggregate's state,
  decides in the use case, and writes the result back.
- **Queries on aggregates answer questions the business asks** (`DayRoster.checkAssignable`,
  `RouteRun.acceptsPositionAt`) and change nothing.

---
## 4. Aggregates, context by context

Each context lists its aggregates, then every invariant with its single owner and its named error. An
error of "—" means the rule can't be broken from outside: the owner simply does the right thing (it
ignores a stale result, or revokes tokens). Invariants checked by a value object are owned by that value,
and every aggregate that holds one inherits them.

### 4.1 Access

| Aggregate | Holds | Operations | Size and writers |
|---|---|---|---|
| `CompanyProfile` | The company's name and language (FR-125) | `rename`, `changeLanguage` | One; Admins |
| `UserAccount` | `EmailAddress`, `RoleSet`, `PasswordHash`, state (*active*, *deactivated* or *anonymized*), language, token `Generation`, and its credential links (entities: kind, token hash, expiry, *open*, *spent* or *voided*) | `open`, `changeRoles`, `deactivate`, `reactivate`, `changePassword`, `changeEmail`, `issueLink`, `spendLink`, `anonymize` | One per person; the person and Admins, rarely |
| `Session` | Its account, the account generation it began under, its own `Generation`, state (*live* or *revoked*), the last step-up, its contact times (kept 14 days, at most one a minute; NFR-280), and its chain of refresh tokens (entities: hash, issue time, exchange time, successor) | `open`, `exchange`, `confirmPassword`, `recordContact`, `revoke` | One per signed-in device; that device |

| Invariant | Owner | Error |
|---|---|---|
| At least two active Admins (BR-19) | `UserAccount.changeRoles` and `deactivate`, which require `ActiveAdmins` | `TooFewAdmins` |
| One account per email (BR-45) | `UserAccount.open`, backed by a unique index (§5) | `EmailInUse` |
| A password is at least 8 characters and not a common one (NFR-95) | `NewPassword` | `PasswordTooShort`, `PasswordTooCommon` |
| A role, password or email change, a deactivation, or losing the last role revokes every token and session at once (NFR-89, NFR-90, NFR-177, BR-123) | `UserAccount`: each of those methods increments its `Generation` | — |
| A deactivated account, or one with no role, can't sign in | `UserAccount.allowsSignIn` | `AccountDeactivated`, `AccountHasNoRole` |
| Every deactivated account, whatever its roles, is anonymized a year after deactivation. The record stays, because plans and audit records refer to it (NFR-258, NFR-260, [ADR-075]) | `UserAccount.anonymize(now)` | `RetentionNotOver` |
| A newer link voids older links of its kind; a password change voids all of them (NFR-224) | `UserAccount.issueLink`, `changePassword` | — |
| A link is spent once, before it expires (NFR-211) | `UserAccount.spendLink` | `LinkExpired`, `LinkAlreadyUsed`, `LinkVoided` |
| A refresh token is exchanged once; a second exchange within 30 s gets the same successor; any later reuse revokes the session (NFR-88, NFR-178) | `Session.exchange` | `RefreshRefused` (the same answer for every refusal, NFR-92) |
| A session ends 14 days after it opened (NFR-87) | `Session.exchange` | `RefreshRefused` |
| Confirming the password refuses the session's older tokens (NFR-214) | `Session.confirmPassword`: its `Generation` + 1 | — |
| Admin-only operations need the password confirmed in the last 5 minutes (NFR-214, NFR-227) | `Session.requireFreshStepUp` | `StepUpRequired` |
| A session keeps its contact times for 14 days, as long as a late report can arrive (NFR-235, NFR-280): every request from it other than a token refresh or an outbox send, at most one a minute, and only for a session of an account with the Driver role. One time is not enough, because a later request would hide an earlier one inside the window. The endpoint decides which requests count, never a header the client sets (BR-132). The data inventory lists them (NFR-72, NFR-280) | `Session.recordContact(now)`, called only for an account with the Driver role. It appends a time unless the last one is under a minute old, and never moves the `Version` or the `Generation`, so it can't make a refresh fail `STALE_VERSION`. Times older than 14 days are dropped | — |

**In the Alpha:** seeded accounts, sign-in and `RoleSet.can`. Sessions in full, links and step-up come
in Semester B.

### 4.2 Fleet

| Aggregate | Holds | Operations | Size and writers |
|---|---|---|---|
| `FleetRegister` | The company's depots (entities: name, `GeoPoint`, `DepotHours`, retirement) and vehicles (entities: name, `Plate`, `Capacity`, start and end depot, retirement) | `addDepot`, `changeDepotHours`, `retireDepot`, `addVehicle`, `changeVehicle`, `retireVehicle`, `reactivateVehicle` | One; at most 15 active vehicles plus retired ones; Admins, rarely |
| `Driver` | The person's ID (the account's), name, phone, `WeeklyShifts`, retirement | `register`, `changeWeeklyShifts`, `retire`, `anonymize` | One per driver; Admins and dispatchers, rarely |
| `DayRoster` | For one delivery date: each driver's `Shift` and availability, the vehicle each drives, and vehicles marked unavailable | `open`, `assign`, `unassign`, `markDriverUnavailable`, `markVehicleUnavailable`, `changeShift`, `moveVehicleWithRoute`, and the query `checkAssignable` | One per date, 15 entries; dispatchers and the hand-over listener |

| Invariant | Owner | Error |
|---|---|---|
| At most 15 active vehicles (BR-36) | `FleetRegister.addVehicle`, `reactivateVehicle` | `FleetFull` |
| Active vehicles have unique names and plates (BR-48) | `FleetRegister` | `DuplicateVehicleName`, `DuplicatePlate` |
| A vehicle starts and ends at active depots (BR-40) | `FleetRegister.addVehicle`, `changeVehicle`, `retireDepot` | `UnknownDepot`, `DepotInUse` |
| Records are retired, never deleted (BR-43) | Every removal is a `retire` method | — |
| A vehicle, depot or driver that a plan not yet completed uses can't be retired | `FleetRegister.retire…` and `Driver.retire`, which require the `InUse` answer read through `RecordUsage` | `VehicleInUse`, `DepotInUse`, `DriverInUse` |
| A driver is registered only for an active account with the Driver role | `Driver.register`, which requires the account's standing | `NotAnActiveDriver` |
| A retired driver is anonymized a year after retirement (BR-98, NFR-152) | `Driver.anonymize(now)` | `RetentionNotOver` |
| On a date, a driver drives at most one vehicle and a vehicle has at most one driver (FR-54, FR-190) | `DayRoster.assign`, `moveVehicleWithRoute` | `VehicleAlreadyAssigned`, `DriverAlreadyHasVehicle` |
| Only active vehicles and active drivers are rostered | `DayRoster.assign`, which requires their standing | `VehicleNotActive`, `NotAnActiveDriver` |
| A shift ends after it starts | `Shift` | `InvalidShift` |

`FleetRegister` holds the set because the set is small by rule (BR-36), changes rarely, and two of its
rules are about the whole set. That replaces a set-wide check and an advisory lock with an ordinary
version check.

**In the Alpha:** the register, drivers and rosters, without weekly shifts (rosters are seeded).

### 4.3 Customers & Orders

| Aggregate | Holds | Operations | Size and writers |
|---|---|---|---|
| `Customer` | Name, `PhoneNumber`, `Address` and its `NormalizedAddress`, `Location`, address flags (FR-63), default window and service time, state (*active*, *retired* or *anonymized*) | `register`, `changeAddress`, `recordGeocode`, `placeLocation`, `retire`, `anonymize` | One per address; dispatchers, imports, geocoding results |
| `Order` | Customer, `DeliveryDate`, `TimeWindow`, `Weight`, `Volume`, `ServiceTime`, `Priority`, leave-at-door, `ExternalReference`, recipient, notes, `Attempt`, state (*active* or *cancelled*), last change time | `place`, `change`, `reschedule`, `cancel`, `putBack` | One per order; dispatchers and imports |
| `ImportBatch` | One uploaded file: its hash and encoding, every row with its errors, state (*previewed*, *confirmed*, *discarded* or *expired*) | `preview`, `confirm`, `discard`, `expire` | One per upload; the dispatcher who uploaded it |

| Invariant | Owner | Error |
|---|---|---|
| An order's date isn't in the past (BR-116) | `Order.place`, `reschedule`, `putBack` | `DateInPast` |
| An order's window is valid and on its date (BR-35) | `TimeWindow`; `Order.place`, `change` | `InvalidTimeWindow`, `WindowNotOnDate` |
| Weight and volume are present and positive (BR-50); priority is 1 to 5, blank meaning 3 (BR-53); service time is positive, or the customer's default (BR-124) | `Weight`, `Volume`, `Priority`, `ServiceTime`; `Order.place` | `NonPositiveQuantity`, `PriorityOutOfRange`, `NonPositiveServiceTime` |
| A reference is unique on its date (FR-211) | `Order.place`; `ImportBatch` within the file; a unique index (§5) | `DuplicateOrderReference` |
| A date holds at most 200 uncancelled orders (BR-101, BR-113) | `Order.place`, `reschedule`, `putBack` and `ImportBatch.confirm`, which require a `DateCapacity` with room | `DateFull` |
| An order can't be changed, rescheduled or cancelled once its stop has a fact (BR-92) | `Order`, given the `StopState` read through `StopProgress` | `StopAlreadyHasFact` |
| A cancelled order stays cancelled | `Order` | `OrderAlreadyCancelled` |
| Only a failed attempt is put back, and putting it back starts the next attempt (FR-258) | `Order.putBack`, given its `OrderStatus` | `OnlyFailedOrdersArePutBack` |
| A hand-placed location always wins over geocoding (BR-13, FR-215) | `Customer.recordGeocode` ignores a result once a location was placed by hand | — |
| A hand-placed location is inside the service area (FR-320) | `Customer.placeLocation`, given the `ServiceArea` | `OutsideServiceArea` |
| An address matching several active customers can't be used (FR-310 to FR-312) | `CustomerMatch` | `AmbiguousAddress` |
| A customer that a plan not yet completed uses can't be retired (BR-43) | `Customer.retire`, given `InUse` | `CustomerInUse` |
| A customer is anonymized a year after retirement, or a year after the last order if never retired (BR-98, NFR-257, [ADR-075]) | `Customer.anonymize(now)` | `RetentionNotOver` |
| An import writes every row or none, and lists every error at once | `ImportBatch.preview` and `confirm`, which run every row through the `Customer` and `Order` factories | `ImportHasErrors(rows)` |
| A file read as Windows-1255 is confirmed only with explicit confirmation (FR-313) | `ImportBatch.confirm` | `EncodingNotConfirmed` |
| A preview is confirmed once, before it expires | `ImportBatch.confirm` | `ImportAlreadyConfirmed`, `ImportExpired` |
| A discarded or expired preview keeps none of its rows | `ImportBatch.discard`, `expire` | — |

`ImportBatch.confirm` checks every row again against the current data (customers, references and date
capacity may have changed since the preview) and returns the new customers and orders, which the use case
saves with the batch in one transaction (§6.1).

**In the Alpha:** UTF-8 CSV imports matched to the seeded customers, which already have coordinates
(FR-310). A new address has no coordinates until geocoding arrives, so its order drops as *missing
location* (BR-14).

### 4.4 Planning

| Aggregate | Holds | Operations | Size and writers |
|---|---|---|---|
| `Plan` | For one delivery date: state, routes (entities: vehicle, driver, label, colour, lock, check state, withdrawal, and their stops as `PlannedStop` entities), dropped orders, pins, open violations, the solve basis, the undo and redo journal, the current revision number, and a pending `PlanRevision` snapshot | `open`, `requestSolve`, `applySolveResult`, `solveFailed`, `moveStop`, `pin`, `unpin`, `lockRoute`, `unlockRoute`, `undo`, `redo`, `publish`, `accept`, `handOver`, `removeStopOf`, `reconcileLateOutcome`, `recordRouteCheck`, `cancel`, `complete`, `abandon` | One per date, up to 15 routes and 200 stops; dispatchers and six automatic writers (§11) |
| `SolveJob` | Plan, lane, solver, limit, seed, phase, the time it was requested, and its two deadlines | `queue`, `claim`, `searchStarted`, `succeed`, `fail`, `expireIfOverdue` | One per solve; its worker, the watchdog, startup recovery and the midnight job |
| `ReoptimizationProposal` | Plan, job, `ProposalBasis` (revision and fact time), the new routes, the changes per driver, withdrawn vehicles, the objective by term, state (*ready* or *declined*) | `decline` | One per finished re-optimization; written once |
| `PlanningSettings` | Travel buckets and buffer (FR-321, FR-322), default limits, the fixed vehicle cost (FR-21) | `change…` | One; Admins |

`Plan` is the one large aggregate, deliberately. Its rules cover the whole date: each order is served or
dropped exactly once (BR-07), each vehicle and driver has at most one route (BR-39), a move takes a stop
off one route and onto another atomically, and a revision is a consistent snapshot of every route
(BR-110). Splitting it per route would turn each of these into a rule across aggregates. Its writers are
few (dispatchers and six automatic writers, at most a few writes a minute), and the high-frequency writers, the
drivers' phones, are kept out of it on purpose: their facts live in Live Operations.

| Invariant | Owner | Error |
|---|---|---|
| One plan per date (BR-34) | `Plan.open`, backed by a partial unique index (§5) | `PlanAlreadyExists` |
| A plan for a past date isn't solved or changed (BR-118) | `Plan`, given `today` | `DateInPast` |
| The state moves only along BR-12's transitions (§9.1) | Each `Plan` method | `PlanNotDraftOrReady`, `PlanNotReady`, `PlanNotPublished` |
| Each order is served or dropped exactly once (BR-07) | `Plan`, backed by unique (plan, order, attempt) | `OrderAlreadyInPlan` |
| Each vehicle and each driver has at most one route (BR-39) | `Plan.applySolveResult`, `accept`, `handOver` | `VehicleAlreadyHasRoute`, `DriverAlreadyHasRoute` |
| Only a validated solution replaces the routes (BR-15) | `Plan.applySolveResult`, which takes only a `ValidatedSolution` | — (by type) |
| Only the plan's own basis job may end its solve; a result from any other job is ignored (BR-59) | `Plan.applySolveResult`, `solveFailed` | — |
| A failed solve keeps the routes the plan had, or returns it to *draft* (BR-57, BR-59) | `Plan.solveFailed` | — |
| Every edit is judged by the validator on the version it was made from; its violations stay open until fixed (FR-23, FR-233) | `Plan.moveStop`, `pin`, `undo`, `redo`, which require an `Evaluation` of the current version | `StaleEvaluation` |
| A locked route doesn't change by an edit or a solve (FR-147) | `Plan` | `RouteLocked` |
| A pinned order is served by its vehicle or dropped (FR-91, FR-228) | `Plan.pin`; the validator | — |
| Publishing passes BR-72's four checks in order: vehicles and drivers (BR-20, BR-63), every order accounted for (BR-22, BR-71), no open violation (BR-17), and the optimizer's re-check of this version (BR-21, BR-67 to BR-70). It stops at the first step that refuses and lists every refusal at that step | `Plan.publish`, given `PublishingChecks` | `PublishingRefused(step, refusals)`: `RouteNotStaffed`, `OrderNotAccounted`, `OrderChangedSinceSolve`, `OpenViolation`, `RecheckFailed`, `RecheckUnavailable` |
| A published plan changes only by a new revision, which records its cause (BR-77, FR-104) | Every change to a published `Plan` | — |
| A proposal is accepted only for this plan, undeclined, if nothing it moves or drops changed since its basis, and if the vehicles it keeps driving are still available (BR-25, BR-64, BR-65, BR-82, BR-83) | `Plan.accept`, given `ChangesSinceBasis` | `ProposalForAnotherPlan`, `ProposalDeclined`, `ProposalOutdated(changes)`, `VehicleUnavailable` |
| A route is handed over only to an active, available driver with the Driver role and no other route that date; a break in their shift needs the dispatcher's confirmation; reported stops keep their deliveries (FR-302 to FR-304). When a confirmed hand-over puts the route past the shift, the confirmed return time becomes that day's shift end, and a re-optimization can't stretch it (FR-304, FR-394, Q-94 #3) | `Plan.handOver`, given `DriverStanding` | `NotAnActiveDriver`, `DriverNotAvailable`, `DriverAlreadyHasRoute`, `BreakNotConfirmed` |
| A late fact puts its stop on the route that served it (BR-89; §8) | `Plan.reconcileLateOutcome` | — |
| A plan is cancelled only while no route has started | `Plan.cancel`, given `StartedRoutes` | `RouteAlreadyStarted` |
| A published plan completes at the midnight after its date (BR-102) | `Plan.complete(today)` | `DateNotOver` |
| A plan that is still unpublished (*draft*, *solving* or *ready*) is abandoned at the midnight after its date, and is then final and read-only (BR-12, BR-102, BR-127, [ADR-053]) | `Plan.abandon(today)` | `DateNotOver` |
| Every queued or running solve job of a date that has passed is failed with the reason *its date passed*, before its plan completes or is abandoned (BR-128). A `SolveFailed` that reaches an *abandoned* plan changes nothing: it is the one named exception to §9's rule that a method called from another state is refused ([ADR-053]) | `SolveJob.fail(reason, now)`, called by the midnight job; `Plan.solveFailed` | — |
| One running re-optimization per plan (BR-38) | `SolveJob.queue`, backed by a partial unique index | `ReoptimizationAlreadyRunning` |
| At most 5 jobs queued in a lane (BR-112) | `SolveJob.queue`, which requires a `LaneCapacity` with room | `SolveQueueFull` |
| A job waits at most 5 minutes in its queue (BR-112) and runs at most its limit plus 30 s (BR-32) | `SolveJob.expireIfOverdue(now)` | — (it fails the job as *queued too long* or *overran*) |
| A job that has ended never changes again | `SolveJob` | `JobAlreadyEnded` |
| A proposal never changes, except to be declined once | `ReoptimizationProposal` | `ProposalAlreadyDeclined` |

```mermaid
classDiagram
  direction LR

  class Plan {
    <<AggregateRoot>>
    PlanId id
    DeliveryDate date
    Status status
    Version version
    RevisionNo currentRevision
    List~Route~ routes
    List~DroppedOrder~ dropped
    List~Violation~ openViolations
    SolveBasis basis
    EditJournal journal
    requestSolve(jobId, solver, limit, LaneCapacity, today, now) SolveJob
    applySolveResult(jobId, ValidatedSolution)
    solveFailed(jobId, reason)
    moveStop(stopId, routeId, position, Evaluation)
    publish(PublishingChecks, now)
    accept(ProposalSnapshot, ChangesSinceBasis, now)
    handOver(routeId, DriverStanding, breakConfirmed, now)
    removeStopOf(orderId, attempt, cause, now)
    reconcileLateOutcome(LateFact, now)
    cancel(StartedRoutes, now)
    complete(today)
    abandon(today)
  }
  class Route {
    <<Entity>>
    RouteId id
    VehicleId vehicle
    AccountId driver
    RouteLock lock
    CheckState check
    Withdrawal withdrawal
    List~PlannedStop~ stops
  }
  class PlannedStop {
    <<Entity>>
    StopId id
    OrderId order
    Attempt attempt
    DaySeconds plannedArrival
    TravelLeg leg
    LateOutcome lateOutcome
  }
  class PlanRevision {
    <<ValueObject>>
    RevisionNo no
    Instant effectiveAt
    Cause cause
    RoutesSnapshot routes
  }
  class SolveJob {
    <<AggregateRoot>>
    JobId id
    PlanId plan
    Lane lane
    Phase phase
    Deadlines deadlines
    claim(now)
    succeed(result, now) ReoptimizationProposal
    fail(reason, now)
    expireIfOverdue(now)
  }
  class ReoptimizationProposal {
    <<AggregateRoot>>
    ProposalId id
    PlanId plan
    ProposalBasis basis
    List~Route~ newRoutes
    State state
    decline(by, now)
  }
  class Delivery {
    <<AggregateRoot>>
    StopId id
    OrderId order
    Attempt attempt
    Status status
    List~OutcomeRecord~ records
    arrive(report, Entitlement)
    deliver(report, Proof, Entitlement, HoldCheck)
    fail(report, FailureReason, Entitlement, HoldCheck)
    correct(Outcome, reason, by)
    confirmHeld(reportId, by, now)
    refuseHeld(reportId, by, now)
  }
  class RouteRun {
    <<AggregateRoot>>
    RouteId id
    Status status
    TrackingWindow tracking
    RevisionNo acknowledged
    finish(at, pendingStops)
    acknowledge(revision)
    acceptsPositionAt(time) boolean
  }
  class Order {
    <<AggregateRoot>>
    OrderId id
    DeliveryDate date
    Attempt attempt
    State state
    change(details, StopState, now)
    reschedule(date, StopState, DateCapacity, today, now)
    cancel(StopState, by, now)
    putBack(date, OrderStatus, DateCapacity, today, now)
  }
  class DayRoster {
    <<AggregateRoot>>
    DeliveryDate date
    List~RosterEntry~ entries
    assign(driver, vehicle, standing)
    moveVehicleWithRoute(vehicle, from, to)
    checkAssignable(vehicle, driver) Assignability
  }

  Plan "1" *-- "many" Route
  Route "1" *-- "many" PlannedStop
  Plan "1" *-- "0..1" PlanRevision : pending
  SolveJob ..> Plan : PlanId
  ReoptimizationProposal ..> Plan : PlanId
  Delivery ..> PlannedStop : StopId
  RouteRun ..> Route : RouteId
  PlannedStop ..> Order : OrderId, Attempt
  Route ..> DayRoster : VehicleId, AccountId
```

**What `Plan` loads.** Its current routes, drops, pins, locks, violations and journal, never past
revisions. A change that needs a revision fills the pending `PlanRevision` snapshot, which the repository
appends in the same transaction, as rows ([ADR-059]); history is read through Planning's queries (`RouteMembership`,
`PlanHistory`). So a plan with thirty revisions costs the same to change as a plan with one.

**What `Plan`'s methods are given.** The facts it can't see arrive as values the use case read first:
`PublishingChecks` (each route's roster answer, the date's uncancelled orders with their last change
time, and the optimizer's `Evaluation` of this version), `ChangesSinceBasis` (deliveries, cancellations,
order, roster and plan changes since a proposal's basis), `DriverStanding` (the new driver's account,
availability, shift and break) and `StartedRoutes`. Each value names the plan version it was read for
where that matters, and `Plan` refuses one read for another version (`StaleEvaluation`).

**In the Alpha:** `Plan` (solve, view, publish) and `SolveJob` in the daily lane. Proposals, hand-overs
and manual edits come later; edits are in if M4 finishes early.

### 4.5 Live Operations

| Aggregate | Holds | Operations | Size and writers |
|---|---|---|---|
| `Delivery` | Its stop's ID, order and attempt; state (*held*, *arrived*, *delivered* or *failed*, from the effective outcome); every outcome record (entities: kind, reason, `Proof`, left at the door, by whom, on behalf or not, `CorrectedTime`, the revision that entitled it, what it corrects, and for a held report its state: *held*, *confirmed* or *refused*) | `firstFact`, `arrive`, `deliver`, `fail`, `recordOnBehalf`, `correct`, `confirmHeld`, `refuseHeld` | One per stop, created by its first fact; the driver's phone and dispatchers |
| `RouteRun` | Its route's ID, state (*started* or *finished*, with pending stops), `TrackingWindow`, the last revision the driver confirmed | `start`, `finish`, `acknowledge`, and the query `acceptsPositionAt` | One per route, created by its first fact; the driver and the first report |
| `Alert` | Kind, references, dedupe key, raise time, state (*open* or *acknowledged*) | `raise`, `acknowledge` | One per alert; any module through `RaiseAlert`, dispatchers |

Positions are records (§3.2), appended through `PositionLog` after `RouteRun.acceptsPositionAt` says yes.

An alert reaches an Admin or a Dispatcher in the phone app through the event stream while the app is open,
and through push while it is in the background. The push carries no personal data (NFR-218), and the app
fetches the details (Q-97, [ADR-083]).

| Invariant | Owner | Error |
|---|---|---|
| A driver reports only a stop that was on their route in the revision in force at the report's corrected time (BR-02, BR-110, BR-114) | `Delivery`, which requires an `Entitlement` for a driver's report | `StopNotOnYourRoute` |
| The first outcome wins; a report that contradicts it is refused, and the dispatcher is alerted (BR-18, BR-88, BR-90) | `Delivery` | `OutcomeAlreadyRecorded` |
| A late outcome that would take a stop off another driver's route, or override a cancellation, is held, not recorded, when any session of the reporting account was in contact between 2 minutes after the report's corrected time and 2 minutes before the report arrived. A held report changes no route and raises no `OutcomeRecorded`. The dispatcher is alerted, and the phone's outbox is told it was received (BR-132, §8) | `Delivery.deliver`, `fail`, given a `HoldCheck`, whose `holds(correctedTime, receivedAt)` makes the decision | — (the answer is *held*) |
| A further late report for a stop that already has a held report is held too, with no contact check (BR-136) | `Delivery.deliver`, `fail` | — (the answer is *held*) |
| While a report is held, an outcome recorded for the stop on its current route wins: the held report is refused, as BR-90 says, and shown to the dispatcher (BR-132, BR-90) | `Delivery.deliver`, `fail`, `recordOnBehalf` | — |
| A dispatcher confirms or refuses a held report, once. Confirming records the outcome it states; refusing is a BR-90 refusal, shown to the dispatcher (BR-132, BR-90) | `Delivery.confirmHeld`, `refuseHeld` | `ReportNotHeld` |
| A driver's *delivered* needs a proof (FR-367) | `Delivery.deliver` | `ProofRequired` |
| A *failed* report needs a reason | `Delivery.fail` | `FailureReasonRequired` |
| *Left at the door* is recorded only for a leave-at-door order (FR-366) | `Delivery.deliver`, given whether the order may be left at the door | `NotALeaveAtDoorOrder` |
| A correction keeps the original and gives a reason (FR-333) | `Delivery.correct` | `CorrectionReasonRequired` |
| A route run starts with the driver's start or the route's first report (FR-327), and finishes once | `RouteRun.start`, `finish` | `RouteAlreadyFinished` |
| A position is kept only between the run's start and its finish, or one hour after the shift ends (FR-328), and only from the route's current driver while available; after a hand-over or once the driver is marked unavailable, the old driver's positions are refused (NFR-30) | `RouteRun.acceptsPositionAt` | `TrackingClosed` |
| A driver's confirmation of a revision never goes backwards (FR-97, FR-250) | `RouteRun.acknowledge` | — (an older one is ignored) |
| An alert is raised once per dedupe key (FR-157, FR-260) | `Alert.raise`, backed by a unique key | — (raising it again does nothing) |

**In the Alpha:** `Delivery` and `RouteRun`, online reports only, with a signature as the proof; photos,
positions and alerts come later.

### 4.6 Recipient Messaging

| Aggregate | Holds | Operations | Size and writers |
|---|---|---|---|
| `StopMessages` | For one stop: the recipient's phone, and every message to it (entities: type, sequence, `MessageText`, trigger time, state, provider reference) | Triggers: `onRouteStarted`, `onJoinedStartedRoute`, `onArrivalProjected`, `onDeliveryChanged`, `onOutcome`. Sender: `claim`, `accepted`, `refused`, `outcomeUnknown`, `dropStale` | One per stop, a handful of messages; triggers and the sender |
| `RatingRequest` | The stop, the phone, when the question was accepted, state (*open*, *rated* or *closed*), comments, whether a reminder and a thank-you were sent | `open`, `answer` | One per *delivered* message carrying the rating question; replies |
| `MessagingSettings` | Messages on or off (FR-350), the rating question on or off, the dispatch phone | `change…` | One; Admins |

Opt-outs and inbound replies are records (§3.2).

| Invariant | Owner | Error |
|---|---|---|
| A message goes only while messages are on (FR-350), to an Israeli mobile (FR-378), never to a phone that opted out (FR-363, NFR-202) | `StopMessages`, given the messaging settings and the opt-out answer | — (no message) |
| Each type is sent at most once per stop, except *time changed*: at most twice, 30 minutes apart (NFR-191, FR-353, FR-384) | `StopMessages`, backed by unique (stop, type, sequence) | — |
| *Out for delivery* only for a stop not yet arrived (FR-351, FR-373); *arriving soon* only when it is 15 minutes away or less on a started route and the stop before it has an outcome (FR-352); *delivered* or *missed you* only for the first outcome (FR-374) | `StopMessages` | — |
| A message not accepted within 15 minutes of its trigger is dropped (NFR-190) | `StopMessages.dropStale(now)` | — |
| A send whose outcome is unknown is retried only if the provider drops repeats by DispatchFlow's reference (NFR-198) | `StopMessages.outcomeUnknown` | — |
| At most 1,000 messages accepted a day (NFR-196) | `DailySendCap`, which the sender reads before each claim | — (sending waits for midnight) |
| A reply counts as a rating only within 48 hours of the question, and only once; other text is kept as a comment of at most 500 characters; the first comment before a rating gets one format reminder (FR-357 to FR-360, FR-371) | `RatingRequest.answer` | — (a reply with no open request is discarded, FR-379) |
| An opt-out word opts the phone out at once, confirmed once if the phone was messaged in the last 48 hours (FR-363) | `Reply` and the opt-out records | — |

**In the Alpha:** none of it.

### 4.7 Reporting

| Aggregate | Holds | Operations | Size and writers |
|---|---|---|---|
| `Baseline` | Its plan, the nearest-neighbour result, its cause (*published* or *vehicle withdrawn*), when it was computed | `compute`, `recompute` | One per published plan; Reporting's listeners |

| Invariant | Owner | Error |
|---|---|---|
| A plan has one baseline, computed when it is published (FR-39, FR-342) | `Baseline.compute`, keyed by plan | — |
| The baseline is recomputed only when a re-optimization withdraws a vehicle, and the comparison then uses kilometres and on-time only (FR-267 to FR-271) | `Baseline.recompute` | — |
| An on-time rate with no delivered stops is *unavailable*, never 0 (FR-265) | `OnTimeRate` | — |

Everything else in Reporting is read models over the views other modules publish.

**In the Alpha:** the daily summary and the baseline comparison.

### 4.8 Audit

Audit records are records (§3.2), written through `AuditLog` in the transaction of the change they
describe (NFR-23). They never carry personal data (NFR-94). The application's database role (`app`) can
only insert and read them, and may execute one database function, owned by `dispatchflow_owner`, that
deletes only audit records older than 1 year and computes the cutoff itself. The core's nightly retention
job calls it (§10.3), so no separate step runs and the owner credential stays deploy-only (NFR-258,
[ADR-075], [ADR-041]).

### 4.9 Optimization (Python)

The optimizer keeps no state between calls, so it has no aggregates. Its domain is value objects whose
methods are the rules:

- **`Problem`** (frozen, structs of NumPy arrays) holds everything one solve needs, including the orders
  that can't enter it (no location, or no road route), each with its reason. Its methods are the single
  definition of what a solution means (BR-42):
  - `validate(solution) -> ValidationReport`: feasibility, one rule per `RuleId` named after its BR
    (BR-15, BR-16).
  - `cost(solution, timings)`: the objective (BR-09, BR-56, BR-115, BR-120).
  - `explain_drop(order, solution) -> DropReason`: why an order was dropped, from no location to
    displaced by others (FR-144).
- **`ProblemFactory`** builds a `Problem` from a daily request, or from a live state with every
  re-optimization convention applied (BR-10, BR-23, BR-24, BR-79, BR-80, BR-106 to BR-108, BR-117; [ADR-050]).
- **`Solution`**, **`TravelTimeModel`** (the only place bucket factors and the buffer are applied:
  FR-227, FR-321, FR-322) and **`PenaltyScale`** (the drop unit and the penalty rates) are values.
- **The drop unit** is K = 5L + 2T + F + 1, in seconds, where L is the longest shift on the date, T the
  largest entry of the date's unbuffered per-bucket matrix at the highest factor, and F the fixed vehicle
  cost (BR-126). Dropping an order of priority p costs K × (6 − p) (BR-54), so even a priority-5 order
  costs at least K, and no solve drops an order to save its detour (BR-55), to avoid a window penalty
  (BR-121) or to save a vehicle (BR-08) ([ADR-049], Q-93 #1).

The modules are split by size (`feasibility.py`, `objective.py`, `drops.py`), and `Problem`'s methods
delegate to them. Every solver, edit check, publishing re-check and research score goes through them.

**In the Alpha:** the daily-solve rules only. The re-optimization conventions come in M9.

---
## 5. Rules over sets of aggregates

A rule over many aggregates can't live inside one of them unless the set itself becomes the aggregate.
Where the set is small and changes together, it is one: `FleetRegister`, `DayRoster`, `StopMessages`,
`Plan`. Where it isn't, the table below gives the rule one owner anyway, and the database makes sure no
code path can break it.

| Rule | Why no aggregate holds the set | Owner | Backstop | Error |
|---|---|---|---|---|
| One plan per date (BR-34) | Plans of one date are one plan plus cancelled ones | `Plan.open` | Partial unique index on the date, excluding cancelled plans | `PlanAlreadyExists` |
| At least two active Admins (BR-19) | Roles live on each account, because changing them must revoke that account's tokens in the same transaction (NFR-177) | `ActiveAdmins`, which `changeRoles` and `deactivate` require | `ActiveAdmins` is read with `FOR UPDATE` on the active Admin accounts | `TooFewAdmins` |
| One account per email (BR-45) | Accounts are independent | `UserAccount.open` | Unique index on the canonical email | `EmailInUse` |
| At most 200 uncancelled orders a date (BR-101, BR-113) | Orders move between dates (FR-258, FR-315), so a per-date aggregate would change two aggregates per move | `DateCapacity`, which every method that adds an order to a date requires | `DateCapacity` is read under a transaction-scoped advisory lock on the date | `DateFull` |
| A reference is unique on its date (FR-211) | Same | `Order.place`, `ImportBatch` | Partial unique index on (date, reference) | `DuplicateOrderReference` |
| One running re-optimization per plan (BR-38) | Jobs end independently, and the plan must not be written for every job's phase | `SolveJob.queue` | Partial unique index on running re-optimization jobs per plan | `ReoptimizationAlreadyRunning` |
| At most 5 jobs queued in a lane (BR-112) | Same | `LaneCapacity`, which `SolveJob.queue` requires | `LaneCapacity` is read under an advisory lock on the lane | `SolveQueueFull` |
| An alert per dedupe key (FR-157) | Alerts are independent | `Alert.raise` | Unique dedupe key | — |

A snapshot value (`ActiveAdmins`, `DateCapacity`, `LaneCapacity`) is built only by the repository query
that takes its lock; an ArchUnit rule allows no other caller of its factory (§3.9). So the method that
could break the rule can't be called without the lock having been taken.

---

## 6. Transactions and commands

### 6.1 The rule

A command changes **one existing aggregate**, saved with a version check (BR-76). In the same transaction
it may also do two other things:

1. **Create new aggregates of the same module that only this command can see.** A new aggregate has no
   other writer and no version that could conflict, so creating it beside the change can't cause the
   contention the rule prevents, and it removes an asynchronous hop that could fail on its own. If two
   dispatchers request a solve at the same moment, the second save fails its version check, and the job
   it created rolls back with it.
2. **Write technical records:** the audit record (NFR-23), the idempotency record (NFR-65), Spring
   Modulith's event publications, and outbox rows such as geocoding requests.

Nothing else. A change to a second existing aggregate always follows by an event (§7) or by a later
call, never in the same transaction.

### 6.2 Commands

| Command | Changes | Creates | Then |
|---|---|---|---|
| Open a plan | — | `Plan` | — |
| Request a solve | `Plan` → *solving*, with the job's ID in its basis | `SolveJob` in the daily lane | The lane's worker claims it |
| A job ends (worker, watchdog, startup recovery or the midnight job) | `SolveJob` → *succeeded* or *failed* | A `ReoptimizationProposal`, for a re-optimization that succeeded | `SolveSucceeded` or `SolveFailed` → `Plan.applySolveResult` or `solveFailed` (BR-59) |
| Edit, pin, lock, undo, redo | `Plan` | — | — (checked through `FeasibilityChecker` before the transaction) |
| Publish | `Plan` → revision 1 | — | `PlanPublished` |
| Request a re-optimization | — | `SolveJob` in the re-optimization lane | The lane's worker claims it |
| Accept a proposal | `Plan` → a revision that cites the proposal | — | `PlanRevised` |
| Decline a proposal | `ReoptimizationProposal` → *declined* | — | — |
| Hand over a route | `Plan` → a new revision | — | `PlanRevised`; `RouteHandedOver` → Fleet's roster follows (FR-191) |
| Place or change an order | `Order`, or creates it | — | — |
| Cancel an order | `Order` → *cancelled* | — | `OrderCancelled` → `Plan.removeStopOf` (FR-207, FR-316) |
| Reschedule an order | `Order` | — | `OrderRescheduled` → `Plan.removeStopOf` (FR-315) |
| Put back a failed order | `Order` → the next attempt (FR-258) | — | — |
| Upload a file | — | `ImportBatch` (*previewed*) | — |
| Confirm an import | `ImportBatch` → *confirmed* | Its `Customer`s and `Order`s; geocoding requests | — |
| A driver reports a stop | `Delivery`, or creates it. A report that BR-132 holds is stored as *held* and records no outcome | The `RouteRun`, on the route's first report (FR-327); a held report doesn't count until confirmed. An `Alert` for the dispatcher, when the report is held | `OutcomeRecorded` for an outcome; `RouteStarted` when the run was created. Nothing for a held report: the phone's outbox gets an acknowledgement (§8) |
| A dispatcher confirms a held report | `Delivery` → the outcome the report states | The `RouteRun`, if this is the reporting route's first report | `OutcomeRecorded` → reconciliation through Planning (§8) |
| A dispatcher refuses a held report | `Delivery` → the report *refused* (BR-90) | — | — |
| A dispatcher corrects an outcome | `Delivery` | — | `OutcomeCorrected` |
| The driver starts the route | — | `RouteRun` | `RouteStarted` |
| The driver finishes the route | `RouteRun` → *finished* | An `Alert` when stops are pending (FR-329) | — |
| A phone sends positions | — | Position records | — |
| Mark a vehicle or a driver unavailable | `DayRoster` | — | — (only a person starts a re-optimization, FR-332) |
| Change roles, password or email; deactivate | `UserAccount`, `Generation` + 1 | — | `SessionsRevoked` |
| Confirm the password (step-up) | `Session`, `Generation` + 1 | — | — |
| Sign out | `Session` → *revoked* | — | — |
| A message trigger | `StopMessages`, or creates it | — | — |
| The sender sends a message | `StopMessages` | A `RatingRequest`, when a *delivered* message with the rating question is accepted | — |
| A reply arrives | `RatingRequest`, if one is open | The reply record; an opt-out record for an opt-out word | `RatingReceived` for a rating |

### 6.3 Outside calls happen before the transaction

A use case calls the optimizer, OSRM or the geocoder **before** it opens its transaction. It then loads,
changes and saves the aggregate with the version it read. If the version moved in the meantime, the save
fails with `STALE_VERSION`, and only the call is lost. No database transaction ever waits on HTTP, so a
slow optimizer can't hold a connection or a row.

---

## 7. Domain events

### 7.1 When an event earns its place

An event is used only where one of these holds, and a direct call or doing nothing would be worse
([ADR-064]):

1. **Another module must react, and the emitter must not know it exists.** An upstream module can't call
   downstream (§2.3), so an event lets Live Operations, Messaging and Reporting react to a plan without
   Planning knowing about them.
2. **Another aggregate must change after this one, reliably.** One transaction changes one aggregate. An
   event is stored with the change and delivered again until its listener succeeds, which a call after
   commit can't promise.
3. **Several paths produce the same fact.** A job ends in its worker, in the watchdog or at startup
   recovery; one event means the plan's reaction is written once.

Two things are never reasons on their own. **Updating a screen**: the use case tells the `LiveUpdates`
port after commit, and a missed update costs the screen one refetch. **Calling another module that this
one may call anyway**: a direct call is simpler, unless it must be retried (reason 2).

### 7.2 The events

| Event | What happened, in business terms | Raised by | Heard by → reaction | Why not a direct call, or nothing |
|---|---|---|---|---|
| `SolveSucceeded`, `SolveFailed` | A solve job ended, with routes or with a reason | `SolveJob` | Planning → `Plan.applySolveResult` or `solveFailed`; dispatchers are told on screen | Three paths end a job (reason 3), and the plan must follow in its own transaction (reason 2) |
| `PlanPublished` | A plan became the drivers' plan | `Plan` | Live Operations → "route ready" push to each driver (FR-152); Reporting → computes the `Baseline` (FR-39, FR-342) | Both are downstream of Planning (reason 1) |
| `PlanRevised` | A published plan has a new revision, with its cause and the routes it changed | `Plan` | Live Operations → change notice and push to each affected driver (FR-71, FR-97, FR-249); Messaging → messages for stops that joined, moved or dropped (FR-354, FR-372); Reporting → recomputes the `Baseline` if a vehicle was withdrawn (FR-267 to FR-271) | All three are downstream (reason 1) |
| `RouteHandedOver` | A route moved to another driver | `Plan` | Planning's own listener → calls Fleet's `MoveVehicleWithRoute`, so the roster follows (FR-191) | Fleet's `DayRoster` must change reliably after the plan (reason 2) |
| `OrderCancelled` | An order was cancelled | `Order` | Planning → `Plan.removeStopOf` (FR-207, FR-316); Messaging → the "your delivery changed" message (FR-354) | Both are downstream (reason 1) |
| `OrderRescheduled` | An order moved to another date | `Order` | Planning → `Plan.removeStopOf` on the old date's plan (FR-315); Messaging → the "your delivery changed" message (FR-354) | Both are downstream (reason 1) |
| `RouteStarted` | A driver started a route | `RouteRun` | Messaging → "out for delivery" for each stop not yet arrived (FR-351, FR-373) | Downstream (reason 1) |
| `OutcomeRecorded` | A stop was delivered or failed (the first outcome) | `Delivery` | Live Operations' own listener → late-outcome reconciliation through Planning (§8); Messaging → "delivered" or "missed you" (FR-355, FR-356, FR-374) | Planning is upstream, so the call must come from a retried listener (reason 2); Messaging is downstream (reason 1) |
| `OutcomeCorrected` | A dispatcher changed a stop's outcome | `Delivery` | Messaging → the message follows the effective outcome (FR-333) | Downstream (reason 1) |
| `RatingReceived` | A recipient rated a delivery | `RatingRequest` | Messaging's own listener → a 1 to 4 rating raises a Live Operations alert (FR-361) | The alert must follow reliably (reason 2) |
| `SessionsRevoked` | Every token and session of an account stopped being valid | `UserAccount` | The live-updates adapter → closes that account's open streams (NFR-188) | Six commands revoke (reason 3); the revocation cache itself is evicted at once, in the same thread, so "refused at once" doesn't wait for an event (NFR-177) |

### 7.3 Shape and delivery

- **A past-tense, immutable record** in the raising module's `<module>/events/` package, which is its
  `events` named interface: its own ID, `occurredAt`, the aggregate's ID and version, and the fact's key
  data. It holds only IDs, shared-kernel values and small enums defined in its own `events` package (a
  revision cause, an outcome kind), never a copy of the aggregate's state; a listener
  that needs more asks the owner. The module's own domain may import its `events` package (§3.9); no
  other module's domain may.
- **The events package is published language.** A field is added, never renamed or removed, so a
  listener in another module (or, later, another service) never breaks.
- **At least once.** Spring Modulith stores each publication in the same transaction as the change and
  delivers it after commit, again and again until its listener succeeds ([ADR-064]). So every reaction
  is idempotent: by nature (`applySolveResult` ignores a job it has applied) or by recording
  `(listener, event ID)` first.
- **Raised by aggregates, not by use cases.** An event says that the aggregate's state changed, so only
  the aggregate's method can raise it.

### 7.4 Considered and left out

| Candidate | Why it isn't an event |
|---|---|
| A proposal is ready | Only a screen reacts. The worker tells `LiveUpdates` after the commit that stores the proposal |
| A late outcome was reconciled | The caller gets the answer from its own call to `ReconcileLateOutcome`, and raises the alert itself (§8) |
| A route finished | The only reaction, the pending-stops alert, is created in the same transaction (§6.2) |
| A projected arrival changed | Projections change as time passes, not only when something is reported. Messaging asks for them every minute instead (§10.3) |
| An order's details changed | Nobody must react at once. On a published plan, the answer to the edit says that the order is on a published route and that the driver sees the change only after a re-optimization (FR-206, FR-396), and the live view marks the stop *changed since publishing* until a re-optimization applies the change or the stop has an outcome (FR-397). Neither covers a date change, which takes the stop off at once (FR-315). Both read the order's last change time, as do publishing, accepting a proposal and re-optimizing (BR-22, BR-82) |
| A user's roles changed | Every role change also revokes, so `SessionsRevoked` covers it, and nothing else reacts to roles |
| A stop was arrived at | Nothing reacts to an arrival alone: projections and messages read it when they run |

---

## 8. When a fact contradicts the plan

Facts arrive late. A phone can work offline for hours (FR-69, FR-70), so a report can reach the server
after a dispatcher has moved, dropped or cancelled the stop. No lock can prevent this, because the
delivery has already happened on the street. The design accepts the fact and corrects the plan.

1. **Entitlement decides whether the fact is accepted.** A driver's report is accepted if the stop was on
   that driver's route in the revision in force at the report's corrected time (BR-02, BR-110, BR-114):
   the use case asks Planning's `RouteMembership` and passes the answer to `Delivery` as an
   `Entitlement`. A report that contradicts an outcome already recorded is refused, and the dispatcher is
   alerted (BR-18, BR-88, BR-90). One timed before the order or plan was cancelled is not refused (BR-130).
   A report that would move a stop off another driver's route, or override a cancellation, is *held*
   for the dispatcher's confirmation if any session of the reporting account was in contact with the
   server between 2 minutes after the report's corrected time and 2 minutes before the report arrived
   (BR-132). A further late report for a stop that already has a held report is held too (BR-136). A phone that was
   in contact after the time it claims was not offline, so its corrected time is in doubt. A token
   refresh and an outbox send are not contact, because the phone makes them while it empties its queue.
   The use case builds a `HoldCheck` from `RouteMembership` and Access's `SessionContacts`, which looks
   across every session of the account, and `Delivery` decides.
   - **What the outbox gets back:** the report is acknowledged, so the phone stops resending it
     (FR-248). The dispatcher is alerted, and the report waits as *held*. It records no outcome, raises
     no `OutcomeRecorded` and changes no route.
   - **The dispatcher decides:** confirming records the outcome the report states, and step 2 follows
     as for any accepted fact. Refusing is a BR-90 refusal: the report is refused and shown to the
     dispatcher.
   - **An outcome on the stop's current route wins:** while a report is held, an outcome recorded for
     the stop on its current route (the driver's, or a dispatcher's on their behalf) stands, and the
     held report is refused as BR-90 says. The first outcome wins (BR-18) in both orders.
2. **An accepted fact stands.** `Delivery` records it and raises `OutcomeRecorded`. Live Operations'
   listener compares it with the current revision, through `RouteMembership`. If the two disagree, it
   calls `ReconcileLateOutcome`, which changes `Plan` and returns what it changed; the listener then
   raises the dispatcher's alert with a dedupe key, so a retry raises it once. Both steps are idempotent:
   `Plan.reconcileLateOutcome` does nothing if the stop is already where the fact puts it.

| In the current plan, the stop is… | `Plan.reconcileLateOutcome` | Who is told |
|---|---|---|
| still on the reporting driver's route | Nothing | — |
| on another driver's route | Removes it from that route and records it as *served late* on the reporting driver's route: a new revision (BR-89) | The dispatcher (alert) and the other driver (change notice, from `PlanRevised`) |
| dropped by a later re-optimization | Moves it from *dropped* to *served late* on the reporting driver's route. No other route changes | The dispatcher |
| gone because its order was cancelled | If the report's corrected time is before the cancellation, keeps the cancellation and records the stop as *served after cancellation* on the reporting driver's route (BR-130); no other route changes. A report timed after it is refused (BR-88, BR-90) | The dispatcher |

In every case the stop ends up on the route that actually served it, so reports, the next
re-optimization and the "every order accounted for" check (BR-22) all see the truth. The one exception is
a report held under BR-132 (step 1): it changes no route until the dispatcher confirms it.

3. **The order's status follows the fact** (§9.6).

This one path also settles a race: a cancellation that commits a moment before an *arrived* report.
`Order.cancel` still refuses when the `StopState` it was given already shows a fact (BR-92). When the fact
lands after that read, the table above settles it.

*Why not lock instead?* An advisory lock per order would serialize the online cancel-versus-report race,
but not the offline case, which is the common one. One mechanism that handles both is simpler than two.

```mermaid
sequenceDiagram
  autonumber
  participant P as Driver A's phone
  participant LO as Live Operations
  participant PL as Planning
  participant MS as Messaging
  P->>LO: queued report: stop S delivered at 10:05 device time
  Note over LO: tx 1 writes Delivery(S) only.<br/>Corrected time 10:05 (BR-114).<br/>The revision in force at 10:05 had S on A's route (BR-110).<br/>First outcome wins (BR-18, BR-88).
  LO-->>P: acknowledged (FR-248)
  LO->>PL: ReconcileLateOutcome(S, A's route), from LO's listener after commit
  Note over PL: tx 2 writes Plan only.<br/>S leaves B's route: a new revision (BR-89).
  PL-->>LO: what changed
  Note over LO: tx 3 raises the dispatcher's alert
  PL--)LO: PlanRevised → change notice to driver B
  LO--)MS: OutcomeRecorded → "delivered" SMS (FR-356)
```

---
## 9. Lifecycles

Each lifecycle is a sealed state type with guarded transitions (§3.4). Every arrow is one method; a
method called from any other state is refused with a named error. There is one named exception:
`Plan.solveFailed` on an *abandoned* plan does nothing, because the midnight job fails a date's solve jobs
just before it abandons the plan, and a job's `SolveFailed` may arrive after that ([ADR-053], BR-128).

### 9.1 Plan (BR-12)

```mermaid
stateDiagram-v2
  [*] --> draft
  draft --> solving : request solve
  solving --> ready : validated solution
  solving --> draft : failed, never solved
  solving --> ready : failed, solved before
  ready --> solving : solve again
  ready --> ready : edit, pin, lock, undo, redo
  ready --> published : publish, all checks pass
  published --> published : new revision
  published --> completed : midnight after its date
  published --> cancelled : cancel, no route started
  draft --> abandoned : midnight after its date
  solving --> abandoned : midnight after its date
  ready --> abandoned : midnight after its date
  completed --> [*]
  cancelled --> [*]
  abandoned --> [*]
```

A published plan's revisions come from an accepted re-optimization, a hand-over, a removed stop (a
cancelled or rescheduled order) and a late outcome. Each records its cause (BR-77).

A plan that is not published by the midnight after its date becomes *abandoned*, which is final and
read-only (BR-127, [ADR-053]). The midnight job that completes published plans (BR-102) does this too. First it
fails every queued or running solve job of that date, published plan or not, with the reason *its date
passed* (§9.2, BR-128). Orders on an abandoned plan show *unplanned* (§9.6, BR-131).

### 9.2 SolveJob

```mermaid
stateDiagram-v2
  [*] --> queued
  queued --> building_matrix : worker claims it
  building_matrix --> running : matrix ready, search starts
  running --> succeeded : validated result
  running --> failed : invalid result, overran, optimizer down, restart, its date passed
  building_matrix --> failed : OSRM down, restart, its date passed
  queued --> failed : queued too long, a re-optimization at startup, its date passed
  succeeded --> [*]
  failed --> [*]
```

`building_matrix` is separate from `running` because building the matrix counts toward the response time
(NFR-07) but not toward the search limit (BR-75).

A failed job records its reason. *Its date passed* is the reason when the midnight job fails the job of a
plan whose date is over (§9.1).

### 9.3 Delivery (BR-87)

A `Delivery` is created by the first fact, so *pending* means no fact stands: no `Delivery` exists yet,
or its only report was refused.

```mermaid
stateDiagram-v2
  [*] --> arrived : arrive
  [*] --> delivered : deliver, with proof if a driver's
  [*] --> failed : fail, with a reason
  [*] --> held : deliver or fail, held under BR-132
  arrived --> delivered : deliver
  arrived --> failed : fail
  arrived --> held : deliver or fail, held under BR-132
  pending --> arrived : arrive
  pending --> delivered : deliver
  pending --> failed : fail
  pending --> held : deliver or fail, held under BR-132
  held --> held : a further late report, held too (BR-136)
  held --> delivered : confirmed as delivered
  held --> failed : confirmed as failed
  held --> delivered : overtaken by a delivered outcome on the stop's current route
  held --> failed : overtaken by a failed outcome on the stop's current route
  held --> arrived : refused, an arrival was recorded
  held --> pending : refused, no arrival was recorded
  delivered --> failed : dispatcher's correction
  failed --> delivered : dispatcher's correction
```

A report is held only when it would move a stop off another driver's route or override a cancellation,
or when the stop already has a held report (BR-136), and only an outcome (*delivered* or *failed*) can
be held, never an arrival. Each held report is one of three things in the end. **Confirmed** by a dispatcher: it becomes the stop's outcome. **Refused** by a
dispatcher: it is a BR-90 refusal, shown to the dispatcher. **Overtaken**: an outcome recorded for the
stop on its current route becomes the stop's outcome, and the held report is refused as BR-90 says. In
the last two cases the held report stays on record, marked *refused*. An arrival recorded while a
report is held doesn't change the state (BR-132). Once one held report is confirmed, the stop has its
outcome, and every other report held for it is refused as BR-90 says (BR-18).

A dispatcher may correct a failed stop to delivered after a put-back (FR-258) while the new attempt's
stop has no outcome. The new stop then leaves its route, and the change is audited. Once the new stop has
an outcome, the correction is refused and the conflict is named (FR-404, FR-333, Q-94 #2).

### 9.4 ImportBatch

```mermaid
stateDiagram-v2
  [*] --> previewed : upload, every row checked
  previewed --> confirmed : confirm, no errors, encoding confirmed if asked
  previewed --> discarded : discard
  previewed --> expired : one hour without confirming
  confirmed --> [*]
  discarded --> [*]
  expired --> [*]
```

The hour is decided ([ADR-086]). The rows of an expired or discarded preview are deleted.

### 9.5 A message to a recipient

```mermaid
stateDiagram-v2
  [*] --> pending : trigger
  pending --> sending : sender claims it
  pending --> dropped : 15 minutes after its trigger
  sending --> accepted : provider accepts
  sending --> dropped : provider refuses
  sending --> unknown : timeout or crash
  unknown --> sending : retried, only if the provider drops repeats
  unknown --> dropped : otherwise
  accepted --> [*]
  dropped --> [*]
```

### 9.6 Order status (BR-33) is derived, not stored

`OrderStatus.derive(order, placement, progress)` takes one value from each of its three owners and
returns the first match:

1. **delivered / failed**: the effective outcome of the current attempt's `Delivery`. This is a fact. A
   held report (§9.3) is not an outcome, so it changes nothing here until a dispatcher confirms it.
2. **cancelled**: the `Order` is cancelled.
3. **planned / dropped**: where the `Plan` holds the current attempt, unless that plan is *abandoned*.
4. **unplanned**: anything else, including a put-back that starts a new attempt (FR-258) and an order
   on an abandoned plan (BR-131).

The outcome comes before *cancelled*: the other way round would show *cancelled* for an order a driver
delivered, which hides a fact. The cancellation still shows beside the outcome. BR-33 states this
precedence ([ADR-065]). Customers & Orders' `OrderStatuses` query reads its own orders, `OrderPlacement` and
`StopProgress` for a whole date at once, and calls `derive` per order, so a screen of 200 orders makes
three queries, not 600. Every state change in BR-93 to BR-96 is a precondition on a command in its owner,
so no mirrored status can drift.

**A stop unreported at midnight.** When its plan completes (BR-102), the stop's order stays *planned*.
The daily summary shows the stop as *not reported*, and a late report or a dispatcher's record settles it
(BR-33, BR-102, BR-129, Q-94 #1).

### 9.7 How things end

Nothing the business keeps history about is deleted. It ends in a named state, and the record stays.

| Thing | How it ends | What stays |
|---|---|---|
| Plan | *completed*, or *cancelled* | Every revision (BR-37) |
| Plan never published | *abandoned*, at the midnight after its date (BR-12, BR-102, BR-127) | The plan as it was, read-only; its orders show *unplanned* (BR-131) |
| Solve job | *succeeded* or *failed* | Its input, result and failure code |
| Proposal | *declined*; accepted (a revision cites it); or refused as outdated when someone tries to accept it | The proposal |
| Order | *cancelled*; or put back, starting a new attempt | The order and every attempt's facts |
| Delivery | Corrected | The original outcome, beside the correction (FR-333) |
| Held report | *confirmed*, or *refused* by a dispatcher or by an outcome on the stop's current route (BR-132, BR-90) | The report, with who decided |
| Stop with no report at midnight | Its order stays *planned*; a late report or a dispatcher's record settles it (BR-129, Q-94 #1) | The stop, shown as *not reported* in the summary |
| Route | *withdrawn* by a re-optimization, or handed over | The revision history |
| Route run | *finished* | The run |
| Customer, driver | *retired*, then *anonymized* a year later; a customer never retired is anonymized a year after the last order (BR-98, NFR-152) | The figures, without personal data |
| Vehicle, depot | *retired* (BR-43) | The record |
| Account, of any role | *deactivated*, then *anonymized* a year later (NFR-258, NFR-260) | The record, without personal data: plans and audit records refer to it |
| Session, credential link | *revoked* or expired; *spent*, *voided* or expired | The record, until retention |
| Alert | *acknowledged* | The alert |
| Message | *accepted* or *dropped* | The message, until retention |
| Rating request | *rated*, or closed after 48 hours | The rating |
| Import | *confirmed*, *discarded* or *expired* | The batch; a discarded or expired one without its rows |

Deletion happens only where retention requires it (NFR-31, NFR-184, NFR-257 to NFR-260, [ADR-075]).
A row that other records refer to is anonymized in place instead.

- **Deleted:** positions after 7 days; proofs, messages and replies after 90 days; audit records after
  1 year, by the purge function that `dispatchflow_owner` owns and the core's nightly retention job calls
  (§4.8); sessions 30 days after they end; contact times 14 days after they are made (NFR-280); geocode cache
  entries unused for 1 year; research instances 1 year after the project ends; backups, which age out of the bucket after 35 days.
- **Anonymized:** an order's personal fields 90 days after its date; a customer 1 year after retirement,
  or after the last order if never retired; a driver 1 year after retirement; every account, whatever its
  roles, 1 year after deactivation.

The rows of a discarded or expired import preview are also deleted (§9.4).

---

## 10. Time

### 10.1 Principles

- **The domain never reads the clock.** Use cases pass `now` and `today` in, from the `Clock` port
  (NFR-13, NFR-175).
- **A scheduled job is only a trigger.** It reads the clock and calls the owner's method with `now`; the
  rule itself is in the owner and is tested with a fixed time.
- **`DaySeconds`** are elapsed seconds since the delivery date's local midnight in `Asia/Jerusalem`. On a
  clock-change day a 09:00 window is 8 or 10 hours after midnight, which is what travel durations need
  (BR-31, BR-111).

### 10.2 One travel-time model, in Python

The core sends OSRM's base durations and the bucket profile; the Python `TravelTimeModel` alone applies
factors and the buffer (FR-227, FR-321, FR-322). Every leg's travel seconds and metres come back with the
routes, and the plan stores them, so the core never applies a factor. The rules below are Q-20's answers
([ADR-048]):

- **An arc takes the factor of the bucket it departs in** (FR-400), read from the departure's local
  wall-clock time. A bucket's hours are start-inclusive and end-exclusive, so 07:00 to 09:30 means
  [07:00, 09:30).
- **Three kinds of travel time.** The validator and the ETAs use *buffered* times (the factor and the
  buffer). The objective's travel cost uses per-bucket times *without* the buffer. The search's upper
  bound is the highest factor × (1 + buffer). The lateness and door-window penalties use the validator's
  times.
- **Door stops.** A solver may plan a wait at a door stop. The validator re-times the stop with no waiting
  (BR-122) and charges an early arrival as time outside the window (BR-120).
- **Buckets.** They start as 07:00 to 09:30 (morning peak) and 15:30 to 18:30 (evening peak), and the
  rest of the day is off-peak. These hours are an estimate, not a measurement, and are calibrated
  together with the factors from pilot arrivals (FR-227, FR-321).
- **The upper bound stays.** Solvers plan with the highest factor and the buffer (FR-322). M1 solves the
  demo day both that way and with per-bucket times. If the upper bound needs an extra vehicle, or drops an
  order that the per-bucket solve serves, an ADR decides whether FR-322 changes.
- **Drop penalty.** The objective counts seconds, so a drop costs K × (6 − priority) seconds, with K from
  §4.9 (BR-126, Q-93 #1).

### 10.3 Every rule that depends on time

| Rule | Owner | When it is checked |
|---|---|---|
| A plan for a past date isn't solved or changed (BR-118) | `Plan`, with `today` | Each command |
| An order's date isn't in the past (BR-116) | `Order`, with `today` | Place, reschedule, put back |
| A job waits at most 5 minutes queued (BR-112) and runs at most its limit plus 30 s (BR-32) | `SolveJob`'s deadlines | The watchdog, every 15 s, calls `expireIfOverdue(now)` |
| A proposal is accepted only if nothing it relies on changed after its basis time (BR-25, BR-82) | `Plan.accept` | On acceptance |
| A report is judged at its corrected time (BR-110, BR-114) | `CorrectedTime`, `Entitlement` | Each report |
| A route not started 15 minutes after its shift start plus allowance raises an alert (FR-101) | `StartDeadline` | A job every minute |
| Positions are kept until an hour after the shift ends (FR-328) | `RouteRun`'s `TrackingWindow` | Each batch of positions |
| *Arriving soon* and *time changed* (FR-352, FR-353) | `ArrivalProjection`, `StopMessages` | Messaging's job, every minute, asks `ArrivalProjections` and calls `onArrivalProjected` |
| A message is dropped 15 minutes after its trigger (NFR-190) | `StopMessages.dropStale` | The sender, every 5 s |
| A rating counts only within 48 hours of the question (FR-357, FR-371) | `RatingRequest.answer` | Each reply |
| A password confirmation lasts 5 minutes for Admin-only operations (NFR-214) | `Session` | Each Admin-only call |
| Access tokens last 15 minutes, sessions 14 days, and a reused refresh token has 30 s of grace (NFR-86, NFR-87, NFR-178) | The token check; `Session.exchange` | Each request and each refresh |
| Credential links expire (NFR-211, NFR-224) | `UserAccount`'s links | When spent |
| An import preview expires after an hour, and its rows are deleted | `ImportBatch.confirm` and `expire` | On confirmation, and an hourly sweep |
| A published plan completes at midnight, and a plan still unpublished is abandoned (BR-102, BR-127). Before either, every queued or running solve job of the date fails (BR-128) | `Plan.complete(today)` and `Plan.abandon(today)`, after `SolveJob.fail` for every queued or running job of that date | A job at 00:00 |
| A geocoding lookup is retried for 24 hours (FR-64) | The geocoding request (outbox row) | Every 15 minutes |
| Positions are kept 7 days; proofs, messages and replies 90 days. An order's personal fields are removed 90 days after its date. A customer is anonymized 1 year after retirement or after their last order, a driver 1 year after retirement, every account, whatever its roles, 1 year after deactivation. Sessions are deleted 30 days after they end, contact times after 14 days (NFR-280), geocode cache entries unused for 1 year, research instances 1 year after the project ends (NFR-31, NFR-184, NFR-257 to NFR-260, BR-98, [ADR-075]). Backups age out of the bucket after 35 days | `RetentionPeriods`; `Customer.anonymize(now)`, `Driver.anonymize(now)`, `UserAccount.anonymize(now)` | A job at 03:00 |
| Audit records are deleted after 1 year (NFR-258, [ADR-075]) | A database function owned by `dispatchflow_owner`, which deletes only records older than 1 year and computes the cutoff itself. The application's role (`app`) may only execute it, because it can only insert and read audit records ([ADR-041]) | The same retention job, at 03:00 in `Asia/Jerusalem` |

### 10.4 Projected arrivals

`ArrivalProjection.of(legs, facts, now)` doesn't re-apply factors. It propagates what has happened along
the legs the optimizer stored for the current revision ([ADR-067]):

1. **Leaving the last reported stop:** `d` is the outcome's corrected time if that stop has one. If it is
   only *arrived*, `d = max(arrival + service, now)`, because the vehicle is still there. Before the first
   report, the planned times stand.
2. **The next stop:** `arrival = max(d + leg, now)`. The vehicle is either still on the leg, or it is late
   and hasn't reported, and in both cases it can't arrive before now.
3. **Every stop after that:** `start = max(arrival, window start)` for a stop with a hard window, so
   waiting absorbs delay; `start = arrival` for a door stop (BR-122). Then
   `next arrival = start + service + leg`.

If a delay pushes a departure into another bucket, the projection keeps the planned bucket; exact
re-timing happens whenever the optimizer produces a new revision. A projection informs messages and
at-risk alerts (FR-253, FR-254, FR-383); it never decides feasibility. Because it depends on `now`, it is
computed when asked, never stored.

---

## 11. Concurrency

Every aggregate root has a version, and every save checks it (BR-76, [ADR-057]). The table names the aggregates more
than one writer may change at once.

| Aggregate | Who may change it at the same moment | How a conflict ends |
|---|---|---|
| `Plan` | Dispatchers (edit, publish, accept, hand over), and six automatic writers: the solve-result listener, the listeners for cancelled and for rescheduled orders, Live Operations' late-outcome call, the route re-check job, and the midnight job (`complete`, `abandon`) | A dispatcher gets `STALE_VERSION` (412) and refetches. A listener retries at once, up to three times, then the event registry resubmits it. The version check refuses whichever of a dispatcher and the midnight job saves second, as it does for the other writers; a refused dispatcher then reads a completed or abandoned plan, which no longer changes (BR-118). When the version check refuses the midnight job, it reloads the plan and retries, up to three times like a listener, then alerts the team |
| `DayRoster` | Dispatchers, and the hand-over listener | The same |
| `SolveJob` | Its worker, the watchdog, startup recovery and the midnight job | The version check refuses the loser, which finds the job ended (`JobAlreadyEnded`) and stops |
| `Delivery` | The driver, a dispatcher correcting, reporting on behalf, or confirming or refusing a held report, and the phone's own retries | A retry carries the same idempotency key and gets the stored answer (NFR-65, NFR-130); otherwise the version decides, and the first outcome wins. A dispatcher who confirms a held report that an outcome on the stop's current route has overtaken meanwhile is refused, because the report is no longer held (`ReportNotHeld`) |
| `RouteRun` | Two first reports from one route at once | The second insert fails on the key, and the report is retried as a change |
| `StopMessages` | Message triggers and the sender | The sender retries its claim |
| `Session` | Two tabs refreshing at once | The version decides; the 30 s grace gives both the same successor (NFR-178) |
| `UserAccount` | The person and an Admin | The version decides; `ActiveAdmins` is read under row locks |
| `Order`, `Customer` | Dispatchers, imports, geocoding results | The version decides; a geocoding result that arrives after a hand-placed location is ignored |
| `ImportBatch` | A double-clicked confirm | The second gets `ImportAlreadyConfirmed` |
| `FleetRegister`, the settings | Admins | The version decides |

**Hotspots.** `Plan` is the only aggregate with several writers in a normal day, and it sees a few writes
a minute at peak. The one high-volume stream, positions from 15 phones, is records with no version, and
the 15 phones' reports go to 200 separate `Delivery`s, never to `Plan`. The set rules of §5 serialize on
small locks (one date, one lane, the Admin accounts) that a request holds for milliseconds.

---

## 12. Who notices when an asynchronous step doesn't happen

Each step that crosses a commit has a watcher, so a lost step can't leave something stuck ([ADR-066]).

| Step | Expected outcome | Watcher | What the watcher does |
|---|---|---|---|
| A queued or running `SolveJob` | Ends within its limit plus 30 s (BR-32); waits at most 5 minutes (BR-112) | The queue watchdog, every 15 s | `expireIfOverdue(now)` fails it, and `SolveFailed` returns the plan to *draft* or *ready* |
| A plan still unpublished at the midnight after its date | It is *abandoned*, with no solve job of it queued or running | The midnight job, at 00:00 (BR-102, BR-127, BR-128) | Fails every queued or running solve job of that date with the reason *its date passed*, then calls `Plan.complete(today)` or `Plan.abandon(today)`. A save the version check refuses is reloaded and retried up to three times, then alerts the team (§11) |
| The nightly retention run: the anonymizations, the deletions and the audit purge | Completes each night | A Healthchecks.io ping at the end of each run, as for backups | A missed ping alerts the team |
| Any event listener | Completes | Spring Modulith's staleness monitor, a resubmission every minute, and republication at restart ([ADR-064]) | Resubmits at most 5 times, then alerts the team with the listener's name |
| A route handed over or removed while the optimizer was down | Checked when it is back. Meanwhile the hand-over or cancellation completes, the route shows *not yet checked*, and publishing refuses (BR-70) | A job every minute while routes show *not yet checked* (FR-304, FR-315, FR-395, NFR-42, [ADR-058]) | Evaluates them and calls `Plan.recordRouteCheck` |
| `PlanRevised` → the driver confirms | The driver taps "Seen" (FR-97, FR-250) | `RouteRun`'s confirmed revision, on the live screen | The dispatcher sees who hasn't confirmed |
| A message | Accepted within 15 minutes of its trigger (NFR-190) | The sender | `dropStale` drops and counts it |
| A geocoding request | Answered | The geocoding retry, every 15 minutes for 24 hours (FR-64) | Leaves the customer flagged for a hand-placed location |
| A server restart | Nothing half-done survives silently | Startup recovery | Running jobs fail, queued daily solves stay, queued re-optimizations fail (BR-74, BR-85, NFR-12) |

---
## 13. Inside each module (hexagon)

```mermaid
flowchart LR
  subgraph IN["adapter/in"]
    WEB["web (generated from core-api)"]
    SSE["event stream"]
    JOB["job workers, schedulers, watchers"]
    LIS["listeners on events"]
  end
  subgraph APP["application"]
    API["api: interfaces other modules call"]
    SVC["one class per use case:<br/>outside calls first,<br/>then one transaction, one aggregate"]
    POUT["port/out: repositories, logs,<br/>RouteOptimizer, Geocoder, SmsGateway,<br/>PushNotifier, LiveUpdates, Clock, IdGenerator"]
  end
  subgraph DOM["domain (plain Java)"]
    M["aggregates, entities, value objects,<br/>sealed states, named errors"]
  end
  subgraph OUT["adapter/out"]
    DB[("jOOQ / PostgreSQL,<br/>the module's own schema")]
    HTTP["optimizer, OSRM, geocoder, SMS, push"]
  end
  IN --> SVC
  API --> SVC
  SVC --> M
  SVC --> POUT
  EVT["events/ (named interface):<br/>IDs, shared-kernel values, its own enums"]
  M -->|raises| EVT
  POUT -.->|implemented by| OUT
```

- **A module exposes three things**, each marked `@NamedInterface`: `api` (the use cases and queries
  other modules call), `events` (`<module>/events/`, a package beside `domain`, holding only IDs,
  shared-kernel values and its own enums, so the module's own domain may import it, §3.9), and `spi` (the
  ports it owns for others to implement).
- **An interface only where it earns its place.** Use cases that only the module's own adapters call are
  plain classes. A use case another module calls gets an interface in `api`, because that is the seam a
  remote client replaces if the module is split out. Secondary ports are interfaces because tests replace
  them with in-memory fakes.
- **`ValidatedSolution`** has one factory, `ValidatedSolution.of(ValidationReport)`, which refuses a
  report that isn't accepted. Only the optimizer adapter builds `ValidationReport`s and `Evaluation`s,
  from the optimizer's answer. So no code path can hand `Plan` an unvalidated solution (BR-15).

**Optimization service (Python)**

```mermaid
flowchart LR
  API["HTTP adapter<br/>solve, reoptimize, evaluate, baseline"] --> UC["use cases"]
  H["research harness CLI"] --> UC
  UC --> CORE[["domain<br/>ProblemFactory<br/>Problem: validate, cost, explain_drop<br/>Solution, TravelTimeModel, PenaltyScale"]]
  CORE --> SOL["Solver port<br/>ortools, alns, nearest_neighbour,<br/>pyvrp (harness only)"]
```

---

## 14. Testing the domain

- **Every invariant has a test that breaks it** and checks the named error, and every transition has one
  test that takes it and one per other state that is refused. The invariant tables in §4 are the test
  list.
- **No mocks.** Aggregates and values take facts, `now` and new IDs as parameters, so a domain test builds
  them with plain test builders (`aPlan().on(date).withRoute(...)`) and calls methods. Fakes appear only in
  application tests, for secondary ports.
- **Sequences, not just single steps.** For `Plan` and `Delivery`, a test runs seeded random sequences of
  commands and checks after every step that the invariants still hold: each order served or dropped once,
  one route per vehicle and driver, the first outcome wins. The seed is printed, so a failure replays.
- **Python** uses Hypothesis for generated instances, and the violation catalogue names the `RuleId` each
  crafted case must trigger (NFR-169).
- **The architecture is tested too**: Spring Modulith's `verify()`, the purity rules of §3.9, and
  import-linter in Python.

---

## 15. Growth: likely changes and where they go

The most likely changes, and whether each fits by adding code or needs existing aggregates rewritten.

| Likely change | Fits by |
|---|---|
| Excel and Windows-1255 imports (M7) | Adding a file reader. `ImportBatch` doesn't change |
| Geocoding, or a second geocoder | Adding a `Geocoder` adapter. `Customer.recordGeocode` already exists |
| Photo proofs, barcode checks (FR-98) | Adding a `Proof` variant. "A driver's *delivered* needs a proof" doesn't change |
| A new solver, or a new OR-Tools version | Adding a `Solver` adapter in Python. The validator and the objective don't change |
| A new message type or channel (email, WhatsApp) | Adding a `MessageType` and a channel adapter. `StopMessages`' frequency rules cover it |
| A second source of orders (an API, a shop integration) | Adding an inbound adapter that calls `PlaceOrder`. `Order`'s factory doesn't change |
| New report figures | Adding a read model over the published views |
| A tracking page for recipients | Adding a read model over `ArrivalProjection` |
| Vehicle types and road limits (FR-118) | Adding a `VehicleType` value to `Vehicle`, and allowed arcs to `Problem`. A new field, not a rewrite |
| More vehicles or orders than 15 and 200 | Changing two limits (BR-36, BR-101). `Plan` and `FleetRegister` still load whole at ten times the size |
| Customer accounts, billing or a CRM | Splitting Customers & Orders (§16) |
| Moving a module into its own service | Changing its adapters (§2.3) |
| **Pickups or returns** | **Not additive.** A second kind of stop, with its own capacity rule, changes `Plan`, `Problem` and the validator |
| **More than one company** | **Not additive.** Every aggregate would need the company's ID. Out of scope for the pilot |

---
## 16. Why it looks like this

| Decision | Reason |
|---|---|
| `Plan` holds intent; `Delivery` and `RouteRun` hold facts | Reports from 15 offline phones must never bump the version that protects dispatchers' edits (BR-76) |
| `Plan` is one aggregate per date, not one per route | Its rules cover every route at once: each order once (BR-07), one route per vehicle and driver (BR-39), atomic moves between routes, and revisions as consistent snapshots (BR-110). Its writers are few |
| `Delivery` per stop, not inside `RouteRun` | "First outcome wins, contradictions refused" is a per-stop rule (BR-18, BR-88, BR-90), and stops move between routes (BR-89) |
| Fact aggregates are created by the first fact | Publishing changes only `Plan`. Fifteen `RouteRun`s and 200 `Delivery`s are never written ahead of time |
| A command may create new aggregates of its module beside its one change | A new aggregate has no other writer. Creating `SolveJob` with the plan's *solving* state removes a hop that could fail by itself and strand the plan |
| No domain services | Every rule found an owner (§3.7). A service that read repositories or called other modules would be a use case in the wrong layer, and one that held a single aggregate's rule would leave that aggregate anaemic |
| Set rules owned by lock-read snapshots (`ActiveAdmins`, `DateCapacity`, `LaneCapacity`) | The method that could break the rule can't be called without the snapshot, and the lock and the database back it (§5) |
| `FleetRegister` holds every depot and vehicle | The set is small by rule (BR-36) and two of its rules are about the whole set (BR-36, BR-48). One version check replaces a set-wide check and a lock |
| `StopMessages` holds every message about a stop | The frequency rules are about the messages of one stop (NFR-191, FR-353, FR-384). One message per aggregate would split them |
| Positions, audit records, replies and opt-outs are records | They have no rule between entries. As aggregates they would be the busiest write path in the system, versioned for nothing |
| Settings split by context (`CompanyProfile`, `PlanningSettings`, `MessagingSettings`) | Each setting belongs with the rules that use it. One settings aggregate in Access would couple every context to it |
| Events only where they beat a direct call or doing nothing (§7) | Each event is a contract another module depends on. Seven candidates did no better than a call or nothing |
| Facts outrank intent, and a person is told | An offline phone can't be prevented from delivering a stop that was moved or cancelled meanwhile. One reconciliation path handles every case, online or offline |
| Hand-over is a `Plan` revision | Late reports are judged by route history (BR-110), and published route changes are audited (BR-77). Both break if the driver changes outside the plan |
| `PlanRevision` snapshots are appended, never loaded to change the present | One structure answers late-report judging, re-optimization history (FR-104), driver diffs (FR-249) and history kept after changes (BR-37), without slowing every change as the day goes on |
| Events sit in `<module>/events/`, outside `domain` | They hold only IDs, shared-kernel values and enums of their own, so a module's domain can raise its own events without importing a framework or another module, and other modules hear them through one named interface (§7.3) |
| A plan never published ends *abandoned* | A plan whose date has passed can't be solved, changed or published (BR-118). A named terminal state says so, and the midnight job that completes published plans already runs. The cost is one state, its transitions and tests (§9.1, [ADR-053]) |
| `ReoptimizationProposal` is immutable; accepting it is a revision that cites it | Accepting changes one aggregate, and a stale preview is caught by its basis (BR-25, BR-82) |
| Order status is derived, with facts first | It has three owners. Mirroring it through the actions of BR-93 to BR-96 invites drift, and putting *cancelled* above an outcome would hide a delivery |
| Revocation is a generation counter on `UserAccount`, and another on `Session` | "Refused at once" (NFR-177) without writing every session, with an exact integer comparison. A timestamp compared with a JWT's `iat`, which has one-second resolution, could refuse or accept wrongly within the same second |
| The validator, the objective, the conventions, the bucket factors and every drop reason live only in Python | One definition of feasibility (BR-42), so the product and the research score routes identically (BR-58, NFR-25) |
| `ArrivalProjection` propagates delay instead of re-timing | Live arrival times keep working while the optimizer is down, and the core still never applies a factor |
| Outside calls before the transaction | No transaction waits on HTTP; a stale version costs one repeated call, never a lost write |
| Ports owned upstream, implemented downstream | Keeps the module graph acyclic, which Spring Modulith checks in CI |
| One schema per module, and published views for Reporting | Ownership is visible and checkable, and a module can be split out without untangling joins |
| Customers and orders are one context | A customer exists here to give orders an address, a location, a phone and a default window; nothing about a customer lives apart from its deliveries. The two share one language, and one rule spans both: an import creates customers and orders all or nothing, after matching each address (FR-310). They are already separate aggregates that refer to each other by ID, so a later split moves packages and adds an API. Split when a customer gains a life of its own (accounts, billing, a portal), when orders arrive through a second channel that brings its own customer data, or when managing addresses grows into its own work |

---

## 17. Ubiquitous language

One word per concept, and one meaning per word within a context. The code uses these names, the screens
use the Hebrew column, and the documents use the English one. The Hebrew terms are the working terms. The
code and screens use them, and the company's dispatcher checks them when there is one: a term changes
where the company's own word differs. "—" in the Hebrew column marks a design term that no screen shows.

| Term | Context | Meaning | Hebrew | In code |
|---|---|---|---|---|
| Delivery date | All | The day a plan, its orders and its roster are for | תאריך חלוקה | `DeliveryDate` |
| Time window | Customers & Orders, Planning, Optimization | When an order may be served; hard, except for a leave-at-door order | חלון זמן | `TimeWindow` |
| Priority | Customers & Orders, Planning | 1 to 5, default 3; sets what dropping the order costs (BR-53, BR-54) | עדיפות | `Priority` |
| Account | Access | A person's sign-in identity, with an email and roles | חשבון משתמש | `UserAccount` |
| Role | Access | Admin, Dispatcher or Driver; one person can hold several | תפקיד | `Role`, `RoleSet` |
| Admin | All | Manages accounts and settings | מנהל מערכת | `Role.ADMIN` |
| Dispatcher | All | Plans and runs the day | סדרן | `Role.DISPATCHER` |
| Session | Access | One signed-in device | התחברות | `Session` |
| Step-up | Access | Confirming the password before an Admin-only operation | אימות סיסמה חוזר | `StepUp` |
| Credential link | Access | A one-time emailed link to set a password or confirm an email | קישור לאימות | `CredentialLink` |
| Fleet register | Fleet | The company's depots and vehicles | מאגר רכבים | `FleetRegister` |
| Depot | Fleet, Planning | Where vehicles start and end | מרכז הפצה | `Depot` |
| Vehicle | Fleet, Planning, Live Operations | A delivery vehicle. Not "van" | רכב | `Vehicle` |
| Driver | Fleet, Planning, Live Operations | A person who drives routes; their account holds the Driver role | נהג | `Driver` |
| Shift | Fleet | When a driver works on a date | משמרת | `Shift` |
| Roster | Fleet | For one date: who works, when, and with which vehicle | סידור עבודה | `DayRoster` |
| Customer | Customers & Orders | A delivery address with a name, phone and defaults | לקוח | `Customer` |
| Location | Customers & Orders | A customer's coordinates, geocoded or placed by hand. Not "pin" | מיקום | `Location` |
| Address flag | Customers & Orders | A doubt about a customer's location (FR-63) | סימון כתובת | `AddressFlag` |
| Order | Customers & Orders, Planning, Optimization | Goods to deliver to a customer on a date | הזמנה | `Order` |
| Attempt | Customers & Orders, Live Operations | Which try at delivering an order this is; a put-back starts the next one | ניסיון מסירה | `Attempt` |
| Put back | Customers & Orders | Returning a failed order for a new attempt (FR-258) | החזרה לתכנון | `Order.putBack` |
| Reschedule | Customers & Orders | Moving an order to another date (FR-315) | שינוי תאריך | `Order.reschedule` |
| Leave-at-door order | Customers & Orders | An order the recipient allows to be left at the door; its window is a preference (BR-119) | השארה ליד הדלת | `leaveAtDoor` |
| Recipient | Customers & Orders, Messaging | The person who receives an order and its messages; may differ from the customer | נמען | `Recipient` |
| Import | Customers & Orders | A file of orders, previewed and then confirmed all or nothing | ייבוא | `ImportBatch` |
| Order status | Customers & Orders | Derived: delivered, failed, cancelled, planned, dropped or unplanned (BR-33) | סטטוס הזמנה | `OrderStatus` |
| Plan | Planning | The intent for one delivery date: routes and dropped orders | תוכנית | `Plan` |
| Route | Planning | One vehicle's and driver's ordered stops in a plan | מסלול | `Route` |
| Stop | Planning, Live Operations, Messaging | An order's place on a route. Its ID stays the same while it moves between routes | תחנה | `PlannedStop`, `StopId` |
| Drop | Planning, Optimization | An order a plan leaves unserved, with its reason (FR-144) | לא שובצה | `DroppedOrder` |
| Solve | Planning | Computing a date's routes from scratch | חישוב מסלולים | `RequestSolve` |
| Solve job | Planning | One queued or running call to the optimizer | — | `SolveJob` |
| Lane | Planning | One of two job queues: daily solves and re-optimizations | — | `Lane` |
| Basis | Planning | What a result was computed from: for a plan, the solve that made its routes; for a proposal, the revision and time it started from | — | `SolveBasis`, `ProposalBasis` |
| Violation | Planning, Optimization | A broken rule the validator reports; while open it blocks publishing (BR-17). Not "flag" | הפרה | `Violation` |
| Pin | Planning | An order fixed to a vehicle (FR-91) | הצמדה לרכב | `Pin` |
| Lock | Planning | A route the dispatcher fixed against edits and solves (FR-147) | נעילה | `RouteLock` |
| Publish | Planning | Making a plan the drivers' plan, as revision 1 | פרסום | `Plan.publish` |
| Abandoned plan | Planning | A plan never published by the midnight after its date: final and read-only (BR-12, BR-102, BR-127). Not a *drop*, which is an unserved order | נזנחה | `Plan.Abandoned` |
| Revision | Planning | A numbered state of a published plan; every change makes one. Not "version" | גרסת תוכנית | `PlanRevision` |
| Version | All | The technical counter that optimistic locking checks. Never shown | — | `Version` |
| Re-optimization | Planning, Optimization | Solving the rest of a day from what has happened. Not "re-opt" in text | חישוב מחדש | `RequestReoptimization` |
| Proposal | Planning | A re-optimization's result, waiting for a dispatcher | הצעה | `ReoptimizationProposal` |
| Hand-over | Planning | Moving a route to another driver during the day | העברת מסלול | `Plan.handOver` |
| Withdrawn vehicle | Planning, Reporting | A vehicle a re-optimization took off its route after it had served stops (FR-267) | רכב שהוצא | `Withdrawal` |
| Served late | Planning | A stop whose fact arrived after the plan moved, dropped or cancelled it (§8) | — | `LateOutcome` |
| Report | Live Operations | What a driver sends about a stop: arrived, delivered or failed | דיווח | `StopReport` |
| Fact | Live Operations, Planning | Something that happened on the street: an arrival or an outcome | — | — |
| Delivery | Live Operations | The facts recorded for one stop | מסירה | `Delivery` |
| Outcome | Live Operations, Customers & Orders | Delivered or failed; the first one wins | תוצאת מסירה | `Outcome` |
| Proof | Live Operations | A signature or a photo; a driver's *delivered* needs one (FR-367) | אישור מסירה | `Proof` |
| Correction | Live Operations | A dispatcher's change of an outcome; the original stays (FR-333) | תיקון | `Delivery.correct` |
| Corrected time | Live Operations | A report's device time moved by the phone's clock offset (BR-114) | — | `CorrectedTime` |
| Route run | Live Operations | A route being driven: started, finished, the revision the driver confirmed | ביצוע מסלול | `RouteRun` |
| Position | Live Operations | A vehicle's reported location at a time | מיקום רכב | `VehiclePosition` |
| Projected arrival | Live Operations, Messaging | When a stop is now expected to be reached (§10.4) | זמן הגעה משוער | `ArrivalProjection` |
| At-risk stop | Live Operations | A stop projected to miss its window (FR-253) | תחנה בסיכון | — |
| Alert | Live Operations | Something a dispatcher must look at | התראה | `Alert` |
| Message | Messaging | An SMS to a recipient about a stop | הודעה | `OutgoingMessage` |
| Reply | Messaging | An SMS from a recipient | תשובה | `Reply` |
| Opt-out | Messaging | A recipient's choice to get no more messages | הסרה | `OptOut` |
| Rating | Messaging, Reporting | A recipient's answer, 1 to 10, to the rating question | דירוג | `Rating` |
| Baseline | Reporting | The nearest-neighbour plan for the same day, which the plan is compared with (FR-39) | קו בסיס | `Baseline` |
| On-time rate | Reporting | Delivered stops reached by their window's end, over delivered stops (FR-264, FR-265) | אחוז מסירות בזמן | `OnTimeRate` |
| Problem | Optimization | Everything one solve needs | — | `Problem` |
| Solution | Optimization | The routes and drops a solver proposes | — | `Solution` |
| Validator | Optimization | The single definition of feasibility (BR-42) | — | `Problem.validate` |
| Objective | Optimization | The cost every solver and every score uses | — | `Problem.cost` |
| Door stop | Planning, Optimization | The stop of a leave-at-door order | — | `door` |
| Reported prefix | Optimization | A route's stops already reported, fixed during a re-optimization (BR-10, BR-23) | — | `ReoptContext` |
| Travel bucket | Planning, Optimization | A part of the day with its own travel-time factor (FR-321) | — | `TravelBucket` |
| Buffer | Planning, Optimization | The safety margin added to travel times (FR-322) | — | `buffer` |

**Words not used:** "van" (say *vehicle*), "re-opt" (say *re-optimization*), "flag" for a plan's
violation (say *violation*; *address flag* is a different thing), "pin" for a customer's location (say
*hand-placed location*), "version" for a plan's revision, and "plan date" or "order date" (say *delivery
date*).

---

## 18. Still open and design-relevant

Nothing. These ADRs record the decisions of this document:

- [ADR-063]: a transaction changes one aggregate, every rule has one owner, and there are no domain
  services (§3.7, §5, §6.1).
- [ADR-064]: events only where they earn their place, delivered through Spring Modulith's registry (§7).
- [ADR-065]: facts outrank intent (§8).
- [ADR-066]: every asynchronous step has a watcher (§12).
- [ADR-067]: live arrivals propagate delay along stored legs when asked (§10.4).
- [ADR-040]: the core is a Spring Modulith monolith with one module per context (§2, §3.9, §13).
- [ADR-041]: one PostgreSQL schema per module (§2.3).
- [ADR-048], [ADR-049]: travel times from buckets and a buffer, and the drop-penalty unit (§4.9, §10.2).
- [ADR-053]: the plan lifecycle, with the *abandoned* state (§9.1).

[ADR-040]: ../../ADRs/ADR-040-core-is-a-spring-modulith-monolith-with-one-module-per-context.md
[ADR-041]: ../../ADRs/ADR-041-one-postgresql-schema-per-module-read-and-written-through-jooq.md
[ADR-048]: ../../ADRs/ADR-048-travel-times-from-buckets-and-a-buffer-with-no-live-traffic.md
[ADR-049]: ../../ADRs/ADR-049-drop-penalty-unit-k-scaled-by-priority.md
[ADR-050]: ../../ADRs/ADR-050-re-optimization-conventions-live-only-in-the-python-problem-factory.md
[ADR-053]: ../../ADRs/ADR-053-plan-lifecycle-publishing-stability-drop-reasons-and-abandoned-plans.md
[ADR-057]: ../../ADRs/ADR-057-concurrent-changes-to-a-plan-use-optimistic-locking.md
[ADR-058]: ../../ADRs/ADR-058-routes-changed-while-the-optimizer-is-down-are-checked-when-it-returns.md
[ADR-059]: ../../ADRs/ADR-059-plan-revisions-are-stored-as-rows.md
[ADR-061]: ../../ADRs/ADR-061-capacity-is-weight-and-volume.md
[ADR-063]: ../../ADRs/ADR-063-a-transaction-changes-one-aggregate-and-every-rule-has-one-owner.md
[ADR-064]: ../../ADRs/ADR-064-events-only-where-they-earn-their-place-through-the-modulith-registry.md
[ADR-065]: ../../ADRs/ADR-065-facts-outrank-intent.md
[ADR-066]: ../../ADRs/ADR-066-every-asynchronous-step-has-a-watcher.md
[ADR-067]: ../../ADRs/ADR-067-live-arrivals-propagate-delay-along-stored-legs-when-asked.md
[ADR-075]: ../../ADRs/ADR-075-retention-deletes-records-and-anonymizes-people-in-place.md
[ADR-083]: ../../ADRs/ADR-083-admins-and-dispatchers-get-alerts-by-stream-and-push-in-the-phone-app.md
[ADR-086]: ../../ADRs/ADR-086-csv-imports-upload-then-confirm-within-an-hour.md
[ADR-097]: ../../ADRs/ADR-097-the-shared-kernel-holds-the-domains-building-blocks.md

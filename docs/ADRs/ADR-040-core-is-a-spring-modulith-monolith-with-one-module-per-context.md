# ADR-040: The core is a Spring Modulith monolith with one module per bounded context, checked in CI

- **Status:** Proposed; would amend ADR-001
- **Date:** 2026-11-13

## Context

[ADR-001] chose domain-driven design, with every service built as ports and adapters. It set a
package layout, and left the context map and enforcement open:

- **The package layout.** In `core`, each context is one package under `com.dispatchflow`, split
  into `api`, `domain`, `application` and `adapter`. A context reaches another only through its
  `api` package.
- **The context map.** Which contexts `core` has was left open.
- **Enforcement.** The rules were conventions that review enforces. ArchUnit or Spring Modulith
  tests were the options, to be chosen when the first context landed, and recorded in a new ADR.

Today `core` holds `DispatchFlowApplication` and one package, `com.dispatchflow.web`, for the
error handler that answers when nothing else did. No context package exists. The pom declares
neither Spring Modulith nor ArchUnit. The only automated boundary check in the repository is the
optimizer's PyVRP test ([ADR-004]).

The domain layer design (DL) now defines the contexts, and it wins where documents disagree:

- **Nine bounded contexts:** Planning, Optimization, Live Operations, Customers & Orders, Fleet,
  Recipient Messaging, Reporting, Access and Audit ([DL §1][DL-1]). Optimization is the Python
  service ([ADR-002]); the other eight are modules of the core, named `planning`, `liveops`,
  `orders`, `fleet`, `messaging`, `reporting`, `access` and `audit`.
- **A one-way module graph** ([DL §2.1][DL-21]).
- **Rules between contexts** ([DL §2.3][DL-23]).
- **Six purity rules** for the domain ([DL §3.9][DL-39]).

The tech stack ([TS §2][TS-2]) gives the shape: a Java modular monolith plus the Python optimizer.
One deployable keeps transactions local and operations simple on one VM ([NFR-14]). Its first rule
for close calls is that an option needing a cluster, a message broker or a second database loses
([TS §1][TS-1]).

## Decision

We build the core as a Spring Modulith application, with one module per bounded context. The
modules, their dependencies and the domain's purity are checked in CI. [ADR-001]'s decision to
use DDD with ports and adapters stands, as does [ADR-002]'s split into a Java core and a Python
service. This ADR shapes the inside of the core.

**What changes in [ADR-001]:** its package layout. Each context's `api`, `domain`, `application`
and `adapter` packages, with `api` beside `domain`, become the module layout below, with `events`
and `spi` added. The rest of ADR-001 stands: DDD, ports and adapters, and the one-way dependencies.
The context map and the enforcement it left open are settled here.

- **Eight modules:** `access`, `fleet`, `orders`, `planning`, `liveops`, `messaging`, `reporting`
  and `audit`, each a package directly under the root package. Each is one bounded context.
- **The module graph points one way** ([DL §2.1][DL-21]).
  - An upstream module never imports a downstream type. When it needs something downstream, it owns
    a port that the downstream module implements. Planning owns `ExecutionFacts`, for example, and
    Live Operations implements it.
  - Every module also depends on the shared kernel, on Audit's `AuditLog` port, and on Access for
    the acting user ([DL §2.1][DL-21]).
  - **Access and Audit, this ADR's reading of DL.** Read literally, "every module" includes Access
    and Audit, so each would depend on the other, and DL §2.1 requires that "no arrow points
    back". We read it this way: Audit depends only on the shared kernel, and `AuditLog` takes
    shared-kernel IDs (the account ID and the session ID), never an `Actor`. Access can then depend
    on Audit, and the graph stays acyclic. To confirm with DL's owner.
  - Planning and Reporting reach the Python service over HTTP, through ports.
- **A module's contract is three packages,** each marked `@NamedInterface`: `api` (what other
  modules call), `events` (what they hear) and `spi` (the ports it owns for others to implement).
  Spring Modulith fails the build on any other dependency ([DL §2.3][DL-23], rule 4).
  - `@ApplicationModule(allowedDependencies = …)` in each module's `package-info.java` lists what
    it may use ([PL §11.1][PL-111]).
- **No domain object crosses a boundary.** A module's domain imports its own domain, the shared
  kernel and pure libraries. What another module's API returns is translated into the caller's own
  values by the caller's application layer ([DL §2.3][DL-23], rule 1).
- **The shared kernel is tiny:** the typed IDs, and nine values that mean the same everywhere. A
  change to it needs every module owner's agreement ([DL §2.3][DL-23], rule 5).
- **Inside a module,** the packages are `domain`, `events`, `application` (with `api`, `spi`,
  `port/out` and `service`) and `adapter` (with `in` and `out`) ([TS §4][TS-4],
  [PL §11.1][PL-111]). A use case gets an interface in `api` only when another module calls it
  ([DL §13][DL-13]).
- **The dependency rule** is in [TS §2.1][TS-2-1]. In short:
  - the domain depends on the JDK, JSpecify annotations, the shared kernel and pure libraries;
  - the application layer depends on the domain, other modules' `api`, `spi` and `events`, and on
    `@Transactional` and `@Retryable` from Spring, and on nothing else from it;
  - adapters depend on the application layer and frameworks, never on each other.
  - **Open conflict in the sources.** TS §2.1 allows `@Transactional` and `@Retryable` in the
    application layer, while [TS §6.2][TS-62] says `@Transactional` "is the only Spring annotation
    allowed in this layer". The ArchUnit rule for the layer needs one of them. This ADR leaves it
    for the team.
- **Six purity rules,** from [DL §3.9][DL-39], each an ArchUnit test:
  1. the domain imports only the JDK, the shared kernel, its own domain, its own module's `events`
     package, JSpecify and pure libraries (libphonenumber, JTS). DL §3.9 does not list `events`, but
     aggregates raise events ([DL §7.3][DL-73]), and an event lives in the raising module's `events`
     package. An event is a past-tense, immutable record with its own ID, `occurredAt`, the
     aggregate's ID and version, and the fact's key data (IDs, kinds, times). It is never a copy of
     the aggregate's state ([DL §7.3][DL-73]);
  2. no clock calls: `now` and `today` are parameters;
  3. no `UUID.randomUUID()`: factories take the new ID;
  4. no randomness, I/O or logging;
  5. no `new IllegalArgumentException` or `new IllegalStateException`: domain errors are named;
  6. only persistence adapters call `restore`, and the factories of the snapshots read under a lock.
- **Enforcement,** as [TS §2.2][TS-2-2] lists it, runs in CI on every change once adopted
  ([NFR-133]):

| Where | Tool | When |
|---|---|---|
| Core | Spring Modulith `ApplicationModules.verify()`: no cycles, and only exposed APIs used | Alpha |
| Core | ArchUnit: the six purity rules, and each persistence adapter using only its own module's schema ([ADR-041]) | Alpha |
| Core | Error Prone and NullAway with JSpecify annotations, in warning mode first, then as errors | January |
| Optimizer, research | import-linter: layers, and no `fastapi`, `pydantic`, `ortools`, `pyvrp` or `numba` in the domain | Alpha |

- **Spring Modulith 2.1,** the line that matches Spring Boot 4.1 ([TS §6][TS-6], Modules row). The
  pom's parent is Spring Boot 4.1.2.
- **Not decided here.** The implementation plan lists each of these as needing its own ADR
  ([PL §25][PL-25]):
  - the transaction rule ([DL §6.1][DL-61]) and the owner of every rule ([DL §3.7][DL-37]);
  - when an event earns its place, and the event publication registry ([PL §11.3][PL-113]).
- **Open points not yet on [PL §25][PL-25]'s list.** The plan does not list these two, and they
  need a decision of their own:
  - where the optimizer's solver adapters sit. [ADR-001] has a `solvers` package, and TS §4 puts
    them under `adapters/outbound/solvers`;
  - where the app-wide `web` package goes. The shared kernel's list has no place for it.

## Consequences

- **Easy:** a cycle, or a use of another module's internals, fails the build.
- **Easy:** a module's contract is visible in three packages, so a pull request shows what other
  modules gain or lose.
- **Easy:** each module can be booted alone in a test ([TS §17][TS-17]).
- **Easy:** the domain runs as plain unit tests, as in [ADR-001], and six rules keep it that way.
- **Easy:** splitting a module into a service later changes adapters only. Its API calls become
  generated HTTP clients, its events go through a broker, and its schema moves to its own database
  ([DL §2.3][DL-23]).
- **Hard:** each module carries the same package layout, including the ones whose rules are
  simple, such as Audit. [ADR-001] noted this cost.
- **Hard:** an upstream module that must react to a downstream fact needs a port that the
  downstream module implements, which is an indirection a direct call would not need.
- **Hard:** a change to the shared kernel needs the agreement of every module's owner.
- **Hard:** a query that spans modules goes through the other module's `api`. Reporting reads the
  views other modules publish ([ADR-041]).
- **Cost:** Spring Modulith and ArchUnit join the build. Error Prone needs JDK compiler flags that
  are a common source of build trouble, so it waits until January ([TS §2.2][TS-2-2]). The sources
  do not give the licence or maintenance status of these tools. Record both before each is added.
- **Risk:** the module split shapes every package name, as [ADR-001] said. A wrong split costs
  moves of code between modules once they hold code.
- **Risk:** until the build files exist, only review enforces these rules, as today.

## Alternatives considered

- **A Maven multi-module build,** one Maven module per context. Rejected: it gives compile-time
  boundaries, but nine modules of build configuration, and no event registry ([TS §6][TS-6]). The
  event-registry reason depends on the pending events ADR ([PL §25][PL-25]), which this ADR does
  not settle: if that ADR drops the registry, this reason falls with it.
- **ArchUnit alone.** Rejected: it checks packages, but has no event registry or module tests. It is
  still used, for the purity rules. The same dependency on the pending events ADR applies to the
  registry reason.
- **jMolecules' hexagonal rules.** Rejected: they check the same layering, at the price of
  annotating every type, and the package names already say it ([TS §2.2][TS-2-2]).
- **One service per context.** Rejected: nine services on one VM, and distributed transactions
  for no gain ([TS §2][TS-2]). [ADR-001] gave the same reason. A split follows the steps in
  [DL §2.3][DL-23].
- **Clean Architecture,** with interactors and presenters. Rejected: more ceremony than ports and
  adapters for the same separation ([TS §2][TS-2]).
- **[ADR-001]'s layout,** with `api` beside `domain`. Not chosen: it has no place for `events` and
  `spi`, which [DL §2.3][DL-23] requires.
- **Classic layers and a modular monolith without hexagonal internals.** Still rejected, for
  [ADR-001]'s reasons.

## Revisit when

- Two modules change together in most pull requests. The boundary is wrong; merge them
  ([ADR-001]).
- A module needs to scale, deploy or fail apart from the rest, or the single-VM requirement
  ([NFR-14]) is lifted. Split it, following [DL §2.3][DL-23].
- The January build cannot take Error Prone's compiler flags. Keep NullAway out until it can
  ([TS §2.2][TS-2-2]).
- `ApplicationModules.verify()` passes while a boundary breach reaches review. Add an ArchUnit rule
  for that case, or compare a Maven multi-module build.

[ADR-001]: ADR-001-domain-driven-design-with-hexagonal-architecture.md
[ADR-002]: ADR-002-core-backend-and-separate-optimization-service.md
[ADR-004]: ADR-004-pluggable-solver-port.md
[ADR-041]: ADR-041-one-postgresql-schema-per-module-read-and-written-through-jooq.md
[DL-1]: ../architecture/domain_layer/domain_layer_design.md#1-subdomains-and-bounded-contexts
[DL-13]: ../architecture/domain_layer/domain_layer_design.md#13-inside-each-module-hexagon
[DL-21]: ../architecture/domain_layer/domain_layer_design.md#21-module-graph
[DL-23]: ../architecture/domain_layer/domain_layer_design.md#23-rules-between-contexts
[DL-37]: ../architecture/domain_layer/domain_layer_design.md#37-domain-services-none
[DL-39]: ../architecture/domain_layer/domain_layer_design.md#39-purity
[DL-61]: ../architecture/domain_layer/domain_layer_design.md#61-the-rule
[DL-73]: ../architecture/domain_layer/domain_layer_design.md#73-shape-and-delivery
[NFR-14]: ../requirements/requirements.md#nfr-14
[NFR-133]: ../requirements/requirements.md#nfr-133
[PL-25]: ../init_impl_ref/technical_implementation_plan.md#25-decisions-to-record-as-adrs
[PL-111]: ../init_impl_ref/technical_implementation_plan.md#111-module-skeleton
[PL-113]: ../init_impl_ref/technical_implementation_plan.md#113-event-publication-making-retries-happen
[TS-1]: ../architecture/tech_stack.md#1-what-decides-close-calls
[TS-2]: ../architecture/tech_stack.md#2-architecture-style
[TS-4]: ../architecture/tech_stack.md#4-repository-layout
[TS-6]: ../architecture/tech_stack.md#6-core-service-java
[TS-62]: ../architecture/tech_stack.md#62-application-layer
[TS-17]: ../architecture/tech_stack.md#17-testing
[TS-2-1]: ../architecture/tech_stack.md#21-the-dependency-rule
[TS-2-2]: ../architecture/tech_stack.md#22-enforcement

# ADR-001: Domain-driven design, with every service built as ports and adapters

- **Status:** Accepted; amended by ADR-040
- **Date:** 2026-10-30

## Context

DispatchFlow has a domain with many rules and a long list of outside systems. Three people build
it in parallel.

- **The rules are the product.** Every entity belongs to exactly one organization ([BR-01]). A
  plan moves through a fixed lifecycle ([BR-12]). A route obeys time windows and vehicle
  capacities ([BR-03], [BR-05]). These rules must be testable without a web server, a database
  or a solver.
- **Outside systems change and fail.** The backend talks to OSRM for travel times and road
  geometry, to a geocoding provider, to the optimization service, and to a store for
  proof-of-delivery files ([Q-14] has not chosen one). Each must be replaceable, and each must
  be faked in a test.
- **Two solvers sit behind one port** ([ADR-004]). The service already needs a port-and-adapter
  shape, and the backend's view of that service must have the same shape.
- **The testing rules ask for it.** Tests go through the port, fake only our own ports, and
  use real containers for what we do not own (`.claude/CLAUDE.md`, [ADR-014]). That needs a
  place where "our port" is a named interface.
- **Three people work on separate parts.** A package boundary that matches an area of
  ownership keeps their changes apart.

No real context is built yet. `core` holds its entry point and a placeholder context,
`example`, that shows the layering with dummy names. The solver's `domain`, `application` and
`solvers` packages hold only placeholders
([code-structure.md](../architecture/code-structure.md)).

## Decision

We use domain-driven design for the shape of the system, and ports and adapters for the shape
of each service.

- **Strategic design.** A bounded context is the unit of modularity and ownership.
  - In `core`, each context is one package under `com.dispatchflow`, split into `api`,
    `domain`, `application` and `adapter`.
  - A context reaches another context only through that context's `api` package.
  - The solver service is one context, split into `domain`, `application`, `solvers` and
    `adapters`.
- **Tactical design.** Inside a context, the business rules live in the `domain` package, in
  entities, value objects and aggregates that guard their own invariants. A use case in
  `application` loads a domain object, calls it and saves it.
- **Ports and adapters.** Inside each context and in the solver:
  - the `domain` package imports no framework, I/O or transport type;
  - a port is an interface declared on the inside, never in `adapter`;
  - an adapter implements a port, or calls a use case, and depends inward;
  - there are no cycles between packages.
- **Out of scope.** The two frontends. [ADR-003] covers them.

### Not decided here

- **The context map.** Which bounded contexts `core` has is open.
  [bounded-contexts.md](../architecture/bounded-contexts.md) is an empty stub. This ADR fixes
  how a context is built, not which ones exist.
- **Enforcement.** Only the PyVRP boundary is checked by a test today. The rules above are
  conventions that review enforces. ArchUnit or Spring Modulith tests in `core` and
  import-linter in the solver are the options. We choose when the first context lands (see
  *Revisit when*).

## Consequences

- **Easy:** the business rules run as plain unit tests, with no Spring context and no database.
- **Easy:** an outside system is replaced, or faked, by writing one adapter. A test replaces
  OSRM or the optimization service with a fake of our own port.
- **Easy:** a context's `api` package shows what other contexts depend on, so the boundary is
  visible in a pull request.
- **Easy:** the backend and the solver share one vocabulary: port, adapter, domain.
- **Hard:** a persisted concept can exist as three classes: a domain object, a JPA entity and a
  transport DTO. Adding a field means touching each of them and the mappers between them.
- **Hard:** each context carries four packages, and a simple context carries the same
  boilerplate as a rule-heavy one.
- **Hard:** a query that spans contexts must go through each context's `api`. Each such query
  needs a method there.
- **Cost:** until an architecture test exists, a wrong import is caught only by a reviewer.
- **Risk:** the split into contexts shapes every package name. A wrong split costs a move of
  code between packages once several contexts exist.

## Alternatives considered

- **Classic layers (controller, service, repository) with JPA entities as the domain model.**
  Rejected: the domain then imports JPA and Spring, so a rule can only be tested with a
  framework around it. A call to OSRM or the optimization service would sit inside a service
  class, with no port to fake. It has no mapping cost, which is its one advantage.
- **Microservices, one per context.** Rejected: three people deploy to a single VM ([NFR-14]),
  and every service boundary would need its own versioned contract ([NFR-21]) and its own
  build. The context map is not decided either, so the boundaries would be guesses. A package
  boundary costs far less to move than a network boundary.
- **A modular monolith without hexagonal internals.** Rejected: it gives ownership boundaries
  but leaves each module's rules coupled to frameworks and I/O. The outside systems would not be
  swappable, and the testing rules would have nothing to fake.

## Revisit when

- **The first bounded context lands in `core`.** Add an architecture test in the same pull
  request: ArchUnit or Spring Modulith tests for the package rules. Record the choice in a new
  ADR. Do the same for import-linter in the solver when its `domain` package gets its first
  module.
- **The context map is decided.** Update [bounded-contexts.md](../architecture/bounded-contexts.md)
  and check the rules above against it.
- **Two contexts change together in most pull requests.** The boundary is wrong. Merge them.
- **A context turns out to hold no rule and no outside system.** Allow it a flatter layout.

[ADR-003]: ADR-003-dispatcher-console-and-driver-app.md
[ADR-004]: ADR-004-pluggable-solver-port.md
[ADR-014]: ADR-014-real-dependencies-in-containers-for-tests-and-local-runs.md
[BR-01]: ../requirements/requirements.md#business-rules-and-invariants
[BR-03]: ../requirements/requirements.md#business-rules-and-invariants
[BR-05]: ../requirements/requirements.md#business-rules-and-invariants
[BR-12]: ../requirements/requirements.md#business-rules-and-invariants
[NFR-14]: ../requirements/requirements.md#nfr-14
[NFR-21]: ../requirements/requirements.md#nfr-21
[Q-14]: ../requirements/requirements.md#open-questions

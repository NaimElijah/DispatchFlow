# Backend

The server side of DispatchFlow: two services that meet only at versioned contracts ([ADR-002]).
Each service has its own `CLAUDE.md` with its layout, rules and commands.

| Folder | What it does | Stack today |
|---|---|---|
| `core/` | The REST API for the web app and the phone app, and the system of record | Java 21, Spring Boot 4, Spring Data JPA, Flyway, PostgreSQL, Maven |
| `optimizer/` | Solves the routing problems `core` sends it | Python 3.14, FastAPI, NumPy, uv |

Google OR-Tools, our own Clarke-Wright + ALNS solver and the `nearest_neighbour` baseline arrive in
`optimizer/` with their adapters ([TS §7.1]); Spring Security arrives in `core/` with the Access
context ([TS §6]).

Accepted, and not in the tree yet, among others:

- [ADR-040]: Spring Modulith in `core`, one module per bounded context.
- [ADR-041]: jOOQ replaces JPA, with one PostgreSQL schema per module.

## Contracts

- `core` serves `contracts/client-api/v1` to both frontends.
- `optimizer` serves `contracts/optimization-api/v1`, and `core` is its only client.
- A change to either contract goes in the same PR as every side it touches, and each side is
  tested against the contract. See `contracts/CLAUDE.md`. [ADR-043] (Accepted) generates code from
  both contracts on both sides.
- Only `core` owns a database. `optimizer` keeps no business data of its own.

## Structure

Both services are built as ports and adapters around a domain package ([ADR-001]). Each
service's `CLAUDE.md` names its packages and what may import what.

## Rules for both services

- Time, IDs and randomness come in through ports (`Clock`, `IdGenerator`, seeded generators). The
  domain never reads the clock ([TS §2.1], rule 3). In `core`, `Clock` and `IdGenerator` live in
  `shared/application`, which no domain may import ([ADR-120]).
- The validator, the objective, the travel-time factors and the re-optimization conventions exist
  once, in the optimizer's Python domain. `core` calls them through the contract and never applies
  a bucket factor: OSRM gives it base durations only ([DL §10.2], [TS §6.4]).
- A solve works in seconds of the day in `Asia/Jerusalem`. The core converts with `ZonedDateTime`,
  and the database keeps instants as `timestamptz` and local values as `date` and `time`
  ([ADR-088], [TS §6.3]).
- Logs are structured JSON carrying the trace ID, and request bodies are never logged (NFR-19,
  NFR-20; the Observability rows of [TS §6] and [TS §7]).
- The core calls the optimizer over plain synchronous HTTP, with a client timeout of the solve limit
  plus 30 seconds ([TS §6.5], [ADR-051]).

## Test names

`GivenX_WhenY_ThenZ` in Java, `test_given_x_when_y_then_z` in Python.

[ADR-001]: ../../docs/ADRs/ADR-001-domain-driven-design-with-hexagonal-architecture.md
[ADR-002]: ../../docs/ADRs/ADR-002-core-backend-and-separate-optimization-service.md
[ADR-040]: ../../docs/ADRs/ADR-040-core-is-a-spring-modulith-monolith-with-one-module-per-context.md
[ADR-041]: ../../docs/ADRs/ADR-041-one-postgresql-schema-per-module-read-and-written-through-jooq.md
[ADR-043]: ../../docs/ADRs/ADR-043-generated-code-from-both-contracts-and-problem-details-errors.md
[ADR-051]: ../../docs/ADRs/ADR-051-one-solver-worker-per-lane-killed-30-seconds-past-its-limit.md
[ADR-088]: ../../docs/ADRs/ADR-088-israel-local-time-for-every-date-and-schedule.md
[ADR-120]: ../../docs/ADRs/ADR-120-the-application-layer-takes-an-actor-and-a-call-context-and-reads-through-query-ports.md
[DL §10.2]: ../../docs/architecture/domain_layer/domain_layer_design.md#102-one-travel-time-model-in-python
[TS §2.1]: ../../docs/architecture/tech_stack.md#21-the-dependency-rule
[TS §6]: ../../docs/architecture/tech_stack.md#6-core-service-java
[TS §6.3]: ../../docs/architecture/tech_stack.md#63-persistence
[TS §6.4]: ../../docs/architecture/tech_stack.md#64-ports-and-adapters
[TS §6.5]: ../../docs/architecture/tech_stack.md#65-core--optimizer-calls
[TS §7]: ../../docs/architecture/tech_stack.md#7-optimization-service-python
[TS §7.1]: ../../docs/architecture/tech_stack.md#71-the-hexagon

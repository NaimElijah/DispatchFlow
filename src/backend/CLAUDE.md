# Backend

The server side of DispatchFlow: two services that meet only at versioned contracts ([ADR-002]).
Each service has its own `CLAUDE.md` with its layout, rules and commands.

| Folder | What it does | Stack today |
|---|---|---|
| `core/` | The REST API for the web app and the phone app, and the system of record | Java 21, Spring Boot 4, Spring Data JPA, Flyway, PostgreSQL, Maven |
| `solver/` | Solves the routing problems `core` sends it | Python 3.14, FastAPI, uv |

Google OR-Tools, our own Clarke-Wright + ALNS solver and the `nearest_neighbour` baseline arrive in
`solver/` with their adapters ([TS §7.1]); Spring Security arrives in `core/` with the Access
context ([DL §1]).

Proposed by ADRs that the team has not accepted, and not in the tree yet:

- [ADR-040]: Spring Modulith in `core`, one module per bounded context.
- [ADR-041]: jOOQ replaces JPA, with one PostgreSQL schema per module.

## Contracts

- `core` serves `contracts/client-api/v1` to both frontends.
- `solver` serves `contracts/optimization-api/v1`, and `core` is its only client.
- A change to either contract goes in the same PR as every side it touches, and each side is
  tested against the contract. See `contracts/CLAUDE.md`. [ADR-043] (Proposed) generates code from
  both contracts on both sides.
- Only `core` owns a database. `solver` keeps no business data of its own.

## Structure

Both services are built as ports and adapters around a domain package ([ADR-001]). Each
service's `CLAUDE.md` names its packages and what may import what.

## Rules for both services

- Time, IDs and randomness come in through ports (`Clock`, `IdGenerator`, seeded generators). The
  domain never reads the clock ([TS §2.1], rule 3).
- The validator, the objective, the travel-time factors and the re-optimization conventions exist
  once, in the solver's Python domain. `core` calls them through the contract and never applies
  a bucket factor: OSRM gives it base durations only ([DL], design line 6; [TS §6.4]).

## Test names

`GivenX_WhenY_ThenZ` in Java, `test_given_x_when_y_then_z` in Python.

[ADR-001]: ../../docs/ADRs/ADR-001-domain-driven-design-with-hexagonal-architecture.md
[ADR-002]: ../../docs/ADRs/ADR-002-core-backend-and-separate-optimization-service.md
[ADR-040]: ../../docs/ADRs/ADR-040-core-is-a-spring-modulith-monolith-with-one-module-per-context.md
[ADR-041]: ../../docs/ADRs/ADR-041-one-postgresql-schema-per-module-read-and-written-through-jooq.md
[ADR-043]: ../../docs/ADRs/ADR-043-generated-code-from-both-contracts-and-problem-details-errors.md
[DL]: ../../docs/architecture/domain_layer/domain_layer_design.md
[DL §1]: ../../docs/architecture/domain_layer/domain_layer_design.md#1-subdomains-and-bounded-contexts
[TS §2.1]: ../../docs/architecture/tech_stack.md#21-the-dependency-rule
[TS §6.4]: ../../docs/architecture/tech_stack.md#64-ports-and-adapters
[TS §7.1]: ../../docs/architecture/tech_stack.md#71-the-hexagon

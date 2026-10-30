# Backend

The server side of DispatchFlow: two services that meet only at versioned contracts ([ADR-002]).
Each service has its own `CLAUDE.md` with its layout, rules and commands.

| Folder | What it does | Stack today |
|---|---|---|
| `core/` | The REST API for the console and the driver app, and the system of record | Java 21, Spring Boot 4, Spring Data JPA, Flyway, PostgreSQL, Maven |
| `solver/` | Solves the routing problems `core` sends it | Python 3.14, FastAPI, uv |

Google OR-Tools and our own Clarke-Wright + ALNS solver arrive in `solver/` with their adapters;
Spring Security arrives in `core/` with the identity context.

## Contracts

- `core` serves `contracts/client-api/v1` to both frontends.
- `solver` serves `contracts/optimization-api/v1`, and `core` is its only client.
- A change to either contract goes in the same PR as every side it touches, and each side is
  tested against the contract. See `contracts/CLAUDE.md`.
- Only `core` owns a database. `solver` keeps no business data of its own.

## Structure

Both services are built as ports and adapters around a domain package ([ADR-001]). Each
service's `CLAUDE.md` names its packages and what may import what.

## Test names

`GivenX_WhenY_ThenZ` in Java, `test_given_x_when_y_then_z` in Python.

[ADR-001]: ../../docs/ADRs/ADR-001-domain-driven-design-with-hexagonal-architecture.md
[ADR-002]: ../../docs/ADRs/ADR-002-core-backend-and-separate-optimization-service.md

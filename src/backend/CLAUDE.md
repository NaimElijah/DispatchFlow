# Backend

Two services that meet only at versioned contracts ([ADR-002]). Each has its own `CLAUDE.md`
with its layout, rules and commands.

## Contracts

- A change to either contract goes in the same PR as its provider and every consumer that calls
  what it changes, and each side is tested against the contract. See `contracts/CLAUDE.md`.
- Both services generate their boundary types from the contracts, never by hand ([ADR-043]).
  `contracts/CLAUDE.md` says when the generator comes.
- Only `core` owns a database. `optimizer` keeps no business data of its own.

## Rules for both services

- Time, IDs and randomness come in through ports (`Clock`, `IdGenerator`, seeded generators). The
  domain never reads the clock ([TS §2.1], rule 3).
- The validator, the objective, the travel-time factors and the re-optimization conventions exist
  once, in the optimizer's Python domain. `core` calls them through the contract and never applies
  a bucket factor: OSRM gives it base durations only ([DL §10.2], [TS §6.4]).
- A solve works in seconds of the day in `Asia/Jerusalem`. The core converts with `ZonedDateTime`.
  The database keeps instants as `timestamptz`, a local date and time as `timestamp(6)`, a date
  and a time of day as `date` and `time`, and a solve's seconds as `bigint` beside a `date`
  ([ADR-088], [TS §6.3]); its session runs in UTC.
- Logs are structured JSON carrying the trace ID, and request bodies are never logged (NFR-19,
  NFR-20; the Observability rows of [TS §6] and [TS §7]).
- The core calls the optimizer over plain synchronous HTTP, with a client timeout of the solve limit
  plus 30 seconds ([TS §6.5], [ADR-051]).
- Both images run as the non-root user `dispatchflow` ([ADR-019]). Keep it that way.

## Test names

`GivenX_WhenY_ThenZ` in Java, `test_given_x_when_y_then_z` in Python.

[ADR-002]: ../../docs/ADRs/ADR-002-core-backend-and-separate-optimization-service.md
[ADR-019]: ../../docs/ADRs/ADR-019-container-images-run-as-non-root.md
[ADR-043]: ../../docs/ADRs/ADR-043-generated-code-from-both-contracts-and-problem-details-errors.md
[ADR-051]: ../../docs/ADRs/ADR-051-one-solver-worker-per-lane-killed-30-seconds-past-its-limit.md
[ADR-088]: ../../docs/ADRs/ADR-088-israel-local-time-for-every-date-and-schedule.md
[DL §10.2]: ../../docs/architecture/domain_layer/domain_layer_design.md#102-one-travel-time-model-in-python
[TS §2.1]: ../../docs/architecture/tech_stack.md#21-the-dependency-rule
[TS §6.3]: ../../docs/architecture/tech_stack.md#63-persistence
[TS §6.4]: ../../docs/architecture/tech_stack.md#64-ports-and-adapters
[TS §6.5]: ../../docs/architecture/tech_stack.md#65-core--optimizer-calls
[TS §6]: ../../docs/architecture/tech_stack.md#6-core-service-java
[TS §7]: ../../docs/architecture/tech_stack.md#7-optimization-service-python

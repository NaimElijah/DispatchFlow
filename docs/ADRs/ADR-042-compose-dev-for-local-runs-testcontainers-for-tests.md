# ADR-042: Local runs start their services from `compose.dev.yaml`; tests keep Testcontainers

- **Status:** Accepted; amended by ADR-123
- **Date:** 2026-11-13

## Context

Today, under [ADR-014], the integration tests and local runs both start one real PostgreSQL
through Testcontainers. `TestcontainersConfiguration` declares the image `postgres:18.6`, and
`just core-run` runs `mvnw spring-boot:test-run`, which starts `TestDispatchFlowApplication` on
port 8080 against a throwaway database. The repository holds no Compose file. ADR-014 deferred
one and named its own trigger: "The Compose file exists. Decide whether `just core-run` should use
its database."

The proposed design fires that trigger. A laptop has to run more than a database: OSRM for travel
times, the optimizer, a mail catcher, fake SMS and geocoder providers, and a tile server
([PL §5]). The forces:

- **The whole stack runs on a laptop with no outside network** ([NFR-175], [TS §5]).
- **Production is one Compose file** ([NFR-16], [TS §1], rule 1). The design keeps `compose.yaml`
  and `compose.dev.yaml` side by side in `deploy/` ([TS §4]).
- **Laptops are Windows and macOS** ([NFR-208]). The plan warns that Docker, line-ending and WSL 2
  problems on Windows "eat a week if they're found in December" ([PL §5]).
- **A test needs the real database and one nobody else uses**, as ADR-014 found. A mock or an
  in-memory database tests our expectations of PostgreSQL, not PostgreSQL.

## Decision

We start local services from `deploy/compose.dev.yaml`, and we keep Testcontainers for the tests.

- **Local runs use the Compose file.** The core runs from the IDE with Spring Boot's Docker Compose
  support, which starts the services below ([PL §5], [TS §5], the *Local services* row).
- **The services and their ports** ([PL §5], the `compose.dev.yaml` table):

  | Service | Image | Port | Notes |
  |---|---|---|---|
  | `postgres` | `postgres:18` | 5432 | One database, `dispatchflow` |
  | `osrm` | OSRM 26.x | 5000 | Files from `data/osrm/` |
  | `optimizer` | built from the optimizer's folder ([ADR-037]) | 8081 | Runs from source, with reload |
  | `mailpit` | Mailpit | 8025, 1025 | Catches every email |
  | `wiremock` | WireMock | 8089 | Fake SMS provider and fake GovMap |
  | `tiles` | Caddy | 8090 | Serves `data/tiles/israel.pmtiles` |

- **Tests keep Testcontainers**, as ADR-014 decided. Integration and adapter tests start a real
  PostgreSQL in a container and never an in-memory stand-in ([TS §17.1], the *Adapters* row). Each
  run starts from an empty database.
- **Docker is the prerequisite:** Docker Desktop on Windows and macOS, or Docker Engine on Linux,
  with Compose v2 ([TS §5], the *Containers on laptops* row). Each laptop is checked in the first
  two days of M0: Docker Desktop running, line endings (`.gitattributes`) and the WSL 2 backend
  ([PL §5], the Windows note).
- **A long-lived local database has a reset.** The plan lists `just db-reset` and `just demo`,
  which resets the database and seeds the demo day ([PL §5]).
- **Decided in the M0 restructure pull request** (deferred to it on 2026-11-14): whether
  `TestDispatchFlowApplication` and `just core-run` stay once `just dev` exists ([PL §5]).

## Consequences

- **Easy:** one command family starts everything the core talks to, and Mailpit and WireMock stand
  in for email, SMS and geocoding, so no run needs an outside network ([NFR-175]).
- **Easy:** the tests still run against the real engine, in a database of their own.
- **Easy:** the Compose files sit together in `deploy/` ([TS §4]). TS §4 gives no reason for it.
  [TS §5] says the Compose file is "already shared with production", but as its reason for preferring
  Compose over Testcontainers at development time (see Alternatives), not for where the files sit.
- **Hard:** a laptop runs six containers where it runs one today, and OSRM needs its extract built
  first (`just data`, [PL §5]).
- **Hard:** `postgres:18` is named in `compose.dev.yaml`, and `postgres:18.6` in
  `TestcontainersConfiguration` today. ADR-014 asked for the version "in one place". The sources do
  not say how the two are kept in step.
- **Cost:** Docker on every laptop and on the CI runner, as before.
- **Risk:** the local database now outlives a run, so state from an earlier run can leak into the
  next one. ADR-014's throwaway container could not do that. `just db-reset` is the control.
- **Risk:** the Windows checks of [PL §5] fail late. Docker Desktop and WSL 2 are the likeliest
  cause.

## Alternatives considered

- **Testcontainers at development time, as ADR-014 has it.** Not chosen: [TS §5] calls it "good
  too", but the Compose file is already shared with production, and a laptop needs six services
  rather than a database.
- **Starting containers by hand.** Rejected: "someone always forgets one" ([TS §5]).
- **Podman instead of Docker.** Rejected: it works, but Testcontainers, Spring Boot's Compose
  support and the production Compose file each need extra setup on Windows ([TS §5]).
- **An in-memory H2 database in tests.** Still rejected, as in ADR-014: its SQL behaves
  differently from PostgreSQL's ([TS §6], the *Database* row).
- **A shared development database, or a PostgreSQL installed by hand.** Still rejected, as in
  ADR-014: tests would share state, and each machine would drift.

## Revisit when

- A team laptop cannot run the Compose stack after the M0 checks. Offer Testcontainers for local
  runs on that machine, or fix the setup, before more services are added.
- `compose.dev.yaml` and production's `compose.yaml` diverge until the reason [TS §5] gives for
  preferring Compose at development time, that the Compose file is already shared with production,
  no longer holds.
- The two PostgreSQL version strings disagree and a migration passes in one and fails in the
  other. Put the version in one place.
- Starting six containers is the main wait in the edit-and-run loop. Weigh Testcontainers for a
  core-only run.

[ADR-014]: ADR-014-real-dependencies-in-containers-for-tests-and-local-runs.md
[ADR-037]: ADR-037-flat-layout-under-src-with-deploy-and-infra.md
[NFR-16]: ../requirements/requirements.md#nfr-16
[NFR-175]: ../requirements/requirements.md#nfr-175
[NFR-208]: ../requirements/requirements.md#nfr-208
[PL §5]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#5-repository-and-local-environment
[TS §1]: ../architecture/tech_stack.md#1-what-decides-close-calls
[TS §4]: ../architecture/tech_stack.md#4-repository-layout
[TS §5]: ../architecture/tech_stack.md#5-development-environment-and-workflow
[TS §6]: ../architecture/tech_stack.md#6-core-service-java
[TS §17.1]: ../architecture/tech_stack.md#171-by-layer

# ADR-007: PostgreSQL is the system of record, and Flyway migrations own its schema

- **Status:** Accepted
- **Date:** 2026-10-30

## Context

The core backend is the system of record: orders, fleets, users, plans and statuses
([ADR-002]). It needs a database, and a way to change that database's schema that every
developer, every test run and every deployment applies the same way.

The forces:

- **The data is relational.** The implementation plan lists the core's data as orders, fleets,
  users, plans and statuses, and its technology table gives the database as "PostgreSQL,
  PostGIS optional" for "relational data plus geo queries".
- **Geo queries may come.** No feature needs a spatial query yet. When the first one does, the
  team adopts PostGIS ([Q-18]).
- **Solve jobs may need a queue.** The plan says a job table in PostgreSQL is enough for a queue
  at this scale. How solves are queued is not decided yet ([ADR-002]); if it is a job table, the
  queue lives in the database the core already has.
- **Tests should run against the real database** ([ADR-014]). The core's integration tests start
  `postgres:18.6` through Testcontainers (`TestcontainersConfiguration`).
- **The schema must be reproducible.** A schema that is edited by hand, or generated at start-up,
  gives each environment a chance to differ.

Today `src/backend/core` declares Spring Data JPA, the Flyway starter, `flyway-database-postgresql`
and the PostgreSQL driver in `pom.xml`. `src/main/resources/db/migration/` holds one migration,
`V0_1__create_example_widget.sql`, for the placeholder example context; no real table exists
yet. `application.properties` sets `spring.jpa.hibernate.ddl-auto=validate`.

## Decision

We use PostgreSQL as the system of record, and we change its schema only through Flyway
migrations.

- **The core is the only service that owns a database.** The solver service has none
  ([ADR-002]). Anything it needs arrives in a request, through the optimization API
  ([ADR-006]).
- **A migration is a plain SQL file** in `src/backend/core/src/main/resources/db/migration/`.
  Flyway applies the files in version order.
- **An applied migration is never edited.** A change to the schema is a new migration. Flyway
  records a checksum for each applied migration and refuses to start when a file no longer
  matches.
- **Hibernate does not create or change the schema.** JPA maps the entities onto tables that
  migrations created. Persistence is an adapter, and the domain does not import JPA types
  ([ADR-001]).
- **PostGIS stays out until a feature needs it.** The first spatial query ([Q-18]) brings it in,
  as a migration that enables the extension.

## Consequences

- **Easy:** one database to run, back up and reason about, for relational data, the job queue
  if it is a table, and, later, spatial queries.
- **Easy:** every environment reaches the same schema by running the same files, and a schema
  change is a reviewable SQL diff in the pull request that needs it.
- **Easy:** integration tests start from an empty PostgreSQL container and apply the real
  migrations ([ADR-014]).
- **Hard:** migrations only move forward. A mistake in an applied migration is corrected by
  another migration, not by an edit.
- **Hard:** `ddl-auto=validate` checks only that each mapped table and column exists with a
  compatible type. It does not check constraints, nullability or indexes; the migrations and the
  integration tests that run them are the check for those.
- **Hard:** the integration tests need Docker.
- **Risk:** deleting a migration that a persistent database has applied stops the core from
  starting there (Flyway reports an applied migration it cannot resolve). The example's `V0_1`
  can be deleted only while no persistent database exists; after that, a new migration drops
  its table instead ([code-structure.md](../architecture/code-structure.md)).
- **Cost:** a schema change takes a SQL file, written by hand, where an entity edit with
  generated DDL would take none.

## Alternatives considered

- **Liquibase.** Rejected: plain SQL migrations with Flyway cover the need.
- **Hibernate auto-DDL** (`spring.jpa.hibernate.ddl-auto` creating or updating tables).
  Rejected: it leaves no record of which change ran, and no SQL file for a reviewer to read in
  the pull request.
- **A document database.** Rejected: the plan describes the data as relational, and it names
  PostgreSQL as enough for a job queue too.
- **MySQL.** Rejected: the plan names PostgreSQL, with PostGIS as the route to geo queries
  ([Q-18]), and no requirement asks for MySQL.

## Revisit when

- The first feature needs a spatial query ([Q-18]). Add PostGIS, and record what it needs from
  the Docker image used in tests and deployment.
- Solve jobs outgrow a table used as a queue, or another service needs its own database. The
  plan names RabbitMQ as the option for a queue.

[ADR-001]: ADR-001-domain-driven-design-with-hexagonal-architecture.md
[ADR-002]: ADR-002-core-backend-and-separate-optimization-service.md
[ADR-006]: ADR-006-versioned-openapi-contracts.md
[ADR-014]: ADR-014-real-dependencies-in-containers-for-tests-and-local-runs.md
[Q-18]: ../requirements/requirements.md#open-questions

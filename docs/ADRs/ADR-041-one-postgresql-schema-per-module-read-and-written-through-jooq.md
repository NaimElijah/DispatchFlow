# ADR-041: Each module owns a PostgreSQL schema, read and written through jOOQ by a role that owns no table

- **Status:** Proposed; would supersede ADR-007
- **Date:** 2026-11-13

## Context

[ADR-007] chose PostgreSQL as the system of record and Flyway migrations as the only way to change
its schema. It had JPA map the entities onto tables that the migrations created, with Hibernate
never changing the schema ([ADR-001]: persistence is an adapter).

The core is set up that way today:

- `src/backend/core/pom.xml` declares `spring-boot-starter-data-jpa` and its test starter, the
  Flyway starter, `flyway-database-postgresql` and the PostgreSQL driver. It declares no jOOQ.
- `application.properties` sets `spring.jpa.hibernate.ddl-auto=validate` and
  `spring.jpa.open-in-view=false`.
- `src/main/resources/db/migration/` holds only a `.gitkeep`. No migration and no entity exist.
- The integration tests start `postgres:18.6` through Testcontainers ([ADR-014]). No database role
  is set up: the core connects as the user the container gives it.

The domain layer design (DL) and the tech stack (TS) change three things:

- **Each module owns its tables, in its own PostgreSQL schema.** No SQL reads another module's
  schema. Reporting reads read-only views that the owning module publishes ([DL §2.3][DL-23],
  rule 3). The reason in [DL §16][DL-16]: ownership is visible and checkable, and a module can be
  split out without untangling joins ([ADR-040]).
- **The persistence adapter is jOOQ,** marked "Proposed (needs an ADR)" ([TS §6][TS-6]).
  [TS §22][TS-22] lists it, and "One PostgreSQL schema per module", as decisions to record in an
  ADR.
- **The application must not own a table.** [PL §12][PL-12] and [TS §6.3][TS-63] say why: an owner
  can always grant itself back whatever was revoked. The schema has an `audit_record` table that the
  application may only insert into and read. Audit owns an append-only trail ([DL §1][DL-1]), and
  every change leaves one record ([NFR-23]).

The forces behind the persistence choice, from [TS §6][TS-6]:

- The domain imports no jOOQ, Spring, Jackson or Jakarta type ([DL §3.9][DL-39]). Persisted
  aggregates are rebuilt from rows by the aggregate's `restore` factory.
- A plan can hold 200 stops, and a change to it must not rewrite every child row.
- PostgreSQL features carry real rules: partial unique indexes, `FOR UPDATE SKIP LOCKED`, advisory
  locks and `jsonb`.
- Reporting's read models are SQL over published views ([PL §21][PL-21]).

## Decision

We keep PostgreSQL and Flyway, replace Spring Data JPA with jOOQ, and give each module its own
schema.

- **Carried forward from [ADR-007]:**
  - PostgreSQL is the system of record, at version 18 ([TS §6][TS-6]).
  - Flyway migrations are the only way to change the schema. They are plain SQL, move only
    forward, and an applied migration is never edited.
  - The core is the only service that owns a database.
  - PostGIS stays out until a feature needs a spatial query ([Q-18]).
- **jOOQ Open Source Edition replaces Spring Data JPA** in the persistence adapters.
  - The code is generated from the Flyway-migrated schema.
  - Mappers between jOOQ records and aggregates are written by hand, so the domain carries no
    annotations.
- **Each module owns one PostgreSQL schema,** in the one database `dispatchflow` ([PL §5][PL-5]):
  `access`, `fleet`, `orders`, `planning`, `liveops`, `messaging`, `reporting` and `audit`
  ([PL §12][PL-12]).
  - A module's adapter imports only its own schema's generated classes. ArchUnit checks it
    ([ADR-040]).
  - **The exception is Reporting.** Its adapter may also import the published views of the modules
    it depends on (Planning, Live Operations, Customers & Orders, [DL §2.1][DL-21]), and nothing
    else from their schemas ([DL §2.3][DL-23], rule 3). Without it, the rule above would forbid
    the reads that DL §2.3 requires.
  - No SQL touches another module's schema.
- **Reporting reads published views.** Each module that Reporting reads publishes read-only views
  as part of its API, such as `planning.report_stops` and `liveops.report_outcomes`
  ([PL §21][PL-21]). A module can change its tables, as long as its views keep their columns.
- **Two database roles** ([TS §6.3][TS-63]):
  - Flyway runs as `dispatchflow_owner`, which owns every table.
  - The core connects as `app`, which has only the grants it needs: `INSERT` and `SELECT` on
    `audit_record`, and nothing else on it. The application never owns a table.
- **Code generation runs the migrations** against a Testcontainers PostgreSQL, through the
  `testcontainers-jooq-codegen-maven-plugin`, so the generated code matches the real schema.
  Docker must be running for `./mvnw verify` ([TS §6.3][TS-63]). It already had to be, for the
  integration tests ([ADR-013], [ADR-014]; [ADR-042], Proposed, would supersede ADR-014 and keeps
  Testcontainers for tests).
- **Not repeated here:** the conventions that follow, such as the version check on a root table,
  loading an aggregate whole with `MULTISET`, and the column types. [TS §6.3][TS-63] holds them.
- **Not decided here: how the migrations get the owner role.** [PL §23][PL-23] has the deploy step
  "run Flyway through the core's startup". If the core process runs the migrations, it holds the
  `dispatchflow_owner` credentials at startup, and that weakens the reason for two roles: an owner
  can re-grant itself, so the core could too. The alternative is a one-shot migration step, in its
  own container or command, that alone gets the owner credentials, while the core sees only `app`.
  The team picks one. Either way, an integration test connected as `app` asserts that `UPDATE` and
  `DELETE` on `audit_record` fail (SQLSTATE 42501).

## Consequences

- **Easy:** the SQL is generated from the real schema, so a column that does not exist fails at
  build time. The domain stays free of persistence annotations.
- **Easy:** PostgreSQL features are first-class: `SKIP LOCKED` for the solve queue, `ON CONFLICT`,
  advisory locks, and the partial unique indexes behind the plan-per-date rule.
- **Easy:** Reporting's queries use the same tool, and the owning modules stay free to change their
  tables.
- **Easy:** the application cannot update or delete an audit record, because its role has no such
  grant. The database enforces it, not the code ([NFR-23]).
- **Easy:** a module can move to its own database later, as [DL §2.3][DL-23] describes.
- **Hard:** every aggregate needs a hand-written repository and mappers. A new column touches the
  migration, the regenerated code, the mapper and the aggregate. [ADR-001] noted this shape as a
  cost.
- **Hard:** optimistic locking and the writes of child rows are written by hand ([TS §6.3][TS-63]).
- **Hard:** a Maven build that runs code generation needs Docker even when tests are skipped. The
  bootstrap in [PL §5][PL-5] runs `./mvnw -q verify -DskipTests`, and the core `Dockerfile` builds
  its image with `./mvnw package -DskipTests`, without Docker. Whether the generated code is committed, or the
  image is built from a jar built elsewhere, is not stated in the sources.
- **Hard:** a role with grants only needs every migration that adds a table to grant `app` what it
  needs. The sources do not say whether the integration tests connect as `app`. If they connect as
  the owner, a missing grant shows up only after deployment.
- **Hard:** how Flyway gets `dispatchflow_owner` while the core runs as `app` is open, between
  Flyway in the core's startup ([PL §23][PL-23]) and a separate one-shot migration step. See
  "Not decided here" above.
- **Cost:** `spring-boot-starter-data-jpa` and its test starter leave `src/backend/core/pom.xml`,
  with the `spring.jpa.*` settings in `application.properties`. These files still describe JPA as
  today's setup, and change in the same pull request (each checked with a search):
  `src/backend/core/CLAUDE.md` (lines 5 and 55), `src/backend/CLAUDE.md` (line 8),
  `.claude/agents/df-code-reviewer.md` (line 62), `README.md` (line 139),
  `docs/architecture/architecture.md` (line 89) and `docs/architecture/data-model.md` (line 4).
  `src/backend/core/CLAUDE.md` no longer carries JPA entity or Spring Data rules, and
  `.claude/agents/df-tester.md` never named JPA, so neither needs a change for that.
- **Cost:** jOOQ and the code-generation plugin are new dependencies. TS §21 says the Open Source
  Edition supports the latest PostgreSQL versions; not checked here. The sources do not state its
  licence or maintenance status. Record both against the licence allow-list ([TS §18][TS-18]) before
  adding it.
- **Risk:** [NFR-77] cites [ADR-007] ("Every database change follows ADR-007"). Once this ADR is
  accepted, that citation needs repointing.
- **Risk:** the ArchUnit rule checks which schema's generated classes an adapter uses
  ([TS §2.2][TS-2-2]). jOOQ's plain-SQL API, the methods annotated `@PlainSQL` such as
  `DSL.field(String)` and `DSL.condition(String)`, takes a string and is where an injection, or SQL
  that names another module's schema, could slip in unchecked. The sources do not say how that
  would be caught. Not checked: whether an ArchUnit rule can ban those calls.
- **Risk:** PostgreSQL 19 is due on 2026-11-19. The tech stack stays on 18 for the project
  ([TS §21][TS-21]).

## Alternatives considered

- **JPA and Hibernate,** as [ADR-007] and the pom have it. Rejected: a pure domain needs a second
  set of entity classes plus mappers, and lazy loading and dirty checking do not earn their cost
  ([TS §6][TS-6]).
- **Spring Data JDBC.** Rejected: it is designed for aggregates, but it saves a changed collection
  by deleting and reinserting every child row, so every edit would rewrite a 200-stop plan.
- **MyBatis.** Rejected: SQL in XML, with no compile-time checking.
- **`JdbcTemplate`.** Rejected: no type safety across about 40 tables ([PL §12][PL-12]).
- **One shared schema for all modules.** Rejected: ownership would not be visible or checkable, and
  splitting a module out would mean untangling joins ([DL §16][DL-16]).
- **The core connecting as the table owner.** Rejected: an owner can always grant itself back
  whatever was revoked ([TS §6.3][TS-63]).
- **Liquibase.** Still rejected: XML or YAML changelogs and rollbacks, which expand-then-contract
  migrations do not need ([TS §6][TS-6]).
- **MySQL.** Still rejected: no partial indexes, which [BR-34] and [BR-38] rely on.
- **H2 in tests.** Still rejected: it behaves differently, and Testcontainers runs the real
  database.
- **Hibernate auto-DDL and a document database.** Still rejected, for [ADR-007]'s reasons.

## Revisit when

- jOOQ's Open Source Edition does not support the PostgreSQL version in use, or a feature the team
  needs is only in a paid edition.
- Code generation makes the core's CI job approach its 20-minute timeout in `ci.yml`, or a
  contributor cannot run Docker for the build.
- A module is split out as a service ([DL §2.3][DL-23]). Its schema moves to its own database, and
  the Reporting views over it need a replacement.
- The first feature needs a spatial query ([Q-18]). Add PostGIS, and record what it needs from the
  Testcontainers image the code generation uses, as well as from the deployment image
  ([ADR-007]).
- Solve jobs outgrow a table used as a queue ([ADR-007]).

[ADR-001]: ADR-001-domain-driven-design-with-hexagonal-architecture.md
[ADR-007]: ADR-007-postgresql-with-flyway-migrations.md
[ADR-013]: ADR-013-java-tooling-maven-wrapper-and-spotless.md
[ADR-014]: ADR-014-real-dependencies-in-containers-for-tests-and-local-runs.md
[ADR-040]: ADR-040-core-is-a-spring-modulith-monolith-with-one-module-per-context.md
[ADR-042]: ADR-042-compose-dev-for-local-runs-testcontainers-for-tests.md
[DL-1]: ../architecture/domain_layer/domain_layer_design.md#1-subdomains-and-bounded-contexts
[DL-16]: ../architecture/domain_layer/domain_layer_design.md#16-why-it-looks-like-this
[DL-21]: ../architecture/domain_layer/domain_layer_design.md#21-module-graph
[DL-23]: ../architecture/domain_layer/domain_layer_design.md#23-rules-between-contexts
[DL-39]: ../architecture/domain_layer/domain_layer_design.md#39-purity
[BR-34]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[BR-38]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[NFR-23]: ../requirements/requirements.md#nfr-23
[NFR-77]: ../requirements/requirements.md#nfr-77
[PL-5]: ../init_impl_ref/technical_implementation_plan.md#5-repository-and-local-environment
[PL-12]: ../init_impl_ref/technical_implementation_plan.md#12-database-schema
[PL-21]: ../init_impl_ref/technical_implementation_plan.md#21-reporting-and-baseline
[PL-23]: ../init_impl_ref/technical_implementation_plan.md#23-deployment-and-operations
[Q-18]: ../requirements/requirements.md#open-questions
[TS-2-2]: ../architecture/tech_stack.md#22-enforcement
[TS-6]: ../architecture/tech_stack.md#6-core-service-java
[TS-18]: ../architecture/tech_stack.md#18-cicd-and-supply-chain
[TS-21]: ../architecture/tech_stack.md#21-version-baseline
[TS-22]: ../architecture/tech_stack.md#22-open-decisions
[TS-63]: ../architecture/tech_stack.md#63-persistence

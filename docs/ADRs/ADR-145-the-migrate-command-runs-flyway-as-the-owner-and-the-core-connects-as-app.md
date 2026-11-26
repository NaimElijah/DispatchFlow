# ADR-145: The migrate command runs Flyway as the owner, and the core connects as app with only what its adapters run

- **Status:** Accepted; amended by ADR-146
- **Date:** 2026-11-26

## Context

[ADR-041] gave the database two roles: `dispatchflow_owner` runs Flyway and owns every table, and
the core connects as `app`, which owns nothing and holds only the grants it needs, `INSERT` and
`SELECT` alone on the audit trail. An owner can always grant itself back whatever was revoked, so the
core must never be one. Flyway runs in a one-shot step before the core, and only that step holds the
owner's credential. ADR-041 left open whether that step is "a `migrate` container or command", and
[ADR-127] decision 8 held every grant back until the roles exist: the change that adds them grants on
every table made before it.

Until now Spring Boot ran Flyway inside the core, as the core's own user, which therefore owned all
72 tables that `V2` to `V9` make. [#160][ISSUE-160] builds the roles and the step. Its open points:

- what runs the step, and how the core's jar becomes it;
- how long a migration waits for a lock: [ADR-136] gave the core's connections 2 seconds, Flyway's
  included while it ran inside the core, and asked #160 to choose the step's own;
- what the owner needs on the database, since `V1` creates schemas and PostgreSQL 15 and later let no
  ordinary role create in `public`, where Flyway keeps its history;
- how narrow each table's grant is, and what keeps a later migration from forgetting one;
- how the integration tests and `just core-run`, which run against a throwaway container nothing
  else migrates, get both roles.

Spring Boot 4.1.2's `@ServiceConnection` gives the core and Flyway the container's superuser, and
`spring.flyway.user` makes Flyway a data source of its own from the URL and the credentials alone,
without the pool's session settings: UTC, no server error detail, the lock timeout.

## Decision

Decided on 26 November 2026.

1. **The core's own image runs the step,** as `java -jar app.jar migrate`. The migrations ship inside
   the jar the tests ran, with the Flyway version they ran.
2. **`migrate` is the first argument, and it starts a context of its own.**
   `DispatchFlowApplication.main` hands it to `MigrateCommand`, whose context imports only the data
   source and Flyway auto-configuration, with no web server, and closes once Flyway has run. A
   failure is thrown out of `main`, so the process exits non-zero, and Flyway rolls back the
   migration that failed. The application's own beans never start holding the owner's credential.
   `application.properties` sets `spring.flyway.enabled=false`, and the command alone turns it on,
   in its own context, above every property file. No profile does it, so a run of the whole core
   that names one, a Compose service given the owner's credential and a profile but not the
   command, still migrates nothing. The step's environment sets the usual `SPRING_DATASOURCE_*` keys
   to the owner, so Flyway runs on the pool those settings build, with the core's session.
3. **A migration waits at most 2 seconds for a lock,** the core's own limit, inherited from
   `application.properties`. A migration that waits behind a long transaction holds every later query
   on its table behind it, so a short limit caps how long the running core stalls. The migration then
   fails and rolls back, the step exits non-zero, the new core doesn't start, and running the step
   again is the retry. A migration that needs longer sets `SET LOCAL lock_timeout` as its first
   statement, with a comment that says why. A non-transactional one, such as `CREATE INDEX
   CONCURRENTLY`, can't: it sets `SET lock_timeout` and ends with `RESET lock_timeout`, so the pooled
   connection goes back to 2 seconds. When one fails, Flyway keeps a failed row and the database may
   keep an invalid index, so the retry drops the index and runs Flyway's `repair` first. The step
   sets no statement timeout.
4. **The owner gets `CREATE` on the database and on `public`, and nothing else.** That is what `V1`'s
   schemas and Flyway's history table need. The database and `public` stay the bootstrap superuser's,
   so the owner can't drop or rename the database, change its settings or take `app`'s `CONNECT`
   away. Neither role is a superuser or may create a database or a role. The two grants still let
   the owner put a function in `public`, or in a schema named after the superuser, on the
   superuser's default `search_path`, where an unqualified call by the superuser would run it with
   the superuser's rights. So the init script sets the superuser's `search_path` in this database to
   the catalog alone; a superuser who sets another path in a session takes that risk back.
5. **An init script creates both roles:** `infra/deploy/postgres/initdb/create-roles.sql`, which the
   postgres image runs once, on an empty data folder. It reads `DISPATCHFLOW_OWNER_PASSWORD` and
   `DISPATCHFLOW_APP_PASSWORD` from the environment, stops before creating either role when one is
   missing or empty or both are the same, and keeps its statements out of the server's log. The
   `postgres` container that runs it therefore holds the owner's password too, in development and in
   any deployment that creates its roles this way: the first of two exceptions to ADR-041's rule that
   only the step holds it. Whoever can read that container's environment can already open a
   superuser session in it. The integration tests run the same folder in their containers, through a
   pom test resource, with passwords made afresh for each run.
6. **Each table grants `app` exactly what its adapter runs.** `V11` grants `USAGE` on the eight
   schemas and, on the 72 tables:
   - all four of `SELECT`, `INSERT`, `UPDATE` and `DELETE` on 16 that change and lose rows through
     their root;
   - `SELECT`, `INSERT` and `UPDATE` on 24 never deleted;
   - `SELECT`, `INSERT` and `DELETE` on 6 whose rows are added and removed but never changed;
   - `SELECT` and `INSERT` on 21 appended once;
   - `SELECT` and `INSERT` on the audit trail's two tables, as ADR-041 has it for `audit_record` and
     for the table [ADR-127] split off it;
   - `INSERT` alone on a published plan's three revision tables, which nothing reads yet.

   `VersionedSave`'s forced version increment is an `UPDATE` of the root, and every `SELECT … FOR
   UPDATE` needs `UPDATE` too. A foreign key's check and its `ON DELETE CASCADE` run as the table's
   owner, and an identity column's default needs no grant on its sequence. From `V11` on, each
   migration grants on the tables it creates (ADR-127 decision 8). A statement an adapter starts to
   run later, a delete or an update, needs a grant in a migration of its own, and so does a mapping
   that starts to remove from a collection or replace an element of one; a persistence test that
   saves that change over stored rows fails with SQLSTATE `42501` until it has one. `V11` also
   revokes `EXECUTE` from every role on the functions the owner creates from then on, which
   PostgreSQL would otherwise grant to all: the migration that creates a function grants it to
   whoever may call it.
7. **A test guards the grants.** `DatabaseRolesIT` fails when a table in a schema the owner owns
   grants `app` nothing, or `TRUNCATE`, `REFERENCES`, `TRIGGER` or `MAINTAIN`, and when a function
   there is open to every role. It doesn't pin each table's row privileges, which `V11` and each
   later migration state once. Only the records the documents keep as written are pinned:
   `DatabaseRolesIT` fails when `app` may update or delete the audit trail or a published plan's
   revisions. The audit trail's behaviour tests and its purge function come with #180.
8. **The tests and `just core-run` run Flyway in the core's JVM, as the owner.** Nothing else
   migrates their throwaway container, so the test-only `throwaway-database` profile turns Flyway on
   there, on an owner pool of `TestcontainersConfiguration`'s own. That pool binds the core's pool
   settings, so its session is the core's. The core connects as `app`. This is the second exception
   to ADR-041's step, for those two runs alone. [ADR-121] lets its stand-ins boot only when `test`
   or `dev` is the only active profile, so whichever change adds those profiles moves
   `throwaway-database`'s one setting into them and deletes it. Every adapter call in the tests runs as `app`, so every
   `*PersistenceIT` proves the grants. The tests' own SQL that damages, plants or removes rows runs
   on the owner's `JdbcClient` (`@DatabaseOwner`), so a write the database must refuse is refused by
   the schema, never by a missing grant. A damage that shares the load's transaction, which the
   refused load rolls back, runs on `app`'s connection, the only one that can join it, and only where
   `app`'s grants allow it.
9. **A database migrated before this change** keeps the old owner. Recreate it with `just db-reset`
   once #172 has merged. Otherwise, as a superuser: run the init script's `CREATE ROLE` and `GRANT`
   statements by hand, since the image runs the script only on an empty data folder; then
   `ALTER SCHEMA … OWNER TO dispatchflow_owner` for the eight schemas and `ALTER TABLE … OWNER TO
   dispatchflow_owner` for their tables and `public.flyway_schema_history`. An identity column's
   sequence moves with its table, and PostgreSQL refuses to move it alone. No database outside a
   container exists yet.

## Consequences

- **Easy:** the core can't change the schema, own a table or grant itself anything, and the database,
  not the code, keeps the audit trail append-only and a published plan's revisions unchanged.
- **Easy:** a migration that forgets its grant fails in CI, by the guard's message or the persistence
  test's `42501`, never first in a deployment.
- **Easy:** one artifact serves both the core and its migrations, and the step's connections are the
  core's: UTC, no server error detail, the same lock timeout.
- **Hard:** every migration that adds a table grants on it, and an adapter that starts to update or
  delete where it didn't needs a grant migration.
- **Hard:** a migration that can't get a lock within 2 seconds fails the deploy, which then has to
  run the step again, or ship that migration with a limit of its own.
- **Hard:** the tests use two clients, and a reader has to know which SQL runs as whom: the
  `DatabaseOwner` qualifier's comment and `IntegrationTest`'s say it.
- **Hard:** the core's pom reads one folder outside its module, the init script. The image's build
  sees only the module, and Maven skips a resource folder that doesn't exist there, as the image
  skips the tests.
- **Hard:** Compose gains the `migrate` service, with the owner's password, and the `postgres`
  service gets both passwords. #146 adds both. It also decides [ADR-042]'s run from the IDE: Spring
  Boot's Docker Compose support builds the connection from the `postgres` service and would connect
  the core as its superuser, not as `app`.
- **Hard:** an init script that stops leaves the data folder made but the roles missing, and the
  image skips its init scripts on every later start, so the folder has to go before a retry.

## Alternatives considered

- **Flyway's own image.** A second image to vet and pin ([TS §1][TS-1] rule 6), whose Flyway version
  would have to be kept equal to the tests' by hand, a second way to find the migrations, and the
  session settings repeated in its configuration.
- **The whole core under a `migrate` profile.** Only a properties file, but every bean of the
  application, the jobs and the event registry included, would start holding the owner's credential,
  which [TS §6.3][TS-63] rules out, and the process would need a way to stop after Flyway.
- **A second main class.** The same small context, but Compose and the documents would need a
  longer, less obvious command line to pick that class instead of the jar's default.
- **A `migrate` profile that turns Flyway on.** One properties file, but any run that named the
  profile would migrate: the whole core, started with the profile and the owner's credential but
  without the command, would run every bean as the owner and never exit.
- **`spring.flyway.user` and `spring.flyway.password`.** Spring Boot would build Flyway a data source
  from the URL and credentials alone, without the pool's session settings, which would then be
  repeated in `spring.flyway.init-sqls` and the URL.
- **A longer lock limit, such as 30 seconds, or none.** Fewer failed deploys on a busy database, but
  the running core's queries on the migrated table fail for up to that long, or, with no limit, wait
  with no end behind one long transaction.
- **The owner owns the database.** One statement, but it could drop the database or change its
  defaults for every role, which Flyway never needs.
- **Flyway's history in a schema of its own.** `public` would stay untouched, at the cost of one more
  setting to keep equal in the step and the tests, for little: the owner owns the history either way.
- **The roles in the tests' Java configuration.** No file outside the module, but two definitions of
  the roles that could drift, and the development script never tested. Copying the script by a path
  relative to the working directory instead breaks a run started from another folder.
- **All four grants on every table, the audit trail narrowed.** Simplest, but only the audit trail
  would be protected, against TS §6.3's "only the grants it needs".
- **Narrowing only where a document states the rule.** It would survive a mapping's refactoring
  better, but it grants statements the code never runs.
- **No guard test, or a full snapshot of every grant.** Without one, the persistence tests still catch
  a missing grant on a table they use, but not a table no test uses yet, nor a privilege beyond a
  row's. A snapshot would catch a row privilege too wide on any table, but write every grant twice,
  in its migration and in the snapshot; the guard pins only the records kept as written.
- **`@ServiceConnection` as before.** It gives the core and Flyway the container's superuser, so the
  tests would never run as `app`.

## Revisit when

- A migration can't get its lock within 2 seconds on an ordinary day, or a deploy fails on the limit
  twice in a row.
- The core runs as more than one instance, or deploys without stopping, which needs migrations that
  the running version survives.
- The database moves to a managed PostgreSQL, whose administrator creates the roles instead of the
  init script.
- A table needs a grant per column, or a view, a function or a sequence needs one.
- A review asks to revoke the `TEMPORARY` and `CONNECT` privileges PostgreSQL gives every role.

[ADR-041]: ADR-041-one-postgresql-schema-per-module-read-and-written-through-jooq.md
[ADR-042]: ADR-042-compose-dev-for-local-runs-testcontainers-for-tests.md
[ADR-121]: ADR-121-the-application-skeletons-review-settles-its-seams-conflicts-retention-and-scoring-inputs.md
[ADR-127]: ADR-127-how-the-aggregates-are-mapped-and-where-their-stored-state-is-tested.md
[ADR-136]: ADR-136-the-core-waits-two-seconds-for-a-lock-and-a-lock-it-cannot-get-is-retried.md
[ISSUE-160]: https://github.com/NaimElijah/DispatchFlow/issues/160
[TS-1]: ../architecture/tech_stack.md#1-what-decides-close-calls
[TS-63]: ../architecture/tech_stack.md#63-persistence

# ADR-146: The jar takes only options but for its command, and the roles' guards cover every object and log

- **Status:** Accepted; extended by ADR-151
- **Date:** 2026-11-26

## Context

[ADR-145] built [ADR-041]'s two roles (#160, #293): the migrate command runs Flyway as
`dispatchflow_owner`, the core connects as `app`, an init script creates both roles, and
`DatabaseRolesIT` guards the grants. A review after the merge, each of its claims tried on a real
`postgres:18.6`, found five gaps:

- **A misplaced or misspelt command started the core.** Spring Boot ignores an argument that isn't
  an option, so `migrate` anywhere but first, `MIGRATE` or `--migrate` started the whole core in the
  step's place, holding the owner's credential, and it never exited.
- **Two settings logged a password, and the test couldn't see the third.** With `debug_print_parse`
  or `debug_print_rewritten` on, the server logs `CREATE ROLE`'s parse tree, password and all; both
  are off by default. The test read psql's output alone, so it missed a failed statement's
  `STATEMENT:` line, which the server writes to its own log: removing
  `SET log_min_error_statement = panic` left it green.
- **The guard missed what lay outside the owner's schemas.** It read the objects in schemas the
  owner owns, but `public` belongs to `pg_database_owner`, and the owner may create there. It read
  table privileges alone, so a grant on one column passed, an `UPDATE` of one column of the audit
  trail included. Its ownership check read four catalogs, which miss a large object `app` creates.
- **[ADR-145] decision 8 pointed at the wrong file.** It moves `throwaway-database`'s one setting
  into the `test` and `dev` profiles, but `application-dev.properties` lies on the main classpath
  (#170), where the Compose core reads it too (#146) and would then run Flyway, which [ADR-145]
  decision 2 keeps to the command.
- **[ADR-145] decision 9's manual path skipped the script's protections.** Typed by hand, the
  `CREATE ROLE` and `GRANT` statements run without the script's log settings and its `search_path`.

The same review corrected two sentences of [ADR-145] that decide nothing: its context counts the 72
tables in `V2` to `V9`, not `V10`, and decision 6 says why `VersionedSave` needs `UPDATE`, and that a
persistence test catches a missing grant only where it saves the change over stored rows.

## Decision

Decided on 26 November 2026.

1. **The jar takes only `--` options, but for a first `migrate`.** `DispatchFlowApplication.main`
   refuses a bare word anywhere else, and `--migrate` in any case, before anything starts, with a
   message that names no argument, since one may be a password. Nothing in the core reads a bare
   word. What follows a first `migrate` goes to the command's own context, which holds only the data
   source and Flyway and reads none either. This amends [ADR-145] decision 2.
2. **The init script turns off, for its own session, every setting that writes a statement's text
   or its parse tree to the server's log.** It names `log_statement`, `log_min_duration_statement`,
   `log_min_duration_sample`, `log_transaction_sample_rate` and `log_min_error_statement`, and finds
   every `debug_print_` setting the server has by name, so one a later version adds is covered too. A
   crash still logs the statement it died on. A module the server preloads to record statements,
   such as `pg_stat_statements`, `pgaudit` or `auto_explain`, keeps a record of its own, which the
   script doesn't reach. `DatabaseRolesIT` runs the script with every one of those settings on, and
   reads the server's own log and psql's output for the passwords and for any trace of the two
   statements that carry them. This makes [ADR-145] decision 5's "keeps its statements out of the
   server's log" exact.
3. **The guard covers every object the owner owns, wherever it is.** `DatabaseRolesIT` fails when:
   - a table, view, materialized view or foreign table the owner owns, in whichever schema, grants
     `app` nothing, a grant on one column counting as one on the table. A partition answers through
     its parent, so it needs no grant of its own;
   - any of them, partitions included, grants `app` `TRUNCATE`, `REFERENCES`, `TRIGGER` or
     `MAINTAIN`, or a privilege it may pass on, on the table or on a column;
   - Flyway's history grants `app` anything at all, since the core never reads it;
   - a sequence the owner owns lets `app` use it, or a function the owner owns is open to every
     role;
   - `app` may not use a schema the owner owns, or may create in any schema;
   - `app` owns any object at all, as `pg_shdepend` records owners, or is a member of the owner;
   - `app` may update, even one column, or delete a record kept as written, or one of its
     partitions.

   A temporary table belongs to its session, and the session's own temporary schema answers yes to
   every role, so the guard reads neither. This amends [ADR-145] decision 7.
4. **`throwaway-database`'s setting moves to the test classpath alone.** Whichever change adds the
   `test` and `dev` profiles turns Flyway on in `application-test.properties`, on the test classpath,
   and in `TestDispatchFlowApplication` for `just core-run`, and then deletes the profile. Never in
   `application-dev.properties`. This amends [ADR-145] decision 8.
5. **An older database gets its roles from the script itself.** A superuser connected to the core's
   database runs `create-roles.sql` with `psql -f`, both passwords read into psql's environment from
   a file and never typed on a command line, and then moves the owners as [ADR-145] decision 9 says.
   This amends that decision's "run the init script's `CREATE ROLE` and `GRANT` statements by hand".

## Consequences

- **Easy:** a misplaced or misspelt command fails at once with a message that says what to do,
  instead of a core that runs as the owner and never exits.
- **Easy:** each of these fails `DatabaseRolesIT`:
  - removing one of the script's log settings;
  - a grant on a partition, in `public` or on Flyway's history;
  - a column grant that carries `REFERENCES` or a grant option, or that updates a record kept as
    written;
  - a sequence, or a membership in the owner, given to `app`.
- **Hard:** an argument the core might want later that isn't an option has to change decision 1.
- **Hard:** a PostgreSQL major version may add a `log_` setting that writes a statement. The upgrade
  checks `pg_settings` for one and names it in the script and in the test.
- **Hard:** what the superuser's pinned `search_path` doesn't stop stays: code the owner attached to
  its tables, views or types, a trigger, a default, a constraint, an index expression, a rule or a
  function a view calls, runs with the superuser's rights when the superuser writes or reads them.
  And `CREATE` on the database lets the owner create any trusted extension the image ships, whose
  script runs as the superuser.

## Alternatives considered

- **Refusing `migrate` alone anywhere but first.** It stops the misplaced command, but not `MIGRATE`,
  `migrate ` with a space or `--migrate`, which start the whole core just the same.
- **A startup check that the core's database user is no superuser and owns nothing.** It would also
  catch a credential mapped to the wrong service, which nothing here does. It is left to #146, which
  maps the credentials and must prove that the run from the IDE connects as `app`.
- **Turning off each `debug_print_` setting by name.** Easier to read, but `SET` of a setting the
  server doesn't have fails, so the list could name only the current version's.
- **A full snapshot of every grant.** [ADR-145] turned it down, and these gaps don't change why.
- **Revoking the large-object functions from `PUBLIC`.** `app` could still fill the disk through the
  tables it may insert into, so it buys little.
- **Rewriting [ADR-145] in place.** Quicker to read, but an accepted ADR is history, so the changes
  live here.

## Revisit when

- The core needs a command-line argument that isn't an option.
- The server preloads a module that records statements, or a PostgreSQL major version adds a
  setting that logs one.
- A table is partitioned, or a sequence needs a grant.
- A foreign server arrives, whose user mapping for `app` `pg_shdepend` lists as an owner's row.

[ADR-041]: ADR-041-one-postgresql-schema-per-module-read-and-written-through-jooq.md
[ADR-145]: ADR-145-the-migrate-command-runs-flyway-as-the-owner-and-the-core-connects-as-app.md

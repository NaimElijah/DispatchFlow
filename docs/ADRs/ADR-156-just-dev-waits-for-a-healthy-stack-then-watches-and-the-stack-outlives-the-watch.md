# ADR-156: `just dev` waits for a healthy stack, then watches, and the stack outlives the watch

- **Status:** Accepted
- **Date:** 2026-11-28

## Context

[ADR-151] decision 4 carries edits into the dev stack's containers through Compose Watch, and its
Consequences say that "live reload needs `up --watch` … #172's `just dev` passes it". [ADR-152]
decision 9 puts a service whose data git ignores under a Compose profile, "which #172's `just dev`
will name". #172 added `just dev`, `just dev-down` and `just db-reset`, and merged on 28 November
2026. An audit after the merge measured the recipes on Windows with Docker Compose v5.7.1 and just
1.59.0:

- **`up --watch` runs attached.** It never reports that the stack is healthy, and a service that
  never turns healthy, or a migrate step that fails, doesn't fail the command. `up --wait` does
  both, and then detaches, so the watch runs on its own as `watch --no-up`.
- **The stack outlives the watch.** Interrupting `watch --no-up` ends it with a failing status, so
  just reports `recipe 'dev' failed`, and every service stays up.
- **A watch left running starts services again.** After `just dev-down` from a second terminal, a
  touch of the optimizer's `pyproject.toml`, a rebuild trigger, rebuilt the optimizer's image and
  started it again.
- **One project for every checkout.** The file names its project, `dispatchflow`, so each checkout
  and worktree of the repository runs the same stack and the same `dispatchflow_postgres-data`
  volume. Compose Watch takes a lock per project: a second `just dev` beside a running one
  rebuilds, runs the migrate step and restarts the core, then stops with `cannot take exclusive
  lock`.
- **`down --volumes` deletes more than the database.** It removes every named volume the file
  creates, and every anonymous one; it leaves an external volume.
- **The live check never starts a profiled service.** `just infra-check` starts the stack without
  `--profile`, since CI has none of the data such a service needs. Its root check only sees the
  services that run.

## Decision

Decided on 28 November 2026.

1. **`just dev` runs `up --build --wait`, prints where each service listens, then runs
   `watch --no-up` in the foreground.** It is the one command that starts the stack ([NFR-16]).
   The addresses come from `docker compose port`, so a remapped PostgreSQL port prints as it is.
   This amends [ADR-151] decision 4's `up --watch` and the consequence that `just dev` passes it;
   the watch itself is unchanged.
2. **The stack outlives the watch.** Ctrl+C stops only the watch, and just reports the recipe as
   failed. `just dev-down` stops the stack once the watch is stopped. The recipe's closing lines
   and the README say both.
3. **`just dev-down` runs `down`, and `just db-reset` runs `down --volumes` behind just's
   `[confirm]` prompt.** The prompt says that every checkout shares the database, and
   `just --yes db-reset` skips it. All three recipes stop with the fix while `infra/deploy/.env`
   is missing, through just's own `path_exists()` and `error()`, so the check reads the same under
   PowerShell and `sh`; `db-reset` asks first.
4. **Every checkout runs the one project.** The recipes pass no `-p`, and the README says that
   `just db-reset` in any checkout deletes the shared database.
5. **The data-bound services' profile is `data`.** This settles the name [ADR-152] decision 9
   left. Since no live check starts such a service, `just infra-check` holds it to the file
   statically:
   - every service under a profile is under `data`, among any others it names;
   - it sets a numeric non-root `user:` and drops every capability ([ADR-019]); a user given by
     name could still be root inside the image;
   - `postgres-data` is the only volume: no other named one the file creates, and no anonymous
     one the file mounts. A data service keeps its files in a bind mount of a folder git ignores,
     mounted over any `VOLUME` its image declares;
   - it runs outside the root group, in `user:` and in `group_add`.
6. **The static checks close what a profiled service could reach past the live checks,** for
   every service, since a profiled one gets only these. No service shares a namespace with
   another container (`service:` or `container:`), as none may share the host's ([ADR-152]); none
   takes a security option but `no-new-privileges:true`, which also refuses a contradicting
   `no-new-privileges:false` and a custom seccomp profile; and only `postgres` reaches the
   database's files: no other service mounts its volume, no other key names that volume, and no
   service takes another's volumes through `volumes_from` ([ADR-145]).

## Consequences

- **Easy:**
  - A start that fails fails `just dev`, and the line that failed is Compose's own.
  - A teammate sees where each service listens without reading the file.
  - Ctrl+C is harmless: nothing restarts, and the next `just dev` finds the stack up.
- **Hard:**
  - Stopping the stack takes two steps, in order: Ctrl+C, then `just dev-down`.
  - One laptop runs one stack. `just db-reset` in a scratch worktree deletes the database of the
    main checkout's `just dev`.
  - A data service runs as a numeric non-root user, whatever its image's default, and drops every
    capability. #168's `osrm` is the first.
- **Cost:** none.
- **Risk:**
  - A watch left running starts a service again when its lockfile or manifest changes, for
    example on a `git pull` after `just dev-down`. The recipe's closing line says to stop it
    first.
  - Just's `recipe 'dev' failed` after Ctrl+C can read as a fault. A line `just dev` prints before
    the watch starts says it is expected.
  - The recipes are checked on Windows only, where [NFR-208] asks for macOS too. #120 runs them on
    a Mac.

## Alternatives considered

- **`up --build --watch`, as [ADR-151] wrote.** It runs attached and never says when the stack is
  ready, a service that never turns healthy doesn't fail it, and Ctrl+C stops the stack with the
  watch.
- **A `-` before the watch line, so Ctrl+C ends without just's failure line.** It would also turn
  the watch's real failures, such as a second `just dev`'s lock error, into success.
- **A project per checkout, named from its folder.** Every stack publishes the same `127.0.0.1`
  ports, so two can't run at once anyway. The README's raw `docker compose` commands would then
  name another project than the recipes do.
- **Having `just dev-down` stop the watch too.** The watch runs in another terminal, and no Compose
  command stops it from outside.
- **Reading the justfile's profile in the checker, so the name lives once.** That is a parser of the
  justfile for one word; the comment beside each copy names the other.

## Revisit when

- Compose can run a watch detached with the stack, or stops it with `down`.
- Someone needs two stacks on one laptop, such as a worktree's beside the main checkout's.
- A data service's image can't run as a numeric non-root user.

[ADR-019]: ADR-019-container-images-run-as-non-root.md
[ADR-145]: ADR-145-the-migrate-command-runs-flyway-as-the-owner-and-the-core-connects-as-app.md
[ADR-151]: ADR-151-the-dev-stack-runs-the-core-as-a-compose-service-and-the-core-checks-its-database-session.md
[ADR-152]: ADR-152-the-session-guard-reads-the-real-login-and-accepts-only-untranslated-messages-and-the-stack-check-checks-what-it-claims.md
[NFR-16]: ../requirements/requirements.md#nfr-16
[NFR-208]: ../requirements/requirements.md#nfr-208

# ADR-151: The dev stack runs the core as a Compose service, and the core checks its database session at startup

- **Status:** Accepted; amended by ADR-152
- **Date:** 2026-11-27

## Context

[ADR-042] starts local services from `compose.dev.yaml`. It runs the core from the IDE through
Spring Boot's Docker Compose support. It left open whether `TestDispatchFlowApplication` and
`just core-run` stay once `just dev` exists. Since then:

- [ADR-145] made the core connect as `app`, and a one-shot `migrate` step run Flyway as
  `dispatchflow_owner`. It said that #146 decides the run from the IDE. Spring Boot's Compose
  support builds the core's connection from the `postgres` service, so it would connect the core
  as the PostgreSQL superuser ([ADR-145], Hard).
- [ADR-146] left a startup check to #146. The check makes the core refuse a database user that is
  a superuser or owns anything, which also catches a credential mapped to the wrong service.
- [ADR-140] decision 4, [ADR-147] and [ADR-148] require three things of the Compose core:
  - it runs the stand-in session;
  - every published port binds `127.0.0.1`;
  - the web app reaches the core through a proxy on its own origin, which keeps the browser's
    `Host` and `Sec-Fetch-Site`.
- #250 found three settings outside the core that it relies on:
  - the server log, which keeps a refused row's detail and its bind values unless told not to
    ([NFR-19]);
  - the server's message language. Hibernate 7.4.13 reads a violated constraint's name from the
    English message text, and four race answers depend on it.
  - the JVM's zone, in which Hibernate binds an Israel-local date and time.
- [ADR-135] makes every CI job a required check, and has the pull request that adds a job change
  the ruleset.

Most laptops run Windows ([NFR-208]). There, a folder mounted into a container sends no file-change
events, so a dev server in the container never sees an edit unless it polls.

## Decision

Decided on 27 November 2026. This amends [ADR-042]'s first bullet, which ran the core from the IDE
through Spring Boot's Compose support, and answers its deferred question. It amends [ADR-135]
decision 1 and [ADR-015], whose seven jobs become eight, and extends [ADR-145] and [ADR-146] with
the decisions they left to #146, and [ADR-134], whose mirror the core's image build now reads.

1. **`infra/deploy/compose.dev.yaml` runs five services:**
   - `postgres`;
   - `migrate`: the core's image run as `migrate`, once;
   - `core`: the core's image under the `dev` profile;
   - `optimizer`;
   - `web`: Vite's dev server.

   `osrm`, `tiles`, `wiremock` and Mailpit join with the tickets that need them, as [ADR-042]'s
   table has them. `core` starts only after `migrate` succeeds, and `migrate` only after `postgres`
   is healthy and both roles exist. `postgres` runs `postgres:18.6`, the tag
   `TestcontainersConfiguration` runs, so the tests and the stack run the same server. The four
   services built from our own images drop every Linux capability and can't gain one.
2. **The core runs as a Compose service, never through Spring Boot's Compose support.**
   - The support is not adopted.
   - `TestDispatchFlowApplication` and `just core-run` stay. They keep [ADR-042]'s Testcontainers
     route for the core alone, which its last Revisit trigger foresaw.
   - To debug the core in the IDE, stop the stack's `core` and `web` and run
     `TestDispatchFlowApplication` and `just web-run` on the laptop. The two share the stack's ports
     8080 and 5173. The web app's proxy reaches the core at `127.0.0.1:8080` by default, and at the
     Compose `core` service inside the stack.
3. **Each service gets only its own credentials, as environment variables read from
   `infra/deploy/.env`:**
   - `postgres` gets the superuser's password and both roles' passwords;
   - `migrate` gets the owner's;
   - `core` gets only `app`'s.

   No service reads an env file. `infra/deploy/.env.example` lists every key the file reads, with no
   values.
4. **Edits reach the containers through Compose Watch.**
   - `up --watch` copies an edit under `src/frontend/web/`, `src/frontend/packages/` or the
     optimizer's `src/` into its container. Vite and uvicorn then reload it from the container's own
     file system, so nothing polls.
   - A changed lockfile rebuilds that image. A change to the core needs `up --build`.
   - `src/frontend/web/Dockerfile.dev` builds the dev server's image from the pnpm workspace. It
     fetches every dependency at build time, so a started stack needs no outside network
     ([NFR-175]).
   - The optimizer's image gains a `dev` stage in which `dispatchflow` owns `src/`, because Watch
     deletes and renames files as the container's user. The production stage stays the default, and
     root owns its code.
   - The dev server answers no CORS request: the app calls only its own origin.
5. **`just infra-check` checks the file and the running stack, and CI's `infra` job runs it as the
   eighth required check.** `infra/deploy/check_dev_stack.py` sits beside the file it checks. Its
   live part starts this file's stack, not the components' own tests, so it stays out of `tests/`.
   ruff and mypy check it at the versions the optimizer pins.
   - It reads the file with every profile, `.env.example`'s keys and throwaway passwords, and fails
     when:
     - a published port binds anything but a written `127.0.0.1`;
     - a service shares a host namespace, runs privileged, adds a capability, runs as root or
       mounts Docker's socket;
     - a service holds another's password, or reads an env file;
     - `.env.example` misses a key the file reads;
     - the start order is missing.
   - It then starts the whole stack in a project with a random name and no port on the host, so it
     never meets a running `just dev` or another check. It checks:
     - that the migrate step exited 0;
     - #250's server settings;
     - that every Java connection is `app`'s;
     - the JVM's zone;
     - the proxy: a same-origin call is signed in, a same-site one is refused, and another origin
       gets no CORS answer.
   - It removes the project, its volume and its images afterwards, and fails when it can't.
   - The core's image fetches Maven and every dependency through [ADR-134]'s mirror of Maven
     Central, in its build stage alone. Central answered the job's first run with HTTP 429, for
     the Maven distribution itself.
6. **The core refuses to start on a database session that would undo the roles or the English
   messages.** At startup, `DatabaseSessionGuard` refuses to start when:
   - the session switched from the role it logged in as;
   - its role has `SUPERUSER`, `CREATEDB`, `CREATEROLE`, `REPLICATION` or `BYPASSRLS`;
   - its role is a member of any other role. PostgreSQL records no owner for the superuser or a
     predefined role, so only the membership shows those;
   - its role owns an object in any database;
   - the server's `lc_messages` is neither `C`, `POSIX` nor an English locale.

   Every context the core's component scan builds has the guard, a server step's ([ADR-131])
   included. Only the `schema-draft` profile's has none, because it opens no connection. The migrate
   command's context is never scanned.
7. **#250's settings, where they have a home today:**
   - The dev `postgres` starts with `log_error_verbosity=terse`, `log_parameter_max_length=0`,
     `log_parameter_max_length_on_error=0` and `lc_messages=C`.
   - The core's image starts the JVM with `-Duser.timezone=Asia/Jerusalem`, the tests' zone, so
     `migrate`, the Compose core and a deployed core all run in it ([ADR-088]). `just core-run`
     passes the same flag through the Spring Boot Maven plugin.
   - The deployed server's settings come with its own Compose file.
8. **A setting the core can't start without goes to three places in the pull request that adds
   it:**
   - the Compose `core` service;
   - `infra/deploy/.env.example`;
   - `src/backend/core/.env.example`.

   A setting that is no secret gets its value in `compose.dev.yaml` as `${KEY:-value}`, so a `.env`
   with only its passwords filled starts the stack. A secret gets no default.

## Consequences

- **Easy:** one command starts the server side and the web app, each healthy ([NFR-16]). A merge
  into `dev` or `main` needs CI to prove it on Linux ([NFR-208]).
- **Easy:** a credential mapped to the wrong service stops the core at startup, naming the role,
  before it serves a request. So does a deployed core that connects as, or through, the owner or a
  superuser.
- **Easy:** reload works the same on Windows and macOS, with no polling and no host
  `node_modules` in a container.
- **Easy:** a new service that forgets `127.0.0.1`, or holds a password not its own, fails
  `just check`.
- **Hard:**
  - The core's debugger attaches to `just core-run`, not to the Compose `core` service.
  - A core edit reaches the stack only through `up --build`.
- **Hard:** `just check` starts the whole stack. The first run builds three images.
- **Hard:** live reload needs `up --watch`. A plain `up` serves the code the images were built
  with. #172's `just dev` passes it.
- **Hard:** Dependabot updates only the core's and the optimizer's Dockerfiles. The tags
  `node:24-slim`, in `Dockerfile.dev`, and `postgres:18.6`, here and in
  `TestcontainersConfiguration`, are bumped by hand until Renovate takes over in January
  ([ADR-046]).
- **Cost:** no money. CI's `infra` job runs on a standard runner, free for this public repository.
- **Risk:** in CI the core's image downloads its whole dependency tree on every run, with no
  cache, now from Google's mirror. When the mirror fails, the job fails on a clean tree.
- **Risk:** a laptop whose Compose version predates Compose Watch can't reload. This was checked on
  Docker 29.9.3 with Compose v5.7.1, where a sync into the non-root containers works.
- **Risk:** on Linux, Docker Engine 28.2.0 to 28.3.2 exposes ports published on `127.0.0.1` after a
  firewalld reload (CVE-2025-54388). The README asks for 28.3.3 or later.
- **Risk:** the stack runs only on Linux in CI. Windows was checked by hand for this decision;
  macOS needs a teammate's laptop before the Alpha ([NFR-208]).

## Alternatives considered

- **ADR-042's run from the IDE, through Spring Boot's Docker Compose support.** Rejected. It
  connects the core as the PostgreSQL superuser, which the guard refuses. To keep it, `postgres`
  would need Spring Boot's ignore label, and each IDE would have to set `app`'s password itself,
  for a run `just core-run` already gives. It also adds a dependency.
- **`core` and `web` under a Compose profile, so that Spring Boot's plain `up` would skip them.**
  Rejected along with the IDE path it served.
- **Mounting the source folders and polling.** Rejected:
  - On Windows, Vite would poll every file.
  - Each shared web package would need its own mount over its `node_modules`.
  - An image rebuilt with new dependencies would keep its stale `node_modules` volume.
- **Compose `secrets:` for the passwords.** Rejected for the dev stack: the same values already sit
  in `infra/deploy/.env` on the laptop, so they would hide nothing there. The deployed Compose file
  decides it again.
- **`JAVA_TOOL_OPTIONS` on the Compose core for the zone, as #146 first had it.** Rejected: only
  the dev core would get it, and the JVM prints a "Picked up" line on every start.
- **The messages check in `DispatchFlowApplicationIT` alone.** Rejected: the test container always
  runs in English, so it could never fail. Only the running core sees the server it is given.
- **Refusing only a membership in a role that owns something.** Rejected: `GRANT postgres TO app`
  or `GRANT pg_write_all_data TO app` would pass, since PostgreSQL records no owner for either.
- **The guard in web applications alone.** Rejected: a server step runs the core's context with no
  web server, and would then run on any credential.
- **Checking the stack by hand alone.** Rejected: no CI job built the image or ran `migrate` as a
  process ([ADR-146]), and [NFR-208] wants the images' health checks run on Linux in CI.

## Revisit when

- A developer needs a debugger on the Compose core. Publish a JDWP port on `127.0.0.1` alone.
- The deployed `compose.yaml` is written. Decide `secrets:` there, set #250's server settings
  there, and name its server log in the data inventory.
- The `infra` job fails on the mirror. Give the image build a layer cache in CI.
- `just infra-check` becomes the longest step of `just check`. Run its live part in CI alone and
  say so in the root `CLAUDE.md`.
- A team laptop's Compose can't run Compose Watch. Fall back to mounts with polling on that machine.
- Code in the core creates a temporary table. PostgreSQL records its owner, so a second core
  starting meanwhile would be refused: exclude temporary objects from the guard.
- A Hibernate upgrade stops reading constraint names from the message text. Drop the messages
  check.

[ADR-015]: ADR-015-ci-one-workflow-one-job-per-component.md
[ADR-042]: ADR-042-compose-dev-for-local-runs-testcontainers-for-tests.md
[ADR-046]: ADR-046-renovate-replaces-dependabot-and-targets-dev.md
[ADR-088]: ADR-088-israel-local-time-for-every-date-and-schedule.md
[ADR-131]: ADR-131-the-declared-structures-review-tightens-its-rules-and-fills-its-gaps.md
[ADR-134]: ADR-134-the-audit-resolves-the-cores-pom-from-a-mirror-of-maven-central.md
[ADR-135]: ADR-135-the-seven-ci-jobs-are-required-checks-and-a-pull-request-needs-one-approval.md
[ADR-140]: ADR-140-the-app-wide-web-package-sits-above-the-modules.md
[ADR-145]: ADR-145-the-migrate-command-runs-flyway-as-the-owner-and-the-core-connects-as-app.md
[ADR-146]: ADR-146-the-jar-takes-only-options-but-for-its-command-and-the-roles-guards-cover-every-object-and-log.md
[ADR-147]: ADR-147-the-stand-in-condition-sits-in-the-root-package-and-a-refused-access-token-answers-one-code.md
[ADR-148]: ADR-148-the-stand-in-serves-only-its-own-origin-every-unsigned-api-call-answers-401-and-the-guard-outlives-the-session.md
[NFR-16]: ../requirements/requirements.md#nfr-16
[NFR-19]: ../requirements/requirements.md#nfr-19
[NFR-175]: ../requirements/requirements.md#nfr-175
[NFR-208]: ../requirements/requirements.md#nfr-208

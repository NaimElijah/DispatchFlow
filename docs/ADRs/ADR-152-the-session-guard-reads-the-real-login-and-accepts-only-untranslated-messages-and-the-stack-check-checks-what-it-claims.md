# ADR-152: The session guard reads the real login and accepts only untranslated messages, and the stack's check checks what it claims

- **Status:** Accepted
- **Date:** 2026-11-27

## Context

A review of #146 after it merged (#302) checked [ADR-151] against the code, PostgreSQL 18.6 and the
pinned tools. Nothing needed a revert, but the review found these gaps:

- **`DatabaseSessionGuard` has two holes:**
  - It read the login as `session_user`. A superuser's login that runs
    `SET SESSION AUTHORIZATION app` changes `session_user` too, so it passed, and could take the
    superuser back with `RESET SESSION AUTHORIZATION`. `pg_stat_activity.usename` still named
    `postgres`.
  - It accepted any English `lc_messages`. gettext lets a `LANGUAGE` variable in the server's
    environment override every locale but `C`, and the stock image ships the translations.
    `LANGUAGE=de` with `lc_messages=en_US.UTF-8` answered `FEHLER: Division durch Null`.
    Hibernate 7.4.13 finds a violated constraint's name by matching `constraint "…"` in the message
    text (its bytecode shows the template), so four race answers would turn into 500s. ADR-151 and
    the tests had said no stock image could start a server in another language. That was wrong.
- **`check_dev_stack.py` checks less than [ADR-151] decision 5 says:**
  - Its env-file rule could never fail. It read the interpolated configuration, where Compose has
    already folded each `env_file` into the service's `environment`.
  - Its root rule read only an explicit `user:`, so an image that runs as root passed.
  - Nothing required the hardening block on our own images.
  - A new secret, such as #165's GovMap token, got no holder check.
  - Its Host probe would miss a proxy that rewrote `Host` to a fixed `localhost`.
  - None of its rules had a test, which is how the dead env-file rule went unnoticed.
- **The local run's zone broke two things:**
  - The `spring-boot-maven-plugin` `jvmArguments` that ADR-151 decision 7 set for the zone
    replaced any `-Dspring-boot.run.jvmArguments` a developer passed to `just core-run`, a
    debugger agent among them, without a word.
  - A run started from the IDE got no zone at all.
- **The supply chain had three gaps:**
  - The deployable image now downloads the Maven distribution from Google's mirror, and the wrapper
    pinned no checksum.
  - `mirrorOf central` left the repositories that dependencies' poms name to be contacted directly.
    In CI, GitHub Packages and Red Gate's feed both answered 401.
  - `uvx --from mypy==2.4.1` resolved mypy's own dependencies fresh from PyPI on every run, while
    the optimizer's lockfile pins them with hashes.
- **ADR-151 states two things that aren't true:**
  - Decision 4 says nothing polls, but uvicorn has no `watchfiles` in the lock, so its reloader
    polls `/app/src`, cheaply.
  - Its Hard bullet says Dependabot can't update `Dockerfile.dev` or `compose.dev.yaml`. dependabot-core
    reads both file names. The real reasons the two tags wait are in decision 5.

## Decision

Decided on 27 November 2026. This amends [ADR-151]:
- decisions 5, 6 and 7;
- its rule for later settings, decision 8;
- its Hard bullet on Dependabot;
- decision 4's "nothing polls".

It also narrows #250's "`C` or `en_US.UTF-8`" to `C`.

1. **The guard reads the login from the server's own record of the connection.** It reads
   `pg_stat_activity.usename` for its own backend, which neither `SET ROLE` nor
   `SET SESSION AUTHORIZATION` changes, and refuses a session whose login the server doesn't show.
2. **The guard accepts only `lc_messages` `C`, `C` with an encoding, or `POSIX`.**
   - These alone keep the messages untranslated, whatever the server's environment holds.
   - The tests' PostgreSQL starts with `lc_messages=C` and #250's other three settings, as
     `compose.dev.yaml` does, so the tests and the stack run the same server settings. The deployed
     Compose file sets the same.
   - `DatabaseSessionGuardIT` starts a server with `LANGUAGE=de` and proves the refusal.
3. **The guard checks who the core connects as, not what that role was granted.**
   - Only the migrations grant `app` anything, and `DatabaseRolesIT` proves each grant: no CREATE
     anywhere, no TRUNCATE, TRIGGER or REFERENCES, and an append-only audit trail.
   - A grant made by hand on the server needs a superuser, who could change the data directly
     anyway.
   - A check of the audit tables' grants would also make the root-package guard name one module's
     tables.
4. **`just infra-check` checks what its docstring and this ADR say. It fails when:**
   - a service reads an env file, judged from the file as written;
   - a service shares any host namespace (`cgroup` and `uts` included), or runs seccomp or
     AppArmor unconfined;
   - a service may gain a privilege (`no-new-privileges` on every service, `postgres` included), or
     one built from our own images keeps a capability;
   - a key is read with no default, which makes it a secret, and `SECRET_HOLDERS` doesn't name the
     services that may hold it;
   - the stack's `postgres` tag differs from `TestcontainersConfiguration`'s, or `Dockerfile.dev`'s
     Node major from `.nvmrc`'s;
   - any running service's main process runs as root;
   - a call through the proxy whose `Host` is `dispatchflow.localhost`, which Vite lets through and
     the stand-in refuses, isn't refused.

   It also changes how it runs:
   - It reads the file with a copy of the developer's environment that drops `COMPOSE_*` and every
     key `.env.example` lists, so a laptop checks what CI checks.
   - It removes a check project that an interrupted run left stopped.
   - It prints each line as it happens.
   - `test_check_dev_stack.py` feeds each rule a configuration that breaks it, with no Docker, and
     runs before the live part.
   - ruff, mypy and pytest come from the optimizer's lockfile's dev group, through
     `uv run --only-dev`.
5. **The two tags get a check, not new Dependabot entries.**
   - `node:24-slim` already moves with every Node 24 release, and [ADR-038] keeps majors by hand.
   - PostgreSQL's next scheduled minor release is due in February, after Renovate takes over in
     January ([ADR-046]).
   - A Dependabot bump of the Compose file would leave `TestcontainersConfiguration` behind.
   - When Renovate is set up, a regex manager covers that Java constant, and the check stays.
6. **The Maven wrapper pins the distribution's SHA-256.**
   - The value is the zip's, which matched Apache's published SHA-512.
   - The core's build stage installs `unzip`, so it fetches that same zip and not the `.tar.gz`.
   - The image's mirror stands in for every repository (`mirrorOf *`), so the build contacts no
     third-party feed.
   - The image skips the formatting check, which CI already runs, so one unformatted file never
     stops the stack from starting.
7. **`TestDispatchFlowApplication` makes `Asia/Jerusalem` the JVM's default itself.** That covers
   `just core-run` and a run from the IDE alike. The pom sets no JVM option, so a developer's own,
   such as a debugger's, reach the JVM.
8. **Smaller changes:**
   - The role script makes both roles and their grants in one transaction, so an interrupted first
     start leaves all of them or none, and the health check's count of both roles proves the script
     finished.
   - The core's health check reads the status line, 200 or 503, not a body that may list a part
     that is up.
   - Compose Watch rebuilds the web image when `src/frontend/package.json` pins another pnpm.
   - No sync sets `initial_sync`: in this Compose it can copy what the ignore rules leave out, such as
     the host's `node_modules`.
9. **The rule for later settings gains three clauses:**
   - a secret's key joins `SECRET_HOLDERS`;
   - a key only the `dev` profile reads, as `DISPATCHFLOW_STANDIN_ACCOUNT` is, goes in
     `infra/deploy/.env.example` alone;
   - a service whose data git ignores (`osrm`, `tiles`) joins under a Compose profile, which
     `just dev` names and `just infra-check` doesn't start.

   #161's move of the core's build context also moves its `.dockerignore`, as
   `Dockerfile.dockerignore` beside the Dockerfile.

## Consequences

- **Easy:** a credential mapped to the superuser, the owner or a member of either stops the core,
  however the session got to `app`.
- **Easy:** a server that would translate its messages stops the core too, and a test proves it on a
  real translating server.
- **Easy:** each of the checker's rules fails a test when it breaks, and a new secret, a root image
  or an unhardened service fails `just check`.
- **Easy:** a bump of either tag fails until its twin follows, whoever makes it.
- **Hard:** a deployment that would run its server under an English locale must set `C` instead.
- **Hard:** a grant made by hand on the server goes unseen by the core. Only the migrations' grants
  are tested.
- **Hard:** `just infra-check` now syncs the optimizer's dev dependencies, about 30 packages, on a
  machine that has none.
- **Cost:** no money.
- **Risk:** the core's build stage now reaches Ubuntu's package archive for `unzip`, one more host
  that can fail a clean CI build.
- **Risk:** a Maven bump in the wrapper now needs its SHA-256 too, or the wrapper refuses to
  download.

## Alternatives considered

- **`system_user` for the login.** Rejected: it is null under `trust` authentication, which the
  image uses inside its container. `pg_stat_activity` shows the login either way.
- **Keeping English locales, with a rule that the deployed server's environment sets no
  `LANGUAGE`.** Rejected: the guard can't see that environment, so a German server would pass.
- **Reading the violated constraint's name from the protocol's structured field.** pgjdbc keeps the
  server's error fields even with detail logging off. Rejected for now: it needs a custom Hibernate
  dialect, pgjdbc at compile scope and its own decision, while setting `C` is enough on a server we
  configure.
- **Two targeted grant refusals:** CREATE anywhere, or a write to the audit tables. Rejected, for
  decision 3's reasons.
- **A full scan of `app`'s grants.** Rejected: every migration that grants something would have to
  update the guard.
- **Dependabot entries for `src/frontend/web` and `infra/deploy` now.** Rejected, for decision 5's
  reasons.
- **`systemPropertyVariables` in the pom.** Rejected: it fixes `just core-run`, but a run from the
  IDE still gets no zone.
- **`uvx --with` for mypy's dependencies.** Rejected: it pins versions without hashes, a second
  copy of what `uv.lock` already holds.
- **`initial_sync` on every sync.** Rejected, for decision 8's reason.
- **A read-only root filesystem for the core.** Not adopted: it is unproven with Tomcat's temporary
  folders, and worth little on a laptop.

## Revisit when

- The core must run on a server whose `lc_messages` we can't set to `C`. Read the constraint's name
  from the structured field instead.
- Grants can be made outside the migrations, for example from a managed database's console, or a
  pilot's security review asks for runtime proof. Add grant checks to the guard.
- The core's credential is rotated through a group role. Allow membership in that one `NOLOGIN`
  role, which owns nothing.
- Renovate is set up. Give it a regex manager for `TestcontainersConfiguration`'s tag; the check
  stays.
- Compose stops copying ignored files on `initial_sync`. Consider it again.

[ADR-038]: ADR-038-mise-pins-every-toolchain-and-core-moves-to-java-25.md
[ADR-046]: ADR-046-renovate-replaces-dependabot-and-targets-dev.md
[ADR-151]: ADR-151-the-dev-stack-runs-the-core-as-a-compose-service-and-the-core-checks-its-database-session.md

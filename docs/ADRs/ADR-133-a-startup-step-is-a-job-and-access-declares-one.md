# ADR-133: A step that runs once at startup is a `job` adapter, and Access declares one

- **Status:** Accepted; extended by ADR-147
- **Date:** 2026-11-24

## Context

[ADR-130] decision 2 names six kinds of inbound adapter. Its `job` is "a worker takes queued work:
the solve lanes, the geocoding outbox, and the startup recovery that runs before the lanes".
Decision 4 gives Access `web`, `listen`, `schedule` and `cli`. `ApplicationRules.DECLARED_ADAPTERS`
mirrors that list, and a test fails on any adapter package outside it.

[#181][ISSUE-181] stores the seeded accounts for the three roles at startup, one for each entry on
the shared list that [#170][ISSUE-170] keeps in `seeded-accounts.properties`. Only `dev` and `test`
seed, which was decided on 23 November in #181, after [ADR-121] decision 5's rule for the stand-in
session. [#185][ISSUE-185] later gives the Admin, the Dispatcher and the first Driver an Argon2id
hash. A `dev` core runs from the main classpath, from the IDE ([ADR-042]) or from an image, and
neither holds test code, so the step is main code.

None of Access's adapters fits it. `cli` is a person running a server step, `schedule` is the
clock, `listen` is an event, and `web` is a screen. The step was found with no folder when the
team's issues were checked against the declared structure, on 24 November 2026. The seeded IDs are
fixed, and the repository is public, so a seed that reached a production database would leave an
Admin that anyone could name, and [FR-291]'s first-Admin step would then refuse.

## Decision

Decided on 24 November 2026. This amends ADR-130 decisions 2 and 4; everything else in it stands.

1. **A `job` adapter is also a step that runs once at startup.** The kind now reads: a worker takes
   queued work, or a step runs once at startup. ADR-130's `job` row already names the startup
   recovery that runs before Planning's lanes.
2. **Access declares `adapter/in/job`.** It holds the trigger that runs the seed at startup, and
   nothing else: no transaction and no port. Its `package-info.java` says so, and
   `DECLARED_ADAPTERS` lists `in.job` for Access.
3. **The seed is a use case** that #181 adds in `access/application/service/account/` ([ADR-119]
   lets a class be added). Its transactions start there ([ADR-129]), and the trigger only calls it.
4. **The seed's four guards:**
   1. It exists only when the active profiles are exactly `dev` or exactly `test`, and startup
      fails when either runs with any other profile.
   2. It only adds: it stores an entry whose ID is absent and never changes an existing account's
      state, roles or hash.
   3. Under `dev`, a seeded password comes only from an environment variable, with no default in
      any file, and a missing one fails startup. Under `test`, it is generated at startup and held
      only in memory (#185). No password or hash is ever logged.
   4. Each stored account leaves an audit record, written through `CallContext.serverStep`
      ([ADR-110]).

   #181's and #185's tests cover each guard.
5. **The count of declared packages becomes 54:** 51 in the eight modules and 3 beside the kernel.
6. **The documents follow:** `code-structure.md`, `layers_visual.html`, [TS §2.1][TS-2-1], the
   [DL §13][DL-13] diagram and the Javadoc of `ApplicationRules.INBOUND_KINDS` say what `job` now
   covers, and `code-structure.md` and `architecture.md` count 54 packages.

## Consequences

- **Easy:**
  - #181 and #185 have an exact folder, and their pull requests add no package or ADR.
  - The next module that needs a startup step has a kind to put it in.
- **Hard:**
  - `job` now holds two shapes of trigger, a worker over queued work and a step that runs once. A
    reader tells them apart by the class and its Javadoc, not by the folder.
  - Because the seed only adds, a `dev` database seeded before #185 keeps its accounts without
    hashes until `just db-reset`.
- **Cost:** one `package-info.java`, one entry in `DECLARED_ADAPTERS`, and a few lines in the
  documents above.
- **Risk:** the list and the step ship in every image, so the profile is what keeps a production
  database unseeded. Guard 1 refuses every mix of profiles, guard 2 keeps a stray seed from
  restoring an account, guard 3 keeps a known password out of the repository, and guard 4 shows
  when and where a seeded account appeared. A deployment started with `dev` alone still seeds;
  keeping the list out of the image would close that, at the cost of changing how `dev` reads it.

## Alternatives considered

- **Widen Access's `in/cli`.** Rejected: `cli` is a person running a server step (ADR-130 decision
  2), and the seed runs with nobody at a prompt.
- **A seventh kind, `startup`.** Rejected: the Alpha has one such step beside Planning's recovery,
  and ADR-130's `job` already names that recovery.
- **A configuration class outside the modules,** in TS §2.1's configuration layer. Rejected: the
  seed is Access's work, and ADR-130 rejected a third top-level package beside the kernel and
  `web`.
- **A Flyway migration in a location only `dev` and `test` read.** Rejected: the accounts live in
  #170's one shared list, and SQL would keep a second copy. The hashes #185 adds are computed by the
  core, not written ahead.
- **Test code.** Rejected: a `dev` core started from the IDE or from an image holds no test code.
- **Keep the list out of the image,** in test resources mounted into `just dev`. Not now: it changes
  #170's design and how `just core-run` reads the list. It is the next step if guard 1 proves too
  weak.
- **Let #181's pull request decide.** Rejected: an adapter package outside `DECLARED_ADAPTERS` fails
  the exact-tree test, so that pull request would need this ADR anyway.

## Revisit when

- A startup step appears that neither seeds nor recovers queued work, a third startup step
  appears, or a rule (an ArchUnit rule, a readiness gate or a profile guard) has to treat a startup
  step apart from a worker: then give startup steps a kind of their own.
- A deployment other than `dev` or `test` is found seeded: then keep the list out of the image.

[ADR-042]: ADR-042-compose-dev-for-local-runs-testcontainers-for-tests.md
[ADR-110]: ADR-110-audit-records-hold-only-ids-codes-enums-numbers-and-instants-record-refusals-and-are-kept-a-year.md
[ADR-119]: ADR-119-the-application-layer-is-declared-ahead-as-signatures-whose-bodies-throw.md
[ADR-121]: ADR-121-the-application-skeletons-review-settles-its-seams-conflicts-retention-and-scoring-inputs.md
[ADR-129]: ADR-129-transactions-start-only-in-the-service-layer.md
[ADR-130]: ADR-130-every-folder-is-declared-ahead-empty-and-documented.md
[DL-13]: ../architecture/domain_layer/domain_layer_design.md#13-inside-each-module-hexagon
[FR-291]: ../requirements/requirements.md#company-users-and-roles
[ISSUE-170]: https://github.com/NaimElijah/DispatchFlow/issues/170
[ISSUE-181]: https://github.com/NaimElijah/DispatchFlow/issues/181
[ISSUE-185]: https://github.com/NaimElijah/DispatchFlow/issues/185
[TS-2-1]: ../architecture/tech_stack.md#21-the-dependency-rule

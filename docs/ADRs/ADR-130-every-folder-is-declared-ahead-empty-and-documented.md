# ADR-130: Every folder the design draws is declared ahead, empty and documented

- **Status:** Accepted
- **Date:** 2026-11-24

## Context

Four people start their first adapters, screens and packages in the same weeks of the Alpha. On
2026-11-24 the tree and the decided layout ([TS §4][TS-4], [DL §13][DL-13], [ADR-040],
[ADR-123]) differed ([issue #102][ISSUE-102]):

- **The core.** Every module had `domain`, `events`, `application` and `adapter/out/persistence`,
  and nothing else under `adapter`.
- **The optimizer.** Its solvers sat in `solvers/ortools` and `solvers/alns`, holding only a
  `.gitkeep` each, while [ADR-040] puts them under `adapters/outbound/solvers/`.
- **The apps.** The web app had no `src/features/`, which [TS §10][TS-10] draws. [TS §11][TS-11]
  draws no structure for the phone app.
- **Everything else.** `src/frontend/packages/`, `infra/terraform/`, the backup and observability
  folders, and the `docs/` folders [TS §4][TS-4] draws did not exist. `src/research/instances/`
  held a `.gitkeep`, although [ADR-123] puts benchmark instances in the git-ignored `data/`.

Without agreed places, each lane would invent its own, and the first GovMap and OSRM adapters
would land in different shapes.

The documents also disagreed, or said nothing:

- **Inbound kinds.** [TS §4][TS-4] draws `in/web`, `in/job`, `in/schedule` and `in/callback`. The
  core's `CLAUDE.md` draws `in/listen`. [DL §13][DL-13] draws an event stream and listeners.
  [TS §2.1][TS-2-1] and [ADR-120] decision 1 name a CLI. The declared use cases hold all of
  these: 103 web calls, 6 of them before sign-in; 20 listeners; 20 clock triggers; 2 queue workers
  and a startup recovery; 1 provider callback; and 6 server steps a person runs at a prompt or
  from a runbook, such as `CreateFirstAdmin`.
- **Outbound names.** "Named for the outside system" ([TS §4][TS-4]) fits 12 ports. The other 12
  call nothing outside the process: hashing, signing, a cache, configuration, a seeded generator,
  bundled files, the clock and the IDs.
- **The shared ports.** `Clock` and `IdGenerator` in `shared.application` had no place for their
  adapters. The flat and purity guards let only `shared.adapter.out.persistence` through.
- **Live updates.** Planning and Live Operations each declare a `LiveUpdates` port. Planning may
  not use Live Operations, so the registry of open streams fits neither module.
- **State behind a port that calls nothing outside.** [NFR-215], a Must row, caps recovery emails
  at 100 per Israel day. A count kept in memory restarts at 0 when the core restarts. GovMap's
  cache of geocoded addresses ([FR-13]) and the proof store's bindings and tombstones are tables,
  but the adapters that need them are `govmap` and `disk`, not `persistence`.
- **The optimizer client.** [ADR-043] generates one client from `optimization-api`. Planning
  solves and validates through it, and Reporting plans its baseline through it, but one module's
  adapter may not use another's.

On 2026-11-24 the owner, Naim Elijah, asked that every folder the project will need, to its end,
be declared now, so that the team only adds files to the right places. The owner chose each
answer below. [Issue #118][ISSUE-118] and [issue #142][ISSUE-142] carry the work.

## Decision

This extends [ADR-039], [ADR-040], [ADR-119], [ADR-120] decision 10, [ADR-123], [ADR-125]
decision 4 and [ADR-126]. All of them stand.

1. **Every folder is declared now, empty, and says what it is for.** [ADR-119] declared the
   application layer ahead. This does the same for every folder [TS §4][TS-4], [DL §13][DL-13] and
   this ADR draw, to the end of the project.
   - A Java package gets a `package-info.java`. Its Javadoc says what goes in the package and
     which ports it implements, and it carries `@NullMarked`. It compiles to a class that ArchUnit
     imports, so the rules see the package before it holds code.
   - A Python package gets an `__init__.py` with a Google-style docstring, which ruff requires.
     Where import-linter runs, as in the optimizer, it checks a folder only once it is a package.
   - Any other folder gets an empty `.gitkeep`. The first real file put in the folder deletes it.
   - No class, bean, stub test or `package.json` is added.
   - Nothing [ADR-090] cuts gets a folder.
2. **Six inbound kinds,** `<module>/adapter/in/<kind>`, read off each use case's first parameters
   and, where those don't tell, its Javadoc ([ADR-120] decision 1):

   | Kind | Starts a use case when |
   |---|---|
   | `web` | a screen calls the client API: an `Actor` first or, before sign-in, values and a `CallContext` |
   | `listen` | another module's event arrives: the event, then a `CallContext` |
   | `schedule` | the clock says so, over whatever is due: the midnight close, the minute jobs, the nightly retention steps |
   | `job` | a worker takes queued work: the solve lanes, the geocoding outbox, and the startup recovery that runs before the lanes |
   | `callback` | an outside service calls in: the SMS provider's reply |
   | `cli` | a person runs a server step at a prompt or from a runbook |

   There is no `in/sse`. The browser opens one stream for every module, and its endpoint is
   app-wide (decision 6).
3. **Outbound names,** `<module>/adapter/out/<name>`:
   - named for the outside system or channel the adapter talks to: `osrm`, `optimizer`, `govmap`,
     `sms`, `email`, `push`, `disk` and `sse`;
   - `persistence` for the module's own database ([ADR-126]);
   - for an adapter that calls nothing outside the process, named for the technology it wraps:
     `crypto` (hashing and signing), `config`, `random`, `resources` (files shipped with the core)
     and `roaddata` (what the monthly road-data build produces).

   A second system behind the same port gets its own package beside the first. If GovMap's terms
   fail, Nominatim ([ADR-085]) is `nominatim`.
4. **Each module's packages.** `persistence` already exists in every module.

   | Module | `adapter/in` | Other `adapter/out` packages, and the ports they implement |
   |---|---|---|
   | access | `web`, `listen`, `schedule`, `cli` | `email` (`EmailSender`); `crypto` (`PasswordHasher`, `TokenSigner`, `SecretTokens`); `resources` (`CommonPasswordList`) |
   | audit | `schedule` | none |
   | fleet | `web`, `listen`, `schedule` | none |
   | orders | `web`, `schedule`, `job` | `govmap` (`Geocoder`); `osrm` (`RoadSnap`); `roaddata` (`ServiceAreas`) |
   | planning | `web`, `listen`, `schedule`, `job` | `optimizer` (`RouteOptimizer`, `FeasibilityChecker`); `osrm` (`TravelTimes`, `RouteShapes`); `sse` (`LiveUpdates`); `random` (`Seeds`); `config` (`SolveConfiguration`) |
   | liveops | `web`, `listen`, `schedule` | `push` (`PushNotifier`); `sse` (`LiveUpdates`); `disk` (`ProofStore`, [ADR-027]) |
   | messaging | `web`, `listen`, `schedule`, `callback`, `cli` | `sms` (`SmsGateway`); `crypto` (`PhoneHasher`) |
   | reporting | `web`, `listen` | `optimizer` (`BaselinePlanner`) |

   - **Persistence-shaped ports go in the module's `persistence` package:** the `*Views` ports,
     `GeocodeRequests`, `StoredMatrices`, `ReportReceipts` and `PushTokens`, and Access's
     `RevocationCache` and `RecoveryEmailCap`.
   - **`RevocationCache` sits in `persistence`,** because the repositories answer
     `activeGenerationOf` and `liveGenerationOf` through the cache's loader: "the cache sits in
     the adapter", as `UserAccountRepository` says.
   - **`RecoveryEmailCap` counts in the database:** the day's `RECOVERY` links in
     `access.credential_link`, by `issued_at` in Israel time ([ADR-088]). A restart then keeps the
     count, so the cap [NFR-215] sets holds all day, and no table is added.
   - **An adapter that needs its module's tables reaches them through a port** that the
     module's `persistence` adapter implements ([TS §2.1][TS-2-1]), never by mapping them itself:
     GovMap's cache of geocoded addresses ([FR-13]) and the proof store's bindings and tombstones.
     The milestone that builds the adapter declares the port, so every table stays in
     `persistence`, and decision 7's first rule holds unchanged.
   - **Planning and Reporting each have their own `optimizer` adapter,** and both wrap the one
     generated client (decision 5). Planning and Orders each have their own `osrm`. One module's
     adapter never uses another's (decision 7), and Reporting may use no Planning class.
5. **Three shared adapters.** `shared/adapter/out/clock` implements `Clock`: the system clock,
   and the end-to-end profile's settable one. `shared/adapter/out/uuid` implements `IdGenerator`,
   with UUIDv7. `shared/adapter/out/optimizer` holds the client [ADR-043] generates from
   `optimization-api`: one generator run and one set of types, which Planning's and Reporting's
   `optimizer` adapters each wrap for their own ports. It implements no port, and its types stop
   at those adapters. `ProcessedEvents` goes in `shared/adapter/out/persistence`.
   - The flat guard (`DomainPackageRules.packagesBelowAFlatOne`, [ADR-125] decision 4) and the
     purity rules (`DomainPurityRules.inPureLayers`) let any `shared.adapter.in.<x>` or
     `shared.adapter.out.<x>` through, not only `persistence` ([ADR-126]).
   - Any other kernel subpackage, such as `shared.adapter.util`, still fails.
   - No pure class may use any `shared.adapter` class, and a shared adapter uses no module
     (decision 7).
6. **Live updates.** Planning's and Live Operations' `LiveUpdates` adapters each go in their
   module's `adapter/out/sse`. The stream's endpoint, its registry of open streams and the listener
   that closes an account's streams on `SessionsRevoked` belong to no module, nor do the end-to-end
   profile's control endpoints, such as the one that sets the test clock. The app-wide `web` ADR
   places them ([ADR-040], [issue #143][ISSUE-143]), and it also decides how an `out/sse`
   adapter reaches the registry: today rule 2 refuses a module any dependency on `web`.
7. **Adapters meet only through ports** ([TS §2.1][TS-2-1]). Three ArchUnit rules extend [ADR-120]
   decision 10:
   - **Within a module, adapters stay apart.** A class in one adapter (`adapter.in.<a>` or
     `adapter.out.<a>`, with its subpackages) uses no class of another. That covers `in` using
     `out`, `out` using `in`, and two adapters of one kind. The adapters beside the kernel stay
     apart from each other too.
   - **No adapter uses another module's adapter.** `shared` and `web` are not modules, so this rule
     leaves their adapters usable. Rule 2 still decides whether a module may reach `web`.
   - **A shared adapter uses no module.** A class under `shared.adapter` uses no class of a
     module, so no module reaches another through it, around DL §2.1's graph.

   Each rule has a fixture it must flag. `ApplicationRules` rule 5's `@NullMarked` guard covers
   the adapter packages too.
8. **The optimizer.** The solvers move under
   `adapters/outbound/solvers/{ortools,alns,nearest_neighbour}`, as [ADR-040] decided.
   `nearest_neighbour` is the baseline's solver, which the service's registry does not hold
   ([ADR-121] P16). The import-linter layers become `adapters`, `application`, `domain`, and the
   optional `(solvers)` layer goes. The HTTP adapter's `create_app()` is the composition root: it
   fills the solver registry, so it imports the solver packages, which one `adapters` layer
   allows. Decision 7's rules bind the core.
9. **The apps and the shared packages.**
   - **Feature folders.** The web app and the phone app each get `src/features/<context>` for
     `access`, `audit`, `fleet`, `liveops`, `messaging`, `orders`, `planning` and `reporting`
     ([DL §1][DL-1]). The Optimization context has no screens.
   - **The phone app's routes come at M10.** Expo Router's route folder arrives with `expo-router`,
     because the folder becomes the route root once the package is installed.
   - **The shared packages.** `src/frontend/packages/` gets the seven folders [ADR-039] names. A
     folder is not a package: a package exists once its `package.json` does, and the ticket that
     first puts code in the folder adds it. Any eighth package still waits until hand-written code
     would otherwise be copied between the apps ([ADR-123]).
10. **Infra, docs and data,** as [TS §4][TS-4] draws them:
    - **infra:** `infra/terraform/`, `infra/deploy/backup/` and `infra/deploy/observability/`;
    - **docs:** `docs/operations/`, `docs/guides/he/`, `docs/guides/en/`,
      `docs/research/usability/` and `docs/architecture/generated/`;
    - **data:** `data/` gets a tracked `.gitkeep`, as `logs/` does, and git still ignores
      everything else in it. Benchmark instances live there ([ADR-123]), so
      `src/research/instances/` goes;
    - **research:** `benchmark`, `generator` and `simulation` become packages.

## Consequences

- **Easy:** a new adapter, screen, package or document has one place, and its folder already says
  what goes there and which port it implements. Lanes that start in the same weeks land in the same
  places.
- **Easy:** the adapter rules hold before the first adapter exists, so a wrong import between
  adapters fails the build rather than waiting for a review.
- **Hard:** the declaration adds 85 files that hold no code: 45 `package-info.java`, 8 `__init__.py`
  and 32 `.gitkeep`. The tree alone can't show which folders are built, so `code-structure.md` marks
  the placeholders.
- **Hard:** a name chosen before its code may prove wrong. An empty package is cheap to rename, but
  the issues that name it change too.
- **Cost:** the 85 files, three ArchUnit rules with their fixtures, two widened guards, and the
  documents that describe the tree.
- **Risk:** a placeholder can outlive its purpose and mislead. The first trigger below deletes it.

## Alternatives considered

- **Create each folder when its first file lands.** Rejected: four lanes start adapters in the same
  weeks and would each invent a place ([issue #102][ISSUE-102]).
- **A README in each folder instead of `package-info.java`.** Rejected: ArchUnit can't see a README,
  and a Java folder with no class is no package to the rules.
- **Five inbound kinds, with the server steps under `job`.** Rejected by the owner: a job takes
  queued work, and a step a person types at a prompt is not one.
- **`security` for the hashing and signing adapters.** Rejected: it reads like Spring Security's
  filter chain, which is app-wide.
- **`proofstore`, named for the port.** Rejected: an adapter is named for what it talks to, here the
  VM's disk ([ADR-027]).
- **One `shared/adapter/out/system` for the clock and the IDs.** Rejected by the owner: the name
  says less about what is inside.
- **A top-level `config` package for the clock and the IDs.** Rejected by the owner: it would be a
  third non-module beside `shared` and `web`.
- **An `in/sse` kind in each module.** Rejected: the browser would open one stream per module.
- **Declare the stream's registry in `shared/adapter/out/sse` now.** Rejected by the owner: it
  settles part of the app-wide `web` ADR before that ADR is written.
- **Keep `RecoveryEmailCap`'s count in memory, in an `access/adapter/out/cache`.** Rejected by
  the owner: after a restart the count is 0, so up to 100 more recovery emails could go out that
  day, against [NFR-215], a Must row.
- **Let `govmap` and `disk` map their own tables.** Rejected by the owner: a module would have two
  database adapters, and the cache could not reuse `persistence`'s converters.
- **Split `Geocoder` now, so the geocoding job checks a cache port itself.** Rejected by the owner:
  it changes the declared ports' contracts before the milestone that builds them.
- **Each module generates its own optimizer client.** Rejected by the owner: two generator runs
  and two identical sets of types.
- **Leave the phone app until M10.** Rejected by the owner: its feature folders mirror the web
  app's.
- **Keep `src/research/instances/`.** Rejected: [ADR-123] puts benchmark instances in the git-ignored
  `data/`.

## Revisit when

- A milestone ends with a folder it should have filled still empty. Delete the folder, or name the
  milestone that fills it.
- A use case starts in a way none of the six kinds describes, or a port talks to a system this ADR
  names no package for.
- The app-wide `web` ADR ([issue #143][ISSUE-143]) places the stream's endpoint and registry.
- M10 adopts Expo Router, which fixes where the phone app's routes go.
- Two modules' adapters for one system, such as `osrm`, grow the same code. Then the shared part
  moves to `shared/adapter/out/<system>`.

[ADR-027]: ADR-027-proof-files-on-the-vm-disk-with-hourly-backups.md
[ADR-039]: ADR-039-one-pnpm-workspace-for-both-apps-and-shared-packages.md
[ADR-040]: ADR-040-core-is-a-spring-modulith-monolith-with-one-module-per-context.md
[ADR-043]: ADR-043-generated-code-from-both-contracts-and-problem-details-errors.md
[ADR-085]: ADR-085-govmap-geocodes-addresses-with-nominatim-as-the-fallback.md
[ADR-088]: ADR-088-israel-local-time-for-every-date-and-schedule.md
[ADR-090]: ADR-090-scope-cuts-for-the-first-release.md
[ADR-119]: ADR-119-the-application-layer-is-declared-ahead-as-signatures-whose-bodies-throw.md
[ADR-120]: ADR-120-the-application-layer-takes-an-actor-and-a-call-context-and-reads-through-query-ports.md
[ADR-121]: ADR-121-the-application-skeletons-review-settles-its-seams-conflicts-retention-and-scoring-inputs.md
[ADR-123]: ADR-123-the-code-keeps-its-grouped-layout-and-three-components-take-new-names.md
[ADR-125]: ADR-125-a-folder-above-20-files-splits-wherever-a-group-of-four-can-move.md
[ADR-126]: ADR-126-jpa-replaces-jooq-and-the-domain-carries-its-mapping.md
[DL-1]: ../architecture/domain_layer/domain_layer_design.md#1-subdomains-and-bounded-contexts
[DL-13]: ../architecture/domain_layer/domain_layer_design.md#13-inside-each-module-hexagon
[FR-13]: ../requirements/requirements.md#geocoding
[ISSUE-102]: https://github.com/NaimElijah/DispatchFlow/issues/102
[ISSUE-118]: https://github.com/NaimElijah/DispatchFlow/issues/118
[ISSUE-142]: https://github.com/NaimElijah/DispatchFlow/issues/142
[ISSUE-143]: https://github.com/NaimElijah/DispatchFlow/issues/143
[NFR-215]: ../requirements/requirements.md#nfr-215
[TS-2-1]: ../architecture/tech_stack.md#21-the-dependency-rule
[TS-4]: ../architecture/tech_stack.md#4-repository-layout
[TS-10]: ../architecture/tech_stack.md#10-web-app
[TS-11]: ../architecture/tech_stack.md#11-phone-app

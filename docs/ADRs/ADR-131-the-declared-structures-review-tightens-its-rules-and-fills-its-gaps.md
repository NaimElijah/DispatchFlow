# ADR-131: The declared structure's review tightens its rules and fills its gaps

- **Status:** Accepted
- **Date:** 2026-11-24

## Context

[ADR-130] declared every folder the project will need, and #263 merged it into `dev`. Before it
reached `main`, the owner, Naim Elijah, asked for the merged work to be reviewed again. Six
reviewers read it: code, tests, docs, security, architecture and infrastructure. None found a
blocker. They found four kinds of gap:

- **Rules that promise more than they check.**
  - Rule 11 let a shared adapter use `web`, and rule 2 skips `web`. So a module could reach any
    other through a shared adapter and `web`, around [DL §2.1][DL-2-1]'s graph.
  - Nothing kept the generated optimizer client inside the optimizer adapters, although ADR-130
    decision 5 says its types stop there.
  - The optimizer's one layers contract put every adapter in one layer, so a solver could import
    the HTTP adapter or another solver, against [ADR-040]'s "never on each other".
- **Folders the design draws that nobody declared:**
  - Fleet's own depot geocoding ([ADR-120] P4);
  - Reporting's own port for the scoring rates ([ADR-121] decision 1);
  - the optimizer's `Stopwatch` adapter, and [ADR-051]'s solver workers;
  - research's PyVRP adapter, and its YAML study files ([TS §4][TS-4], [NFR-122]).
- **Two runtime gaps the placement made visible.**
  - A server step runs in a process of its own ([ADR-069]). When it deactivates an account
    ([FR-346]), its revocation-cache eviction can't reach the serving core, so the old token keeps
    working for up to 60 seconds, against [NFR-177], a Must row.
  - Counting the day's recovery links ([ADR-130] decision 4) lets two requests at 99 both pass,
    and the database session runs in UTC, not Israel time.
- **The steps that span every module.** The personal-data requests ([NFR-49], [NFR-185], a Must
  row), the company's export and deletion ([NFR-150]) and the audited pilot-day export ([FR-289],
  a Must row) touch all eight modules, and no module may reach all eight. [ADR-075]'s "one audited
  transaction" over every module also conflicts with [ADR-063] and [ADR-129]: a transaction never
  spans modules.

The owner chose each answer below on 2026-11-24, all as recommended.

## Decision

This amends [ADR-130] decisions 4, 5 and 7, and [ADR-075]'s requests bullet. It extends [ADR-045],
[ADR-051], [ADR-120] (P4 and decision 9) and [ADR-121] decision 1. Everything else in them
stands.

1. **Rule 11 allows only the kernel.** A class in, or below, `shared.adapter` uses no class of
   another top-level package, `web` included. No requirement needs a shared adapter to use `web`:
   the end-to-end profile's clock endpoint points the other way, from `web` to the clock.
2. **Rule 12 keeps the generated optimizer client in its adapters.** A class outside
   `shared.adapter.out.optimizer` that uses it lies in, or below, Planning's or Reporting's
   `adapter.out.optimizer` ([ADR-043], DL §2.1's arrows to the optimizer).
3. **The optimizer's adapters stay apart.** Two import-linter contracts join the layers contract:
   - no outbound adapter imports an inbound one;
   - the three solvers import none of each other.

   `create_app()` still fills the solver registry, because an inbound adapter may import an
   outbound one.
4. **Six more folders:**

   | Folder | What goes there |
   |---|---|
   | `fleet/adapter/out/govmap` | the port of Fleet's own that geocodes a depot's address, which M7 declares; Fleet and Customers & Orders have no relationship |
   | `reporting/adapter/out/config` | Reporting's port for the scoring rates, bound to the configuration Planning's `SolveConfiguration` reads, which M5 declares |
   | optimizer `adapters/outbound/clock` | the `Stopwatch` port, over the monotonic clock |
   | optimizer `adapters/inbound/worker` | [ADR-051]'s solver worker per lane. It sits apart from `http` because `spawn` mode re-imports the worker's module in each process, so FastAPI never loads in a solver process |
   | research `dispatchflow_research/solvers/pyvrp` | the harness's PyVRP adapter, which never enters the product ([FR-286]) |
   | research `studies/` | the YAML study files, validated by Pydantic ([NFR-122]) |

5. **A `cli` in every module for the steps that span them.**
   - Fleet, Customers & Orders, Planning, Live Operations, Reporting and Audit gain
     `adapter/in/cli`, as Access and Messaging already have.
   - Each module's step works on that module's own personal columns, as the data inventory lists
     them. It finds, exports, corrects or anonymizes them in one audited transaction of its own.
     It is idempotent, so a rerun completes a run that stopped.
   - One runbook script runs the eight steps in order. [NFR-185]'s 30 days leave time for a rerun.
   - This replaces [ADR-075]'s single transaction over every module. The use cases stay out until
     their milestone designs them ([ADR-120] decision 9), and they land in these packages.
6. **An eviction reaches every core process** ([ADR-045]).
   - `RevocationCache`'s adapter, in Access's `persistence`, also sends the evicted key on a
     PostgreSQL channel, with `pg_notify`.
   - The serving core listens on that channel and drops the same entry. If its listening
     connection drops, it clears the whole cache when it reconnects, since it may have missed a
     notification.
   - No package is added: the listener belongs to the adapter that owns the cache.
   - A test pins it: `GivenAServingCoreThatCachedAnAdminsGeneration_WhenAServerStepDeactivatesTheAccount_ThenTheOldTokenIsRefused`.
7. **The recovery cap holds under concurrency** ([NFR-215]).
   - `RecoveryEmailCap`'s adapter counts the Israel day's `RECOVERY` links under
     `pg_advisory_xact_lock`, keyed by the day, so takes serialize until they commit.
   - It counts between the day's first and last instant, computed in `Asia/Jerusalem`
     ([ADR-088]), because the database session runs in UTC.
   - A test keeps `UserAccount.LINK_KEPT_FOR` at 25 hours or more, the longest Israel day.
     Otherwise pruning could drop the same day's rows and lower the count.
   - The once-a-day alert's mark is kept where a restart can't lose it, and a test restarts the
     core between two refusals.
   - No table is added, so ADR-130's choice stands.
8. **The app-wide `web` ADR places more than the stream** ([issue #143][ISSUE-143], [ADR-040]).
   Besides what ADR-130 decision 6 lists, it places:
   - the code generated from `client-api`, whose interfaces every `in/web` controller implements;
   - the component that builds the `Actor`, and the capability check;
   - where the use cases become beans.

   It also decides how an `in/web` adapter reaches them, since rule 2 refuses a module any
   dependency on `web` today.
9. **The design documents follow ADR-130 and this ADR.** Under the precedence rule, these are
   corrected in the same change:
   - [DL §13][DL-13] and DL §7.2: the event stream is one app-wide endpoint;
   - [TS §2.1][TS-2-1] and §2.2: the adapter kinds, and rules 9 to 12;
   - TS §4, §6.4 and §19: the folders, the optimizer adapters, and the usability study's
     recordings.

## Consequences

- **Easy:** the rules now check what ADR-130 says they check. A wrong import through a shared
  adapter, `web`, the optimizer client or another solver fails the build.
- **Easy:** every step that spans the modules, and every adapter the accepted ADRs name, has its
  place before its milestone.
- **Hard:** a personal-data request is eight audited transactions, not one. A run that stops
  partway leaves some modules done until the rerun.
- **Hard:** the token check now depends on a listening connection. A dropped one costs a full
  cache clear, not stale entries.
- **Cost:** two ArchUnit rule changes with their fixtures, two import-linter contracts, and 13
  more files that hold no code:
  - 8 `package-info.java`, making 53: 50 in the modules and 3 beside the kernel;
  - 4 `__init__.py`, making 12;
  - 1 `.gitkeep`, making 33.

  M6 builds the notification and the lock with their tests.
- **Risk:** the runbook script must run every module's step. A module missing from it leaves that
  module's personal data untouched. The data inventory's test ([ADR-075]) lists each module's
  personal columns, so the script is checked against it.

## Alternatives considered

- **Leave rule 11 allowing `web`.** Rejected: rule 2 skips `web`, so a shared adapter that used it
  could reach any module.
- **Only review keeps the generated client in its adapters.** Rejected: ADR-130 states the
  invariant, and a rule costs one fixture.
- **Run a revoking server step with `core` stopped.** Not chosen by the owner: it costs a minute of
  downtime for every written request. The serving core hears the eviction instead.
- **Design the cross-module steps at their milestone, and declare nothing now.** Not chosen by the
  owner: each module will own its part anyway, and the company exit needs every module's export.
- **A top-level `admin` package that calls every module's `api`.** Rejected: it would be a third
  non-module beside `shared` and `web`, and the transaction conflict would remain.
- **Put the solver workers in `adapters/inbound/http`.** Rejected: `spawn` mode would import
  FastAPI and Pydantic in every solver process.

## Revisit when

- A requirement needs a shared adapter to use `web`, or a module other than Planning and Reporting
  to call the optimizer.
- The runbook script for the cross-module steps is written. Check that it covers every module the
  data inventory names.
- PostgreSQL notifications prove unreliable in the serving core's connection pool. Then a revoking
  step runs with `core` stopped.

[ADR-040]: ADR-040-core-is-a-spring-modulith-monolith-with-one-module-per-context.md
[ADR-043]: ADR-043-generated-code-from-both-contracts-and-problem-details-errors.md
[ADR-045]: ADR-045-generation-counters-revoke-tokens.md
[ADR-051]: ADR-051-one-solver-worker-per-lane-killed-30-seconds-past-its-limit.md
[ADR-063]: ADR-063-a-transaction-changes-one-aggregate-and-every-rule-has-one-owner.md
[ADR-069]: ADR-069-sign-in-and-transport-security.md
[ADR-075]: ADR-075-retention-deletes-records-and-anonymizes-people-in-place.md
[ADR-088]: ADR-088-israel-local-time-for-every-date-and-schedule.md
[ADR-120]: ADR-120-the-application-layer-takes-an-actor-and-a-call-context-and-reads-through-query-ports.md
[ADR-121]: ADR-121-the-application-skeletons-review-settles-its-seams-conflicts-retention-and-scoring-inputs.md
[ADR-129]: ADR-129-transactions-start-only-in-the-service-layer.md
[ADR-130]: ADR-130-every-folder-is-declared-ahead-empty-and-documented.md
[DL-2-1]: ../architecture/domain_layer/domain_layer_design.md#21-module-graph
[DL-13]: ../architecture/domain_layer/domain_layer_design.md#13-inside-each-module-hexagon
[FR-286]: ../requirements/requirements.md#research-and-evaluation
[FR-289]: ../requirements/requirements.md#research-and-evaluation
[FR-346]: ../requirements/requirements.md#company-users-and-roles
[ISSUE-143]: https://github.com/NaimElijah/DispatchFlow/issues/143
[NFR-49]: ../requirements/requirements.md#nfr-49
[NFR-122]: ../requirements/requirements.md#nfr-122
[NFR-150]: ../requirements/requirements.md#nfr-150
[NFR-177]: ../requirements/requirements.md#nfr-177
[NFR-185]: ../requirements/requirements.md#nfr-185
[NFR-215]: ../requirements/requirements.md#nfr-215
[TS-2-1]: ../architecture/tech_stack.md#21-the-dependency-rule
[TS-4]: ../architecture/tech_stack.md#4-repository-layout

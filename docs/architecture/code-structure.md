# DispatchFlow — Code structure

Where each kind of code and test lives, and which way dependencies point. It describes the
repository as it is today. What the system must do is in
[requirements.md](../requirements/requirements.md); why the repository is laid out this way is in
[ADR-008](../ADRs/ADR-008-monorepo-layout-under-src.md) (superseded by ADR-037, not in the code
yet), and why it is built and checked the way
it is in [ADR-009](../ADRs/ADR-009-just-as-the-single-entry-point.md) to
[ADR-016](../ADRs/ADR-016-dependency-audit-with-osv-scanner.md).

[ADR-037](../ADRs/ADR-037-flat-layout-under-src-with-deploy-and-infra.md) (Accepted) moves
this tree to a flat layout, in the M0 restructure. The pull request that moves the code rewrites
this file.

Also Accepted and not in the tree yet:
[ADR-038](../ADRs/ADR-038-mise-pins-every-toolchain-and-core-moves-to-java-25.md) (`mise` pins every
toolchain, and the core moves to Java 25),
[ADR-039](../ADRs/ADR-039-one-pnpm-workspace-for-both-apps-and-shared-packages.md) (one pnpm
workspace for both apps),
[ADR-042](../ADRs/ADR-042-compose-dev-for-local-runs-testcontainers-for-tests.md) (`compose.dev.yaml`
for local runs) and
[ADR-046](../ADRs/ADR-046-renovate-replaces-dependabot-and-targets-dev.md) (Renovate replaces
Dependabot). They supersede or amend some of ADR-009 to ADR-016 above: ADR-010, ADR-012 and ADR-014
are superseded; ADR-009, ADR-013 and ADR-015 are amended.

> [!NOTE]
> Most folders below are still empty. A folder listed as a placeholder holds only a `.gitkeep`
> file. What is real today is the skeleton, and the domain layer of `core` and `solver`: each
> component builds, passes its own checks and runs a first test.

## Top level

| Path | Holds |
|---|---|
| `src/backend/core/` | The REST API and system of record: Java 21, Spring Boot 4.1.2, Maven |
| `src/backend/solver/` | The optimization service: Python 3.14, FastAPI, NumPy, uv |
| `src/frontend/console/` | The web app, for every role: React, TypeScript, Vite |
| `src/frontend/driver-app/` | The phone app, for every role: React Native, Expo SDK 58, TypeScript |
| `src/research/` | Benchmarks, simulation and the order generator: Python, uv. Never deployed |
| `contracts/` | `client-api/v1` and `optimization-api/v1` (OpenAPI), and the Redocly lint setup |
| `tests/` | Tests that span components: `e2e/` and `load/`. Both are placeholders |
| `infra/` | `osrm/` and `deploy/`. Both are placeholders |
| `logs/` | Local log output. Git keeps the folder and ignores everything in it |
| `docs/` | `CLAUDE.md`, `requirements/` (with `ARD/`), `architecture/` (with `domain_layer/` and `tech_stack.md`), `ADRs/`, `roadmap.md` and `presentations/` |
| `.claude/` | Claude Code's context: the root `CLAUDE.md`, the team agents (`agents/`), the team skills (`skills/`), `settings.json`, and an empty `rules/` folder ([ADR-018](../ADRs/ADR-018-ai-assistant-context-in-the-repository.md), [ADR-020](../ADRs/ADR-020-team-subagents-in-the-repository.md), [ADR-025](../ADRs/ADR-025-team-skills-in-the-repository.md)) |
| `.github/` | The CI workflow, Dependabot configuration, and issue and pull request templates |
| `justfile` | Every command. CI runs the same recipes |
| `README.md`, `.editorconfig`, `.gitattributes`, `.gitignore` | The readme, and the editor, line-ending and ignore settings |
| `.java-version`, `.python-version`, `.nvmrc` | Toolchain pins: Java 21, Python 3.14, Node 24 |
| `LICENSE` | Proprietary, all rights reserved ([ADR-017](../ADRs/ADR-017-proprietary-licence.md)) |

Each component has its own manifest, and each except `core` (Maven) has a lockfile. The one link
at build time is `research`'s path dependency on `solver`, below.

`src/backend`, `src/frontend`, each component, `contracts/` and `docs/` also hold a `CLAUDE.md` with
the rules for that folder.

## How the components depend on each other

```mermaid
flowchart LR
    console["console"] -.->|"client API"| core["core"]
    driver["driver-app"] -.->|"client API"| core
    core -.->|"optimization API"| solver["solver"]
    research["research"] -->|"editable path dependency"| solver
```

A dotted arrow is a contract that is defined but that no code calls yet. The solid arrow is a
build dependency that exists today.

- The two frontends talk only to `core`, through `contracts/client-api/v1`. `core` serves that
  contract.
- `core` is the only client of `solver`, through `contracts/optimization-api/v1`. `solver` serves
  that contract.
- Neither side owns a contract, so the contracts sit at the root, outside every component.
- Today `contracts/client-api/v1`, served under `/api/v1`, declares no operation yet, and
  `contracts/optimization-api/v1` declares one: `GET /health/liveness`. `core` has no client for
  the optimization API yet.
- `research` installs `solver` from `../backend/solver` as an editable path dependency, and adds
  PyVRP. The solver's environment never contains PyVRP:
  `src/backend/solver/tests/test_dependency_boundary.py` fails if PyVRP is installed there or locked
  in its `uv.lock` ([ADR-004](../ADRs/ADR-004-pluggable-solver-port.md)).
- A change to `src/backend/solver/pyproject.toml` also needs `uv lock` in `src/research`.

## Inside each component

### `src/backend/core`

```text
src/backend/core/
  pom.xml, mvnw, mvnw.cmd, .mvn/   Maven and its wrapper
  CLAUDE.md                        the rules for this folder
  Dockerfile, .dockerignore
  .env.example                     the database connection keys, with no values
  src/main/java/com/dispatchflow/
    DispatchFlowApplication.java   the Spring Boot entry point
    shared/                        the shared kernel: the typed IDs, the shared values, their
                                   errors, and the building blocks every module's domain uses
    access/                        a bounded context's module: domain/ (account, session,
                                   companyprofile) and events/ so far
    fleet/                         domain/ (register, roster, driver) and events/ so far
    orders/                        domain/ (flat) and events/ so far
    planning/                      domain/ (plan, solution, basis, revision, reconciliation, settings)
                                   and events/ so far
    audit/                         domain/ (flat) so far; it raises no event
    reporting/                     domain/ (flat) so far; it raises no event
    messaging/                     domain/ (message, rating, settings) and events/ so far
    liveops/                       domain/ (delivery, alert, routerun) and events/ so far
    web/                           application-wide web concerns: the fallback for unexpected errors
      UnexpectedErrorHandler.java  answers an unhandled exception with a 500 problem body
      package-info.java
  src/main/resources/
    application.properties
    db/migration/                  Flyway migrations: none yet
  src/test/java/com/dispatchflow/
    shared/                        the kernel's tests, and DomainAssertions, the refusal helper
                                   every module's domain tests use
    architecture/                  the domain purity, shape and package rules (ArchUnit) and the
                                   error-code, event-name and refusal-test checks
    archfixture/                   deliberately wrong classes that each rule must flag, one
                                   subpackage per rule under alpha/domain/, and epsilon/ for the
                                   package rules
    access/                        that module's domain tests, in the package of the type each tests
    fleet/                         that module's domain tests
    orders/                        that module's domain tests
    planning/                      that module's domain tests
    audit/                         that module's domain tests
    reporting/                     that module's domain tests
    messaging/                     that module's domain tests
    liveops/                       that module's domain tests
    web/UnexpectedErrorHandlerTest.java  the 500 problem body keeps the cause out
    IntegrationTest.java                 the one annotation every *IT uses: the whole app and PostgreSQL
    DispatchFlowApplicationIT.java       the health probe answers UP against PostgreSQL
    TestDispatchFlowApplication.java     what `just core-run` starts
    TestcontainersConfiguration.java     the PostgreSQL container
```

The shared kernel exists
([ADR-097](../ADRs/ADR-097-the-shared-kernel-holds-the-domains-building-blocks.md)), and so do the
`domain` packages of `access`, `audit`, `fleet`, `liveops`, `messaging`, `orders`, `planning` and
`reporting`, and the `events` packages of the six that raise events. A domain package of more than 30
files splits into one package per aggregate, over a root that holds the module's error
([ADR-118](../ADRs/ADR-118-a-domain-package-above-30-files-splits-by-aggregate.md)): `access`,
`fleet`, `liveops`, `messaging` and `planning` are split, `orders`, `audit` and `reporting` are flat,
and so are the kernel and every `events` package. A test sits in the package of the type it tests,
and a refusal test may sit anywhere under its error's package. The decided design is the domain document, the tech stack and the ADRs:
see the [domain layer
design](domain_layer/domain_layer_design.md#1-subdomains-and-bounded-contexts). How a context is
laid out inside is in [tech stack §4](tech_stack.md#4-repository-layout) and [domain layer design
§13](domain_layer/domain_layer_design.md#13-inside-each-module-hexagon) (the hexagon). New contexts
are written to tech stack §4.
[ADR-040](../ADRs/ADR-040-core-is-a-spring-modulith-monolith-with-one-module-per-context.md)
(Accepted) makes each context a Spring Modulith module, and
[ADR-041](../ADRs/ADR-041-one-postgresql-schema-per-module-read-and-written-through-jooq.md)
(Accepted) gives each module its own schema, accessed through jOOQ. Neither is in the tree yet.
[ADR-001](../ADRs/ADR-001-domain-driven-design-with-hexagonal-architecture.md)'s layout (one package
per context, split into `api`, `domain`, `application` and `adapter`) is amended by ADR-040, which
is accepted.

### `src/backend/solver`

```text
src/backend/solver/
  pyproject.toml, uv.lock, Dockerfile, .dockerignore
  CLAUDE.md                        the rules for this folder
  src/dispatchflow_solver/
    __init__.py                    the package marker; one also in adapters/, inbound/ and http/
    py.typed                       marks the package as typed
    domain/                        Problem (validate, cost, explain_drop), ProblemFactory, Solution,
                                   TravelTimeModel, PenaltyScale, RuleId; problem_parts (the parts a
                                   problem holds) and problem_view (the protocol the validator, the
                                   objective and the drop reasons read a problem through)
    application/                   use cases, the Solver port, the registry (placeholder: only an
                                   empty __init__.py)
    solvers/
      ortools/                     the OR-Tools adapter (placeholder)
      alns/                        the DispatchFlow solver (placeholder)
    adapters/inbound/http/app.py   create_app(): the FastAPI app, with GET /health/liveness
  tests/                           mirrors the package path
    domain/                        the catalogue, properties, Solomon acceptance (data/solomon/)
    adapters/inbound/http/test_app.py
    test_dependency_boundary.py
```

The roles follow [ADR-004](../ADRs/ADR-004-pluggable-solver-port.md): every solver sits behind
one `Solver` port and is chosen by name. The port's call is `solve(problem, start, budget, seed)`
([ADR-044](../ADRs/ADR-044-solver-port-takes-start-budget-and-seed-and-problem-judges.md),
Accepted).
[ADR-040](../ADRs/ADR-040-core-is-a-spring-modulith-monolith-with-one-module-per-context.md)
(Accepted) moves the solvers under `adapters/outbound/solvers/`, where `nearest_neighbour` joins
them. Neither is in the tree yet.

```mermaid
flowchart TD
    http["adapters/inbound/http"] --> application["application"]
    solvers["solvers/ortools, solvers/alns"] --> application
    application --> domain["domain"]
```

Everything may use `domain`. `domain` uses nothing else in the package.
The solver registry is a type in `application`; `create_app()` in `adapters/inbound/http`, the
process's entry point, fills it with the solvers. `application` therefore never imports
`solvers`, and the two cannot form a cycle. The research harness builds its own registry.

### `src/research`

```text
src/research/
  pyproject.toml, uv.lock
  CLAUDE.md       the rules for this folder
  src/dispatchflow_research/
    __init__.py   the package marker
    benchmark/    the benchmark harness (placeholder)
    simulation/   the disruption simulation (placeholder)
    generator/    the order generator (placeholder)
  instances/      benchmark instances (placeholder)
  results/        benchmark results (placeholder)
  tests/test_environment.py
```

`test_environment.py` checks that the research environment holds both `dispatchflow_solver` and
`pyvrp`.

### `src/frontend/console`

A Vite project. `src/main.tsx` mounts `src/App.tsx`, which renders a heading. `src/test/setup.ts`
configures Vitest. The configuration files (`vite.config.ts`, `tsconfig*.json`,
`eslint.config.js`, `.prettierrc.json`) sit at the project root. `osv-scanner.toml` lists the
vulnerability the audit ignores there, with its reason and an expiry date
([ADR-023](../ADRs/ADR-023-accept-minimatch-and-glob-advisories.md)).

### `src/frontend/driver-app`

An Expo project. `index.ts` registers `App.tsx`, which renders a heading. `app.json`
and `assets/` hold the Expo configuration and icons. `osv-scanner.toml` lists the vulnerabilities
the audit ignores, each with a reason and an expiry date. The owner accepted the minimatch and glob
entries in [ADR-023](../ADRs/ADR-023-accept-minimatch-and-glob-advisories.md), and the zod and
deepmerge entries in
[ADR-095](../ADRs/ADR-095-accept-the-zod-and-deepmerge-advisories-until-2027-01-31.md).

The two apps share no code. `contracts/client-api/v1` declares no operation yet, so neither has an
API client.
[ADR-043](../ADRs/ADR-043-generated-code-from-both-contracts-and-problem-details-errors.md)
(Accepted, not in the tree yet) generates code from the contracts on both sides.

## Where the tests live

| Kind of test | Location | Run by |
|---|---|---|
| `core`, unit (`*Test`) | `src/backend/core/src/test/java` | Maven Surefire |
| `core`, integration (`*IT`) | `src/backend/core/src/test/java` | Failsafe, with a PostgreSQL container |
| `solver` | `src/backend/solver/tests`, mirroring the package path | pytest |
| `research` | `src/research/tests` | pytest |
| `console` | next to the code, in `src/` (`App.test.tsx`) | Vitest, in jsdom |
| `driver-app` | next to the code (`App.test.tsx`) | Jest, through `jest-expo` |
| End-to-end and load | `tests/e2e`, `tests/load` | No tool is configured yet |

Unit and integration tests stay inside their component, where each tool looks for them. Root
`tests/` holds only tests that span components. No end-to-end tool is configured
([`src/frontend/CLAUDE.md`](../../src/frontend/CLAUDE.md)), and [NFR-10] calls for k6 for load
tests. [Tech stack §17](tech_stack.md#17-testing) decides on Playwright for end-to-end tests and k6
for load tests.

The rule is that each side of a contract is tested against it ([NFR-162]). No test does that
yet.

`just <component>-check` runs a component's tests with its format and lint checks, and
`just check` runs every component's, plus the contract lint and the dependency audit.
See the [`justfile`](../../justfile).

## Dependency rules

These follow from
[ADR-001](../ADRs/ADR-001-domain-driven-design-with-hexagonal-architecture.md).

- **Domain code imports no framework, I/O or transport type.** Adapters depend on the domain,
  never the reverse. There are no cycles between modules or packages.
- **In `core`, a context reaches another only through that context's `api`, `spi` and `events`
  packages** ([domain layer design §2.3](domain_layer/domain_layer_design.md#23-rules-between-contexts)).
  ADR-001 names `api` only.
- **In `solver`, every solver implements the one `Solver` port** and is chosen by name. The
  registry has no default, and a name it does not hold is rejected.
- **PyVRP is never a dependency of `solver`.** Only `src/research` installs it.
- **The frontends talk only to `core`.** The API client is generated and never edited.

Three sets of rules are checked automatically today:
- the PyVRP rule;
- in `core`, the domain's purity rules, the shape rules and the package rules;
- in `solver`, the import-linter contracts in `pyproject.toml`: the domain imports no framework,
  solver or standard-library module that reads a clock, draws randomness or does I/O; the validator,
  the objective and the drop reasons never import `problem`; `application` imports no FastAPI,
  Pydantic or OR-Tools; the layers point one way; and the product never imports PyVRP. Ruff's
  `TID251` also bans `numpy.random`, `datetime64` and `timedelta64` in the domain
  ([ADR-113](../ADRs/ADR-113-route-stability-is-its-own-cost-the-rules-read-a-problem-through-a-protocol-and-untimed-outputs-are-documented.md)).

The purity rules are ArchUnit tests in `src/test/java/com/dispatchflow/architecture/`, over
`shared` and every module's `domain` and `events` packages
([domain layer design §3.9](domain_layer/domain_layer_design.md#39-purity)). The shape rules cover
who may construct a restricted type and what an error or an event may carry. The package rules are
two: no cycle between a module's domain packages, and `@NullMarked` on the kernel and on every
domain and events package
([ADR-118](../ADRs/ADR-118-a-domain-package-above-30-files-splits-by-aggregate.md)). The rest are
conventions that review enforces.
[Tech stack §2.2](tech_stack.md#22-enforcement) also plans Spring Modulith's `verify()` for the
Alpha
([ADR-040](../ADRs/ADR-040-core-is-a-spring-modulith-monolith-with-one-module-per-context.md),
Accepted).

[NFR-10]: ../requirements/requirements.md#nfr-10
[NFR-162]: ../requirements/requirements.md#nfr-162

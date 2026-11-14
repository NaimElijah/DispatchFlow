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

> [!NOTE]
> Most folders below are still empty. A folder listed as a placeholder holds only a `.gitkeep`
> file. What is real today is the skeleton: each component builds, passes its own checks and
> runs a first test.

## Top level

| Path | Holds |
|---|---|
| `src/backend/core/` | The REST API and system of record: Java 21, Spring Boot 4.1.2, Maven |
| `src/backend/solver/` | The optimization service: Python 3.14, FastAPI, uv |
| `src/frontend/console/` | The web app, for every role: React, TypeScript, Vite |
| `src/frontend/driver-app/` | The phone app, for every role: React Native, Expo SDK 58, TypeScript |
| `src/research/` | Benchmarks, simulation and the order generator: Python, uv. Never deployed |
| `contracts/` | `client-api/v1` and `optimization-api/v1` (OpenAPI), and the Redocly lint setup |
| `tests/` | Tests that span components: `e2e/` and `load/`. Both are placeholders |
| `infra/` | `osrm/` and `deploy/`. Both are placeholders |
| `logs/` | Local log output. Git keeps the folder and ignores everything in it |
| `docs/` | `requirements/` (with `ARD/`), `architecture/` (with `domain_layer/` and `tech_stack.md`), `ADRs/`, `roadmap.md` and `presentations/` |
| `.github/` | The CI workflow, Dependabot configuration, and issue and pull request templates |
| `justfile` | Every command. CI runs the same recipes |
| `.java-version`, `.python-version`, `.nvmrc` | Toolchain pins: Java 21, Python 3.14, Node 24 |
| `LICENSE` | Proprietary, all rights reserved ([ADR-017](../ADRs/ADR-017-proprietary-licence.md)) |

Each component has its own manifest and lockfile, and nothing is shared at build time.

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
  `src/backend/solver/tests/test_dependency_boundary.py` fails if it can be imported there
  ([ADR-004](../ADRs/ADR-004-pluggable-solver-port.md)).
- A change to `src/backend/solver/pyproject.toml` also needs `uv lock` in `src/research`.

## Inside each component

### `src/backend/core`

```text
src/backend/core/
  pom.xml, mvnw, mvnw.cmd, .mvn/   Maven and its wrapper
  Dockerfile, .dockerignore
  .env.example                     the database connection keys, with no values
  src/main/java/com/dispatchflow/
    DispatchFlowApplication.java   the Spring Boot entry point
    web/                           application-wide web concerns: the fallback for unexpected errors
      UnexpectedErrorHandler.java  answers an unhandled exception with a 500 problem body
      package-info.java
  src/main/resources/
    application.properties
    db/migration/                  Flyway migrations: none yet
  src/test/java/com/dispatchflow/
    web/UnexpectedErrorHandlerTest.java  the 500 problem body keeps the cause out
    IntegrationTest.java                 the one annotation every *IT uses: the whole app and PostgreSQL
    DispatchFlowApplicationIT.java       the health probe answers UP against PostgreSQL
    TestDispatchFlowApplication.java     what `just core-run` starts
    TestcontainersConfiguration.java     the PostgreSQL container
```

No bounded context exists yet. The decided design is the domain document, the tech stack and the
ADRs: see the
[domain layer design](domain_layer/domain_layer_design.md#1-subdomains-and-bounded-contexts). How a
context is laid out inside is in [tech stack §4](tech_stack.md#4-repository-layout) and
[domain layer design §13](domain_layer/domain_layer_design.md#13-inside-each-module-hexagon)
(the hexagon). New contexts are written to tech stack §4.
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
  src/dispatchflow_solver/
    domain/                        problem, solution, objective, validator (placeholder)
    application/                   use cases, the Solver port, the registry (placeholder)
    solvers/
      ortools/                     the OR-Tools adapter (placeholder)
      alns/                        the DispatchFlow solver (placeholder)
    adapters/inbound/http/app.py   create_app(): the FastAPI app, with GET /health/liveness
  tests/                           mirrors the package path
    adapters/inbound/http/test_app.py
    test_dependency_boundary.py
```

The roles follow [ADR-004](../ADRs/ADR-004-pluggable-solver-port.md): every solver sits behind
one `Solver` port and is chosen by name.

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
  src/dispatchflow_research/
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
the audit ignores, each with a reason and an expiry date. The zod and deepmerge entries still
await the owner's review ([ADR-016](../ADRs/ADR-016-dependency-audit-with-osv-scanner.md),
[ADR-023](../ADRs/ADR-023-accept-minimatch-and-glob-advisories.md)).

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
`just check` runs every component's. See the [`justfile`](../../justfile).

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

Only the PyVRP rule is checked by a test today. The others are conventions that review
enforces; no tool such as ArchUnit or import-linter is configured.
[Tech stack §2.2](tech_stack.md#22-enforcement) plans ArchUnit, Spring Modulith's `verify()` and
import-linter for the Alpha
([ADR-040](../ADRs/ADR-040-core-is-a-spring-modulith-monolith-with-one-module-per-context.md),
Accepted).

[NFR-10]: ../requirements/requirements.md#nfr-10
[NFR-162]: ../requirements/requirements.md#nfr-162

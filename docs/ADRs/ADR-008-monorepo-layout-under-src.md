# ADR-008: One repository, with the code under `src/backend`, `src/frontend` and `src/research`

- **Status:** Accepted
- **Date:** 2026-10-30

## Context

DispatchFlow has five pieces of code in three languages, written by a team of three:

- **The core backend**, in Java and Spring Boot: the REST API and the system of record
  ([ADR-002]).
- **The solver service**, in Python and FastAPI ([ADR-002], [ADR-004]).
- **The dispatcher console**, in React ([ADR-003]).
- **The driver app**, in React Native and Expo ([ADR-003], [NFR-15]).
- **The research code**: the benchmark harness, the disruption simulation and the order
  generator. It is never deployed ([FR-51]).

The pieces meet at two versioned OpenAPI contracts: the client API between the backend and
both frontends, and the optimization API between the backend and the solver ([ADR-006],
[NFR-21]). Both sides of each contract depend on it, and neither side owns it.

Four forces shape where things go:

- **A contract change touches several components.** It has to land together with every side it
  affects, and each side is tested against the contract.
- **Each toolchain has its own convention for where tests live.** Maven finds `src/test/java`,
  pytest finds the `tests` folder, Vitest and Jest find `*.test.tsx` files next to the code.
- **PyVRP must never reach the solver service.** It is a benchmark reference only, and the
  service never installs it ([ADR-004]). The benchmark harness needs both PyVRP and the solver.
- **Some tests span components.** An end-to-end test drives a browser against the whole system,
  and a load test drives the API. Neither belongs to one component.

## Decision

We keep everything in one repository and put the code under `src/`.

```text
src/backend/core          Java 21, Spring Boot: REST API and system of record
src/backend/solver        Python, FastAPI: the optimization service
src/frontend/console      React: the console for admins and dispatchers
src/frontend/driver-app   Expo: the driver app for iOS, Android and the browser
src/research              benchmarks, simulation, order generator; never deployed
contracts/                client-api/v1 and optimization-api/v1
tests/                    cross-component tests only: e2e/, load/
infra/                    osrm/, deploy/
docs/                     requirements, architecture, ADRs
```

- **The contracts sit at the root.** `contracts/client-api/v1` and `contracts/optimization-api/v1`
  belong to no component, because both sides depend on them and neither owns them. [ADR-006]
  records how they are versioned, linted and used.
- **Root `tests/` holds only tests that span components**: `e2e/` and `load/`. Unit and
  integration tests live inside each component, where Maven, pytest, Vitest and Jest expect
  them.
- **Each component is a self-contained project** with its own manifest, lockfile and toolchain
  configuration. Nothing is shared at build time. The root holds only what the whole
  repository shares: the toolchain pins ([ADR-010]), the `justfile` ([ADR-009]), the CI
  workflow ([ADR-015]), the editor and Git settings, the licence and the README.
- **The research code is a separate Python project**, `src/research`, with its own
  `pyproject.toml` and lockfile. It depends on the solver as an editable path dependency and
  adds PyVRP. The solver's environment cannot contain PyVRP, and
  `src/backend/solver/tests/test_dependency_boundary.py` fails if it ever can.
- **`backend/` and `frontend/` group the components by side.** The code, `contracts/`, `tests/`
  and `infra/` each have one place.

[code-structure.md](../architecture/code-structure.md) describes the layout inside each
component.

## Consequences

- **Easy:** a contract change, its provider and its consumers go in one pull request, and one CI
  run checks them together.
- **Easy:** every tool finds its tests where it expects them, with no configuration to redirect
  it.
- **Easy:** the PyVRP boundary is enforced by the project layout and checked by a test, instead
  of relying on a convention.
- **Easy:** a new contributor finds the code under one root, with one place for the contracts.
- **Hard:** the research project depends on the solver as an editable path dependency. Any change
  to `src/backend/solver/pyproject.toml` also needs `uv lock` in `src/research`; otherwise
  `just research-check` fails on the stale lockfile.
- **Hard:** a new component needs entries in three places: the root `justfile` ([ADR-009]), a CI
  job ([ADR-015]) and `.github/dependabot.yml` ([ADR-015]).
- **Hard:** there is no single build. The root `justfile` aggregates the components' own tools,
  and each component keeps its own lockfile.
- **Hard:** the two frontend apps share no code. A fix to hand-written code that both need is made
  twice, until they share a package (see the alternatives).
- **Cost:** the root has to stay thin. Anything that is only one component's concern belongs in
  that component, even when it would be convenient at the root.

## Alternatives considered

- **One repository per component.** Rejected: a contract change would span repositories and need
  coordinated pull requests, and the contracts would need a home of their own ([ADR-006]). Three
  people do not need the separation.
- **`services/` and `apps/` at the root instead of `src/backend` and `src/frontend`.**
  Rejected: the owner chose `backend/` and `frontend/` under `src/`.
- **A root `tests/unit`, with every component's tests moved into it.** Rejected: it fights the
  convention of every tool in use. Maven, pytest, Vitest and Jest would each need configuration
  to find tests outside their project.
- **The research code inside the solver's package.** Rejected: PyVRP would then be installable
  in the solver, against [ADR-004]. A separate `pyproject.toml` with its own lockfile keeps it
  out, and the boundary test fails the build if it ever gets in.
- **A separate manager app.** Rejected: no requirement asks for one. The console serves both
  admins and dispatchers, and the server decides what each role may do ([ADR-003]).
- **A shared JavaScript package for the two frontend apps.** Deferred, not rejected: today the
  two apps share only the contract, and the API client in each is generated from it. A shared
  package pays off once hand-written code would otherwise be copied between them.

## Revisit when

- A component needs its own release cadence, or another team takes it over. Splitting that
  component into its own repository, with the contract published as a versioned artefact,
  becomes worth its cost then.
- Hand-written, non-generated code would be copied between the console and the driver app.
  Add a shared package.

[ADR-002]: ADR-002-core-backend-and-separate-optimization-service.md
[ADR-003]: ADR-003-dispatcher-console-and-driver-app.md
[ADR-004]: ADR-004-pluggable-solver-port.md
[ADR-006]: ADR-006-versioned-openapi-contracts.md
[ADR-009]: ADR-009-just-as-the-single-entry-point.md
[ADR-010]: ADR-010-toolchain-pins-in-version-files.md
[ADR-015]: ADR-015-ci-one-workflow-one-job-per-component.md
[FR-51]: ../requirements/requirements.md#research-and-evaluation
[NFR-15]: ../requirements/requirements.md#nfr-15
[NFR-21]: ../requirements/requirements.md#nfr-21

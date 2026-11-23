# ADR-037: The code moves to a flat layout under `src/`, with deployment in `deploy/` and Terraform in `infra/`

- **Status:** Superseded by ADR-123
- **Date:** 2026-11-13

## Context

[ADR-008] puts the code under `src/backend` and `src/frontend`, and the tree follows it today:

```text
src/backend/core, src/backend/solver
src/frontend/console, src/frontend/driver-app
src/research
contracts/client-api/v1/openapi.yaml, contracts/optimization-api/v1/openapi.yaml
tests/e2e, tests/load            placeholders, a .gitkeep each
infra/osrm, infra/deploy         placeholders, a .gitkeep each
logs/                            contents git-ignored
```

The solver's Python package is `dispatchflow_solver`. The root `justfile`, the CI workflow and the
Dependabot file name these paths.

The tech stack ([TS §4][TS-4]) and the implementation plan ([PL §5][PL-5]) draw another tree. TS §4
says to adapt it to the existing tree, because `src/research` already exists ([NFR-50]). The
differences from today:

- Components sit directly under `src/`, and the solver is called the optimizer.
- Shared TypeScript code gets a folder, `src/packages` ([ADR-039]).
- Deployment files, Terraform and large data files each get a place.

Four forces from [ADR-008] still hold:

- **A contract change touches several components.** It must land with every side it affects
  ([NFR-21]).
- **Each toolchain finds its tests where it expects them.** Unit and integration tests stay
  inside each component.
- **PyVRP must never reach the optimizer service** ([ADR-004]). The research project stays
  separate.
- **Some tests span components.** The Playwright flows drive the whole Compose stack
  ([TS §17][TS-17]).

New pieces have no home in today's tree: the Compose files, the Caddyfile, backup and
observability configuration, Terraform, and the data files that `just data` builds (the OSM
extract, the OSRM files, the PMTiles file and the benchmark instances).

The sources give no reason for dropping the `backend/` and `frontend/` grouping, or for renaming
`solver`, `console` and `driver-app`. They show the flat tree without comparing it to the old one,
and TS §4 itself says to adapt it to the existing tree.

## Decision

We keep one repository and flatten the tree, as [TS §4][TS-4] and [PL §5][PL-5] draw it.

```text
contracts/      core-api.v1.yaml, optimizer-api.v1.yaml
src/core/       Java, Spring Boot, Maven: the REST API and system of record
src/optimizer/  Python, FastAPI, uv: the optimization service, package dispatchflow_opt
src/research/   benchmarks, simulation, order generator; never deployed
src/web/        React and Vite: the web app
src/mobile/     Expo: the phone app, from M10
src/packages/   shared TypeScript packages
e2e/            Playwright: the demo script and the use-case flows
deploy/         compose.yaml, compose.dev.yaml, Caddyfile, osrm/, tiles/, backup/, observability/
infra/          Terraform, from M13
data/           git-ignored: OSM extract, OSRM files, PMTiles, benchmark instances
docs/           requirements, architecture, ADRs
```

- **Each component is one folder under `src/`.**
  - `optimizer` replaces `solver`. The folder, the Python package (`dispatchflow_opt`) and the
    contract (`optimizer-api`) now share the name.
  - `web` replaces `console`, and `mobile` replaces `driver-app`. The phone app arrives with M10
    ([PL §3][PL-3]).
- **The contracts stay at the root, one file per major version:** `contracts/core-api.v1.yaml`
  between the web and phone apps and the core, and `contracts/optimizer-api.v1.yaml` between the
  core and the optimizer. A breaking change becomes a new file, such as `core-api.v2.yaml`, beside
  the old one. [ADR-006]'s `v1/` folders give way to this; the rest of that ADR stands.
- **`e2e/` at the root replaces `tests/e2e`.** Unit and integration tests stay inside each
  component.
- **`deploy/` holds everything that runs the system:** both Compose files, the Caddyfile,
  `osrm/`, `tiles/`, `backup/` and `observability/`. It replaces the `infra/osrm` and
  `infra/deploy` placeholders.
- **`infra/` holds Terraform only**, from M13 ([TS §15][TS-15]).
- **`data/` is git-ignored.** `just data` builds it ([PL §5][PL-5]). `.gitignore` has no entry
  for it today.
- **The research project stays a separate Python project.** It depends on `src/optimizer` as an
  editable path dependency and adds PyVRP, and the optimizer's environment never contains PyVRP
  ([ADR-004]).
- **Core, optimizer and research keep their own manifests and lockfiles.** The TypeScript projects
  share one pnpm workspace ([ADR-039]). This narrows [ADR-008]'s "nothing is shared at build time".
- **The `justfile` stays the only entry point,** and its recipes follow the new folders.
  `just check` stays the gate that CI and the team skills run ([ADR-009], [ADR-025]).

### What changes in the accepted ADRs

[ADR-008] is replaced whole. Four others change in one part each, and the rest of each stands:

| ADR | What changes | What stands |
|---|---|---|
| [ADR-006] | The `v1/` contract folders become one file per major version, `contracts/*.v1.yaml` | Contract-first, OpenAPI 3.1, the major version in the URL, a new file for a breaking change |
| [ADR-009] | The recipe names (`core-*`, `solver-*`, `console-*`, `driver-app-*`) follow the new folders | The `justfile` as the only entry point, and CI running the same recipes |
| [ADR-018] | The paths of the side-level files, `src/backend/CLAUDE.md` and `src/frontend/CLAUDE.md`, whose home is open (below) | Narrower rules live next to the code, and each file repeats no rule of its parent |
| [ADR-035] | Line 63, "The folders keep their names" | Both apps serve every role, and the rest of that ADR |

### Open before acceptance

- **Whether to flatten at all.** The sources give no reason to drop `backend/` and `frontend/`, or to
  rename `solver` to `optimizer`, `console` to `web` and `driver-app` to `mobile`. The owner gives
  one, or keeps the grouping and adapts the tree to it, as [TS §4][TS-4] allows. Until then the
  decision above is the sources' tree, not a decided one.
- **A home for the side-level `CLAUDE.md` rules,** and the other points Consequences leaves open:
  the new recipe names, the folders with no place in the tree (`tests/load/`, `logs/`,
  `src/research/instances/`) and when the driver-app scaffold moves.

## Consequences

- **Easy:** a contract change, its provider and its consumers still go in one pull request.
- **Easy:** every component is one folder under `src/`, and the deployment files, Terraform and
  data files each have one place.
- **Hard:** the move touches every file that names an old path. Each of these does today:
  - `src/backend/CLAUDE.md` and `src/frontend/CLAUDE.md` ([ADR-018]). A flat tree has no
    `backend/` or `frontend/` folder to hold them, so their rules need a home. This ADR leaves
    that open.
  - The five component `CLAUDE.md` files move with their folders. `.claude/CLAUDE.md`,
    `contracts/CLAUDE.md` and `README.md` name old paths too, and so do four documents under
    `docs/architecture/`: `architecture.md`, `bounded-contexts.md`, `data-model.md` and
    `optimization-engine.md`, as well as `code-structure.md`. These describe the repository as it
    is, so they change in the pull request that moves the code.
  - `.github/workflows/ci.yml` has seven jobs: `core`, `solver`, `research`, `console`,
    `driver-app`, `contracts` and `audit` ([ADR-015]). Five of them carry a dependency-cache path
    under `src/backend`, `src/research` or `src/frontend`. Renaming a job renames its check. Not
    checked: whether branch protection lists them.
  - `.github/dependabot.yml` has seven component directories, all under the old tree
    ([ADR-021]). [ADR-046] replaces the file with Renovate's configuration.
  - The two `osv-scanner.toml` files, in the console and the driver app ([ADR-016], [ADR-023]).
    Their `[[IgnoredVulns]]` entries expire on 2027-01-31 wherever the files live.
  - The `justfile`. Each component recipe sets a `working-directory`, and the names are
    `core-*`, `solver-*`, `research-*`, `console-*` and `driver-app-*`. PL §5 lists the targets
    used daily: `dev`, `test`, `test-core`, `test-opt`, `test-web`, `e2e`, `demo`, `gen`,
    `db-reset`, `seed`, `study`, `lint` and `fmt`. `check` is not among them, and the list is not
    exhaustive. The new recipe names, and whether `fmt` replaces `format`, are open.
  - `.claude/agents/df-code-reviewer.md` and `.claude/agents/df-tester.md` list the component
    paths. The team skills name a component path once, `src/research`, in
    `.claude/skills/sync-with-dev/update-from-dev.md` (checked with a search of `.claude/skills`).
    The same file names an npm lockfile command ([ADR-039]).
  - `contracts/redocly.yaml` has the aliases `client-api@v1` and `optimization-api@v1`, and
    `contracts/.redocly.lint-ignore.yaml` keys its exceptions by the old file paths.
  - The Python rename: `name = "dispatchflow-solver"` in `pyproject.toml`, the research project's
    path dependency and imports, the `solver-run` recipe, the `CMD` of the solver `Dockerfile`, and
    `tests/test_dependency_boundary.py`.
- **Hard:** three things have no place in either source's tree. `tests/load/` is where k6 scripts
  would go ([TS §17.4][TS-174]). `logs/` is the local log folder. `src/research/instances/` is a
  placeholder, while PL §5 puts the benchmark instances in `data/`. This ADR leaves all three open.
- **Hard:** today's `src/frontend/driver-app` is a scaffold, and `src/mobile` arrives with M10.
  The sources do not say whether the scaffold moves now or waits.
- **Hard:** a new component still needs entries in the `justfile`, CI and the dependency-update
  configuration, as in [ADR-008]. There is still no single build.
- **Cost:** the move conflicts with every open branch that touches a moved path. Those branches
  merge `dev` after it lands.
- **Risk:** until the move merges, the documents describe a tree that does not exist yet. The
  `CLAUDE.md` files and the code-structure document must keep saying which layout is real.

## Alternatives considered

- **Keep [ADR-008]'s layout, in whole or in part:** the `backend/` and `frontend/` folders, the
  `v1/` contract folders and root `tests/`, with the new folders added beside them. The sources
  draw the flat tree and compare it with none of these, so no source gives a reason against them.
  The owner can add one.
- **One repository per component.** Still rejected, for [TS §4][TS-4]'s reason: no atomic
  contract changes, and four copies of CI. [ADR-008] gave the same reason.
- **A root `tests/unit`, the research code inside the optimizer's package, and a separate
  manager app.** Still rejected, for [ADR-008]'s reasons. Nothing in the sources reopens them.

## Revisit when

- A component needs its own repository or release cadence. Split it, and publish its contract as
  a versioned artefact ([ADR-008]).
- No home is found for the rules in `src/backend/CLAUDE.md` and `src/frontend/CLAUDE.md` that
  keeps them loaded only where they apply ([ADR-018]). Reconsider a grouping folder.

[ADR-004]: ADR-004-pluggable-solver-port.md
[ADR-006]: ADR-006-versioned-openapi-contracts.md
[ADR-008]: ADR-008-monorepo-layout-under-src.md
[ADR-035]: ADR-035-both-apps-serve-every-role.md
[ADR-009]: ADR-009-just-as-the-single-entry-point.md
[ADR-015]: ADR-015-ci-one-workflow-one-job-per-component.md
[ADR-016]: ADR-016-dependency-audit-with-osv-scanner.md
[ADR-018]: ADR-018-ai-assistant-context-in-the-repository.md
[ADR-021]: ADR-021-dependabot-targets-dev-and-skips-pinned-majors.md
[ADR-023]: ADR-023-accept-minimatch-and-glob-advisories.md
[ADR-025]: ADR-025-team-skills-in-the-repository.md
[ADR-039]: ADR-039-one-pnpm-workspace-for-both-apps-and-shared-packages.md
[NFR-21]: ../requirements/requirements.md#nfr-21
[NFR-50]: ../requirements/requirements.md#nfr-50
[PL-3]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#3-build-sequence
[PL-5]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#5-repository-and-local-environment
[TS-4]: ../architecture/tech_stack.md#4-repository-layout
[TS-15]: ../architecture/tech_stack.md#15-infrastructure-and-operations
[TS-17]: ../architecture/tech_stack.md#17-testing
[TS-174]: ../architecture/tech_stack.md#174-performance-measurement
[ADR-046]: ADR-046-renovate-replaces-dependabot-and-targets-dev.md

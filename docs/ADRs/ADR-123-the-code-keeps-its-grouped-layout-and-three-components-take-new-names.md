# ADR-123: The code keeps its grouped layout, and three components take new names

- **Status:** Accepted
- **Date:** 2026-11-22

## Context

[ADR-037] moves the tree to a flat layout under `src/`: `src/core`, `src/optimizer`, `src/web`,
`src/mobile` and `src/packages`, with `e2e/` and `deploy/` at the root. The move is not made yet. The
tree is still [ADR-008]'s:

```text
src/backend/core, src/backend/solver
src/frontend/console, src/frontend/driver-app
src/research
contracts/client-api/v1/openapi.yaml, contracts/optimization-api/v1/openapi.yaml
tests/e2e, tests/load            placeholders, a .gitkeep each
infra/osrm, infra/deploy         placeholders, a .gitkeep each
logs/                            contents git-ignored
```

ADR-037 gives no reason for flattening. It says "the sources give no reason for dropping the
`backend/` and `frontend/` grouping", and its own *Open before acceptance* section lists "whether to
flatten at all". [TS §4][TS-4] itself says to adapt its tree to the existing one.

What the flat tree costs, counted on 2026-11-22:

- **41 tracked files** name `src/backend/` or `src/frontend/` (`git grep`), 21 of them ADRs.
- The `justfile`, the CI job names in `.github/workflows/ci.yml`, the directories in
  `.github/dependabot.yml` and every open branch that touches a moved path change too, as ADR-037
  records.
- `src/backend/CLAUDE.md` and `src/frontend/CLAUDE.md` would have no folder to live in
  ([ADR-018]). ADR-037 left their home open, and its own revisit trigger is that no home is found.

The flat tree's gain is shorter paths. The places ADR-037 gave to files that later milestones add
(the Compose files, Terraform, the data files) do not depend on flattening; the Decision gives each a
home in the grouped tree.

Three names are also in question:

- **`solver`.** [DL §17][DL-17] calls the service the optimizer: a *solve job* is "one queued or
  running call to the optimizer". A *solver* is an algorithm that proposes a *solution*. The folder
  `src/backend/solver`, the Python package `dispatchflow_solver` and the distribution
  `dispatchflow-solver` name the service with the algorithm's word.
- **`console` and `driver-app`.** Both apps serve every role ([ADR-035]), and the documents already
  call them the web app and the phone app. ADR-035 kept the folder names ("The folders keep their
  names", line 63). The Expo app's `app.json` still says "DispatchFlow Driver", with the slug
  `dispatchflow-driver`. No EAS project ID is in `app.json`, so no store identity depends on either.

On 2026-11-22 the owner, Naim Elijah, decided to drop the flat tree and rename the three components
inside the grouped one.

## Decision

We keep ADR-008's grouped tree and rename three components in it.

```text
src/backend/core         Java, Spring Boot: REST API and system of record
src/backend/optimizer    Python, FastAPI: the optimization service
src/frontend/web         React, TypeScript: the web app, for every role
src/frontend/mobile      Expo: the phone app, for every role
src/research             benchmarks, simulation, order generator; never deployed
contracts/               client-api/v1 and optimization-api/v1
tests/                   cross-component tests only: e2e/, load/
infra/                   deploy/, osrm/, terraform/
data/                    git-ignored: built data files
docs/                    requirements, architecture, ADRs
logs/                    local log output; git ignores its contents
```

- **ADR-037's flat tree is dropped, whole.** Its folders (`src/core`, `src/optimizer`, `src/web`,
  `src/mobile`, `src/packages`, `e2e/`, `deploy/`) are never created.
- **Three components take new names:**

  | Was | Becomes | Package names |
  |---|---|---|
  | `src/backend/solver` | `src/backend/optimizer` | Python package `dispatchflow_solver` becomes `dispatchflow_optimizer`; distribution `dispatchflow-solver` becomes `dispatchflow-optimizer` |
  | `src/frontend/console` | `src/frontend/web` | npm `dispatchflow-console` becomes `dispatchflow-web` |
  | `src/frontend/driver-app` | `src/frontend/mobile` | npm `dispatchflow-driver-app` becomes `dispatchflow-mobile`; Expo name "DispatchFlow Driver" becomes "DispatchFlow", slug `dispatchflow-driver` becomes `dispatchflow-mobile` |

  `core` and `research` keep their names. No EAS project is linked, so the slug change breaks
  nothing.
- **The `justfile` recipes, the CI jobs and the GitHub `area:` labels follow:** `optimizer-*`,
  `web-*` and `mobile-*`, and `area: optimizer`, `area: web` and `area: mobile`.
- **In prose the components are "the optimizer", "the web app" and "the phone app".** "Solver" keeps
  its [DL §17][DL-17] meaning: an algorithm behind the `Solver` port ([ADR-004]), such as OR-Tools,
  ALNS or nearest_neighbour. The contract `optimization-api` keeps its name.
- **Homes for the files that later milestones add:**

  | What | Where |
  |---|---|
  | Compose files, Caddyfile, backup and observability configuration | `infra/deploy/` |
  | OSRM configuration | `infra/osrm/` |
  | Terraform, from M13 | `infra/terraform/` |
  | OSM extract, OSRM files, PMTiles, benchmark instances | `data/` at the root, git-ignored |
  | Playwright end-to-end tests | `tests/e2e/` |
  | Shared TypeScript packages ([ADR-039]) | `src/frontend/packages/` |
  | Contracts | `contracts/client-api/v1/openapi.yaml` and `contracts/optimization-api/v1/openapi.yaml` ([ADR-006]); a breaking change gets a `v2/` folder |

- **ADR-008's other decisions carry over, with the new names:**
  - the contracts sit at the root, because both sides depend on them and neither owns them;
  - root `tests/` holds only tests that span components, and unit and integration tests stay inside
    each component;
  - each component is its own project with its own manifest and lockfile, except that the
    TypeScript projects share one pnpm workspace ([ADR-039]);
  - `src/research` is a separate Python project that depends on `src/backend/optimizer` as an
    editable path dependency and adds PyVRP, and the optimizer's environment never contains PyVRP
    ([ADR-004]);
  - `backend/` and `frontend/` group the components by side, so `src/backend/CLAUDE.md` and
    `src/frontend/CLAUDE.md` keep their folders ([ADR-018]).
- **The root `justfile` stays the only entry point,** and `just check` stays the gate that CI and the
  team skills run ([ADR-009], [ADR-025]).
- **One exception to the append-only rule, made once.** The change that renames the three folders
  also replaces the old component names inside the text of accepted ADRs, including the titles of
  [ADR-003] and [ADR-028], so the record reads with today's names.
  - It replaces a name only where the text names the component, never where "solver" means an
    algorithm. That includes paths an ADR decided: ADR-003, ADR-008 and ADR-035 now show
    `src/backend/optimizer`, `src/frontend/web` and `src/frontend/mobile`, while ADR-035's "The
    folders keep their names" kept the names of its day, `console` and `driver-app`. The Context
    above and the git history keep the old names, so the reversal can still be traced.
  - Quoted tool output stays verbatim, old paths included, as in ADR-023's scanner log.
  - It changes no decision and no ADR file name, so every link still resolves.
  - It leaves ADR-037 and this ADR as written, because each names the old and the new names side by
    side.
  - The rule stands for everything else.
- **Not affected:** [ADR-038] stands, with mise and Java 25. M0 keeps every item except the
  restructure to ADR-037's layout, and the details ADR-038 and ADR-039 left to the restructure's
  pull request are settled in M0.

### What changes in the accepted ADRs

[ADR-037] is superseded whole. Eight others change in one part each, and the rest of each stands.
For two of them, ADR-006 and ADR-018, the change is that ADR-037's amendment no longer holds:

| ADR | What changes | What stands |
|---|---|---|
| [ADR-037] | Superseded whole | Nothing; this ADR replaces it |
| [ADR-006] | ADR-037's amendment no longer holds: its single contract files are dropped, so ADR-006's `v1/` folders stand again | Contract-first, OpenAPI 3.1, the major version in the URL, a `v2/` folder for a breaking change |
| [ADR-009] | The recipe names follow the new folders: `optimizer-*`, `web-*`, `mobile-*` | The `justfile` as the only entry point, and CI running the same recipes |
| [ADR-018] | ADR-037's amendment no longer holds: the side-level files keep their folders, which ADR-037 left open | Narrower rules live next to the code, and each file repeats no rule of its parent |
| [ADR-035] | Line 63, "The folders keep their names", no longer holds; the phone app's name on the phone is "DispatchFlow", while its orientation stays open | Both apps serve every role, and the rest of that ADR |
| [ADR-039] | The shared packages live in `src/frontend/packages/`, not `src/packages`, and `@dispatchflow/api-client` is generated from `client-api`, not `core-api` | One pnpm workspace for both apps and the packages, and the rest of that ADR |
| [ADR-042] | `compose.dev.yaml` lives in `infra/deploy/`, not `deploy/`; the optimizer service builds from `src/backend/optimizer` | Local runs from Compose, Testcontainers for tests, the services and ports, and `data/` |
| [ADR-043] | The contract file names are [ADR-006]'s, not ADR-037's | The generators, the version scheme, the error shape and the checks |
| [ADR-047] | The production `compose.yaml` whose images are pinned by digest lives in `infra/deploy/`, not `deploy/` | Digest pins from January, the core on the Temurin 25 JRE, and the rest of that ADR |

[ADR-008] stays `Superseded by ADR-037`. This ADR restates its tree with the new names, so the tree
above is the one in force, and a reader need not follow the chain from ADR-008 through ADR-037.

## Consequences

- **Easy:** three folders move and nothing else. `core`, `research`, `contracts/`, `tests/` and
  `infra/` stay where they are, so their paths in the documents, the CI cache keys and the component
  `CLAUDE.md` files stay true.
- **Easy:** the side-level `src/backend/CLAUDE.md` and `src/frontend/CLAUDE.md` keep loading only where
  they apply ([ADR-018]).
- **Easy:** a name means one thing. The optimizer is the service, a solver is an algorithm, and the
  folders match the words the documents and [DL §17][DL-17] already use.
- **Easy:** every file ADR-037 would have placed has a home in the tree that exists.
- **Hard:** the paths stay longer than the flat tree's by one folder.
- **Hard:** a name appears in many places that must change together: the folders, the Python package
  and its imports in `src/research`, the `pyproject.toml` and `package.json` names and their
  lockfiles, the Expo name and slug, the `justfile`, CI job names and cache paths, the dependency-update
  configuration, `.claude/agents/`, and the documents that describe the layout. A miss fails a build
  or leaves a stale path.
- **Hard:** renaming a CI job renames its check. On 2026-11-22 the ruleset on `dev` and `main`
  requires a pull request and a squash merge but names no required status check, so no check list
  needs editing. Required checks are still to come (#2); whoever adds them uses the new names.
- **Cost:** a branch that touches `src/backend/solver`, `src/frontend/console` or
  `src/frontend/driver-app` conflicts with the rename and merges `dev` once after it lands.
- **Cost:** old commit messages, closed issues and pull request titles keep the old names. A search
  for the history of a component needs both.
- **Cost:** [TS §4][TS-4] and the [roadmap's][RM] M0 row drew the flat tree, so they are corrected in
  the same change. The ADR texts change once, under the exception above.
- **Risk:** between this ADR and the rename's merge, the code still has the old names. The
  `CLAUDE.md` files and `docs/architecture/code-structure.md` must keep saying which names are real.
- **Risk:** the text sweep under the exception reads "solver" in a place where it means the
  algorithm and renames it wrongly. The change's review checks each replaced "solver".

## Alternatives considered

- **The flat tree ([ADR-037]).** Rejected for the reasons in Context: it gives no reason for dropping
  the grouping, touches 41 tracked files and every open branch, leaves the side-level `CLAUDE.md`
  files with no folder, and gains only shorter paths.
- **Renaming the components without an ADR.** Rejected: it reverses ADR-035's line 63 and part of
  ADR-037, and the record would then say one thing while the tree said another.
- **Keeping the old names.** Rejected: `solver` names the service with the algorithm's word, and
  `console` and `driver-app` describe apps that now serve every role.
- **The package name `dispatchflow_opt`,** as ADR-037 has it. Rejected: it is an abbreviation that no
  document uses. `dispatchflow_optimizer` matches the folder and the contract.

## Revisit when

- A component appears that belongs to neither `backend/` nor `frontend/`. Reconsider the grouping.
- The side-level `CLAUDE.md` files hold nothing that both of their components share ([ADR-018]).
  Reconsider the grouping, and the flat tree with it.
- A component needs its own repository or release cadence. Split it, and publish its contract as a
  versioned artefact ([ADR-008]).

[ADR-003]: ADR-003-dispatcher-console-and-driver-app.md
[ADR-004]: ADR-004-pluggable-solver-port.md
[ADR-006]: ADR-006-versioned-openapi-contracts.md
[ADR-008]: ADR-008-monorepo-layout-under-src.md
[ADR-009]: ADR-009-just-as-the-single-entry-point.md
[ADR-018]: ADR-018-ai-assistant-context-in-the-repository.md
[ADR-025]: ADR-025-team-skills-in-the-repository.md
[ADR-028]: ADR-028-driver-app-through-test-tracks-with-a-device-checklist.md
[ADR-035]: ADR-035-both-apps-serve-every-role.md
[ADR-037]: ADR-037-flat-layout-under-src-with-deploy-and-infra.md
[ADR-038]: ADR-038-mise-pins-every-toolchain-and-core-moves-to-java-25.md
[ADR-039]: ADR-039-one-pnpm-workspace-for-both-apps-and-shared-packages.md
[ADR-042]: ADR-042-compose-dev-for-local-runs-testcontainers-for-tests.md
[ADR-043]: ADR-043-generated-code-from-both-contracts-and-problem-details-errors.md
[ADR-047]: ADR-047-images-pinned-by-digest-and-core-on-temurin-25-jre.md
[DL-17]: ../architecture/domain_layer/domain_layer_design.md#17-ubiquitous-language
[RM]: ../roadmap.md
[TS-4]: ../architecture/tech_stack.md#4-repository-layout

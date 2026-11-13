# Solver

The optimization service. It serves `contracts/optimization-api/v1`, `core` is its only client,
and it hosts every solver behind one port ([ADR-004]). Python 3.14, FastAPI, Uvicorn, uv.

## Layout

| Package under `src/dispatchflow_solver/` | Holds |
|---|---|
| `domain/` | The problem, the solution, the shared objective and the feasibility validator |
| `application/` | The solve use cases, the `Solver` port and the solver registry |
| `solvers/ortools/`, `solvers/alns/` | One adapter per solver: OR-Tools, and our Clarke-Wright + ALNS |
| `adapters/inbound/http/` | `create_app()`: the FastAPI app and its request and response models |

Everything except `adapters/inbound/http` is a placeholder today. `tests/` mirrors the package
path.

[ADR-037] (Proposed) renames the service to `src/optimizer` with the package `dispatchflow_opt`.
[TS §4] draws the solvers under `adapters/outbound/solvers/`, where `nearest_neighbour` joins them;
no ADR has decided that ([ADR-040] leaves it open).

## Rules

- `domain` imports no `fastapi`, `pydantic`, `ortools`, `pyvrp` or `numba`. NumPy is allowed
  ([TS §2.1]). `application` imports nothing from FastAPI, Pydantic or OR-Tools. OR-Tools types
  live in `solvers/ortools` ([ADR-001]).
- Pydantic models are generated from the contract by datamodel-code-generator ([ADR-043],
  Proposed), and only the HTTP adapter uses them.
- `Problem` owns `validate`, `cost` and `explain_drop` ([TS §7.1]). `validate` is the one
  definition of feasibility, and `cost` is the one objective.
- `ProblemFactory` alone applies the re-optimization conventions ([PL §9.2]).
- `TravelTimeModel` is the only place the bucket factors and the buffer are applied.
- Every solver implements the one `Solver` port, `solve(problem, start, budget, seed)`: `start` is
  an optional warm start, and `budget` is a time limit, an iteration budget or a solution limit
  ([ADR-044], Proposed). A solver is chosen by name for each solve. The registry has no default,
  and a name it does not hold is rejected.
- Every solution passes `Problem.validate` and is scored by `Problem.cost` before it leaves the
  service, whichever solver produced it. A solver's own objective value is never reported.
- A solve takes a seed, and nothing draws unseeded randomness, so a run can be replayed
  ([ADR-005]).
- PyVRP never enters the product (FR-286): only `src/research` installs it.
  `tests/test_dependency_boundary.py` fails if it is installed or locked here. [ADR-040] (Proposed)
  adds import-linter, which checks imports only, so that test keeps its environment and lockfile
  checks.
- Every public module, class and function has a Google-style docstring; ruff enforces it.
  Tests are exempt.

## Dependencies

- `uv add <package>`, or `uv add --dev <package>` for tooling.
- `src/research` depends on this project as an editable path dependency. After any change to
  `pyproject.toml`, run `uv lock` in `src/research` too, or `just research-check` fails on the
  stale lockfile.
- The HTTP tests use `httpx2`, not `httpx`, for Starlette's test client.

## Tests

- pytest.
- HTTP tests go through `TestClient(create_app())`, never by calling handlers directly.
- A test gives a solver an iteration or solution budget, never a wall-clock limit (NFR-82,
  NFR-175).
- The violation catalogue has one YAML case per rule, each naming the rule it must trigger
  ([PL §24]).
- When the validator lands, Hypothesis property tests assert that every generated instance's
  solution passes it. They run from a recorded seed (NFR-82).
- One test checks that the same input, seed and budget give the same solution in two processes
  (NFR-174).

## Commands

Run `just` recipes from the repository root, and `uv` from this folder.

| Command | What it does |
|---|---|
| `just solver-check` | ruff format check, ruff lint, mypy strict, pytest |
| `just solver-format` | ruff format and `ruff check --fix` |
| `just solver-run` | Starts the service on port 8000, reloading on change |
| `uv run --locked pytest tests/path/test_x.py::test_name` | One test |

[ADR-001]: ../../../docs/ADRs/ADR-001-domain-driven-design-with-hexagonal-architecture.md
[ADR-004]: ../../../docs/ADRs/ADR-004-pluggable-solver-port.md
[ADR-005]: ../../../docs/ADRs/ADR-005-or-tools-first-research-solver-for-re-optimization.md
[ADR-037]: ../../../docs/ADRs/ADR-037-flat-layout-under-src-with-deploy-and-infra.md
[ADR-040]: ../../../docs/ADRs/ADR-040-core-is-a-spring-modulith-monolith-with-one-module-per-context.md
[ADR-043]: ../../../docs/ADRs/ADR-043-generated-code-from-both-contracts-and-problem-details-errors.md
[ADR-044]: ../../../docs/ADRs/ADR-044-solver-port-takes-start-budget-and-seed-and-problem-judges.md
[PL §9.2]: ../../../docs/init_impl_ref/technical_implementation_plan.md#92-what-problemfactoryreoptimization-does
[PL §24]: ../../../docs/init_impl_ref/technical_implementation_plan.md#24-testing
[TS §2.1]: ../../../docs/architecture/tech_stack.md#21-the-dependency-rule
[TS §4]: ../../../docs/architecture/tech_stack.md#4-repository-layout
[TS §7.1]: ../../../docs/architecture/tech_stack.md#71-the-hexagon

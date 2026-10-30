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

## Rules

- `domain` and `application` import nothing from FastAPI, Pydantic or OR-Tools. Pydantic models
  live in the HTTP adapter; OR-Tools types live in `solvers/ortools` ([ADR-001]).
- Every solver implements the one `Solver` port and is chosen by name for each solve. The
  registry has no default, and a name it does not hold is rejected.
- Every solution passes the shared feasibility validator and is scored by the shared objective
  before it leaves the service, whichever solver produced it. A solver's own objective value is
  never reported.
- A solve takes a seed, and nothing draws unseeded randomness, so a run can be replayed
  ([ADR-005]).
- PyVRP is never a dependency here; only `src/research` installs it.
  `tests/test_dependency_boundary.py` fails if it is.
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
- When the validator lands, Hypothesis property tests assert that every generated instance's
  solution passes it.

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

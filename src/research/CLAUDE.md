# Research

The benchmark harness, the disruption simulation and the order generator. Never deployed. It is
the only project that installs PyVRP. Python 3.14, uv.

## Layout

| Path | Holds |
|---|---|
| `src/dispatchflow_research/benchmark/` | The benchmark harness (placeholder) |
| `src/dispatchflow_research/simulation/` | The disruption simulation (placeholder) |
| `src/dispatchflow_research/generator/` | The order generator (placeholder) |
| `instances/` | Benchmark instances (placeholder) |
| `results/` | Benchmark results (placeholder) |
| `tests/` | pytest; `test_environment.py` checks that both the solver and PyVRP import |

## How it uses the solver

- It depends on `src/backend/solver` as an editable path dependency, so it always runs the
  solver's current code.
- It uses the solver's `domain`, `application` and `solvers` packages, the last to build its own
  registry. It never imports the solver's `adapters`.
- The harness builds its own solver registry, which adds a PyVRP adapter. PyVRP and its adapter
  stay in this project ([ADR-004]).
- After any change to the solver's `pyproject.toml`, run `uv lock` here.

## Benchmark rules ([ADR-005])

- Every solver gets the same time limit on the same hardware, and runs alone on it.
- Every run records its seed, the iterations or solutions it reached, and the solver versions,
  so it can be replayed under that budget.
- Every result is validated and scored by the solver's shared validator and objective, never by
  the objective a solver reports about itself.
- In re-optimization, our solver is compared with OR-Tools warm-started from the current plan.
- The CI regression gate uses fixed seeds and a fixed search budget, never a wall-clock limit.
  The equal-time study runs offline.
- Results are reported whether or not our solver wins.

## Commands

Run `just` recipes from the repository root, and `uv` from this folder.

| Command | What it does |
|---|---|
| `just research-check` | ruff format check, ruff lint, mypy strict, pytest |
| `just research-format` | ruff format and `ruff check --fix` |
| `uv run --locked pytest tests/test_x.py::test_name` | One test |

Tests and docstrings follow the solver's rules: see `src/backend/solver/CLAUDE.md`.

[ADR-004]: ../../docs/ADRs/ADR-004-pluggable-solver-port.md
[ADR-005]: ../../docs/ADRs/ADR-005-or-tools-first-research-solver-for-re-optimization.md

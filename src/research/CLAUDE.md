# Research

The benchmark harness, the disruption simulation and the order generator. Never deployed. It is
the only project that installs PyVRP. Python 3.14, uv.

It is a primary adapter over the optimizer's application layer ([TS §8]): it calls the
optimizer's code directly, with no HTTP and no `core`.

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
  solver's current code. [ADR-037] (Accepted) moves that project to `src/optimizer`, package
  `dispatchflow_opt`, in the M0 restructure.
- It uses the solver's `domain`, `application` and solver adapter packages, the last to build its
  own registry. It never imports the solver's HTTP adapter, `adapters/inbound/http`. Today the
  solver adapters sit in `solvers/`. [ADR-040] puts them under
  `adapters/outbound/solvers/`, as [TS §4] draws it, and this rule holds either way.
- The harness builds its own solver registry, which adds a PyVRP adapter. PyVRP and its adapter
  stay in this project ([ADR-004]).
- After any change to the solver's `pyproject.toml`, run `uv lock` here.

## Benchmark rules ([ADR-005])

- Every solver gets the same time limit on the same hardware, and runs alone on it.
- Every run records its seed, the iterations or solutions it reached and the solver versions
  ([NFR-115]), so it can be replayed under that budget.
- Every result is validated and scored by the solver's shared validator and objective, never by
  the objective a solver reports about itself.
- In re-optimization, our solver is compared with OR-Tools warm-started from the current plan.
- The CI regression gate uses fixed seeds and a fixed search budget, never a wall-clock limit.
  The equal-time study runs offline.
- Results are reported whether or not our solver wins.

## Data and provenance

Rules for the harness as it lands; nothing here exists yet.

- Data and results derived from pilot data never enter the repository. Only their hash and their
  configuration do (NFR-50, [TS §8]).
- Every run has a manifest, written first: the commit, the configuration hash, the seeds, the CPU
  model, the instance type and the package versions ([TS §8], Provenance).
- Final figures come from scripts, never from a notebook.

## Commands

Run `just` recipes from the repository root, and `uv` from this folder.

| Command | What it does |
|---|---|
| `just research-check` | ruff format check, ruff lint, mypy strict, pytest |
| `just research-format` | ruff format and `ruff check --fix` |
| `uv run --locked pytest tests/test_x.py::test_name` | One test |

`just study` ([TS §5]) and `just seed` ([roadmap §2.2]) are planned recipes; the `justfile` does not
have them yet.

Tests and docstrings follow the solver's rules: see `src/backend/solver/CLAUDE.md`.

[ADR-004]: ../../docs/ADRs/ADR-004-pluggable-solver-port.md
[ADR-005]: ../../docs/ADRs/ADR-005-or-tools-first-research-solver-for-re-optimization.md
[ADR-037]: ../../docs/ADRs/ADR-037-flat-layout-under-src-with-deploy-and-infra.md
[ADR-040]: ../../docs/ADRs/ADR-040-core-is-a-spring-modulith-monolith-with-one-module-per-context.md
[NFR-115]: ../../docs/requirements/requirements.md#nfr-115
[TS §5]: ../../docs/architecture/tech_stack.md#5-development-environment-and-workflow
[TS §4]: ../../docs/architecture/tech_stack.md#4-repository-layout
[TS §8]: ../../docs/architecture/tech_stack.md#8-research-harness
[roadmap §2.2]: ../../docs/roadmap.md#22-alpha-track

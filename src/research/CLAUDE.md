# Research

The benchmark harness, the disruption simulation and the order generator. Never deployed. It is
the only project that installs PyVRP. Python 3.14, uv.

It is a primary adapter over the optimizer's application layer ([TS §8]): it calls the
optimizer's code directly, with no HTTP and no `core`.

## Layout

`src/dispatchflow_research/` holds `benchmark/`, `simulation/`, `generator/` and `solvers/`, the
harness's own solver adapters. This folder also holds `results/`, `studies/` (the YAML study files,
NFR-122) and `tests/` (pytest; `test_environment.py` checks that both the optimizer and PyVRP
import). Benchmark instances go in the root `data/`, which git ignores, never here
([ADR-123]).

## How it uses the optimizer

- It depends on `src/backend/optimizer`, package `dispatchflow_optimizer`, as an editable path
  dependency, so it always runs the optimizer's current code.
- It uses the optimizer's `domain`, `application` and solver adapter packages, the last to build its
  own registry. It never imports the optimizer's HTTP adapter, `adapters/inbound/http`.
- The harness builds its own solver registry, which adds the PyVRP adapter in `solvers/pyvrp/`.
  PyVRP and its adapter stay in this project ([ADR-004]).
- After any change to the optimizer's `pyproject.toml`, run `uv lock` here.

## Benchmark rules ([ADR-005])

- Every solver gets the same time limit on the same hardware, and runs alone on it.
- Every run records its seed, the iterations or solutions it reached and the solver versions
  ([NFR-115]), so it can be replayed under that budget.
- Every result is validated and scored by the optimizer's shared validator and objective, never by
  the objective a solver reports about itself.
- In re-optimization, our solver is compared with OR-Tools warm-started from the current plan.
- The CI regression gate uses fixed seeds and a fixed search budget, never a wall-clock limit.
  The equal-time study runs offline.
- Results are reported whether or not our solver wins.
- The solver comparison follows [ADR-091]. Its tests are paired, one-sided Wilcoxon tests. In the
  static study every solver gets 60 seconds and 10 runs per instance, PyVRP and the
  Gehring-Homberger instances included (item 8). The re-optimization study's primary limit is 5
  seconds (item 7), and the scaling study runs 3 times per size. The 30 plans the re-optimization
  study starts from are solved once, by OR-Tools at 60 seconds, and stored (item 2).
- Research instances are exempt from BR-101's plan size ([ADR-091], item 5), as FR-390 exempts them
  from BR-50.
- A crashed study resumes, because the runner skips the results it already holds ([ADR-091], item
  10).
- mutmut runs once over `Problem.validate` before the first measured run. Every surviving mutant
  becomes a test, or is recorded as equivalent with its reason ([ADR-091], item 12).

## Data and provenance

- Data and results derived from pilot data never enter the repository. Only their hash and their
  configuration do (NFR-50, [TS §8]).
- Every run writes its manifest first, so a crashed run still leaves its provenance. It holds the
  commit, the configuration hash, the seeds, the CPU model, the instance type, the `uv` lock hash and
  the package versions ([TS §8], Provenance; [TS §7], Packaging). For the re-optimization study it
  also holds the hash of the 30 solved plans ([ADR-091]). "First" comes from the
  [retired plan's §22][PL-22].
- Final figures come from scripts, never from a notebook.

## Commands

Run `just` recipes from the repository root, and `uv` from this folder.

| Command | What it does |
|---|---|
| `just research-check` | ruff format check, ruff lint, mypy strict, pytest |
| `just research-format` | ruff format and `ruff check --fix` |
| `uv run --locked pytest tests/test_x.py::test_name` | One test |

Tests are named `test_given_x_when_y_then_z`. Every public module, class and function has a
Google-style docstring, which ruff enforces; tests are exempt.

[ADR-004]: ../../docs/ADRs/ADR-004-pluggable-solver-port.md
[ADR-005]: ../../docs/ADRs/ADR-005-or-tools-first-research-solver-for-re-optimization.md
[ADR-091]: ../../docs/ADRs/ADR-091-study-design-for-the-solver-comparison.md
[ADR-123]: ../../docs/ADRs/ADR-123-the-code-keeps-its-grouped-layout-and-three-components-take-new-names.md
[NFR-115]: ../../docs/requirements/requirements.md#nfr-115
[TS §7]: ../../docs/architecture/tech_stack.md#7-optimization-service-python
[TS §8]: ../../docs/architecture/tech_stack.md#8-research-harness
[PL-22]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#22-research-harness-and-disruption-simulation

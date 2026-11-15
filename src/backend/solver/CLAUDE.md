# Solver

The optimization service. It serves `contracts/optimization-api/v1`, `core` is its only client,
and it hosts every solver behind one port ([ADR-004]). Python 3.14, FastAPI, Uvicorn, NumPy, uv.

## Layout

| Package under `src/dispatchflow_solver/` | Holds |
|---|---|
| `domain/` | `Problem` (`validate`, `cost`, `explain_drop`), `ProblemFactory` (`daily`, `reoptimization`), `Solution`, `TravelTimeModel`, `PenaltyScale` and `RuleId`: the problem, the solution, the shared objective, the feasibility validator and the drop reasons, for daily solves and re-optimizations. `problem_parts` holds the parts a problem is made of, and `problem_view` the `ProblemView` protocol that the validator, the objective and the drop reasons read it through ([ADR-113]) |
| `application/` | The solve use cases, the `Solver` port and the solver registry |
| `solvers/ortools/`, `solvers/alns/` | One adapter per solver: OR-Tools, and ours. Ours runs the `alns` package (MIT) as its outer loop, and the Clarke-Wright construction, the operators, the route state and the Numba kernels are ours ([ADR-092]) |
| `adapters/inbound/http/` | `create_app()`: the FastAPI app and its request and response models |

`application/` holds only an empty `__init__.py` today, and `solvers/` only `.gitkeep` files. `tests/`
mirrors the package path.

[ADR-037] (Accepted) renames the service to `src/optimizer` with the package `dispatchflow_opt`,
in the M0 restructure. [ADR-040] puts the solvers under `adapters/outbound/solvers/`, as [TS §4]
draws it, where `nearest_neighbour` joins them. Neither is in the tree yet.

## Rules

- `domain` imports no `fastapi`, `pydantic`, `ortools`, `pyvrp`, `numba`, `alns`, `starlette`,
  `uvicorn`, `pydantic_settings`, `structlog` or `opentelemetry`, and no standard-library module
  that reads a clock, draws randomness or does I/O (`datetime`, `time`, `random`, `os`, `io` and the
  like, listed in `pyproject.toml`). NumPy is allowed ([TS §2.1]), but not `numpy.random`,
  `datetime64`, `timedelta64`, `numpy.load`, `fromfile` or `loadtxt`, and neither is
  `importlib.import_module`: ruff's `TID251` bans them in the domain. So do `PTH123`, `T20`, `S102`
  and `S307` for `open()`, `print()`, `exec()` and `eval()` ([ADR-113]). `application`
  imports nothing from FastAPI, Pydantic or OR-Tools. OR-Tools types live in `solvers/ortools`
  ([ADR-001]). `feasibility`, `objective` and `drops` never import `problem`: they read it through
  `ProblemView` ([ADR-113]). import-linter, and ruff for the bans above, check all of this, the
  layers, and that the product never imports PyVRP (`pyproject.toml`, run by `just solver-check`).
- Pydantic models are generated from the contract by datamodel-code-generator ([ADR-043],
  Accepted), and only the HTTP adapter uses them.
- `Problem` owns `validate`, `cost` and `explain_drop` ([TS §7.1]). `validate` is the one
  definition of feasibility, and `cost` is the one objective, a `Cost` of seven terms: `travel`,
  `drops`, `door_window`, `fixed_vehicles`, `lateness`, `orphaned_stops` and `moved_stops`. The last
  two are the route-stability cost the objective charges. It leaves out an unroutable stay-on stop,
  which FR-256 counts and [ADR-105] charges nothing. `drops` holds only the drop penalties
  ([ADR-113]). `Problem` and `Solution` are frozen dataclasses over NumPy arrays ([TS §7.1]).
- `ProblemFactory` alone applies the re-optimization conventions ([ADR-050]). It writes each one
  into the `Problem` once ([ADR-105]): a vehicle's start and end into `Vehicles`, a vehicle that is
  out as an exclusion for its state, and the rest into the `ReoptContext`. A daily `Problem` has no
  context, and every check that reads one is skipped, so a daily solve behaves as before.
- A vehicle's driver is -1 only on a vehicle out of a re-optimization, so every vehicle that can
  take a stop has a driver ([ADR-105]).
- `TravelTimeModel` is the only place the bucket factors and the buffer are applied.
- Numbers follow [ADR-098]:
  - `Problem` holds times and distances as float64, and every limit is inclusive within
    `LIMIT_TOLERANCE` (1e-6).
  - Product times are rounded up with `CEILING_SLACK`. Research times, at factor 1 and buffer 0,
    are never rounded.
  - No road is `NaN` in the domain; the HTTP adapter maps the contract's −1 to it.
- The roster pairs each vehicle with its driver. A route on another driver breaks BR-20.
- Every malformed problem input raises `InvalidProblemError`, never a bare Python error. A
  malformed `Solution` raises `InvalidSolutionError`.
- A route may carry the service starts its solver claims (`planned_starts`). The validator checks
  them against its own timings: BR-03 for a claim before a window opens, BR-04 for any other early
  claim ([ADR-099]).
- The domain caps no counts, because research instances are exempt from the plan limits (BR-101,
  FR-390).
- Travel time comes in three kinds. The validator and the ETAs use buffered times. The objective's
  travel cost uses per-bucket times without the buffer. The search's upper bound is the highest
  factor × (1 + buffer). An arc takes the factor of the bucket it departs in ([DL §10.2],
  [ADR-048]).
- Every solver implements the one `Solver` port, `solve(problem, start, budget, seed)`: `start` is
  an optional warm start, and `budget` is a time limit, an iteration budget or a solution limit
  ([ADR-044], Accepted). A solver is chosen by name for each solve. The registry has no default,
  and a name it does not hold is rejected ([ADR-004]).
- Every solution passes `Problem.validate` and is scored by `Problem.cost` before it leaves the
  service, whichever solver produced it. A solver's own objective value is never reported.
- A solve takes a seed, and nothing draws unseeded randomness, so a run can be replayed
  ([ADR-005]).
- One HTTP process, and one solver worker process per lane, started with `spawn`. A solve that
  overruns its limit by 30 seconds is killed, and validation runs inside the limit ([TS §7.2],
  [ADR-051], NFR-119).
- Settings come from environment variables only, through pydantic-settings ([TS §7], NFR-03).
- PyVRP never enters the product (FR-286): only `src/research` installs it.
  `tests/test_dependency_boundary.py` fails if it is installed or locked here. import-linter
  (`pyproject.toml`) checks imports only, so that test keeps its environment and lockfile checks.
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
  NFR-175). The OR-Tools findings tested on 9.15 (pins through `VehicleVar`, a single-threaded
  search, `solution_limit` stopping early) are in [the plan's §8][PL-8].
- The violation catalogue (`tests/domain/test_violation_catalogue.py`) is Python test builders:
  one case per way each rule breaks, each asserting the exact set of `RuleId`s reported ([DL §14],
  NFR-169). The re-optimization rules have their cases there too, built with `LiveDay`
  (`tests/domain/builders.py`). A new `RuleId` needs a case, or the coverage test fails.
- Hypothesis property tests run from a recorded seed with `deadline=None` and no example database
  (NFR-82, `tests/domain/properties.py`).
- `tests/domain/data/solomon/` holds six Solomon instances and their published best-known
  solutions. `validate` accepts each, and its travel total matches the published one (NFR-167).
  Its `README.md` names the source.
- Once the DispatchFlow solver exists, a property test checks that the same input, seed and
  iteration budget give the same solution in separate processes (NFR-174). No solver exists yet, so
  no such test does.

## Commands

Run `just` recipes from the repository root, and `uv` from this folder.

| Command | What it does |
|---|---|
| `just solver-check` | ruff format check, ruff lint, mypy strict, import-linter, pytest |
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
[ADR-048]: ../../../docs/ADRs/ADR-048-travel-times-from-buckets-and-a-buffer-with-no-live-traffic.md
[ADR-050]: ../../../docs/ADRs/ADR-050-re-optimization-conventions-live-only-in-the-python-problem-factory.md
[ADR-051]: ../../../docs/ADRs/ADR-051-one-solver-worker-per-lane-killed-30-seconds-past-its-limit.md
[ADR-092]: ../../../docs/ADRs/ADR-092-the-alns-package-runs-our-solvers-outer-loop.md
[ADR-098]: ../../../docs/ADRs/ADR-098-the-validator-works-in-double-precision-with-a-tolerance.md
[ADR-099]: ../../../docs/ADRs/ADR-099-a-route-may-carry-the-starts-its-solver-claims.md
[ADR-105]: ../../../docs/ADRs/ADR-105-a-re-optimizations-problem-carries-its-conventions-and-only-a-vehicle-that-is-out-has-no-driver.md
[ADR-113]: ../../../docs/ADRs/ADR-113-route-stability-is-its-own-cost-the-rules-read-a-problem-through-a-protocol-and-untimed-outputs-are-documented.md
[TS §2.1]: ../../../docs/architecture/tech_stack.md#21-the-dependency-rule
[TS §4]: ../../../docs/architecture/tech_stack.md#4-repository-layout
[TS §7]: ../../../docs/architecture/tech_stack.md#7-optimization-service-python
[TS §7.1]: ../../../docs/architecture/tech_stack.md#71-the-hexagon
[TS §7.2]: ../../../docs/architecture/tech_stack.md#72-process-model
[DL §10.2]: ../../../docs/architecture/domain_layer/domain_layer_design.md#102-one-travel-time-model-in-python
[DL §14]: ../../../docs/architecture/domain_layer/domain_layer_design.md#14-testing-the-domain
[PL-8]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#8-or-tools-model

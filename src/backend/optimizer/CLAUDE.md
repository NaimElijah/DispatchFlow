# Optimizer

The optimization service. It serves `contracts/optimization-api/v1`, `core` is its only client,
and it hosts every solver behind one port ([ADR-004]). Python 3.14, FastAPI, Uvicorn, NumPy, uv.

## Layout

| Package under `src/dispatchflow_optimizer/` | Holds |
|---|---|
| `domain/` | `Problem` (`validate`, `cost`, `explain_drop`), `ProblemFactory`, `Solution`, `TravelTimeModel`, `PenaltyScale`, `RuleId`; `problem_parts` the parts of a problem, `problem_view` the `ProblemView` protocol the rules read it through ([ADR-113]) |
| `application/` | The `Solver` protocol, `SolverRegistry`, `Budget`, `Stopwatch`, the errors, the requests and outcomes, and the use cases `Solve`, `Reoptimize`, `Evaluate`, `Baseline` and `ListSolvers` |
| `adapters/outbound/solvers/<name>/` | One adapter per solver: OR-Tools, ours and `nearest_neighbour` ([ADR-040], [TS §4]). Ours runs the `alns` package (MIT) as its outer loop; the rest is ours ([ADR-092]) |
| `adapters/inbound/http/` | `create_app()`: the FastAPI app and its request and response models |
| `adapters/inbound/worker/` | The solver worker per lane ([ADR-051]), apart from `http` so `spawn` never loads FastAPI in a solver process |
| `adapters/outbound/clock/` | The `Stopwatch` adapter, over the monotonic clock |

An unbuilt `application/` body raises `NotImplementedError("To Be Implemented.")`, and
`grep -rF "To Be Implemented."` lists the work left ([ADR-132]). To fill one, write the failing
test, replace the `raise`, then build the adapter. Building a body may change a declared signature,
with every caller in the same pull request, and drops the docstring's "To Be Implemented."
([ADR-119]).
`tests/` mirrors the package path.

## Rules

- `domain` imports no framework, transport, solver library, settings or telemetry package, and
  nothing that reads a clock, draws randomness or does I/O. NumPy is allowed ([TS §2.1]) but not its
  random, datetime or file APIs, nor `importlib.import_module`, `open()`, `print()`, `exec()` or
  `eval()` (ruff `TID251`, `PTH123`, `T20`, `S102`, `S307`; [ADR-113]). `application` imports no web
  framework, Pydantic, solver library or process model. `feasibility`, `objective` and `drops` read
  a problem through `ProblemView`, never `problem` ([ADR-113]). OR-Tools types live only in its
  solver adapter ([ADR-001]), the process model in `adapters/inbound/worker` ([ADR-051]), and the
  `Solver` port and registry in `application/ports.py` ([ADR-004]). The full lists are
  `pyproject.toml`'s import-linter contracts and ruff `banned-api`, run by `just optimizer-check`.
  They also keep an outbound adapter from importing an inbound one, and each solver from importing
  another.
- Only the HTTP adapter uses the Pydantic models generated from the contract.
- `Problem` owns `validate`, `cost` and `explain_drop` ([TS §7.1]). `validate` is the one definition
  of feasibility, and `cost` is the one objective. It charges route stability through two terms, and
  leaves out an unroutable stay-on stop, which FR-256 counts and [ADR-105] charges nothing. `drops`
  holds only the drop penalties. `Problem` and `Solution` are frozen dataclasses over NumPy arrays.
- `ProblemFactory` alone applies the re-optimization conventions ([ADR-050]). It writes each one
  into the `Problem` once ([ADR-105]): a vehicle's start and end into `Vehicles`, a vehicle that is
  out as an exclusion for its state, and the rest into the `ReoptContext`. A daily `Problem` has no
  context, and every check that reads one is skipped.
- A vehicle's driver is -1 only on a vehicle out of a re-optimization ([ADR-105]). The roster pairs
  each vehicle with its driver; a route on another driver breaks BR-20.
- `TravelTimeModel` is the only place the bucket factors and the buffer are applied. The validator
  and the ETAs use buffered times, the objective's travel cost per-bucket times without the buffer,
  and the search's upper bound is the highest factor × (1 + buffer). An arc takes the factor of the
  bucket it departs in ([DL §10.2], [ADR-048]).
- Numbers follow [ADR-098]: times and distances are float64, and every limit is inclusive within
  `LIMIT_TOLERANCE` (1e-6). Product times are rounded up with `CEILING_SLACK`; research times, at
  factor 1 and buffer 0, never. No road is `NaN` in the domain; the HTTP adapter maps the contract's
  −1 to it.
- Every malformed problem input raises `InvalidProblemError`, never a bare Python error. A
  malformed `Solution` raises `InvalidSolutionError`.
- A route may carry the service starts its solver claims (`planned_starts`). The validator checks
  them against its own timings: BR-03 for a claim before a window opens, BR-04 for any other early
  claim ([ADR-099]).
- The domain caps no counts, because research instances are exempt from the plan limits (BR-101,
  FR-390).
- Every solver implements the one `Solver` port ([ADR-044]): an optional warm start, a budget (a
  time limit, an iteration budget or a solution limit) and a seed in, a
  `SearchResult(solution, reached)` out, where `reached` is the replay budget ([ADR-120]). A solver
  is chosen by name for each solve. The registry has no default, and a name it does not hold is
  rejected ([ADR-004]).
- Every solution passes `Problem.validate` and is scored by `Problem.cost` before it leaves the
  service, whichever solver produced it. A solver's own objective value is never reported.
- A solve takes a seed, and nothing draws unseeded randomness, so a run can be replayed
  ([ADR-005]).
- One HTTP process, and one solver worker process per lane, started with `spawn`. A solve that
  overruns its limit by 30 seconds is killed, and validation runs inside the limit ([TS §7.2],
  [ADR-051], NFR-119).
- Settings come from environment variables only, through pydantic-settings (NFR-03).
- PyVRP never enters the product (FR-286): only `src/research` installs it.
  `tests/test_dependency_boundary.py` fails if it is installed or locked here. import-linter checks
  imports only, so that test keeps its environment and lockfile checks.
- Docstrings are Google-style; ruff enforces it, and tests are exempt.

## Dependencies

- `uv add <package>`, or `uv add --dev <package>` for tooling.
- `src/research` depends on this project as an editable path dependency. After any change to
  `pyproject.toml`, run `uv lock` in `src/research` too, or `just research-check` fails on the
  stale lockfile.
- The HTTP tests use `httpx2`, not `httpx`, for Starlette's test client.

## Tests

- HTTP tests go through `TestClient(create_app())`, never by calling handlers directly.
- A test gives a solver an iteration or solution budget, never a wall-clock limit (NFR-82,
  NFR-175). The OR-Tools findings tested on 9.15 (pins through `VehicleVar`, a single-threaded
  search, `solution_limit` stopping early) are in [the plan's §8][PL-8].
- The violation catalogue (`tests/domain/test_violation_catalogue.py`) holds one case per way each
  rule breaks, asserting the exact set of `RuleId`s reported (NFR-169), re-optimization rules
  included (`LiveDay`, in `tests/domain/builders.py`). A new `RuleId` needs a case, or the coverage
  test fails.
- Hypothesis property tests run from a recorded seed with `deadline=None` and no example database
  (NFR-82, `tests/domain/properties.py`).
- `tests/domain/data/solomon/` holds six Solomon instances and their published best-known solutions.
  `validate` accepts each, and its travel total matches the published one (NFR-167).
- Once the DispatchFlow solver exists, a property test checks that the same input, seed and
  iteration budget give the same solution in separate processes (NFR-174).

## Commands

Run `just` recipes from the repository root, and `uv` from this folder.

| Command | What it does |
|---|---|
| `just optimizer-check` | ruff format check, ruff lint, mypy strict, import-linter, pytest |
| `just optimizer-format` | ruff format and `ruff check --fix` |
| `just optimizer-run` | Starts the service on port 8000, reloading on change |
| `uv run --locked pytest tests/path/test_x.py::test_name` | One test |

[ADR-001]: ../../../docs/ADRs/ADR-001-domain-driven-design-with-hexagonal-architecture.md
[ADR-004]: ../../../docs/ADRs/ADR-004-pluggable-solver-port.md
[ADR-005]: ../../../docs/ADRs/ADR-005-or-tools-first-research-solver-for-re-optimization.md
[ADR-040]: ../../../docs/ADRs/ADR-040-core-is-a-spring-modulith-monolith-with-one-module-per-context.md
[ADR-044]: ../../../docs/ADRs/ADR-044-solver-port-takes-start-budget-and-seed-and-problem-judges.md
[ADR-048]: ../../../docs/ADRs/ADR-048-travel-times-from-buckets-and-a-buffer-with-no-live-traffic.md
[ADR-050]: ../../../docs/ADRs/ADR-050-re-optimization-conventions-live-only-in-the-python-problem-factory.md
[ADR-051]: ../../../docs/ADRs/ADR-051-one-solver-worker-per-lane-killed-30-seconds-past-its-limit.md
[ADR-092]: ../../../docs/ADRs/ADR-092-the-alns-package-runs-our-solvers-outer-loop.md
[ADR-098]: ../../../docs/ADRs/ADR-098-the-validator-works-in-double-precision-with-a-tolerance.md
[ADR-099]: ../../../docs/ADRs/ADR-099-a-route-may-carry-the-starts-its-solver-claims.md
[ADR-105]: ../../../docs/ADRs/ADR-105-a-re-optimizations-problem-carries-its-conventions-and-only-a-vehicle-that-is-out-has-no-driver.md
[ADR-113]: ../../../docs/ADRs/ADR-113-route-stability-is-its-own-cost-the-rules-read-a-problem-through-a-protocol-and-untimed-outputs-are-documented.md
[ADR-119]: ../../../docs/ADRs/ADR-119-the-application-layer-is-declared-ahead-as-signatures-whose-bodies-throw.md
[ADR-120]: ../../../docs/ADRs/ADR-120-the-application-layer-takes-an-actor-and-a-call-context-and-reads-through-query-ports.md
[ADR-132]: ../../../docs/ADRs/ADR-132-unbuilt-bodies-say-to-be-implemented.md
[TS §2.1]: ../../../docs/architecture/tech_stack.md#21-the-dependency-rule
[TS §4]: ../../../docs/architecture/tech_stack.md#4-repository-layout
[TS §7.1]: ../../../docs/architecture/tech_stack.md#71-the-hexagon
[TS §7.2]: ../../../docs/architecture/tech_stack.md#72-process-model
[DL §10.2]: ../../../docs/architecture/domain_layer/domain_layer_design.md#102-one-travel-time-model-in-python
[PL-8]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#8-or-tools-model

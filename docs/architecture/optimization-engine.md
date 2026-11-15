# DispatchFlow — Optimization engine

`src/backend/solver` serves one operation, `GET /health/liveness`. Its `domain` package holds the
domain for daily solves and re-optimizations ([code-structure.md](code-structure.md#srcbackendsolver)):
- `Problem`, with `validate` (the feasibility validator), `cost` (the objective, a `Cost` of seven
  terms, of which `orphaned_stops` and `moved_stops` are the route-stability cost the objective
  charges, leaving out an unroutable stay-on stop, [ADR-113](../ADRs/ADR-113-route-stability-is-its-own-cost-the-rules-read-a-problem-through-a-protocol-and-untimed-outputs-are-documented.md))
  and `explain_drop` (the drop reasons);
- `ProblemFactory.daily` and `ProblemFactory.reoptimization`, `Solution`, `TravelTimeModel` and
  `PenaltyScale`.

`ProblemFactory.reoptimization` takes a live day's facts and applies
[ADR-050](../ADRs/ADR-050-re-optimization-conventions-live-only-in-the-python-problem-factory.md)'s
six conventions. It writes them into the `Problem`'s vehicles and into an optional `ReoptContext`,
which a daily `Problem` lacks
([ADR-105](../ADRs/ADR-105-a-re-optimizations-problem-carries-its-conventions-and-only-a-vehicle-that-is-out-has-no-driver.md)).

`Problem`'s methods delegate to `feasibility`, `objective` and `drops`, which read the problem through
the `ProblemView` protocol and never import `problem`; the parts a problem holds are in
`problem_parts`
([ADR-113](../ADRs/ADR-113-route-stability-is-its-own-cost-the-rules-read-a-problem-through-a-protocol-and-untimed-outputs-are-documented.md)).

Nothing calls it yet. The `application` package is declared ahead of its milestones and not built
([ADR-119](../ADRs/ADR-119-the-application-layer-is-declared-ahead-as-signatures-whose-bodies-throw.md)):
the `Solver` protocol and its `SearchResult`, `SolverRegistry`, `Budget` (the alias over `TimeLimit |
IterationLimit | SolutionLimit`), the `Stopwatch` protocol and the errors in `ports.py`, the requests
and outcomes, and five use cases, `Solve`, `Reoptimize`, `Evaluate`, `Baseline` and `ListSolvers`.
A request the service can't run raises `UnknownSolverError` or `UnsupportedBudgetError`, and a search
that returns no solution raises `SearchFailedError` with its `SearchFailure`, which the use case
answers as `Failed`.
Every body that would do work raises `NotImplementedError("Built in M<n>")`. No solver implements
the port, and each `solvers` folder holds only a `.gitkeep`.
[ADR-098](../ADRs/ADR-098-the-validator-works-in-double-precision-with-a-tolerance.md) fixes the
validator's numbers.

The decided design is in
[domain layer design §4.9](domain_layer/domain_layer_design.md#49-optimization-python) (`Problem`,
`ProblemFactory` and the drop penalty) and
[§10.2](domain_layer/domain_layer_design.md#102-one-travel-time-model-in-python) (the one
travel-time model), and in [tech stack §7](tech_stack.md#7-optimization-service-python) (the tools,
the hexagon and the process model). The ADRs cover:

- the solver port
  ([ADR-004](../ADRs/ADR-004-pluggable-solver-port.md),
  [ADR-044](../ADRs/ADR-044-solver-port-takes-start-budget-and-seed-and-problem-judges.md));
- the objective and re-optimization rules
  ([ADR-029](../ADRs/ADR-029-solver-objective-and-re-optimization-rules.md),
  [ADR-048](../ADRs/ADR-048-travel-times-from-buckets-and-a-buffer-with-no-live-traffic.md),
  [ADR-049](../ADRs/ADR-049-drop-penalty-unit-k-scaled-by-priority.md),
  [ADR-100](../ADRs/ADR-100-k-covers-door-windows-outside-a-shift-and-no-vehicle-is-a-drop-reason.md),
  [ADR-113](../ADRs/ADR-113-route-stability-is-its-own-cost-the-rules-read-a-problem-through-a-protocol-and-untimed-outputs-are-documented.md));
- capacity ([ADR-061](../ADRs/ADR-061-capacity-is-weight-and-volume.md)) and the re-optimization
  conventions
  ([ADR-050](../ADRs/ADR-050-re-optimization-conventions-live-only-in-the-python-problem-factory.md));
- one worker per lane
  ([ADR-051](../ADRs/ADR-051-one-solver-worker-per-lane-killed-30-seconds-past-its-limit.md));
- the matrix stored with each plan
  ([ADR-052](../ADRs/ADR-052-each-plan-stores-the-matrix-and-values-it-was-solved-with.md));
- time ([ADR-088](../ADRs/ADR-088-israel-local-time-for-every-date-and-schedule.md));
- the DispatchFlow solver
  ([ADR-005](../ADRs/ADR-005-or-tools-first-research-solver-for-re-optimization.md),
  [ADR-092](../ADRs/ADR-092-the-alns-package-runs-our-solvers-outer-loop.md)) and the comparison
  study ([ADR-091](../ADRs/ADR-091-study-design-for-the-solver-comparison.md)).

Only the domain is described as built. The application's bodies, the solvers and the contract's
operations arrive with their milestones.
[The domain layer visual guide](domain_layer/domain_layer_visual_guide.md#8-the-python-optimization-domain)
draws the domain's modules and which imports which.

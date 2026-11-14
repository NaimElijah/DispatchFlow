# DispatchFlow — Optimization engine

`src/backend/solver` serves one operation, `GET /health/liveness`. Its `domain` package holds the
daily-solve domain ([code-structure.md](code-structure.md#srcbackendsolver)):
- `Problem`, with `validate` (the feasibility validator), `cost` (the objective) and
  `explain_drop` (the drop reasons);
- `ProblemFactory.daily`, `Solution`, `TravelTimeModel` and `PenaltyScale`.

Nothing calls it yet. No solver, no `Solver` port and no use case exists; the `application` and
`solvers` folders hold only a `.gitkeep`. The re-optimization conventions are not built yet.
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
  [ADR-049](../ADRs/ADR-049-drop-penalty-unit-k-scaled-by-priority.md));
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

Only the daily-solve domain is described above. The solvers, the port and the re-optimization
conventions arrive with their code.

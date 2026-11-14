# DispatchFlow — Optimization engine

The solver service is a skeleton today. `src/backend/solver` serves one operation,
`GET /health/liveness`, and its `domain`, `application` and `solvers` folders hold only a
`.gitkeep` ([code-structure.md](code-structure.md#srcbackendsolver)). No solver, `Solver` port,
validator or objective exists in code.

The decided design is the domain document, the tech stack and the ADRs. The tools are in
[tech stack §7](tech_stack.md#7-optimization-service-python). The ADRs cover the solver port
([ADR-044](../ADRs/ADR-044-solver-port-takes-start-budget-and-seed-and-problem-judges.md)), the
OR-Tools model's inputs ([ADR-048](../ADRs/ADR-048-travel-times-from-buckets-and-a-buffer-with-no-live-traffic.md),
[ADR-049](../ADRs/ADR-049-drop-penalty-unit-k-scaled-by-priority.md) and
[ADR-061](../ADRs/ADR-061-capacity-is-weight-and-volume.md)),
re-optimization
([ADR-050](../ADRs/ADR-050-re-optimization-conventions-live-only-in-the-python-problem-factory.md))
and the DispatchFlow solver
([ADR-092](../ADRs/ADR-092-the-alns-package-runs-our-solvers-outer-loop.md)). The detail is written
by the code that builds it.

This file will describe the engine as built once solver code exists.

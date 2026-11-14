# DispatchFlow — Optimization engine

The solver service is a skeleton today. `src/backend/solver` serves one operation,
`GET /health/liveness`, and its `domain`, `application` and `solvers` folders hold only a
`.gitkeep` ([code-structure.md](code-structure.md#srcbackendsolver)). No solver, `Solver` port,
validator or objective exists in code.

The decided design is in the
[technical implementation plan](../init_impl_ref/technical_implementation_plan.md): the service in
[§7](../init_impl_ref/technical_implementation_plan.md#7-optimization-service), the OR-Tools model
in [§8](../init_impl_ref/technical_implementation_plan.md#8-or-tools-model),
re-optimization in [§9](../init_impl_ref/technical_implementation_plan.md#9-re-optimization) and
the DispatchFlow solver in
[§10](../init_impl_ref/technical_implementation_plan.md#10-dispatchflow-solver-alns). The tools
are in [tech stack §7](tech_stack.md#7-optimization-service-python).

This file will describe the engine as built once solver code exists.

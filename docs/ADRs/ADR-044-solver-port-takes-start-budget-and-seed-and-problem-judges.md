# ADR-044: The solver port takes a start, a budget and a seed, and `Problem` alone judges a solution

- **Status:** Accepted; extended by ADR-050 and ADR-099; amended by ADR-105
- **Date:** 2026-11-13

## Context

[ADR-004] put every solver behind one port, chosen by name inside the optimization service. It
decided that:

- the problem holds the orders, the fleet, the travel-time matrix, the objective's settings, "the
  time limit and the seed", and, for a re-optimization, the current routes;
- an optional search budget (iterations or a solution limit) ends the search instead of the clock;
- the solution holds the routes and the dropped orders "with their reasons";
- the shared validator ([BR-15]) and the shared objective ([BR-09]) judge every solution before it
  leaves the service;
- PyVRP and its adapter live in the benchmark harness's package, and the service never installs it.

Today none of this is code. The solver service has an HTTP app and a health probe. The
`Solver` port, the domain and the solvers are placeholders (`src/backend/solver/CLAUDE.md`). One
test, `tests/test_dependency_boundary.py`, checks that PyVRP is neither installed nor in the
lockfile.

The proposed design restates the shape. Where its documents disagree, the domain document wins:

- **`Problem` is the one definition of what a solution means.** It owns `validate`, `cost` and
  `explain_drop` as methods. Every solver, edit check, publishing re-check and research score goes
  through them ([DL §4.9], [PL §7], [BR-42]).
- **Tests must not run on wall-clock limits.** Property tests and end-to-end tests run under
  iteration or solution budgets ([NFR-82], [NFR-175]).
- **The same seed gives the same answer in two processes** ([PL §24], [NFR-13]). The matrix arrives
  in the request, so the solver stays a pure function ([TS §7.2]).
- **The research study compares solvers under identical rules.** PyVRP is its reference and never
  serves a solve in the product ([FR-286]).

## Decision

We change the call, the owner of the judgement and the set of adapters. ADR-004's by-name choice
stands: the request carries the solver's name, and `GET /v1/solvers` lists the names and versions
([PL §6.1]).

- **The port is `solve(problem, start, budget, seed) -> Solution`** ([TS §7.1], [PL §7.2]).
  - `start` is an optional warm-start solution.
  - `budget` is a time limit, an iteration budget or a solution limit.
  - `seed` is an argument, not a field of the problem.
- **`Problem` owns three rules** ([TS §7.1], [PL §7], [DL §4.9]):
  - `validate`, the single definition of feasibility ([BR-42], [PL §7.3]);
  - `cost`, the shared objective ([BR-09]);
  - `explain_drop`, every drop reason, from a missing location to *displaced* ([PL §7.5],
    [FR-144]).

  Each lives in its own module (`feasibility.py`, `objective.py`, `drops.py`), and `Problem`'s
  methods delegate to it ([PL §7]).
- **A `Solution` holds routes and the set of dropped orders, and no reasons** ([PL §7.2]). The
  reasons come from `explain_drop` after the solve, so an adapter never supplies one. Orders that
  never enter the search, with no location or no road route, sit in the `Problem` with their
  reason ([DL §4.9]).
- **The adapters** ([TS §7.1]):
  - `ortools`, the product solver ([ADR-005]);
  - `alns`, the DispatchFlow solver ([FR-42]);
  - `nearest_neighbour`, the baseline ([FR-39], [FR-342]);
  - `pyvrp`, for the harness only ([FR-286]). import-linter enforces it: no product adapter imports
    `pyvrp` ([TS §2.2]).
- **Solvers are never compared on an objective they report themselves.** This is ADR-004's rule,
  and `Problem.cost` is how it is kept.
- **The PyVRP boundary test stays.** `src/backend/solver/tests/test_dependency_boundary.py` is kept
  beside import-linter. import-linter checks imports only. The test also checks the installed
  environment and `uv.lock`, so PyVRP cannot arrive as an installed package or a locked
  dependency without an import.

### What changes in ADR-004

The table lists the parts of ADR-004 that this ADR replaces. The rest of ADR-004 stands: the one
port, the choice by name, the optional search budget, and keeping PyVRP out of the service.

| ADR-004 | This ADR |
|---|---|
| The problem holds the time limit and the seed | They are arguments of the call |
| An optional budget replaces the clock | `budget` is an argument, and a time limit is one of its three kinds |
| The problem holds the current routes, and a solver may start from them | `start` carries the warm start. The problem's re-optimization context holds the reported prefixes and each stop's origin vehicle ([PL §7.2]) |
| The solution holds dropped orders with reasons | The solution holds dropped orders. `explain_drop` gives the reasons |
| A shared validator and objective, applied by the solve use case | `Problem.validate` and `Problem.cost`, called by every solver, edit check, re-check and research score |
| OR-Tools and the DispatchFlow solver in the service; PyVRP in the harness | Also `nearest_neighbour` as a solver. `pyvrp` stays harness-only, and import-linter guards it |

The re-optimization conventions that `ProblemFactory` applies are recorded in [ADR-050].

## Consequences

- **Easy:** property and end-to-end tests run on iteration or solution budgets, with no clock
  ([NFR-82], [NFR-175]).
- **Easy:** a business rule changes in one place, `Problem`, and a drop reason is read in one place
  ([BR-42], [FR-144]).
- **Easy:** the baseline and the study's solvers are called the same way as the product's.
- **Hard:** `explain_drop` must name a reason for every order any adapter drops, including drops an
  adapter made for its own reasons. Its checks run in order and stop at the first that fails
  ([PL §7.5]).
- **Hard:** the sources do not say which kinds of budget each adapter accepts, or how it maps them
  onto its search.
- **Hard:** the shared problem is still a lowest common denominator, as ADR-004 found.
- **Cost:** `Problem` carries three rules. Splitting them into modules keeps the class small
  ([PL §7]).
- **Risk:** ADR-004 asked the solution to say whether the current routes seeded the search, so a
  comparison can tell a warm start from a cold one ([FR-46]). The response `stats` in [PL §6.1]
  list the solver, version, seed, iterations or solutions reached and search time, and say nothing
  of a warm start. Whether the flag survives is open.
- **Risk:** two checks guard one rule, import-linter and the boundary test. A change to the rule,
  such as a second reference solver kept out of the service, touches both.

## Alternatives considered

- **Keep ADR-004's shape**, with the limit and the seed inside the problem. Not chosen: the
  sources move all three inputs to the call, and give one reason, tests that must not run on
  wall-clock limits ([TS §7.1]).
- **Let each solver validate and explain its own result.** Rejected: no solver's own check is the
  final word on its solution ([BR-42]), and objectives that solvers report themselves are not
  comparable ([ADR-004]).
- **A validator in the Java core.** Rejected: the product and the study must share one validator,
  and the validator, objective, conventions and every drop reason live only in Python so both
  score routes identically ([TS §2], [DL §16]).
- **PyVRP in the product.** Rejected: [FR-286] keeps it as the study's reference solver.

## Revisit when

- A solver needs a different runtime or must scale on its own. ADR-004's trigger stands: one
  service per solver becomes worth its cost then.
- An adapter cannot stop on any of the three budget kinds, so a test of it would have to run on a
  wall-clock limit ([NFR-82], [NFR-175]). Reopen the set of budget kinds.
- The study ([FR-46]) cannot tell a warm start from a cold one from the response alone. Add the
  flag back to the response.

[ADR-004]: ADR-004-pluggable-solver-port.md
[ADR-005]: ADR-005-or-tools-first-research-solver-for-re-optimization.md
[ADR-050]: ADR-050-re-optimization-conventions-live-only-in-the-python-problem-factory.md
[BR-09]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-15]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-42]: ../requirements/requirements.md#solving-dropping-and-validation
[DL §4.9]: ../architecture/domain_layer/domain_layer_design.md#49-optimization-python
[DL §16]: ../architecture/domain_layer/domain_layer_design.md#16-why-it-looks-like-this
[FR-39]: ../requirements/requirements.md#reporting-and-baseline
[FR-42]: ../requirements/requirements.md#research-and-evaluation
[FR-46]: ../requirements/requirements.md#research-and-evaluation
[FR-144]: ../requirements/requirements.md#daily-planning
[FR-286]: ../requirements/requirements.md#research-and-evaluation
[FR-342]: ../requirements/requirements.md#reporting-and-baseline
[NFR-13]: ../requirements/requirements.md#nfr-13
[NFR-82]: ../requirements/requirements.md#nfr-82
[NFR-175]: ../requirements/requirements.md#nfr-175
[PL §6.1]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#61-optimizer-api-v1
[PL §7]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#7-optimization-service
[PL §7.2]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#72-problem-and-solution
[PL §7.3]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#73-the-validator
[PL §7.5]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#75-drop-reasons
[PL §24]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#24-testing
[TS §2]: ../architecture/tech_stack.md#2-architecture-style
[TS §2.2]: ../architecture/tech_stack.md#22-enforcement
[TS §7.1]: ../architecture/tech_stack.md#71-the-hexagon
[TS §7.2]: ../architecture/tech_stack.md#72-process-model

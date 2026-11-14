# ADR-005: OR-Tools first, with our own solver as a measured research track aimed at re-optimization

- **Status:** Accepted; extended by ADR-029; amended by ADR-091
- **Date:** 2026-10-27
- **Research track owner:** Naim Elijah

## Context

The team asked whether DispatchFlow should solve routing with its own metaheuristic, or simply
run on Google OR-Tools rather than using OR-Tools only as a benchmark for our solver.

- **OR-Tools alone is not enough.** The hard algorithmic core is the project's research
  component and the main thing that sets it apart. If that core is outsourced entirely,
  DispatchFlow becomes a well-built CRUD and maps application.
- **Our solver alone is too risky.** OR-Tools' routing solver is mature: guided local search,
  many neighbourhood operators and years of tuning. A student team is unlikely to beat it on
  standard static VRPTW in one academic year. The project is also judged on its requirements,
  architecture and testing, and on a working system for a real customer. If the product
  depended on an unfinished solver, all of that would be at risk.
- **Where ours can plausibly win.**
  - *Live re-optimization.* A new order arrives or a driver runs late, and we expect the solver
    to repair the current plan within a short time limit instead of solving from scratch.
    ALNS's destroy-and-repair design suits this. OR-Tools can also start its search from given
    routes, so the advantage has to be measured, not assumed.
  - *Customer-specific constraints* that OR-Tools models awkwardly, such as route stability
    ([FR-37], [Q-06]), are a second possible edge.
- **The initial sources pointed the other way.**
  - The implementation plan ranked our solver a Should and re-optimization a Could.
  - The initial presentation used OR-Tools only as a benchmark baseline and scheduled the
    solver core before the platform.
  - All of them were revised on 2026-10-27 to follow this ADR.

## Decision

We build both, in a set order, behind one solver port ([ADR-004]).

1. **OR-Tools is the solver the product runs on.** It is the default for every daily solve and
   every re-optimization, and it serves the demos and the pilot.
2. **Phase 1 builds a working system on OR-Tools first.** A dispatcher's day runs end to end on
   OR-Tools: orders come in, they are solved, validated routes appear on the map, and the plan is
   published. Nothing in Phase 1 waits for our solver.
3. **Phase 2 builds our solver.** The DispatchFlow solver is built behind the same port
   ([FR-42]): a Clarke-Wright savings construction improved by ALNS with simulated-annealing
   acceptance.
   - Naim Elijah owns this track.
   - It may start while Phase 1 is under way, using the validator and the objective that Phase 1
     builds anyway, but it is never on Phase 1's critical path.
4. **Our solver is aimed at re-optimization.** Its goal is to repair the current plan after a
   disruption at least as well as OR-Tools warm-started from the same plan, under the same short
   time limit ([NFR-25]). Both are anytime searches that run until their limit, so "faster"
   means reaching a given quality at a shorter limit. Beating OR-Tools on static instances is a
   non-goal; the static comparison measures how close we come ([NFR-24]).
5. **The benchmark is a planned experiment, run honestly.**
   - **Instances:** the Solomon set, Gehring-Homberger instances ([Q-22]), and the pilot
     customer's real days once they are available ([Q-01]).
   - **Equal terms:** every solver gets the same time limit on the same hardware, and runs
     alone on it. Each run records its seed, the iterations or solutions it reached and the
     solver versions, so it can be replayed under that budget ([NFR-13]).
   - **Scoring:** every result is validated and scored by the shared objective, never by a
     solver's own ([ADR-004]). The gap formula, the runs per instance and how a failed run is
     counted are fixed in [NFR-24].
   - **Regression gate:** CI runs the solvers with fixed seeds and a fixed search budget
     (iterations, or a solution limit for OR-Tools) instead of a time limit, and fails when
     quality drops below set thresholds. The equal-time study runs offline ([Q-22]), because
     wall-clock limits on shared CI runners do not repeat.
   - **Reference:** PyVRP runs on the static instances as a state-of-the-art reference
     ([FR-51]). It is a benchmark-only dependency, and it is vetted when it is added.
   - **Re-optimization:** our solver is compared with OR-Tools warm-started from the current
     plan, not with OR-Tools solving from scratch, at the same set of time limits ([FR-46]). A
     run in which OR-Tools did not start from the current routes is reported and not counted
     as warm-started.
   - **Success criteria are fixed before the study:** the quality gap N ([NFR-24]) and the
     re-optimization limit T ([NFR-25]), with the protocol each one states. The values stay
     open ([Q-29], [Q-30]) until they are agreed with the academic advisor.
   - **Results are reported whether or not our solver wins.** For example: "within X% of
     OR-Tools on static instances; on single-order insertion, ours at 1 s matches OR-Tools
     at Y s."
6. **Build order.** The initial presentation's timeline was revised to this order:
   1. Research and requirements.
   2. The platform on OR-Tools (Phase 1).
   3. Our solver (Phase 2, overlapping Phase 1).
   4. The benchmark suite against OR-Tools and PyVRP.
   5. Live re-optimization with both solvers.
   6. The driver app.
   7. The pilot and its evaluation.

## Consequences

- **Easy:** an end-to-end system exists early, which removes the risk to the customer pilot
  and the timeline.
- **Easy:** a weak research result still ships, and is still a result to report.
- **Easy:** the advisor approves concrete, measurable targets instead of an open-ended promise
  to build a good solver.
- **Hard:** two solvers must stay in step with one problem model, and that model keeps
  changing under the research track while Phase 1 is still settling it.
- **Hard:** the comparison is only fair if the OR-Tools side is built well. Its warm start and
  its approximation of route stability ([Q-06]) must be done properly; otherwise our solver
  only beats a weakened OR-Tools.
- **Risk:** a research track with no product pressure can slip. It is mitigated by one owner,
  success criteria fixed in advance, and the timeline's milestones.

## Alternatives considered

- **OR-Tools only.** Rejected: it removes the research component, the main thing that sets the
  project apart.
- **Only our own solver, with OR-Tools used only as a benchmark baseline** (the initial
  presentation's plan). Rejected: the customer demo and the pilot would depend on the least
  predictable component.
- **Our solver first, then OR-Tools** (the initial presentation's timeline). Rejected: it puts
  the riskiest work before the first working system.
- **Aiming our solver at static benchmarks.** Rejected: against OR-Tools' tuning, a loss is
  the likely outcome, and it would say little. The static comparison stays as a measurement
  ([NFR-24]), not as the goal.

## Revisit when

- **Our solver meets NFR-25 on the pilot's real days and beats OR-Tools warm-started at the
  shorter limits.** Consider making it the default for re-optimization only, in a new ADR that
  supersedes item 1 for that case. The solver port makes this a change to the backend's
  planning settings ([ADR-004]).
- **OR-Tools warm-started does as well as our solver at every limit in the study.** The
  re-optimization edge does not hold. Report it, and move the research focus to route
  stability as the edge.
- **Q-17 shows that Python cannot reach enough iterations within the time limit.** Revisit the
  language of the solver core, not the port or this build order.

[ADR-004]: ADR-004-pluggable-solver-port.md
[FR-37]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-42]: ../requirements/requirements.md#research-and-evaluation
[FR-46]: ../requirements/requirements.md#research-and-evaluation
[FR-51]: ../requirements/requirements.md#research-and-evaluation
[NFR-13]: ../requirements/requirements.md#nfr-13
[NFR-24]: ../requirements/requirements.md#nfr-24
[NFR-25]: ../requirements/requirements.md#nfr-25
[Q-01]: ../requirements/requirements.md#open-questions
[Q-06]: ../requirements/requirements.md#open-questions
[Q-22]: ../requirements/requirements.md#open-questions
[Q-29]: ../requirements/requirements.md#open-questions
[Q-30]: ../requirements/requirements.md#open-questions

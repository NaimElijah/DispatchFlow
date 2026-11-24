# ADR-113: Route stability is a cost of its own, the rules read a problem through a protocol, and untimed and past-the-day outputs are documented

- **Status:** Accepted
- **Date:** 2026-11-15

## Context

A review of the whole domain layer on 2026-11-15 found three things in the optimizer's Python
domain that the core's Java side mirrors or reads, and that no accepted document settled:

- **The route-stability cost was hidden inside `drops`.** [ADR-105] decision 3 lists the
  re-optimization's two terms, *lateness* and *orphaned stops*, and decision 1 puts the moved-stop
  cost of a stop that stays on its vehicle into that stop's drop penalty. So when such a stop is
  dropped, `Cost.drops` holds K x (6 − p) plus 300, and `Cost.orphaned_stops` holds only the
  orphans. [FR-256] counts a dropped stop as one that leaves its vehicle, so the preview
  ([FR-103]) and the study ([FR-281]) would show a route-stability total that leaves those stops
  out, and the Java core's `ObjectiveTerms`, which mirrors the Python terms one to one, would map
  stability to `orphaned_stops` alone.
- **An import cycle ran through `problem`.** `Problem` owns `validate`, `cost` and `explain_drop`
  and delegates each to `feasibility`, `objective` or `drops` ([TS §7.1], [ADR-044]). Each of the
  three imported `problem` back under `TYPE_CHECKING`, so the cycle existed for the type checker
  and for import-linter, and nothing stopped it from becoming a run-time cycle.
- **What the validator returns when it can't time something, and how late a time can be.** The
  validator gives NaN times to a route with no stops, to a stop it can't reach ([BR-14]) and to a
  return with no road to the end. It never caps a time at the date's end, so a return that breaks
  [BR-06] and the facts of a drop can pass `day_length`. [ADR-101] point 19 says a fact time is a
  time of the date, and [ADR-098] fixes the numbers as float64, but neither says that these
  outputs exist or what they mean. The Java side couldn't represent them, and has been given a way
  to: an untimed route or stop, and times past the day carried as seconds from the date's start.

This ADR amends [ADR-105] decision 3, which lists the re-optimization's terms. The rest of ADR-105
stands, decision 7 included. It applies [ADR-044] to the module structure, and documents the
outputs of [ADR-098]'s validator beside [ADR-101] point 19.

## Decision

Decided on 2026-11-15:

1. **The objective reports route stability in a term of its own, `moved_stops`** ([BR-115],
   [FR-256], [BR-56]). `Cost` gains `moved_stops`: the moved-stop cost times the unserved orders
   that pay to leave their vehicle. `drops` holds only the drop penalties, K x (6 − p) ([BR-54],
   [BR-126]). The terms are:
   - `orphaned_stops`: the moved-stop cost times the orphans, every stop on a vehicle that is out,
     whatever the solution does. Unchanged from ADR-105.
   - `moved_stops`: the moved-stop cost times the stops that stay on their vehicle and are
     dropped. A stop that stays on its vehicle but can't be routed there pays nothing for leaving,
     as ADR-105 decision 7 decided, so it is not counted.
   - Together they are the route-stability cost the objective charges ([BR-115]), and no stop is
     counted in both. This differs from [FR-256]'s count of the stops that leave their vehicle by
     the stops that stay on it but can't be routed there: FR-256 counts such a stop, and ADR-105
     decision 7 charges it nothing. The difference was accepted knowingly. A daily solve has
     neither term, so both are 0.

   `total` holds the same terms as before, so a solution's score is unchanged but for the order of
   the sum, which can move it by an ulp (see the Risk below). The drop penalty a solver reads,
   `Problem.drop_penalties`, still holds the moved-stop cost of a stop that pays to leave, so the
   search is steered exactly as before.
   The factory's per-order flag is kept in the problem as `ReoptContext.pays_to_leave`, and
   `Problem` refuses a flag that disagrees with the roads, or a flagged stop whose drop penalty is
   below the moved-stop cost. This amends ADR-105 decision 3: a re-optimization adds three terms,
   *lateness*, *orphaned stops* and *moved stops*.
2. **The validator, the objective and the drop reasons read a problem through a protocol**
   ([TS §7.1], [ADR-044]). `feasibility`, `objective` and `drops` type their problem as
   `ProblemView`, a `Protocol` of what they read from it, and never import `problem`. `Problem`
   satisfies it structurally, and its methods keep delegating to them. The parts a problem holds
   (`Orders`, `Vehicles`, `Drivers`, `Pin` and `ReoptContext`) move to `problem_parts`, so the
   protocol can name their types, and `problem` re-exports them. An import-linter contract fails
   if any of the three modules imports `problem` again, directly or through another module.

   No test plants a forbidden import to show that this contract, or the domain's contract against
   the clock, randomness and I/O ([TS §2.1], rule 3), fires: import-linter's one public Python
   function, `importlinter.api.read_configuration`, reads the configuration and checks nothing.
   Both were proved by hand on 2026-11-15, on a copy of the optimizer run with `lint-imports
   --no-cache`:
   - a line in `objective` importing `problem` broke the first contract, naming that import;
   - a domain module importing `asyncio`, `concurrent.futures`, `ctypes`, `glob`, `http.client`,
     `locale`, `mmap`, `multiprocessing`, `os.path`, `select`, `selectors`, `shutil`, `signal`,
     `sqlite3`, `ssl`, `sys`, `tempfile` and `zoneinfo` broke the second, once for each module;
   - import-linter can't see `numpy.random` or `np.datetime64` through `import numpy as np`, so
     ruff's TID251 bans `numpy.random`, `numpy.datetime64` and `numpy.timedelta64` in the domain
     only. A domain module using `np.random.default_rng()`, `from numpy.random import ...`,
     `np.datetime64("now")` and `np.timedelta64(1, "s")` was flagged at each, and the same file
     under `solvers/` and `tests/` was not. A dtype string, as in `np.array("now",
     "datetime64[s]")`, passes both tools, and review catches it;
   - ruff also bans, in the domain only, `numpy.load`, `numpy.fromfile`, `numpy.loadtxt` and
     `importlib.import_module` through TID251, and `open()`, `print()`, `exec()` and `eval()`
     through PTH123, T20, S102 and S307. A domain module calling each was flagged once for each,
     the same file under `adapters/` was not, and the domain was clean. Another builtin, such as
     `__import__()`, passes, and review catches it.
3. **The validator's untimed and past-the-day outputs are part of its contract** ([ADR-098],
   [ADR-101] point 19). Python's behaviour stays, and the docstrings of `RouteTiming` and the drop
   facts say it:
   - a route with no stops is not driven, so its departure and its return are NaN;
   - a stop the validator can't time, because it breaks [BR-14] or, in a re-optimization, [BR-10],
     has NaN times, and so has the leg into it;
   - with no road from the last place reached to the end, the return and the last leg are NaN;
   - no time is capped at the date's end: a route that breaks a time rule may arrive, serve or
     return past `day_length`, and so may a drop fact, `TimeWindow.earliest_arrival` and the
     `earliest_return` of `Shift` and `DepotHours`. Every such time is finite.

   A route the validator accepts returns within its driver's shift, which lies on the date
   ([FR-190]), so none of its times passes the date's end by more than ADR-098's tolerance. The
   core carries a time past the day as seconds from the date's start.

## Consequences

- **Easy:** the preview and the study read route stability from two named terms, and the Java
  `ObjectiveTerms` mirrors the Python ones one to one.
- **Easy:** a solver still reads one drop penalty per order, so no search changes, and every
  existing score keeps its total, to within the ulp of the Risk below.
- **Easy:** no module of the domain imports `problem` but the factory, and import-linter keeps it
  so.
- **Easy:** the core knows, from the validator's docstrings, which times may be missing and which
  may pass the day.
- **Hard:** `Cost.drops` no longer equals the sum of `Problem.drop_penalties` over the unserved
  orders in a re-optimization; a reader that wants that sum adds `moved_stops`.
- **Hard:** the problem's parts live in a module of their own, so a reader looks in
  `problem_parts` for `Orders` and `Vehicles`, though `problem` still exports them.
- **Hard:** a reader that compares the stability terms with [FR-256]'s count finds them apart by
  the stops that stay but can't be routed, which the terms leave out.
- **Hard:** each contract's proof is by hand, so a change to import-linter or ruff that stops a
  contract firing is caught only when the proof is run again.
- **Cost:** one more check when a re-optimization's problem is built: each stop that stays is
  tested for a road on its vehicle, which the factory already does once.
- **Risk:** `drops` is the problem's drop penalties less the moved-stop cost, so in floating point
  it can differ from K x (6 − p) by an ulp when K is not a whole number, as in a research instance.
  For the same reason `total`, which now adds `drops` and `moved_stops` where it added one drop
  penalty, can differ from the score the same solution had before by an ulp.

## Alternatives considered

- **`drop_penalties` holds K x (6 − p) only, and the solver-facing sum is computed apart.** Not
  chosen: every solver adapter would then add the moved-stop cost itself, and a solver that forgot
  would drop a stop that should stay. The solver-facing sum was allowed to stay as it is.
- **A moved-stop term that also counts a stop that stays but can't be routed.** Rejected: it
  reverses ADR-105 decision 7, and the charge would only shift every solution's score by the same
  amount.
- **One stability term, the orphans and the dropped stops together.** Not chosen: the orphans are
  a constant no solution can change, and the preview and the study can tell the two apart only if
  they are reported apart.
- **A test that runs import-linter on a planted import.** Not chosen: it would drive import-linter
  through its command-line module, which it doesn't offer as an API, and so break on any release
  that changes it.
- **The cycle left under `TYPE_CHECKING`.** Rejected: import-linter sees it as a cycle, and nothing
  stops a run-time import from closing it.
- **Protocols for each part as well, with the parts left in `problem`.** Not chosen: five more
  protocols that repeat the parts field by field, for no boundary the code needs.
- **Times capped at the date's end, or a flag for a time past it.** Rejected: a cap would make a
  drop fact lie about how far an order misses, and the core can carry the real time as seconds
  from the date's start.

## Revisit when

- The contract's cost terms are added to the optimizer's operations, and they need a term this ADR
  does not give.
- The study measures route stability another way than the moved-stop cost times the stops that
  leave ([FR-281]).
- A solver adapter needs the drop penalty without the moved-stop cost.
- A pilot shows a dispatcher misreads a time past the day's end.
- The preview or the study must show [FR-256]'s count, the stops that stay but can't be routed
  included.
- import-linter offers a public API that checks contracts, so a fixture test can prove they fire.

[ADR-044]: ADR-044-solver-port-takes-start-budget-and-seed-and-problem-judges.md
[ADR-098]: ADR-098-the-validator-works-in-double-precision-with-a-tolerance.md
[ADR-101]: ADR-101-completed-plans-take-late-outcomes-as-revisions-and-routes-keep-an-index.md
[ADR-105]: ADR-105-a-re-optimizations-problem-carries-its-conventions-and-only-a-vehicle-that-is-out-has-no-driver.md
[BR-06]: ../requirements/requirements.md#routing-constraints
[BR-10]: ../requirements/requirements.md#re-optimization
[BR-14]: ../requirements/requirements.md#routing-constraints
[BR-54]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-56]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-115]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-126]: ../requirements/requirements.md#solving-dropping-and-validation
[FR-103]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-190]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-256]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-281]: ../requirements/requirements.md#research-and-evaluation
[TS §2.1]: ../architecture/tech_stack.md#21-the-dependency-rule
[TS §7.1]: ../architecture/tech_stack.md#71-the-hexagon

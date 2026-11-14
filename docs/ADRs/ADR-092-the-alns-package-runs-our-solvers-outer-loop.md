# ADR-092: The `alns` package runs our solver's outer loop, and the rest of the solver stays ours

- **Status:** Accepted
- **Date:** 2026-11-13

## Context

[ADR-005] decided that the team builds its own solver: a Clarke-Wright savings construction improved
by Adaptive Large Neighbourhood Search (ALNS) with simulated-annealing acceptance ([FR-42]). It is the
project's research component. [TS §7][TS-7] left one question open, marked "Optional · B (decide with
the advisor)": should the generic outer loop, which chooses an operator and decides whether to accept
the result, come from a published package or from our own code?

On 2026-11-13 the owner, Naim Elijah, decided for the team to use the `alns` package for the outer
loop. The package is published under the MIT licence, according to the owner's decision. The licence
is checked against the installed version when the package is added ([ADR-074]), and it is compatible
with the project's proprietary licence ([NFR-51], [ADR-017]).

The advisor has not yet confirmed that this counts as the team's solver. The owner confirms it with
the advisor. Until then the fallback is to write the loop ourselves ([TS §7][TS-7]).

What the solver is, and where the speed risk lies ([PL §10][PL-10]):

- It is Clarke-Wright followed by ALNS, as Ropke and Pisinger (2006) describe it, with one difference:
  a constant-time insertion check. That check decides whether Python can reach enough iterations
  ([Q-17]).
- [Q-17]'s evidence of 2026-10-10 is about 1,800 iterations in 30 seconds on a 200-stop day, about
  60 a second. The speed plan targets about 600 a second with Numba kernels
  ([PL §10.5][PL-105]). Whether that competes is for the benchmarks, and [Q-17] stays open.
- The search control is specified: a simulated-annealing start temperature that accepts a solution
  5% worse with probability 0.5, geometric cooling toward 1/1000 of it, adaptive operator weights with
  scores 33, 9 and 13, segments of 100 iterations and a reaction factor of 0.1
  ([PL §10.3][PL-103]).

## Decision

**The `alns` package runs the generic outer loop:** operator selection and simulated-annealing
acceptance ([TS §7][TS-7]).

**Everything that makes the solver DispatchFlow's stays ours:**

- the Clarke-Wright construction;
- the destroy and repair operators;
- the route state and the constant-time insertion check ([PL §10.1][PL-101]);
- the Numba kernels that make the insertion search fast.

The package sits inside the `alns` solver adapter, behind the solver port ([ADR-004]), under
`adapters/outbound/solvers/alns/` ([TS §4][TS-4]). Nothing in the domain imports it, and
import-linter enforces that ([TS §7.1][TS-71]).

**The advisor confirms** that a solver whose outer loop is a published package, and whose
construction, operators and kernels are ours, counts as the team's solver ([ADR-005]). Until the
advisor agrees, the row stays marked as awaiting that confirmation.

**The package joins the licence allow-list** of [NFR-51] ([ADR-074]), and the lock file pins its
version ([ADR-011]).

## Consequences

- **Easy:** a tested, published implementation of the generic loop. The TS row estimates it saves about
  300 lines; that is an estimate and not a count.
- **Easy:** the work that sets the solver apart stays our own: the construction, the operators, the
  route state and the kernels.
- **Hard:** the study's rules must fit the package. The adapter has to reproduce the settings of
  [PL §10.3][PL-103], switch single operators off and fix the operator weights for the ablation study
  ([FR-164]), log each iteration at a sampled interval ([FR-163]) and run from a seed with no clock
  reads under an iteration budget ([NFR-13]). None of these has been checked against the package.
- **Hard:** the package's loop runs in Python, and our own loop could keep more of the iteration inside
  the compiled kernels. The overhead is not measured. The speed plan's target of about 600
  iterations a second ([PL §10.5][PL-105]) has to be met with the package in place.
- **Cost:** one third-party dependency, with a licence check ([ADR-074]) and a place in the lock file.
  Its maintenance status and exact version are not recorded in the sources, and are recorded when the
  package is added.
- **Risk:** the advisor may rule that a solver with a published loop is not "our solver". Then the
  loop is written ourselves, at the cost the package would have saved.
- **Risk:** if the package cannot be made to follow the settings, the ablation or the seed rules,
  the adapter would carry workarounds that make it as long as the loop it replaced.

## Alternatives considered

- **Write the loop ourselves.** Not chosen: it spends effort on the generic part, which a tested package
  already has (about 300 lines, by [TS §7][TS-7]'s estimate), and the parts the study compares stay
  ours either way. It stays the default if the advisor wants every line owned ([TS §7][TS-7]).

## Revisit when

- The advisor says the package-based solver does not count as ours. Write the loop ourselves.
- The adapter cannot meet one of the study's rules ([FR-163], [FR-164], [NFR-13]) without a workaround
  that rewrites the package's loop. Write the loop ourselves.
- The speed plan's measurement ([PL §10.5][PL-105]) shows that the package's per-iteration overhead
  is what keeps the rate below the target. Compare the loop inside the Numba kernels.
- The package's licence changes, or it is no longer maintained. The licence check of [ADR-074] runs
  on each upgrade.

[ADR-004]: ADR-004-pluggable-solver-port.md
[ADR-005]: ADR-005-or-tools-first-research-solver-for-re-optimization.md
[ADR-011]: ADR-011-python-tooling-uv-ruff-mypy-pytest.md
[ADR-017]: ADR-017-proprietary-licence.md
[ADR-074]: ADR-074-dependency-licences-checked-against-an-allow-list.md
[FR-42]: ../requirements/requirements.md#research-and-evaluation
[FR-163]: ../requirements/requirements.md#research-and-evaluation
[FR-164]: ../requirements/requirements.md#research-and-evaluation
[NFR-13]: ../requirements/requirements.md#nfr-13
[NFR-51]: ../requirements/requirements.md#nfr-51
[PL-10]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#10-dispatchflow-solver-alns
[PL-101]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#101-route-state-and-the-constant-time-insertion-check
[PL-103]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#103-search-control
[PL-105]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#105-speed-plan-q-17
[Q-17]: ../requirements/requirements.md#open-questions
[TS-4]: ../architecture/tech_stack.md#4-repository-layout
[TS-7]: ../architecture/tech_stack.md#7-optimization-service-python
[TS-71]: ../architecture/tech_stack.md#71-the-hexagon

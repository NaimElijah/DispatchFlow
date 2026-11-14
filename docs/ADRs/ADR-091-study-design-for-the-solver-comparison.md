# ADR-091: The solver comparison follows eleven fixed design choices, and a mutation run checks the validator first

- **Status:** Accepted
- **Date:** 2026-11-14

## Context

[ADR-005] built DispatchFlow's own solver as a measured research track and left the study's values open
until the academic advisor agreed them. [ADR-029] fixed them on 2026-11-04 without the advisor
([Q-29], [Q-30], [Q-84]). The review of the same day left eleven gaps in the study design, which
[Q-93] collects. A full run takes days, so each gap needs an answer before the first measured run.

On 2026-11-13 the owner, Naim Elijah, decided the eleven answers for the team. On 2026-11-14 he
added a twelfth decision, mutation testing of the validator. This ADR extends [ADR-005] and
[ADR-029]. It changes one thing in [ADR-005]: the fallback edge, in item 11.

The advisor has not yet seen these answers. The owner confirms them with the advisor before the first
measured run. [Q-29], [Q-30] and [Q-84] stay "to confirm with the advisor". If the advisor changes
a value, this ADR is amended.

## Decision

1. **The drop penalty is K = 5L + 2T + F + 1, times (6 minus the order's priority)** ([BR-54],
   [NFR-110]). [ADR-049] records the formula and its derivation.
2. **The 30 plans are solved by OR-Tools at 60 seconds, on seeded synthetic days** ([NFR-182]).
   OR-Tools is the solver the product runs on ([ADR-005]), and 60 seconds is also the static
   study's limit ([NFR-24]). The 30 solved plans are stored with the study's inputs, their hash goes
   in the run manifest, and a resumed or rerun study reads them and does not solve them again
   ([NFR-244]).
3. **Each insertion's time is drawn from the seed, across the plan's driving hours.**
4. **Each insertion is separate, from the same plan.** Insertions do not accumulate.
5. **Research instances are exempt from [BR-101],** as [FR-390] exempts them from [BR-50]. A plan at
   the full scale of [NFR-04] and [NFR-05] has 200 stops, and one more insertion makes 201.
6. **[NFR-118]'s exclusions are counted and reported.** The study states how many insertions
   OR-Tools could not serve from scratch and so were left out.
7. **The tests are paired and one-sided.** [NFR-24] is tested on (gap - 0.05) and [NFR-117] on
   (ours - OR-Tools - margin), the margin being 1% of OR-Tools' mean objective, proposed to the
   advisor (the owner, 2026-11-14). A one-sided test with no margin would ask whether ours is lower,
   which is stronger than "no worse". The advisor confirms the margin with the other values. The
   primary limit is 5 seconds ([NFR-25]). The test stays the Wilcoxon signed-rank test with the
   rank-biserial effect size at level 0.05 ([FR-288], [TS §8][TS-8]). This replaces [Q-84]'s plain
   test, and [FR-165]'s wording is updated to say so.
8. **60 seconds and 10 runs also bind Gehring-Homberger and PyVRP** ([NFR-24], [NFR-111], [FR-275],
   [FR-51]). The scaling study ([FR-44]) runs 3 times per size.
9. **[NFR-120] adds the product's 20-second limit** ([BR-84]) to its 1, 2, 10 and 30 seconds.
10. **A crashed study resumes.** The runner skips results it already has ([NFR-121],
    [PL §22][PL-22]).
11. **[ADR-005]'s fallback edge becomes drops and lateness under short limits.** [BR-106] keeps goods
    on their vehicle, so stability varies only through drops ([ADR-029]). If [ADR-005]'s trigger fires,
    the ADR that answers it uses this edge. The owner records it in [Q-93]'s resolution.
12. **mutmut runs once over `Problem.validate` before the first measured run,** and every surviving
    mutant becomes a test or is recorded as equivalent, with its reason. The validator judges both solvers ([BR-42], [ADR-004]), so a rule it fails
    to enforce bends every result. PIT for Java stays optional ([TS §17.1][TS-171]).

## Consequences

- **Easy:** the study reruns from one command ([NFR-121]) with every choice written down, so a
  reader can check what was compared and how.
- **Easy:** a crash does not cost days. The runner resumes from the results it holds.
- **Easy:** the claims "within 5%" and "no worse" are tested as such. A plain difference test cannot
  show that one solver is within 5% of another ([TS §8][TS-8]).
- **Hard:** the study is larger. [NFR-120]'s six limits (1, 2, 5, 10, 20 and 30 seconds) over 300
  insertions and two solvers add up to about 11 hours of solver time (300 x 2 x 68 s), before the
  from-scratch solves of [NFR-118]. That is arithmetic from the limits, not a measurement. The static
  study alone is about 28 hours ([TS §8][TS-8]), and Gehring-Homberger and the scaling study come on
  top.
- **Hard:** exempting research instances from [BR-101] means the research harness and the product
  enforce different limits. A research result at 201 stops says nothing about a refused order in the
  product.
- **Cost:** a mutmut run over `Problem.validate`, and a test or a recorded reason for each survivor. It is free software.
- **Cost:** the study's text changes: [FR-165], [NFR-24], [NFR-117] and the Statistics and "Within 5%"
  rows of [TS §8][TS-8].
- **Risk:** the advisor may change any value, and the targets would move with it.
- **Risk:** some mutants survive because they change nothing the validator can observe. Each needs a
  judgment that it is equivalent and not a missing test.
- **Risk:** under item 11, the research edge rests on drops and lateness at short limits. The edge
  has not been measured.

## Alternatives considered

- **A two-sided test on the gap.** Rejected: it answers "are they different", not "is it close
  enough" ([TS §8][TS-8]).
- **Build the 30 plans with our solver, or at a shorter limit.** Not chosen: a published plan in the
  product comes from OR-Tools, and the plan is the same input for both solvers.
- **One fixed time of day for every insertion.** Not chosen: it measures repair at one point of the
  plan only.
- **Cumulative insertions on one plan.** Not chosen: each repair would start from a plan that the
  earlier insertions changed, so the 300 would not be comparable.
- **Keep [BR-101] binding in the study.** Rejected: a plan at the scale of [NFR-05] plus one order
  has 201 stops, which the limit refuses.
- **Different limits and run counts for Gehring-Homberger and PyVRP.** Rejected: unequal terms break
  the fairness [ADR-005] requires.
- **Rerun a crashed study from the start.** Rejected: a full run takes days ([Q-93]).
- **Keep [ADR-005]'s stability fallback as written.** Not possible: [BR-106] leaves stability room
  only through drops ([ADR-029]).
- **Mutation testing of the whole codebase, or PIT now.** Not adopted: the decision covers one run
  over `Problem.validate`, and PIT stays optional ([TS §17.1][TS-171]).

## Revisit when

- The advisor changes any value or the test of items 7 to 9. Amend this ADR, and [Q-29], [Q-30] and
  [Q-84] with it.
- OR-Tools warm-started does as well as our solver at every limit in the study. [ADR-005]'s trigger
  fires, and the answering ADR takes the edge of item 11 as its starting point.
- The report of [NFR-118]'s exclusions shows that most excluded insertions share a feature, such as
  a time of day or a depot. The exclusion then biases the result, and the instance generator needs
  review.
- `Problem.validate` gains a rule that changes feasibility, such as vehicle types ([FR-118]) or
  pickups. Run mutmut again before the next measured run.

[ADR-004]: ADR-004-pluggable-solver-port.md
[ADR-005]: ADR-005-or-tools-first-research-solver-for-re-optimization.md
[ADR-029]: ADR-029-solver-objective-and-re-optimization-rules.md
[ADR-049]: ADR-049-drop-penalty-unit-k-scaled-by-priority.md
[BR-42]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-50]: ../requirements/requirements.md#routing-constraints
[BR-54]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-84]: ../requirements/requirements.md#re-optimization
[BR-101]: ../requirements/requirements.md#time-and-limits
[BR-106]: ../requirements/requirements.md#re-optimization
[FR-44]: ../requirements/requirements.md#research-and-evaluation
[FR-51]: ../requirements/requirements.md#research-and-evaluation
[FR-118]: ../requirements/requirements.md#daily-planning
[FR-165]: ../requirements/requirements.md#research-and-evaluation
[FR-275]: ../requirements/requirements.md#research-and-evaluation
[FR-288]: ../requirements/requirements.md#research-and-evaluation
[FR-390]: ../requirements/requirements.md#research-and-evaluation
[NFR-04]: ../requirements/requirements.md#nfr-04
[NFR-05]: ../requirements/requirements.md#nfr-05
[NFR-24]: ../requirements/requirements.md#nfr-24
[NFR-25]: ../requirements/requirements.md#nfr-25
[NFR-110]: ../requirements/requirements.md#nfr-110
[NFR-111]: ../requirements/requirements.md#nfr-111
[NFR-117]: ../requirements/requirements.md#nfr-117
[NFR-118]: ../requirements/requirements.md#nfr-118
[NFR-120]: ../requirements/requirements.md#nfr-120
[NFR-121]: ../requirements/requirements.md#nfr-121
[NFR-182]: ../requirements/requirements.md#nfr-182
[NFR-244]: ../requirements/requirements.md#nfr-244
[PL-22]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#22-research-harness-and-disruption-simulation
[Q-29]: ../requirements/requirements.md#open-questions
[Q-30]: ../requirements/requirements.md#open-questions
[Q-84]: ../requirements/requirements.md#open-questions
[Q-93]: ../requirements/requirements.md#open-questions
[TS-8]: ../architecture/tech_stack.md#8-research-harness
[TS-171]: ../architecture/tech_stack.md#171-by-layer

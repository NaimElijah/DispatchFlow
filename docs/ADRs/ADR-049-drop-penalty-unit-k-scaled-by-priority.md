# ADR-049: An order's drop penalty is K times 6 minus its priority, with K = 5L + 2T + F + 1

- **Status:** Accepted; amended by ADR-100 and ADR-105
- **Date:** 2026-11-13

## Context

[ADR-029] made a drop's penalty proportional to 6 minus the order's priority, and "large enough that
no order drops just to save travel" ([BR-54], [BR-55]). It gave no number. Three rules need one:

- an order is dropped only when not every order can be served ([BR-08]);
- equal penalties make a solve prefer fewer drops among orders of one priority ([BR-55]);
- a leave-at-door order's drop penalty exceeds the largest window penalty its date allows, so *time
  window* is never its drop reason ([BR-121], [ADR-032]).

[Q-93] asked how large the drop penalty is, "as a number or a formula". On 2026-11-13 a formula was
adopted. It is to be confirmed with the academic advisor before the first
measured run of the study. This ADR extends [ADR-029]. It keeps the derivation from [PL §7.4],
because the plan that holds it will be deleted.

## Decision

- **The drop unit is K = 5L + 2T + F + 1.** All terms are in seconds, the objective's scale ([PL §8]):
  - `L` is the longest shift on the order's date;
  - `T` is the largest entry of the date's unbuffered per-bucket matrix at the highest factor
    ([BR-126], [ADR-048]);
  - `F` is the fixed vehicle cost ([FR-21]).
- **An order of priority p, from 1 to 5, drops for K × (6 − p).** Priority 5 pays K and priority 1
  pays 5K.
- **The optimizer computes K for each problem,** and holds it in `PenaltyScale` with the penalty rates
  ([DL §4.9]). OR-Tools receives it as the order's disjunction penalty ([PL §8]).

### The derivation

K must be larger than the most that serving one order can cost directly. That is the sum of three
things:

- **Its detour: at most 2T.** So an order is never dropped just to save its detour ([BR-55]).
- **Its own penalty: at most 5L.** A leave-at-door stop outside its window, or in a re-optimization a
  stop past its window, pays 5 a second ([BR-120], [BR-115]), for at most L seconds. So dropping a
  door order is always worse than serving it at some time in the shift ([BR-121]).
- **A vehicle it needs alone: at most F.** So the solver never drops an order to avoid paying for a
  vehicle ([BR-08]).

The three can happen together, which is why they are added. Their maximum, `max(5L, 2T + S, F) + 1`,
is not enough. A door order that needs its own vehicle and can only be reached late in the shift costs
F + 2T plus its penalty. With L = 11 hours, T = 1 hour and F = 20,000, OR-Tools 9.15 dropped such an
order with K at that maximum (198,001, against a serving cost of 206,900) and served it with K at the
sum (225,201). Those values come from the plan's test. They are not settings.

Service time (S) is left out, because it is not a cost term in the objective.

Not covered: an insertion that pushes other door stops later. That cost depends on the whole route
and has no simple bound.

## Consequences

- **Easy:** one rule that keeps the three promises above, whatever the day's shift length, matrix and
  fixed cost. No order drops for a saving in travel, in a detour or in a vehicle.
- **Easy:** both solvers read the same unit, so the comparison does not depend on how each one was
  tuned ([BR-42]).
- **Hard:** K is large next to the travel terms. At the example values the cheapest drop, priority 5,
  costs 225,201 seconds, about 62.6 hours of travel. The solver then prefers almost any route to a
  drop, which is the intent.
- **Hard:** the 5 in 5L is the penalty rate of [BR-115] and [BR-120]. Those rates are starting values,
  tuned before the measured runs. If tuning changes either rate, K must change with it.
- **Cost:** `PenaltyScale` computes K for each problem, and the validator's objective reads the same
  value.
- **Risk:** proportional penalties let several low-priority orders outweigh one high-priority order
  ([ADR-029]).
- **Risk:** the advisor may ask for a different value or form, and the study's targets would move with
  it.
- **Risk:** the insertion that pushes other door stops later can still make a serve cost more than K.

## Alternatives considered

- **The maximum of the three terms.** Rejected: the plan's test dropped an order at that value that
  the sum served.
- **A fixed number.** Not chosen: K must exceed a sum that depends on the date's longest shift, the
  largest matrix entry and the fixed cost, so a constant is too small on some days.
- **Strict penalties,** each priority outweighing all lower ones. Rejected by [ADR-029]: it needs a
  rule for pins and locks, and a many-for-one trade is accepted.
- **Adding service time to K.** Not chosen: it is not a cost term in the objective.

## Revisit when

- The advisor asks for a different value or form, before the first measured run.
- Tuning changes the penalty rates of [BR-115] or [BR-120].
- The pilot shows an order dropped although serving it was cheaper, which would argue for a bound on
  the insertion cost left uncovered above.
- A new cost term appears that serving an order can trigger, such as a depot reload ([FR-111]).

[ADR-029]: ADR-029-solver-objective-and-re-optimization-rules.md
[ADR-032]: ADR-032-leave-at-door-windows-are-preferences.md
[ADR-048]: ADR-048-travel-times-from-buckets-and-a-buffer-with-no-live-traffic.md
[BR-08]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-42]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-54]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-55]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-115]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-120]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-121]: ../requirements/requirements.md#solving-dropping-and-validation
[BR-126]: ../requirements/requirements.md#solving-dropping-and-validation
[DL §4.9]: ../architecture/domain_layer/domain_layer_design.md#49-optimization-python
[FR-21]: ../requirements/requirements.md#daily-planning
[FR-111]: ../requirements/requirements.md#daily-planning
[PL §7.4]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#74-the-objective
[PL §8]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#8-or-tools-model
[Q-93]: ../requirements/requirements.md#open-questions

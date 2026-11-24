# ADR-098: The validator works in double precision, compares limits within 1e-6, and rounds only product travel times

- **Status:** Accepted
- **Date:** 2026-11-14

## Context

[NFR-167] requires the validator to accept published best-known Solomon solutions "under a stated
rounding convention". No accepted document stated one.

- **Solomon's benchmark.** Distance is Euclidean, travel time equals distance, and the published
  best-known solutions are computed with double precision. Their total distances are rounded to two
  decimals only in the tables. Solutions from lower-precision arithmetic are known to differ (SINTEF
  TOP, "100 customers").
- **Product travel times.** [ADR-048] rounds them up: `ceil(d × factor × (1 + buffer))`, with `d`
  OSRM's whole-second base duration. Applied to Solomon's fractional distances, the same ceiling would
  make best-known solutions infeasible.
- **Limits.** [NFR-168] requires the validator to accept a solution that lies exactly on a limit,
  which in floating point needs a tolerance.
- **The retired plan's convention.** It ran research instances in `float64` "with a stated tolerance
  of 1e-6" ([PL §7.1]), but no ADR adopted it.
- **[ADR-088]** expected that "a solver and the validator work with integers". Product inputs still
  are whole seconds; research inputs are not. That consequence no longer holds for the validator.
  ADR-088's decision, elapsed seconds in `Asia/Jerusalem`, stands.

This ADR amends [ADR-048]'s rounding for research times and for floating-point noise. The rest of
ADR-048 stands.

## Decision

Decided on 2026-11-14:

- **Double precision.** The optimizer's `Problem` holds travel times, distances and the timings it
  computes as 64-bit floating point.
- **A tolerance of 1e-6.** Every limit is inclusive, and the validator compares within 1e-6 (seconds,
  kilograms or litres):
  a start at most 1e-6 after a window's end, a load at most 1e-6 over a capacity, and a return at most
  1e-6 after a shift's end all pass ([NFR-168]).
- **Product times stay whole seconds.** `TravelTimeModel` applies [ADR-048]'s ceiling whenever it
  applies a factor other than 1 or a buffer other than 0. The ceiling is taken as `ceil(x − 1e-6)`.
  A product that should be whole can come out a hair above it in floating point: 300 × 1.3 × 1.1 is
  429.00000000000006, and a plain ceiling would give 430 instead of 429. The slack and the limit
  tolerance are two named constants, so changing one never moves the other.
- **Research times stay unrounded.** With factor 1 and buffer 0, the static study's settings
  ([NFR-110]), it returns the base time as given. Solomon instances therefore use double-precision
  Euclidean distances unrounded.
- **No road is NaN.** The contract sends -1 for a pair with no road ([ADR-052]). The domain holds it
  as NaN, and the HTTP adapter converts it. A NaN is never timed: `TravelTimeModel` refuses it.
- **The acceptance test.** The validator's test suite holds a few Solomon instances and their
  published best-known solutions, and accepts each one ([NFR-167]).

## Consequences

- **Easy:** published best-known solutions validate as published. The product's numbers are
  unchanged, because its matrix is whole seconds and the ceiling still applies.
- **Hard:** every comparison in the validator goes through the tolerance. A strict `<` or `>` on a
  limit is a bug, and the limit-case tests catch it.
- **Cost:** none.
- **Risk:** a tolerance this small never hides a real violation of a second or more. It could hide a
  sub-microsecond one, which no product input can produce.

## Alternatives considered

- **Whole seconds everywhere,** with Solomon distances scaled by 10 and truncated, as some exact
  methods do. Rejected: published best-known solutions, computed in double precision, could then be
  rejected, and the results would not be comparable with the literature.
- **Exact rational arithmetic.** Rejected: much slower, for no difference at these sizes.

## Revisit when

- A benchmark the study uses publishes solutions under another convention.
- The product's matrix stops being whole seconds.

[ADR-048]: ADR-048-travel-times-from-buckets-and-a-buffer-with-no-live-traffic.md
[ADR-052]: ADR-052-each-plan-stores-the-matrix-and-values-it-was-solved-with.md
[ADR-088]: ADR-088-israel-local-time-for-every-date-and-schedule.md
[NFR-110]: ../requirements/requirements.md#nfr-110
[NFR-167]: ../requirements/requirements.md#nfr-167
[NFR-168]: ../requirements/requirements.md#nfr-168
[PL §7.1]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#71-time-model

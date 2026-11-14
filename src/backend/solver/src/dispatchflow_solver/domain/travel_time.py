"""The travel-time model: the only place bucket factors and the buffer are applied.

A leg's travel time is the base duration scaled by the factor of the bucket it departs in and by
the buffer, rounded up to a whole second (ADR-048, FR-227, FR-321, FR-322, FR-400 to FR-403,
FR-408). A model with factor 1 and no buffer rounds nothing, so research instances keep their
unrounded Euclidean times (ADR-098, NFR-110, NFR-167).
"""

import math
from bisect import bisect_right
from dataclasses import dataclass, field
from typing import Final

from dispatchflow_solver.domain.errors import InvalidProblemError

CEILING_SLACK: Final = 1e-6
"""Seconds subtracted before rounding up, so float64 noise never adds a second (ADR-098).

Decimal factors are inexact in float64, so a product meant to be whole can overshoot it by a few
ulps: 300 x 1.3 x 1.1 gives 429.00000000000006, which a plain ceil would make 430.
"""

_NEUTRAL_FACTOR: Final = 1.0
_NO_BUFFER: Final = 0.0


@dataclass(frozen=True, slots=True)
class TravelBucket:
    """A part of the day with its own travel-time factor (FR-321).

    Attributes:
        start: The first second of the bucket, in seconds since the date's local midnight; included.
        end: The second the bucket ends at; excluded, so 07:00 to 09:30 is [07:00, 09:30) (FR-408).
        factor: What the base duration is multiplied by for a leg departing in the bucket.

    Raises:
        InvalidProblemError: If the bucket is empty or not finite, or the factor is not positive
            and finite.
    """

    start: float
    end: float
    factor: float

    def __post_init__(self) -> None:
        """Refuses an empty or endless bucket, or a factor that is not positive and finite."""
        if not (math.isfinite(self.start) and math.isfinite(self.end) and self.start < self.end):
            raise InvalidProblemError(
                f"a bucket must end after it starts, both finite: start={self.start}, "
                f"end={self.end}"
            )
        _require_positive_factor("bucket factor", self.factor)

    def contains(self, departure: float) -> bool:
        """Says whether a departure falls in the bucket, start included and end excluded."""
        return self.start <= departure < self.end


@dataclass(frozen=True, slots=True)
class TravelTimeModel:
    """Turns base durations into the three kinds of travel time (DL §10.2, ADR-048).

    - *Buffered* times, for the validator and the ETAs (FR-401).
    - *Unbuffered* per-bucket times, for the objective's travel cost (FR-402).
    - The *upper bound*, the highest factor times (1 + buffer), for the search (FR-322).

    An arc takes the factor of the bucket it departs in, for its whole length (FR-400). Every
    method takes a finite base time: a pair with no road (NaN in the matrices) is never timed, and
    each method raises `InvalidProblemError` if handed one.

    Attributes:
        buckets: The day's buckets, held as a tuple sorted by start; they may not overlap, and
            every other time is off-peak.
        off_peak_factor: The factor outside every bucket.
        buffer: The safety margin, as a fraction of the factored time (FR-227).

    Raises:
        InvalidProblemError: If a bucket is not a `TravelBucket`, buckets overlap, the off-peak
            factor is not positive and finite, or the buffer is negative or not finite.
    """

    buckets: tuple[TravelBucket, ...]
    off_peak_factor: float
    buffer: float
    _starts: tuple[float, ...] = field(init=False, repr=False, compare=False)

    def __post_init__(self) -> None:
        """Sorts a copy of the buckets and refuses overlaps, a bad factor and a bad buffer."""
        buckets = tuple(self.buckets)
        for index, bucket in enumerate(buckets):
            if not isinstance(bucket, TravelBucket):
                raise InvalidProblemError(f"buckets[{index}] is not a TravelBucket")
        ordered = tuple(sorted(buckets, key=lambda bucket: bucket.start))
        for earlier, later in zip(ordered, ordered[1:], strict=False):
            if later.start < earlier.end:
                raise InvalidProblemError(
                    f"buckets overlap: [{earlier.start}, {earlier.end}) and "
                    f"[{later.start}, {later.end})"
                )
        _require_positive_factor("off_peak_factor", self.off_peak_factor)
        if not (math.isfinite(self.buffer) and self.buffer >= 0):
            raise InvalidProblemError(f"buffer must be finite and zero or more: {self.buffer}")
        object.__setattr__(self, "buckets", ordered)
        object.__setattr__(self, "_starts", tuple(bucket.start for bucket in ordered))

    @classmethod
    def static(cls) -> TravelTimeModel:
        """Builds the static study's model: factor 1 everywhere and no buffer (NFR-110).

        Returns:
            A model whose every travel time is the base duration, unrounded.
        """
        return cls(buckets=(), off_peak_factor=_NEUTRAL_FACTOR, buffer=_NO_BUFFER)

    @property
    def highest_factor(self) -> float:
        """The largest factor of the day, off-peak included."""
        return max([self.off_peak_factor, *(bucket.factor for bucket in self.buckets)])

    def factor_at(self, departure: float) -> float:
        """Reads the factor of the bucket a departure falls in (FR-400, FR-408).

        Args:
            departure: The departure, in seconds since the date's local midnight.

        Returns:
            The bucket's factor, or the off-peak factor outside every bucket.
        """
        candidate = bisect_right(self._starts, departure) - 1
        if candidate >= 0 and self.buckets[candidate].contains(departure):
            return self.buckets[candidate].factor
        return self.off_peak_factor

    def buffered(self, base: float, departure: float) -> float:
        """The validator's and the ETAs' travel time for a leg (FR-401).

        Args:
            base: The leg's base duration in seconds.
            departure: When the leg departs, in seconds since the date's local midnight.

        Returns:
            ceil(base x factor x (1 + buffer)), or base itself at factor 1 with no buffer.
        """
        return _scaled(base, self.factor_at(departure), self.buffer)

    def unbuffered(self, base: float, departure: float) -> float:
        """The objective's travel cost for a leg: its bucket's factor, no buffer (FR-402).

        Args:
            base: The leg's base duration in seconds.
            departure: When the leg departs, in seconds since the date's local midnight.

        Returns:
            ceil(base x factor), or base itself at factor 1.
        """
        return _scaled(base, self.factor_at(departure), _NO_BUFFER)

    def upper_bound(self, base: float) -> float:
        """The search's travel time for a leg, whatever its departure (FR-322).

        Args:
            base: The leg's base duration in seconds.

        Returns:
            ceil(base x highest factor x (1 + buffer)), never below a re-timed buffered time.
        """
        return _scaled(base, self.highest_factor, self.buffer)

    def largest_unbuffered(self, base: float) -> float:
        """A leg's unbuffered time at the highest factor, which bounds its travel cost.

        The drop unit's T is this, for the largest entry of the matrix (BR-126).

        Args:
            base: The leg's base duration in seconds.

        Returns:
            ceil(base x highest factor), or base itself when the highest factor is 1.
        """
        return _scaled(base, self.highest_factor, _NO_BUFFER)


def _scaled(base: float, factor: float, buffer: float) -> float:
    # Checked in every model, the static one included, which would otherwise hand a NaN back
    # unchanged and let it reach a comparison that silently passes.
    if not math.isfinite(base):
        raise InvalidProblemError(f"a base time must be finite; the road check comes first: {base}")
    if factor == _NEUTRAL_FACTOR and buffer == _NO_BUFFER:
        return base
    return float(math.ceil(base * factor * (1 + buffer) - CEILING_SLACK))


def _require_positive_factor(name: str, factor: float) -> None:
    if not (math.isfinite(factor) and factor > 0):
        raise InvalidProblemError(f"{name} must be positive and finite: {factor}")

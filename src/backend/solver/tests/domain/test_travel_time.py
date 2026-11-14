import math

import pytest
from hypothesis import given, seed
from hypothesis import strategies as st

from dispatchflow_solver.domain.travel_time import TravelBucket, TravelTimeModel
from tests.domain.properties import PROPERTY_SEED, PROPERTY_SETTINGS

MORNING_PEAK = TravelBucket(start=7 * 3600, end=9.5 * 3600, factor=1.3)
EVENING_PEAK = TravelBucket(start=15.5 * 3600, end=18.5 * 3600, factor=1.3)


def starting_values() -> TravelTimeModel:
    # FR-321 and FR-227's starting values.
    return TravelTimeModel(buckets=(MORNING_PEAK, EVENING_PEAK), off_peak_factor=1.0, buffer=0.1)


def test_given_a_departure_at_a_bucket_start_when_factor_read_then_the_bucket_factor() -> None:
    assert starting_values().factor_at(7 * 3600) == 1.3


def test_given_a_departure_at_a_bucket_end_when_factor_read_then_off_peak() -> None:
    assert starting_values().factor_at(9.5 * 3600) == 1.0


def test_given_a_peak_departure_when_buffered_then_factor_and_buffer_rounded_up() -> None:
    # 300 x 1.3 x 1.1 is 429 exactly, though float64 computes 429.00000000000006.
    assert starting_values().buffered(300.0, 8 * 3600) == 429.0


def test_given_an_off_peak_departure_when_buffered_then_buffer_rounded_up() -> None:
    assert starting_values().buffered(101.0, 12 * 3600) == math.ceil(101 * 1.1)


def test_given_a_peak_departure_when_unbuffered_then_factor_only_rounded_up() -> None:
    assert starting_values().unbuffered(101.0, 8 * 3600) == math.ceil(101 * 1.3)


def test_given_the_starting_values_when_upper_bound_then_highest_factor_and_buffer() -> None:
    assert starting_values().upper_bound(100.0) == 143.0


def test_given_the_static_model_when_timed_then_fractional_seconds_stay_unrounded() -> None:
    model = TravelTimeModel.static()

    assert model.buffered(18.681541692269406, 0.0) == 18.681541692269406
    assert model.unbuffered(18.681541692269406, 0.0) == 18.681541692269406
    assert model.upper_bound(18.681541692269406) == 18.681541692269406


def test_given_an_off_peak_factor_of_one_when_unbuffered_then_unrounded() -> None:
    assert starting_values().unbuffered(10.5, 12 * 3600) == 10.5


def test_given_a_buffer_only_when_buffered_then_rounded_up() -> None:
    model = TravelTimeModel(buckets=(), off_peak_factor=1.0, buffer=0.1)

    assert model.buffered(10.5, 0.0) == math.ceil(10.5 * 1.1)


def test_given_the_largest_entry_when_largest_unbuffered_then_highest_factor_rounded_up() -> None:
    assert starting_values().largest_unbuffered(101.0) == math.ceil(101 * 1.3)


@pytest.mark.parametrize(
    ("buckets", "off_peak", "buffer"),
    [
        ((TravelBucket(0, 100, 1.2), TravelBucket(50, 150, 1.3)), 1.0, 0.0),
        ((), 0.0, 0.0),
        ((), 1.0, -0.1),
        ((), math.nan, 0.0),
    ],
)
def test_given_overlapping_buckets_or_bad_values_when_model_built_then_refused(
    buckets: tuple[TravelBucket, ...], off_peak: float, buffer: float
) -> None:
    with pytest.raises(ValueError):
        TravelTimeModel(buckets=buckets, off_peak_factor=off_peak, buffer=buffer)


@pytest.mark.parametrize(("start", "end", "factor"), [(10, 10, 1.3), (10, 5, 1.3), (0, 10, 0.0)])
def test_given_an_empty_bucket_or_bad_factor_when_built_then_refused(
    start: float, end: float, factor: float
) -> None:
    with pytest.raises(ValueError):
        TravelBucket(start=start, end=end, factor=factor)


factors = st.floats(min_value=0.5, max_value=3.0, allow_nan=False)
day_seconds = st.floats(min_value=0, max_value=90_000, allow_nan=False)


@st.composite
def models(draw: st.DrawFn) -> TravelTimeModel:
    cuts = sorted(draw(st.sets(day_seconds, min_size=0, max_size=6)))
    pairs = list(zip(cuts[0::2], cuts[1::2], strict=False))
    buckets = tuple(TravelBucket(start, end, draw(factors)) for start, end in pairs)
    buffer = draw(st.floats(min_value=0.0, max_value=0.5, allow_nan=False))
    return TravelTimeModel(buckets=buckets, off_peak_factor=draw(factors), buffer=buffer)


@PROPERTY_SETTINGS
@seed(PROPERTY_SEED)
@given(models(), st.floats(min_value=0, max_value=20_000, allow_nan=False), day_seconds)
def test_given_any_model_when_retimed_then_never_above_the_upper_bound(
    model: TravelTimeModel, base: float, departure: float
) -> None:
    assert model.buffered(base, departure) <= model.upper_bound(base)
    assert model.unbuffered(base, departure) <= model.largest_unbuffered(base)


@PROPERTY_SETTINGS
@seed(PROPERTY_SEED)
@given(
    st.floats(min_value=0, max_value=80_000, allow_nan=False),
    st.floats(min_value=1, max_value=10_000, allow_nan=False),
    factors,
)
def test_given_any_bucket_when_factor_read_then_start_included_and_end_excluded(
    start: float, length: float, factor: float
) -> None:
    bucket = TravelBucket(start=start, end=start + length, factor=factor)
    off_peak = factor + 1.0
    model = TravelTimeModel(buckets=(bucket,), off_peak_factor=off_peak, buffer=0.0)

    assert model.factor_at(bucket.start) == factor
    assert model.factor_at(math.nextafter(bucket.end, -math.inf)) == factor
    assert model.factor_at(bucket.end) == off_peak
    assert model.factor_at(math.nextafter(bucket.start, -math.inf)) == off_peak

import math

import pytest
from hypothesis import given, seed
from hypothesis import strategies as st

from dispatchflow_solver.domain.errors import InvalidProblemError
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
    "model", [TravelTimeModel.static(), starting_values()], ids=["static", "peak"]
)
@pytest.mark.parametrize("base", [math.nan, math.inf, -math.inf])
@pytest.mark.parametrize("kind", ["buffered", "unbuffered", "upper_bound", "largest_unbuffered"])
def test_given_a_base_time_that_is_not_finite_when_timed_then_refused(
    model: TravelTimeModel, base: float, kind: str
) -> None:
    timed = getattr(model, kind)
    arguments = (base, 8 * 3600) if kind in ("buffered", "unbuffered") else (base,)

    with pytest.raises(InvalidProblemError, match="base time must be finite"):
        timed(*arguments)


def test_given_buckets_out_of_order_in_a_list_when_built_then_held_sorted_in_a_tuple() -> None:
    model = TravelTimeModel(
        buckets=[EVENING_PEAK, MORNING_PEAK],  # type: ignore[arg-type]
        off_peak_factor=1.0,
        buffer=0.1,
    )

    assert model.buckets == (MORNING_PEAK, EVENING_PEAK)
    assert model.factor_at(8 * 3600) == 1.3
    assert model.factor_at(16 * 3600) == 1.3
    assert model.factor_at(12 * 3600) == 1.0


@pytest.mark.parametrize(
    ("buckets", "off_peak", "buffer", "match"),
    [
        pytest.param(
            (TravelBucket(0, 100, 1.2), TravelBucket(50, 150, 1.3)),
            1.0,
            0.0,
            "overlap",
            id="overlap",
        ),
        pytest.param((), 0.0, 0.0, "off_peak_factor", id="zero off-peak factor"),
        pytest.param((), math.nan, 0.0, "off_peak_factor", id="NaN off-peak factor"),
        pytest.param((), 1.0, -0.1, "buffer", id="negative buffer"),
        pytest.param(
            (), "1", 0.0, r"^off_peak_factor must be a number: '1'$", id="text off-peak factor"
        ),
        pytest.param((), 1.0, "0.1", r"^buffer must be a number: '0.1'$", id="text buffer"),
        pytest.param((), 1.0, math.inf, "buffer", id="infinite buffer"),
        pytest.param(("bucket",), 1.0, 0.0, "buckets", id="not a bucket"),
        pytest.param(
            None,
            1.0,
            0.0,
            r"^buckets must be a sequence of TravelBucket, not NoneType$",
            id="no buckets",
        ),
        pytest.param(
            5,
            1.0,
            0.0,
            r"^buckets must be a sequence of TravelBucket, not int$",
            id="a number as buckets",
        ),
    ],
)
def test_given_overlapping_buckets_or_bad_values_when_model_built_then_refused(
    buckets: tuple[TravelBucket, ...], off_peak: float, buffer: float, match: str
) -> None:
    with pytest.raises(InvalidProblemError, match=match):
        TravelTimeModel(buckets=buckets, off_peak_factor=off_peak, buffer=buffer)


@pytest.mark.parametrize(
    ("start", "end", "factor", "match"),
    [
        pytest.param(10, 10, 1.3, "end after", id="empty"),
        pytest.param(10, 5, 1.3, "end after", id="backwards"),
        pytest.param(0, math.inf, 1.3, "end after", id="endless"),
        pytest.param(0, 10, 0.0, "factor", id="zero factor"),
        pytest.param(0, 10, math.inf, "factor", id="infinite factor"),
        pytest.param("0", 10, 1.3, r"^bucket start must be a number: '0'$", id="text start"),
        pytest.param(0, True, 1.3, r"^bucket end must be a number: True$", id="bool end"),
        pytest.param(0, 10, "x", r"^bucket factor must be a number: 'x'$", id="text factor"),
    ],
)
def test_given_an_empty_bucket_or_bad_factor_when_built_then_refused(
    start: float, end: float, factor: float, match: str
) -> None:
    with pytest.raises(InvalidProblemError, match=match):
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


@PROPERTY_SETTINGS
@seed(PROPERTY_SEED)
@given(models(), day_seconds)
def test_given_any_buckets_when_factor_read_then_the_containing_buckets_factor(
    model: TravelTimeModel, departure: float
) -> None:
    containing = [bucket.factor for bucket in model.buckets if bucket.contains(departure)]

    assert model.factor_at(departure) == (containing[0] if containing else model.off_peak_factor)


@pytest.mark.parametrize("kind", ["buffered", "unbuffered", "upper_bound", "largest_unbuffered"])
def test_given_a_factor_whose_product_overflows_when_timed_then_refused(kind: str) -> None:
    model = TravelTimeModel(buckets=(), off_peak_factor=1e308, buffer=0.5)
    timed = getattr(model, kind)
    arguments = (10.0, 0.0) if kind in ("buffered", "unbuffered") else (10.0,)

    with pytest.raises(
        InvalidProblemError, match=r"^a travel time of 10\.0 base seconds is past a float: factor "
    ):
        timed(*arguments)


def test_given_number_types_when_built_then_held_as_floats() -> None:
    model = TravelTimeModel(buckets=(TravelBucket(0, 10, 2),), off_peak_factor=1, buffer=0)

    assert isinstance(model.buckets[0].start, float)
    assert isinstance(model.buckets[0].factor, float)
    assert isinstance(model.off_peak_factor, float)
    assert isinstance(model.buffer, float)

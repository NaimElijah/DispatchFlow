from typing import Final

from hypothesis import settings
from hypothesis import strategies as st

from dispatchflow_solver.domain.travel_time import TravelBucket, TravelTimeModel

# NFR-82: property tests replay from a recorded seed, keep no example database between runs and
# have no time limit per example, so a slow machine never fails them.
PROPERTY_SEED: Final = 20261114
PROPERTY_SETTINGS: Final = settings(deadline=None, database=None, max_examples=200)

points = st.floats(min_value=1, max_value=1_000, allow_nan=False)
"""A point on the builders' line, away from the depot at 0."""


@st.composite
def travel_models(draw: st.DrawFn) -> TravelTimeModel:
    """A model with an optional bucket, any factors from 0.5 to 3 and a buffer up to 0.5."""
    start = draw(st.floats(min_value=0, max_value=40_000, allow_nan=False))
    length = draw(st.floats(min_value=1, max_value=20_000, allow_nan=False))
    factor = draw(st.floats(min_value=0.5, max_value=3, allow_nan=False))
    buckets = (TravelBucket(start, start + length, factor),) if draw(st.booleans()) else ()
    return TravelTimeModel(
        buckets=buckets,
        off_peak_factor=draw(st.floats(min_value=0.5, max_value=3, allow_nan=False)),
        buffer=draw(st.floats(min_value=0, max_value=0.5, allow_nan=False)),
    )

from typing import Final

from hypothesis import settings

# NFR-82: property tests replay from a recorded seed, keep no example database between runs and
# have no time limit per example, so a slow machine never fails them.
PROPERTY_SEED: Final = 20261114
PROPERTY_SETTINGS: Final = settings(deadline=None, database=None, max_examples=200)

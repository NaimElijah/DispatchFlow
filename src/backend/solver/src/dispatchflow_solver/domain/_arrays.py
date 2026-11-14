"""Read-only NumPy copies, so a frozen value object cannot be changed through its arrays."""

from typing import Final

import numpy as np
import numpy.typing as npt

NO_INDEX: Final = -1
"""An index array's entry for "none": no location, no pin."""


def frozen_floats(values: npt.ArrayLike) -> npt.NDArray[np.float64]:
    """Copies values into a read-only float64 array.

    Args:
        values: Anything NumPy reads as numbers.

    Returns:
        A new array that refuses writes.
    """
    array = np.array(values, dtype=np.float64, copy=True)
    array.setflags(write=False)
    return array


def frozen_ints(values: npt.ArrayLike) -> npt.NDArray[np.int64]:
    """Copies values into a read-only int64 array.

    Args:
        values: Anything NumPy reads as whole numbers.

    Returns:
        A new array that refuses writes.
    """
    array = np.array(values, dtype=np.int64, copy=True)
    array.setflags(write=False)
    return array


def frozen_bools(values: npt.ArrayLike) -> npt.NDArray[np.bool_]:
    """Copies values into a read-only boolean array.

    Args:
        values: Anything NumPy reads as booleans.

    Returns:
        A new array that refuses writes.
    """
    array = np.array(values, dtype=np.bool_, copy=True)
    array.setflags(write=False)
    return array

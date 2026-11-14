"""Read-only NumPy copies, so a frozen value object cannot be changed through its arrays."""

import numpy as np
import numpy.typing as npt


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

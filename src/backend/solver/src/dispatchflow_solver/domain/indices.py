"""The index convention of the domain's arrays, and the check every whole-number input passes."""

import numbers
from typing import Final

import numpy as np

from dispatchflow_solver.domain.errors import InvalidProblemError

NO_INDEX: Final = -1
"""An index array's entry for "none": no location, no pin, no driver."""

_INT64: Final = np.iinfo(np.int64)
"""Every index and priority is held as int64, so a whole number outside it is refused."""


def require_int(name: str, value: object) -> int:
    """Refuses anything but a whole number of an integer type that fits in int64.

    NumPy would truncate 2.7 to 2 and parse "3"; bool is an int subclass, but True as a priority or
    an index is a bug. The range is checked before any message formats the value: CPython refuses
    to print an int of more than 4,300 digits, which would raise a bare ValueError instead.

    Args:
        name: The input's name, for the error.
        value: The value to check.

    Returns:
        The value as a plain int.

    Raises:
        InvalidProblemError: If the value is not an integer, is a bool, or lies outside int64.
    """
    if isinstance(value, bool | np.bool_) or not isinstance(value, numbers.Integral):
        raise InvalidProblemError(f"{name} must be an int: {value!r}")
    whole = int(value)
    if not in_int64(whole):
        raise InvalidProblemError(f"{name} is outside the int64 range")
    return whole


def require_float(name: str, value: object) -> float:
    """Refuses anything but a real number, and converts it to a float.

    A bool is a number to Python, but True as a time or a rate is a bug. A whole number too large
    for a float is refused without formatting it, as `require_int` does.

    Args:
        name: The input's name, for the error.
        value: The value to check.

    Returns:
        The value as a float, which may still be infinite or NaN for the caller to judge.

    Raises:
        InvalidProblemError: If the value is not a real number, is a bool, or does not fit a
            float.
    """
    if isinstance(value, bool | np.bool_) or not isinstance(value, numbers.Real):
        raise InvalidProblemError(f"{name} must be a number: {value!r}")
    try:
        return float(value)
    except (OverflowError, TypeError, ValueError) as error:
        raise InvalidProblemError(f"{name} must be a number that fits a float") from error


def in_int64(value: int) -> bool:
    """Says whether a whole number fits in int64, without formatting it."""
    return bool(_INT64.min <= value <= _INT64.max)

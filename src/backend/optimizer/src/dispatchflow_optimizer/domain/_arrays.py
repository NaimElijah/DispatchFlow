"""Read-only NumPy copies, so a frozen value object cannot be changed through its arrays.

It also holds the checks every array input of a problem passes, shared by `problem` and
`problem_parts`.
"""

from collections.abc import Callable, Sized
from typing import Any, Final

import numpy as np
import numpy.typing as npt

from dispatchflow_optimizer.domain.errors import InvalidProblemError
from dispatchflow_optimizer.domain.indices import NO_INDEX, require_float, require_int

_ACCEPTED_KINDS: Final[dict[type[np.generic], str]] = {
    np.bool_: "b",
    np.int64: "iuf",
    np.float64: "iuf",
}
"""The NumPy kinds each element type is converted from: bools only from bools, and numbers only
from numbers, never from text or bools, which NumPy would otherwise parse or count as 0 and 1."""

_BOOL_TYPES: Final = frozenset({bool, np.bool_})
_type_of: Final = np.frompyfunc(type, 1, 1)
_is_bool_type: Final = np.frompyfunc(_BOOL_TYPES.__contains__, 1, 1)

_INT64_MAX: Final = np.iinfo(np.int64).max
_INT64_FLOAT_BOUND: Final = 2.0**63
"""Floats from -2**63 up to, and not including, 2**63 are whole numbers int64 can hold."""


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


def converted(name: str, values: npt.ArrayLike, dtype: type[np.generic]) -> npt.NDArray[Any]:
    """Copies values into a NumPy array of one type, refusing what does not convert exactly.

    NumPy alone would truncate 2.7 to the int 2, parse "3" as a number and "no" as True, turn None
    into NaN, which the domain reads as "no road", and read True among numbers as 1. So a bool
    column takes only bools, a number column takes only numbers, and an int column takes a float
    only when it is whole and fits. A NumPy array's elements all share its dtype, so a number array
    holds no bool; any other input is checked entry by entry for one.

    Args:
        name: The input's name, for the error.
        values: Anything NumPy reads.
        dtype: The element type: `np.bool_`, `np.int64` or `np.float64`.

    Returns:
        A new, writable array.

    Raises:
        InvalidProblemError: If the values do not form an array, are of the wrong kind, such as
            text for numbers, an entry is a bool among numbers, or a value is not a number of that
            type or overflows it, such as 2.7 or 2**64 as an int64.
    """
    try:
        raw = np.asarray(values)
    except (TypeError, ValueError) as error:
        raise InvalidProblemError(f"{name} must hold {dtype.__name__} values: {error}") from error
    if raw.size == 0:
        return np.zeros(raw.shape, dtype=dtype)
    of_objects = raw.dtype.kind == "O"
    if of_objects and dtype is not np.bool_:
        raw = _numbers_from_objects(name, raw, dtype)
    if raw.dtype.kind not in _ACCEPTED_KINDS[dtype]:
        raise InvalidProblemError(f"{name} must hold {dtype.__name__} values, not {raw.dtype}")
    if dtype is not np.bool_ and not of_objects and not isinstance(values, np.ndarray):
        _refuse_bools(name, np.asarray(values, dtype=object))
    if dtype is np.int64:
        _require_int64(name, raw)
    return raw.astype(dtype)


def set_column(owner: object, prefix: str, name: str, dtype: type[np.generic]) -> None:
    """Replaces a frozen dataclass's field with a read-only one-dimensional copy.

    Args:
        owner: The dataclass, still in its `__post_init__`.
        prefix: The owner's name in errors, such as "orders".
        name: The field's name.
        dtype: The element type, as `converted` takes it.

    Raises:
        InvalidProblemError: If the field does not convert, or is not one-dimensional.
    """
    column = converted(f"{prefix}.{name}", getattr(owner, name), dtype)
    if column.ndim != 1:
        raise InvalidProblemError(f"{prefix}.{name} must be one-dimensional: shape {column.shape}")
    column.setflags(write=False)
    object.__setattr__(owner, name, column)


def require_lengths(what: str, count: int, **columns: Sized) -> None:
    """Refuses a column whose length is not the count.

    Raises:
        InvalidProblemError: Naming the first such column.
    """
    for name, column in columns.items():
        if len(column) != count:
            raise InvalidProblemError(f"{what}.{name} has {len(column)} entries, not {count}")


def refuse_first(bad: npt.NDArray[np.bool_], message: Callable[[int], str]) -> None:
    """Refuses a column with a bad entry, naming the first.

    Args:
        bad: True for each bad entry of a one-dimensional column.
        message: The error's message for the first bad index.

    Raises:
        InvalidProblemError: If any entry is bad.
    """
    if np.any(bad):
        index = int(np.flatnonzero(bad)[0])
        raise InvalidProblemError(message(index))


def require_finite_non_negative(name: str, values: npt.NDArray[np.float64]) -> None:
    """Refuses a column with an entry that is not finite or is negative.

    Raises:
        InvalidProblemError: Naming the first such entry.
    """
    refuse_first(
        ~(np.isfinite(values) & (values >= 0)),
        lambda i: f"{name}[{i}] must be finite and zero or more: {values[i]}",
    )


def require_indices(
    name: str, indices: npt.NDArray[np.int64], size: int, *, allow_none: bool = False
) -> None:
    """Refuses an index outside `size`, or below 0 unless it is -1 and `allow_none` is set.

    Raises:
        InvalidProblemError: Naming the first such index.
    """
    lowest = NO_INDEX if allow_none else 0
    refuse_first(
        (indices < lowest) | (indices >= size),
        lambda i: f"{name}[{i}] is {indices[i]}, not one of {size}",
    )


def _numbers_from_objects(
    name: str, raw: npt.NDArray[Any], dtype: type[np.generic]
) -> npt.NDArray[Any]:
    # Mixed Python values, such as None among numbers or an int past int64, each checked as a
    # scalar input is, so the error names the entry.
    require = require_int if dtype is np.int64 else require_float
    checked = [require(_entry(name, index), value) for index, value in np.ndenumerate(raw)]
    return np.array(checked, dtype=dtype).reshape(raw.shape)


def _refuse_bools(name: str, entries: npt.NDArray[np.object_]) -> None:
    # Builtins mapped over the array, not a Python predicate per entry, keep a million-entry
    # matrix to a fraction of a second. Neither bool nor np.bool_ can be subclassed, so comparing
    # types is exact.
    types = np.asarray(_type_of(entries), dtype=object)
    bools = np.asarray(_is_bool_type(types), dtype=np.bool_)
    if np.any(bools):
        index = tuple(int(i) for i in np.argwhere(bools)[0])
        raise InvalidProblemError(f"{_entry(name, index)} must be a number, not a bool")


def _require_int64(name: str, raw: npt.NDArray[Any]) -> None:
    if raw.dtype.kind == "u" and raw.max() > _INT64_MAX:
        raise InvalidProblemError(f"{name} must hold int64 values: one lies past int64")
    if raw.dtype.kind != "f":
        return
    whole = (
        np.isfinite(raw)
        & (raw == np.trunc(raw))
        & (raw >= -_INT64_FLOAT_BOUND)
        & (raw < _INT64_FLOAT_BOUND)
    )
    if not np.all(whole):
        index = tuple(int(i) for i in np.argwhere(~whole)[0])
        raise InvalidProblemError(
            f"{_entry(name, index)} must be a whole number within int64: {raw[index]}"
        )


def _entry(name: str, index: tuple[int, ...]) -> str:
    return f"{name}[{', '.join(str(i) for i in index)}]"

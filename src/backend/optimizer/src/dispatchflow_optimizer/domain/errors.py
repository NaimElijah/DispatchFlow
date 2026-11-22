"""The error every malformed problem input raises."""


class InvalidProblemError(ValueError):
    """A problem input that is malformed or contradicts itself, before any solve.

    The message names the field and the offending index or value, never a whole record.
    """

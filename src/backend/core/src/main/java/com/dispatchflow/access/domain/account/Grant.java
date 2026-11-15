package com.dispatchflow.access.domain.account;

/**
 * How far one cell of the permission matrix lets a role use a capability (FR-03). A limited grant
 * is a tick that the matrix qualifies, such as "only their own": the role may make the call, and
 * the use case checks the data limit, because the role check never sees the data (ADR-068).
 *
 * <p>The constants are declared from the narrowest to the widest, which {@link #widest} relies
 * on.
 */
public enum Grant {
    /** An empty cell: the role may not. */
    NONE,
    /** A cell that starts "Only": the role may, within the limit the cell names. */
    LIMITED,
    /** A tick: the role may, without a limit of its own beyond the rows the cell cites. */
    FULL;

    /**
     * The wider of two grants, because an account may do everything any of its roles allows
     * (FR-293).
     *
     * @param other the other grant
     * @return whichever of the two allows more
     */
    public Grant widest(Grant other) {
        return compareTo(other) >= 0 ? this : other;
    }
}

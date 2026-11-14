package com.dispatchflow.shared;

import java.util.Objects;
import java.util.UUID;

/**
 * Names a person's account: the sign-in identity that holds their roles (DL §4.1). A driver is
 * keyed by it, and an event's {@code by} is one.
 *
 * <p>A typed ID, so one kind of ID can't be passed for another (DL §3.3). The UUID's version is not
 * checked: {@code IdGenerator} makes version 7, and the domain only carries it.
 *
 * @param value the UUID
 */
public record AccountId(UUID value) {

    /** Checks the UUID is present. */
    public AccountId {
        Objects.requireNonNull(value, "value");
    }

    /**
     * Wraps a UUID.
     *
     * @param value the UUID
     * @return the ID
     */
    public static AccountId of(UUID value) {
        return new AccountId(value);
    }
}

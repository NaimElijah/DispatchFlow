package com.dispatchflow.orders.adapter.out.persistence;

import com.dispatchflow.shared.StoredStateInvalid;
import java.util.Optional;

/**
 * Finds the {@link StoredStateInvalid} a converter threw. Hibernate wraps a converter's exception,
 * and Spring wraps that again, so the ITs look for it in the cause chain (ADR-127).
 */
final class StoredStates {

    private StoredStates() {}

    /**
     * The first {@link StoredStateInvalid} in a chain of causes.
     *
     * @param thrown what the load threw
     * @return the failure, or empty when the chain holds none
     */
    static Optional<StoredStateInvalid> invalidIn(Throwable thrown) {
        for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
            if (cause instanceof StoredStateInvalid invalid) {
                return Optional.of(invalid);
            }
        }
        return Optional.empty();
    }
}

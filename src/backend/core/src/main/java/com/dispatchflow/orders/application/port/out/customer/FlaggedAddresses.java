package com.dispatchflow.orders.application.port.out.customer;

import com.dispatchflow.orders.application.port.out.FlaggedCustomer;
import com.dispatchflow.shared.PersonalData;
import java.util.List;

/**
 * Every active customer whose address is flagged (FR-63, FR-149, FR-216): what the dispatcher
 * checks before a solve. Each holds personal data, so the list prints redacted.
 *
 * <p>Built in M7.
 *
 * @param customers the flagged customers, each once, ordered by name
 */
public record FlaggedAddresses(List<FlaggedCustomer> customers) implements PersonalData {

    /** Copies the customers. */
    public FlaggedAddresses {
        customers = List.copyOf(customers);
    }

    /**
     * A form that hides the customers.
     *
     * @return {@code FlaggedAddresses[redacted]}
     */
    @Override
    public String toString() {
        return "FlaggedAddresses[redacted]";
    }
}

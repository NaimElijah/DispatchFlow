package com.dispatchflow.orders.application.port.out;

import com.dispatchflow.shared.PersonalData;
import java.util.List;

/**
 * A list of customers, as a search finds them (FR-134, FR-259) or as the likely duplicates of a
 * customer being saved (FR-212). Each holds personal data, so the list prints redacted.
 *
 * <p>Built in M2.
 *
 * @param customers the customers, each once, ordered by name
 */
public record CustomerList(List<CustomerSummary> customers) implements PersonalData {

    /** Copies the customers. */
    public CustomerList {
        customers = List.copyOf(customers);
    }

    /**
     * A form that hides the customers.
     *
     * @return {@code CustomerList[redacted]}
     */
    @Override
    public String toString() {
        return "CustomerList[redacted]";
    }
}

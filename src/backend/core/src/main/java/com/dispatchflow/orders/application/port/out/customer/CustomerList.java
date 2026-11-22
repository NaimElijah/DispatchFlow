package com.dispatchflow.orders.application.port.out.customer;

import com.dispatchflow.orders.application.port.out.CustomerSummary;
import com.dispatchflow.shared.PersonalData;
import java.util.List;

/**
 * A list of customers, as a search finds them (FR-134, FR-259) or as the likely duplicates of a
 * customer being saved (FR-212). Each holds personal data, so the list prints redacted.
 *
 * <p>Built in M2.
 *
 * @param customers the customers, each once, ordered by name
 * @param more whether more customers matched than the list holds, so a search was cut at its
 *     limit and the dispatcher types more to narrow it; {@code false} for the likely duplicates,
 *     which are never cut
 */
public record CustomerList(List<CustomerSummary> customers, boolean more) implements PersonalData {

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

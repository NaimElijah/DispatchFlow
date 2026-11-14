package com.dispatchflow.orders.domain;

import com.dispatchflow.orders.domain.OrdersError.AmbiguousAddress;
import com.dispatchflow.shared.CustomerId;
import com.dispatchflow.shared.RuleViolation;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The active customers whose normalized address equals an import row's (FR-60, FR-310 to FR-312):
 * none, one or several. The application layer looks them up and builds the match with {@link
 * #of(List)}; several can't be used.
 */
public sealed interface CustomerMatch {

    /**
     * An active customer found for an address, with the defaults its new orders take.
     *
     * @param id the customer
     * @param defaults its default window and service time (BR-49, BR-124)
     */
    record Candidate(CustomerId id, OrderDefaults defaults) {

        /** Checks both parts are given. */
        public Candidate {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(defaults, "defaults");
        }
    }

    /**
     * Builds the match from the active customers found.
     *
     * @param found the active customers with the row's normalized address
     * @return {@link NoCustomer}, {@link OneCustomer} or {@link SeveralCustomers}
     */
    static CustomerMatch of(List<Candidate> found) {
        return switch (found.size()) {
            case 0 -> new NoCustomer();
            case 1 -> new OneCustomer(found.get(0));
            default -> new SeveralCustomers(found.stream().map(Candidate::id).toList());
        };
    }

    /**
     * The customer to use, if one exists (FR-60).
     *
     * @return the one customer, or empty when none matches and the row creates one (FR-311)
     * @throws RuleViolation with {@link AmbiguousAddress} when several match (FR-312)
     */
    Optional<Candidate> customer();

    /** No active customer has the address, so the row creates one (FR-311). */
    record NoCustomer() implements CustomerMatch {

        @Override
        public Optional<Candidate> customer() {
            return Optional.empty();
        }
    }

    /**
     * Exactly one active customer has the address.
     *
     * @param candidate the customer
     */
    record OneCustomer(Candidate candidate) implements CustomerMatch {

        /** Checks the customer is given. */
        public OneCustomer {
            Objects.requireNonNull(candidate, "candidate");
        }

        @Override
        public Optional<Candidate> customer() {
            return Optional.of(candidate);
        }
    }

    /**
     * Several active customers share the address (FR-312).
     *
     * @param customers the customers, ordered by ID
     */
    record SeveralCustomers(List<CustomerId> customers) implements CustomerMatch {

        /** Copies the customers in a fixed order. */
        public SeveralCustomers {
            customers = customers.stream()
                    .sorted(Comparator.comparing(CustomerId::value))
                    .toList();
        }

        @Override
        public Optional<Candidate> customer() {
            throw new RuleViolation(new AmbiguousAddress(customers));
        }
    }
}

package com.dispatchflow.orders.domain;

import static com.dispatchflow.orders.domain.CustomerBuilder.NAME;
import static com.dispatchflow.orders.domain.CustomerBuilder.PHONE;
import static com.dispatchflow.orders.domain.CustomerBuilder.aCustomer;
import static com.dispatchflow.orders.domain.OrdersTestData.CUSTOMER;
import static com.dispatchflow.orders.domain.OrdersTestData.DEFAULTS;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** A customer as an import row's candidate (FR-60, FR-310): only an active customer is one. */
class CustomerCandidateTest {

    private static final Instant LAST_YEAR = Instant.parse("2025-06-01T09:00:00Z");

    @Test
    void GivenAnActiveCustomer_WhenAskedForItsCandidate_ThenItIsItsIdContactAndDefaults() {
        assertThat(aCustomer().registered().asCandidate())
                .contains(new CustomerMatch.Candidate(CUSTOMER, NAME, PHONE, DEFAULTS));
    }

    @Test
    void GivenARetiredCustomer_WhenAskedForItsCandidate_ThenThereIsNone() {
        assertThat(aCustomer().retiredAt(LAST_YEAR).restored().asCandidate()).isEmpty();
    }

    @Test
    void GivenAnAnonymizedCustomer_WhenAskedForItsCandidate_ThenThereIsNone() {
        assertThat(aCustomer().anonymizedAt(LAST_YEAR).restored().asCandidate()).isEmpty();
    }
}

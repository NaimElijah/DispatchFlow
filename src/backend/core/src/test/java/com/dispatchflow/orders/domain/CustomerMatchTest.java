package com.dispatchflow.orders.domain;

import static com.dispatchflow.orders.domain.OrdersTestData.DEFAULTS;
import static com.dispatchflow.orders.domain.OrdersTestData.customerId;
import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.orders.domain.OrdersError.AmbiguousAddress;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link CustomerMatch}: none, one or several active customers for an address (FR-60, FR-310 to FR-312). */
class CustomerMatchTest {

    private static final CustomerMatch.Candidate FIRST = new CustomerMatch.Candidate(customerId(1), DEFAULTS);
    private static final CustomerMatch.Candidate SECOND = new CustomerMatch.Candidate(customerId(2), DEFAULTS);

    @Test
    void GivenNoActiveCustomer_WhenMatched_ThenNoneIsUsedAndTheRowCreatesOne() {
        CustomerMatch match = CustomerMatch.of(List.of());

        assertThat(match).isEqualTo(new CustomerMatch.NoCustomer());
        assertThat(match.customer()).isEmpty();
    }

    @Test
    void GivenOneActiveCustomer_WhenMatched_ThenItIsUsed() {
        assertThat(CustomerMatch.of(List.of(FIRST)).customer()).contains(FIRST);
    }

    @Test
    void GivenSeveralActiveCustomersInAnyOrder_WhenTheMatchIsUsed_ThenAmbiguousAddress() {
        assertRefused(
                () -> CustomerMatch.of(List.of(SECOND, FIRST)).customer(),
                new AmbiguousAddress(List.of(customerId(1), customerId(2))));
    }
}

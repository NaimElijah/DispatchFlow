package com.dispatchflow.orders.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.shared.DomainError;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins the Orders context's error codes, which both apps translate (NFR-144). That each error has a
 * refusal test is checked for every module by {@code ErrorRefusalTestsTest}.
 */
class OrdersErrorTest {

    @Test
    void GivenTheOrdersErrors_WhenTheirCodesAreListed_ThenTheyAreThePinnedCodes() {
        List<String> codes = Arrays.stream(OrdersError.class.getPermittedSubclasses())
                .map(type -> DomainError.codeOf(type.asSubclass(DomainError.class)))
                .sorted()
                .toList();

        assertThat(codes)
                .containsExactly(
                        "ALREADY_ON_THAT_DATE",
                        "AMBIGUOUS_ADDRESS",
                        "BLANK_ADDRESS",
                        "BLANK_CONTACT_NAME",
                        "CUSTOMER_ANONYMIZED",
                        "CUSTOMER_IN_USE",
                        "CUSTOMER_RETIRED",
                        "DATE_FULL",
                        "DATE_IN_PAST",
                        "DUPLICATE_ORDER_REFERENCE",
                        "ENCODING_NOT_CONFIRMED",
                        "IMPORT_ALREADY_CONFIRMED",
                        "IMPORT_DISCARDED",
                        "IMPORT_EXPIRED",
                        "IMPORT_HAS_ERRORS",
                        "IMPORT_NOT_YET_EXPIRED",
                        "INVALID_DAILY_WINDOW",
                        "NEW_CUSTOMER_NEEDS_NAME_AND_PHONE",
                        "NON_POSITIVE_SERVICE_TIME",
                        "NO_SERVICE_TIME",
                        "ONLY_FAILED_OR_DROPPED_ORDERS_ARE_PUT_BACK",
                        "ORDER_ALREADY_CANCELLED",
                        "ORDER_ANONYMIZED",
                        "OUTSIDE_SERVICE_AREA",
                        "RETENTION_NOT_OVER",
                        "ROW_REFUSED",
                        "STOP_ALREADY_HAS_FACT",
                        "WINDOW_NOT_ON_DATE");
    }
}

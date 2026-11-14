package com.dispatchflow.messaging.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.shared.DomainError;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins Messaging's error codes, which both apps translate (NFR-144). That each error has a refusal
 * test is checked for every module by {@code ErrorRefusalTestsTest}.
 */
class MessagingErrorTest {

    @Test
    void GivenTheMessagingErrors_WhenTheirCodesAreListed_ThenTheyAreThePinnedCodes() {
        List<String> codes = Arrays.stream(MessagingError.class.getPermittedSubclasses())
                .map(type -> DomainError.codeOf(type.asSubclass(DomainError.class)))
                .sorted()
                .toList();

        assertThat(codes)
                .containsExactly(
                        "BLANK_COMMENT",
                        "DISPATCH_PHONE_NOT_ISRAELI",
                        "DISPATCH_PHONE_TOO_LONG",
                        "MESSAGE_NOT_CLAIMABLE",
                        "MESSAGE_NOT_SENDING",
                        "RATING_OUT_OF_RANGE",
                        "RETENTION_NOT_OVER",
                        "UNKNOWN_MESSAGE",
                        "UNUSABLE_COMPANY_NAME");
    }
}

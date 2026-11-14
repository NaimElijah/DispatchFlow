package com.dispatchflow.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

/**
 * The one way every domain test asserts a refusal, so a refusal test reads the same in every
 * module and always compares the whole named error, fields included.
 */
public final class DomainAssertions {

    private DomainAssertions() {}

    /**
     * Asserts that a call is refused with exactly one named error.
     *
     * @param call the call that must refuse
     * @param expected the error it must carry, compared by value
     */
    public static void assertRefused(ThrowingCallable call, DomainError expected) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(
                        RuleViolation.class,
                        violation -> assertThat(violation.error()).isEqualTo(expected));
    }

    /**
     * Asserts that a call is refused with a summary and every refusal under it, in order.
     *
     * @param call the call that must refuse
     * @param summary the summary error it must carry
     * @param errors the refusals it must list, compared by value and in order
     */
    public static void assertAllRefused(
            ThrowingCallable call, DomainError summary, List<? extends DomainError> errors) {
        assertThatThrownBy(call).isInstanceOfSatisfying(RuleViolations.class, violations -> {
            assertThat(violations.summary()).isEqualTo(summary);
            assertThat(violations.errors()).containsExactlyElementsOf(errors);
        });
    }
}

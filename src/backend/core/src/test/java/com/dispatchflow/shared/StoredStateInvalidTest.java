package com.dispatchflow.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StoredStateInvalidTest {

    @Test
    void GivenStoredFieldsAValueAccepts_WhenRestored_ThenTheValueIsReturned() {
        assertThat(StoredStateInvalid.whileRestoring(Priority.class, () -> Priority.of(2)))
                .isEqualTo(Priority.of(2));
    }

    @Test
    void GivenStoredFieldsAValueRefuses_WhenRestored_ThenStoredStateInvalidNamesTheErrorCode() {
        assertThatThrownBy(() -> StoredStateInvalid.whileRestoring(Priority.class, () -> Priority.of(9)))
                .isInstanceOfSatisfying(StoredStateInvalid.class, invalid -> {
                    assertThat(invalid.subject()).isEqualTo("Priority");
                    assertThat(invalid.detail()).contains("PRIORITY_OUT_OF_RANGE");
                    assertThat(invalid.getCause()).isInstanceOf(RuleViolation.class);
                    assertThat(invalid.getMessage()).doesNotContain("9");
                });
    }

    @Test
    void GivenAFailureThatIsNotARefusal_WhenRestored_ThenItPassesThroughUnchanged() {
        StoredStateInvalid damaged = new StoredStateInvalid(Version.class, "a version is never below zero");

        assertThatThrownBy(() -> StoredStateInvalid.whileRestoring(Version.class, () -> {
                    throw damaged;
                }))
                .isSameAs(damaged);
    }
}

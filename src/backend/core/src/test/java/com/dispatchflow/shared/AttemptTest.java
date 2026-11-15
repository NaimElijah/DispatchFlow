package com.dispatchflow.shared;

import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.shared.SharedKernelError.InvalidAttempt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AttemptTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 2, Integer.MAX_VALUE})
    void GivenOneOrMore_WhenAnAttemptIsBuilt_ThenItIsKept(int number) {
        assertThat(Attempt.of(number).number()).isEqualTo(number);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void GivenANumberBelowOne_WhenAnAttemptIsBuilt_ThenInvalidAttempt(int number) {
        assertRefused(() -> Attempt.of(number), new InvalidAttempt(number));
    }

    @Test
    void GivenAStoredNumberBelowOne_WhenRestored_ThenStoredStateInvalidNamesTheErrorCode() {
        assertThatThrownBy(() -> StoredStateInvalid.whileRestoring(Attempt.class, () -> Attempt.of(0)))
                .isInstanceOfSatisfying(StoredStateInvalid.class, invalid -> {
                    assertThat(invalid.subject()).isEqualTo("Attempt");
                    assertThat(invalid.detail()).contains("INVALID_ATTEMPT");
                });
    }

    @Test
    void GivenANewOrder_WhenItsFirstAttemptIsRead_ThenItIsNumberedOne() {
        assertThat(Attempt.FIRST).isEqualTo(Attempt.of(1));
    }

    @Test
    void GivenAnAttempt_WhenTheNextIsTaken_ThenItIsNumberedOneHigher() {
        assertThat(Attempt.FIRST.next()).isEqualTo(Attempt.of(2));
        assertThat(Attempt.of(Integer.MAX_VALUE - 1).next()).isEqualTo(Attempt.of(Integer.MAX_VALUE));
    }

    @Test
    void GivenTheLastNumberAnIntHolds_WhenTheNextIsTaken_ThenTheOverflowIsRefused() {
        Attempt last = Attempt.of(Integer.MAX_VALUE);

        assertThatThrownBy(last::next).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void GivenTwoAttempts_WhenCompared_ThenTheEarlierComesFirst() {
        assertThat(Attempt.FIRST).isLessThan(Attempt.of(2));
        assertThat(Attempt.of(3)).isGreaterThan(Attempt.of(2));
        assertThat(Attempt.of(2)).isEqualByComparingTo(Attempt.of(2));
    }
}

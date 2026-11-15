package com.dispatchflow.shared;

import static com.dispatchflow.shared.DomainAssertions.assertAllRefused;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.dispatchflow.shared.SharedKernelError.InvalidPhoneNumber;
import com.dispatchflow.shared.SharedKernelError.PriorityOutOfRange;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuleViolationsTest {

    record RowsRefused() implements DomainError {}

    @Test
    void GivenSeveralRefusals_WhenThrownIfAny_ThenAllAreListedInOrderUnderTheSummary() {
        List<DomainError> refusals = List.of(new PriorityOutOfRange(0), new InvalidPhoneNumber());

        assertAllRefused(() -> RuleViolations.throwIfAny(new RowsRefused(), refusals), new RowsRefused(), refusals);
    }

    @Test
    void GivenNoRefusals_WhenThrownIfAny_ThenNothingIsThrown() {
        assertThatCode(() -> RuleViolations.throwIfAny(new RowsRefused(), List.of()))
                .doesNotThrowAnyException();
    }

    @Test
    void GivenTheCallersListChangesAfterward_WhenTheRefusalsAreRead_ThenTheyAreUnchanged() {
        List<DomainError> refusals = new ArrayList<>(List.of(new InvalidPhoneNumber()));
        RuleViolations thrown = catchThrowableOfType(
                RuleViolations.class, () -> RuleViolations.throwIfAny(new RowsRefused(), refusals));

        refusals.add(new PriorityOutOfRange(9));

        assertThat(thrown.errors()).containsExactly(new InvalidPhoneNumber());
    }

    @Test
    void GivenRefusals_WhenThrown_ThenTheMessageIsTheSummaryCodeAlone() {
        RuleViolations thrown = catchThrowableOfType(
                RuleViolations.class,
                () -> RuleViolations.throwIfAny(new RowsRefused(), List.of(new InvalidPhoneNumber())));

        assertThat(thrown).hasMessage("ROWS_REFUSED");
    }
}

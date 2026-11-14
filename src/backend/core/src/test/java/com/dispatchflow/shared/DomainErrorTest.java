package com.dispatchflow.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.shared.SharedKernelError.NonexistentLocalTime;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class DomainErrorTest {

    record DateFull() implements DomainError {}

    record NotAnActiveDriver() implements DomainError {}

    record SMSGatewayDown() implements DomainError {}

    record Step2Failed() implements DomainError {}

    record Refused() implements DomainError {}

    @Test
    void GivenACamelCaseName_WhenTheCodeIsDerived_ThenItIsUpperSnakeCase() {
        assertThat(DomainError.codeOf(NonexistentLocalTime.class)).isEqualTo("NONEXISTENT_LOCAL_TIME");
        assertThat(DomainError.codeOf(DateFull.class)).isEqualTo("DATE_FULL");
        assertThat(DomainError.codeOf(NotAnActiveDriver.class)).isEqualTo("NOT_AN_ACTIVE_DRIVER");
    }

    @Test
    void GivenARunOfCapitals_WhenTheCodeIsDerived_ThenTheRunStaysOneWord() {
        assertThat(DomainError.codeOf(SMSGatewayDown.class)).isEqualTo("SMS_GATEWAY_DOWN");
    }

    @Test
    void GivenADigitBeforeACapital_WhenTheCodeIsDerived_ThenTheCapitalStartsAWord() {
        assertThat(DomainError.codeOf(Step2Failed.class)).isEqualTo("STEP2_FAILED");
    }

    @Test
    void GivenOneWord_WhenTheCodeIsDerived_ThenItIsThatWord() {
        assertThat(DomainError.codeOf(Refused.class)).isEqualTo("REFUSED");
    }

    @Test
    void GivenAnError_WhenItsCodeIsRead_ThenItIsDerivedFromItsType() {
        DomainError error = new NonexistentLocalTime(LocalDateTime.of(2027, 3, 26, 2, 30));

        assertThat(error.code()).isEqualTo("NONEXISTENT_LOCAL_TIME");
    }

    @Test
    void GivenAnError_WhenRefusedWith_ThenTheMessageIsTheCodeAlone() {
        RuleViolation violation = new RuleViolation(new NonexistentLocalTime(LocalDateTime.of(2027, 3, 26, 2, 30)));

        assertThat(violation.getMessage()).isEqualTo("NONEXISTENT_LOCAL_TIME");
    }
}

package com.dispatchflow.shared;

import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.dispatchflow.shared.SharedKernelError.InvalidPhoneNumber;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PhoneNumberTest {

    private static final String MOBILE_E164 = "+972523456789";

    @ParameterizedTest
    @ValueSource(strings = {"052-345-6789", "0523456789", "+972 52-345-6789", "+972523456789"})
    void GivenAnIsraeliMobileInAnyForm_WhenParsed_ThenItIsStoredInE164(String text) {
        assertThat(PhoneNumber.parse(text).e164()).isEqualTo(MOBILE_E164);
    }

    @Test
    void GivenAnIsraeliMobile_WhenAskedIfMessagesMayGoToIt_ThenTheyMay() {
        assertThat(PhoneNumber.parse("052-345-6789").isIsraeliMobile()).isTrue();
    }

    @Test
    void GivenAnIsraeliLandline_WhenParsed_ThenItIsValidButNotAMobile() {
        PhoneNumber landline = PhoneNumber.parse("03-612-3456");

        assertThat(landline.e164()).isEqualTo("+97236123456");
        assertThat(landline.isIsraeliMobile()).isFalse();
    }

    @Test
    void GivenAForeignMobile_WhenParsed_ThenItIsValidButNotAnIsraeliMobile() {
        PhoneNumber foreign = PhoneNumber.parse("+44 7400 123456");

        assertThat(foreign.e164()).isEqualTo("+447400123456");
        assertThat(foreign.isIsraeliMobile()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"not a phone", "", "123", "054-123", "054-123-4567", "+972 52-345-6789-89012"})
    void GivenTextThatIsNotAValidNumber_WhenParsed_ThenInvalidPhoneNumber(String text) {
        assertRefused(() -> PhoneNumber.parse(text), new InvalidPhoneNumber());
    }

    @Test
    void GivenAPhoneNumber_WhenPrinted_ThenTheNumberIsHidden() {
        assertThat(PhoneNumber.parse("052-345-6789").toString())
                .doesNotContain("523456789")
                .isEqualTo("PhoneNumber[redacted]");
    }

    @Test
    void GivenAnInvalidNumber_WhenRefused_ThenTheMessageDoesNotQuoteIt() {
        RuleViolation refusal = catchThrowableOfType(RuleViolation.class, () -> PhoneNumber.parse("054-123"));

        assertThat(refusal.getMessage()).isEqualTo("INVALID_PHONE_NUMBER");
        assertThat(refusal.getCause()).isNull();
    }

    @Test
    void GivenTheSameNumberInTwoForms_WhenCompared_ThenTheyAreEqual() {
        assertThat(PhoneNumber.parse("052-345-6789")).isEqualTo(new PhoneNumber("+972523456789"));
    }
}

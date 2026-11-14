package com.dispatchflow.access.domain;

import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.dispatchflow.access.domain.AccessError.InvalidEmail;
import com.dispatchflow.shared.RuleViolation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class EmailAddressTest {

    @ParameterizedTest
    @CsvSource({
        "dana@example.com, dana@example.com",
        "'  Dana@Example.COM  ', dana@example.com",
        "DANA.COHEN+route@MAIL.Example.co.il, dana.cohen+route@mail.example.co.il",
    })
    void GivenAnAddressWithSpacesAndCapitals_WhenParsed_ThenItIsTrimmedAndAsciiFolded(String typed, String canonical) {
        assertThat(EmailAddress.parse(typed).value()).isEqualTo(canonical);
    }

    @Test
    void GivenAnInternationalDomain_WhenParsed_ThenTheDomainIsInPunycode() {
        assertThat(EmailAddress.parse("dana@דוגמה.ישראל").value()).isEqualTo("dana@xn--6dbbec0c.xn--4dbrk0ce");
    }

    @Test
    void GivenANonAsciiLocalPart_WhenParsed_ThenOnlyAsciiLettersAreFolded() {
        assertThat(EmailAddress.parse("ÉDana@example.com").value()).isEqualTo("Édana@example.com");
    }

    @Test
    void GivenTwoFormsOfOneAddress_WhenCompared_ThenTheyAreEqual() {
        assertThat(EmailAddress.parse(" DANA@example.com")).isEqualTo(EmailAddress.parse("dana@EXAMPLE.com"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "   ",
                "dana",
                "@example.com",
                "dana@",
                "dana@@example.com",
                "da@na@example.com",
                "da na@example.com",
                "dana@exa mple.com",
                "dana@-example.com",
                "dana@example..com",
                "dana@example.com.",
                "dana@exa_mple.com",
                "dana\t@example.com",
                ".dana@example.com",
                "dana.@example.com",
                "da..na@example.com",
                "\"dana cohen\"@example.com",
                "\"dana\"@example.com",
                "da(na)@example.com",
                "da,na@example.com",
                "da[na]@example.com",
                "dana@localhost",
                "dana@com"
            })
    void GivenTextThatIsNotAnAddress_WhenParsed_ThenInvalidEmail(String text) {
        assertRefused(() -> EmailAddress.parse(text), new InvalidEmail());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"dana@stra\u00dfe.de", "dana@\u03bf\u03c2.gr", "dana@ex\u200Cample.com", "dana@ex\u200Dample.com"
            })
    void GivenADomainWithAnIdnaDeviationCharacter_WhenParsed_ThenInvalidEmail(String text) {
        assertRefused(() -> EmailAddress.parse(text), new InvalidEmail());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "d.a.n.a@example.com",
                "dana+route_1@example.com",
                "o'brien@example.com",
                "a!#$%&*=?^`{|}~-/@example.com"
            })
    void GivenADotAtomLocalPart_WhenParsed_ThenItIsAccepted(String text) {
        assertThat(EmailAddress.parse(text).value()).isEqualTo(text);
    }

    @Test
    void GivenAHebrewLocalPart_WhenParsed_ThenItIsAccepted() {
        assertThat(EmailAddress.parse("דנה@example.com").value()).isEqualTo("דנה@example.com");
    }

    @Test
    void GivenAnInternationalAddressUnder254CharactersWhosePunycodeIsOver254_WhenParsed_ThenInvalidEmail() {
        String label = "אבגדהוזחטיכלמנסעפצקרשתאבגד";
        String domain = String.join(".", label, label, label, label, label, label, label) + ".com";
        String address = "dana@" + domain;

        assertThat(address.length()).isLessThan(254);
        assertRefused(() -> EmailAddress.parse(address), new InvalidEmail());
    }

    @Test
    void GivenALocalPartOver64Characters_WhenParsed_ThenInvalidEmail() {
        assertRefused(() -> EmailAddress.parse("a".repeat(65) + "@example.com"), new InvalidEmail());
    }

    @Test
    void GivenALocalPartOf64Characters_WhenParsed_ThenItIsAccepted() {
        assertThat(EmailAddress.parse("a".repeat(64) + "@example.com").value()).startsWith("a".repeat(64));
    }

    @Test
    void GivenAnAddressOf254Characters_WhenParsed_ThenItIsAccepted() {
        String address = "dana@" + domainOfLastLabel(57);

        assertThat(address).hasSize(254);
        assertThat(EmailAddress.parse(address).value()).isEqualTo(address);
    }

    @Test
    void GivenAnAddressOf255Characters_WhenParsed_ThenInvalidEmail() {
        String address = "dana@" + domainOfLastLabel(58);

        assertThat(address).hasSize(255);
        assertRefused(() -> EmailAddress.parse(address), new InvalidEmail());
    }

    private static String domainOfLastLabel(int lastLabelLength) {
        return String.join(".", "a".repeat(63), "b".repeat(63), "c".repeat(63), "d".repeat(lastLabelLength));
    }

    @Test
    void GivenAnInvalidAddress_WhenRefused_ThenTheMessageDoesNotQuoteIt() {
        RuleViolation refusal = catchThrowableOfType(RuleViolation.class, () -> EmailAddress.parse("dana@-x.com"));

        assertThat(refusal.getMessage()).isEqualTo("INVALID_EMAIL");
        assertThat(refusal.getCause()).isNull();
    }

    @Test
    void GivenAnAddress_WhenPrinted_ThenItIsHidden() {
        assertThat(EmailAddress.parse("dana@example.com").toString())
                .isEqualTo("EmailAddress[redacted]")
                .doesNotContain("dana");
    }
}

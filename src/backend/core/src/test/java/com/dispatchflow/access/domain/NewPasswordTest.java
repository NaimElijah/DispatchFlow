package com.dispatchflow.access.domain;

import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.access.domain.AccessError.PasswordTooCommon;
import com.dispatchflow.access.domain.AccessError.PasswordTooShort;
import java.util.List;
import org.junit.jupiter.api.Test;

class NewPasswordTest {

    private static final CommonPasswords COMMON = CommonPasswords.of(List.of("password123", "12345678"));
    private static final String TRUCK = "🚚";

    @Test
    void GivenEightCharactersNotOnTheList_WhenChecked_ThenTheyAreTheNewPassword() {
        assertThat(NewPassword.of("tz7!kw0q", COMMON).text()).isEqualTo("tz7!kw0q");
    }

    @Test
    void GivenSevenCharacters_WhenChecked_ThenPasswordTooShort() {
        assertRefused(() -> NewPassword.of("tz7!kw0", COMMON), new PasswordTooShort(8));
    }

    @Test
    void GivenCharactersOutsideTheBasicPlane_WhenChecked_ThenEachCountsAsOneCharacter() {
        assertThat(NewPassword.of(TRUCK.repeat(8), COMMON).text()).isEqualTo(TRUCK.repeat(8));
        assertRefused(() -> NewPassword.of(TRUCK.repeat(7), COMMON), new PasswordTooShort(8));
    }

    @Test
    void GivenAPasswordOnTheList_WhenChecked_ThenPasswordTooCommon() {
        assertRefused(() -> NewPassword.of("password123", COMMON), new PasswordTooCommon());
    }

    @Test
    void GivenAPasswordDifferingFromAnEntryOnlyInCase_WhenChecked_ThenItIsAccepted() {
        assertThat(NewPassword.of("Password123", COMMON).text()).isEqualTo("Password123");
    }

    @Test
    void GivenANewPassword_WhenPrinted_ThenItIsHidden() {
        assertThat(NewPassword.of("tz7!kw0q", COMMON).toString())
                .isEqualTo("NewPassword[redacted]")
                .doesNotContain("tz7");
    }

    @Test
    void GivenTheCommonList_WhenPrinted_ThenOnlyItsCountShows() {
        assertThat(COMMON.toString()).isEqualTo("CommonPasswords[2 entries]");
    }
}

package com.dispatchflow.access.domain;

import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.access.domain.AccessError.BlankCompanyName;
import com.dispatchflow.access.domain.AccessError.CompanyNameTooLong;
import com.dispatchflow.access.domain.AccessError.InvalidDefaultWindow;
import com.dispatchflow.access.domain.AccessError.NonPositiveDefaultServiceTime;
import com.dispatchflow.shared.SharedKernelError.TextHasForbiddenCharacters;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.TextKind;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CompanyValuesTest {

    @Test
    void GivenANameOf19CharactersWithSpacesAround_WhenChecked_ThenItIsKeptTrimmed() {
        assertThat(new CompanyName("  " + "מ".repeat(19) + " ").value()).isEqualTo("מ".repeat(19));
    }

    @Test
    void GivenANameOf20Characters_WhenChecked_ThenCompanyNameTooLong() {
        assertRefused(() -> new CompanyName("a".repeat(20)), new CompanyNameTooLong(19));
    }

    @Test
    void GivenANameWhoseLastCharacterTakesTwoSmsUnits_WhenItReaches20Units_ThenCompanyNameTooLong() {
        assertRefused(() -> new CompanyName("a".repeat(18) + "🚚"), new CompanyNameTooLong(19));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t"})
    void GivenAnEmptyName_WhenChecked_ThenBlankCompanyName(String name) {
        assertRefused(() -> new CompanyName(name), new BlankCompanyName());
    }

    @Test
    void GivenANameWithARightToLeftOverride_WhenChecked_ThenTextHasForbiddenCharacters() {
        assertRefused(() -> new CompanyName("Fast\u202EDeliveries"), new TextHasForbiddenCharacters(TextKind.NAME));
    }

    @Test
    void GivenANameOfNoBreakSpacesOnly_WhenChecked_ThenBlankCompanyName() {
        assertRefused(() -> new CompanyName("\u00A0\u00A0\u202F"), new BlankCompanyName());
    }

    @Test
    void GivenAHebrewNameWithAGeresh_WhenChecked_ThenItIsKept() {
        assertThat(new CompanyName("\u00A0צ׳יפס משלוחים\u00A0").value()).isEqualTo("צ׳יפס משלוחים");
    }

    @Test
    void GivenANameInDecomposedForm_WhenChecked_ThenItIsKeptInNfc() {
        assertThat(new CompanyName("Cafe\u0301").value()).isEqualTo("Caf\u00E9");
    }

    @Test
    void GivenAStoredNameTheRulesNowRefuse_WhenRebuilt_ThenStoredStateInvalid() {
        assertThat(CompanyName.ofStored("Fast Deliveries")).isEqualTo(new CompanyName("Fast Deliveries"));
        assertThatThrownBy(() -> CompanyName.ofStored("a".repeat(20))).isInstanceOf(StoredStateInvalid.class);
    }

    @Test
    void GivenAWindowOpeningBeforeItCloses_WhenChecked_ThenItIsTheDefaultWindow() {
        DefaultWindow window = new DefaultWindow(LocalTime.of(9, 0), LocalTime.of(17, 0));

        assertThat(window.opens()).isEqualTo(LocalTime.of(9, 0));
        assertThat(window.closes()).isEqualTo(LocalTime.of(17, 0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"09:00", "08:59"})
    void GivenAWindowThatDoesNotOpenBeforeItCloses_WhenChecked_ThenInvalidDefaultWindow(String closes) {
        LocalTime opens = LocalTime.of(9, 0);
        LocalTime closing = LocalTime.parse(closes);

        assertRefused(() -> new DefaultWindow(opens, closing), new InvalidDefaultWindow(opens, closing));
    }

    @Test
    void GivenOneSecond_WhenChecked_ThenItIsTheDefaultServiceTime() {
        assertThat(new DefaultServiceTime(1).seconds()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void GivenNoTimeOrLess_WhenChecked_ThenNonPositiveDefaultServiceTime(long seconds) {
        assertRefused(() -> new DefaultServiceTime(seconds), new NonPositiveDefaultServiceTime(seconds));
    }
}

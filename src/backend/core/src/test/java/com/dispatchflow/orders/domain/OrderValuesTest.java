package com.dispatchflow.orders.domain;

import static com.dispatchflow.orders.domain.OrdersTestData.TOMORROW;
import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.orders.domain.OrdersError.BlankAddress;
import com.dispatchflow.orders.domain.OrdersError.BlankContactName;
import com.dispatchflow.orders.domain.OrdersError.InvalidDailyWindow;
import com.dispatchflow.orders.domain.OrdersError.NonPositiveServiceTime;
import com.dispatchflow.shared.TimeWindow;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The Orders context's small values (DL §3.3): what each checks, and that personal ones hide themselves. */
class OrderValuesTest {

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n", "-", "?", "(!)", "\u00a0", "\u00a0\u00a0", "\"", "'", "''", "-'-"})
    void GivenNoTextOrOnlyPunctuation_WhenAnAddressIsBuilt_ThenBlankAddress(String text) {
        assertRefused(() -> Address.of(text), new BlankAddress());
    }

    @Test
    void GivenAnAddress_WhenBuilt_ThenItIsKeptWithItsEndsTrimmedAndItsInteriorAsTyped() {
        assertThat(Address.of(" \u00a0הרצל  5, ת\"א ").text()).isEqualTo("הרצל  5, ת\"א");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void GivenNoText_WhenANameIsBuilt_ThenBlankContactName(String text) {
        assertRefused(() -> ContactName.of(text), new BlankContactName());
    }

    @Test
    void GivenANameWithSurroundingSpaces_WhenBuilt_ThenItIsTrimmedAndHiddenWhenPrinted() {
        ContactName name = ContactName.of("  דנה לוי ");

        assertThat(name.text()).isEqualTo("דנה לוי");
        assertThat(name.toString()).doesNotContain("דנה");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void GivenNoPositiveSeconds_WhenAServiceTimeIsBuilt_ThenNonPositiveServiceTime(long seconds) {
        assertRefused(() -> ServiceTime.ofSeconds(seconds), new NonPositiveServiceTime(seconds));
    }

    @Test
    void GivenOneSecond_WhenAServiceTimeIsBuilt_ThenItIsKept() {
        assertThat(ServiceTime.ofSeconds(1).seconds()).isEqualTo(1);
    }

    @Test
    void GivenAStartNotBeforeTheEnd_WhenADailyWindowIsBuilt_ThenInvalidDailyWindow() {
        assertRefused(
                () -> DailyWindow.of(LocalTime.of(12, 0), LocalTime.of(12, 0)),
                new InvalidDailyWindow(LocalTime.of(12, 0), LocalTime.of(12, 0)));
    }

    @Test
    void GivenADailyWindow_WhenPutOnADate_ThenItIsThatDatesWindow() {
        DailyWindow window = DailyWindow.of(LocalTime.of(8, 0), LocalTime.of(10, 0));

        assertThat(window.on(TOMORROW)).isEqualTo(TimeWindow.on(TOMORROW, LocalTime.of(8, 0), LocalTime.of(10, 0)));
    }

    @Test
    void GivenBlankText_WhenANoteOrAReferenceIsRead_ThenThereIsNone() {
        assertThat(DriverNote.of("  ")).isEmpty();
        assertThat(ExternalReference.of("")).isEmpty();
    }

    @Test
    void GivenTextWithSurroundingSpaces_WhenANoteOrAReferenceIsRead_ThenItIsTrimmedAndHiddenWhenPrinted() {
        DriverNote note = DriverNote.of(" הכלב נובח ").orElseThrow();
        ExternalReference reference = ExternalReference.of(" SHOP-7 ").orElseThrow();

        assertThat(note.text()).isEqualTo("הכלב נובח");
        assertThat(note.toString()).doesNotContain("הכלב");
        assertThat(reference).isEqualTo(ExternalReference.of("SHOP-7").orElseThrow());
        assertThat(reference.toString()).doesNotContain("SHOP");
    }

    @Test
    void GivenBlankAccessParts_WhenRead_ThenTheyAreAbsentAndTheRestAreTrimmedAndHidden() {
        AccessDetails access = AccessDetails.of(" 4 ", "  ", "12#");

        assertThat(access.floor().map(Floor::text)).contains("4");
        assertThat(access.apartment()).isEmpty();
        assertThat(access.entranceCode().map(EntranceCode::text)).contains("12#");
        assertThat(access.isGiven()).isTrue();
        assertThat(AccessDetails.of(" ", "", "	")).isEqualTo(AccessDetails.NONE);
        assertThat(access.toString()).doesNotContain("4").doesNotContain("12#");
        assertThat(Apartment.of(" 7 ").orElseThrow().toString()).doesNotContain("7");
    }

    @Test
    void GivenAnAttempt_WhenTheNextIsTaken_ThenItIsNumberedOneHigher() {
        assertThat(Attempt.FIRST.next()).isEqualTo(Attempt.of(2));
    }
}
